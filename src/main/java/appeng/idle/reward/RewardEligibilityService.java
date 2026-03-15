package appeng.idle.reward;

import java.util.Objects;

import net.minecraft.server.level.ServerPlayer;

import appeng.idle.player.PlayerIdleDataManager;

/**
 * Centralized eligibility checks for active reward triggers.
 * <p>
 * Current policy requires the idle visor to be actively equipped at trigger time. Having the visor unlocked alone is
 * insufficient.
 */
public final class RewardEligibilityService {
    private RewardEligibilityService() {
    }

    /**
     * Returns whether the player can receive an active reward right now.
     * <p>
     * Current policy requires the idle visor to be actively equipped at trigger time. Having the visor unlocked alone
     * is insufficient.
     */
    public static boolean canReceiveActiveReward(ServerPlayer player, RewardDefinition reward) {
        Objects.requireNonNull(reward, "reward");

        if (!PlayerIdleDataManager.isActiveRewardEligibleNow(player)) {
            return false;
        }

        return passesUpgradeGate(player, reward);
    }

    /**
     * Returns whether the player can receive an active reward right now when no explicit reward definition is
     * available.
     */
    public static boolean canReceiveActiveReward(ServerPlayer player) {
        return PlayerIdleDataManager.isActiveRewardEligibleNow(player);
    }

    private static boolean passesUpgradeGate(ServerPlayer player, RewardDefinition reward) {
        if (reward.isUngated()) {
            return true;
        }

        var ownedUpgradeLevels = PlayerIdleDataManager.get(player).ownedUpgradeLevelsView();
        return ownedUpgradeLevels.getOrDefault(reward.upgradeGateId(), 0) > 0;
    }
}
