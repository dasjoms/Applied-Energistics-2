package appeng.idle.reward.timber;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

import appeng.idle.player.PlayerIdleData;
import appeng.idle.player.PlayerIdleDataManager;
import appeng.idle.upgrade.IdleUpgradeHooks;

class TimberBlockBreakHandlerTest {
    @AfterEach
    void clearMessageCooldowns() {
        TimberBlockBreakHandler.resetLimitExceededCooldownsForTests();
    }

    @Test
    void cancelsBreakWhenConnectedLogsExceedTimberCap() {
        var player = mock(ServerPlayer.class);
        var level = mock(ServerLevel.class);
        var event = blockBreakEvent(level, player, BlockPos.ZERO, logState());

        when(level.getGameTime()).thenReturn(100L);
        when(player.getUUID()).thenReturn(UUID.randomUUID());

        try (MockedStatic<PlayerIdleDataManager> dataManager = Mockito.mockStatic(PlayerIdleDataManager.class);
                MockedStatic<IdleUpgradeHooks> upgradeHooks = Mockito.mockStatic(IdleUpgradeHooks.class);
                MockedStatic<TimberChopService> chopService = Mockito.mockStatic(TimberChopService.class)) {
            dataManager.when(() -> PlayerIdleDataManager.get(player)).thenReturn(new PlayerIdleData());
            upgradeHooks.when(() -> IdleUpgradeHooks.getTimberLogLimit(any(PlayerIdleData.class))).thenReturn(8);
            chopService.when(() -> TimberChopService.collectEligibleLogs(eq(level), eq(BlockPos.ZERO), eq(8)))
                    .thenReturn(TimberChopService.TimberChopResult.exceedsLimit());

            TimberBlockBreakHandler.onBlockBreak(event);

            verify(event).setCanceled(true);
            verify(level, never()).destroyBlock(any(BlockPos.class), eq(true), eq(player));
            verify(player).displayClientMessage(Component.translatable("message.ae2.idle.timber.limit_exceeded"), true);
        }
    }

    @Test
    void throttlesLimitExceededMessagesDuringCooldown() {
        var player = mock(ServerPlayer.class);
        var level = mock(ServerLevel.class);
        var event = blockBreakEvent(level, player, BlockPos.ZERO, logState());

        when(level.getGameTime()).thenReturn(100L, 110L);
        when(player.getUUID()).thenReturn(UUID.randomUUID());

        try (MockedStatic<PlayerIdleDataManager> dataManager = Mockito.mockStatic(PlayerIdleDataManager.class);
                MockedStatic<IdleUpgradeHooks> upgradeHooks = Mockito.mockStatic(IdleUpgradeHooks.class);
                MockedStatic<TimberChopService> chopService = Mockito.mockStatic(TimberChopService.class)) {
            dataManager.when(() -> PlayerIdleDataManager.get(player)).thenReturn(new PlayerIdleData());
            upgradeHooks.when(() -> IdleUpgradeHooks.getTimberLogLimit(any(PlayerIdleData.class))).thenReturn(8);
            chopService.when(() -> TimberChopService.collectEligibleLogs(eq(level), eq(BlockPos.ZERO), eq(8)))
                    .thenReturn(TimberChopService.TimberChopResult.exceedsLimit());

            TimberBlockBreakHandler.onBlockBreak(event);
            TimberBlockBreakHandler.onBlockBreak(event);

            verify(player).displayClientMessage(Component.translatable("message.ae2.idle.timber.limit_exceeded"), true);
        }
    }

    @Test
    void breaksRemainingLogsWhenWithinCap() {
        var player = mock(ServerPlayer.class);
        var level = mock(ServerLevel.class);
        var origin = new BlockPos(0, 0, 0);
        var otherLog = new BlockPos(0, 1, 0);
        var event = blockBreakEvent(level, player, origin, logState());

        try (MockedStatic<PlayerIdleDataManager> dataManager = Mockito.mockStatic(PlayerIdleDataManager.class);
                MockedStatic<IdleUpgradeHooks> upgradeHooks = Mockito.mockStatic(IdleUpgradeHooks.class);
                MockedStatic<TimberChopService> chopService = Mockito.mockStatic(TimberChopService.class)) {
            dataManager.when(() -> PlayerIdleDataManager.get(player)).thenReturn(new PlayerIdleData());
            upgradeHooks.when(() -> IdleUpgradeHooks.getTimberLogLimit(any(PlayerIdleData.class))).thenReturn(8);
            chopService.when(() -> TimberChopService.collectEligibleLogs(eq(level), eq(origin), eq(8)))
                    .thenReturn(TimberChopService.TimberChopResult.withinLimit(List.of(origin, otherLog)));

            TimberBlockBreakHandler.onBlockBreak(event);

            verify(level).destroyBlock(otherLog, true, player);
            verify(level, never()).destroyBlock(origin, true, player);
            verify(event, never()).setCanceled(true);
            verify(player, never()).displayClientMessage(any(Component.class), eq(true));
        }
    }

    @Test
    void skipsTimberWhenCapIsOneOrLess() {
        var player = mock(ServerPlayer.class);
        var level = mock(ServerLevel.class);
        var event = blockBreakEvent(level, player, BlockPos.ZERO, logState());

        try (MockedStatic<PlayerIdleDataManager> dataManager = Mockito.mockStatic(PlayerIdleDataManager.class);
                MockedStatic<IdleUpgradeHooks> upgradeHooks = Mockito.mockStatic(IdleUpgradeHooks.class);
                MockedStatic<TimberChopService> chopService = Mockito.mockStatic(TimberChopService.class)) {
            dataManager.when(() -> PlayerIdleDataManager.get(player)).thenReturn(new PlayerIdleData());
            upgradeHooks.when(() -> IdleUpgradeHooks.getTimberLogLimit(any(PlayerIdleData.class))).thenReturn(1);

            TimberBlockBreakHandler.onBlockBreak(event);

            chopService.verify(() -> TimberChopService.collectEligibleLogs(any(), any(), anyInt()), never());
        }
    }

    private static BlockState logState() {
        var state = mock(BlockState.class);
        when(state.isAir()).thenReturn(false);
        when(state.is(BlockTags.LOGS)).thenReturn(true);
        return state;
    }

    private static BlockEvent.BreakEvent blockBreakEvent(ServerLevel level, ServerPlayer player, BlockPos pos,
            BlockState state) {
        var event = mock(BlockEvent.BreakEvent.class);

        when(event.isCanceled()).thenReturn(false);
        when(event.getLevel()).thenReturn(level);
        when(level.isClientSide()).thenReturn(false);
        when(event.getPlayer()).thenReturn(player);
        when(event.getPos()).thenReturn(pos);
        when(event.getState()).thenReturn(state);

        return event;
    }
}
