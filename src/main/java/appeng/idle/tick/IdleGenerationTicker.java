package appeng.idle.tick;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import appeng.core.AEConfig;
import appeng.idle.currency.CurrencyId;
import appeng.idle.currency.IdleCurrencies;
import appeng.idle.currency.IdleCurrencyManager;
import appeng.idle.player.PlayerIdleDataManager;
import appeng.idle.upgrade.IdleUpgradeHooks;

/**
 * Applies periodic idle generation accrual for online players.
 */
public final class IdleGenerationTicker {
    private static final CurrencyId DEFAULT_CURRENCY = IdleCurrencies.IDLE;
    private static final String REASON_ONLINE_TICK = "ONLINE_TICK";
    private static final String REASON_OFFLINE_CATCHUP = "OFFLINE_CATCHUP";

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
        if (!PlayerIdleDataManager.isPassiveGenerationEnabled(player)) {
            return;
        }

        var data = PlayerIdleDataManager.get(player);
        var generatedAmounts = IdleGenerationProgressService.accrueOnlineProgress(
                data,
                intervalTicks,
                currenciesToGenerate());
        var server = player.getServer();
        if (server != null) {
            data.setOnlineProgressBaselineTick(server.getTickCount());
        }
        PlayerIdleDataManager.save(player, data);
        if (!generatedAmounts.isEmpty()) {
            PlayerIdleDataManager.addGeneratedBalances(player, generatedAmounts, REASON_ONLINE_TICK);
        }
    }

    public static void accrueOfflineCatchup(ServerPlayer player, long elapsedSeconds) {
        if (elapsedSeconds <= 0L || !PlayerIdleDataManager.isPassiveGenerationEnabled(player)) {
            return;
        }

        var data = PlayerIdleDataManager.get(player);

        var offlineBasePercent = AEConfig.instance().getIdleOfflineBasePercent();
        var offlinePercentMultiplier = IdleUpgradeHooks.getOfflinePercentMultiplier(data);
        var maxOfflineSeconds = AEConfig.instance().getIdleOfflineMaxSeconds();

        var generatedAmounts = IdleGenerationProgressService.accrueOfflineProgress(
                data,
                elapsedSeconds,
                maxOfflineSeconds,
                offlineBasePercent,
                offlinePercentMultiplier,
                currenciesToGenerate());
        PlayerIdleDataManager.save(player, data);
        if (!generatedAmounts.isEmpty()) {
            PlayerIdleDataManager.addGeneratedBalances(player, generatedAmounts, REASON_OFFLINE_CATCHUP);
        }
    }

    static Set<CurrencyId> currenciesToGenerate() {
        var currencies = new HashSet<>(IdleCurrencyManager.getCurrencies().keySet());
        if (currencies.isEmpty()) {
            currencies.add(DEFAULT_CURRENCY);
        }
        return currencies;
    }

}
