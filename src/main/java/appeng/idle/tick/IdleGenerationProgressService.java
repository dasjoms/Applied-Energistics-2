package appeng.idle.tick;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.server.level.ServerPlayer;

import appeng.idle.currency.CurrencyId;
import appeng.idle.player.PlayerIdleData;
import appeng.idle.player.PlayerIdleDataManager;
import appeng.idle.reward.RewardDefinition;
import appeng.idle.reward.RewardEligibilityService;

/**
 * Converts accumulated progress ticks into whole currency units while persisting remainder progress.
 */
public final class IdleGenerationProgressService {
    static final int TICKS_PER_SECOND = 20;

    private IdleGenerationProgressService() {
    }

    public static Map<CurrencyId, Long> accrueOnlineProgress(PlayerIdleData data, int intervalTicks,
            Set<CurrencyId> currencies) {
        if (intervalTicks <= 0) {
            return Map.of();
        }

        return accrueProgressTicks(data, intervalTicks, currencies);
    }

    public static Map<CurrencyId, Long> accrueOfflineProgress(PlayerIdleData data, long elapsedSeconds,
            long maxOfflineSeconds, double offlineBasePercent, double offlinePercentMultiplier,
            Set<CurrencyId> currencies) {
        if (elapsedSeconds <= 0L || maxOfflineSeconds <= 0L) {
            return Map.of();
        }
        if (offlineBasePercent <= 0.0 || offlinePercentMultiplier <= 0.0
                || !Double.isFinite(offlineBasePercent) || !Double.isFinite(offlinePercentMultiplier)) {
            return Map.of();
        }

        var cappedElapsedSeconds = Math.min(elapsedSeconds, maxOfflineSeconds);
        var progressTicks = cappedElapsedSeconds * TICKS_PER_SECOND * offlineBasePercent * offlinePercentMultiplier;
        if (progressTicks <= 0.0 || !Double.isFinite(progressTicks)) {
            return Map.of();
        }

        return accrueProgressTicks(data, progressTicks, currencies);
    }

    public static boolean grantActiveProgressTicks(ServerPlayer player, CurrencyId currency, long progressTicks,
            String source) {
        return grantActiveProgressTicks(player, currency, progressTicks, source, null);
    }

    public static boolean grantActiveProgressTicks(ServerPlayer player, CurrencyId currency, long progressTicks,
            String source, RewardDefinition rewardDefinition) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(source, "source");

        if (progressTicks <= 0L) {
            throw new IllegalArgumentException("progressTicks must be > 0.");
        }

        var eligible = rewardDefinition == null
                ? RewardEligibilityService.canReceiveActiveReward(player)
                : RewardEligibilityService.canReceiveActiveReward(player, rewardDefinition);
        if (!eligible) {
            return false;
        }

        var data = PlayerIdleDataManager.get(player);
        var generatedAmounts = accrueProgressTicks(data, progressTicks, Set.of(currency));
        PlayerIdleDataManager.save(player, data);

        return !generatedAmounts.isEmpty() && PlayerIdleDataManager.addGeneratedBalances(player, generatedAmounts,
                source);
    }

    private static Map<CurrencyId, Long> accrueProgressTicks(PlayerIdleData data, double progressTicks,
            Set<CurrencyId> currencies) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(currencies, "currencies");

        if (progressTicks <= 0.0 || currencies.isEmpty()) {
            return Map.of();
        }

        var generatedAmounts = new HashMap<CurrencyId, Long>();
        for (var currency : currencies) {
            var award = accrueCurrency(data, currency, progressTicks);
            if (award > 0L) {
                generatedAmounts.put(currency, award);
            }
        }

        return generatedAmounts;
    }

    private static long accrueCurrency(PlayerIdleData data, CurrencyId currency, double addedProgressTicks) {
        var effectiveTicksPerUnit = IdleGenerationMath.effectiveTicksPerUnit(data, currency);
        if (effectiveTicksPerUnit <= 0.0 || !Double.isFinite(effectiveTicksPerUnit)) {
            return 0L;
        }

        var totalProgress = data.getGenerationProgressTicks(currency) + addedProgressTicks;
        if (totalProgress < effectiveTicksPerUnit) {
            data.setGenerationProgressTicks(currency, clampNonNegativeLong(Math.floor(totalProgress)));
            return 0L;
        }

        var generatedUnits = Math.floor(totalProgress / effectiveTicksPerUnit);
        var wholeUnits = generatedUnits >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) generatedUnits;

        var remainder = totalProgress - (wholeUnits * effectiveTicksPerUnit);
        data.setGenerationProgressTicks(currency, clampNonNegativeLong(Math.floor(remainder)));

        return IdleGenerationMath.clampAwardByCaps(data, currency, wholeUnits);
    }

    private static long clampNonNegativeLong(double value) {
        if (value <= 0.0 || !Double.isFinite(value)) {
            return 0L;
        }

        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) value;
    }
}
