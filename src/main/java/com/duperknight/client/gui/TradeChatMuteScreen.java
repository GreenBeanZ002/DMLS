package com.duperknight.client.gui;

import com.duperknight.client.modules.TradeChatMuteModule;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Settings screen for the client-side trade chat filter. */
public final class TradeChatMuteScreen extends DMLSMenuScreen {
    private final TradeChatMuteModule module;

    public TradeChatMuteScreen(Screen parent, TradeChatMuteModule module) {
        super(Text.translatable("dmls.module.trade_chat.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(44));
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;
        addScrollableChild(CyclingButtonWidget.builder((Boolean value) -> Text.translatable(value ? "dmls.option.on" : "dmls.option.off")
                        .formatted(value ? Formatting.GREEN : Formatting.RED), DMLSConfig.tradeChatMuted()).values(true, false)
                .build(x, contentY(0), controlWidth, STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.module.trade_chat.toggle"),
                        (button, value) -> DMLSConfig.setTradeChatMuted(value)), 0);
        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        super.render(context, mouseX, mouseY, delta);
    }
}
