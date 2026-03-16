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

    public static boolean isUnarmedDualPunchEnabled(PlayerIdleData data) {
        for (var ownedUpgrade : data.ownedUpgradeLevelsView().entrySet()) {
            var definition = IdleUpgrades.get(ownedUpgrade.getKey());
            if (definition == null) {
                continue;
            }

            var levels = Math.min(ownedUpgrade.getValue(), definition.maxLevel());
            if (levels <= 0) {
                continue;
            }

            if (definition.effects().enablesUnarmedDualPunch()) {
                return true;
            }
        }

        return false;
    }

    public static boolean hasCombatUpgrade(PlayerIdleData data) {
        var ownedLevels = data.ownedUpgradeLevelsView().get(IdleUpgrades.COMBAT_1.id());
        return ownedLevels != null && ownedLevels > 0;
    }

    /**
     * Computes the effective cooldown multiplier for unarmed punches.
     * <p>
     * Policy: Each owned level multiplies the running cooldown multiplier by that definition's per-level multiplier,
     * and owned levels are capped to each definition's max level. Invalid (non-finite or non-positive) per-level
     * multipliers are ignored.
     */
    public static double getUnarmedPunchCooldownMultiplier(PlayerIdleData data) {
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

            var levelMultiplier = definition.effects().unarmedPunchCooldownMultiplier();
            if (levelMultiplier <= 0.0 || !Double.isFinite(levelMultiplier)) {
                continue;
            }

            for (var i = 0; i < levels; i++) {
                totalMultiplier *= levelMultiplier;
            }
        }

        return totalMultiplier > 0.0 && Double.isFinite(totalMultiplier) ? totalMultiplier : 1.0;
    }

    public static long getUnarmedPunchIntervalTicks(PlayerIdleData data, long baseIntervalTicks) {
        if (baseIntervalTicks <= 0) {
            return 1;
        }

        var effectiveTicks = Math.floor(baseIntervalTicks * getUnarmedPunchCooldownMultiplier(data));
        if (!Double.isFinite(effectiveTicks)) {
            return baseIntervalTicks;
        }

        return Math.max(1L, (long) effectiveTicks);
    }

    public static int getTimberLogLimit(PlayerIdleData data) {
        var timberDefinition = IdleUpgrades.TIMBER_1;
        var ownedLevels = data.ownedUpgradeLevelsView().get(timberDefinition.id());
        if (ownedLevels == null || ownedLevels <= 0) {
            return 0;
        }

        var levels = Math.min(ownedLevels, timberDefinition.maxLevel());
        var perLevelLimit = timberDefinition.effects().timberLogLimitPerLevel();
        if (perLevelLimit <= 0) {
            return 0;
        }

        return perLevelLimit * levels;
    }

    public static boolean trySpendForUpgrade(PlayerIdleData data, CostBundle upgradeCost) {
        return CurrencyTransactionService.trySpend(data, upgradeCost, SpendReason.UPGRADE_PURCHASE);
    }
}
