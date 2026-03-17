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
    void barPositionHelpersPlaceBarsAroundHotbarBounds() {
        var anchor = IdleCombatHudOverlayRenderer.getHotbarAnchor(320, 240);

        assertThat(IdleCombatHudOverlayRenderer.getLeftBarX(anchor)).isEqualTo(63);
        assertThat(IdleCombatHudOverlayRenderer.getRightBarX(anchor)).isEqualTo(253);
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
        assertThat(IdleCombatHudOverlayRenderer.getFillHeight(20, 0.0001)).isEqualTo(1);
        assertThat(IdleCombatHudOverlayRenderer.getFillHeight(20, 0.5)).isEqualTo(10);
        assertThat(IdleCombatHudOverlayRenderer.getFillHeight(20, 2.0)).isEqualTo(20);
    }

    @Test
    void getFillColorUsesGreenWhenReadyAndRedWhileCoolingDown() {
        assertThat(IdleCombatHudOverlayRenderer.getFillColor(0)).isEqualTo(0xFF58FF58);
        assertThat(IdleCombatHudOverlayRenderer.getFillColor(-3)).isEqualTo(0xFF58FF58);
        assertThat(IdleCombatHudOverlayRenderer.getFillColor(1)).isEqualTo(0xFFFF5050);
    }
}
