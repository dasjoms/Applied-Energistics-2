package appeng.idle.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

import appeng.core.definitions.AEItems;
import appeng.idle.net.IdleCurrencySyncService;

class PlayerIdleDataManagerEquipmentChangeTest {
    @Test
    void equippingIdleVisorUnlocksAndSendsHudSnapshotImmediately() {
        var player = serverPlayerWithPersistentTag();
        var event = equipmentChangeEvent(player, EquipmentSlot.HEAD, ItemStack.EMPTY, AEItems.IDLE_VISOR.stack());

        try (MockedStatic<IdleCurrencySyncService> syncService = mockStatic(IdleCurrencySyncService.class)) {
            PlayerIdleDataManager.handleEquipmentChanged(event);

            syncService.verify(() -> IdleCurrencySyncService.sendHudSnapshot(player), times(1));
            syncService.verify(() -> IdleCurrencySyncService.sendEmptyHudSnapshot(player), never());
        }

        assertThat(PlayerIdleDataManager.isIdleGenerationUnlocked(player)).isTrue();
    }

    @Test
    void removingIdleVisorSendsEmptyHudSnapshot() {
        var player = serverPlayerWithPersistentTag();
        var event = equipmentChangeEvent(player, EquipmentSlot.HEAD, AEItems.IDLE_VISOR.stack(), ItemStack.EMPTY);

        try (MockedStatic<IdleCurrencySyncService> syncService = mockStatic(IdleCurrencySyncService.class)) {
            PlayerIdleDataManager.handleEquipmentChanged(event);

            syncService.verify(() -> IdleCurrencySyncService.sendEmptyHudSnapshot(player), times(1));
            syncService.verify(() -> IdleCurrencySyncService.sendHudSnapshot(player), never());
        }
    }

    @Test
    void nonHeadSlotChangesAreIgnored() {
        var player = serverPlayerWithPersistentTag();
        var event = equipmentChangeEvent(player, EquipmentSlot.CHEST, ItemStack.EMPTY, AEItems.IDLE_VISOR.stack());

        try (MockedStatic<IdleCurrencySyncService> syncService = mockStatic(IdleCurrencySyncService.class)) {
            PlayerIdleDataManager.handleEquipmentChanged(event);

            syncService.verifyNoInteractions();
        }

        assertThat(PlayerIdleDataManager.isIdleGenerationUnlocked(player)).isFalse();
    }

    private static LivingEquipmentChangeEvent equipmentChangeEvent(ServerPlayer player, EquipmentSlot slot,
            ItemStack from, ItemStack to) {
        var event = mock(LivingEquipmentChangeEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getSlot()).thenReturn(slot);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);
        return event;
    }

    private static ServerPlayer serverPlayerWithPersistentTag() {
        var player = mock(ServerPlayer.class);
        var level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        when(player.level()).thenReturn(level);
        when(player.getPersistentData()).thenReturn(new CompoundTag());
        return player;
    }
}
