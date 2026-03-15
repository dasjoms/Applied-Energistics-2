package appeng.client.gui.overlay;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import appeng.client.idle.IdleHudVisibility;
import appeng.idle.currency.IdleCurrencyManager;
import appeng.idle.net.IdleCurrencyClientCache;
import appeng.util.ReadableNumberConverter;

public class IdleHudOverlayRenderer {
    private static final int COLOR_TITLE = 0xFFFFFF;
    private static final int COLOR_LINE = 0xE0E0E0;
    private static final int COLOR_BAR_BACKGROUND = 0x80404040;
    private static final int COLOR_BAR_FILL = 0xFF70FF70;
    private static final int HUD_MARGIN = 6;
    private static final int COLUMN_SPACING = 8;
    private static final int BAR_HEIGHT = 6;

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
            var ticksPerUnit = Math.max(0L, value.ticksPerUnit());
            var progressTicks = Math.max(0L, value.progressTicks());
            var progressFraction = ticksPerUnit <= 0L ? 0.0 : (double) progressTicks / ticksPerUnit;
            var clampedProgressFraction = Math.max(0.0, Math.min(1.0, progressFraction));

            rows.add(new RowModel(currencyName, clampedProgressFraction, formatSeconds(ticksPerUnit / 20.0), balance));
        }

        var x = HUD_MARGIN;
        var y = 28;
        guiGraphics.drawString(font, Component.translatable("gui.ae2.idle.hud.title"), x, y, COLOR_TITLE, true);
        y += font.lineHeight + 2;

        var rightEdge = minecraft.getWindow().getGuiScaledWidth() - HUD_MARGIN;

        for (var row : rows) {
            guiGraphics.drawString(font, row.displayName(), x, y, COLOR_LINE, true);

            var balanceX = rightEdge - font.width(row.balanceText());
            var timeRight = balanceX - COLUMN_SPACING;
            var timeX = timeRight - font.width(row.timePerUnitText());
            var barRight = timeX - COLUMN_SPACING;
            var barLeft = x + font.width(row.displayName()) + COLUMN_SPACING;
            var barWidth = Math.max(0, barRight - barLeft);

            if (barWidth > 0) {
                var barTop = y + Math.max(0, (font.lineHeight - BAR_HEIGHT) / 2);
                var barBottom = barTop + BAR_HEIGHT;
                guiGraphics.fill(barLeft, barTop, barRight, barBottom, COLOR_BAR_BACKGROUND);

                var fillWidth = (int) Math.floor(barWidth * row.progressFraction());
                if (fillWidth <= 0 && row.progressFraction() > 0.0) {
                    fillWidth = 1;
                }

                if (fillWidth > 0) {
                    guiGraphics.fill(barLeft, barTop, barLeft + Math.min(barWidth, fillWidth), barBottom,
                            COLOR_BAR_FILL);
                }
            }

            guiGraphics.drawString(font, row.timePerUnitText(), timeX, y, COLOR_LINE, true);
            guiGraphics.drawString(font, row.balanceText(), balanceX, y, COLOR_LINE, true);
            y += font.lineHeight;
        }
    }

    private static String formatSeconds(double seconds) {
        var safeSeconds = Math.max(0.0, seconds);
        if (Math.abs(safeSeconds - Math.rint(safeSeconds)) < 0.0001) {
            return (long) Math.rint(safeSeconds) + "s";
        }

        return String.format("%.1fs", safeSeconds);
    }

    private record RowModel(Component displayName, double progressFraction, String timePerUnitText,
            String balanceText) {
    }
}
