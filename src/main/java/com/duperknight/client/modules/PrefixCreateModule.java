package com.duperknight.client.modules;

import com.duperknight.client.gui.PrefixCreateScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.PrefixTextFormatter;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class PrefixCreateModule extends DMLSModule {
    public static final int MAX_COMMAND_LENGTH = 256;
    public static final String CUSTOM_LIMIT = "Custom";
    public static final List<String> LIMITS = List.of("10", "30", Integer.toString(Integer.MAX_VALUE), CUSTOM_LIMIT);
    private static final List<String> COMMAND_LIMIT_SUGGESTIONS = LIMITS.stream().filter(limit -> !CUSTOM_LIMIT.equals(limit)).toList();

    private static final String PREFIX = "§8[§6DMLS - Prefix§8] §7";
    private static final int COMMAND_DELAY_TICKS = 20;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private CreateSession activeSession;

    public PrefixCreateModule() {
        super(StaffRank.SUPPORT);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.prefix.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.OAK_SIGN);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.prefix.description.1"),
                Text.translatable("dmls.module.prefix.description.2")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new PrefixCreateScreen(parent, this));
    }

    @Override
    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("prefixlazy")
                        .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                                .then(ClientCommandManager.argument("limit", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(COMMAND_LIMIT_SUGGESTIONS, builder))
                                        .then(ClientCommandManager.argument("prefixid", StringArgumentType.word())
                                                .then(ClientCommandManager.argument("prefixtext", StringArgumentType.greedyString())
                                                        .suggests((context, builder) -> CommandSource.suggestMatching(List.of("&a", "&#FFFFFF", "<green>"), builder))
                                                        .executes(context -> {
                                                            submit(context.getSource().getClient(),
                                                                    StringArgumentType.getString(context, "ign").trim(),
                                                                    StringArgumentType.getString(context, "limit").trim(),
                                                                    StringArgumentType.getString(context, "prefixid").trim(),
                                                                    StringArgumentType.getString(context, "prefixtext").trim());
                                                            return 1;
                                                        })))))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (activeSession != null) {
                activeSession.tick(client);
            }
        });
    }

    /** Starts the prefix creation. The command and GUI both call this method. */
    public ValidationResult submit(MinecraftClient client, String ign, String limit, String prefixId, String prefixText) {
        if (!hasRequiredRank(client)) {
            return ValidationResult.error("dmls.validation.required_rank");
        }

        ValidationResult validation = validate(ign, limit, prefixId, prefixText);
        if (!validation.valid()) {
            ChatUtils.sendClientMessage(client, Text.literal(PREFIX).append(validation.message()));
            return validation;
        }

        if (activeSession != null) {
            ValidationResult activeSessionError = ValidationResult.error("dmls.validation.prefix.active");
            ChatUtils.sendClientMessage(client, Text.literal(PREFIX).append(activeSessionError.message()));
            return activeSessionError;
        }

        activeSession = new CreateSession(ign, validation.limit(), prefixId, prefixText);
        activeSession.start(client);
        return ValidationResult.success(validation.limit());
    }

    public static ValidationResult validate(String ign, String limit, String prefixId, String prefixText) {
        if (!USERNAME.matcher(ign).matches()) {
            return ValidationResult.error("dmls.validation.prefix.ign");
        }

        Optional<String> resolvedLimit = resolveLimit(limit);
        if (resolvedLimit.isEmpty()) {
            return ValidationResult.error("dmls.validation.prefix.limit");
        }

        if (prefixId.isEmpty()) {
            return ValidationResult.error("dmls.validation.prefix.id");
        }

        if (prefixText.isEmpty()) {
            return ValidationResult.error("dmls.validation.prefix.text");
        }

        PrefixTextFormatter.ParseResult formattedPrefix = PrefixTextFormatter.parse(prefixText);
        if (!formattedPrefix.valid()) {
            return ValidationResult.error("dmls.validation.prefix.format", formattedPrefix.error());
        }

        int commandLength = createCommand(prefixId, prefixText).length();
        if (commandLength > MAX_COMMAND_LENGTH) {
            return ValidationResult.error("dmls.validation.prefix.command_length", commandLength, MAX_COMMAND_LENGTH);
        }

        return ValidationResult.success(resolvedLimit.get());
    }

    public static Optional<String> resolveLimit(String limit) {
        try {
            int parsed = Integer.parseInt(limit);
            return parsed > 0 ? Optional.of(Integer.toString(parsed)) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public static String createCommand(String prefixId, String prefixText) {
        return "prefix create %s %s".formatted(prefixId, prefixText);
    }

    public record ValidationResult(String limit, Text message) {
        public static ValidationResult success(String limit) {
            return new ValidationResult(limit, Text.empty());
        }

        public static ValidationResult error(String translationKey, Object... args) {
            return new ValidationResult("", Text.translatable(translationKey, args));
        }

        public boolean valid() {
            return message.getString().isEmpty();
        }
    }

    private final class CreateSession {
        private final String ign;
        private final String limit;
        private final String prefixId;
        private final String prefixText;
        private final List<String> commands;

        private int commandIndex;
        private int waitTicks;

        private CreateSession(String ign, String limit, String prefixId, String prefixText) {
            this.ign = ign;
            this.limit = limit;
            this.prefixId = prefixId;
            this.prefixText = prefixText;
            this.commands = List.of(
                    createCommand(prefixId, prefixText),
                    "prefix x setlimit %s %s".formatted(prefixId, limit),
                    "prefix x setmanager %s %s".formatted(prefixId, ign),
                    "prefix x info %s".formatted(prefixId)
            );
        }

        private void start(MinecraftClient client) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.prefix.start", prefixId, ign);
            ClientUtils.sendCommand(client, commands.get(0));
        }

        private void tick(MinecraftClient client) {
            if (ClientUtils.isNotConnected(client)) {
                activeSession = null;
                return;
            }

            waitTicks++;
            if (waitTicks < COMMAND_DELAY_TICKS) {
                return;
            }

            waitTicks = 0;
            commandIndex++;
            if (commandIndex >= commands.size()) {
                report(client);
                activeSession = null;
                return;
            }

            ClientUtils.sendCommand(client, commands.get(commandIndex));
        }

        private void report(MinecraftClient client) {
            PrefixTextFormatter.ParseResult formattedPrefix = PrefixTextFormatter.parse(prefixText);
            Text displayedPrefix = formattedPrefix.valid() ? formattedPrefix.preview() : Text.literal(prefixText);
            ChatUtils.sendClientMessage(client, Text.literal(PREFIX)
                    .append(Text.translatable("dmls.chat.prefix.created.before", prefixId))
                    .append(displayedPrefix)
                    .append(Text.translatable("dmls.chat.prefix.created.after", limit, ign)));
        }
    }
}
