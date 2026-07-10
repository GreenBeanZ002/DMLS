package com.duperknight.client.parser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
class CoreProtectResponseParserTest {
 @Test void acceptsOnlyNarrowCompletionLines() { assertEquals(CoreProtectResponseParser.Result.CONFIRMED,CoreProtectResponseParser.parse("[CoreProtect] Rollback complete.")); assertEquals(CoreProtectResponseParser.Result.UNRELATED,CoreProtectResponseParser.parse("Player says rollback complete please")); assertEquals(CoreProtectResponseParser.Result.UNRELATED,CoreProtectResponseParser.parse("rollback complete for someone else")); }
}
