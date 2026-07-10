package com.duperknight.client.gui;

import com.duperknight.client.modules.DonorPetModule;
import com.duperknight.client.utils.ClientUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Form for giving a donor their pet permission. */
public final class DonorPetScreen extends DMLSMenuScreen {
    private final DonorPetModule module;
    private TextFieldWidget ignField;
    private String pet = DonorPetModule.pets().get(0);
    private ButtonWidget submitButton;
    private Text validationMessage = Text.empty();

    public DonorPetScreen(Screen parent, DonorPetModule module) {
        super(Text.literal("Donor Pets"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        int formWidth = Math.min(360, width - 48);
        int formX = (width - formWidth) / 2;
        ignField = addDrawableChild(new TextFieldWidget(textRenderer, formX, height / 2 - 4, formWidth, 20,
                Text.literal("Player IGN")));
        ignField.setMaxLength(16);
        ignField.setSuggestion("PlayerName");
        ignField.setChangedListener(value -> ignField.setSuggestion(value.isEmpty() ? "PlayerName" : null));
        setInitialFocus(ignField);

        addDrawableChild(CyclingButtonWidget.builder((String value) -> Text.literal(value), pet)
                .values(DonorPetModule.pets())
                .build(formX, height / 2 + 24, formWidth, 20, Text.literal("Pet"), (button, value) -> pet = value));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), height - 31, pairedButtonWidth(), 20).build());
        submitButton = addDrawableChild(ButtonWidget.builder(Text.literal("Give Pet"), button -> submit())
                .dimensions(rightPairedButtonX(), height - 31, pairedButtonWidth(), 20).build());
        submitButton.active = !ClientUtils.isNotConnected(client);
    }

    private void submit() {
        String input = ignField.getText().trim();
        if (input.isEmpty()) {
            validationMessage = Text.literal("Enter a player IGN.");
            return;
        }
        module.submit(client, input, pet);
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
        context.drawTextWithShadow(textRenderer, Text.literal("Player IGN:"), ignField.getX(), height / 2 - 20, 0xFFCCCCCC);
        if (!validationMessage.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, height - 45, 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
