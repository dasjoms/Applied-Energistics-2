package appeng.idle.upgrade;

import java.util.Objects;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import appeng.core.AELog;
import appeng.idle.player.PlayerIdleData;
import appeng.idle.player.PlayerIdleDataManager;

/**
 * Server-authoritative idle upgrade purchasing.
 */
public final class IdleUpgradePurchaseService {
    private IdleUpgradePurchaseService() {
    }

    public static PurchaseResult tryPurchase(ServerPlayer player, ResourceLocation upgradeId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(upgradeId, "upgradeId");

        var definition = IdleUpgrades.get(upgradeId);
        if (definition == null) {
            AELog.debug("Rejected idle upgrade purchase: player=%s upgrade=%s result=%s",
                    player.getGameProfile().getName(), upgradeId, PurchaseResult.UNKNOWN_UPGRADE);
            return PurchaseResult.UNKNOWN_UPGRADE;
        }

        var data = PlayerIdleDataManager.get(player);
        var currentLevel = data.ownedUpgradeLevelsView().getOrDefault(upgradeId, 0);
        if (currentLevel >= definition.maxLevel()) {
            AELog.debug("Rejected idle upgrade purchase: player=%s upgrade=%s level=%s max=%s result=%s",
                    player.getGameProfile().getName(), upgradeId, currentLevel, definition.maxLevel(),
                    PurchaseResult.MAX_LEVEL);
            return PurchaseResult.MAX_LEVEL;
        }

        if (!CurrencyTransactionService.trySpend(data, definition.costPerLevel(), SpendReason.UPGRADE_PURCHASE)) {
            AELog.debug("Rejected idle upgrade purchase: player=%s upgrade=%s result=%s",
                    player.getGameProfile().getName(), upgradeId, PurchaseResult.INSUFFICIENT_FUNDS);
            return PurchaseResult.INSUFFICIENT_FUNDS;
        }

        persistPurchase(player, data, upgradeId, currentLevel + 1);
        return PurchaseResult.SUCCESS;
    }

    private static void persistPurchase(ServerPlayer player, PlayerIdleData data, ResourceLocation upgradeId, int level) {
        PlayerIdleDataManager.save(player, data);
        PlayerIdleDataManager.setUpgradeLevel(player, upgradeId, level);
    }

    public enum PurchaseResult {
        SUCCESS,
        UNKNOWN_UPGRADE,
        MAX_LEVEL,
        INSUFFICIENT_FUNDS
    }
}
