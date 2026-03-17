package appeng.client.gui.overlay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdleCombatHudOverlayRendererTest {

    @Test
    void getHotbarAnchorUsesCenteredVanillaHotbarCoordinates() {
        var anchor = IdleCombatHudOverlayRenderer.getHotbarAnchor(320, 240);

        assertThat(anchor.hotbarLeft()).isEqualTo(69);
        assertThat(anchor.hotbarRight()).isEqualTo(251);
        assertThat(anchor.hotbarY()).isEqualTo(218);
    }

    @Test
    void getFillFractionShowsFullBarWhenReadyAndClampsOutOfRange() {
        assertThat(IdleCombatHudOverlayRenderer.getFillFraction(0, 20)).isEqualTo(1.0);
        assertThat(IdleCombatHudOverlayRenderer.getFillFraction(-5, 20)).isEqualTo(1.0);
        assertThat(IdleCombatHudOverlayRenderer.getFillFraction(5, 20)).isEqualTo(0.25);
        assertThat(IdleCombatHudOverlayRenderer.getFillFraction(50, 20)).isEqualTo(1.0);
        assertThat(IdleCombatHudOverlayRenderer.getFillFraction(10, 0)).isEqualTo(0.0);
    }

    @Test
    void getFillHeightKeepsVisibleProgressForSmallFractionsAndClampsBounds() {
        assertThat(IdleCombatHudOverlayRenderer.getFillHeight(20, 0.0)).isEqualTo(0);
        assertThat(IdleCombatHudOverlayRenderer.getFillHeight(20, 0.01)).isEqualTo(1);
        assertThat(IdleCombatHudOverlayRenderer.getFillHeight(20, 0.5)).isEqualTo(10);
        assertThat(IdleCombatHudOverlayRenderer.getFillHeight(20, 2.0)).isEqualTo(20);
    }
}
