package com.duperknight.client.utils;

import com.duperknight.DMLS;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Loads the canned replies for /dmls say from config/dmls-replies.properties.
 * The file is read on every use, so edits apply without a reload command.
 */
public final class CannedReplies {
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("dmls-replies.properties");
    private static final String TEMPLATE = """
            # DMLS canned replies for /dmls say <name>. One reply per line, format: name=message
            # Names can't contain spaces. Edits apply immediately.
            rules=Please make sure to read the server rules, you can find them with /rules.
            appeal=You can appeal your punishment on the Stoneworks website or Discord.
            helpop=If you need staff assistance, please use /helpop with a short description of the issue.
            """;

    private CannedReplies() {
    }

    /**
     * Returns the message for the given reply name.
     *
     * @param name the reply name
     * @return the message, or empty if no reply with that name exists
     */
    public static Optional<String> get(String name) {
        return Optional.ofNullable(load().getProperty(name));
    }

    /**
     * Returns the names of all configured replies, sorted alphabetically.
     *
     * @return the reply names
     */
    public static List<String> names() {
        return load().stringPropertyNames().stream().sorted().toList();
    }

    private static Properties load() {
        Properties properties = new Properties();
        if (!Files.exists(PATH)) {
            try {
                Files.writeString(PATH, TEMPLATE);
            } catch (IOException e) {
                DMLS.LOGGER.warn("Failed to create {}", PATH, e);
            }
        }

        try (InputStream in = Files.newInputStream(PATH)) {
            properties.load(in);
        } catch (IOException e) {
            DMLS.LOGGER.warn("Failed to read {}", PATH, e);
        }
        return properties;
    }
}
