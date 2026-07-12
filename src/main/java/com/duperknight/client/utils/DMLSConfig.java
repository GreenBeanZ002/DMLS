package com.duperknight.client.utils;

import com.duperknight.DMLS;
import com.duperknight.client.modules.StaffRank;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Handles the DMLS settings (selected staff rank and chat alerts toggle), stored in config/dmls.properties.
 */
public final class DMLSConfig {
    public static final List<String> RANK_SUGGESTIONS = List.of("helper", "moderator", "senior_moderator", "support", "admin");

    private static final String RANK_KEY = "staffRank";
    private static final String ALERTS_KEY = "chatAlerts";
    private static final String TRADE_CHAT_MUTED_KEY = "tradeChatMuted";
    private static final String SERVER_MESSAGES_MUTED_KEY = "serverMessagesMuted";
    private static final String ALLOWED_SERVERS_KEY = "allowedServers";
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("dmls.properties");
    private static final StaffRank DEFAULT_RANK = StaffRank.HELPER;

    private static boolean loaded;
    private static StaffRank staffRank = DEFAULT_RANK;
    private static boolean alertsEnabled = true;
    private static boolean tradeChatMuted;
    private static boolean serverMessagesMuted;
    private static List<String> allowedServers = ServerGuard.DEFAULT_ALLOWED_SERVERS;

    private DMLSConfig() {
    }

    public static StaffRank staffRank() {
        ensureLoaded();
        return staffRank;
    }

    public static void setStaffRank(StaffRank rank) {
        ensureLoaded();
        staffRank = rank;
        save();
    }

    public static boolean alertsEnabled() {
        ensureLoaded();
        return alertsEnabled;
    }

    public static void setAlertsEnabled(boolean enabled) {
        ensureLoaded();
        alertsEnabled = enabled;
        save();
    }

    public static boolean tradeChatMuted() {
        ensureLoaded();
        return tradeChatMuted;
    }

    public static void setTradeChatMuted(boolean muted) {
        ensureLoaded();
        tradeChatMuted = muted;
        save();
    }

    public static boolean serverMessagesMuted() {
        ensureLoaded();
        return serverMessagesMuted;
    }

    public static void setServerMessagesMuted(boolean muted) {
        ensureLoaded();
        serverMessagesMuted = muted;
        save();
    }

    public static List<String> allowedServers() {
        ensureLoaded();
        return allowedServers;
    }

    public static void setAllowedServers(List<String> servers) {
        ensureLoaded();
        allowedServers = servers.stream()
                .map(ServerGuard::normalizeRule)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
        save();
    }

    /**
     * Parses a staff rank from user input, also accepting aliases like "mod" and "srmod".
     *
     * @param input the input to parse
     * @return the parsed rank, or empty if the input is not a valid rank
     */
    public static Optional<StaffRank> parseRank(String input) {
        String normalized = input.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (StaffRank rank : StaffRank.values()) {
            if (rank.name().equals(normalized)) {
                return Optional.of(rank);
            }
        }

        return switch (normalized) {
            case "MOD" -> Optional.of(StaffRank.MODERATOR);
            case "SRMOD", "SR_MOD", "SENIORMOD", "SENIOR_MOD" -> Optional.of(StaffRank.SENIOR_MODERATOR);
            default -> Optional.empty();
        };
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;

        if (!Files.exists(CONFIG_PATH)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            properties.load(in);
        } catch (IOException e) {
            DMLS.LOGGER.warn("Failed to read {}, using defaults", CONFIG_PATH, e);
            return;
        }

        staffRank = parseRank(properties.getProperty(RANK_KEY, "")).orElse(DEFAULT_RANK);
        alertsEnabled = Boolean.parseBoolean(properties.getProperty(ALERTS_KEY, "true"));
        tradeChatMuted = Boolean.parseBoolean(properties.getProperty(TRADE_CHAT_MUTED_KEY, "false"));
        serverMessagesMuted = Boolean.parseBoolean(properties.getProperty(SERVER_MESSAGES_MUTED_KEY, "false"));
        if (properties.containsKey(ALLOWED_SERVERS_KEY)) {
            allowedServers = java.util.Arrays.stream(properties.getProperty(ALLOWED_SERVERS_KEY, "").split(","))
                    .map(ServerGuard::normalizeRule)
                    .flatMap(Optional::stream)
                    .distinct()
                    .toList();
        } else {
            allowedServers = ServerGuard.DEFAULT_ALLOWED_SERVERS;
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty(RANK_KEY, staffRank.name());
        properties.setProperty(ALERTS_KEY, Boolean.toString(alertsEnabled));
        properties.setProperty(TRADE_CHAT_MUTED_KEY, Boolean.toString(tradeChatMuted));
        properties.setProperty(SERVER_MESSAGES_MUTED_KEY, Boolean.toString(serverMessagesMuted));
        properties.setProperty(ALLOWED_SERVERS_KEY, String.join(",", allowedServers));
        try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(out, "DMLS settings");
        } catch (IOException e) {
            DMLS.LOGGER.warn("Failed to save {}", CONFIG_PATH, e);
        }
    }
}
