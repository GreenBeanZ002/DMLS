package com.duperknight.client.modules;

import com.duperknight.client.gui.XrayRollbackScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.ServerGuard;
import com.duperknight.client.parser.CoreProtectResponseParser;
import com.duperknight.client.parser.BalanceResponseParser;
import com.duperknight.client.session.RollbackSafetyGate;
import com.duperknight.client.session.OperationOutcome;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.message.ServerMessageRouter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.EnumSet;

public final class XrayRollbackModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Xray§8] §7";
    private static final int ROLLBACK_TIMEOUT_TICKS = 20 * 60;
    private static final int BALANCE_WAIT_TICKS = 20 * 2;
    private static final int COMMAND_GAP_TICKS = 20 * 2;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private static final List<Step> STEPS = List.of(
            new Step("dmls.chat.xray.step.deepslate",
                    "co rollback u:%s t:30d radius:#global a:block include:deepslate, deepslate_gold_ore, deepslate_emerald_ore, deepslate_diamond_ore, deepslate_iron_ore, deepslate_lapis_ore, deepslate_redstone_ore, deepslate_copper_ore, tuff",
                    StepType.ROLLBACK),
            new Step("dmls.chat.xray.step.stone",
                    "co rollback u:%s t:30d radius:#global a:block include:stone, gold_ore, iron_ore, emerald_ore, diamond_ore, redstone_ore, lapis_ore, coal_ore, granite, diorite, andesite, gravel",
                    StepType.ROLLBACK),
            new Step("dmls.chat.xray.step.containers",
                    "co rollback u:%s t:7d radius:#global action:container",
                    StepType.ROLLBACK),
            new Step("dmls.chat.xray.step.balance",
                    "bal %s",
                    StepType.BALANCE)
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
                        .then(ClientCommandManager.literal("cancel").executes(context -> {
                            cancel(context.getSource().getClient());
                            return 1;
                        }))
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

        ServerMessageRouter.subscribe(EnumSet.of(MessageOrigin.SERVER_SYSTEM), this::handleServerMessage);
    }

    /** Starts the xray rollback for the given player. The command and GUI both call this method. */
    public void submit(MinecraftClient client, String ign) {
        if (!canRunPrivilegedOperation(client)) {
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

    public void cancel(MinecraftClient client) {
        if (activeSession != null) activeSession.cancel(client, "dmls.chat.xray.cancelled.user");
    }

    private void handleServerMessage(ServerMessage message) {
        if (activeSession != null) {
            activeSession.handleServerMessage(message.cleanText());
        }
    }

    private record Step(String label, String commandTemplate, StepType type) {
    }

    private enum StepType { ROLLBACK, BALANCE }

    private final class RollbackSession {
        private final String ign;
        private final List<Text> results = new ArrayList<>();

        private int stepIndex = -1;
        private int waitTicks;
        private int postCompletionTicks;
        private RollbackSafetyGate safetyGate;
        private OperationOutcome balanceOutcome;
        private final String serverIdentity;

        private RollbackSession(String ign) {
            this.ign = ign;
            this.serverIdentity = ServerGuard.connectionIdentity(MinecraftClient.getInstance());
        }

        private void start(MinecraftClient client) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.xray.start", ign, STEPS.size());
            nextStep(client);
        }

        private void tick(MinecraftClient client) {
            if (ClientUtils.isNotConnected(client) || !serverIdentity.equals(ServerGuard.connectionIdentity(client))) {
                cancel(client, "dmls.chat.xray.cancelled.connection");
                return;
            }

            if (stepIndex >= STEPS.size()) {
                return;
            }

            Step step = STEPS.get(stepIndex);
            if (step.type() == StepType.ROLLBACK) {
                OperationOutcome outcome = safetyGate.tick();
                if (outcome == OperationOutcome.CONFIRMED) {
                    // small gap after the completion message before the next command
                    if (postCompletionTicks++ >= COMMAND_GAP_TICKS) {
                        results.add(Text.translatable("dmls.chat.xray.result.success", Text.translatable(step.label())));
                        nextStep(client);
                    }
                } else if (outcome == OperationOutcome.TIMED_OUT) {
                    results.add(Text.translatable("dmls.chat.xray.result.timed_out", Text.translatable(step.label())));
                    report(client);
                    finish();
                }
            } else if (balanceOutcome == OperationOutcome.CONFIRMED) {
                results.add(Text.translatable("dmls.chat.xray.result.balance_confirmed", Text.translatable(step.label())));
                nextStep(client);
            } else if (balanceOutcome == OperationOutcome.REJECTED) {
                results.add(Text.translatable("dmls.chat.xray.result.balance_rejected", Text.translatable(step.label())));
                nextStep(client);
            } else if (++waitTicks > BALANCE_WAIT_TICKS) {
                results.add(Text.translatable("dmls.chat.xray.result.output", Text.translatable(step.label())));
                nextStep(client);
            }
        }

        private void handleServerMessage(String message) {
            if (stepIndex < 0 || stepIndex >= STEPS.size()) {
                return;
            }

            if (STEPS.get(stepIndex).type() == StepType.BALANCE) {
                switch (BalanceResponseParser.parse(ign, message)) {
                    case CONFIRMED -> balanceOutcome = OperationOutcome.CONFIRMED;
                    case REJECTED -> balanceOutcome = OperationOutcome.REJECTED;
                    case UNRELATED -> { }
                }
                return;
            }

            switch (CoreProtectResponseParser.parse(message)) {
                case CONFIRMED -> safetyGate.confirm();
                case REJECTED -> {
                    safetyGate.reject();
                    results.add(Text.translatable("dmls.chat.xray.result.rejected", Text.translatable(STEPS.get(stepIndex).label())));
                    MinecraftClient client = MinecraftClient.getInstance();
                    report(client);
                    finish();
                }
                case UNRELATED -> { }
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
            safetyGate = step.type() == StepType.ROLLBACK ? new RollbackSafetyGate(ROLLBACK_TIMEOUT_TICKS) : null;
            balanceOutcome = step.type() == StepType.BALANCE ? OperationOutcome.PENDING : null;
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.xray.step", stepIndex + 1, STEPS.size(),
                    Text.translatable(step.label()));
            if (!ClientUtils.sendCommand(client, step.commandTemplate().formatted(ign))) {
                cancel(client, "dmls.chat.xray.cancelled.connection");
                return;
            }

            if (com.duperknight.client.utils.DMLSConfig.dryRun()) {
                // nothing will confirm in dry run, so treat every step as completed
                if (safetyGate != null) safetyGate.confirm();
                else balanceOutcome = OperationOutcome.CONFIRMED;
            }
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

        private void cancel(MinecraftClient client, String reasonKey) {
            if (safetyGate != null) safetyGate.cancel();
            if (stepIndex >= 0 && stepIndex < STEPS.size()) {
                results.add(Text.translatable("dmls.chat.xray.result.cancelled", Text.translatable(STEPS.get(stepIndex).label())));
            }
            ChatUtils.sendTranslatedMessage(client, PREFIX, reasonKey);
            report(client);
            finish();
        }
    }
}
