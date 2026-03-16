package appeng.idle.reward.timber;

import java.util.HashMap;
import java.util.List;
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
    private static final ThreadLocal<Boolean> TIMBER_IN_PROGRESS = ThreadLocal.withInitial(() -> false);
    private static final HashMap<UUID, Long> LAST_LIMIT_EXCEEDED_MESSAGE_TICKS = new HashMap<>();
    private static final HashMap<UUID, TreeSignature> LAST_WARNED_OVERSIZED_TARGET = new HashMap<>();

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
            maybeNotifyTimberLimitExceeded(player, level, timberResult.oversizedComponentSamplePositions(),
                    level.getGameTime());
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
        LAST_WARNED_OVERSIZED_TARGET.clear();
    }

    private static void maybeNotifyTimberLimitExceeded(ServerPlayer player, ServerLevel level,
            List<BlockPos> oversizedComponentSamplePositions, long gameTime) {
        var playerUuid = player.getUUID();
        var targetSignature = TreeSignature.from(level, oversizedComponentSamplePositions);
        if (targetSignature != null) {
            var lastWarnedTarget = LAST_WARNED_OVERSIZED_TARGET.get(playerUuid);
            if (lastWarnedTarget != null && lastWarnedTarget.matches(targetSignature)) {
                LAST_WARNED_OVERSIZED_TARGET.put(playerUuid, targetSignature);
                return;
            }

            LAST_WARNED_OVERSIZED_TARGET.put(playerUuid, targetSignature);
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

    private record TreeSignature(ResourceLocation dimension, long hashA, long hashB, long hashC, long hashD) {

        private static final long FNV64_OFFSET_BASIS = 0xcbf29ce484222325L;
        private static final long FNV64_PRIME = 0x100000001b3L;

        private static TreeSignature from(ServerLevel level, List<BlockPos> sampledLogs) {
            var dimensionLocation = level.dimension() != null ? level.dimension().location() : null;
            if (dimensionLocation == null) {
                return null;
            }

            if (sampledLogs == null || sampledLogs.isEmpty()) {
                return null;
            }

            long[] minHashes = { Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE };
            for (var logPos : sampledLogs) {
                insertMinHash(minHashes, mixPos(logPos));
            }

            return new TreeSignature(dimensionLocation, minHashes[0], minHashes[1], minHashes[2], minHashes[3]);
        }

        private boolean matches(TreeSignature other) {
            if (!dimension.equals(other.dimension)) {
                return false;
            }

            int sharedHashes = 0;
            long[] ownHashes = { hashA, hashB, hashC, hashD };
            long[] otherHashes = { other.hashA, other.hashB, other.hashC, other.hashD };

            for (var ownHash : ownHashes) {
                for (var otherHash : otherHashes) {
                    if (ownHash == otherHash) {
                        sharedHashes++;
                        break;
                    }
                }
            }

            return sharedHashes >= 3;
        }

        private static long mixPos(BlockPos pos) {
            long hash = FNV64_OFFSET_BASIS;
            hash = fnv1a(hash, pos.getX());
            hash = fnv1a(hash, pos.getY());
            hash = fnv1a(hash, pos.getZ());
            return hash;
        }

        private static long fnv1a(long hash, int value) {
            hash ^= (value) & 0xFFL;
            hash *= FNV64_PRIME;
            hash ^= (value >> 8) & 0xFFL;
            hash *= FNV64_PRIME;
            hash ^= (value >> 16) & 0xFFL;
            hash *= FNV64_PRIME;
            hash ^= (value >> 24) & 0xFFL;
            hash *= FNV64_PRIME;
            return hash;
        }

        private static void insertMinHash(long[] minHashes, long hash) {
            for (int i = 0; i < minHashes.length; i++) {
                if (hash >= minHashes[i]) {
                    continue;
                }

                for (int j = minHashes.length - 1; j > i; j--) {
                    minHashes[j] = minHashes[j - 1];
                }
                minHashes[i] = hash;
                return;
            }
        }
    }
}
