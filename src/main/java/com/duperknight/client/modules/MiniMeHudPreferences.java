package com.duperknight.client.modules;

/** Persisted settings owned by the AdMinis HUD module. */
public record MiniMeHudPreferences(
        boolean dupeyHud,
        boolean siaffyHud,
        boolean beanyHud,
        boolean morvyHud,
        boolean biggyHud,
        boolean serpyHud,
        boolean doggyHud,
        boolean confyHud,
        boolean chaosMode,
        boolean snowballFights
) {
    public static MiniMeHudPreferences defaults() {
        return new MiniMeHudPreferences(false, false, false, false, false, false, false, false, false, true);
    }

    public MiniMeHudPreferences withDupeyHud(boolean enabled) {
        return new MiniMeHudPreferences(enabled, siaffyHud, beanyHud, morvyHud, biggyHud, serpyHud, doggyHud, confyHud, chaosMode, snowballFights);
    }

    public MiniMeHudPreferences withSiaffyHud(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, enabled, beanyHud, morvyHud, biggyHud, serpyHud, doggyHud, confyHud, chaosMode, snowballFights);
    }

    public MiniMeHudPreferences withBeanyHud(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, enabled, morvyHud, biggyHud, serpyHud, doggyHud, confyHud, chaosMode, snowballFights);
    }

    public MiniMeHudPreferences withMorvyHud(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, beanyHud, enabled, biggyHud, serpyHud, doggyHud, confyHud, chaosMode, snowballFights);
    }

    public MiniMeHudPreferences withBiggyHud(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, beanyHud, morvyHud, enabled, serpyHud, doggyHud, confyHud, chaosMode, snowballFights);
    }

    public MiniMeHudPreferences withSerpyHud(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, beanyHud, morvyHud, biggyHud, enabled, doggyHud, confyHud, chaosMode, snowballFights);
    }

    public MiniMeHudPreferences withDoggyHud(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, beanyHud, morvyHud, biggyHud, serpyHud, enabled, confyHud, chaosMode, snowballFights);
    }

    public MiniMeHudPreferences withConfyHud(boolean enabled)
    {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, beanyHud, morvyHud, biggyHud, serpyHud, doggyHud, enabled, chaosMode, snowballFights);
    }

    public MiniMeHudPreferences withChaosMode(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, beanyHud, morvyHud, biggyHud, serpyHud, doggyHud, confyHud, enabled, snowballFights);
    }

    public MiniMeHudPreferences withSnowballFights(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, beanyHud, morvyHud, biggyHud, serpyHud, doggyHud, confyHud, chaosMode, enabled);
    }
}