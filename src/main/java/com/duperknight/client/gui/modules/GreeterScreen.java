package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.GreeterModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Settings screen for the new player greeter. */
public final class GreeterScreen extends DMLSMenuScreen {
    private final GreeterModule module;

    public GreeterScreen(Screen parent, GreeterModule module) {
        super(Text.translatable("dmls.module.greeter.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(44));
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;
        addScrollableChild(CyclingButtonWidget.builder((Boolean value) -> Text.translatable(value ? "dmls.option.on" : "dmls.option.off")
                        .formatted(value ? Formatting.GREEN : Formatting.RED), module.enabled()).values(true, false)
                .build(x, contentY(0), controlWidth, STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.module.greeter.toggle"),
                        (button, value) -> module.setEnabled(client, value)), 0);
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
