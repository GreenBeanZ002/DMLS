package com.duperknight.client.moderation;

import com.duperknight.client.modules.StaffRank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentLogServiceTest {
    private static final String PLAYER_UUID = "685556f7e4db40068d760faeb3ad842c";

    @AfterEach
    void reset() {
        PunishmentLogService.resetForTests();
    }

    @Test
    void parsesLiteBansRowsForAllPunishmentPages() {
        for (PunishmentType type : PunishmentType.values()) {
            String html = """
                    <table>
                      <tbody>
                        <tr>
                          <td><a><div><img src='https://cravatar.eu/avatar/%s/25'/><br>Player_1</div></a></td>
                          <td><a><div><img src='inc/img/console.png'/><br>Staff_1</div></a></td>
                          <td><a>Reason &amp; details</a></td>
                        </tr>
                    </table>
                    """.formatted(PLAYER_UUID);

            PunishmentLogEntry entry = PunishmentLogService.parsePage(type, html).getFirst();
            assertEquals(type, entry.type());
            assertEquals("Player_1", entry.playerName());
            assertEquals("Staff_1", entry.staffName());
            assertEquals(UUID.fromString("685556f7-e4db-4006-8d76-0faeb3ad842c"), entry.playerUuid());
        }
    }

    @Test
    void localEntriesLeadDeduplicateAndCapTheCombinedLogAtForty() {
        PunishmentLogEntry local = entry(0, PunishmentType.BAN, "Player0", "Staff");
        List<PunishmentLogEntry> website = new ArrayList<>();
        website.add(local);
        for (int index = 1; index <= 50; index++) {
            website.add(entry(index, PunishmentType.MUTE, "Player" + index, "Staff"));
        }

        List<PunishmentLogEntry> merged = PunishmentLogService.mergeEntries(List.of(local), website);

        assertEquals(PunishmentLogService.MAX_ENTRIES, merged.size());
        assertEquals(local, merged.getFirst());
        assertEquals(1, merged.stream().filter(local::equals).count());
    }

    @Test
    void combinedPunishmentsAreSortedByDateAcrossTypes() {
        PunishmentLogEntry olderBan = datedEntry(PunishmentType.BAN, "Older", "July 14, 2026, 12:00");
        PunishmentLogEntry newestKick = datedEntry(PunishmentType.KICK, "Newest", "July 16, 2026, 12:00");
        PunishmentLogEntry middleMute = datedEntry(PunishmentType.MUTE, "Middle", "July 15, 2026, 12:00");
        PunishmentLogEntry undatedWarning = datedEntry(PunishmentType.WARNING, "Undated", "");

        List<PunishmentLogEntry> merged = PunishmentLogService.mergeEntries(
                List.of(), List.of(olderBan, undatedWarning, newestKick, middleMute));

        assertEquals(List.of("Newest", "Middle", "Older", "Undated"),
                merged.stream().map(PunishmentLogEntry::playerName).toList());
    }

    @Test
    void malformedPagesProduceNoEntries() {
        assertEquals(List.of(), PunishmentLogService.parsePage(PunishmentType.KICK, "<html>No table</html>"));
    }

    @Test
    void websiteRowsKeepDetailsAvatarAndCalculatedDuration() {
        String html = """
                <table><tbody><tr>
                  <td><a href="info.php?type=ban&id=22993"><img src="https://cravatar.eu/avatar/%s/25"><br>Player_1</a></td>
                  <td><a><br>Staff_1</a></td>
                  <td><a>Rule 14.2</a></td>
                  <td><a>July 16, 2026, 11:27</a></td>
                  <td><a>August 15, 2026, 11:27</a></td>
                </tr></table>
                """.formatted(PLAYER_UUID);

        PunishmentLogEntry entry = PunishmentLogService.parsePage(PunishmentType.BAN, html).getFirst();

        assertTrue(entry.website());
        assertEquals("Rule 14.2", entry.reason());
        assertEquals("30 days", entry.duration());
        assertEquals("July 16, 2026, 11:27", entry.occurredAt());
        assertEquals("August 15, 2026, 11:27", entry.expiresAt());
        assertEquals("https://stoneworks.gg/bans/info.php?type=ban&id=22993", entry.detailsUrl());
        assertEquals("https://cravatar.eu/avatar/" + PLAYER_UUID + "/25", entry.avatarUrl());
    }

    @Test
    void parsesSilentAndVisiblePunishmentBroadcastsIncludingIpBans() {
        assertBroadcast(PunishmentType.BAN, "styl00", "DuperKnight",
                "[Silent] DuperKnight banned styl00 for 'test'");
        assertBroadcast(PunishmentType.BAN, "styl00", "DuperKnight",
                "DuperKnight tempbanned styl00 for 1 day for 'test'");
        assertBroadcast(PunishmentType.BAN, "styl00", "DuperKnight",
                "[Silent] DuperKnight temp IP-banned styl00 for 1 day for 'test'");
        assertBroadcast(PunishmentType.BAN, "styl00", "DuperKnight",
                "[Silent] DuperKnight IP-banned styl00 for 'test'");
        assertBroadcast(PunishmentType.BAN, "styl00", "DuperKnight",
                "DuperKnight banip styl00 for 'test'");
        assertBroadcast(PunishmentType.MUTE, "styl00", "DuperKnight",
                "[Silent] DuperKnight muted styl00 for 'test'");
        assertBroadcast(PunishmentType.MUTE, "styl00", "DuperKnight",
                "[Silent] DuperKnight tempmuted styl00 for 1 day for 'test'");
        assertBroadcast(PunishmentType.KICK, "styl00", "DuperKnight",
                "[Silent] styl00 was kicked by DuperKnight for 'test'.");
        assertBroadcast(PunishmentType.WARNING, "styl00", "DuperKnight",
                "DuperKnight warned styl00 for 'test'");
        assertTrue(PunishmentLogService.parseBroadcast("Previous ban for styl00 removed.").isEmpty());

        PunishmentLogEntry temporary = PunishmentLogService.parseBroadcast(
                "[Silent] DuperKnight tempbanned styl00 for 1 day for 'test'").orElseThrow();
        assertEquals("1 day", temporary.duration());
        assertEquals("test", temporary.reason());
        assertEquals(PunishmentLogOrigin.REALTIME, temporary.origin());

        PunishmentLogEntry permanent = PunishmentLogService.parseBroadcast(
                "DuperKnight banned styl00 for 'test'").orElseThrow();
        assertEquals("Permanent", permanent.duration());
    }

    @Test
    void doubledBroadcastIsAddedOnlyOnce() {
        String line = "[Silent] DuperKnight muted styl00 for 'test'";

        assertTrue(PunishmentLogService.recordChatLine(line));
        assertFalse(PunishmentLogService.recordChatLine(line.replace("[Silent] ", "")));
        assertEquals(1, PunishmentLogService.localEntriesForTests().size());
    }

    @Test
    void distinctBanBroadcastsForTheSamePlayerRemainDistinct() {
        assertTrue(PunishmentLogService.recordChatLine(
                "[Silent] DuperKnight banned styl00 for 'test'"));
        assertTrue(PunishmentLogService.recordChatLine(
                "[Silent] DuperKnight IP-banned styl00 for 'test'"));
        assertTrue(PunishmentLogService.recordChatLine(
                "[Silent] DuperKnight tempbanned styl00 for 1 day for 'test'"));
        assertEquals(3, PunishmentLogService.localEntriesForTests().size());
    }

    @Test
    void commandAndItsBroadcastShareOneLocalEntry() {
        PunishmentLogService.shared().recordLocal(null,
                new PunishmentRequest(PunishmentType.MUTE, "styl00", "1d", "test"));

        assertFalse(PunishmentLogService.recordChatLine(
                "[Silent] You tempmuted styl00 for 1 day for 'test'"));
        assertEquals(1, PunishmentLogService.localEntriesForTests().size());
        PunishmentLogEntry confirmed = PunishmentLogService.localEntriesForTests().getFirst();
        assertEquals(PunishmentLogOrigin.REALTIME, confirmed.origin());
        assertEquals("1 day", confirmed.duration());
        assertEquals("test", confirmed.reason());
    }

    @Test
    void onlyRealtimeAndPendingCommandsAreHighlighted() {
        long now = System.currentTimeMillis();
        PunishmentLogEntry realtime = new PunishmentLogEntry(PunishmentType.BAN, "Player", UUID.randomUUID(), "Staff",
                "reason", "1 day", "", "", PunishmentLogOrigin.REALTIME, "", "", now);
        PunishmentLogEntry website = new PunishmentLogEntry(PunishmentType.BAN, "Player", UUID.randomUUID(), "Staff",
                "reason", "1 day", "", "", PunishmentLogOrigin.WEBSITE, "", "", now);
        PunishmentLogEntry command = new PunishmentLogEntry(PunishmentType.BAN, "Player", UUID.randomUUID(), "Staff",
                "reason", "1 day", "", "", PunishmentLogOrigin.COMMAND, "", "", 0L);

        assertTrue(realtime.highlightAlpha(now) > 0);
        assertEquals(0, realtime.highlightAlpha(now + PunishmentLogEntry.HIGHLIGHT_FADE_MILLIS));
        assertEquals(0, website.highlightAlpha(now));
        assertTrue(command.highlightAlpha(now + 60_000L) > 0);
    }

    @Test
    void unchangedWebsiteEntriesDoNotRestartTheirHighlightAfterRefresh() {
        long originalStart = 100L;
        PunishmentLogEntry previous = new PunishmentLogEntry(PunishmentType.BAN, "Player", UUID.randomUUID(), "Staff",
                "old", "Permanent", "date", "expires", PunishmentLogOrigin.WEBSITE,
                "https://stoneworks.gg/bans/info.php?type=ban&id=1", "avatar", originalStart);
        PunishmentLogEntry refreshed = new PunishmentLogEntry(PunishmentType.BAN, "Player", previous.playerUuid(), "Staff",
                "updated", "Permanent", "date", "expires", PunishmentLogOrigin.WEBSITE,
                previous.detailsUrl(), "avatar", 500L);

        PunishmentLogEntry result = PunishmentLogService.preserveWebsiteHighlightTimes(
                List.of(previous), List.of(refreshed)).getFirst();

        assertEquals(originalStart, result.highlightFadeStartedAtMillis());
        assertEquals("updated", result.reason());
    }

    @Test
    void onlyHelpersRefreshPeriodicallyAndTheirWebsiteEntriesAreFiltered() {
        assertTrue(PunishmentLogService.shouldPeriodicallyRefresh(StaffRank.HELPER));
        assertFalse(PunishmentLogService.shouldPeriodicallyRefresh(StaffRank.MODERATOR));
        assertFalse(PunishmentLogService.shouldPeriodicallyRefresh(StaffRank.SENIOR_MODERATOR));
        assertFalse(PunishmentLogService.shouldPeriodicallyRefresh(StaffRank.SUPPORT));
        assertFalse(PunishmentLogService.shouldPeriodicallyRefresh(StaffRank.ADMIN));
        assertEquals(300_000L, PunishmentLogService.HELPER_REFRESH_INTERVAL_MILLIS);

        List<PunishmentLogEntry> filtered = PunishmentLogService.filterWebsiteEntries(List.of(
                entry(1, PunishmentType.BAN, "Player1", "DuperKnight"),
                entry(2, PunishmentType.MUTE, "Player2", "OtherStaff")
        ), "duperknight");
        assertEquals(1, filtered.size());
        assertEquals("OtherStaff", filtered.getFirst().staffName());
    }

    private static void assertBroadcast(PunishmentType type, String player, String staff, String line) {
        PunishmentLogEntry entry = PunishmentLogService.parseBroadcast(line).orElseThrow();
        assertEquals(type, entry.type());
        assertEquals(player, entry.playerName());
        assertEquals(staff, entry.staffName());
    }

    private static PunishmentLogEntry entry(int index, PunishmentType type, String player, String staff) {
        return new PunishmentLogEntry(type, player,
                UUID.nameUUIDFromBytes(("player-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8)), staff);
    }

    private static PunishmentLogEntry datedEntry(PunishmentType type, String player, String date) {
        return new PunishmentLogEntry(type, player, UUID.randomUUID(), "Staff", "reason", "",
                date, "", PunishmentLogOrigin.WEBSITE, "", "", System.currentTimeMillis());
    }
}
