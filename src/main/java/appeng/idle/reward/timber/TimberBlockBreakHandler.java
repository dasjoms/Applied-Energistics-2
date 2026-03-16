package appeng.idle.reward.timber;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    private static final long OVERSIZED_TARGET_SUPPRESSION_TICKS = 20 * 30;
    private static final int MAX_OVERSIZED_TARGETS_PER_PLAYER = 64;
    private static final ThreadLocal<Boolean> TIMBER_IN_PROGRESS = ThreadLocal.withInitial(() -> false);
    private static final HashMap<UUID, Long> LAST_LIMIT_EXCEEDED_MESSAGE_TICKS = new HashMap<>();
    private static final HashMap<UUID, HashMap<OversizedTargetKey, Long>> WARNED_OVERSIZED_TARGETS = new HashMap<>();

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

        if (!PlayerIdleDataManager.isActiveRewardEligibleNow(player)) {
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
            maybeNotifyTimberLimitExceeded(player, level, event.getPos(), level.getGameTime());
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
        WARNED_OVERSIZED_TARGETS.clear();
    }

    private static void maybeNotifyTimberLimitExceeded(ServerPlayer player, ServerLevel level, BlockPos brokenPos,
            long gameTime) {
        var playerUuid = player.getUUID();
        var targetKey = OversizedTargetKey.from(level, brokenPos);
        if (targetKey != null) {
            var perPlayerTargets = WARNED_OVERSIZED_TARGETS.computeIfAbsent(playerUuid, ignored -> new HashMap<>());
            cleanupExpiredTargets(perPlayerTargets, gameTime);

            var previousWarnTick = perPlayerTargets.get(targetKey);
            if (previousWarnTick != null && gameTime - previousWarnTick < OVERSIZED_TARGET_SUPPRESSION_TICKS) {
                return;
            }

            perPlayerTargets.put(targetKey, gameTime);
            trimTargetsToMaxEntries(perPlayerTargets);
        } else {
            var previousMessageTick = LAST_LIMIT_EXCEEDED_MESSAGE_TICKS.get(playerUuid);
            if (previousMessageTick != null
                    && gameTime - previousMessageTick < LIMIT_EXCEEDED_MESSAGE_COOLDOWN_TICKS) {
                return;
            }

            LAST_LIMIT_EXCEEDED_MESSAGE_TICKS.put(playerUuid, gameTime);
        }

        player.displayClientMessage(Component.translatable(LIMIT_EXCEEDED_MESSAGE_KEY), true);
    }

    private static void cleanupExpiredTargets(HashMap<OversizedTargetKey, Long> perPlayerTargets, long gameTime) {
        Iterator<Map.Entry<OversizedTargetKey, Long>> iterator = perPlayerTargets.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (gameTime - entry.getValue() >= OVERSIZED_TARGET_SUPPRESSION_TICKS) {
                iterator.remove();
            }
        }
    }

    private static void trimTargetsToMaxEntries(HashMap<OversizedTargetKey, Long> perPlayerTargets) {
        if (perPlayerTargets.size() <= MAX_OVERSIZED_TARGETS_PER_PLAYER) {
            return;
        }

        OversizedTargetKey oldestKey = null;
        long oldestTick = Long.MAX_VALUE;
        for (var entry : perPlayerTargets.entrySet()) {
            if (entry.getValue() < oldestTick) {
                oldestTick = entry.getValue();
                oldestKey = entry.getKey();
            }
        }

        if (oldestKey != null) {
            perPlayerTargets.remove(oldestKey);
        }
    }

    private record OversizedTargetKey(ResourceLocation dimension, int chunkX, int chunkZ, BlockPos canonicalSeedPos) {
        private static OversizedTargetKey from(ServerLevel level, BlockPos brokenPos) {
            var dimensionLocation = level.dimension() != null ? level.dimension().location() : null;
            if (dimensionLocation == null) {
                return null;
            }

            return new OversizedTargetKey(
                    dimensionLocation,
                    brokenPos.getX() >> 4,
                    brokenPos.getZ() >> 4,
                    findCanonicalSeedLogPos(level, brokenPos));
        }

        private static BlockPos findCanonicalSeedLogPos(ServerLevel level, BlockPos brokenPos) {
            var current = brokenPos.immutable();
            var minBuildHeight = level.getMinBuildHeight();
            while (current.getY() > minBuildHeight) {
                var below = current.below();
                var belowState = level.getBlockState(below);
                if (!belowState.is(BlockTags.LOGS)) {
                    break;
                }

                current = below.immutable();
            }

            return current;
        }
    }
}
