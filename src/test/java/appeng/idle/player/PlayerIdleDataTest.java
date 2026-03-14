package appeng.idle.player;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import appeng.idle.currency.CurrencyId;

class PlayerIdleDataTest {
    @Test
    void roundTripSerializationPreservesData() {
        var ae = ResourceLocation.fromNamespaceAndPath("ae2", "matter");
        var speedUpgrade = ResourceLocation.fromNamespaceAndPath("ae2", "speed_upgrade");

        var data = new PlayerIdleData(
                Map.of(new CurrencyId(ae), 42L),
                Map.of(new CurrencyId(ae), 11L),
                1720000000L,
                7,
                Map.of(speedUpgrade, 3),
                true);

        var restored = PlayerIdleData.fromTag(data.toTag());

        assertThat(restored.getBalance(new CurrencyId(ae))).isEqualTo(42L);
        assertThat(restored.getGenerationProgressTicks(new CurrencyId(ae))).isEqualTo(11L);
        assertThat(restored.getLastSeenEpochSeconds()).isEqualTo(1720000000L);
        assertThat(restored.getDataVersion()).isEqualTo(7);
        assertThat(restored.ownedUpgradeLevelsView()).containsEntry(speedUpgrade, 3);
        assertThat(restored.isIdleVisorUnlocked()).isTrue();
    }

    @Test
    void generationProgressMutatorsEnforceConstraints() {
        var ae = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "matter"));
        var data = new PlayerIdleData();

        data.setGenerationProgressTicks(ae, 8L);
        assertThat(data.getGenerationProgressTicks(ae)).isEqualTo(8L);

        data.setGenerationProgressTicks(ae, 0L);
        assertThat(data.generationProgressTicksView()).doesNotContainKey(ae);
        assertThat(data.getGenerationProgressTicks(ae)).isZero();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> data.setGenerationProgressTicks(ae, -1L));
    }

    @Test
    void invalidEntriesAreIgnored() {
        var brokenTag = dataWithInvalidEntries();

        var restored = PlayerIdleData.fromTag(brokenTag);

        assertThat(restored.balancesView()).isEmpty();
        assertThat(restored.generationProgressTicksView()).isEmpty();
        assertThat(restored.ownedUpgradeLevelsView()).isEmpty();
    }

    private static net.minecraft.nbt.CompoundTag dataWithInvalidEntries() {
        var root = new net.minecraft.nbt.CompoundTag();

        var badBalance = new net.minecraft.nbt.CompoundTag();
        badBalance.putString("id", "Invalid Id");
        badBalance.putLong("amount", 100L);

        var balances = new net.minecraft.nbt.ListTag();
        balances.add(badBalance);
        root.put("balances", balances);

        var badProgress = new net.minecraft.nbt.CompoundTag();
        badProgress.putString("id", "Invalid Id");
        badProgress.putLong("amount", 10L);

        var progress = new net.minecraft.nbt.ListTag();
        progress.add(badProgress);
        root.put("generationProgressTicks", progress);

        var badUpgrade = new net.minecraft.nbt.CompoundTag();
        badUpgrade.putString("id", "also invalid");
        badUpgrade.putInt("level", 2);

        var upgrades = new net.minecraft.nbt.ListTag();
        upgrades.add(badUpgrade);
        root.put("ownedUpgradeLevels", upgrades);

        return root;
    }
}
