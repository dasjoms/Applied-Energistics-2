package appeng.idle.tick;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdleGenerationTickerTest {
    @Test
    void computesOfflineGeneratedUsingBaseRateAndPercent() {
        var generated = IdleGenerationTicker.computeOfflineGenerated(1L, 10L, 100L, 0.25, 1.0);

        assertThat(generated).isEqualTo(50L);
    }

    @Test
    void capsOfflineSecondsBeforeApplyingCatchup() {
        var generated = IdleGenerationTicker.computeOfflineGenerated(2L, 120L, 30L, 0.5, 1.0);

        assertThat(generated).isEqualTo(600L);
    }

    @Test
    void returnsZeroForInvalidInputs() {
        assertThat(IdleGenerationTicker.computeOfflineGenerated(0L, 10L, 10L, 0.25, 1.0)).isZero();
        assertThat(IdleGenerationTicker.computeOfflineGenerated(1L, 0L, 10L, 0.25, 1.0)).isZero();
        assertThat(IdleGenerationTicker.computeOfflineGenerated(1L, 10L, 0L, 0.25, 1.0)).isZero();
        assertThat(IdleGenerationTicker.computeOfflineGenerated(1L, 10L, 10L, 0.0, 1.0)).isZero();
        assertThat(IdleGenerationTicker.computeOfflineGenerated(1L, 10L, 10L, 0.25, 0.0)).isZero();
    }
}
