package com.duperknight.client.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LuckPermsResponseParserTest {
    @Test void matchesSuppliedStoneworksLinesExactly() {
        assertEquals(LuckPermsResponseParser.Result.CONFIRMED,
                LuckPermsResponseParser.parsePermissionAlreadySet("DuperKnight", "redischat.admin",
                        "[LP] DuperKnight already has redischat.admin set in context global."));
        assertEquals(LuckPermsResponseParser.Result.CONFIRMED,
                LuckPermsResponseParser.parseParentChange(LuckPermsResponseParser.Action.ADD, "DuperKnight", "helper",
                        "[LP] DuperKnight now inherits permissions from helper in context global."));
        assertEquals(LuckPermsResponseParser.Result.CONFIRMED,
                LuckPermsResponseParser.parseParentChange(LuckPermsResponseParser.Action.REMOVE, "DuperKnight", "helper",
                        "[LP] DuperKnight no longer inherits permissions from helper in context global."));
        assertEquals(LuckPermsResponseParser.Result.UNRELATED,
                LuckPermsResponseParser.parseParentChange(LuckPermsResponseParser.Action.ADD, "Other", "helper",
                        "[LP] DuperKnight now inherits permissions from helper in context global."));
    }
}
