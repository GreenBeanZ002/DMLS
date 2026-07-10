package com.duperknight.client.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UuidLookupModuleTest {
    @Test void parsesUniqueCommaOrWhitespaceSeparatedUsernames() {
        UuidLookupModule.ParsedInput parsed = UuidLookupModule.parseInput("Alice, Bob alice");
        assertEquals(UuidLookupModule.InputStatus.VALID, parsed.status());
        assertEquals(java.util.List.of("Alice", "Bob"), parsed.usernames());
    }

    @Test void rejectsInvalidOrOversizedBatches() {
        assertEquals(UuidLookupModule.InputStatus.INVALID, UuidLookupModule.parseInput("Alice bad-name").status());
        assertEquals(UuidLookupModule.InputStatus.TOO_MANY,
                UuidLookupModule.parseInput("a01 a02 a03 a04 a05 a06 a07 a08 a09 a10 a11").status());
    }
}
