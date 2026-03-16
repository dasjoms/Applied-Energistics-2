package appeng.core.network.serverbound;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

import appeng.core.network.CustomAppEngPayload;
import appeng.core.network.ServerboundPacket;
import appeng.idle.combat.IdleCombatHandler;

/**
 * Requests the server to perform an idle unarmed punch on the currently targeted entity.
 */
public record IdlePunchRequestPacket(int targetEntityId) implements ServerboundPacket {
    public static final StreamCodec<RegistryFriendlyByteBuf, IdlePunchRequestPacket> STREAM_CODEC = StreamCodec
            .ofMember(IdlePunchRequestPacket::write, IdlePunchRequestPacket::decode);

    public static final Type<IdlePunchRequestPacket> TYPE = CustomAppEngPayload.createType("idle_punch_request");

    @Override
    public Type<IdlePunchRequestPacket> type() {
        return TYPE;
    }

    public static IdlePunchRequestPacket decode(RegistryFriendlyByteBuf data) {
        return new IdlePunchRequestPacket(data.readVarInt());
    }

    public void write(RegistryFriendlyByteBuf data) {
        data.writeVarInt(targetEntityId);
    }

    @Override
    public void handleOnServer(ServerPlayer player) {
        IdleCombatHandler.handlePunchRequest(player, targetEntityId);
    }
}
