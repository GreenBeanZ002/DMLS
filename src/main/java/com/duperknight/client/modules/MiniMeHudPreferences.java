package com.duperknight.client.modules;

import java.util.HashSet;
import java.util.Set;

/** Persisted settings owned by the AdMinis HUD module. */
public record MiniMeHudPreferences(
        boolean dupeyHud,
        boolean siaffyHud,
        boolean beanyHud,
        boolean bubbleyHud,
        boolean morvyHud,
        boolean biggyHud,
        boolean serpyHud,
        boolean doggyHud,
        boolean confyHud,
        boolean chaosMode,
        boolean snowballFights,
        Set<String> customEnabled
) {
    public MiniMeHudPreferences {
        // Null-safe + immutable: handles old saved configs that lack this field
        customEnabled = customEnabled == null ? Set.of() : Set.copyOf(customEnabled);
    }

    public static MiniMeHudPreferences defaults() {
        return new MiniMeHudPreferences(
                false, // dupeyHud
                false, // siaffyHud
                false, // beanyHud
                false, // bubbleyHud
                false, // morvyHud
                false, // biggyHud
                false, // serpyHud
                false, // doggyHud
                false, // confyHud
                true,  // chaosMode
                false, // snowballFights
                Set.of()
        );
    }

    public MiniMeHudPreferences withDupeyHud(boolean enabled) {
        return new MiniMeHudPreferences(enabled, siaffyHud, beanyHud, bubbleyHud, morvyHud, biggyHud, serpyHud, doggyHud, confyHud, chaosMode, snowballFights, customEnabled);
    }

    public MiniMeHudPreferences withSiaffyHud(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, enabled, beanyHud, bubbleyHud, morvyHud, biggyHud, serpyHud, doggyHud, confyHud, chaosMode, snowballFights, customEnabled);
    }

    public MiniMeHudPreferences withBeanyHud(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, enabled, bubbleyHud, morvyHud, biggyHud, serpyHud, doggyHud, confyHud, chaosMode, snowballFights, customEnabled);
    }

    public MiniMeHudPreferences withBubbleyHud(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, beanyHud, enabled, morvyHud, biggyHud, serpyHud, doggyHud, confyHud, chaosMode, snowballFights, customEnabled);
    }

    public MiniMeHudPreferences withMorvyHud(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, beanyHud, bubbleyHud, enabled, biggyHud, serpyHud, doggyHud, confyHud, chaosMode, snowballFights, customEnabled);
    }

    public MiniMeHudPreferences withBiggyHud(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, beanyHud, bubbleyHud, morvyHud, enabled, serpyHud, doggyHud, confyHud, chaosMode, snowballFights, customEnabled);
    }

    public MiniMeHudPreferences withSerpyHud(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, beanyHud, bubbleyHud, morvyHud, biggyHud, enabled, doggyHud, confyHud, chaosMode, snowballFights, customEnabled);
    }

    public MiniMeHudPreferences withDoggyHud(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, beanyHud, bubbleyHud, morvyHud, biggyHud, serpyHud, enabled, confyHud, chaosMode, snowballFights, customEnabled);
    }

    public MiniMeHudPreferences withConfyHud(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, beanyHud, bubbleyHud, morvyHud, biggyHud, serpyHud, doggyHud, enabled, chaosMode, snowballFights, customEnabled);
    }

    public MiniMeHudPreferences withChaosMode(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, beanyHud, bubbleyHud, morvyHud, biggyHud, serpyHud, doggyHud, confyHud, enabled, snowballFights, customEnabled);
    }

    public MiniMeHudPreferences withSnowballFights(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, beanyHud, bubbleyHud, morvyHud, biggyHud, serpyHud, doggyHud, confyHud, chaosMode, enabled, customEnabled);
    }

    public boolean customHud(String id) {
        return customEnabled.contains(id);
    }

    public MiniMeHudPreferences withCustomHud(String id, boolean enabled) {
        Set<String> next = new HashSet<>(customEnabled);
        if (enabled) {
            next.add(id);
        } else {
            next.remove(id);
        }
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, beanyHud, bubbleyHud, morvyHud, biggyHud, serpyHud, doggyHud, confyHud, chaosMode, snowballFights, next);
    }
}