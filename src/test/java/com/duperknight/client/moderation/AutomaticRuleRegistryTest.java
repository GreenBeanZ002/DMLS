package com.duperknight.client.moderation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomaticRuleRegistryTest {
    @Test
    void bundledRulesLoadWithExpectedThresholds() {
        AutomaticRuleRegistry.LoadResult result = AutomaticRuleRegistry.loadBundled();

        assertTrue(result.successful(), result.error());
        assertEquals(2, result.definitions().size());
        SpamRuleDefinition spam = assertInstanceOf(SpamRuleDefinition.class, result.definitions().getFirst());
        assertEquals("spam", spam.id());
        assertEquals(4, spam.repeatCount());
        assertEquals(5_000_000_000L, spam.repeatWindowNanos());
        assertEquals(0.8D, spam.similarityThreshold());
        assertEquals(3, spam.burstCount());
        assertEquals(1_500_000_000L, spam.burstWindowNanos());
        TextWallRuleDefinition textWall = assertInstanceOf(
                TextWallRuleDefinition.class, result.definitions().getLast());
        assertEquals(2, textWall.minLines());
    }

    @Test
    void rejectsDuplicateIdsAndUnknownFields() {
        String duplicate = document(spamRule("spam"), spamRule("spam"));
        assertThrows(IOException.class, () -> AutomaticRuleRegistry.parse(new StringReader(duplicate)));

        String unknown = document(spamRule("spam").replace(
                "\"burstWindowMs\":1500", "\"burstWindowMs\":1500,\"surprise\":true"));
        assertThrows(IOException.class, () -> AutomaticRuleRegistry.parse(new StringReader(unknown)));
    }

    @Test
    void rejectsUnsupportedSchemaAndInvalidThresholds() {
        String unsupported = document(spamRule("spam")).replace("\"schemaVersion\":1", "\"schemaVersion\":2");
        assertThrows(IOException.class, () -> AutomaticRuleRegistry.parse(new StringReader(unsupported)));

        List<String> invalidRules = List.of(
                spamRule("spam").replace("\"repeatCount\":4", "\"repeatCount\":1"),
                spamRule("spam").replace("\"repeatWindowMs\":5000", "\"repeatWindowMs\":0"),
                spamRule("spam").replace("\"similarityThreshold\":0.8", "\"similarityThreshold\":1.1"),
                spamRule("spam").replace("\"fuzzyMinLength\":8", "\"fuzzyMinLength\":0"),
                textWallRule().replace("\"minLines\":2", "\"minLines\":1")
        );
        for (String rule : invalidRules) {
            assertThrows(IOException.class, () -> AutomaticRuleRegistry.parse(new StringReader(document(rule))));
        }
    }

    @Test
    void rejectsFieldsThatBelongToAnotherRuleType() {
        String invalid = document(textWallRule().replace("\"minLines\":2",
                "\"minLines\":2,\"repeatCount\":4"));
        assertThrows(IOException.class, () -> AutomaticRuleRegistry.parse(new StringReader(invalid)));
    }

    private static String document(String... rules) {
        return "{\"schemaVersion\":1,\"rules\":[" + String.join(",", rules) + "]}";
    }

    private static String spamRule(String id) {
        return "{\"id\":\"" + id + "\",\"type\":\"spam\"," +
                "\"labelKey\":\"dmls.test.spam\",\"defaultEnabled\":true," +
                "\"repeatCount\":4,\"repeatWindowMs\":5000,\"similarityThreshold\":0.8," +
                "\"fuzzyMinLength\":8,\"burstCount\":3,\"burstWindowMs\":1500}";
    }

    private static String textWallRule() {
        return "{\"id\":\"text_wall\",\"type\":\"text_wall\"," +
                "\"labelKey\":\"dmls.test.text_wall\",\"defaultEnabled\":true,\"minLines\":2}";
    }
}
