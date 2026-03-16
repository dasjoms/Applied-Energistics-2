package appeng.idle.reward.timber;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import appeng.idle.reward.natural.NaturalLogTracker;

public final class TimberChopService {
    private static final int OVERSIZED_COMPONENT_SAMPLE_LIMIT = 128;

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
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }

                        var candidate = current.offset(dx, dy, dz);
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
                            return TimberChopResult.exceedsLimit(sampleForOversizedComponent(visited));
                        }

                        frontier.addLast(immutableCandidate);
                    }
                }
            }
        }

        return TimberChopResult.withinLimit(List.copyOf(visited));
    }

    private static boolean isEligibleLog(ServerLevel level, BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        return NaturalLogTracker.isEligibleLogForReward(level, pos, state);
    }

    private static List<BlockPos> sampleForOversizedComponent(Set<BlockPos> visited) {
        if (visited.isEmpty()) {
            return List.of();
        }

        return visited.stream()
                .sorted((a, b) -> {
                    var compareX = Integer.compare(a.getX(), b.getX());
                    if (compareX != 0) {
                        return compareX;
                    }

                    var compareY = Integer.compare(a.getY(), b.getY());
                    if (compareY != 0) {
                        return compareY;
                    }

                    return Integer.compare(a.getZ(), b.getZ());
                })
                .limit(OVERSIZED_COMPONENT_SAMPLE_LIMIT)
                .toList();
    }

    public record TimberChopResult(Status status, List<BlockPos> collectedPositions,
            List<BlockPos> oversizedComponentSamplePositions) {
        public TimberChopResult {
            collectedPositions = List.copyOf(collectedPositions);
            oversizedComponentSamplePositions = List.copyOf(oversizedComponentSamplePositions);
        }

        public static TimberChopResult disabledNoUpgrade() {
            return new TimberChopResult(Status.DISABLED_NO_UPGRADE, List.of(), List.of());
        }

        public static TimberChopResult ineligibleOrNonLog() {
            return new TimberChopResult(Status.INELIGIBLE_OR_NON_LOG, List.of(), List.of());
        }

        public static TimberChopResult exceedsLimit(List<BlockPos> oversizedComponentSamplePositions) {
            return new TimberChopResult(Status.EXCEEDS_LIMIT, List.of(), oversizedComponentSamplePositions);
        }

        public static TimberChopResult exceedsLimit() {
            return exceedsLimit(List.of());
        }

        public static TimberChopResult withinLimit(List<BlockPos> collectedPositions) {
            return new TimberChopResult(Status.WITHIN_LIMIT, collectedPositions, List.of());
        }
    }

    public enum Status {
        DISABLED_NO_UPGRADE,
        INELIGIBLE_OR_NON_LOG,
        EXCEEDS_LIMIT,
        WITHIN_LIMIT
    }
}
