package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.EventRandomTeleportScreen;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessageRouter;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ConnectionSnapshot;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.utils.DryRunFeedback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EventRandomTeleportModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - RandomTP§8] §7";
    private static final Random RANDOM = new Random();
    private final VanishTracker vanishTracker = new VanishTracker();

    public enum TeleportStatus {
        SENT,
        SIMULATED,
        NO_PLAYERS,
        BLOCKED
    }

    public record TeleportResult(TeleportStatus status, String target, boolean vanishRequested) {
        public boolean accepted() {
            return status == TeleportStatus.SENT || status == TeleportStatus.SIMULATED;
        }
    }

    public EventRandomTeleportModule() {
        super(StaffDepartment.EVENTS);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.random_teleport.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.ENDER_PEARL);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.random_teleport.description"));
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.EVENTS;
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new EventRandomTeleportScreen(parent, this));
    }

    @Override
    public void register() {
        ServerMessageRouter.subscribe(EnumSet.of(MessageOrigin.SERVER_SYSTEM, MessageOrigin.OVERLAY),
                message -> vanishTracker.accept(message.cleanText()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> vanishTracker.reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> vanishTracker.reset());
    }

    /** Compatibility helper for callers that only need the accepted target. */
    public String teleportToRandomPlayer(MinecraftClient client) {
        TeleportResult result = teleport(client);
        return result.accepted() ? result.target() : null;
    }

    /** Enables vanish only when this session has observed it off, then teleports once. */
    public TeleportResult teleport(MinecraftClient client) {
        if (!canRunPrivilegedOperation(client)) {
            return new TeleportResult(TeleportStatus.BLOCKED, "", false);
        }
        if (ClientUtils.isNotConnected(client)) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.command.not_sent");
            return new TeleportResult(TeleportStatus.BLOCKED, "", false);
        }

        List<String> players = new ArrayList<>(ClientUtils.getOnlinePlayerNames(client));
        String selfName = client.getSession().getUsername();
        players.removeIf(player -> player.equalsIgnoreCase(selfName));

        if (players.isEmpty()) {
            return new TeleportResult(TeleportStatus.NO_PLAYERS, "", false);
        }

        String target = players.get(RANDOM.nextInt(players.size()));
        boolean dryRun = DMLSConfig.dryRun();
        ConnectionSnapshot connection = ConnectionSnapshot.capture(client);
        boolean needsVanish = vanishTracker.needsEnable();

        if (needsVanish) {
            CommandDispatch vanish = ClientUtils.dispatchCommand(client, "vanish", dryRun, connection,
                    DryRunFeedback.OPERATION_SUMMARY);
            if (vanish == CommandDispatch.BLOCKED) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.command.not_sent");
                return new TeleportResult(TeleportStatus.BLOCKED, target, true);
            }
            if (vanish == CommandDispatch.SENT) vanishTracker.enableDispatched();
        }

        CommandDispatch teleport = ClientUtils.dispatchCommand(client, "tp " + target, dryRun, connection,
                DryRunFeedback.OPERATION_SUMMARY);
        if (teleport == CommandDispatch.BLOCKED) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.command.not_sent");
            return new TeleportResult(TeleportStatus.BLOCKED, target, needsVanish);
        }
        if (teleport == CommandDispatch.SIMULATED) {
            ChatUtils.sendTranslatedMessage(client, PREFIX,
                    needsVanish
                            ? "dmls.chat.random_teleport.simulated_with_vanish"
                            : "dmls.chat.random_teleport.simulated",
                    target);
            return new TeleportResult(TeleportStatus.SIMULATED, target, needsVanish);
        }
        return new TeleportResult(TeleportStatus.SENT, target, needsVanish);
    }

    static final class VanishTracker {
        private static final Pattern STATUS = Pattern.compile(
                "^vanish\\s*:\\s*(enabled|disabled)[.!]?$", Pattern.CASE_INSENSITIVE);
        private State state = State.OFF;

        enum State { OFF, ENABLING, ON }

        boolean needsEnable() {
            return state == State.OFF;
        }

        State state() {
            return state;
        }

        void enableDispatched() {
            if (state == State.OFF) state = State.ENABLING;
        }

        void accept(String message) {
            Matcher matcher = STATUS.matcher(message == null ? "" : message.trim());
            if (!matcher.matches()) return;
            state = matcher.group(1).equalsIgnoreCase("enabled") ? State.ON : State.OFF;
        }

        void reset() {
            state = State.OFF;
        }
    }
}
