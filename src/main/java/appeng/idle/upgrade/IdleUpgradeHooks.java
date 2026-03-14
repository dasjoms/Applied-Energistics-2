package appeng.idle.upgrade;

import appeng.idle.currency.CurrencyId;
import appeng.idle.player.PlayerIdleData;

/**
 * Hook points for idle upgrade-related multipliers.
 */
public final class IdleUpgradeHooks {
    private IdleUpgradeHooks() {
    }

    public static MultiplierBundle getOnlineGenerationMultipliers(PlayerIdleData data, CurrencyId currency) {
        var totalMultiplier = 1.0;

        for (var ownedUpgrade : data.ownedUpgradeLevelsView().entrySet()) {
            var definition = IdleUpgrades.get(ownedUpgrade.getKey());
            if (definition == null) {
                continue;
            }

            var levels = Math.min(ownedUpgrade.getValue(), definition.maxLevel());
            if (levels <= 0) {
                continue;
            }

            var levelMultiplier = definition.effects().onlineGenerationMultiplier(currency);
            if (levelMultiplier <= 0.0 || !Double.isFinite(levelMultiplier)) {
                continue;
            }

            for (var i = 0; i < levels; i++) {
                totalMultiplier *= levelMultiplier;
            }
        }

        if (totalMultiplier <= 0.0 || !Double.isFinite(totalMultiplier)) {
            return MultiplierBundle.IDENTITY;
        }

        return new MultiplierBundle(totalMultiplier);
    }

    public static double getOfflinePercentMultiplier(PlayerIdleData data) {
        var totalBonus = 0.0;

        for (var ownedUpgrade : data.ownedUpgradeLevelsView().entrySet()) {
            var definition = IdleUpgrades.get(ownedUpgrade.getKey());
            if (definition == null) {
                continue;
            }

            var levels = Math.min(ownedUpgrade.getValue(), definition.maxLevel());
            if (levels <= 0) {
                continue;
            }

            var levelBonus = definition.effects().offlinePercentBonus();
            if (!Double.isFinite(levelBonus)) {
                continue;
            }

            totalBonus += levelBonus * levels;
        }

        var multiplier = 1.0 + totalBonus;
        return multiplier > 0.0 && Double.isFinite(multiplier) ? multiplier : 1.0;
    }

    public static boolean trySpendForUpgrade(PlayerIdleData data, CostBundle upgradeCost) {
        return CurrencyTransactionService.trySpend(data, upgradeCost, SpendReason.UPGRADE_PURCHASE);
    }
}
