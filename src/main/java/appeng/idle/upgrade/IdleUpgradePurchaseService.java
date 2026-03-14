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
        var data = PlayerIdleDataManager.get(player);
        var currentLevel = definition == null ? 0 : data.ownedUpgradeLevelsView().getOrDefault(upgradeId, 0);

        var attempt = evaluatePurchase(data, definition, currentLevel);
        if (attempt.result() == PurchaseResult.UNKNOWN_UPGRADE) {
            AELog.debug("Rejected idle upgrade purchase: player=%s upgrade=%s result=%s",
                    player.getGameProfile().getName(), upgradeId, PurchaseResult.UNKNOWN_UPGRADE);
            return PurchaseResult.UNKNOWN_UPGRADE;
        }

        if (attempt.result() == PurchaseResult.MAX_LEVEL) {
            AELog.debug("Rejected idle upgrade purchase: player=%s upgrade=%s level=%s max=%s result=%s",
                    player.getGameProfile().getName(), upgradeId, currentLevel, definition.maxLevel(),
                    PurchaseResult.MAX_LEVEL);
            return PurchaseResult.MAX_LEVEL;
        }

        if (attempt.result() == PurchaseResult.INSUFFICIENT_FUNDS) {
            AELog.debug("Rejected idle upgrade purchase: player=%s upgrade=%s result=%s",
                    player.getGameProfile().getName(), upgradeId, PurchaseResult.INSUFFICIENT_FUNDS);
            return PurchaseResult.INSUFFICIENT_FUNDS;
        }

        persistPurchase(player, data, upgradeId, attempt.nextLevel());
        return PurchaseResult.SUCCESS;
    }

    static PurchaseAttempt evaluatePurchase(PlayerIdleData data, UpgradeDefinition definition, int currentLevel) {
        Objects.requireNonNull(data, "data");

        if (definition == null) {
            return PurchaseAttempt.of(PurchaseResult.UNKNOWN_UPGRADE, currentLevel);
        }

        if (currentLevel >= definition.maxLevel()) {
            return PurchaseAttempt.of(PurchaseResult.MAX_LEVEL, currentLevel);
        }

        if (!CurrencyTransactionService.trySpend(data, definition.costPerLevel(), SpendReason.UPGRADE_PURCHASE)) {
            return PurchaseAttempt.of(PurchaseResult.INSUFFICIENT_FUNDS, currentLevel);
        }

        return PurchaseAttempt.of(PurchaseResult.SUCCESS, currentLevel + 1);
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

    record PurchaseAttempt(PurchaseResult result, int nextLevel) {
        private static PurchaseAttempt of(PurchaseResult result, int nextLevel) {
            return new PurchaseAttempt(result, nextLevel);
        }
    }
}
