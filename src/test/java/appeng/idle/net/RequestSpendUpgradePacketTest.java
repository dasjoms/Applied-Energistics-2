package appeng.idle.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import appeng.util.CodecTestUtil;

class RequestSpendUpgradePacketTest {

    @Test
    void streamCodecRoundTripRetainsUpgradeId() {
        var original = new RequestSpendUpgradePacket(ResourceLocation.fromNamespaceAndPath("ae2", "speed"));

        CodecTestUtil.testRoundtrip(RequestSpendUpgradePacket.STREAM_CODEC, original);

        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        RequestSpendUpgradePacket.STREAM_CODEC.encode(buffer, original);
        var decoded = RequestSpendUpgradePacket.STREAM_CODEC.decode(buffer);

        assertThat(decoded.upgradeId()).isEqualTo(original.upgradeId());
    }

    @Test
    void packetCarriesOnlyUpgradeId() {
        var componentNames = Arrays.stream(RequestSpendUpgradePacket.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();

        assertThat(componentNames)
                .containsExactly("upgradeId")
                .doesNotContain("currency", "currencyId", "amount");
    }
}
