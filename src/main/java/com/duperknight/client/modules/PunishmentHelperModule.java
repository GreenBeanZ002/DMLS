package com.duperknight.client.modules;

import com.duperknight.DMLS;
import com.duperknight.client.gui.PunishmentHelperScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Rulebook browser and ban-log composer built from the Stoneworks rulebook. */
public final class PunishmentHelperModule extends DMLSModule {
    /** The minimum rank required to actually issue a ban from the helper. */
    public static final StaffRank BAN_RANK = StaffRank.MODERATOR;
    private static final String PREFIX = "§8[§6DMLS - Punish§8] §7";
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Identifier RULEBOOK = Identifier.of(DMLS.MOD_ID.toLowerCase(Locale.ROOT), "rulebook.json");
    private static final Pattern RULE_ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SECTION = Pattern.compile("\"section\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern TITLE = Pattern.compile("\"title\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern PUNISHMENT = Pattern.compile("\"punishment\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static final List<Rule> RULES = new ArrayList<>();

    /** One rulebook entry. */
    public record Rule(String id, String section, String title, String punishment) {
        public String label() {
            return id + " " + title;
        }

        public boolean matches(String needle) {
            return id.toLowerCase(Locale.ROOT).contains(needle)
                    || section.toLowerCase(Locale.ROOT).contains(needle)
                    || title.toLowerCase(Locale.ROOT).contains(needle)
                    || punishment.toLowerCase(Locale.ROOT).contains(needle);
        }
    }

    public PunishmentHelperModule() {
        super(StaffRank.HELPER);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.punish.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.WRITTEN_BOOK);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.punish.description.1"),
                Text.translatable("dmls.module.punish.description.2")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new PunishmentHelperScreen(parent, this));
    }

    @Override
    public void register() {
        loadRules();
    }

    /** Returns all rules, loading them on first use. */
    public static List<Rule> rules() {
        if (RULES.isEmpty()) {
            loadRules();
        }
        return List.copyOf(RULES);
    }

    /** Returns rules matching the query in id, section, title or punishment. */
    public static List<Rule> search(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return rules();
        }
        return rules().stream().filter(rule -> rule.matches(needle)).collect(Collectors.toList());
    }

    /** Whether the selected rank may issue bans. */
    public static boolean canBan() {
        return com.duperknight.client.utils.DMLSConfig.staffRank().isAtLeast(BAN_RANK);
    }

    /**
     * Runs the server ban command: /ban &lt;ign&gt; &lt;duration&gt; &lt;reason&gt;.
     *
     * @return true if the command was dispatched
     */
    public static boolean ban(MinecraftClient client, String ign, String duration, String reason) {
        if (!canBan()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.rank.required",
                    BAN_RANK.displayName(), com.duperknight.client.utils.DMLSConfig.staffRank().displayName());
            return false;
        }

        String cleanIgn = ign.trim();
        if (!USERNAME.matcher(cleanIgn).matches()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.common.invalid_ign");
            return false;
        }

        String cleanDuration = duration.trim();
        String cleanReason = reason.trim();
        if (cleanDuration.isEmpty() || cleanReason.isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.punish.ban_incomplete");
            return false;
        }

        if (ClientUtils.sendCommand(client, "ban %s %s %s".formatted(cleanIgn, cleanDuration, cleanReason))) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.punish.banned", cleanIgn, cleanDuration);
            return true;
        }
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.command.not_sent");
        return false;
    }

    /** Builds the ban-log text in the Stoneworks format. */
    public static String banLog(String ign, String discord, String reason, String type,
                                String ticket, String comments, String evidence) {
        return "Ban Format\n"
                + "IGN - " + ign.trim() + "\n"
                + "Discord - " + discord.trim() + "\n"
                + "Reason - " + reason.trim() + "\n"
                + "Type - " + type.trim() + "\n"
                + "Ticket & Category - " + ticket.trim() + "\n"
                + "Comments - " + comments.trim() + "\n"
                + "Evidence - " + evidence.trim();
    }

    private static synchronized void loadRules() {
        if (!RULES.isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getResourceManager() == null) {
            return;
        }

        try (InputStream in = client.getResourceManager().open(RULEBOOK);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
            parse(builder.toString());
        } catch (Exception e) {
            DMLS.LOGGER.warn("Failed to load the rulebook", e);
        }
    }

    private static void parse(String json) {
        // Each rule object holds id, section, title and punishment in order.
        int index = 0;
        Matcher idMatcher = RULE_ID.matcher(json);
        while (idMatcher.find(index)) {
            String id = idMatcher.group(1);
            int from = idMatcher.end();
            String section = firstAfter(SECTION, json, from);
            String title = firstAfter(TITLE, json, from);
            String punishment = firstAfter(PUNISHMENT, json, from);
            RULES.add(new Rule(id, unescape(section), unescape(title), unescape(punishment)));
            index = from;
        }
    }

    private static String firstAfter(Pattern pattern, String json, int from) {
        Matcher matcher = pattern.matcher(json);
        return matcher.find(from) ? matcher.group(1) : "";
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\n", " ").replace("\\\\", "\\").replace("\\/", "/");
    }
}
