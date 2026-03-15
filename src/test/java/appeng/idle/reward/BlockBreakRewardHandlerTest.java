package appeng.idle.reward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

import appeng.idle.currency.CurrencyId;
import appeng.idle.reward.natural.NaturalLogTracker;
import appeng.idle.tick.IdleGenerationProgressService;

class BlockBreakRewardHandlerTest {
    @Test
    void awardsProgressForMatchingBlockWhenPlayerIsActivelyEligible() {
        var reward = rewardForBlock("break_oak_log_idle", "minecraft", "oak_log", 20L);
        var player = mock(ServerPlayer.class);
        var event = blockBreakEvent(player, Blocks.OAK_LOG.defaultBlockState());

        try (MockedStatic<IdleRewardManager> rewardManager = mockStatic(IdleRewardManager.class);
                MockedStatic<RewardEligibilityService> eligibilityService = mockStatic(RewardEligibilityService.class);
                MockedStatic<IdleGenerationProgressService> progressService = mockStatic(
                        IdleGenerationProgressService.class)) {
            rewardManager.when(() -> IdleRewardManager.getByTrigger(RewardTriggerType.BLOCK_BREAK))
                    .thenReturn(List.of(reward));
            eligibilityService.when(() -> RewardEligibilityService.canReceiveActiveReward(player, reward))
                    .thenReturn(true);

            BlockBreakRewardHandler.onBlockBreak(event);

            progressService.verify(() -> IdleGenerationProgressService.grantActiveProgressTicks(
                    eq(player),
                    eq(reward.currencyId()),
                    eq(reward.progressTicksAwarded()),
                    eq("BLOCK_BREAK:" + reward.id())));
        }
    }

    @Test
    void doesNotAwardNaturalLogRewardForPlayerPlacedLogs() {
        var reward = rewardForBlock("break_natural_log_idle", "minecraft", "oak_log", 20L);
        var player = mock(ServerPlayer.class);
        var event = blockBreakEvent(player, Blocks.OAK_LOG.defaultBlockState());

        try (MockedStatic<IdleRewardManager> rewardManager = mockStatic(IdleRewardManager.class);
                MockedStatic<NaturalLogTracker> naturalLogTracker = mockStatic(NaturalLogTracker.class);
                MockedStatic<RewardEligibilityService> eligibilityService = mockStatic(RewardEligibilityService.class);
                MockedStatic<IdleGenerationProgressService> progressService = mockStatic(
                        IdleGenerationProgressService.class)) {
            rewardManager.when(() -> IdleRewardManager.getByTrigger(RewardTriggerType.BLOCK_BREAK))
                    .thenReturn(List.of(reward));
            naturalLogTracker
                    .when(() -> NaturalLogTracker.isNaturallyGeneratedLog(any(ServerLevel.class), any(BlockPos.class),
                            any(BlockState.class)))
                    .thenReturn(false);

            BlockBreakRewardHandler.onBlockBreak(event);

            eligibilityService.verifyNoInteractions();
            progressService.verifyNoInteractions();
        }
    }

    @Test
    void awardsNaturalLogRewardForNaturallyGeneratedLogs() {
        var reward = rewardForBlock("break_natural_log_idle", "minecraft", "oak_log", 20L);
        var player = mock(ServerPlayer.class);
        var event = blockBreakEvent(player, Blocks.OAK_LOG.defaultBlockState());

        try (MockedStatic<IdleRewardManager> rewardManager = mockStatic(IdleRewardManager.class);
                MockedStatic<NaturalLogTracker> naturalLogTracker = mockStatic(NaturalLogTracker.class);
                MockedStatic<RewardEligibilityService> eligibilityService = mockStatic(RewardEligibilityService.class);
                MockedStatic<IdleGenerationProgressService> progressService = mockStatic(
                        IdleGenerationProgressService.class)) {
            rewardManager.when(() -> IdleRewardManager.getByTrigger(RewardTriggerType.BLOCK_BREAK))
                    .thenReturn(List.of(reward));
            naturalLogTracker
                    .when(() -> NaturalLogTracker.isNaturallyGeneratedLog(any(ServerLevel.class), any(BlockPos.class),
                            any(BlockState.class)))
                    .thenReturn(true);
            eligibilityService.when(() -> RewardEligibilityService.canReceiveActiveReward(player, reward))
                    .thenReturn(true);

            BlockBreakRewardHandler.onBlockBreak(event);

            progressService.verify(() -> IdleGenerationProgressService.grantActiveProgressTicks(
                    eq(player),
                    eq(reward.currencyId()),
                    eq(reward.progressTicksAwarded()),
                    eq("BLOCK_BREAK:" + reward.id())));
        }
    }

