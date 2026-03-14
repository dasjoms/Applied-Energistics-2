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

public record IdleCurrencyDeltaPacket(Map<CurrencyId, Long> changedBalances) implements ClientboundPacket {
    public static final StreamCodec<RegistryFriendlyByteBuf, IdleCurrencyDeltaPacket> STREAM_CODEC = StreamCodec
            .ofMember(IdleCurrencyDeltaPacket::write, IdleCurrencyDeltaPacket::decode);

    public static final Type<IdleCurrencyDeltaPacket> TYPE = CustomAppEngPayload.createType("idle_currency_delta");

    @Override
    public Type<IdleCurrencyDeltaPacket> type() {
        return TYPE;
    }

    public static IdleCurrencyDeltaPacket decode(RegistryFriendlyByteBuf data) {
        return new IdleCurrencyDeltaPacket(IdleCurrencyPacketCodec.readBalances(data));
    }

    public void write(RegistryFriendlyByteBuf data) {
        IdleCurrencyPacketCodec.writeBalances(data, changedBalances);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void handleOnClient(Player player) {
        IdleCurrencyClientCache.applyDelta(changedBalances);
    }
}
