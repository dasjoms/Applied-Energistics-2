package appeng.idle.reward.natural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

class NaturalLogTrackerPolicyTest {
    @Test
    void placedLogDenied() {
        assertThat(NaturalLogTracker.isProvenanceEligibleForReward(NaturalLogTracker.Provenance.PLAYER_PLACED))
                .isFalse();
    }

    @Test
    void worldgenLogAccepted() {
        assertThat(NaturalLogTracker.isProvenanceEligibleForReward(NaturalLogTracker.Provenance.NATURAL_WORLDGEN))
                .isTrue();
    }

    @Test
    void saplingGrownLogAccepted() {
        assertThat(NaturalLogTracker.isProvenanceEligibleForReward(NaturalLogTracker.Provenance.SAPLING_GROWN))
                .isTrue();
    }

    @Test
    void unknownLegacyLogAccepted() {
        assertThat(NaturalLogTracker.isProvenanceEligibleForReward(NaturalLogTracker.Provenance.UNKNOWN)).isTrue();
    }

    @Test
    void entityPlacedLogOnlyMarkedAsPlayerPlacedWhenEntityIsPlayer() {
        var player = mock(Player.class);
        var nonPlayer = mock(Entity.class);
        var logState = mockLogState();

        assertThat(NaturalLogTracker.shouldMarkPlayerPlaced(logState, player)).isTrue();
        assertThat(NaturalLogTracker.shouldMarkPlayerPlaced(logState, nonPlayer)).isFalse();
    }

    @Test
    void nonPlayerPlacementStaysEligibleForNaturalLogRewards() {
        var nonPlayer = mock(Entity.class);

        assertThat(NaturalLogTracker.shouldMarkPlayerPlaced(mockLogState(), nonPlayer)).isFalse();
        assertThat(NaturalLogTracker.isProvenanceEligibleForReward(NaturalLogTracker.Provenance.UNKNOWN)).isTrue();
    }

    private static BlockState mockLogState() {
        var state = mock(BlockState.class);
        when(state.is(org.mockito.ArgumentMatchers.<TagKey<Block>>any())).thenReturn(true);
        return state;
    }
}