    @Test
    void doesNotAwardProgressWhenPlayerIsNotActivelyEligible() {
        var reward = rewardForBlock("break_oak_log_idle", "minecraft", "oak_log", 20L);
        var player = mock(ServerPlayer.class);
        var event = blockBreakEvent(player, Blocks.OAK_LOG.defaultBlockState());

        try (MockedStatic<IdleRewardManager> rewardManager = mockStatic(IdleRewardManager.class);
                MockedStatic<RewardEligibilityService> eligibilityService = mockStatic(RewardEligibilityService.class);
                MockedStatic<IdleGenerationProgressService> progressService = mockStatic(
                        IdleGenerationProgressService.class)) {
            rewardManager.when(() -> IdleRewardManager.getByTrigger(RewardTriggerType.BLOCK_BREAK))
                    .thenReturn(List.of(reward));
            eligibilityService.when(() -> RewardEligibilityService.canReceiveActiveReward(player, reward))
                    .thenReturn(false);

            BlockBreakRewardHandler.onBlockBreak(event);

            progressService.verify(() -> IdleGenerationProgressService.grantActiveProgressTicks(
                    any(ServerPlayer.class),
                    any(CurrencyId.class),
                    anyLong(),
                    anyString()), never());
        }
    }

    @Test
    void doesNotAwardProgressWhenBrokenBlockDoesNotMatchRewardCondition() {
        var reward = rewardForBlock("break_oak_log_idle", "minecraft", "oak_log", 20L);
        var player = mock(ServerPlayer.class);
        var event = blockBreakEvent(player, Blocks.STONE.defaultBlockState());

        try (MockedStatic<IdleRewardManager> rewardManager = mockStatic(IdleRewardManager.class);
                MockedStatic<RewardEligibilityService> eligibilityService = mockStatic(RewardEligibilityService.class);
                MockedStatic<IdleGenerationProgressService> progressService = mockStatic(
                        IdleGenerationProgressService.class)) {
            rewardManager.when(() -> IdleRewardManager.getByTrigger(RewardTriggerType.BLOCK_BREAK))
                    .thenReturn(List.of(reward));

            BlockBreakRewardHandler.onBlockBreak(event);

            eligibilityService.verifyNoInteractions();
            progressService.verifyNoInteractions();
        }
    }

    @Test
    void matchesBlockIdConditionAgainstBrokenState() {
        var reward = rewardForBlock("break_oak_log_idle", "minecraft", "oak_log", 20L);

        assertThat(BlockBreakRewardHandler.matchesBlockCondition(reward, Blocks.OAK_LOG.defaultBlockState())).isTrue();
        assertThat(BlockBreakRewardHandler.matchesBlockCondition(reward, Blocks.STONE.defaultBlockState())).isFalse();
    }

    @Test
    void matchesTagConditionAgainstBrokenState() {
        var reward = new RewardDefinition(
                ResourceLocation.fromNamespaceAndPath("ae2", "break_natural_log_idle"),
                RewardTriggerType.BLOCK_BREAK,
                new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "idle")),
                20,
                new RewardDefinition.BlockFilterCondition(
                        null,
                        ResourceLocation.fromNamespaceAndPath("minecraft", "logs")),
                null);

        var state = mock(BlockState.class);
        when(state.is(org.mockito.ArgumentMatchers.<TagKey<Block>>any())).thenReturn(true);

        assertThat(BlockBreakRewardHandler.matchesBlockCondition(reward, state)).isTrue();
    }

    @Test
    void doesNotMatchWhenRewardHasNoConditions() {
        var reward = new RewardDefinition(
                ResourceLocation.fromNamespaceAndPath("ae2", "bad"),
                RewardTriggerType.BLOCK_BREAK,
                new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "idle")),
                20,
                null,
                null);

        assertThat(BlockBreakRewardHandler.matchesBlockCondition(reward, Blocks.OAK_LOG.defaultBlockState())).isFalse();
    }

    private static RewardDefinition rewardForBlock(String rewardPath, String blockNamespace, String blockPath,
            long ticksAwarded) {
        return new RewardDefinition(
                ResourceLocation.fromNamespaceAndPath("ae2", rewardPath),
                RewardTriggerType.BLOCK_BREAK,
                new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "idle")),
                ticksAwarded,
                new RewardDefinition.BlockFilterCondition(
                        ResourceLocation.fromNamespaceAndPath(blockNamespace, blockPath),
                        null),
                null);
    }

    private static RewardDefinition rewardForTag(String rewardPath, String tagNamespace, String tagPath,
            long ticksAwarded) {
        return new RewardDefinition(
                ResourceLocation.fromNamespaceAndPath("ae2", rewardPath),
                RewardTriggerType.BLOCK_BREAK,
                new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "idle")),
                ticksAwarded,
                new RewardDefinition.BlockFilterCondition(
                        null,
                        ResourceLocation.fromNamespaceAndPath(tagNamespace, tagPath)),
                null);
    }

    private static BlockEvent.BreakEvent blockBreakEvent(ServerPlayer player, BlockState state) {
        var event = mock(BlockEvent.BreakEvent.class);
        var level = mock(ServerLevel.class);

        when(event.isCanceled()).thenReturn(false);
        when(event.getLevel()).thenReturn(level);
        when(level.isClientSide()).thenReturn(false);
        when(event.getPos()).thenReturn(BlockPos.ZERO);
        when(event.getPlayer()).thenReturn(player);
        when(event.getState()).thenReturn(state);

        return event;
    }
}
