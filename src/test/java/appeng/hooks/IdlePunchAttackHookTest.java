package appeng.hooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.core.definitions.AEItems;
import appeng.idle.net.IdleCurrencyClientCache;

class IdlePunchAttackHookTest {

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
}
