package com.duperknight.client.modules;

import com.duperknight.client.gui.XrayRollbackScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class XrayRollbackModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Xray§8] §7";
    private static final int ROLLBACK_TIMEOUT_TICKS = 20 * 60;
    private static final int BALANCE_WAIT_TICKS = 20 * 2;
    private static final int COMMAND_GAP_TICKS = 20 * 2;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private static final List<Step> STEPS = List.of(
            new Step("dmls.chat.xray.step.deepslate",
                    "co rollback u:%s t:30d radius:#global a:block include:deepslate, deepslate_gold_ore, deepslate_emerald_ore, deepslate_diamond_ore, deepslate_iron_ore, deepslate_lapis_ore, deepslate_redstone_ore, deepslate_copper_ore, tuff",
                    true),
            new Step("dmls.chat.xray.step.stone",
                    "co rollback u:%s t:30d radius:#global a:block include:stone, gold_ore, iron_ore, emerald_ore, diamond_ore, redstone_ore, lapis_ore, coal_ore, granite, diorite, andesite, gravel",
                    true),
            new Step("dmls.chat.xray.step.containers",
                    "co rollback u:%s t:7d radius:#global action:container",
                    true),
            new Step("dmls.chat.xray.step.balance",
                    "bal %s",
                    false)
    );

    private RollbackSession activeSession;

    public XrayRollbackModule() {
        super(StaffRank.SENIOR_MODERATOR);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.xray.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.DEEPSLATE_DIAMOND_ORE);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.xray.description.1"),
                Text.translatable("dmls.module.xray.description.2")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new XrayRollbackScreen(parent, this));
    }

    @Override
    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("xray")
                        .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                                .executes(context -> {
                                    submit(context.getSource().getClient(), StringArgumentType.getString(context, "ign").trim());
                                    return 1;
                                }))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (activeSession != null) {
                activeSession.tick(client);
            }
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> handleServerMessage(message.getString()));
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> handleServerMessage(message.getString()));
    }

    /** Starts the xray rollback for the given player. The command and GUI both call this method. */
    public void submit(MinecraftClient client, String ign) {
        if (!hasRequiredRank(client)) {
            return;
        }

        if (!USERNAME.matcher(ign).matches()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.common.invalid_ign");
            return;
        }

        if (activeSession != null) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.xray.active", activeSession.ign);
            return;
        }

        activeSession = new RollbackSession(ign);
        activeSession.start(client);
    }

    private void handleServerMessage(String message) {
        if (activeSession != null) {
            activeSession.handleServerMessage(ChatUtils.cleanLine(message));
        }
    }

    private record Step(String label, String commandTemplate, boolean waitForCompletion) {
    }

    private final class RollbackSession {
        private final String ign;
        private final List<Text> results = new ArrayList<>();

        private int stepIndex = -1;
        private int waitTicks;
        private int postCompletionTicks;
        private boolean completionSeen;

        private RollbackSession(String ign) {
            this.ign = ign;
        }

        private void start(MinecraftClient client) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.xray.start", ign, STEPS.size());
            nextStep(client);
        }

        private void tick(MinecraftClient client) {
            if (ClientUtils.isNotConnected(client)) {
                activeSession = null;
                return;
            }

            if (stepIndex >= STEPS.size()) {
                return;
            }

            waitTicks++;
            Step step = STEPS.get(stepIndex);
            if (step.waitForCompletion()) {
                if (completionSeen) {
                    // small gap after the completion message before the next command
                    if (postCompletionTicks++ >= COMMAND_GAP_TICKS) {
                        results.add(Text.translatable("dmls.chat.xray.result.success", Text.translatable(step.label())));
                        nextStep(client);
                    }
                } else if (waitTicks > ROLLBACK_TIMEOUT_TICKS) {
                    results.add(Text.translatable("dmls.chat.xray.result.manual", Text.translatable(step.label())));
                    nextStep(client);
                }
            } else if (waitTicks > BALANCE_WAIT_TICKS) {
                results.add(Text.translatable("dmls.chat.xray.result.output", Text.translatable(step.label())));
                nextStep(client);
            }
        }

        private void handleServerMessage(String message) {
            if (stepIndex < 0 || stepIndex >= STEPS.size() || !STEPS.get(stepIndex).waitForCompletion()) {
                return;
            }

            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("rollback complete")) {
                completionSeen = true;
            }
        }

        private void nextStep(MinecraftClient client) {
            stepIndex++;
            if (stepIndex >= STEPS.size()) {
                report(client);
                finish();
                return;
            }

            Step step = STEPS.get(stepIndex);
            waitTicks = 0;
            postCompletionTicks = 0;
            completionSeen = false;
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.xray.step", stepIndex + 1, STEPS.size(),
                    Text.translatable(step.label()));
            ClientUtils.sendCommand(client, step.commandTemplate().formatted(ign));
        }

        private void report(MinecraftClient client) {
            String header = Text.translatable("dmls.chat.xray.header", ign).getString();
            ChatUtils.sendClientMessage(client, header + ChatUtils.separatorForChatWidth(client, header));
            for (Text result : results) {
                ChatUtils.sendClientMessage(client, result);
            }
            ChatUtils.sendClientMessage(client, "§7" + ChatUtils.separatorForChatWidth(client, ""));
        }

        private void finish() {
            if (activeSession == this) {
                activeSession = null;
            }
        }
    }
}
