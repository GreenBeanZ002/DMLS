package com.duperknight.client.utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
class ChatNormalizerTest {
 @Test void handlesCaseAccentsLeetAndSpacing(){assertEquals("alert",ChatNormalizer.normalize(" À l 3 r 7 "));}
 @Test void collapsesStretchedLetters(){assertEquals("alert",ChatNormalizer.collapseRepeats(ChatNormalizer.normalize("aaallleeert")));}
}
