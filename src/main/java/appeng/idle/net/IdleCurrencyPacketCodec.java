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
}
