package com.duperknight.client.moderation;

import com.duperknight.client.gui.DMLSMenuScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/** Read-only details for one punishment-log row. */
public final class PunishmentDetailsScreen extends DMLSMenuScreen {
    private final PunishmentLogEntry entry;

    public PunishmentDetailsScreen(Screen parent, PunishmentLogEntry entry) {
        super(Text.translatable("dmls.moderation.details.title"), parent);
        this.entry = entry;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(scaled(240), width - scaled(32));
        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions((width - buttonWidth) / 2, footerButtonY(), buttonWidth, STANDARD_BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        int panelWidth = Math.min(scaled(420), width - scaled(32));
        int panelX = (width - panelWidth) / 2;
        int availableTop = HEADER_HEIGHT + scaled(10);
        int availableBottom = height - FOOTER_TOP_OFFSET - scaled(10);
        int availableHeight = Math.max(scaled(170), availableBottom - availableTop);
        int panelHeight = Math.min(scaled(250), availableHeight);
        int panelY = availableTop + Math.max(0, (availableHeight - panelHeight) / 2);
        renderPanel(context, panelX, panelY, panelWidth, panelHeight);

        int x = panelX + scaled(12);
        int valueX = x + scaled(92);
        int y = panelY + scaled(12);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, y, 0xFFFFFFFF);
        y += scaled(24);
        drawRow(context, "dmls.moderation.details.player", entry.playerName(), x, valueX, y);
        y += scaled(22);
        drawRow(context, "dmls.moderation.details.type", entry.type().displayName().getString(), x, valueX, y);
        y += scaled(22);
        drawRow(context, "dmls.moderation.details.staff", entry.staffName(), x, valueX, y);
        y += scaled(22);
        if (!entry.duration().isEmpty()) {
            drawRow(context, "dmls.moderation.details.duration", entry.duration(), x, valueX, y);
            y += scaled(22);
        }
        if (!entry.occurredAt().isEmpty()) {
            drawRow(context, "dmls.moderation.details.date", entry.occurredAt(), x, valueX, y);
            y += scaled(22);
        }
        if (!entry.expiresAt().isEmpty()) {
            drawRow(context, "dmls.moderation.details.expires", entry.expiresAt(), x, valueX, y);
            y += scaled(22);
        }

        y += scaled(3);
        context.drawTextWithShadow(textRenderer, Text.translatable("dmls.moderation.details.reason"),
                x, y, 0xFFAAAAAA);
        y += scaled(13);
        String reason = entry.reason().isEmpty()
                ? Text.translatable("dmls.moderation.details.unknown").getString() : entry.reason();
        List<OrderedText> reasonLines = textRenderer.wrapLines(Text.literal(reason), panelWidth - scaled(24));
        for (OrderedText line : reasonLines) {
            context.drawTextWithShadow(textRenderer, line, x, y, 0xFFFFFFFF);
            y += textRenderer.fontHeight + scaled(3);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawRow(DrawContext context, String key, String value, int x, int valueX, int y) {
        context.drawTextWithShadow(textRenderer, Text.translatable(key), x, y, 0xFFAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.literal(value), valueX, y, 0xFFFFFFFF);
    }
}
