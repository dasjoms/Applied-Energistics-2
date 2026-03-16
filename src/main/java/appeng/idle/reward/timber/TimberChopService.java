package appeng.idle.reward.timber;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;

import appeng.idle.reward.natural.NaturalLogTracker;

public final class TimberChopService {
    private TimberChopService() {
    }

    public static TimberChopResult collectEligibleLogs(ServerLevel level, BlockPos brokenLogPos, int maxAllowedLogs) {
        if (maxAllowedLogs <= 0) {
            return TimberChopResult.disabledNoUpgrade();
        }

        var startState = level.getBlockState(brokenLogPos);
        if (!isEligibleLog(level, brokenLogPos, startState)) {
            return TimberChopResult.ineligibleOrNonLog();
        }

        Set<BlockPos> visited = new HashSet<>();
        var frontier = new ArrayDeque<BlockPos>();

        var start = brokenLogPos.immutable();
        visited.add(start);
        frontier.add(start);

        while (!frontier.isEmpty()) {
            var current = frontier.removeFirst();
            for (var direction : Direction.values()) {
                var candidate = current.relative(direction);
                if (visited.contains(candidate)) {
                    continue;
                }

                var candidateState = level.getBlockState(candidate);
                if (!isEligibleLog(level, candidate, candidateState)) {
                    continue;
                }

                var immutableCandidate = candidate.immutable();
                visited.add(immutableCandidate);
                if (visited.size() > maxAllowedLogs) {
                    return TimberChopResult.exceedsLimit();
                }

                frontier.addLast(immutableCandidate);
            }
        }

        return TimberChopResult.withinLimit(List.copyOf(visited));
    }

    private static boolean isEligibleLog(ServerLevel level, BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        return state.is(BlockTags.LOGS) && NaturalLogTracker.isEligibleLogForReward(level, pos, state);
    }

    public record TimberChopResult(Status status, List<BlockPos> collectedPositions) {
        public TimberChopResult {
            collectedPositions = List.copyOf(collectedPositions);
        }

        public static TimberChopResult disabledNoUpgrade() {
            return new TimberChopResult(Status.DISABLED_NO_UPGRADE, List.of());
        }

        public static TimberChopResult ineligibleOrNonLog() {
            return new TimberChopResult(Status.INELIGIBLE_OR_NON_LOG, List.of());
        }

        public static TimberChopResult exceedsLimit() {
            return new TimberChopResult(Status.EXCEEDS_LIMIT, List.of());
        }

        public static TimberChopResult withinLimit(List<BlockPos> collectedPositions) {
            return new TimberChopResult(Status.WITHIN_LIMIT, collectedPositions);
        }
    }

    public enum Status {
        DISABLED_NO_UPGRADE,
        INELIGIBLE_OR_NON_LOG,
        EXCEEDS_LIMIT,
        WITHIN_LIMIT
    }
}
