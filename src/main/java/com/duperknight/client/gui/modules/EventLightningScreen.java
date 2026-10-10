package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.EventLightningModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

/** Inputs for the Lightning event module (ring, line, plus or cross). */
public final class EventLightningScreen extends DMLSMenuScreen {
    private final EventLightningModule module;
    // Kept as fields so the values survive init() being re-run on window resize.
    private EventLightningModule.Shape shape = EventLightningModule.Shape.RING;
    // 12 is valid for every shape (ring radius min 5, line/plus/cross length min 10).
    private String sizeValue = "12";
    // 4 is valid for every shape (plus and cross allow at most 6 per arm).
    private String pointsValue = "4";
    private Text status = Text.empty();

    public EventLightningScreen(Screen parent, EventLightningModule module) {
        super(Text.translatable("dmls.module.event_lightning_ring.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(138));
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;

        addScrollableChild(CyclingButtonWidget.builder(
                        (EventLightningModule.Shape value) -> Text.translatable(
                                "dmls.module.event_lightning_ring.shape." + value.name().toLowerCase(Locale.ROOT)),
                        shape)
                .values(EventLightningModule.Shape.values())
                .build(x, contentY(0), controlWidth, STANDARD_BUTTON_HEIGHT,
                        Text.translatable("dmls.module.event_lightning_ring.shape"),
                        (button, value) -> shape = value), 0);

        TextFieldWidget sizeField = new TextFieldWidget(textRenderer, x, contentY(scaled(30)), controlWidth,
                STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.module.event_lightning_ring.size"));
        sizeField.setMaxLength(3);
        sizeField.setPlaceholder(Text.translatable("dmls.module.event_lightning_ring.size"));
        sizeField.setText(sizeValue);
        sizeField.setChangedListener(value -> sizeValue = value);
        addScrollableChild(sizeField, scaled(30));

        TextFieldWidget pointsField = new TextFieldWidget(textRenderer, x, contentY(scaled(60)), controlWidth,
                STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.module.event_lightning_ring.points"));
        pointsField.setMaxLength(3);
        pointsField.setPlaceholder(Text.translatable("dmls.module.event_lightning_ring.points"));
        pointsField.setText(pointsValue);
        pointsField.setChangedListener(value -> pointsValue = value);
        addScrollableChild(pointsField, scaled(60));

        addScrollableChild(ButtonWidget.builder(Text.translatable("dmls.module.event_lightning_ring.run"), button -> {
            status = resultText(module.run(client, shape, sizeValue, pointsValue));
        }).dimensions(x, contentY(scaled(90)), controlWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(90));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int statusY = contentY(scaled(122));
        if (isContentVisible(statusY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, status, width / 2, statusY, 0xFFAAAAAA);
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private Text resultText(EventLightningModule.RunResult result) {
        return switch (result) {
            case SENT -> Text.translatable("dmls.module.event_lightning_ring.status.sent")
                    .formatted(Formatting.GREEN);
            case SIMULATED -> Text.translatable("dmls.module.event_lightning_ring.status.simulated")
                    .formatted(Formatting.GRAY);
            case INVALID_RADIUS -> Text.translatable("dmls.module.event_lightning_ring.status.invalid_radius",
                            EventLightningModule.MIN_RADIUS, EventLightningModule.MAX_RADIUS)
                    .formatted(Formatting.RED);
            case INVALID_LENGTH -> Text.translatable("dmls.module.event_lightning_ring.status.invalid_length",
                            EventLightningModule.MIN_LENGTH, EventLightningModule.MAX_LENGTH)
                    .formatted(Formatting.RED);
            case INVALID_POINTS -> Text.translatable("dmls.module.event_lightning_ring.status.invalid_points",
                            EventLightningModule.MIN_POINTS, shape.maxPoints())
                    .formatted(Formatting.RED);
            case RANK_BLOCKED -> Text.translatable("dmls.module.event_lightning_ring.status.rank_blocked")
                    .formatted(Formatting.RED);
            case SERVER_BLOCKED -> Text.translatable("dmls.module.event_lightning_ring.status.server_blocked")
                    .formatted(Formatting.RED);
        };
    }
}