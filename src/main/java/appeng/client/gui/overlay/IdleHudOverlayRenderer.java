package appeng.client.gui.overlay;

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

        var x = 6;
        var y = 28;
        guiGraphics.drawString(font, Component.translatable("gui.ae2.idle.hud.title"), x, y, COLOR_TITLE, true);
        y += font.lineHeight + 1;

        for (var entry : hudValues.entrySet()) {
            var definition = definitions.get(entry.getKey());
            Component currencyName = definition == null
                    ? Component.literal(entry.getKey().id().toString())
                    : Component.translatable(definition.displayNameKey());

            var value = entry.getValue();
            var balance = ReadableNumberConverter.format(Math.max(0L, value.balance()), 7);
            var gainPerSecond = ReadableNumberConverter.format(Math.max(0L, value.gainPerSecond()), 6);

            var line = Component.translatable(
                    "gui.ae2.idle.hud.line",
                    currencyName,
                    Component.literal(balance),
                    Component.translatable("gui.ae2.idle.hud.rate", gainPerSecond,
                            Component.translatable("gui.ae2.idle.hud.per_second_suffix")));

            guiGraphics.drawString(font, line, x, y, COLOR_LINE, true);
            y += font.lineHeight;
        }
    }
}
