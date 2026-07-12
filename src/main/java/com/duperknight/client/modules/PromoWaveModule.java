package com.duperknight.client.modules;

import com.duperknight.client.gui.PromoWaveScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.ServerGuard;
import com.duperknight.client.utils.InputValidators;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.message.ServerMessageRouter;
import com.duperknight.client.parser.LuckPermsResponseParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Pattern;
import java.util.EnumSet;

public final class PromoWaveModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - PromoWave§8] §7";
    // LuckPerms silently ignores commands that arrive too quickly ("is spamming LuckPerms commands"),
    // so leave a generous gap between them.
    private static final int COMMAND_DELAY_TICKS = 20 * 3;
    private static final int RESPONSE_TIMEOUT_TICKS = 20 * 10;
    private static final Map<String, List<String>> RANK_COMMANDS = new LinkedHashMap<>();

    static {
        // The add always runs before the remove, so a failed command can never leave someone rankless.
        RANK_COMMANDS.put("helper", List.of(
                "lp user %s parent add helper"));
        RANK_COMMANDS.put("mod", List.of(
                "lp user %s parent add mod",
                "lp user %s parent remove helper"));
        RANK_COMMANDS.put("sr-mod", List.of(
                "lp user %s parent add sr-mod",
                "lp user %s parent remove mod"));
        RANK_COMMANDS.put("support", List.of(
                "lp user %s parent add support"));
        RANK_COMMANDS.put("admin", List.of(
                "lp user %s parent add admin",
                "lp user %s parent remove sr-mod"));
    }

    private PromoSession activeSession;

    public PromoWaveModule() {
        super(StaffRank.ADMIN);
    }

    /** Returns the names of all promotable ranks. */
    public static List<String> ranks() {
        return List.copyOf(RANK_COMMANDS.keySet());
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.promo_wave.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.GOLDEN_HELMET);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.promo_wave.description.1"),
                Text.translatable("dmls.module.promo_wave.description.2")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new PromoWaveScreen(parent, this));
    }

    @Override
    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var promowave = ClientCommandManager.literal("promowave");
            for (String rank : RANK_COMMANDS.keySet()) {
                promowave.then(ClientCommandManager.literal(rank)
                        .then(ClientCommandManager.argument("igns", StringArgumentType.greedyString())
                                .executes(context -> {
                                    submit(context.getSource().getClient(), rank, StringArgumentType.getString(context, "igns"));
                                    return 1;
                                })));
            }
            dispatcher.register(promowave);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (activeSession != null) {
                activeSession.tick(client);
            }
        });
        ServerMessageRouter.subscribe(EnumSet.of(MessageOrigin.SERVER_SYSTEM), this::handleServerMessage);
    }

    private void handleServerMessage(ServerMessage message) {
        if (activeSession != null) activeSession.handleServerMessage(message.cleanText());
    }

    /** Starts a promotion wave. The command and GUI both call this method. */
    public void submit(MinecraftClient client, String rank, String input) {
        if (!canRunPrivilegedOperation(client)) {
            return;
        }

        List<String> commandsPerPlayer = RANK_COMMANDS.get(rank);
        if (commandsPerPlayer == null) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.promo.unknown_rank", rank, String.join(", ", ranks()));
            return;
        }

        List<String> igns = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        igns.addAll(InputValidators.uniqueUsernames(input, skipped));

        if (!skipped.isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX,
                    skipped.size() == 1 ? "dmls.chat.promo.skipping.one" : "dmls.chat.promo.skipping.many",
                    String.join(", ", skipped));
        }

        if (igns.isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.common.invalid_igns");
            return;
        }

        if (activeSession != null) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.promo.active");
            return;
        }

        activeSession = new PromoSession(rank, commandsPerPlayer, igns);
        activeSession.start(client);
    }

    private final class PromoSession {
        private final String rank;
        private final List<String> commandsPerPlayer;
        private final List<String> igns;
        private final Queue<String> remainingCommands = new ArrayDeque<>();

        private int playerIndex;
        private int commandIndexWithinPlayer;
        private int waitTicks;
        private final String serverIdentity;
        private final List<String> dispatchedPlayers = new ArrayList<>();
        private boolean awaitingResponse;
        private String awaitingIgn;
        private String awaitingGroup;
        private LuckPermsResponseParser.Action awaitingAction;

        private PromoSession(String rank, List<String> commandsPerPlayer, List<String> igns) {
            this.rank = rank;
            this.commandsPerPlayer = commandsPerPlayer;
            this.igns = igns;
            this.serverIdentity = ServerGuard.connectionIdentity(MinecraftClient.getInstance());
            for (String ign : igns) {
                for (String template : commandsPerPlayer) {
                    remainingCommands.add(template.formatted(ign));
                }
            }
        }

        private void start(MinecraftClient client) {
            ChatUtils.sendTranslatedMessage(client, PREFIX,
                    igns.size() == 1 ? "dmls.chat.promo.start.one" : "dmls.chat.promo.start.many", igns.size(), rank);
            sendNext(client);
        }

        private void tick(MinecraftClient client) {
            if (ClientUtils.isNotConnected(client) || !serverIdentity.equals(ServerGuard.connectionIdentity(client))) {
                cancel(client);
                return;
            }

            waitTicks++;
            if (awaitingResponse && waitTicks >= RESPONSE_TIMEOUT_TICKS) {
                cancel(client);
            } else if (!awaitingResponse && waitTicks >= COMMAND_DELAY_TICKS) {
                sendNext(client);
            }
        }

        private void sendNext(MinecraftClient client) {
            waitTicks = 0;
            String command = remainingCommands.poll();
            if (command == null) {
                report(client);
                activeSession = null;
                return;
            }

            if (commandIndexWithinPlayer == 0) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.promo.progress",
                        igns.get(playerIndex), playerIndex + 1, igns.size());
            }

            String template = commandsPerPlayer.get(commandIndexWithinPlayer);
            awaitingIgn = igns.get(playerIndex);
            awaitingAction = template.contains(" parent add ")
                    ? LuckPermsResponseParser.Action.ADD : LuckPermsResponseParser.Action.REMOVE;
            String marker = awaitingAction == LuckPermsResponseParser.Action.ADD ? " parent add " : " parent remove ";
            awaitingGroup = template.substring(template.indexOf(marker) + marker.length());
            if (!ClientUtils.sendCommand(client, command)) {
                cancel(client);
                return;
            }
            awaitingResponse = true;

            if (com.duperknight.client.utils.DMLSConfig.dryRun()) {
                // nothing will confirm in dry run, so advance as if it did
                awaitingResponse = false;
                commandIndexWithinPlayer++;
                if (commandIndexWithinPlayer >= commandsPerPlayer.size()) {
                    dispatchedPlayers.add(igns.get(playerIndex));
                    commandIndexWithinPlayer = 0;
                    playerIndex++;
                }
            }
        }

        private void handleServerMessage(String message) {
            if (!awaitingResponse || LuckPermsResponseParser.parseParentChange(
                    awaitingAction, awaitingIgn, awaitingGroup, message) != LuckPermsResponseParser.Result.CONFIRMED) {
                return;
            }
            awaitingResponse = false;
            waitTicks = 0;
            commandIndexWithinPlayer++;
            if (commandIndexWithinPlayer >= commandsPerPlayer.size()) {
                dispatchedPlayers.add(igns.get(playerIndex));
                commandIndexWithinPlayer = 0;
                playerIndex++;
            }
        }

        private void report(MinecraftClient client) {
            ChatUtils.sendTranslatedMessage(client, PREFIX,
                    igns.size() == 1 ? "dmls.chat.promo.confirmed.one" : "dmls.chat.promo.confirmed.many",
                    dispatchedPlayers.size(), rank, String.join(", ", dispatchedPlayers));
        }

        private void cancel(MinecraftClient client) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.promo.cancelled",
                    dispatchedPlayers.size(), igns.size());
            if (activeSession == this) activeSession = null;
        }
    }
}
