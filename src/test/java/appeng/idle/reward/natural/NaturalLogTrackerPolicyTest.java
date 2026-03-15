package appeng.idle.reward.natural;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
}
