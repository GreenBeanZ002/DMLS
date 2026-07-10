package com.duperknight.client.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrefixResponseParserTest {
    @Test void confirmsKnownStoneworksPrefixResponses() {
        assertEquals(PrefixResponseParser.Result.CONFIRMED,
                PrefixResponseParser.parse(PrefixResponseParser.Command.CREATE, "prefixid", "DuperKnight", "10",
                        "Successfully created prefix PrefixText (ID: prefixid)."));
        assertEquals(PrefixResponseParser.Result.CONFIRMED,
                PrefixResponseParser.parse(PrefixResponseParser.Command.SET_LIMIT, "prefixid", "DuperKnight", "10",
                        "Set the limit of prefixid to 10."));
        assertEquals(PrefixResponseParser.Result.CONFIRMED,
                PrefixResponseParser.parse(PrefixResponseParser.Command.SET_MANAGER, "prefixid", "DuperKnight", "10",
                        "Successfully set the manager of prefixid to DuperKnight."));
        assertEquals(PrefixResponseParser.Result.CONFIRMED,
                PrefixResponseParser.parse(PrefixResponseParser.Command.INFO, "prefixid", "DuperKnight", "10",
                        "Manager: DuperKnight (f1a1f93b-64bd-4ea5-b40b-a71a335c064b)"));
    }

    @Test void rejectsKnownManagerFailureAndIgnoresOtherOutput() {
        assertEquals(PrefixResponseParser.Result.REJECTED,
                PrefixResponseParser.parse(PrefixResponseParser.Command.SET_MANAGER, "prefixid", "DuperKnight", "10",
                        "Unable to get that player's profile."));
        assertEquals(PrefixResponseParser.Result.UNRELATED,
                PrefixResponseParser.parse(PrefixResponseParser.Command.INFO, "prefixid", "DuperKnight", "10",
                        "Crafter | Beru : none of your business buddy"));
    }
}
