package appeng.idle.tick;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

import appeng.core.AEConfig;
import appeng.idle.currency.CurrencyId;
import appeng.idle.currency.IdleCurrencyManager;
import appeng.idle.player.GenerationContext;
import appeng.idle.player.PlayerIdleData;
import appeng.idle.player.PlayerIdleDataManager;
import appeng.idle.upgrade.IdleUpgradeHooks;

/**
 * Computes effective online generation rates for idle currencies.
 */
public final class IdleRateCalculator {
    private static final int TICKS_PER_SECOND = 20;
    private static final GenerationRule GENERATION_RULE = new BasicGenerationRule();

    private IdleRateCalculator() {
    }

    public static Map<CurrencyId, Double> calculateOnlinePerSecond(ServerPlayer player) {
        var intervalTicks = AEConfig.instance().getIdleGenerationIntervalTicks();
        var generatedPerInterval = calculateGeneratedForInterval(player, intervalTicks);
        if (generatedPerInterval.isEmpty() || intervalTicks <= 0) {
            return Map.of();
        }

        var secondsPerInterval = intervalTicks / (double) TICKS_PER_SECOND;
        if (secondsPerInterval <= 0.0 || !Double.isFinite(secondsPerInterval)) {
            return Map.of();
        }

        var rates = new HashMap<CurrencyId, Double>();
        for (var entry : generatedPerInterval.entrySet()) {
            rates.put(entry.getKey(), entry.getValue() / secondsPerInterval);
        }

        return Map.copyOf(rates);
    }

    public static Map<CurrencyId, Long> calculateGeneratedForInterval(ServerPlayer player, int intervalTicks) {
        if (intervalTicks <= 0) {
            return Map.of();
        }

        var data = PlayerIdleDataManager.get(player);
        return calculateGeneratedForInterval(player.getUUID(), data, intervalTicks, IdleGenerationTicker.currenciesToGenerate());
    }

    static Map<CurrencyId, Long> calculateGeneratedForInterval(UUID playerId, PlayerIdleData data, int intervalTicks,
            Set<CurrencyId> currencies) {
        if (intervalTicks <= 0 || currencies.isEmpty()) {
            return Map.of();
        }

        var generatedAmounts = new HashMap<CurrencyId, Long>();
        for (var currency : currencies) {
            var generatedPerTick = generatedPerTick(playerId, data, currency);
            if (generatedPerTick <= 0L) {
                continue;
            }

            var generatedThisInterval = safeMultiply(generatedPerTick, intervalTicks);
            generatedThisInterval = clampOnlineGenerationCap(currency, generatedThisInterval);
            if (generatedThisInterval <= 0L) {
                continue;
            }

            generatedAmounts.put(currency, generatedThisInterval);
        }

        return generatedAmounts;
    }

    static long generatedPerTick(UUID playerId, PlayerIdleData data, CurrencyId currency) {
        var multipliers = IdleUpgradeHooks.getOnlineGenerationMultipliers(data, currency);
        var context = new GenerationContext(playerId, true, multipliers);
        return GENERATION_RULE.generatePerTick(context, currency).units();
    }

    static long safeMultiply(long value, int multiplier) {
        if (value <= 0L || multiplier <= 0) {
            return 0L;
        }

        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }

        return value * multiplier;
    }

    static long clampOnlineGenerationCap(CurrencyId currency, long generatedAmount) {
        if (generatedAmount <= 0L) {
            return 0L;
        }

        var definition = IdleCurrencyManager.get(currency);
        var caps = definition == null ? null : definition.caps();
        var onlineGenerationCap = caps == null ? null : caps.onlineGenerationCap();

        return onlineGenerationCap == null ? generatedAmount : Math.min(generatedAmount, onlineGenerationCap);
    }
}
