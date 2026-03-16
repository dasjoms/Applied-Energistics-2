package appeng.idle.reward.timber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import appeng.idle.reward.natural.NaturalLogTracker;

class TimberChopServiceTest {
    @Test
    void exactLimitClusterAccepted() {
        var level = mockLevelWithStates(new HashMap<>() {
            {
                put(new BlockPos(0, 0, 0), Blocks.OAK_LOG.defaultBlockState());
                put(new BlockPos(1, 0, 0), Blocks.OAK_LOG.defaultBlockState());
                put(new BlockPos(2, 0, 0), Blocks.OAK_LOG.defaultBlockState());
            }
        });

        try (MockedStatic<NaturalLogTracker> naturalLogTracker = mockStatic(NaturalLogTracker.class)) {
            naturalLogTracker.when(() -> NaturalLogTracker.isEligibleLogForReward(any(), any(), any()))
                    .thenAnswer(invocation -> {
                        var state = invocation.getArgument(2, BlockState.class);
                        return state.is(Blocks.OAK_LOG);
                    });

            var result = TimberChopService.collectEligibleLogs(level, new BlockPos(0, 0, 0), 3);

            assertThat(result.status()).isEqualTo(TimberChopService.Status.WITHIN_LIMIT);
            assertThat(result.collectedPositions()).hasSize(3)
                    .containsExactlyInAnyOrder(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0));
        }
    }

    @Test
    void limitPlusOneRejected() {
        var level = mockLevelWithStates(new HashMap<>() {
            {
                put(new BlockPos(0, 0, 0), Blocks.OAK_LOG.defaultBlockState());
                put(new BlockPos(1, 0, 0), Blocks.OAK_LOG.defaultBlockState());
                put(new BlockPos(2, 0, 0), Blocks.OAK_LOG.defaultBlockState());
                put(new BlockPos(3, 0, 0), Blocks.OAK_LOG.defaultBlockState());
            }
        });

        try (MockedStatic<NaturalLogTracker> naturalLogTracker = mockStatic(NaturalLogTracker.class)) {
            naturalLogTracker.when(() -> NaturalLogTracker.isEligibleLogForReward(any(), any(), any()))
                    .thenAnswer(invocation -> {
                        var state = invocation.getArgument(2, BlockState.class);
                        return state.is(Blocks.OAK_LOG);
                    });

            var result = TimberChopService.collectEligibleLogs(level, new BlockPos(0, 0, 0), 3);

            assertThat(result.status()).isEqualTo(TimberChopService.Status.EXCEEDS_LIMIT);
            assertThat(result.collectedPositions()).isEmpty();
            assertThat(result.oversizedComponentSamplePositions()).isNotEmpty();
        }
    }

    @Test
    void playerPlacedLogsExcludedFromTraversal() {
        var start = new BlockPos(0, 0, 0);
        var playerPlaced = new BlockPos(1, 0, 0);
        var behindPlayerPlaced = new BlockPos(2, 0, 0);

        var level = mockLevelWithStates(new HashMap<>() {
            {
                put(start, Blocks.OAK_LOG.defaultBlockState());
                put(playerPlaced, Blocks.OAK_LOG.defaultBlockState());
                put(behindPlayerPlaced, Blocks.OAK_LOG.defaultBlockState());
            }
        });

        try (MockedStatic<NaturalLogTracker> naturalLogTracker = mockStatic(NaturalLogTracker.class)) {
            naturalLogTracker.when(() -> NaturalLogTracker.isEligibleLogForReward(any(), any(), any()))
                    .thenAnswer(invocation -> {
                        var pos = invocation.getArgument(1, BlockPos.class);
                        var state = invocation.getArgument(2, BlockState.class);
                        return state.is(Blocks.OAK_LOG) && !playerPlaced.equals(pos);
                    });

            var result = TimberChopService.collectEligibleLogs(level, start, 10);

            assertThat(result.status()).isEqualTo(TimberChopService.Status.WITHIN_LIMIT);
            assertThat(result.collectedPositions()).containsExactly(start);
            assertThat(result.collectedPositions()).doesNotContain(playerPlaced, behindPlayerPlaced);
        }
    }

    @Test
    void playerPlacedStartLogDoesNotChain() {
        var start = new BlockPos(0, 0, 0);
        var adjacentNatural = new BlockPos(1, 0, 0);

        var level = mockLevelWithStates(new HashMap<>() {
            {
                put(start, Blocks.OAK_LOG.defaultBlockState());
                put(adjacentNatural, Blocks.OAK_LOG.defaultBlockState());
            }
        });

        try (MockedStatic<NaturalLogTracker> naturalLogTracker = mockStatic(NaturalLogTracker.class)) {
            naturalLogTracker.when(() -> NaturalLogTracker.isEligibleLogForReward(any(), any(), any()))
                    .thenAnswer(invocation -> !start.equals(invocation.getArgument(1, BlockPos.class)));

            var result = TimberChopService.collectEligibleLogs(level, start, 10);

            assertThat(result.status()).isEqualTo(TimberChopService.Status.INELIGIBLE_OR_NON_LOG);
            assertThat(result.collectedPositions()).isEmpty();
        }
    }

    @Test
    void nonLogStartBlockRejected() {
        var start = new BlockPos(0, 0, 0);
        var level = mockLevelWithStates(new HashMap<>() {
            {
                put(start, Blocks.STONE.defaultBlockState());
            }
        });

        try (MockedStatic<NaturalLogTracker> naturalLogTracker = mockStatic(NaturalLogTracker.class)) {
            naturalLogTracker.when(() -> NaturalLogTracker.isEligibleLogForReward(any(), any(), any()))
                    .thenReturn(false);

            var result = TimberChopService.collectEligibleLogs(level, start, 5);

            assertThat(result.status()).isEqualTo(TimberChopService.Status.INELIGIBLE_OR_NON_LOG);
            assertThat(result.collectedPositions()).isEmpty();
        }
    }

    @Test
    void oversizedComponentSampleIsDeterministicallySorted() {
        var start = new BlockPos(0, 0, 0);
        var first = new BlockPos(-1, -1, -1);
        var second = new BlockPos(-1, -1, 0);
        var third = new BlockPos(-1, -1, 1);

        var level = mockLevelWithStates(new HashMap<>() {
            {
                put(start, Blocks.OAK_LOG.defaultBlockState());
                put(first, Blocks.OAK_LOG.defaultBlockState());
                put(second, Blocks.OAK_LOG.defaultBlockState());
                put(third, Blocks.OAK_LOG.defaultBlockState());
            }
        });

        try (MockedStatic<NaturalLogTracker> naturalLogTracker = mockStatic(NaturalLogTracker.class)) {
            naturalLogTracker.when(() -> NaturalLogTracker.isEligibleLogForReward(any(), any(), any()))
                    .thenAnswer(invocation -> {
                        var state = invocation.getArgument(2, BlockState.class);
                        return state.is(Blocks.OAK_LOG);
                    });

            var result = TimberChopService.collectEligibleLogs(level, start, 3);

            assertThat(result.status()).isEqualTo(TimberChopService.Status.EXCEEDS_LIMIT);
            assertThat(result.collectedPositions()).isEmpty();
            assertThat(result.oversizedComponentSamplePositions()).containsExactly(first, second, third, start);
        }
    }

    @Test
    void diagonalOnlyConnectionIncluded() {
        var start = new BlockPos(0, 0, 0);
        var diagonal = new BlockPos(1, 1, 1);

        var level = mockLevelWithStates(new HashMap<>() {
            {
                put(start, Blocks.OAK_LOG.defaultBlockState());
                put(diagonal, Blocks.OAK_LOG.defaultBlockState());
            }
        });

        try (MockedStatic<NaturalLogTracker> naturalLogTracker = mockStatic(NaturalLogTracker.class)) {
            naturalLogTracker.when(() -> NaturalLogTracker.isEligibleLogForReward(any(), any(), any()))
                    .thenAnswer(invocation -> {
                        var state = invocation.getArgument(2, BlockState.class);
                        return state.is(Blocks.OAK_LOG);
                    });

            var result = TimberChopService.collectEligibleLogs(level, start, 2);

            assertThat(result.status()).isEqualTo(TimberChopService.Status.WITHIN_LIMIT);
            assertThat(result.collectedPositions()).containsExactlyInAnyOrder(start, diagonal);
        }
    }

    @Test
    void diagonalChainCanExceedLimit() {
        var start = new BlockPos(0, 0, 0);
        var diagonalOne = new BlockPos(1, 1, 1);
        var diagonalTwo = new BlockPos(2, 2, 2);

        var level = mockLevelWithStates(new HashMap<>() {
            {
                put(start, Blocks.OAK_LOG.defaultBlockState());
                put(diagonalOne, Blocks.OAK_LOG.defaultBlockState());
                put(diagonalTwo, Blocks.OAK_LOG.defaultBlockState());
            }
        });

        try (MockedStatic<NaturalLogTracker> naturalLogTracker = mockStatic(NaturalLogTracker.class)) {
            naturalLogTracker.when(() -> NaturalLogTracker.isEligibleLogForReward(any(), any(), any()))
                    .thenAnswer(invocation -> {
                        var state = invocation.getArgument(2, BlockState.class);
                        return state.is(Blocks.OAK_LOG);
                    });

            var result = TimberChopService.collectEligibleLogs(level, start, 2);

            assertThat(result.status()).isEqualTo(TimberChopService.Status.EXCEEDS_LIMIT);
            assertThat(result.collectedPositions()).isEmpty();
            assertThat(result.oversizedComponentSamplePositions()).isNotEmpty();
        }
    }

    private static ServerLevel mockLevelWithStates(HashMap<BlockPos, BlockState> states) {
        var level = mock(ServerLevel.class);
        when(level.getBlockState(any())).thenAnswer(invocation -> {
            var pos = invocation.getArgument(0, BlockPos.class);
            return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        });
        return level;
    }
}
