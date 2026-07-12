package com.duperknight.client.modules;

import com.duperknight.client.gui.CoreProtectBuilderScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Composes CoreProtect lookup, rollback and restore commands from a form instead of memorized syntax. */
public final class CoreProtectBuilderModule extends DMLSModule {
    public static final List<String> MODES = List.of("lookup", "rollback", "restore");
    public static final List<String> ACTIONS = List.of("(any)", "block", "+block", "-block", "container", "kill", "item", "sign", "chat", "command", "session");

    private static final String PREFIX = "§8[§6DMLS - CoreProtect§8] §7";
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern TIME = Pattern.compile("(\\d{1,4}[wdhms])+");
    private static final Pattern RADIUS = Pattern.compile("#global|\\d{1,5}");
    private static final Pattern LIST = Pattern.compile("[a-z0-9_,:#\\-]+");

    /** The composed command, or the translation key of the first validation error. */
    public record BuildResult(String command, String errorKey) {
        public boolean valid() {
            return errorKey.isEmpty();
        }

        private static BuildResult error(String errorKey) {
            return new BuildResult("", errorKey);
        }
    }

    public CoreProtectBuilderModule() {
        super(StaffRank.SENIOR_MODERATOR);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.co_builder.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.SPYGLASS);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.co_builder.description.1"),
                Text.translatable("dmls.module.co_builder.description.2")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new CoreProtectBuilderScreen(parent, this));
    }

    @Override
    public void register() {
        // GUI only, opened from the DMLS menu or /dmls co.
    }

    /** Opens the builder screen. */
    public void openScreenDeferred(MinecraftClient client) {
        // next tick, otherwise the closing chat screen overrides it
        client.send(() -> client.setScreen(new CoreProtectBuilderScreen(null, this)));
    }

    /** Validates the form values and composes the CoreProtect command. */
    public static BuildResult build(String mode, String user, String time, String radius, String action, String include, String exclude) {
        if (!MODES.contains(mode)) {
            return BuildResult.error("dmls.validation.co.mode");
        }

        String cleanUser = user.trim();
        if (!cleanUser.isEmpty() && !USERNAME.matcher(cleanUser).matches()) {
            return BuildResult.error("dmls.validation.co.user");
        }

        String cleanTime = time.trim().toLowerCase(Locale.ROOT);
        if (!cleanTime.isEmpty() && !TIME.matcher(cleanTime).matches()) {
            return BuildResult.error("dmls.validation.co.time");
        }

        String cleanRadius = radius.trim().toLowerCase(Locale.ROOT);
        if (!cleanRadius.isEmpty() && !RADIUS.matcher(cleanRadius).matches()) {
            return BuildResult.error("dmls.validation.co.radius");
        }

        String cleanInclude = normalizeList(include);
        if (!cleanInclude.isEmpty() && !LIST.matcher(cleanInclude).matches()) {
            return BuildResult.error("dmls.validation.co.include");
        }

        String cleanExclude = normalizeList(exclude);
        if (!cleanExclude.isEmpty() && !LIST.matcher(cleanExclude).matches()) {
            return BuildResult.error("dmls.validation.co.exclude");
        }

        boolean destructive = !mode.equals("lookup");
        if (destructive && cleanTime.isEmpty()) {
            return BuildResult.error("dmls.validation.co.time_required");
        }
        if (destructive && cleanUser.isEmpty() && cleanRadius.isEmpty()) {
            return BuildResult.error("dmls.validation.co.target_required");
        }
        if (!destructive && cleanUser.isEmpty() && cleanTime.isEmpty() && cleanRadius.isEmpty() && cleanInclude.isEmpty()) {
            return BuildResult.error("dmls.validation.co.lookup_empty");
        }

        StringBuilder command = new StringBuilder("co ").append(mode);
        if (!cleanUser.isEmpty()) {
            command.append(" u:").append(cleanUser);
        }
        if (!cleanTime.isEmpty()) {
            command.append(" t:").append(cleanTime);
        }
        if (!cleanRadius.isEmpty()) {
            command.append(" r:").append(cleanRadius);
        }
        if (!action.isEmpty() && !action.equals(ACTIONS.get(0))) {
            command.append(" a:").append(action);
        }
        if (!cleanInclude.isEmpty()) {
            command.append(" i:").append(cleanInclude);
        }
        if (!cleanExclude.isEmpty()) {
            command.append(" e:").append(cleanExclude);
        }
        return new BuildResult(command.toString(), "");
    }

    /** Validates and runs the composed command. The GUI calls this method. */
    public void submit(MinecraftClient client, String mode, String user, String time, String radius, String action, String include, String exclude) {
        if (!canRunPrivilegedOperation(client)) {
            return;
        }

        BuildResult result = build(mode, user, time, radius, action, include, exclude);
        if (!result.valid()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, result.errorKey());
            return;
        }

        if (ClientUtils.sendCommand(client, result.command())) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.co.sent", "/" + result.command());
        } else {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.command.not_sent");
        }
    }

    private static String normalizeList(String input) {
        return input.trim().toLowerCase(Locale.ROOT).replaceAll("\\s*,\\s*", ",").replaceAll(",+$", "");
    }
}
