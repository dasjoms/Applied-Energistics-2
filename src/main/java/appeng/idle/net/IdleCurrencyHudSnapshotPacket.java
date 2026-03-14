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

public record IdleCurrencyHudSnapshotPacket(Map<CurrencyId, IdleCurrencyHudValue> values)
        implements ClientboundPacket {
    public static final StreamCodec<RegistryFriendlyByteBuf, IdleCurrencyHudSnapshotPacket> STREAM_CODEC = StreamCodec
            .ofMember(IdleCurrencyHudSnapshotPacket::write, IdleCurrencyHudSnapshotPacket::decode);

    public static final Type<IdleCurrencyHudSnapshotPacket> TYPE = CustomAppEngPayload
            .createType("idle_currency_hud_snapshot");

    @Override
    public Type<IdleCurrencyHudSnapshotPacket> type() {
        return TYPE;
    }

    public static IdleCurrencyHudSnapshotPacket decode(RegistryFriendlyByteBuf data) {
        return new IdleCurrencyHudSnapshotPacket(IdleCurrencyPacketCodec.readHudValues(data));
    }

    public void write(RegistryFriendlyByteBuf data) {
        IdleCurrencyPacketCodec.writeHudValues(data, values);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void handleOnClient(Player player) {
        IdleCurrencyClientCache.applyHudSnapshot(values);
    }
}
