package appeng.hooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;

import appeng.client.idle.combat.IdlePunchAnimationComponent;
import appeng.core.definitions.AEItems;
import appeng.idle.net.IdleCurrencyClientCache;

class IdlePunchAttackHookTest {

    private static final Method GET_IDLE_PUNCH_HAND = getIdlePunchHandMethod();
    private static final Method TRIGGER_LOCAL_SWING_FOR_NON_ENTITY = getTriggerLocalSwingForNonEntityMethod();

    @AfterEach
    void resetIdlePunchEligibility() {
        IdleCurrencyClientCache.applySnapshot(Map.of(), Map.of(), false);
        IdlePunchAttackHook.resetClientCooldowns();
        IdlePunchAnimationComponent.resetServerStateTracking();
    }

    @Test
    void shouldNotTakeOverAttackInputWithoutCombatEligibility() {
        var player = mock(Player.class);
        when(player.getItemBySlot(EquipmentSlot.HEAD)).thenReturn(AEItems.IDLE_VISOR.stack());
        when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        IdleCurrencyClientCache.applySnapshot(Map.of(), Map.of(), false);

        assertThat(IdlePunchAttackHook.shouldTakeOverAttackInput(player)).isFalse();
    }

    @Test
    void shouldTakeOverAttackInputWhenVisorHandsAndCombatEligibilityPass() {
        var player = mock(Player.class);
        when(player.getItemBySlot(EquipmentSlot.HEAD)).thenReturn(AEItems.IDLE_VISOR.stack());
        when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        IdleCurrencyClientCache.applySnapshot(Map.of(), Map.of(), true);

        assertThat(IdlePunchAttackHook.shouldTakeOverAttackInput(player)).isTrue();
    }

    @Test
    void shouldNotTakeOverAttackInputWithNonEmptyHandsEvenWhenEligible() {
        var player = mock(Player.class);
        when(player.getItemBySlot(EquipmentSlot.HEAD)).thenReturn(AEItems.IDLE_VISOR.stack());
        when(player.getMainHandItem()).thenReturn(new ItemStack(Items.STICK));
        when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        IdleCurrencyClientCache.applySnapshot(Map.of(), Map.of(), true);

        assertThat(IdlePunchAttackHook.shouldTakeOverAttackInput(player)).isFalse();
    }

    @Test
    void attackInputMapsToOffHandPunchRequest() {
        var event = mock(InputEvent.InteractionKeyMappingTriggered.class);
        when(event.isAttack()).thenReturn(true);
        when(event.isUseItem()).thenReturn(false);

        assertThat(invokeIdlePunchHand(event)).isEqualTo(InteractionHand.OFF_HAND);
    }

    @Test
    void suppressesVanillaAttackSwingForEligibleEntityHitTakeover() {
        var player = mock(Player.class);
        var target = mock(Entity.class);
        when(player.getItemBySlot(EquipmentSlot.HEAD)).thenReturn(AEItems.IDLE_VISOR.stack());
        when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        when(player.distanceToSqr(target)).thenReturn(4.0D);
        when(target.isAlive()).thenReturn(true);
        IdleCurrencyClientCache.applySnapshot(Map.of(), Map.of(), true);

        var hitResult = new EntityHitResult(target);

        assertThat(IdlePunchAttackHook.shouldSuppressVanillaAttackSwing(player, hitResult)).isTrue();
    }

    @Test
    void doesNotSuppressVanillaAttackSwingForNonEntityHit() {
        var player = mock(Player.class);
        when(player.getItemBySlot(EquipmentSlot.HEAD)).thenReturn(AEItems.IDLE_VISOR.stack());
        when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        IdleCurrencyClientCache.applySnapshot(Map.of(), Map.of(), true);

        HitResult blockHit = new BlockHitResult(Vec3.ZERO, Direction.UP, BlockPos.ZERO, false);

        assertThat(IdlePunchAttackHook.shouldSuppressVanillaAttackSwing(player, blockHit)).isFalse();
    }

    @Test
    void sameHandStaysCoolingUntilBaselineIntervalElapses() {
        var player = mock(Player.class);
        var level = mock(net.minecraft.world.level.Level.class);
        when(player.level()).thenReturn(level);
        when(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED))
                .thenReturn(4.0D);
        when(level.getGameTime()).thenReturn(100L, 109L, 110L);

