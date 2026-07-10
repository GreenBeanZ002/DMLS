package com.duperknight.client.utils;

import com.duperknight.DMLS;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Loads the canned replies for /dmls say from config/dmls-replies.properties.
 * The file is read on every use, so edits apply without a reload command.
 * When the mod ships newer defaults (a higher template version), the old file
 * is backed up to dmls-replies.properties.bak and replaced with the new defaults.
 */
public final class CannedReplies {
    private static final int TEMPLATE_VERSION = 1;
    private static final String VERSION_KEY = "templateVersion";
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("dmls-replies.properties");
    private static final Path BACKUP_PATH = FabricLoader.getInstance().getConfigDir().resolve("dmls-replies.properties.bak");
    private static final String TEMPLATE = """
            # DMLS canned replies for /dmls say <name>. One reply per line, format: name=message
            # Names can't contain spaces. Edits apply immediately.
            # Don't remove templateVersion, it lets the mod replace outdated defaults
            # (your old file is kept as dmls-replies.properties.bak).
            templateVersion=%d
            rules=Please make sure to read the server rules, you can find them with /rules.
            appeal=You can appeal your punishment on the Stoneworks website or Discord.
            helpop=If you need staff assistance, please use /helpop with a short description of the issue.
            """.formatted(TEMPLATE_VERSION);

    private CannedReplies() {
    }

    /**
     * Returns the message for the given reply name.
     *
     * @param name the reply name
     * @return the message, or empty if no reply with that name exists
     */
    public static Optional<String> get(String name) {
        if (name.equalsIgnoreCase(VERSION_KEY)) {
            return Optional.empty();
        }
        return Optional.ofNullable(load().getProperty(name));
    }

    /**
     * Returns the names of all configured replies, sorted alphabetically.
     *
     * @return the reply names
     */
    public static List<String> names() {
        return load().stringPropertyNames().stream()
                .filter(name -> !name.equalsIgnoreCase(VERSION_KEY))
                .sorted()
                .toList();
    }

    private static Properties load() {
        if (!Files.exists(PATH)) {
            writeTemplate();
        }

        Properties properties = read();
        if (templateVersion(properties) < TEMPLATE_VERSION) {
            backupAndReplace();
            properties = read();
        }
        return properties;
    }

    private static Properties read() {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(PATH)) {
            properties.load(in);
        } catch (IOException e) {
            DMLS.LOGGER.warn("Failed to read {}", PATH, e);
        }
        return properties;
    }

    private static int templateVersion(Properties properties) {
        try {
            return Integer.parseInt(properties.getProperty(VERSION_KEY, "0").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void backupAndReplace() {
        try {
            Files.copy(PATH, BACKUP_PATH, StandardCopyOption.REPLACE_EXISTING);
            DMLS.LOGGER.info("Replaced outdated canned replies, the old file was kept as {}", BACKUP_PATH.getFileName());
        } catch (IOException e) {
            DMLS.LOGGER.warn("Failed to back up {}", PATH, e);
        }
        writeTemplate();
    }

    private static void writeTemplate() {
        try {
            Files.writeString(PATH, TEMPLATE);
        } catch (IOException e) {
            DMLS.LOGGER.warn("Failed to create {}", PATH, e);
        }
    }
}
