package appeng.idle.upgrade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import appeng.idle.currency.IdleCurrencies;
import appeng.idle.player.PlayerIdleData;

class IdleUpgradeHooksTest {
    @Test
    void offlinePercentMultiplierStacksFromOwnedLevels() {
        var data = new PlayerIdleData(
                Map.of(),
                0L,
                PlayerIdleData.CURRENT_DATA_VERSION,
                Map.of(IdleUpgrades.OFFLINE_EFFICIENCY_1.id(), 3));

        var multiplier = IdleUpgradeHooks.getOfflinePercentMultiplier(data);

        assertThat(multiplier).isEqualTo(1.3);
    }

    @Test
    void onlineMultiplierDefaultsToIdentityForNoopAndUnknownUpgrades() {
        var unknown = ResourceLocation.fromNamespaceAndPath("ae2", "unknown_upgrade");
        var data = new PlayerIdleData(
                Map.of(),
                0L,
                PlayerIdleData.CURRENT_DATA_VERSION,
                Map.of(IdleUpgrades.NO_OP.id(), 1, unknown, 50));

        var multiplier = IdleUpgradeHooks.getOnlineGenerationMultipliers(data, IdleCurrencies.IDLE);

        assertThat(multiplier.totalMultiplier()).isEqualTo(1.0);
    }

    @Test
    void offlineMultiplierCapsAtDefinitionMaxLevel() {
        var data = new PlayerIdleData(
                Map.of(),
                0L,
                PlayerIdleData.CURRENT_DATA_VERSION,
                Map.of(IdleUpgrades.OFFLINE_EFFICIENCY_1.id(), 1000));

        var multiplier = IdleUpgradeHooks.getOfflinePercentMultiplier(data);

        assertThat(multiplier).isEqualTo(1.5);
    }
}
