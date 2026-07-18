package com.duperknight.client.moderation;

import java.util.List;
import java.util.Set;

/** One validated automatic rule assembled from reusable, data-defined requirements. */
record AutomaticRuleDefinition(
        String id,
        String labelKey,
        boolean defaultEnabled,
        Set<ChatChannel> channels,
        int minimumMatches,
        List<AutomaticRuleRequirement> requirements
) {
    AutomaticRuleDefinition {
        channels = Set.copyOf(channels);
        requirements = List.copyOf(requirements);
        if (requirements.isEmpty()) throw new IllegalArgumentException("requirements must not be empty");
        if (minimumMatches < 1) throw new IllegalArgumentException("minimumMatches must be positive");
        minimumMatches = Math.min(minimumMatches, requirements.size());
    }
}

/** A reusable metric that can be composed into any automatic rule. */
sealed interface AutomaticRuleRequirement permits MessageCountRequirement, RenderedLinesRequirement {
}

record MessageCountRequirement(
        MessageCountGrouping groupBy,
        int minimum,
        long windowNanos,
        double similarityThreshold,
        int fuzzyMinLength
) implements AutomaticRuleRequirement {
}

enum MessageCountGrouping {
    SIMILAR_CONTENT,
    SENDER
}

record RenderedLinesRequirement(int minimum) implements AutomaticRuleRequirement {
}
