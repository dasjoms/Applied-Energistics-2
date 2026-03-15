package appeng.idle.reward.natural;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NaturalLogTrackerPolicyTest {
    @Test
    void placedLogDenied() {
        assertThat(NaturalLogTracker.isProvenanceNatural(NaturalLogTracker.Provenance.PLAYER_PLACED, false, false))
                .isFalse();
    }

    @Test
    void worldgenLogAccepted() {
        assertThat(NaturalLogTracker.isProvenanceNatural(NaturalLogTracker.Provenance.NATURAL_WORLDGEN, false, false))
                .isTrue();
    }

    @Test
    void saplingGrownLogFollowsConfig() {
        assertThat(NaturalLogTracker.isProvenanceNatural(NaturalLogTracker.Provenance.SAPLING_GROWN, false, false))
                .isFalse();
        assertThat(NaturalLogTracker.isProvenanceNatural(NaturalLogTracker.Provenance.SAPLING_GROWN, false, true))
                .isTrue();
    }

    @Test
    void unknownLegacyLogFollowsConfig() {
        assertThat(NaturalLogTracker.isProvenanceNatural(NaturalLogTracker.Provenance.UNKNOWN, false, false)).isFalse();
        assertThat(NaturalLogTracker.isProvenanceNatural(NaturalLogTracker.Provenance.UNKNOWN, true, false)).isTrue();
    }
}
