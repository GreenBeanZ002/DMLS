package com.duperknight.client.moderation;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Stateful, side-effect-free evaluator for JSON-composed automatic moderation rules. */
final class AutomaticRuleDetector {
    record Message(long sequence, long capturedAtNanos, ChatChannel channel, String body,
                   String senderKey, int renderedBodyLines) {
    }

    record DetectionUpdate(boolean newAlert, long alertRevision) {
    }

    record Snapshot(Map<Long, Set<String>> matches, long alertRevision) {
        Snapshot {
            Map<Long, Set<String>> copy = new HashMap<>();
            matches.forEach((sequence, ids) -> copy.put(sequence, Set.copyOf(ids)));
            matches = Map.copyOf(copy);
        }

        boolean flagged(long sequence) {
            return matches.containsKey(sequence);
        }
    }

    private final Map<String, AutomaticRuleDefinition> definitions = new LinkedHashMap<>();
    private final Set<String> enabledRuleIds = new HashSet<>();
    private final Map<String, RuleState> ruleStates = new HashMap<>();
    private final Map<Long, Set<String>> matches = new HashMap<>();
    private long alertRevision;

    AutomaticRuleDetector(Collection<AutomaticRuleDefinition> rules, Predicate<AutomaticRuleDefinition> enabled) {
        for (AutomaticRuleDefinition rule : rules) {
            definitions.put(rule.id(), rule);
            if (enabled.test(rule)) {
                enabledRuleIds.add(rule.id());
                ruleStates.put(rule.id(), new RuleState(rule));
            }
        }
    }

    List<AutomaticRuleDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    boolean isEnabled(String ruleId) {
        return enabledRuleIds.contains(ruleId);
    }

    boolean setEnabled(String ruleId, boolean enabled) {
        AutomaticRuleDefinition definition = definitions.get(ruleId);
        if (definition == null || enabled == enabledRuleIds.contains(ruleId)) return false;
        if (enabled) {
            enabledRuleIds.add(ruleId);
            ruleStates.put(ruleId, new RuleState(definition));
        } else {
            enabledRuleIds.remove(ruleId);
            ruleStates.remove(ruleId);
            removeRuleMatches(ruleId);
        }
        return true;
    }

    DetectionUpdate accept(Message message) {
        if (message == null) return new DetectionUpdate(false, alertRevision);
        boolean newAlert = false;
        for (AutomaticRuleDefinition definition : definitions.values()) {
            if (!enabledRuleIds.contains(definition.id()) || !definition.channels().contains(message.channel())) continue;
            RuleMatch result = ruleStates.get(definition.id()).accept(message);
            if (!result.matched()) continue;
            result.sequences().forEach(sequence -> addMatch(sequence, definition.id()));
            newAlert |= result.newIncident();
        }
        if (newAlert) alertRevision++;
        return new DetectionUpdate(newAlert, alertRevision);
    }

    Snapshot snapshot() {
        return new Snapshot(matches, alertRevision);
    }

    void removeSequence(long sequence) {
        matches.remove(sequence);
        ruleStates.values().forEach(state -> state.removeSequence(sequence));
    }

    void reset() {
        ruleStates.clear();
        for (String ruleId : enabledRuleIds) {
            ruleStates.put(ruleId, new RuleState(definitions.get(ruleId)));
        }
        matches.clear();
    }

    private void addMatch(long sequence, String ruleId) {
        matches.computeIfAbsent(sequence, ignored -> new HashSet<>()).add(ruleId);
    }

    private void removeRuleMatches(String ruleId) {
        matches.entrySet().removeIf(entry -> {
            entry.getValue().remove(ruleId);
            return entry.getValue().isEmpty();
        });
    }

