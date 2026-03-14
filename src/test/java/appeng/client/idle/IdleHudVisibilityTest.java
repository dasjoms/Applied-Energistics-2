package appeng.client.idle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.core.definitions.AEItems;

class IdleHudVisibilityTest {

    @Test
    void hasIdleVisorEquippedReturnsTrueOnlyForIdleVisorInHeadSlot() {
        var player = mock(Player.class);

        when(player.getItemBySlot(EquipmentSlot.HEAD)).thenReturn(ItemStack.EMPTY);
        assertThat(IdleHudVisibility.hasIdleVisorEquipped(player)).isFalse();

        when(player.getItemBySlot(EquipmentSlot.HEAD)).thenReturn(AEItems.IDLE_VISOR.stack());
        assertThat(IdleHudVisibility.hasIdleVisorEquipped(player)).isTrue();
    }
}
