package appeng.idle.net;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdleCurrencySyncServiceTest {

    @Test
    void projectedDisplayProgressAdvancesFromPersistedBaseline() {
        var projected = IdleCurrencySyncService.projectDisplayProgressTicks(15L, 100L, 7L);

        assertThat(projected).isEqualTo(22L);
    }

    @Test
    void projectedDisplayProgressWrapsAtTicksPerUnitBoundary() {
        var projected = IdleCurrencySyncService.projectDisplayProgressTicks(18L, 20L, 5L);

        assertThat(projected).isEqualTo(3L);
    }

    @Test
    void projectedDisplayProgressClampsNegativeBaselineAndElapsed() {
        var projected = IdleCurrencySyncService.projectDisplayProgressTicks(-2L, 20L, -4L);

        assertThat(projected).isZero();
    }

    @Test
    void projectedDisplayProgressReturnsZeroWhenTicksPerUnitIsNonPositive() {
        assertThat(IdleCurrencySyncService.projectDisplayProgressTicks(10L, 0L, 5L)).isZero();
        assertThat(IdleCurrencySyncService.projectDisplayProgressTicks(10L, -1L, 5L)).isZero();
    }

}
