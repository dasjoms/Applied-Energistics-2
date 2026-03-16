package appeng.client.idle.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.core.definitions.AEItems;
import appeng.idle.net.IdleCurrencyClientCache;

class IdlePunchAnimationComponentTest {

    @AfterEach
    void resetComponentState() {
        IdlePunchAnimationComponent.resetServerStateTracking();
        IdleCurrencyClientCache.applySnapshot(java.util.Map.of(), java.util.Map.of(), false);
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

    @Test
    void rendersOffHandInIdlePunchModeWithoutActiveSwing() {
        var fixture = createFixture();
        IdleCurrencyClientCache.applySnapshot(java.util.Map.of(), java.util.Map.of(), true);

        assertThat(IdlePunchAnimationComponent.shouldRenderIdleHand(fixture.player, InteractionHand.OFF_HAND)).isTrue();
        assertThat(IdlePunchAnimationComponent.shouldRenderIdleHand(fixture.player, InteractionHand.MAIN_HAND))
                .isFalse();
    }

    @Test
    void doesNotRenderOffHandWhenIdlePunchRequirementsFail() {
        var fixture = createFixture();
        IdleCurrencyClientCache.applySnapshot(java.util.Map.of(), java.util.Map.of(), true);

        when(fixture.player.getOffhandItem()).thenReturn(new ItemStack(Items.STICK));
        assertThat(IdlePunchAnimationComponent.shouldRenderIdleHand(fixture.player, InteractionHand.OFF_HAND))
                .isFalse();

        when(fixture.player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        when(fixture.player.getItemBySlot(EquipmentSlot.HEAD)).thenReturn(ItemStack.EMPTY);
        assertThat(IdlePunchAnimationComponent.shouldRenderIdleHand(fixture.player, InteractionHand.OFF_HAND))
                .isFalse();

        when(fixture.player.getItemBySlot(EquipmentSlot.HEAD)).thenReturn(AEItems.IDLE_VISOR.stack());
        IdleCurrencyClientCache.applySnapshot(java.util.Map.of(), java.util.Map.of(), false);
        assertThat(IdlePunchAnimationComponent.shouldRenderIdleHand(fixture.player, InteractionHand.OFF_HAND))
                .isFalse();
    }

    private static Fixture createFixture() {
        var player = mock(Player.class);
        var level = mock(Level.class);
        when(player.level()).thenReturn(level);
        when(player.getItemBySlot(EquipmentSlot.HEAD)).thenReturn(AEItems.IDLE_VISOR.stack());
        when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        when(player.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SPEED)).thenReturn(false);
        when(player.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN)).thenReturn(false);
        return new Fixture(player, level);
    }

    private record Fixture(Player player, Level level) {
    }
}
