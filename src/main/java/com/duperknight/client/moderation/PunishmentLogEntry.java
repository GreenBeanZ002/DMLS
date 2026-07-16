package com.duperknight.client.moderation;

import java.util.Objects;
import java.util.UUID;

public record PunishmentLogEntry(
        PunishmentType type,
        String playerName,
        UUID playerUuid,
        String staffName,
        String reason,
        String duration,
        String occurredAt,
        String expiresAt,
        PunishmentLogOrigin origin,
        String detailsUrl,
        String avatarUrl,
        long highlightFadeStartedAtMillis
) {
    public static final long HIGHLIGHT_FADE_MILLIS = 3_000L;
    private static final int FULL_HIGHLIGHT_ALPHA = 0x85;

    public PunishmentLogEntry {
        type = Objects.requireNonNull(type, "type");
        playerName = Objects.requireNonNullElse(playerName, "").trim();
        playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        staffName = Objects.requireNonNullElse(staffName, "").trim();
        reason = Objects.requireNonNullElse(reason, "").trim();
        duration = Objects.requireNonNullElse(duration, "").trim();
        occurredAt = Objects.requireNonNullElse(occurredAt, "").trim();
        expiresAt = Objects.requireNonNullElse(expiresAt, "").trim();
        origin = Objects.requireNonNull(origin, "origin");
        detailsUrl = Objects.requireNonNullElse(detailsUrl, "").trim();
        avatarUrl = Objects.requireNonNullElse(avatarUrl, "").trim();
        if (playerName.isEmpty() || staffName.isEmpty()) {
            throw new IllegalArgumentException("Punishment log entries require player and staff names");
        }
    }

    public PunishmentLogEntry(PunishmentType type, String playerName, UUID playerUuid, String staffName) {
        this(type, playerName, playerUuid, staffName, "", "", "",
                "", PunishmentLogOrigin.REALTIME, "", "", System.currentTimeMillis());
    }

    public boolean website() {
        return origin == PunishmentLogOrigin.WEBSITE;
    }

    public int highlightAlpha(long nowMillis) {
        if (origin == PunishmentLogOrigin.WEBSITE) return 0;
        if (origin == PunishmentLogOrigin.COMMAND) return FULL_HIGHLIGHT_ALPHA;
        long elapsed = Math.max(0L, nowMillis - highlightFadeStartedAtMillis);
        if (elapsed >= HIGHLIGHT_FADE_MILLIS) return 0;
        return Math.round(FULL_HIGHLIGHT_ALPHA * (1.0F - (float) elapsed / HIGHLIGHT_FADE_MILLIS));
    }

    public PunishmentLogEntry withHighlightFadeStartedAt(long startedAtMillis) {
        return new PunishmentLogEntry(type, playerName, playerUuid, staffName, reason, duration,
                occurredAt, expiresAt, origin, detailsUrl, avatarUrl, startedAtMillis);
    }

    public PunishmentLogEntry confirmedBy(PunishmentLogEntry broadcast) {
        String confirmedReason = broadcast.reason().isEmpty() ? reason : broadcast.reason();
        String confirmedDuration = broadcast.duration().isEmpty() ? duration : broadcast.duration();
        return new PunishmentLogEntry(type, playerName, playerUuid, staffName,
                confirmedReason, confirmedDuration, broadcast.occurredAt(), broadcast.expiresAt(),
                PunishmentLogOrigin.REALTIME, "", "", broadcast.highlightFadeStartedAtMillis());
    }
}
