package appeng.idle.reward.natural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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

    @Test
    void trackerDataOnlyStoresDeniedPlayerPlacedPositions() {
        var data = new NaturalLogTracker.NaturalLogTrackerData();
        var playerPlacedPos = new BlockPos(1, 64, 1);
        var naturalPos = new BlockPos(2, 64, 2);

        data.setProvenance(playerPlacedPos, NaturalLogTracker.Provenance.PLAYER_PLACED);
        data.setProvenance(naturalPos, NaturalLogTracker.Provenance.NATURAL_WORLDGEN);

        assertThat(data.getProvenance(playerPlacedPos)).isEqualTo(NaturalLogTracker.Provenance.PLAYER_PLACED);
        assertThat(data.getProvenance(naturalPos)).isEqualTo(NaturalLogTracker.Provenance.UNKNOWN);

        var saved = data.save(new CompoundTag(), mock(net.minecraft.core.HolderLookup.Provider.class));
        assertThat(saved.getLongArray("positions")).containsExactly(playerPlacedPos.asLong());
        assertThat(saved.getByteArray("provenance"))
                .containsExactly(NaturalLogTracker.Provenance.PLAYER_PLACED.id());
    }

    @Test
    void trackerDataLoadFiltersOutNonDeniedProvenanceEntries() {
        var playerPlacedPos = new BlockPos(10, 70, 10);
        var naturalPos = new BlockPos(11, 70, 11);
        var saplingPos = new BlockPos(12, 70, 12);
        var tag = new CompoundTag();
        tag.putLongArray("positions",
                new long[] { playerPlacedPos.asLong(), naturalPos.asLong(), saplingPos.asLong() });
        tag.putByteArray("provenance", new byte[] {
                NaturalLogTracker.Provenance.PLAYER_PLACED.id(),
                NaturalLogTracker.Provenance.NATURAL_WORLDGEN.id(),
                NaturalLogTracker.Provenance.SAPLING_GROWN.id()
        });

        var data = NaturalLogTracker.NaturalLogTrackerData.load(tag);

        assertThat(data.getProvenance(playerPlacedPos)).isEqualTo(NaturalLogTracker.Provenance.PLAYER_PLACED);
        assertThat(data.getProvenance(naturalPos)).isEqualTo(NaturalLogTracker.Provenance.UNKNOWN);
        assertThat(data.getProvenance(saplingPos)).isEqualTo(NaturalLogTracker.Provenance.UNKNOWN);
    }

    private static BlockState mockLogState() {
        var state = mock(BlockState.class);
        when(state.is(org.mockito.ArgumentMatchers.<TagKey<Block>>any())).thenReturn(true);
        return state;
    }
}
