package appeng.idle.net;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.core.AEConfig;
import appeng.core.definitions.AEItems;
import appeng.idle.combat.IdleCombatHandler;
import appeng.idle.currency.CurrencyId;
import appeng.idle.currency.IdleCurrencies;
import appeng.idle.currency.IdleCurrencyManager;
import appeng.idle.generation.IdleGenerationCapService;
import appeng.idle.player.PlayerIdleData;
import appeng.idle.player.PlayerIdleDataManager;
import appeng.idle.tick.IdleGenerationMath;
import appeng.idle.upgrade.IdleUpgradeHooks;

/**
 * Server->client idle currency synchronization.
 */
public final class IdleCurrencySyncService {
    private static final int HEARTBEAT_INTERVAL_TICKS = 20 * 10;
    private static final int TICKS_PER_SECOND = 20;
    private static final Map<UUID, Map<CurrencyId, IdleCurrencyHudValue>> LAST_SENT_HUD_VALUES_BY_PLAYER = new HashMap<>();
    private static final Map<UUID, IdleCombatHudState> LAST_SENT_COMBAT_HUD_STATE_BY_PLAYER = new HashMap<>();

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

    public static void handlePlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        clearLastSentHudSnapshot(player);
    }

    public static void handleServerTickEnd(ServerTickEvent.Post event) {
        var tickCount = event.getServer().getTickCount();

        if (tickCount % HEARTBEAT_INTERVAL_TICKS == 0) {
            for (var player : event.getServer().getPlayerList().getPlayers()) {
                sendHeartbeat(player);
            }
        }

        var hudSyncIntervalTicks = AEConfig.instance().getIdleHudSyncIntervalTicks();
        if (hudSyncIntervalTicks > 0 && tickCount % hudSyncIntervalTicks == 0) {
            for (var player : event.getServer().getPlayerList().getPlayers()) {
                if (shouldReceiveHudHeartbeat(player)) {
                    sendHudSnapshot(player);
                    sendCombatHudSnapshot(player);
                }
            }
        }
    }

    static boolean shouldReceiveHudHeartbeat(ServerPlayer player) {
        return AEItems.IDLE_VISOR.is(player.getItemBySlot(EquipmentSlot.HEAD));
    }

    private static void sendHeartbeat(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        PacketDistributor.sendToPlayer(player,
                new IdleCurrencySnapshotPacket(snapshotBalances(player), snapshotRates(player),
                        isIdlePunchEligible(player)));

        if (shouldReceiveHudHeartbeat(player)) {
            sendHudSnapshot(player);
            sendCombatHudSnapshot(player);
        }
    }

    public static void sendSnapshot(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        PacketDistributor.sendToPlayer(player,
                new IdleCurrencySnapshotPacket(snapshotBalances(player), snapshotRates(player),
                        isIdlePunchEligible(player)));
        sendCombatHudSnapshot(player);
        if (shouldReceiveHudHeartbeat(player)) {
            sendHudSnapshot(player);
        }
    }

    public static void sendHudSnapshot(ServerPlayer player) {
        Objects.requireNonNull(player, "player");

        var hudValues = snapshotHudValues(player);
        var playerId = player.getUUID();
        var previousHudValues = LAST_SENT_HUD_VALUES_BY_PLAYER.get(playerId);
        if (previousHudValues != null && previousHudValues.equals(hudValues)) {
            return;
        }

        PacketDistributor.sendToPlayer(player, new IdleCurrencyHudSnapshotPacket(hudValues));
        LAST_SENT_HUD_VALUES_BY_PLAYER.put(playerId, Map.copyOf(hudValues));
    }

    public static void sendCombatHudSnapshot(ServerPlayer player) {
        Objects.requireNonNull(player, "player");

        var snapshot = IdleCombatHandler.snapshotCombatHudState(player, player.serverLevel().getGameTime());
        var playerId = player.getUUID();
        var previousSnapshot = LAST_SENT_COMBAT_HUD_STATE_BY_PLAYER.get(playerId);
        if (previousSnapshot != null && previousSnapshot.equals(snapshot)) {
            return;
        }

        PacketDistributor.sendToPlayer(player, new IdleCombatHudSnapshotPacket(snapshot));
        LAST_SENT_COMBAT_HUD_STATE_BY_PLAYER.put(playerId, snapshot);
    }

    public static void sendEmptyHudSnapshot(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        clearLastSentHudSnapshot(player);
        PacketDistributor.sendToPlayer(player, new IdleCurrencyHudSnapshotPacket(Map.of()));
    }

    private static void clearLastSentHudSnapshot(ServerPlayer player) {
        var playerId = player.getUUID();
        LAST_SENT_HUD_VALUES_BY_PLAYER.remove(playerId);
        LAST_SENT_COMBAT_HUD_STATE_BY_PLAYER.remove(playerId);
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
                new IdleCurrencyDeltaPacket(new LinkedHashMap<>(changedBalances), new LinkedHashMap<>(rates),
                        isIdlePunchEligible(player)));
    }

    private static boolean isIdlePunchEligible(ServerPlayer player) {
        return IdleUpgradeHooks.hasCombatUpgrade(PlayerIdleDataManager.get(player));
    }

    private static Map<CurrencyId, Long> snapshotBalances(ServerPlayer player) {
        return new LinkedHashMap<>(PlayerIdleDataManager.get(player).balancesView());
    }

    private static Map<CurrencyId, Long> snapshotRates(ServerPlayer player) {
        var hudValues = snapshotHudValues(player);
        var rates = new LinkedHashMap<CurrencyId, Long>(hudValues.size());
        for (var entry : hudValues.entrySet()) {
            rates.put(entry.getKey(), toRatePerSecond(entry.getKey(), entry.getValue().gainPerSecond()));
        }
        return rates;
    }

    private static Map<CurrencyId, IdleCurrencyHudValue> snapshotHudValues(ServerPlayer player) {
        var data = PlayerIdleDataManager.get(player);
        var elapsedTicksSinceBaseline = elapsedTicksSinceOnlineProgressBaseline(player, data);

        var hudValues = new LinkedHashMap<CurrencyId, IdleCurrencyHudValue>();
        for (var currency : currenciesToGenerate()) {
            var balance = data.getBalance(currency);
            var definition = IdleCurrencyManager.get(currency);
            var baseTicksPerUnit = definition == null ? 0L : definition.baseTicksPerUnit();

            var effectiveTicksPerUnit = IdleGenerationMath.effectiveTicksPerUnit(data, currency);
            var ticksPerUnit = IdleGenerationMath.ticksPerUnitForDisplay(effectiveTicksPerUnit);
            var gainPerSecond = gainPerSecond(data, currency, baseTicksPerUnit, effectiveTicksPerUnit);
            var progressTicks = projectDisplayProgressTicks(
                    data.getGenerationProgressTicks(currency),
                    ticksPerUnit,
                    elapsedTicksSinceBaseline);
            var secondsToNext = secondsToNext(progressTicks, ticksPerUnit);

            hudValues.put(currency,
                    new IdleCurrencyHudValue(balance, gainPerSecond, progressTicks, ticksPerUnit, secondsToNext));
        }

        return hudValues;
    }

    static long elapsedTicksSinceOnlineProgressBaseline(ServerPlayer player, PlayerIdleData data) {
        if (!PlayerIdleDataManager.isPassiveGenerationEnabled(player)) {
            return 0L;
        }

        var intervalTicks = AEConfig.instance().getIdleGenerationIntervalTicks();
        if (intervalTicks <= 0) {
            return 0L;
        }

        var server = player.getServer();
        if (server == null) {
            return 0L;
        }

        var baselineTick = data.getOnlineProgressBaselineTick();
        if (baselineTick < 0L) {
            return Math.floorMod(server.getTickCount(), intervalTicks);
        }

        return Math.max(0L, server.getTickCount() - baselineTick);
    }

    static long projectDisplayProgressTicks(long baselineProgressTicks, long ticksPerUnit,
            long elapsedTicksSinceBoundary) {
        if (ticksPerUnit <= 0L) {
            return 0L;
        }

        var clampedBaseline = Math.min(Math.max(0L, baselineProgressTicks), ticksPerUnit - 1L);
        var clampedElapsed = Math.min(Math.max(0L, elapsedTicksSinceBoundary), ticksPerUnit - 1L - clampedBaseline);
        return (clampedBaseline + clampedElapsed) % ticksPerUnit;
    }

    private static double gainPerSecond(PlayerIdleData data, CurrencyId currency, long baseTicksPerUnit,
            double effectiveTicksPerUnit) {
        if (baseTicksPerUnit <= 0L || effectiveTicksPerUnit <= 0.0 || !Double.isFinite(effectiveTicksPerUnit)) {
            return 0.0;
        }

        if (IdleGenerationMath.remainingBalanceCapacity(data, currency) <= 0L) {
            return 0.0;
        }

        var generatedPerSecond = TICKS_PER_SECOND / effectiveTicksPerUnit;
        return IdleGenerationCapService.clampOnlineGenerationCap(currency, generatedPerSecond);
    }

    private static Long secondsToNext(long progressTicks, long ticksPerUnit) {
        if (ticksPerUnit <= 0L || progressTicks >= ticksPerUnit) {
            return null;
        }

        var remainingTicks = ticksPerUnit - progressTicks;
        var seconds = (remainingTicks + TICKS_PER_SECOND - 1L) / TICKS_PER_SECOND;
        return Math.max(0L, seconds);
    }

    private static Set<CurrencyId> currenciesToGenerate() {
        var currencies = new LinkedHashSet<CurrencyId>(IdleCurrencyManager.getCurrencies().keySet());
        if (currencies.isEmpty()) {
            currencies.add(IdleCurrencies.IDLE);
        }

        return currencies;
    }

    private static long toRatePerSecond(CurrencyId currency, double generatedPerSecond) {
        return (long) Math.floor(IdleGenerationCapService.clampOnlineGenerationCap(currency, generatedPerSecond));
    }

}
