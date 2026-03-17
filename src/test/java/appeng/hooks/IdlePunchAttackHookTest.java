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

import appeng.core.definitions.AEItems;
import appeng.idle.net.IdleCurrencyClientCache;

class IdlePunchAttackHookTest {

    private static final Method GET_IDLE_PUNCH_HAND = getIdlePunchHandMethod();

    @AfterEach
    void resetIdlePunchEligibility() {
        IdleCurrencyClientCache.applySnapshot(Map.of(), Map.of(), false);
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
    void useInputMapsToMainHandPunchRequest() {
        var event = mock(InputEvent.InteractionKeyMappingTriggered.class);
        when(event.isAttack()).thenReturn(false);
        when(event.isUseItem()).thenReturn(true);

        assertThat(invokeIdlePunchHand(event)).isEqualTo(InteractionHand.MAIN_HAND);
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
