package appeng.idle.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import appeng.core.network.ClientboundPacket;
import appeng.core.network.CustomAppEngPayload;

public record IdleCombatHudSnapshotPacket(IdleCombatHudState state) implements ClientboundPacket {

    public static final StreamCodec<RegistryFriendlyByteBuf, IdleCombatHudSnapshotPacket> STREAM_CODEC = StreamCodec
            .ofMember(IdleCombatHudSnapshotPacket::write, IdleCombatHudSnapshotPacket::decode);

    public static final Type<IdleCombatHudSnapshotPacket> TYPE = CustomAppEngPayload
            .createType("idle_combat_hud_snapshot");

    @Override
    public Type<IdleCombatHudSnapshotPacket> type() {
        return TYPE;
    }

    public static IdleCombatHudSnapshotPacket decode(RegistryFriendlyByteBuf data) {
        return new IdleCombatHudSnapshotPacket(new IdleCombatHudState(
                data.readVarLong(),
                data.readVarLong(),
                data.readVarLong(),
                data.readVarLong(),
                data.readVarLong(),
                data.readBoolean()));
    }

    public void write(RegistryFriendlyByteBuf data) {
        data.writeVarLong(state.gameTime());
        data.writeVarLong(state.mainRemainingTicks());
        data.writeVarLong(state.offRemainingTicks());
        data.writeVarLong(state.mainIntervalTicks());
        data.writeVarLong(state.offIntervalTicks());
        data.writeBoolean(state.inIdleCombatMode());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void handleOnClient(Player player) {
        IdleCurrencyClientCache.applyCombatHudSnapshot(state);
    }
}
