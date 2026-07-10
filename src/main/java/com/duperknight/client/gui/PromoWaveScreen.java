package com.duperknight.client.gui;

import com.duperknight.client.modules.PromoWaveModule;
import com.duperknight.client.utils.ClientUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Form for invoking a promotion wave. */
public final class PromoWaveScreen extends DMLSMenuScreen {
    private final PromoWaveModule module;
    private TextFieldWidget ignsField;
    private String rank = PromoWaveModule.ranks().get(0);
    private ButtonWidget submitButton;
    private Text validationMessage = Text.empty();

    public PromoWaveScreen(Screen parent, PromoWaveModule module) {
        super(Text.literal("Promo Wave"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        int formWidth = Math.min(360, width - 48);
        int formX = (width - formWidth) / 2;
        ignsField = addDrawableChild(new TextFieldWidget(textRenderer, formX, height / 2 - 4, formWidth, 20,
                Text.literal("Player IGN(s)")));
        ignsField.setMaxLength(1024);
        ignsField.setSuggestion("PlayerOne, PlayerTwo, PlayerThree");
        ignsField.setChangedListener(value -> ignsField.setSuggestion(value.isEmpty() ? "PlayerOne, PlayerTwo, PlayerThree" : null));
        setInitialFocus(ignsField);

        addDrawableChild(CyclingButtonWidget.builder((String value) -> Text.literal(value), rank)
                .values(PromoWaveModule.ranks())
                .build(formX, height / 2 + 24, formWidth, 20, Text.literal("Rank"), (button, value) -> rank = value));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), height - 31, pairedButtonWidth(), 20).build());
        submitButton = addDrawableChild(ButtonWidget.builder(Text.literal("Promote"), button -> submit())
                .dimensions(rightPairedButtonX(), height - 31, pairedButtonWidth(), 20).build());
        submitButton.active = !ClientUtils.isNotConnected(client);
    }

    private void submit() {
        String input = ignsField.getText().trim();
        if (input.isEmpty()) {
            validationMessage = Text.literal("Enter at least one player IGN.");
            return;
        }
        module.submit(client, rank, input);
        close();
    }

    @Override
    public void tick() {
        submitButton.active = !ClientUtils.isNotConnected(client);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        context.drawTextWithShadow(textRenderer, Text.literal("Player IGN(s):"), ignsField.getX(), height / 2 - 20, 0xFFCCCCCC);
        if (!validationMessage.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, height - 45, 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
