package com.duperknight.client.modules;

import com.duperknight.client.export.ContainerExportOperation;
import com.duperknight.client.export.ContainerExportResult;
import com.duperknight.client.gui.modules.ContainerExportScreen;
import com.duperknight.client.reimbursement.ContainerSelectionController;
import com.duperknight.client.reimbursement.ContainerTarget;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationStartResult;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.DMLSConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.EnderChestBlock;

import java.util.ArrayList;
import java.util.List;

/** Select containers in the world, then export everything inside them to a JSON file. */
public final class EventsContainerExportModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Container Export§8] §7";
    private static final String OPERATION_ID = "container_export";

    private final List<ContainerTarget> selected = new ArrayList<>();
    private Component lastStatus = Component.empty();
    private boolean lastStatusIsError;
    private String lastExportPath = "";

    public EventsContainerExportModule() {
        super(DepartmentRank.EVENTS_ADMIN);
    }

    @Override
    public Component displayName() {
        return Component.translatable("dmls.container_export.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.BARREL);
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.translatable("dmls.container_export.description.pick"),
                Component.translatable("dmls.container_export.description.process"));
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.EVENTS;
    }

    @Override
    public void openScreen(Minecraft client, Screen parent) {
        client.gui.setScreen(new ContainerExportScreen(parent, this));
    }

    @Override
    public void register() {
        ContainerSelectionController.register();
        ClientTickEvents.END_CLIENT_TICK.register(this::pruneSelection);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> selected.clear());
    }

    // ---- state used by the screen ---------------------------------------------------------

    public List<ContainerTarget> selected() {
        return List.copyOf(selected);
    }

    public int selectionCount() {
        return selected.size();
    }

    public void clearSelection() {
        selected.clear();
        lastStatus = Component.translatable("dmls.container_export.status.cleared");
        lastStatusIsError = false;
    }

    public Component lastStatus() {
        return lastStatus;
    }

    public boolean lastStatusIsError() {
        return lastStatusIsError;
    }

    public String lastExportPath() {
        return lastExportPath;
    }

    // ---- actions --------------------------------------------------------------------------

    public void beginSelection(Minecraft client, Screen screen) {
        ContainerSelectionController.begin(client, screen, selected, picked -> {
            selected.clear();
            selected.addAll(picked);
            lastStatus = Component.translatable(picked.size() == 1
                    ? "dmls.container_export.status.selected.one"
                    : "dmls.container_export.status.selected.many", picked.size());
            lastStatusIsError = false;
        });
    }

    public OperationStartResult startExport(Minecraft client, ContainerExportScreen source) {
        if (selected.isEmpty()) {
            return fail("dmls.validation.container_export.no_selection", OperationStartResult.INVALID);
        }
        if (client.player == null || client.level == null) {
            return fail("dmls.validation.container_export.no_world", OperationStartResult.INVALID);
        }
        if (!canRunPrivilegedOperation(client)) return OperationStartResult.SERVER_BLOCKED;
        if (!DMLSConfig.dryRun() && !client.player.getMainHandItem().isEmpty()) {
            return fail("dmls.validation.container_export.empty_hand", OperationStartResult.INVALID);
        }

        Screen returnParent = source.returnParent();
        ContainerExportOperation operation = new ContainerExportOperation(selected,
                new ContainerExportOperation.Listener() {
                    @Override
                    public void progress(int done, int total, String status) {
                        client.gui.hud.setOverlayMessage(Component.translatable(
                                "dmls.container_export.progress", status, done, total), false);
                    }

                    @Override
                    public void finished(ContainerExportResult result) {
                        // The result text is produced by the export operation itself.
                        lastStatus = Component.literal(result.message());
                        lastStatusIsError = result.isError();
                        if (!result.filePath().isBlank()) lastExportPath = result.filePath();
                        ChatUtils.sendClientMessage(client, PREFIX + result.message());
                        for (String note : result.notes()) {
                            ChatUtils.sendClientMessage(client, PREFIX + "§8- " + note);
                        }
                        client.execute(() -> client.gui.setScreen(
                                new ContainerExportScreen(returnParent, EventsContainerExportModule.this)));
                    }
                });
        OperationStartResult started = OperationCoordinator.global().start(
                client, OPERATION_ID, Component.translatable("dmls.container_export.name").getString(), operation);
        if (started != OperationStartResult.STARTED) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, switch (started) {
                case BUSY -> "dmls.chat.container_export.busy";
                case SERVER_BLOCKED -> "dmls.chat.container_export.server_blocked";
                default -> "dmls.chat.container_export.failed";
            });
        }
        return started;
    }

    private OperationStartResult fail(String translationKey, OperationStartResult result) {
        lastStatus = Component.translatable(translationKey);
        lastStatusIsError = true;
        return result;
    }

    /** Drops selected containers that have been broken or replaced since they were picked. */
    private void pruneSelection(Minecraft client) {
        if (client.level == null || selected.isEmpty()) return;
        String dimension = client.level.dimension().identifier().toString();
        selected.removeIf(target -> dimension.equals(target.dimension())
                && client.level.hasChunkAt(target.position())
                && (!(client.level.getBlockEntity(target.position()) instanceof Container)
                || client.level.getBlockState(target.position()).getBlock() instanceof EnderChestBlock));
    }
}