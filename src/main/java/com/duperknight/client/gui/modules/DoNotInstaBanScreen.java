package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.DoNotInstaBanModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** The module deliberately has one setting: enabled or disabled. */
public final class DoNotInstaBanScreen extends DMLSMenuScreen {
    private final DoNotInstaBanModule module;
    private Component saveStatus = Component.empty();

    public DoNotInstaBanScreen(Screen parent, DoNotInstaBanModule module) {
        super(Component.translatable("dmls.module.do_not_insta_ban.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(60));
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;
        addScrollableChild(CycleButton.builder((Boolean value) -> Component.translatable(
                                value ? "dmls.option.on" : "dmls.option.off")
                        .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED), module.enabled()).withValues(true, false)
                .create(x, contentY(0), controlWidth, STANDARD_BUTTON_HEIGHT,
                        Component.translatable("dmls.module.do_not_insta_ban.toggle"), (button, value) -> {
                            if (module.setEnabled(minecraft, value)) {
                                saveStatus = Component.empty();
                            } else {
                                button.setValue(module.enabled());
                                saveStatus = Component.translatable("dmls.validation.config.save_failed");
                            }
                        }), 0);
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int statusY = contentY(scaled(32));
        if (!saveStatus.getString().isEmpty() && isContentVisible(statusY, font.lineHeight)) {
            context.centeredText(font, saveStatus, width / 2, statusY, 0xFFFF5555);
        }
        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
