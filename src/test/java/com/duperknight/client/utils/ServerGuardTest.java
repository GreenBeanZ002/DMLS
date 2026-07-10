package com.duperknight.client.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServerGuardTest {
    @Test void officialStoneworksHostIsAllowedByDefault() {
        assertTrue(ServerGuard.isAllowed("play.stoneworks.gg", ServerGuard.DEFAULT_ALLOWED_SERVERS));
        assertTrue(ServerGuard.isAllowed("PLAY.STONEWORKS.GG:25565", ServerGuard.DEFAULT_ALLOWED_SERVERS));
    }

    @Test void supportsExactHostsPortsAndExplicitSubdomains() {
        assertTrue(ServerGuard.isAllowed("staff.example.org", List.of("staff.example.org")));
        assertTrue(ServerGuard.isAllowed("staff.example.org:25566", List.of("staff.example.org:25566")));
        assertFalse(ServerGuard.isAllowed("staff.example.org:25566", List.of("staff.example.org")));
        assertTrue(ServerGuard.isAllowed("proxy.example.org", List.of("*.example.org")));
        assertFalse(ServerGuard.isAllowed("example.org", List.of("*.example.org")));
        assertFalse(ServerGuard.isAllowed("example.org.evil.test", List.of("*.example.org")));
    }

    @Test void validatesAndNormalizesEditableRules() {
        assertEquals(java.util.Optional.of("play.stoneworks.gg"), ServerGuard.normalizeRule(" PLAY.STONEWORKS.GG. "));
        assertEquals(java.util.Optional.of("*.proxy.example.org:25566"), ServerGuard.normalizeRule("*.Proxy.Example.org:25566"));
        assertTrue(ServerGuard.normalizeRule("https://play.stoneworks.gg").isEmpty());
        assertTrue(ServerGuard.normalizeRule("stone works.gg").isEmpty());
        assertTrue(ServerGuard.normalizeRule("*.localhost").isEmpty());
        assertTrue(ServerGuard.normalizeRule("example.org:70000").isEmpty());
    }
}
