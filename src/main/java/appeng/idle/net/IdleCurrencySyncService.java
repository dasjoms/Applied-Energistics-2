package appeng.idle.net;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.idle.currency.CurrencyId;
import appeng.core.AEConfig;
import appeng.idle.currency.IdleCurrencies;
import appeng.idle.currency.IdleCurrencyManager;
import appeng.idle.player.GenerationContext;
import appeng.idle.player.PlayerIdleDataManager;
import appeng.idle.tick.BasicGenerationRule;
import appeng.idle.tick.GenerationRule;
import appeng.idle.upgrade.IdleUpgradeHooks;

/**
 * Server->client idle currency synchronization.
 */
public final class IdleCurrencySyncService {
    private static final int HEARTBEAT_INTERVAL_TICKS = 20 * 10;
    private static final int TICKS_PER_SECOND = 20;
    private static final GenerationRule GENERATION_RULE = new BasicGenerationRule();

    private IdleCurrencySyncService() {
    }

    public static void handlePlayerLoggedIn(PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        sendSnapshot(player);
    }

    public static void handleContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        sendSnapshot(player);
    }

    public static void handleServerTickEnd(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % HEARTBEAT_INTERVAL_TICKS != 0) {
            return;
        }

        for (var player : event.getServer().getPlayerList().getPlayers()) {
            sendSnapshot(player);
        }
    }

    public static void sendSnapshot(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        PacketDistributor.sendToPlayer(player, new IdleCurrencySnapshotPacket(snapshotBalances(player), snapshotRates(player)));
        sendHudSnapshot(player);
    }

    public static void sendHudSnapshot(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        PacketDistributor.sendToPlayer(player, new IdleCurrencyHudSnapshotPacket(snapshotHudValues(player)));
    }

    public static void sendDelta(ServerPlayer player, Map<CurrencyId, Long> changedBalances) {
        sendDelta(player, changedBalances, false);
    }

    public static void sendDelta(ServerPlayer player, Map<CurrencyId, Long> changedBalances, boolean refreshRates) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(changedBalances, "changedBalances");

        var rates = refreshRates ? snapshotRates(player) : Map.<CurrencyId, Long>of();
        if (changedBalances.isEmpty() && rates.isEmpty()) {
            return;
        }

        PacketDistributor.sendToPlayer(player,
                new IdleCurrencyDeltaPacket(new LinkedHashMap<>(changedBalances), new LinkedHashMap<>(rates)));
    }

    private static Map<CurrencyId, Long> snapshotBalances(ServerPlayer player) {
        return new LinkedHashMap<>(PlayerIdleDataManager.get(player).balancesView());
    }



    private static Map<CurrencyId, Long> snapshotRates(ServerPlayer player) {
        var hudValues = snapshotHudValues(player);
        var rates = new LinkedHashMap<CurrencyId, Long>(hudValues.size());
        for (var entry : hudValues.entrySet()) {
            rates.put(entry.getKey(), entry.getValue().gainPerSecond());
        }
        return rates;
    }

    private static Map<CurrencyId, IdleCurrencyHudValue> snapshotHudValues(ServerPlayer player) {
        var data = PlayerIdleDataManager.get(player);
        var generationIntervalTicks = Math.max(AEConfig.instance().getIdleGenerationIntervalTicks(), 1);

        var hudValues = new LinkedHashMap<CurrencyId, IdleCurrencyHudValue>();
        for (var currency : currenciesToGenerate()) {
            var balance = data.getBalance(currency);
            var multipliers = IdleUpgradeHooks.getOnlineGenerationMultipliers(data, currency);
            var context = new GenerationContext(player.getUUID(), true, multipliers);
            var generatedPerTick = GENERATION_RULE.generatePerTick(context, currency).units();
            var generatedPerInterval = generatedPerTick <= 0L ? 0L : safeMultiply(generatedPerTick, generationIntervalTicks);
            var clampedPerInterval = clampOnlineGenerationCap(currency, generatedPerInterval);
            var gainPerSecond = clampedPerInterval <= 0L
                    ? 0L
                    : safeMultiply(clampedPerInterval, TICKS_PER_SECOND) / generationIntervalTicks;

            hudValues.put(currency, new IdleCurrencyHudValue(balance, gainPerSecond));
        }

        return hudValues;
    }


    private static Set<CurrencyId> currenciesToGenerate() {
        var currencies = new LinkedHashSet<CurrencyId>(IdleCurrencyManager.getCurrencies().keySet());
        if (currencies.isEmpty()) {
            currencies.add(IdleCurrencies.IDLE);
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

}
