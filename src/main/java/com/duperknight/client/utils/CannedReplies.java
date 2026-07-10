package com.duperknight.client.utils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The built-in replies for /dmls say. Edit this map to change the available replies.
 */
public final class CannedReplies {
    private static final Map<String, String> REPLIES = new LinkedHashMap<>();

    static {
        REPLIES.put("rules", "Please make sure to read the server rules, you can find them with /rules.");
        REPLIES.put("appeal", "You can appeal your punishment on the Stoneworks website or Discord.");
        REPLIES.put("helpop", "If you need staff assistance, please use /helpop with a short description of the issue.");
    }

    private CannedReplies() {
    }

    /**
     * Returns the message for the given reply name.
     *
     * @param name the reply name
     * @return the message, or empty if no reply with that name exists
     */
    public static Optional<String> get(String name) {
        return Optional.ofNullable(REPLIES.get(name));
    }

    /**
     * Returns the names of all replies.
     *
     * @return the reply names
     */
    public static List<String> names() {
        return List.copyOf(REPLIES.keySet());
    }
}
