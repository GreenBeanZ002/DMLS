package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.EventsContainerExportModule;
import com.duperknight.client.reimbursement.ContainerTarget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Select containers in the world and export their contents to JSON. */
public final class ContainerExportScreen extends DMLSMenuScreen {
    private static final int MAX_LISTED = 6;

    private final EventsContainerExportModule module;
    private Button selectButton;
    private Button clearButton;
    private Button exportButton;
    private Button copyButton;

    public ContainerExportScreen(Screen parent, EventsContainerExportModule module) {
        super(module.displayName(), parent);
        this.module = module;
    }

    public Screen returnParent() {
        return parent;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(260));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;

        selectButton = addScrollableChild(Button.builder(selectLabel(),
                        button -> module.beginSelection(minecraft, this))
                .bounds(formX, contentY(scaled(14)), formWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(14));
        clearButton = addScrollableChild(Button.builder(Component.translatable("dmls.container_export.button.clear"), button -> {
                    module.clearSelection();
                    refreshButtons();
                })
                .bounds(formX, contentY(scaled(42)), formWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(42));
        exportButton = addScrollableChild(Button.builder(exportLabel(), button -> module.startExport(minecraft, this))
                .bounds(formX, contentY(scaled(70)), formWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(70));
        copyButton = addScrollableChild(Button.builder(Component.translatable("dmls.container_export.button.copy_path"), button -> {
                    if (!module.lastExportPath().isBlank()) {
                        minecraft.keyboardHandler.setClipboard(module.lastExportPath());
                    }
                })
                .bounds(formX, contentY(scaled(98)), formWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(98));

        addRenderableWidget(Button.builder(Component.translatable("dmls.container_export.button.back"), button -> minecraft.gui.setScreen(parent))
                .bounds(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
        refreshButtons();
    }

    @Override
    public void tick() {
        refreshButtons();
    }

    private void refreshButtons() {
        if (selectButton == null) return;
        selectButton.setMessage(selectLabel());
        exportButton.setMessage(exportLabel());
        clearButton.active = module.selectionCount() > 0;
        exportButton.active = module.selectionCount() > 0;
        copyButton.active = !module.lastExportPath().isBlank();
    }

    private Component selectLabel() {
        int count = module.selectionCount();
        return count == 0
                ? Component.translatable("dmls.container_export.button.select")
                : Component.translatable("dmls.container_export.button.select.count", count);
    }

    private Component exportLabel() {
        int count = module.selectionCount();
        if (count == 0) return Component.translatable("dmls.container_export.button.export");
        return Component.translatable(count == 1
                ? "dmls.container_export.button.export.one"
                : "dmls.container_export.button.export.many", count);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);

        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        int y = contentY(scaled(132));

        List<ContainerTarget> selected = module.selected();
        if (!selected.isEmpty()) {
            if (isContentVisible(y, font.lineHeight)) {
                context.text(font, Component.translatable("dmls.container_export.selected.header"), formX, y, 0xFFCCCCCC);
            }
            y += font.lineHeight + 2;
            for (int index = 0; index < Math.min(MAX_LISTED, selected.size()); index++) {
                if (isContentVisible(y, font.lineHeight)) {
                    context.text(font, Component.literal(selected.get(index).displayCoordinates()),
                            formX + 6, y, 0xFFAAAAAA);
                }
                y += font.lineHeight + 1;
            }
            if (selected.size() > MAX_LISTED) {
                if (isContentVisible(y, font.lineHeight)) {
                    context.text(font, Component.translatable("dmls.container_export.selected.more", selected.size() - MAX_LISTED),
                            formX + 6, y, 0xFFAAAAAA);
                }
                y += font.lineHeight + 1;
            }
            y += 6;
        } else if (isContentVisible(y, font.lineHeight)) {
            context.text(font, Component.translatable("dmls.container_export.selected.none"), formX, y, 0xFFAAAAAA);
            y += font.lineHeight + 6;
        }

        Component status = module.lastStatus();
        if (!status.getString().isBlank()) {
            int color = module.lastStatusIsError() ? 0xFFFF5555 : 0xFF55FF55;
            for (FormattedCharSequence line : font.split(status, formWidth)) {
                if (isContentVisible(y, font.lineHeight)) context.text(font, line, formX, y, color);
                y += font.lineHeight + 2;
            }
        }

        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}