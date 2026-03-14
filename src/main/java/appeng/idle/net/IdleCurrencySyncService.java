package appeng.idle.net;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.idle.currency.CurrencyId;
import appeng.idle.player.PlayerIdleDataManager;

/**
 * Server->client idle currency synchronization.
 */
public final class IdleCurrencySyncService {
    private static final int HEARTBEAT_INTERVAL_TICKS = 20 * 10;

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
        PacketDistributor.sendToPlayer(player, new IdleCurrencySnapshotPacket(snapshotBalances(player)));
    }

    public static void sendDelta(ServerPlayer player, Map<CurrencyId, Long> changedBalances) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(changedBalances, "changedBalances");

        if (changedBalances.isEmpty()) {
            return;
        }

        PacketDistributor.sendToPlayer(player, new IdleCurrencyDeltaPacket(new LinkedHashMap<>(changedBalances)));
    }

    private static Map<CurrencyId, Long> snapshotBalances(ServerPlayer player) {
        return new LinkedHashMap<>(PlayerIdleDataManager.get(player).balancesView());
    }
}
