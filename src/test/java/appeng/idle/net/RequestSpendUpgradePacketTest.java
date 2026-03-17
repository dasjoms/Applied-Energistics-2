package appeng.idle.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import appeng.idle.currency.CurrencyId;
import appeng.util.CodecTestUtil;

class RequestSpendUpgradePacketTest {

    private static final CurrencyId IDLE = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "idle"));
    private static final CurrencyId MATTER = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "matter"));

    @AfterEach
    void resetClientCache() {
        IdleCurrencyClientCache.applySnapshot(Map.of(), Map.of(), false);
    }

    @Test
    void streamCodecRoundTripRetainsAllFields() {
        var original = new RequestSpendUpgradePacket(
                ResourceLocation.fromNamespaceAndPath("ae2", "speed"),
                IDLE,
                15);

        CodecTestUtil.testRoundtrip(RequestSpendUpgradePacket.STREAM_CODEC, original);

        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        RequestSpendUpgradePacket.STREAM_CODEC.encode(buffer, original);
        var decoded = RequestSpendUpgradePacket.STREAM_CODEC.decode(buffer);

        assertThat(decoded.upgradeId()).isEqualTo(original.upgradeId());
        assertThat(decoded.currencyId()).isEqualTo(original.currencyId());
        assertThat(decoded.amount()).isEqualTo(original.amount());
    }

    @Test
    void packetCarriesUpgradeIdCurrencyIdAndAmount() {
        var componentNames = Arrays.stream(RequestSpendUpgradePacket.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();

        assertThat(componentNames)
                .containsExactly("upgradeId", "currencyId", "amount")
                .doesNotContain("currency");
    }

    @Test
    void snapshotPacketCodecRoundTripRetainsBalancesAndRates() {
        var original = new IdleCurrencySnapshotPacket(
                Map.of(IDLE, 42L, MATTER, 13L),
                Map.of(IDLE, 5L, MATTER, 8L),
                true);

        CodecTestUtil.testRoundtrip(IdleCurrencySnapshotPacket.STREAM_CODEC, original);

        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        IdleCurrencySnapshotPacket.STREAM_CODEC.encode(buffer, original);
        var decoded = IdleCurrencySnapshotPacket.STREAM_CODEC.decode(buffer);

        assertThat(decoded.balances()).containsExactlyEntriesOf(original.balances());
        assertThat(decoded.rates()).containsExactlyEntriesOf(original.rates());
        assertThat(decoded.idlePunchEligible()).isTrue();
    }

    @Test
    void deltaPacketCodecRoundTripRetainsChangedBalancesAndRefreshedRates() {
        var original = new IdleCurrencyDeltaPacket(
                Map.of(IDLE, 100L, MATTER, 0L),
                Map.of(IDLE, 17L),
                false);

        CodecTestUtil.testRoundtrip(IdleCurrencyDeltaPacket.STREAM_CODEC, original);

        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        IdleCurrencyDeltaPacket.STREAM_CODEC.encode(buffer, original);
        var decoded = IdleCurrencyDeltaPacket.STREAM_CODEC.decode(buffer);

        assertThat(decoded.changedBalances()).containsExactlyEntriesOf(original.changedBalances());
        assertThat(decoded.refreshedRates()).containsExactlyEntriesOf(original.refreshedRates());
        assertThat(decoded.idlePunchEligible()).isFalse();
    }

    @Test
    void hudSnapshotCodecRoundTripRetainsProgressFields() {
        var original = new IdleCurrencyHudSnapshotPacket(Map.of(
                IDLE,
                new IdleCurrencyHudValue(100L, 4.5, 37L, 120L, 21L),
                MATTER,
                new IdleCurrencyHudValue(8L, 0.125, 5L, 200L, null)));

        CodecTestUtil.testRoundtrip(IdleCurrencyHudSnapshotPacket.STREAM_CODEC, original);

        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        IdleCurrencyHudSnapshotPacket.STREAM_CODEC.encode(buffer, original);
        var decoded = IdleCurrencyHudSnapshotPacket.STREAM_CODEC.decode(buffer);

        assertThat(decoded.values()).containsExactlyEntriesOf(original.values());
        assertThat(decoded.values().get(IDLE).gainPerSecond()).isEqualTo(4.5);
        assertThat(decoded.values().get(MATTER).gainPerSecond()).isEqualTo(0.125);
        assertThat(decoded.values().get(IDLE).progressTicks()).isEqualTo(37L);
        assertThat(decoded.values().get(IDLE).ticksPerUnit()).isEqualTo(120L);
        assertThat(decoded.values().get(IDLE).secondsToNext()).isEqualTo(21L);
        assertThat(decoded.values().get(MATTER).secondsToNext()).isNull();
    }

    @Test
    void combatHudSnapshotPacketCodecRoundTripRetainsPerHandCooldownAndGatingState() {
        var original = new IdleCombatHudSnapshotPacket(new IdleCombatHudState(1234L, 9L, 2L, 20L, 16L, true));

        CodecTestUtil.testRoundtrip(IdleCombatHudSnapshotPacket.STREAM_CODEC, original);

        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        IdleCombatHudSnapshotPacket.STREAM_CODEC.encode(buffer, original);
        var decoded = IdleCombatHudSnapshotPacket.STREAM_CODEC.decode(buffer);

        assertThat(decoded.state()).isEqualTo(original.state());
        assertThat(decoded.state().mainRemainingTicks()).isEqualTo(9L);
        assertThat(decoded.state().offRemainingTicks()).isEqualTo(2L);
        assertThat(decoded.state().inIdleCombatMode()).isTrue();
    }

}
