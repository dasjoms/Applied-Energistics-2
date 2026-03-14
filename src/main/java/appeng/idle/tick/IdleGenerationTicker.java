package appeng.idle.tick;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import appeng.core.AEConfig;
import appeng.idle.currency.CurrencyId;
import appeng.idle.currency.IdleCurrencies;
import appeng.idle.currency.IdleCurrencyManager;
import appeng.idle.player.GenerationContext;
import appeng.idle.player.PlayerIdleDataManager;
import appeng.idle.upgrade.IdleUpgradeHooks;

/**
 * Applies periodic idle generation accrual for online players.
 */
public final class IdleGenerationTicker {
    private static final CurrencyId DEFAULT_CURRENCY = IdleCurrencies.IDLE;
    private static final String REASON_ONLINE_TICK = "ONLINE_TICK";
    private static final String REASON_OFFLINE_CATCHUP = "OFFLINE_CATCHUP";
    private static final int TICKS_PER_SECOND = 20;
    private static final GenerationRule GENERATION_RULE = new BasicGenerationRule();

    private IdleGenerationTicker() {
    }

    public static void onServerTickEnd(ServerTickEvent.Post event) {
        var interval = AEConfig.instance().getIdleGenerationIntervalTicks();
        if (interval <= 0 || event.getServer().getTickCount() % interval != 0) {
            return;
        }

        for (var player : event.getServer().getPlayerList().getPlayers()) {
            accrueForPlayer(player, interval);
        }
    }

    private static void accrueForPlayer(ServerPlayer player, int intervalTicks) {
        var data = PlayerIdleDataManager.get(player);

        var currencies = currenciesToGenerate();
        var generatedAmounts = new HashMap<CurrencyId, Long>();

        for (var currency : currencies) {
            var multipliers = IdleUpgradeHooks.getOnlineGenerationMultipliers(data, currency);
            var context = new GenerationContext(player.getUUID(), true, multipliers);
            var generatedPerTick = GENERATION_RULE.generatePerTick(context, currency).units();
            if (generatedPerTick <= 0L) {
                continue;
            }

            var generatedThisInterval = safeMultiply(generatedPerTick, intervalTicks);
            // Clamp order starts here: generation cap -> addition -> balance cap.
            generatedThisInterval = clampOnlineGenerationCap(currency, generatedThisInterval);
            if (generatedThisInterval <= 0L) {
                continue;
            }

            generatedAmounts.put(currency, generatedThisInterval);
        }

        PlayerIdleDataManager.addGeneratedBalances(player, generatedAmounts, REASON_ONLINE_TICK);
    }

    public static void accrueOfflineCatchup(ServerPlayer player, long elapsedSeconds) {
        if (elapsedSeconds <= 0L) {
            return;
        }

        var data = PlayerIdleDataManager.get(player);

        var offlineBasePercent = AEConfig.instance().getIdleOfflineBasePercent();
        var offlinePercentMultiplier = IdleUpgradeHooks.getOfflinePercentMultiplier(data);
        var maxOfflineSeconds = AEConfig.instance().getIdleOfflineMaxSeconds();

        var currencies = currenciesToGenerate();
        var generatedAmounts = new HashMap<CurrencyId, Long>();

        for (var currency : currencies) {
            var multipliers = IdleUpgradeHooks.getOnlineGenerationMultipliers(data, currency);
            var context = new GenerationContext(player.getUUID(), true, multipliers);
            var generatedPerTick = GENERATION_RULE.generatePerTick(context, currency).units();
            if (generatedPerTick <= 0L) {
                continue;
            }

            var generatedOffline = computeOfflineGenerated(generatedPerTick, elapsedSeconds, maxOfflineSeconds,
                    offlineBasePercent, offlinePercentMultiplier);
            // Clamp order starts here: generation cap -> addition -> balance cap.
            generatedOffline = clampOnlineGenerationCap(currency, generatedOffline);
            if (generatedOffline <= 0L) {
                continue;
            }

            generatedAmounts.put(currency, generatedOffline);
        }

        PlayerIdleDataManager.addGeneratedBalances(player, generatedAmounts, REASON_OFFLINE_CATCHUP);
    }

    static Set<CurrencyId> currenciesToGenerate() {
        var currencies = new HashSet<>(IdleCurrencyManager.getCurrencies().keySet());
        if (currencies.isEmpty()) {
            currencies.add(DEFAULT_CURRENCY);
        }
        return currencies;
    }

    private static long safeMultiply(long value, int multiplier) {
        if (value <= 0L || multiplier <= 0) {
            return 0L;
        }

        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }

        return value * multiplier;
    }

    private static long clampOnlineGenerationCap(CurrencyId currency, long generatedAmount) {
        if (generatedAmount <= 0L) {
            return 0L;
        }

        var definition = IdleCurrencyManager.get(currency);
        var caps = definition == null ? null : definition.caps();
        var onlineGenerationCap = caps == null ? null : caps.onlineGenerationCap();

        return onlineGenerationCap == null ? generatedAmount : Math.min(generatedAmount, onlineGenerationCap);
    }

    static long computeOfflineGenerated(long generatedPerTick, long elapsedSeconds, long maxOfflineSeconds,
            double offlineBasePercent, double offlinePercentMultiplier) {
        if (generatedPerTick <= 0L || elapsedSeconds <= 0L || maxOfflineSeconds <= 0L) {
            return 0L;
        }
        if (offlineBasePercent <= 0.0 || offlinePercentMultiplier <= 0.0
                || !Double.isFinite(offlineBasePercent) || !Double.isFinite(offlinePercentMultiplier)) {
            return 0L;
        }

        var cappedElapsedSeconds = Math.min(elapsedSeconds, maxOfflineSeconds);
        var generatedPerSecond = safeMultiply(generatedPerTick, TICKS_PER_SECOND);
        if (generatedPerSecond <= 0L) {
            return 0L;
        }

        var offlineRate = generatedPerSecond * offlineBasePercent * offlinePercentMultiplier;
        if (offlineRate <= 0.0 || !Double.isFinite(offlineRate)) {
            return 0L;
        }

        var generatedOffline = Math.floor(offlineRate * cappedElapsedSeconds);
        if (generatedOffline <= 0.0) {
            return 0L;
        }

        return generatedOffline >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) generatedOffline;
    }

}
