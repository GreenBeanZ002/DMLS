package com.duperknight.client.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BalanceResponseParserTest {
    @Test void parsesKnownBalanceAndMissingPlayerResponses() {
        assertEquals(BalanceResponseParser.Result.CONFIRMED,
                BalanceResponseParser.parse("DuperKnight", "DuperKnight has 200618.07 Coins !"));
        assertEquals(BalanceResponseParser.Result.REJECTED,
                BalanceResponseParser.parse("DuperKnight", "Player not found!"));
        assertEquals(BalanceResponseParser.Result.UNRELATED,
                BalanceResponseParser.parse("DuperKnight", "AnotherPlayer has 10 Coins !"));
    }
}
