package appeng.idle.upgrade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import appeng.idle.currency.CurrencyId;
import appeng.idle.player.PlayerIdleData;

class IdleUpgradePurchaseServiceTest {
    private static final CurrencyId MATTER = currency("matter");
    private static final ResourceLocation UPGRADE_ID = ResourceLocation.fromNamespaceAndPath("ae2", "test_upgrade");

    @Test
    void unknownUpgradeIsRejectedAndDoesNotChangeState() {
        var data = dataWithBalance(75L);

        var attempt = IdleUpgradePurchaseService.evaluatePurchase(data, null, 0);

        assertThat(attempt.result()).isEqualTo(IdleUpgradePurchaseService.PurchaseResult.UNKNOWN_UPGRADE);
        assertThat(attempt.nextLevel()).isEqualTo(0);
        assertThat(data.getBalance(MATTER)).isEqualTo(75L);
        assertThat(levelOf(data, UPGRADE_ID)).isZero();
    }

    @Test
    void maxLevelIsRejectedAndDoesNotChangeState() {
        var data = dataWithBalance(75L, Map.of(UPGRADE_ID, 2));
        var definition = definition(2, 25L);

        var attempt = IdleUpgradePurchaseService.evaluatePurchase(data, definition, 2);

        assertThat(attempt.result()).isEqualTo(IdleUpgradePurchaseService.PurchaseResult.MAX_LEVEL);
        assertThat(attempt.nextLevel()).isEqualTo(2);
        assertThat(data.getBalance(MATTER)).isEqualTo(75L);
        assertThat(levelOf(data, UPGRADE_ID)).isEqualTo(2);
    }

    @Test
    void insufficientFundsIsRejectedAndDoesNotChangeState() {
        var data = dataWithBalance(24L);
        var definition = definition(3, 25L);

        var attempt = IdleUpgradePurchaseService.evaluatePurchase(data, definition, 0);

        assertThat(attempt.result()).isEqualTo(IdleUpgradePurchaseService.PurchaseResult.INSUFFICIENT_FUNDS);
        assertThat(attempt.nextLevel()).isZero();
        assertThat(data.getBalance(MATTER)).isEqualTo(24L);
        assertThat(levelOf(data, UPGRADE_ID)).isZero();
    }

    @Test
    void exactCostPurchaseSucceedsAndIncrementsLevel() {
        var data = dataWithBalance(25L, Map.of(UPGRADE_ID, 1));
        var definition = definition(3, 25L);

        var attempt = IdleUpgradePurchaseService.evaluatePurchase(data, definition, 1);

        assertThat(attempt.result()).isEqualTo(IdleUpgradePurchaseService.PurchaseResult.SUCCESS);
        assertThat(attempt.nextLevel()).isEqualTo(2);
        assertThat(data.getBalance(MATTER)).isZero();
        assertThat(levelOf(data, UPGRADE_ID)).isEqualTo(1);
    }

    private static int levelOf(PlayerIdleData data, ResourceLocation upgradeId) {
        return data.ownedUpgradeLevelsView().getOrDefault(upgradeId, 0);
    }

    private static UpgradeDefinition definition(int maxLevel, long matterCost) {
        return new UpgradeDefinition(UPGRADE_ID, maxLevel, new CostBundle(Map.of(MATTER, matterCost)),
                UpgradeEffects.NO_OP);
    }

    private static CurrencyId currency(String path) {
        return new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", path));
    }

    private static PlayerIdleData dataWithBalance(long amount) {
        return dataWithBalance(amount, Map.of());
    }

    private static PlayerIdleData dataWithBalance(long amount, Map<ResourceLocation, Integer> ownedUpgradeLevels) {
        return new PlayerIdleData(
                Map.of(MATTER, amount),
                0L,
                PlayerIdleData.CURRENT_DATA_VERSION,
                ownedUpgradeLevels);
    }
}
