package com.duperknight.client.parser;

import java.util.Locale;
import java.util.regex.Pattern;

/** Exact response matcher for the known Stoneworks prefix commands. */
public final class PrefixResponseParser {
    private PrefixResponseParser() {
    }

    public static Result parse(Command command, String prefixId, String ign, String limit, String message) {
        String value = message == null ? "" : message.trim();
        return switch (command) {
            case CREATE -> matches("^Successfully created prefix .+ \\(ID: " + Pattern.quote(prefixId) + "\\)\\.$", value)
                    ? Result.CONFIRMED : Result.UNRELATED;
            case SET_LIMIT -> value.equalsIgnoreCase("Set the limit of " + prefixId + " to " + limit + ".")
                    ? Result.CONFIRMED : Result.UNRELATED;
            case SET_MANAGER -> {
                if (value.equalsIgnoreCase("Successfully set the manager of " + prefixId + " to " + ign + ".")) yield Result.CONFIRMED;
                if (value.equalsIgnoreCase("Unable to get that player's profile.")) yield Result.REJECTED;
                yield Result.UNRELATED;
            }
            case INFO -> matches("^Manager: " + Pattern.quote(ign) + " \\(.+\\)$", value) ? Result.CONFIRMED : Result.UNRELATED;
        };
    }

    private static boolean matches(String expression, String value) {
        return Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(value).matches();
    }

    public enum Command { CREATE, SET_LIMIT, SET_MANAGER, INFO }
    public enum Result { CONFIRMED, REJECTED, UNRELATED }
}
