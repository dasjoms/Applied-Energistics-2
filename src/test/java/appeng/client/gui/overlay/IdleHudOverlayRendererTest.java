package appeng.client.gui.overlay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import appeng.idle.net.IdleCurrencyHudValue;

class IdleHudOverlayRendererTest {

    @Test
    void getFillWidthReservesAtLeastOnePixelForVisibleProgress() {
        assertThat(IdleHudOverlayRenderer.getFillWidth(20, 0.0)).isEqualTo(0);
        assertThat(IdleHudOverlayRenderer.getFillWidth(20, 0.01)).isEqualTo(1);
        assertThat(IdleHudOverlayRenderer.getFillWidth(20, 0.5)).isEqualTo(10);
        assertThat(IdleHudOverlayRenderer.getFillWidth(20, 2.0)).isEqualTo(20);
    }

    @Test
    void getProgressStateNormalizesTicksToPerCurrencyFraction() {
        var fastCurrency = new IdleCurrencyHudValue(0L, 1.0, 10L, 20L, 1L);
        var slowCurrency = new IdleCurrencyHudValue(0L, 1.0, 10L, 200L, 10L);

        var fastState = IdleHudOverlayRenderer.getProgressState(fastCurrency);
        var slowState = IdleHudOverlayRenderer.getProgressState(slowCurrency);

        assertThat(fastState.progressFraction()).isEqualTo(0.5f);
        assertThat(slowState.progressFraction()).isEqualTo(0.05f);
        assertThat(fastState.timingText()).isEqualTo("0.5s");
        assertThat(slowState.timingText()).isEqualTo("9.5s");
    }

    @Test
    void getProgressStateClampsInvalidProgressValues() {
        var negativeProgress = new IdleCurrencyHudValue(0L, 1.0, -10L, 40L, 2L);
        var overflowingProgress = new IdleCurrencyHudValue(0L, 1.0, 80L, 40L, 0L);

        var negativeState = IdleHudOverlayRenderer.getProgressState(negativeProgress);
        var overflowingState = IdleHudOverlayRenderer.getProgressState(overflowingProgress);

        assertThat(negativeState.progressFraction()).isEqualTo(0f);
        assertThat(overflowingState.progressFraction()).isEqualTo(1f);
    }

    @Test
    void getProgressStateUsesFallbackWhenCappedOrTimingIsInvalid() {
        var cappedValue = new IdleCurrencyHudValue(0L, 0.0, 10L, 20L, null);
        var invalidTiming = new IdleCurrencyHudValue(0L, 2.0, 10L, 0L, null);

        var cappedState = IdleHudOverlayRenderer.getProgressState(cappedValue);
        var invalidTimingState = IdleHudOverlayRenderer.getProgressState(invalidTiming);

        assertThat(cappedState.progressFraction()).isEqualTo(0f);
        assertThat(cappedState.timingText()).isEqualTo("--");
        assertThat(invalidTimingState.progressFraction()).isEqualTo(0f);
        assertThat(invalidTimingState.timingText()).isEqualTo("--");
    }

    @Test
    void getBarWidthIsLimitedToLeftBoundScreenFraction() {
        assertThat(IdleHudOverlayRenderer.getBarWidth(300, 90, 60)).isEqualTo(60);
        assertThat(IdleHudOverlayRenderer.getBarWidth(300, 260, 60)).isEqualTo(34);
        assertThat(IdleHudOverlayRenderer.getBarWidth(300, 295, 60)).isEqualTo(0);
    }
}
