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

/** Stateful, side-effect-free detector for configured automatic moderation rules. */
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
    private final Map<String, SpamState> spamStates = new HashMap<>();
    private final Map<Long, Set<String>> matches = new HashMap<>();
    private long alertRevision;

    AutomaticRuleDetector(Collection<AutomaticRuleDefinition> rules, Predicate<AutomaticRuleDefinition> enabled) {
        for (AutomaticRuleDefinition rule : rules) {
            definitions.put(rule.id(), rule);
            if (enabled.test(rule)) enabledRuleIds.add(rule.id());
        }
    }

    List<AutomaticRuleDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    boolean isEnabled(String ruleId) {
        return enabledRuleIds.contains(ruleId);
    }

    boolean setEnabled(String ruleId, boolean enabled) {
        if (!definitions.containsKey(ruleId) || enabled == enabledRuleIds.contains(ruleId)) return false;
        if (enabled) {
            enabledRuleIds.add(ruleId);
        } else {
            enabledRuleIds.remove(ruleId);
            spamStates.remove(ruleId);
            removeRuleMatches(ruleId);
        }
        return true;
    }

    DetectionUpdate accept(Message message) {
        if (message == null || message.channel() != ChatChannel.GLOBAL) {
            return new DetectionUpdate(false, alertRevision);
        }
        boolean newAlert = false;
        for (AutomaticRuleDefinition definition : definitions.values()) {
            if (!enabledRuleIds.contains(definition.id())) continue;
            if (definition instanceof SpamRuleDefinition spam) {
                newAlert |= detectSpam(spam, message);
            } else if (definition instanceof TextWallRuleDefinition textWall
                    && message.renderedBodyLines() >= textWall.minLines()) {
                addMatch(message.sequence(), definition.id());
                newAlert = true;
            }
        }
        if (newAlert) alertRevision++;
        return new DetectionUpdate(newAlert, alertRevision);
    }

    Snapshot snapshot() {
        return new Snapshot(matches, alertRevision);
    }

    void removeSequence(long sequence) {
        matches.remove(sequence);
        for (SpamState state : spamStates.values()) state.removeSequence(sequence);
    }

    void reset() {
        spamStates.clear();
        matches.clear();
    }

    private boolean detectSpam(SpamRuleDefinition rule, Message message) {
        SpamState state = spamStates.computeIfAbsent(rule.id(), ignored -> new SpamState());
        boolean repeatedAlert = state.repeated.accept(rule, message, this::addMatches);
        boolean burstAlert = state.bursts.accept(rule, message, this::addMatches);
        return repeatedAlert || burstAlert;
    }

    private void addMatches(Collection<Long> sequences, String ruleId) {
        sequences.forEach(sequence -> addMatch(sequence, ruleId));
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

    private interface MatchConsumer {
        void add(Collection<Long> sequences, String ruleId);
    }

    private static final class SpamState {
        private final RepeatTracker repeated = new RepeatTracker();
        private final BurstTracker bursts = new BurstTracker();

        private void removeSequence(long sequence) {
            repeated.removeSequence(sequence);
            bursts.removeSequence(sequence);
        }
    }

    private static final class RepeatTracker {
        private final List<RepeatCluster> clusters = new ArrayList<>();

        private boolean accept(SpamRuleDefinition rule, Message message, MatchConsumer matches) {
            clusters.removeIf(cluster -> message.capturedAtNanos() - cluster.lastAt > rule.repeatWindowNanos());
            String normalized = normalizeForSimilarity(message.body());
            RepeatCluster best = null;
            double bestSimilarity = -1.0D;
            for (RepeatCluster cluster : clusters) {
                double similarity = similarity(cluster.representative, normalized, rule.fuzzyMinLength());
                if (similarity >= rule.similarityThreshold() && similarity > bestSimilarity) {
                    best = cluster;
                    bestSimilarity = similarity;
                }
            }
            if (best == null) {
                best = new RepeatCluster(normalized);
                clusters.add(best);
            }
            best.recent.removeIf(entry -> message.capturedAtNanos() - entry.at > rule.repeatWindowNanos());
            best.recent.addLast(new TimedSequence(message.sequence(), message.capturedAtNanos()));
            best.lastAt = message.capturedAtNanos();
            if (best.triggered) {
                matches.add(List.of(message.sequence()), rule.id());
                return false;
            }
            if (best.recent.size() < rule.repeatCount()) return false;
            best.triggered = true;
            matches.add(best.recent.stream().map(TimedSequence::sequence).toList(), rule.id());
            return true;
        }

        private void removeSequence(long sequence) {
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

    private static final class BurstTracker {
        private final Map<String, BurstWindow> bySender = new HashMap<>();

        private boolean accept(SpamRuleDefinition rule, Message message, MatchConsumer matches) {
            if (message.senderKey() == null || message.senderKey().isBlank()) return false;
            BurstWindow window = bySender.computeIfAbsent(message.senderKey(), ignored -> new BurstWindow());
            if (!window.recent.isEmpty() && message.capturedAtNanos() - window.lastAt > rule.burstWindowNanos()) {
                window = new BurstWindow();
                bySender.put(message.senderKey(), window);
            }
            window.recent.removeIf(entry -> message.capturedAtNanos() - entry.at > rule.burstWindowNanos());
            window.recent.addLast(new TimedSequence(message.sequence(), message.capturedAtNanos()));
            window.lastAt = message.capturedAtNanos();
            if (window.triggered) {
                matches.add(List.of(message.sequence()), rule.id());
                return false;
            }
            if (window.recent.size() < rule.burstCount()) return false;
            window.triggered = true;
            matches.add(window.recent.stream().map(TimedSequence::sequence).toList(), rule.id());
            return true;
        }

        private void removeSequence(long sequence) {
            bySender.values().forEach(window -> window.recent.removeIf(entry -> entry.sequence == sequence));
            bySender.entrySet().removeIf(entry -> entry.getValue().recent.isEmpty());
        }
    }

    private static final class BurstWindow {
        private final Deque<TimedSequence> recent = new ArrayDeque<>();
        private long lastAt;
        private boolean triggered;
    }

    private record TimedSequence(long sequence, long at) {
    }
}
