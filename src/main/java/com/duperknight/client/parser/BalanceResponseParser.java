package com.duperknight.client.parser;

import java.util.regex.Pattern;

/** Matcher for the known Stoneworks /bal responses. */
public final class BalanceResponseParser {
    private BalanceResponseParser() {
    }

    public static Result parse(String username, String message) {
        String value = message == null ? "" : message.trim();
        if (Pattern.compile("^" + Pattern.quote(username) + " has .+ Coins !$", Pattern.CASE_INSENSITIVE).matcher(value).matches()) {
            return Result.CONFIRMED;
        }
        return value.equalsIgnoreCase("Player not found!") ? Result.REJECTED : Result.UNRELATED;
    }

    public enum Result { CONFIRMED, REJECTED, UNRELATED }
}
