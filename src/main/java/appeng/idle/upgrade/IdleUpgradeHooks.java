package appeng.idle.upgrade;

import appeng.idle.player.PlayerIdleData;

/**
 * Hook points for idle upgrade-related multipliers.
 */
public final class IdleUpgradeHooks {
    private IdleUpgradeHooks() {
    }

    public static double getOfflinePercentMultiplier(PlayerIdleData data) {
        return 1.0;
    }

    public static boolean trySpendForUpgrade(PlayerIdleData data, CostBundle upgradeCost) {
        return CurrencyTransactionService.trySpend(data, upgradeCost, SpendReason.UPGRADE_PURCHASE);
    }
}
