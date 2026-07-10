package com.duperknight.client.parser;

import java.util.Locale;
import java.util.regex.Pattern;

/** Matching is intentionally narrow until anonymized Stoneworks response fixtures are available. */
public final class CoreProtectResponseParser {
    private static final Pattern COMPLETE = Pattern.compile("^(?:\\[coreprotect]\\s*)?rollback complete[.!]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REJECTED = Pattern.compile("^(?:\\[coreprotect]\\s*)?(?:error|rollback failed|no permission)(?:[:.!].*)?$", Pattern.CASE_INSENSITIVE);

    private CoreProtectResponseParser() {
    }

    public static Result parse(String cleanText) {
        String value = cleanText == null ? "" : cleanText.trim().toLowerCase(Locale.ROOT);
        if (COMPLETE.matcher(value).matches()) return Result.CONFIRMED;
        if (REJECTED.matcher(value).matches()) return Result.REJECTED;
        return Result.UNRELATED;
    }

    public enum Result { CONFIRMED, REJECTED, UNRELATED }
}
