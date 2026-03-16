package appeng.idle.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import appeng.client.idle.combat.IdlePunchAnimationComponent;
import appeng.core.network.ClientboundPacket;
import appeng.core.network.CustomAppEngPayload;

public record IdlePunchSwingPacket(int attackingPlayerId, InteractionHand hand, long sequence)
        implements
            ClientboundPacket {

    public static final StreamCodec<RegistryFriendlyByteBuf, IdlePunchSwingPacket> STREAM_CODEC = StreamCodec
            .ofMember(IdlePunchSwingPacket::write, IdlePunchSwingPacket::decode);

    public static final Type<IdlePunchSwingPacket> TYPE = CustomAppEngPayload.createType("idle_punch_swing");

    @Override
    public Type<IdlePunchSwingPacket> type() {
        return TYPE;
    }

    public static IdlePunchSwingPacket decode(RegistryFriendlyByteBuf data) {
        return new IdlePunchSwingPacket(data.readVarInt(), data.readEnum(InteractionHand.class), data.readVarLong());
    }

    public void write(RegistryFriendlyByteBuf data) {
        data.writeVarInt(attackingPlayerId);
        data.writeEnum(hand);
        data.writeVarLong(sequence);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void handleOnClient(Player player) {
        if (player.getId() != attackingPlayerId) {
            return;
        }

        IdlePunchAnimationComponent.applyServerConfirmedSwing(player, hand, sequence);
    }
}
