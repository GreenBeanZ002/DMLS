package com.duperknight.client.utils;

import com.duperknight.client.utils.TooltipUtils.TooltipLine;
import net.minecraft.client.MinecraftClient;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MenuCommandQuery {
    private final String command;
    private final String expectedTitle;
    private final int timeoutTicks;
    private final List<Integer> slotIndexes;

    private int previousSyncId = -1;
    private int waitTicks;
    private boolean started;

    public MenuCommandQuery(String command, String expectedTitle, int timeoutTicks, int... slotIndexes) {
        this.command = command;
        this.expectedTitle = expectedTitle;
        this.timeoutTicks = timeoutTicks;
        this.slotIndexes = Arrays.stream(slotIndexes).boxed().toList();
    }

    public String command() {
        return command;
    }

    public void start(MinecraftClient client) {
        previousSyncId = ScreenUtils.currentSyncId(client);
        waitTicks = 0;
        started = true;
        ClientUtils.sendCommand(client, command);
    }

    public TickResult tick(MinecraftClient client) {
        if (!started) {
            start(client);
        }

        waitTicks++;
        if (waitTicks > timeoutTicks) {
            return TickResult.timedOut();
        }

        Map<Integer, List<TooltipLine>> slotTooltips = new LinkedHashMap<>();
        String title = null;
        for (int slotIndex : slotIndexes) {
            Optional<ScreenUtils.ScreenSnapshot> snapshot = ScreenUtils.readSlot(client, slotIndex, expectedTitle, previousSyncId, waitTicks);
            if (snapshot.isEmpty()) {
                return TickResult.waiting();
            }

            title = snapshot.get().title();
            slotTooltips.put(slotIndex, snapshot.get().tooltip());
        }

        return TickResult.ready(new Result(command, title == null ? expectedTitle : title, slotTooltips));
    }

    public enum Status {
        WAITING,
        READY,
        TIMED_OUT
    }

    public record TickResult(Status status, Optional<Result> result) {
        private static TickResult waiting() {
            return new TickResult(Status.WAITING, Optional.empty());
        }

        private static TickResult timedOut() {
            return new TickResult(Status.TIMED_OUT, Optional.empty());
        }

        private static TickResult ready(Result result) {
            return new TickResult(Status.READY, Optional.of(result));
        }
    }

    public record Result(String command, String title, Map<Integer, List<TooltipLine>> slotTooltips) {
        public Optional<List<TooltipLine>> tooltip(int slotIndex) {
            return Optional.ofNullable(slotTooltips.get(slotIndex));
        }
    }
}
