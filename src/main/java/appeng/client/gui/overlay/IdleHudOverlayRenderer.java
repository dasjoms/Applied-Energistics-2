package appeng.client.gui.overlay;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import appeng.client.idle.IdleHudVisibility;
import appeng.core.AppEng;
import appeng.idle.currency.IdleCurrencyManager;
import appeng.idle.net.IdleCurrencyClientCache;
import appeng.idle.net.IdleCurrencyHudValue;
import appeng.util.ReadableNumberConverter;

public class IdleHudOverlayRenderer {
    private static final int COLOR_TITLE = 0xFFFFFF;
    private static final int COLOR_LINE = 0xE0E0E0;
    private static final int COLOR_BAR_BACKGROUND = 0x80404040;
    private static final int COLOR_BAR_FILL = 0xFF70FF70;
    private static final int HUD_MARGIN = 6;
    private static final int COLUMN_SPACING = 8;
    private static final int BAR_HEIGHT = 6;
    private static final int MAX_BAR_SCREEN_WIDTH_DIVISOR = 5;
    private static final boolean USE_TEXTURED_BAR_SKIN = false;

    private static final ResourceLocation BAR_SKIN_TEXTURE = AppEng.makeId("textures/guis/inscriber.png");
    private static final int BAR_TEXTURE_U = 177;
    private static final int BAR_TEXTURE_BACKGROUND_V = 68;
    private static final int BAR_TEXTURE_FILL_V = 75;
    private static final int BAR_TEXTURE_WIDTH = 14;
    private static final int BAR_TEXTURE_HEIGHT = 6;

    public void onRenderGui(RenderGuiEvent.Post event) {
        if (!IdleHudVisibility.shouldShow()) {
            return;
        }

        var hudValues = IdleCurrencyClientCache.getHudValues();
        if (hudValues.isEmpty()) {
            return;
        }

        var minecraft = Minecraft.getInstance();
        var font = minecraft.font;
        if (font == null) {
            return;
        }

        var guiGraphics = event.getGuiGraphics();
        var definitions = IdleCurrencyManager.getCurrencies();

        List<RowModel> rows = new ArrayList<>(hudValues.size());
        for (var entry : hudValues.entrySet()) {
            var definition = definitions.get(entry.getKey());
            Component currencyName = definition == null
                    ? Component.literal(entry.getKey().id().toString())
                    : Component.translatable(definition.displayNameKey());

            var value = entry.getValue();
            var balance = ReadableNumberConverter.format(Math.max(0L, value.balance()), 7);
            var progressState = getProgressState(value);

            rows.add(new RowModel(currencyName, progressState.progressFraction(), progressState.timingText(), balance));
        }

        var x = HUD_MARGIN;
        var y = 28;
        guiGraphics.drawString(font, Component.translatable("gui.ae2.idle.hud.title"), x, y, COLOR_TITLE, true);
        y += font.lineHeight + 2;

        var screenWidth = minecraft.getWindow().getGuiScaledWidth();
        var maxBarWidth = Math.max(0, screenWidth / MAX_BAR_SCREEN_WIDTH_DIVISOR);

        for (var row : rows) {
            var balanceX = x;
            guiGraphics.drawString(font, row.balanceText(), balanceX, y, COLOR_LINE, true);

            var nameX = balanceX + font.width(row.balanceText()) + COLUMN_SPACING;
            guiGraphics.drawString(font, row.displayName(), nameX, y, COLOR_LINE, true);

            var barLeft = nameX + font.width(row.displayName()) + COLUMN_SPACING;
            var barWidth = getBarWidth(screenWidth, barLeft, maxBarWidth);
            var timeX = barLeft + barWidth + COLUMN_SPACING;

            if (barWidth > 0) {
                var barTop = y + Math.max(0, (font.lineHeight - BAR_HEIGHT) / 2);
                var barBottom = barTop + BAR_HEIGHT;
                drawBarBackground(guiGraphics, barLeft, barTop, barWidth);

                var fillWidth = getFillWidth(barWidth, row.progressFraction());

                if (fillWidth > 0) {
                    drawBarFill(guiGraphics, barLeft, barTop, fillWidth);
                }
            }

            guiGraphics.drawString(font, row.timeRemainingText(), timeX, y, COLOR_LINE, true);
            y += font.lineHeight;
        }
    }

    static int getBarWidth(int screenWidth, int barLeft, int maxBarWidth) {
        var rightBound = screenWidth - HUD_MARGIN;
        return Math.max(0, Math.min(maxBarWidth, rightBound - barLeft));
    }

    static int getFillWidth(int barWidth, double progressFraction) {
        var fillWidth = (int) Math.floor(barWidth * progressFraction);
        if (fillWidth <= 0 && progressFraction > 0.0) {
            fillWidth = 1;
        }

        return Math.min(barWidth, fillWidth);
    }

    private static void drawBarBackground(GuiGraphics guiGraphics, int x, int y, int width) {
        if (USE_TEXTURED_BAR_SKIN) {
            blitBarTexture(guiGraphics, x, y, width, BAR_TEXTURE_BACKGROUND_V);
            return;
        }

        guiGraphics.fill(x, y, x + width, y + BAR_HEIGHT, COLOR_BAR_BACKGROUND);
    }

    private static void drawBarFill(GuiGraphics guiGraphics, int x, int y, int width) {
        if (USE_TEXTURED_BAR_SKIN) {
            blitBarTexture(guiGraphics, x, y, width, BAR_TEXTURE_FILL_V);
            return;
        }

        guiGraphics.fill(x, y, x + width, y + BAR_HEIGHT, COLOR_BAR_FILL);
    }

    private static void blitBarTexture(GuiGraphics guiGraphics, int x, int y, int width, int sourceV) {
        var blitted = 0;
        while (blitted < width) {
            var segmentWidth = Math.min(BAR_TEXTURE_WIDTH, width - blitted);
            guiGraphics.blit(BAR_SKIN_TEXTURE, x + blitted, y, BAR_TEXTURE_U, sourceV, segmentWidth,
                    BAR_TEXTURE_HEIGHT);
            blitted += segmentWidth;
        }
    }

    private static String formatSeconds(double seconds) {
        var safeSeconds = Math.max(0.0, seconds);
        if (Math.abs(safeSeconds - Math.rint(safeSeconds)) < 0.0001) {
            return (long) Math.rint(safeSeconds) + "s";
        }

        return String.format("%.1fs", safeSeconds);
    }

    static ProgressState getProgressState(IdleCurrencyHudValue value) {
        var ticks = Math.max(0L, value.progressTicks());
        var maxTicks = Math.max(1L, value.ticksPerUnit());
        var fraction = Math.min(1f, ticks / (float) maxTicks);

        if (isCapped(value) || hasInvalidTimingData(value)) {
            return new ProgressState(0f, "--");
        }

        var remainingTicks = Math.max(0L, maxTicks - ticks);
        return new ProgressState(fraction, formatSeconds(remainingTicks / 20.0));
    }

    private static boolean isCapped(IdleCurrencyHudValue value) {
        return !Double.isFinite(value.gainPerSecond()) || value.gainPerSecond() <= 0.0;
    }

    private static boolean hasInvalidTimingData(IdleCurrencyHudValue value) {
        return value.ticksPerUnit() <= 0L;
    }

    record ProgressState(float progressFraction, String timingText) {
    }

    private record RowModel(Component displayName, double progressFraction, String timeRemainingText,
            String balanceText) {
    }
}
