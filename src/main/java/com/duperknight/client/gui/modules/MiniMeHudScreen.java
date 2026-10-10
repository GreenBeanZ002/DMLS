package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.MiniMeHudModule;
import com.duperknight.client.modules.MiniMeHudPreferences;
import com.duperknight.client.utils.DMLSConfig;
import java.util.function.BiFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Settings screen for AdMinis shown on the in-game HUD. */
public final class MiniMeHudScreen extends DMLSMenuScreen {
    private final MiniMeHudModule module;
    private Component saveStatus = Component.empty();

    public MiniMeHudScreen(Screen parent, MiniMeHudModule module) {
        super(Component.translatable("dmls.module.mini_me_hud.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(190));
        MiniMeHudPreferences preferences = DMLSConfig.miniMeHudPreferences();
        addToggle(0, "dmls.module.mini_me_hud.dupey", preferences.dupeyHud(), MiniMeHudPreferences::withDupeyHud);
        addToggle(30, "dmls.module.mini_me_hud.siaffy", preferences.siaffyHud(), MiniMeHudPreferences::withSiaffyHud);
        addToggle(60, "dmls.module.mini_me_hud.beany", preferences.beanyHud(), MiniMeHudPreferences::withBeanyHud);
        addToggle(90, "dmls.module.mini_me_hud.morvy", preferences.morvyHud(), MiniMeHudPreferences::withMorvyHud);
        addToggle(120, "dmls.module.mini_me_hud.biggy", preferences.biggyHud(), MiniMeHudPreferences::withBiggyHud);
        addToggle(160, "dmls.module.mini_me_hud.chaos_mode", preferences.chaosMode(), MiniMeHudPreferences::withChaosMode);
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    private void addToggle(int offset, String labelKey, boolean initialValue,
                           BiFunction<MiniMeHudPreferences, Boolean, MiniMeHudPreferences> update) {
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;
        int scaledOffset = scaled(offset);
        addScrollableChild(CycleButton.builder((Boolean value) -> Component.translatable(
                                value ? "dmls.option.on" : "dmls.option.off")
                        .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED), initialValue).withValues(true, false)
                .create(x, contentY(scaledOffset), controlWidth, STANDARD_BUTTON_HEIGHT,
                        Component.translatable(labelKey), (button, value) -> {
                            MiniMeHudPreferences previous = DMLSConfig.miniMeHudPreferences();
                            if (!DMLSConfig.setMiniMeHudPreferences(update.apply(previous, value))) {
                                button.setValue(previousValue(previous, labelKey));
                                saveStatus = Component.translatable("dmls.validation.config.save_failed");
                            } else {
                                saveStatus = Component.empty();
                            }
                        }), scaledOffset);
    }

    private boolean previousValue(MiniMeHudPreferences preferences, String labelKey) {
        return switch (labelKey) {
            case "dmls.module.mini_me_hud.dupey" -> preferences.dupeyHud();
            case "dmls.module.mini_me_hud.siaffy" -> preferences.siaffyHud();
            case "dmls.module.mini_me_hud.beany" -> preferences.beanyHud();
            case "dmls.module.mini_me_hud.morvy" -> preferences.morvyHud();
            case "dmls.module.mini_me_hud.biggy" -> preferences.biggyHud();
            default -> preferences.chaosMode();
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int statusY = contentY(scaled(185));
        if (!saveStatus.getString().isEmpty() && isContentVisible(statusY, font.lineHeight)) {
            context.centeredText(font, saveStatus, width / 2, statusY, 0xFFFF5555);
        }
        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
