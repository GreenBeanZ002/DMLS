package com.duperknight.client.gui.widgets;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.concurrent.ThreadLocalRandom;

/** Floating dots that drift around and flee from the mouse cursor. No connecting lines. */
public final class FleeingDotsBackground {
    private static final int DOT_COUNT = 110;
    private static final double DRIFT_SPEED = 14.0;      // GUI pixels / second, ambient motion
    private static final double FLEE_RADIUS = 70.0;      // how close the mouse must be to scare a dot
    private static final double FLEE_ACCEL = 900.0;      // push strength (px/s^2) at point-blank range
    private static final double RELAX = 1.8;             // how quickly a dot settles back to its drift speed
    private static final double MAX_SPEED = 260.0;       // cap so dots don't get flung off wildly

    private static final double[] x = new double[DOT_COUNT];
    private static final double[] y = new double[DOT_COUNT];
    private static final double[] vx = new double[DOT_COUNT];
    private static final double[] vy = new double[DOT_COUNT];
    private static final double[] baseVx = new double[DOT_COUNT];
    private static final double[] baseVy = new double[DOT_COUNT];
    private static final int[] size = new int[DOT_COUNT];
    private static final int[] color = new int[DOT_COUNT];

    private static long lastNanos;
    private static int lastW, lastH;
    private static boolean initialized;

    private FleeingDotsBackground() {}

    public static void render(GuiGraphicsExtractor context, int width, int height, int mouseX, int mouseY) {
        long now = System.nanoTime();

        if (!initialized) {
            var rng = ThreadLocalRandom.current();
            for (int i = 0; i < DOT_COUNT; i++) {
                x[i] = rng.nextDouble() * width;
                y[i] = rng.nextDouble() * height;

                double angle = rng.nextDouble() * Math.PI * 2;
                double speed = DRIFT_SPEED * (0.4 + rng.nextDouble() * 0.9);
                baseVx[i] = Math.cos(angle) * speed;
                baseVy[i] = Math.sin(angle) * speed;
                vx[i] = baseVx[i];
                vy[i] = baseVy[i];

                size[i] = 1 + rng.nextInt(3);                    // 1..3 px
                int alpha = 90 + rng.nextInt(120);               // 90..209
                color[i] = (alpha << 24) | 0xFFFFFF;             // white, varying opacity
            }
            lastW = width;
            lastH = height;
            lastNanos = now;
            initialized = true;
        }

        double dt = Math.min((now - lastNanos) / 1_000_000_000.0, 0.05); // clamp lag spikes
        lastNanos = now;

        // Window resized: scale dot positions so they stay spread across the screen
        if (width != lastW || height != lastH) {
            double sx = (double) width / Math.max(1, lastW);
            double sy = (double) height / Math.max(1, lastH);
            for (int i = 0; i < DOT_COUNT; i++) {
                x[i] *= sx;
                y[i] *= sy;
            }
            lastW = width;
            lastH = height;
        }

        double relax = Math.min(1.0, RELAX * dt);

        for (int i = 0; i < DOT_COUNT; i++) {
            // Flee from the mouse
            double dx = x[i] - mouseX;
            double dy = y[i] - mouseY;
            double distSq = dx * dx + dy * dy;
            if (distSq < FLEE_RADIUS * FLEE_RADIUS) {
                double dist = Math.sqrt(distSq);
                if (dist < 0.001) { dx = 1; dy = 0; dist = 1; }  // exactly on the cursor: pick any direction
                double strength = 1.0 - dist / FLEE_RADIUS;      // 0 at the edge, 1 at the cursor
                double push = FLEE_ACCEL * strength * strength * dt;
                vx[i] += (dx / dist) * push;
                vy[i] += (dy / dist) * push;
            }

            // Ease back toward the dot's own drift velocity
            vx[i] += (baseVx[i] - vx[i]) * relax;
            vy[i] += (baseVy[i] - vy[i]) * relax;

            // Speed cap
            double speed = Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i]);
            if (speed > MAX_SPEED) {
                vx[i] *= MAX_SPEED / speed;
                vy[i] *= MAX_SPEED / speed;
            }

            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;

            // Wrap around the edges
            if (x[i] < -4) x[i] += width + 8;
            else if (x[i] > width + 4) x[i] -= width + 8;
            if (y[i] < -4) y[i] += height + 8;
            else if (y[i] > height + 4) y[i] -= height + 8;

            int px = (int) Math.round(x[i]);
            int py = (int) Math.round(y[i]);
            context.fill(px, py, px + size[i], py + size[i], color[i]);
        }
    }
}