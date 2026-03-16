package appeng.idle.reward.timber;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import appeng.idle.player.PlayerIdleDataManager;
import appeng.idle.upgrade.IdleUpgradeHooks;

public final class TimberBlockBreakHandler {
    private static final String LIMIT_EXCEEDED_MESSAGE_KEY = "message.ae2.idle.timber.limit_exceeded";
    private static final long LIMIT_EXCEEDED_MESSAGE_COOLDOWN_TICKS = 40;
    private static final ThreadLocal<Boolean> TIMBER_IN_PROGRESS = ThreadLocal.withInitial(() -> false);
    private static final HashMap<UUID, Long> LAST_LIMIT_EXCEEDED_MESSAGE_TICKS = new HashMap<>();

    private TimberBlockBreakHandler() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || TIMBER_IN_PROGRESS.get()) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel level) || level.isClientSide()) {
            return;
        }

        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        var brokenState = event.getState();
        if (brokenState == null || brokenState.isAir() || !brokenState.is(BlockTags.LOGS)) {
            return;
        }

        var timberLogCap = IdleUpgradeHooks.getTimberLogLimit(PlayerIdleDataManager.get(player));
        if (timberLogCap <= 1) {
            return;
        }

        var timberResult = TimberChopService.collectEligibleLogs(level, event.getPos(), timberLogCap);
        if (timberResult.status() == TimberChopService.Status.EXCEEDS_LIMIT) {
            maybeNotifyTimberLimitExceeded(player, level.getGameTime());
            event.setCanceled(true);
            return;
        }

        if (timberResult.status() != TimberChopService.Status.WITHIN_LIMIT
                || timberResult.collectedPositions().size() <= 1) {
            return;
        }

        TIMBER_IN_PROGRESS.set(true);
        try {
            var origin = event.getPos();
            for (var logPos : timberResult.collectedPositions()) {
                if (Objects.equals(logPos, origin)) {
                    continue;
                }

                level.destroyBlock(logPos, true, player);
            }
        } finally {
            TIMBER_IN_PROGRESS.set(false);
        }
    }

    static void resetLimitExceededCooldownsForTests() {
        LAST_LIMIT_EXCEEDED_MESSAGE_TICKS.clear();
    }

    private static void maybeNotifyTimberLimitExceeded(ServerPlayer player, long gameTime) {
        var playerUuid = player.getUUID();
        var previousMessageTick = LAST_LIMIT_EXCEEDED_MESSAGE_TICKS.get(playerUuid);
        if (previousMessageTick != null
                && gameTime - previousMessageTick < LIMIT_EXCEEDED_MESSAGE_COOLDOWN_TICKS) {
            return;
        }

        LAST_LIMIT_EXCEEDED_MESSAGE_TICKS.put(playerUuid, gameTime);
        player.displayClientMessage(Component.translatable(LIMIT_EXCEEDED_MESSAGE_KEY), true);
    }
}
