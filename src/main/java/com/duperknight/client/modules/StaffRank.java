package com.duperknight.client.modules;

public enum StaffRank {
    HELPER("§b§lHELPER", 0),
    MODERATOR("§2§lMODERATOR", 1),
    SENIOR_MODERATOR("§6§lSR MOD", 2),
    SUPPORT("§f§lSUPPORT", 3),
    ADMIN("§4§lADMIN", 4);

    private final String displayName;
    private final int level;

    StaffRank(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    public String displayName() {
        return displayName;
    }

    public int level() {
        return level;
    }

    public boolean isAtLeast(StaffRank minimumRank) {
        return level >= minimumRank.level;
    }
}
