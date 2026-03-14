package appeng.idle.net;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import appeng.idle.currency.CurrencyId;

final class IdleCurrencyPacketCodec {
    private IdleCurrencyPacketCodec() {
    }

    static void writeBalances(RegistryFriendlyByteBuf data, Map<CurrencyId, Long> balances) {
        data.writeVarInt(balances.size());
        for (var entry : balances.entrySet()) {
            data.writeResourceLocation(entry.getKey().id());
            data.writeVarLong(entry.getValue());
        }
    }

    static Map<CurrencyId, Long> readBalances(RegistryFriendlyByteBuf data) {
        var size = data.readVarInt();
        var balances = new LinkedHashMap<CurrencyId, Long>(Math.max(size, 0));

        for (var i = 0; i < size; i++) {
            ResourceLocation currency = data.readResourceLocation();
            var value = data.readVarLong();
            balances.put(new CurrencyId(currency), value);
        }

        return balances;
    }

    static void writeRates(RegistryFriendlyByteBuf data, Map<CurrencyId, Long> rates) {
        writeBalances(data, rates);
    }

    static Map<CurrencyId, Long> readRates(RegistryFriendlyByteBuf data) {
        return readBalances(data);
    }

    static void writeHudValues(RegistryFriendlyByteBuf data, Map<CurrencyId, IdleCurrencyHudValue> values) {
        data.writeVarInt(values.size());
        for (var entry : values.entrySet()) {
            data.writeResourceLocation(entry.getKey().id());
            var value = entry.getValue();
            data.writeVarLong(value.balance());
            data.writeVarLong(value.gainPerSecond());
            data.writeVarLong(value.progressTicks());
            data.writeVarLong(value.ticksPerUnit());
            data.writeBoolean(value.secondsToNext() != null);
            if (value.secondsToNext() != null) {
                data.writeVarLong(value.secondsToNext());
            }
        }
    }

    static Map<CurrencyId, IdleCurrencyHudValue> readHudValues(RegistryFriendlyByteBuf data) {
        var size = data.readVarInt();
        var values = new LinkedHashMap<CurrencyId, IdleCurrencyHudValue>(Math.max(size, 0));

        for (var i = 0; i < size; i++) {
            ResourceLocation currency = data.readResourceLocation();
            var balance = data.readVarLong();
            var gainPerSecond = data.readVarLong();
            var progressTicks = data.readVarLong();
            var ticksPerUnit = data.readVarLong();
            Long secondsToNext = data.readBoolean() ? data.readVarLong() : null;
            values.put(new CurrencyId(currency),
                    new IdleCurrencyHudValue(balance, gainPerSecond, progressTicks, ticksPerUnit, secondsToNext));
        }

        return values;
    }

}
