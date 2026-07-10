package com.duperknight.client.parser;

/** Exact matcher for the Stoneworks LuckPerms lines supplied by staff. */
public final class LuckPermsResponseParser {
    private LuckPermsResponseParser() {
    }

    public static Result parsePermissionAlreadySet(String username, String permission, String message) {
        return equals(message, "[LP] " + username + " already has " + permission + " set in context global.")
                ? Result.CONFIRMED : Result.UNRELATED;
    }

    public static Result parseParentChange(Action action, String username, String group, String message) {
        String expected = switch (action) {
            case ADD -> "[LP] " + username + " now inherits permissions from " + group + " in context global.";
            case REMOVE -> "[LP] " + username + " no longer inherits permissions from " + group + " in context global.";
        };
        return equals(message, expected) ? Result.CONFIRMED : Result.UNRELATED;
    }

    private static boolean equals(String actual, String expected) {
        return actual != null && actual.trim().equalsIgnoreCase(expected);
    }

    public enum Action { ADD, REMOVE }
    public enum Result { CONFIRMED, UNRELATED }
}