        IdlePunchAttackHook.markClientPunchStarted(player, InteractionHand.MAIN_HAND);

        assertThat(IdlePunchAttackHook.isHandCoolingDown(player, InteractionHand.MAIN_HAND)).isTrue();
        assertThat(IdlePunchAttackHook.isHandCoolingDown(player, InteractionHand.MAIN_HAND)).isFalse();
    }

    @Test
    void handCooldownTrackingIsIndependentBetweenMainAndOffHand() {
        var player = mock(Player.class);
        var level = mock(net.minecraft.world.level.Level.class);
        when(player.level()).thenReturn(level);
        when(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED))
                .thenReturn(4.0D);
        when(level.getGameTime()).thenReturn(200L, 201L);

        IdlePunchAttackHook.markClientPunchStarted(player, InteractionHand.MAIN_HAND);

        assertThat(IdlePunchAttackHook.isHandCoolingDown(player, InteractionHand.MAIN_HAND)).isTrue();
        assertThat(IdlePunchAttackHook.isHandCoolingDown(player, InteractionHand.OFF_HAND)).isFalse();
    }

    @Test
    void attackInputDuringCooldownStillSetsStartAttackSuppressionFlag() {
        var player = mock(Player.class);
        var level = mock(net.minecraft.world.level.Level.class);
        when(player.level()).thenReturn(level);
        when(level.getGameTime()).thenReturn(250L);

        when(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED))
                .thenReturn(4.0D);

        IdlePunchAttackHook.markClientPunchStarted(player, InteractionHand.OFF_HAND);
        assertThat(IdlePunchAttackHook.isHandCoolingDown(player, InteractionHand.OFF_HAND)).isTrue();

        IdlePunchAttackHook.markStartAttackSuppressionForTakeoverClick(player, true);

        assertThat(IdlePunchAttackHook.consumeStartAttackSuppressionForCurrentClick(player)).isTrue();
        assertThat(IdlePunchAttackHook.consumeStartAttackSuppressionForCurrentClick(player)).isFalse();
    }

    @Test
    void noSuppressionWhenTakeoverConditionsFail() {
        var player = mock(Player.class);
        var level = mock(net.minecraft.world.level.Level.class);
        when(player.level()).thenReturn(level);
        when(level.getGameTime()).thenReturn(260L);

        IdlePunchAttackHook.markStartAttackSuppressionForTakeoverClick(player, false);

        assertThat(IdlePunchAttackHook.consumeStartAttackSuppressionForCurrentClick(player)).isFalse();
    }

    @Test
    void attackClickWithQueuedPacketSetsStartAttackSuppressionFlag() {
        var player = mock(Player.class);
        var level = mock(net.minecraft.world.level.Level.class);
        when(player.level()).thenReturn(level);
        when(level.getGameTime()).thenReturn(300L, 300L);

        IdlePunchAttackHook.markStartAttackSuppressionForCurrentClick(player);

        assertThat(IdlePunchAttackHook.consumeStartAttackSuppressionForCurrentClick(player)).isTrue();
        assertThat(IdlePunchAttackHook.consumeStartAttackSuppressionForCurrentClick(player)).isFalse();
    }

    @Test
    void leftClickTakeoverSuppressesMainHandSwingAnimationForCurrentClick() {
        var player = mock(Player.class);
        var level = mock(net.minecraft.world.level.Level.class);
        when(player.level()).thenReturn(level);
        when(level.getGameTime()).thenReturn(400L, 400L);

        IdlePunchAttackHook.markStartAttackSuppressionForTakeoverClick(player, true);

        assertThat(IdlePunchAttackHook.consumeStartAttackSuppressionForCurrentClick(player)).isTrue();
        assertThat(IdlePunchAttackHook.consumeStartAttackSuppressionForCurrentClick(player)).isFalse();
    }

    @Test
    void leftClickTakeoverSuppressionDoesNotCarryToLaterTick() {
        var player = mock(Player.class);
        var level = mock(net.minecraft.world.level.Level.class);
        when(player.level()).thenReturn(level);
        when(level.getGameTime()).thenReturn(410L, 411L);

        IdlePunchAttackHook.markStartAttackSuppressionForTakeoverClick(player, true);

        assertThat(IdlePunchAttackHook.consumeStartAttackSuppressionForCurrentClick(player)).isFalse();
    }

    @Test
    void offHandSwingStartsForNonEntityAttackWhenVanillaMainHandSwingIsInactive() {
        var player = mock(Player.class);
        var level = mock(net.minecraft.world.level.Level.class);
        when(player.level()).thenReturn(level);
        when(level.getGameTime()).thenReturn(500L, 500L);

        invokeTriggerLocalSwingForNonEntityInteraction(player, InteractionHand.OFF_HAND);

        assertThat(IdlePunchAnimationComponent.getActiveHand()).isEqualTo(InteractionHand.OFF_HAND);
        assertThat(IdlePunchAnimationComponent.isAnimationActive(player)).isTrue();
    }

    @Test
    void offHandSwingDoesNotRestartWhenVanillaMainHandSwingIsAlreadyActive() {
        var player = mock(Player.class);
        var level = mock(net.minecraft.world.level.Level.class);
        when(player.level()).thenReturn(level);

        player.swinging = true;
        player.swingingArm = InteractionHand.MAIN_HAND;
        player.swingTime = 1;

        invokeTriggerLocalSwingForNonEntityInteraction(player, InteractionHand.OFF_HAND);

        assertThat(IdlePunchAnimationComponent.isAnimationActive(player)).isFalse();
        assertThat(IdlePunchAnimationComponent.getSwingStartTick()).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void mainHandSwingStartsForNonEntityUseInteraction() {
        var player = mock(Player.class);
        var level = mock(net.minecraft.world.level.Level.class);
        when(player.level()).thenReturn(level);
        when(level.getGameTime()).thenReturn(520L, 520L);

        invokeTriggerLocalSwingForNonEntityInteraction(player, InteractionHand.MAIN_HAND);

        assertThat(IdlePunchAnimationComponent.getActiveHand()).isEqualTo(InteractionHand.MAIN_HAND);
        assertThat(IdlePunchAnimationComponent.isAnimationActive(player)).isTrue();
    }

    @Test
    void rightClickMainHandPathDoesNotSetStartAttackSuppressionFlag() {
        var event = mock(InputEvent.InteractionKeyMappingTriggered.class);
        when(event.isAttack()).thenReturn(false);
        when(event.isUseItem()).thenReturn(true);

        assertThat(invokeIdlePunchHand(event)).isEqualTo(InteractionHand.MAIN_HAND);

        var player = mock(Player.class);
        var level = mock(net.minecraft.world.level.Level.class);
        when(player.level()).thenReturn(level);
        when(level.getGameTime()).thenReturn(350L);

        IdlePunchAttackHook.markStartAttackSuppressionForTakeoverClick(player, event.isAttack());

        assertThat(IdlePunchAttackHook.consumeStartAttackSuppressionForCurrentClick(player)).isFalse();
    }

    @Test
    void useInputMapsToMainHandPunchRequest() {
        var event = mock(InputEvent.InteractionKeyMappingTriggered.class);
        when(event.isAttack()).thenReturn(false);
        when(event.isUseItem()).thenReturn(true);

        assertThat(invokeIdlePunchHand(event)).isEqualTo(InteractionHand.MAIN_HAND);
    }

    private static Method getTriggerLocalSwingForNonEntityMethod() {
        try {
            var method = IdlePunchAttackHook.class.getDeclaredMethod("triggerLocalSwingForNonEntityInteraction",
                    Player.class,
                    InteractionHand.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to access IdlePunchAttackHook#triggerLocalSwingForNonEntityInteraction",
                    e);
        }
    }

    private static void invokeTriggerLocalSwingForNonEntityInteraction(Player player, InteractionHand hand) {
        try {
            TRIGGER_LOCAL_SWING_FOR_NON_ENTITY.invoke(null, player, hand);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke IdlePunchAttackHook#triggerLocalSwingForNonEntityInteraction",
                    e);
        }
    }

    private static Method getIdlePunchHandMethod() {
        try {
            var method = IdlePunchAttackHook.class.getDeclaredMethod("getIdlePunchHand",
                    InputEvent.InteractionKeyMappingTriggered.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to access IdlePunchAttackHook#getIdlePunchHand", e);
        }
    }

    private static InteractionHand invokeIdlePunchHand(InputEvent.InteractionKeyMappingTriggered event) {
        try {
            return (InteractionHand) GET_IDLE_PUNCH_HAND.invoke(null, event);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke IdlePunchAttackHook#getIdlePunchHand", e);
        }
    }
}
