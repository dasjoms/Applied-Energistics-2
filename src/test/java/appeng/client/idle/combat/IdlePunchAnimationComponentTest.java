package appeng.client.idle.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

class IdlePunchAnimationComponentTest {

    @AfterEach
    void resetComponentState() {
        IdlePunchAnimationComponent.resetServerStateTracking();
    }

    @Test
    void matchingAuthoritativeSwingKeepsPredictedAnimationTiming() {
        var fixture = createFixture();
        when(fixture.level.getGameTime()).thenReturn(100L, 101L);

        IdlePunchAnimationComponent.startPredictedSwing(fixture.player, InteractionHand.MAIN_HAND);
        IdlePunchAnimationComponent.applyServerConfirmedSwing(fixture.player, InteractionHand.MAIN_HAND, 7L);

        assertThat(IdlePunchAnimationComponent.getActiveHand()).isEqualTo(InteractionHand.MAIN_HAND);
        assertThat(IdlePunchAnimationComponent.getSwingStartTick()).isEqualTo(100L);
    }

    @Test
    void mismatchedAuthoritativeSwingImmediatelyCorrectsHandAndTiming() {
        var fixture = createFixture();
        when(fixture.level.getGameTime()).thenReturn(200L, 201L);

        IdlePunchAnimationComponent.startPredictedSwing(fixture.player, InteractionHand.MAIN_HAND);
        IdlePunchAnimationComponent.applyServerConfirmedSwing(fixture.player, InteractionHand.OFF_HAND, 8L);

        assertThat(IdlePunchAnimationComponent.getActiveHand()).isEqualTo(InteractionHand.OFF_HAND);
        assertThat(IdlePunchAnimationComponent.getSwingStartTick()).isEqualTo(201L);
    }

    @Test
    void olderServerSequenceIsIgnored() {
        var fixture = createFixture();
        when(fixture.level.getGameTime()).thenReturn(400L, 401L, 402L);

        IdlePunchAnimationComponent.startPredictedSwing(fixture.player, InteractionHand.MAIN_HAND);
        IdlePunchAnimationComponent.applyServerConfirmedSwing(fixture.player, InteractionHand.MAIN_HAND, 10L);
        IdlePunchAnimationComponent.applyServerConfirmedSwing(fixture.player, InteractionHand.OFF_HAND, 9L);

        assertThat(IdlePunchAnimationComponent.getActiveHand()).isEqualTo(InteractionHand.MAIN_HAND);
        assertThat(IdlePunchAnimationComponent.getSwingStartTick()).isEqualTo(400L);
    }

    @Test
    void resetServerStateTrackingClearsPendingPrediction() {
        var fixture = createFixture();
        when(fixture.level.getGameTime()).thenReturn(300L, 301L, 302L);

        IdlePunchAnimationComponent.startPredictedSwing(fixture.player, InteractionHand.OFF_HAND);
        IdlePunchAnimationComponent.resetServerStateTracking();
        IdlePunchAnimationComponent.applyServerConfirmedSwing(fixture.player, InteractionHand.MAIN_HAND, 1L);

        assertThat(IdlePunchAnimationComponent.getActiveHand()).isEqualTo(InteractionHand.MAIN_HAND);
        assertThat(IdlePunchAnimationComponent.getSwingStartTick()).isEqualTo(301L);
    }

    private static Fixture createFixture() {
        var player = mock(Player.class);
        var level = mock(Level.class);
        when(player.level()).thenReturn(level);
        when(player.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SPEED)).thenReturn(false);
        when(player.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN)).thenReturn(false);
        return new Fixture(player, level);
    }

    private record Fixture(Player player, Level level) {
    }
}
