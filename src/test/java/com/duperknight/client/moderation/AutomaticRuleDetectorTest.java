package com.duperknight.client.moderation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomaticRuleDetectorTest {
    private static final long SECOND = 1_000_000_000L;
    private static final SpamRuleDefinition SPAM = new SpamRuleDefinition(
            "spam", "dmls.test.spam", true, 4, 5 * SECOND, 0.8D, 8,
            3, 1_500_000_000L);
    private static final TextWallRuleDefinition TEXT_WALL = new TextWallRuleDefinition(
            "text_wall", "dmls.test.text_wall", true, 2);

    @Test
    void repeatedMessagesCanComeFromDifferentSendersAndHighlightWholeIncident() {
        AutomaticRuleDetector detector = detector(SPAM);
        accept(detector, 1, 0, "Selling iron cheaply", "one", 1);
        accept(detector, 2, SECOND, "selling iron, cheaply!", "two", 1);
        accept(detector, 3, 2 * SECOND, "Selling iron cheap", "three", 1);
        AutomaticRuleDetector.DetectionUpdate update = accept(
                detector, 4, 3 * SECOND, "SELLING IRON CHEAPLY", "four", 1);

        assertTrue(update.newAlert());
        assertEquals(1, update.alertRevision());
        assertEquals(List.of(1L, 2L, 3L, 4L), detector.snapshot().matches().keySet().stream().sorted().toList());

        AutomaticRuleDetector.DetectionUpdate continuation = accept(
                detector, 5, 4 * SECOND, "selling iron cheaply", "five", 1);
        assertFalse(continuation.newAlert());
        assertTrue(detector.snapshot().flagged(5));
    }

    @Test
    void oldRepeatedMessagesFallOutsideTheFiveSecondWindow() {
        AutomaticRuleDetector detector = detector(SPAM);
        accept(detector, 1, 0, "same long message", "one", 1);
        accept(detector, 2, SECOND, "same long message", "two", 1);
        accept(detector, 3, 2 * SECOND, "same long message", "three", 1);
        AutomaticRuleDetector.DetectionUpdate update = accept(
                detector, 4, 8 * SECOND, "same long message", "four", 1);

        assertFalse(update.newAlert());
        assertTrue(detector.snapshot().matches().isEmpty());
    }

    @Test
    void similarityUsesEightyPercentButShortMessagesMustBeExact() {
        assertEquals(0.8D, AutomaticRuleDetector.similarity("abcdefghij", "abxdxfghij", 8), 0.0001D);
        assertTrue(AutomaticRuleDetector.similarity("abcdefghij", "abxdxfghij", 8) >= 0.8D);
        assertTrue(AutomaticRuleDetector.similarity("hello", "hello", 8) >= 0.8D);
        assertEquals(0.0D, AutomaticRuleDetector.similarity("hello", "hullo", 8));
        assertEquals("hélloworld", AutomaticRuleDetector.normalizeForSimilarity(" Héllo,  World! "));
        assertEquals("🔥🔥", AutomaticRuleDetector.normalizeForSimilarity("🔥 🔥"));
        assertEquals("!!!", AutomaticRuleDetector.normalizeForSimilarity("! ! !"));
    }

    @Test
    void rapidBurstIsPerSenderAndIncludesTheBoundary() {
        AutomaticRuleDetector detector = detector(SPAM);
        accept(detector, 1, 0, "first unique body", "one", 1);
        accept(detector, 2, 750_000_000L, "second unique body", "one", 1);
        AutomaticRuleDetector.DetectionUpdate third = accept(
                detector, 3, 1_500_000_000L, "third unique body", "one", 1);

        assertTrue(third.newAlert());
        assertEquals(3, detector.snapshot().matches().size());

        AutomaticRuleDetector mixed = detector(SPAM);
        accept(mixed, 1, 0, "first unique body", "one", 1);
        accept(mixed, 2, 500_000_000L, "second unique body", "two", 1);
        AutomaticRuleDetector.DetectionUpdate mixedThird = accept(
                mixed, 3, SECOND, "third unique body", "three", 1);
        assertFalse(mixedThird.newAlert());
        assertTrue(mixed.snapshot().matches().isEmpty());
    }

    @Test
    void burstResetsAfterSenderGapAndContinuationDoesNotResound() {
        AutomaticRuleDetector detector = detector(SPAM);
        accept(detector, 1, 0, "one alpha body", "sender", 1);
        accept(detector, 2, 100_000_000L, "two beta body", "sender", 1);
        assertTrue(accept(detector, 3, 200_000_000L, "three gamma body", "sender", 1).newAlert());
        assertFalse(accept(detector, 4, 300_000_000L, "four delta body", "sender", 1).newAlert());
        assertTrue(detector.snapshot().flagged(4));

        accept(detector, 5, 2 * SECOND, "five epsilon body", "sender", 1);
        accept(detector, 6, 2_100_000_000L, "six zeta body", "sender", 1);
        assertTrue(accept(detector, 7, 2_200_000_000L, "seven eta body", "sender", 1).newAlert());
    }

    @Test
    void textWallIsImmediateAndOnlyGlobalPlayerInputIsEvaluated() {
        AutomaticRuleDetector detector = detector(TEXT_WALL);
        AutomaticRuleDetector.DetectionUpdate wall = accept(detector, 1, 0, "long body", "sender", 2);
        assertTrue(wall.newAlert());
        assertTrue(detector.snapshot().flagged(1));

        AutomaticRuleDetector.DetectionUpdate oneLine = accept(detector, 2, SECOND, "short", "sender", 1);
        assertFalse(oneLine.newAlert());

        AutomaticRuleDetector.DetectionUpdate local = detector.accept(new AutomaticRuleDetector.Message(
                3, 2 * SECOND, ChatChannel.LOCAL, "long body", "sender", 4));
        assertFalse(local.newAlert());
        assertFalse(detector.snapshot().flagged(3));
    }

    @Test
    void oneMessageMatchingMultipleRulesCreatesOneAlertRevision() {
        SpamRuleDefinition quickSpam = new SpamRuleDefinition(
                "spam", "dmls.test.spam", true, 2, 5 * SECOND, 0.8D, 8,
                2, 1_500_000_000L);
        AutomaticRuleDetector detector = detector(quickSpam, TEXT_WALL);
        assertTrue(accept(detector, 1, 0, "same long body", "sender", 2).newAlert());
        AutomaticRuleDetector.DetectionUpdate overlap = accept(
                detector, 2, 100_000_000L, "same long body", "sender", 2);

        assertTrue(overlap.newAlert());
        assertEquals(2, overlap.alertRevision());
        assertEquals(2, detector.snapshot().matches().get(2L).size());
    }

    @Test
    void disablingRuleRemovesHighlightsAndStartsFreshWhenReenabled() {
        AutomaticRuleDetector detector = detector(TEXT_WALL);
        accept(detector, 1, 0, "wall", "sender", 2);
        assertTrue(detector.snapshot().flagged(1));

        assertTrue(detector.setEnabled("text_wall", false));
        assertTrue(detector.snapshot().matches().isEmpty());
        assertFalse(accept(detector, 2, SECOND, "wall", "sender", 2).newAlert());

        assertTrue(detector.setEnabled("text_wall", true));
        assertTrue(accept(detector, 3, 2 * SECOND, "wall", "sender", 2).newAlert());
        detector.removeSequence(3);
        assertFalse(detector.snapshot().flagged(3));
    }

    private static AutomaticRuleDetector detector(AutomaticRuleDefinition... rules) {
        return new AutomaticRuleDetector(List.of(rules), ignored -> true);
    }

    private static AutomaticRuleDetector.DetectionUpdate accept(AutomaticRuleDetector detector, long sequence,
                                                                 long at, String body, String sender, int lines) {
        return detector.accept(new AutomaticRuleDetector.Message(
                sequence, at, ChatChannel.GLOBAL, body, sender, lines));
    }
}
