package appeng.idle.net;

import java.util.Map;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import appeng.core.network.ClientboundPacket;
import appeng.core.network.CustomAppEngPayload;
import appeng.idle.currency.CurrencyId;

public record IdleCurrencySnapshotPacket(Map<CurrencyId, Long> balances) implements ClientboundPacket {
    public static final StreamCodec<RegistryFriendlyByteBuf, IdleCurrencySnapshotPacket> STREAM_CODEC = StreamCodec
            .ofMember(IdleCurrencySnapshotPacket::write, IdleCurrencySnapshotPacket::decode);

    public static final Type<IdleCurrencySnapshotPacket> TYPE = CustomAppEngPayload
            .createType("idle_currency_snapshot");

    @Override
    public Type<IdleCurrencySnapshotPacket> type() {
        return TYPE;
    }

    public static IdleCurrencySnapshotPacket decode(RegistryFriendlyByteBuf data) {
        return new IdleCurrencySnapshotPacket(IdleCurrencyPacketCodec.readBalances(data));
    }

    public void write(RegistryFriendlyByteBuf data) {
        IdleCurrencyPacketCodec.writeBalances(data, balances);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void handleOnClient(Player player) {
        IdleCurrencyClientCache.applySnapshot(balances);
    }
}