    static String normalizeForSimilarity(String input) {
        String normalized = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder content = new StringBuilder(normalized.length());
        StringBuilder fallback = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint -> {
            if (!Character.isWhitespace(codePoint)) fallback.appendCodePoint(codePoint);
            if (!Character.isWhitespace(codePoint) && !isPunctuation(codePoint)) content.appendCodePoint(codePoint);
        });
        return content.isEmpty() ? fallback.toString() : content.toString();
    }

    static double similarity(String left, String right, int fuzzyMinLength) {
        if (left.equals(right)) return 1.0D;
        int[] a = left.codePoints().toArray();
        int[] b = right.codePoints().toArray();
        if (a.length < fuzzyMinLength || b.length < fuzzyMinLength) return 0.0D;
        int maximum = Math.max(a.length, b.length);
        return maximum == 0 ? 1.0D : 1.0D - (double) editDistance(a, b) / maximum;
    }

    private static boolean isPunctuation(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION,
                    Character.START_PUNCTUATION, Character.END_PUNCTUATION,
                    Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
                    Character.OTHER_PUNCTUATION -> true;
            default -> false;
        };
    }

    private static int editDistance(int[] left, int[] right) {
        if (left.length > right.length) return editDistance(right, left);
        int[] previous = new int[left.length + 1];
        int[] current = new int[left.length + 1];
        for (int index = 0; index <= left.length; index++) previous[index] = index;
        for (int rightIndex = 1; rightIndex <= right.length; rightIndex++) {
            current[0] = rightIndex;
            for (int leftIndex = 1; leftIndex <= left.length; leftIndex++) {
                int substitution = previous[leftIndex - 1]
                        + (left[leftIndex - 1] == right[rightIndex - 1] ? 0 : 1);
                current[leftIndex] = Math.min(Math.min(
                        current[leftIndex - 1] + 1,
                        previous[leftIndex] + 1), substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[left.length];
    }

    private record RuleMatch(boolean matched, boolean newIncident, Set<Long> sequences) {
        private static final RuleMatch NONE = new RuleMatch(false, false, Set.of());
    }

    private record RequirementMatch(boolean matched, boolean newIncident, Set<Long> sequences) {
        private static final RequirementMatch NONE = new RequirementMatch(false, false, Set.of());

        private static RequirementMatch incident(Collection<Long> sequences) {
            return new RequirementMatch(true, true, Set.copyOf(sequences));
        }

        private static RequirementMatch continuation(long sequence) {
            return new RequirementMatch(true, false, Set.of(sequence));
        }
    }

    private interface RequirementTracker {
        RequirementMatch accept(Message message);

        default void removeSequence(long sequence) {
        }
    }

    private static final class RuleState {
        private final int minimumMatches;
        private final List<RequirementTracker> trackers;

        private RuleState(AutomaticRuleDefinition definition) {
            minimumMatches = definition.minimumMatches();
            trackers = definition.requirements().stream().map(RuleState::tracker).toList();
        }

        private RuleMatch accept(Message message) {
            List<RequirementMatch> results = trackers.stream().map(tracker -> tracker.accept(message)).toList();
            List<RequirementMatch> matching = results.stream().filter(RequirementMatch::matched).toList();
            if (matching.size() < minimumMatches) return RuleMatch.NONE;

            Map<Long, Integer> sequenceMatches = new HashMap<>();
            matching.forEach(result -> result.sequences().forEach(
                    sequence -> sequenceMatches.merge(sequence, 1, Integer::sum)));
            Set<Long> sequences = new HashSet<>();
            sequenceMatches.forEach((sequence, count) -> {
                if (count >= minimumMatches) sequences.add(sequence);
            });
            if (sequences.isEmpty()) sequences.add(message.sequence());
            boolean newIncident = matching.stream().anyMatch(RequirementMatch::newIncident);
            return new RuleMatch(true, newIncident, Set.copyOf(sequences));
        }

        private void removeSequence(long sequence) {
            trackers.forEach(tracker -> tracker.removeSequence(sequence));
        }

        private static RequirementTracker tracker(AutomaticRuleRequirement requirement) {
            return switch (requirement) {
                case MessageCountRequirement count when count.groupBy() == MessageCountGrouping.SIMILAR_CONTENT ->
                        new SimilarContentCountTracker(count);
                case MessageCountRequirement count -> new SenderCountTracker(count);
                case RenderedLinesRequirement lines -> message ->
                        message.renderedBodyLines() >= lines.minimum()
                                ? RequirementMatch.incident(List.of(message.sequence()))
                                : RequirementMatch.NONE;
            };
        }
    }

    private static final class SimilarContentCountTracker implements RequirementTracker {
        private final MessageCountRequirement requirement;
        private final List<RepeatCluster> clusters = new ArrayList<>();

        private SimilarContentCountTracker(MessageCountRequirement requirement) {
            this.requirement = requirement;
        }

        @Override
        public RequirementMatch accept(Message message) {
            clusters.removeIf(cluster -> message.capturedAtNanos() - cluster.lastAt > requirement.windowNanos());
            String normalized = normalizeForSimilarity(message.body());
            RepeatCluster best = null;
            double bestSimilarity = -1.0D;
            for (RepeatCluster cluster : clusters) {
                double similarity = similarity(cluster.representative, normalized, requirement.fuzzyMinLength());
                if (similarity >= requirement.similarityThreshold() && similarity > bestSimilarity) {
                    best = cluster;
                    bestSimilarity = similarity;
                }
            }
            if (best == null) {
                best = new RepeatCluster(normalized);
                clusters.add(best);
            }
            best.recent.removeIf(entry -> message.capturedAtNanos() - entry.at > requirement.windowNanos());
            best.recent.addLast(new TimedSequence(message.sequence(), message.capturedAtNanos()));
            best.lastAt = message.capturedAtNanos();
            if (best.triggered) return RequirementMatch.continuation(message.sequence());
            if (best.recent.size() < requirement.minimum()) return RequirementMatch.NONE;
            best.triggered = true;
            return RequirementMatch.incident(best.recent.stream().map(TimedSequence::sequence).toList());
        }

        @Override
        public void removeSequence(long sequence) {
            clusters.forEach(cluster -> cluster.recent.removeIf(entry -> entry.sequence == sequence));
            clusters.removeIf(cluster -> cluster.recent.isEmpty());
        }
    }

    private static final class RepeatCluster {
        private final String representative;
        private final Deque<TimedSequence> recent = new ArrayDeque<>();
        private long lastAt;
        private boolean triggered;

        private RepeatCluster(String representative) {
            this.representative = representative;
        }
    }

    private static final class SenderCountTracker implements RequirementTracker {
        private final MessageCountRequirement requirement;
        private final Map<String, CountWindow> bySender = new HashMap<>();

        private SenderCountTracker(MessageCountRequirement requirement) {
            this.requirement = requirement;
        }

        @Override
        public RequirementMatch accept(Message message) {
            if (message.senderKey() == null || message.senderKey().isBlank()) return RequirementMatch.NONE;
            CountWindow window = bySender.computeIfAbsent(message.senderKey(), ignored -> new CountWindow());
            if (!window.recent.isEmpty() && message.capturedAtNanos() - window.lastAt > requirement.windowNanos()) {
                window = new CountWindow();
                bySender.put(message.senderKey(), window);
            }
            window.recent.removeIf(entry -> message.capturedAtNanos() - entry.at > requirement.windowNanos());
            window.recent.addLast(new TimedSequence(message.sequence(), message.capturedAtNanos()));
            window.lastAt = message.capturedAtNanos();
            if (window.triggered) return RequirementMatch.continuation(message.sequence());
            if (window.recent.size() < requirement.minimum()) return RequirementMatch.NONE;
            window.triggered = true;
            return RequirementMatch.incident(window.recent.stream().map(TimedSequence::sequence).toList());
        }

        @Override
        public void removeSequence(long sequence) {
            bySender.values().forEach(window -> window.recent.removeIf(entry -> entry.sequence == sequence));
            bySender.entrySet().removeIf(entry -> entry.getValue().recent.isEmpty());
        }
    }

    private static final class CountWindow {
        private final Deque<TimedSequence> recent = new ArrayDeque<>();
        private long lastAt;
        private boolean triggered;
    }

    private record TimedSequence(long sequence, long at) {
    }
}
