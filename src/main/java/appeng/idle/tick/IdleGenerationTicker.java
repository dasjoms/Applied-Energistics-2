package appeng.idle.tick;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import appeng.core.AEConfig;
import appeng.core.AppEng;
import appeng.idle.currency.CurrencyId;
import appeng.idle.player.GenerationContext;
import appeng.idle.player.PlayerIdleData;
import appeng.idle.player.PlayerIdleDataManager;
import appeng.idle.upgrade.MultiplierBundle;

/**
 * Applies periodic idle generation accrual for online players.
 */
public final class IdleGenerationTicker {
    private static final CurrencyId DEFAULT_CURRENCY = new CurrencyId(AppEng.makeId("idle"));
    private static final String REASON_ONLINE_TICK = "ONLINE_TICK";
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
        var context = new GenerationContext(player.getUUID(), true, MultiplierBundle.IDENTITY);

        var currencies = currenciesToGenerate(data);
        var generatedAmounts = new HashMap<CurrencyId, Long>();

        for (var currency : currencies) {
            var generatedPerTick = GENERATION_RULE.generatePerTick(context, currency).units();
            if (generatedPerTick <= 0L) {
                continue;
            }

            var generatedThisInterval = safeMultiply(generatedPerTick, intervalTicks);
            if (generatedThisInterval <= 0L) {
                continue;
            }

            generatedAmounts.put(currency, generatedThisInterval);
        }

        PlayerIdleDataManager.addGeneratedBalances(player, generatedAmounts, REASON_ONLINE_TICK);
    }

    private static Set<CurrencyId> currenciesToGenerate(PlayerIdleData data) {
        var currencies = new HashSet<>(data.balancesView().keySet());
        currencies.add(DEFAULT_CURRENCY);
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

}
