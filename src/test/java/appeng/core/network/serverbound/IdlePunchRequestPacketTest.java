package appeng.core.network.serverbound;

import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.netty.buffer.Unpooled;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

import appeng.idle.combat.IdleCombatHandler;
import appeng.util.CodecTestUtil;

class IdlePunchRequestPacketTest {

    @Test
    void streamCodecRoundTripRetainsTargetEntityId() {
        var original = new IdlePunchRequestPacket(42, InteractionHand.OFF_HAND);

        CodecTestUtil.testRoundtrip(IdlePunchRequestPacket.STREAM_CODEC, original);

        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        IdlePunchRequestPacket.STREAM_CODEC.encode(buffer, original);
        var decoded = IdlePunchRequestPacket.STREAM_CODEC.decode(buffer);

        org.assertj.core.api.Assertions.assertThat(decoded.targetEntityId()).isEqualTo(42);
        org.assertj.core.api.Assertions.assertThat(decoded.hand()).isEqualTo(InteractionHand.OFF_HAND);
    }

    @Test
    void handleOnServerDelegatesToIdleCombatHandler() {
        var player = mock(ServerPlayer.class);
        var packet = new IdlePunchRequestPacket(77, InteractionHand.MAIN_HAND);

        try (MockedStatic<IdleCombatHandler> combatHandler = Mockito.mockStatic(IdleCombatHandler.class)) {
            packet.handleOnServer(player);

            combatHandler.verify(() -> IdleCombatHandler.handlePunchRequest(player, 77, InteractionHand.MAIN_HAND));
        }
    }
}
