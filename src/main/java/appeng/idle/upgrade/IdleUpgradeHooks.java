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
}
