package com.duperknight.client.moderation;

/** One validated, developer-provided automatic moderation rule. */
sealed interface AutomaticRuleDefinition permits SpamRuleDefinition, TextWallRuleDefinition {
    String id();

    String labelKey();

    boolean defaultEnabled();
}

record SpamRuleDefinition(
        String id,
        String labelKey,
        boolean defaultEnabled,
        int repeatCount,
        long repeatWindowNanos,
        double similarityThreshold,
        int fuzzyMinLength,
        int burstCount,
        long burstWindowNanos
) implements AutomaticRuleDefinition {
}

record TextWallRuleDefinition(
        String id,
        String labelKey,
        boolean defaultEnabled,
        int minLines
) implements AutomaticRuleDefinition {
}
