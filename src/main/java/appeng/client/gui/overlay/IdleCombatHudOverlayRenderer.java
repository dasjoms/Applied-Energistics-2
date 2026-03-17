package appeng.client.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import appeng.client.idle.IdleHudVisibility;
import appeng.idle.net.IdleCombatHudState;
import appeng.idle.net.IdleCurrencyClientCache;

public class IdleCombatHudOverlayRenderer {
    private static final int BAR_WIDTH = 4;
    private static final int BAR_HEIGHT = 20;
    private static final int BAR_HORIZONTAL_OFFSET = 2;
    private static final int BAR_FRAME_COLOR = 0xA0101010;
    private static final int BAR_BACKGROUND_COLOR = 0x60303030;
    private static final int BAR_READY_COLOR = 0xFF58FF58;
    private static final int BAR_COOLDOWN_COLOR = 0xFFFF5050;

    public void onRenderGui(RenderGuiEvent.Post event) {
        if (!IdleHudVisibility.shouldShow()) {
            return;
        }

        var snapshot = IdleCurrencyClientCache.getCombatHudSnapshot();
        if (snapshot == null || snapshot == IdleCombatHudState.EMPTY || !snapshot.inIdleCombatMode()) {
            return;
        }

        var minecraft = Minecraft.getInstance();
        var screenWidth = minecraft.getWindow().getGuiScaledWidth();
        var screenHeight = minecraft.getWindow().getGuiScaledHeight();

        var anchor = getHotbarAnchor(screenWidth, screenHeight);
        var barBottom = anchor.hotbarY() + 22;
        var barTop = barBottom - BAR_HEIGHT;

        var leftBarX = getLeftBarX(anchor);
        var rightBarX = getRightBarX(anchor);

        var guiGraphics = event.getGuiGraphics();
        drawHandBar(guiGraphics, leftBarX, barTop, snapshot.mainRemainingTicks(), snapshot.mainIntervalTicks());
        drawHandBar(guiGraphics, rightBarX, barTop, snapshot.offRemainingTicks(), snapshot.offIntervalTicks());
    }

    private static void drawHandBar(GuiGraphics guiGraphics, int x, int top, long remainingTicks, long intervalTicks) {
        guiGraphics.fill(x - 1, top - 1, x + BAR_WIDTH + 1, top + BAR_HEIGHT + 1, BAR_FRAME_COLOR);
        guiGraphics.fill(x, top, x + BAR_WIDTH, top + BAR_HEIGHT, BAR_BACKGROUND_COLOR);

        var fillFraction = getFillFraction(remainingTicks, intervalTicks);
        var fillHeight = getFillHeight(BAR_HEIGHT, fillFraction);
        if (fillHeight <= 0) {
            return;
        }

        var fillTop = top + BAR_HEIGHT - fillHeight;
        var fillColor = getFillColor(remainingTicks);
        guiGraphics.fill(x, fillTop, x + BAR_WIDTH, top + BAR_HEIGHT, fillColor);
    }

    static int getLeftBarX(HotbarAnchor anchor) {
        return anchor.hotbarLeft() - BAR_HORIZONTAL_OFFSET - BAR_WIDTH;
    }

    static int getRightBarX(HotbarAnchor anchor) {
        return anchor.hotbarRight() + BAR_HORIZONTAL_OFFSET;
    }

    static int getFillColor(long remainingTicks) {
        return remainingTicks <= 0 ? BAR_READY_COLOR : BAR_COOLDOWN_COLOR;
    }

    static HotbarAnchor getHotbarAnchor(int screenWidth, int screenHeight) {
        return new HotbarAnchor(screenWidth / 2 - 91, screenWidth / 2 + 91, screenHeight - 22);
    }

    static double getFillFraction(long remainingTicks, long intervalTicks) {
        if (remainingTicks <= 0) {
            return 1.0;
        }

        if (intervalTicks <= 0) {
            return 0.0;
        }

        return clamp01(remainingTicks / (double) intervalTicks);
    }

    static int getFillHeight(int barHeight, double fillFraction) {
        var clampedFraction = clamp01(fillFraction);
        var fillHeight = (int) Math.floor(barHeight * clampedFraction);
        if (fillHeight <= 0 && clampedFraction > 0.0) {
            fillHeight = 1;
        }

        return Math.min(barHeight, fillHeight);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    record HotbarAnchor(int hotbarLeft, int hotbarRight, int hotbarY) {
    }
}
