package appeng.idle.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.minecraft.server.level.ServerPlayer;

import appeng.core.definitions.AEItems;

class PlayerIdleDataManagerUnlockPolicyTest {
    @Test
    void unlockSetsFlagAndPersistsWithoutEquipment() {
        var player = serverPlayerWithPersistentTag();

        assertThat(PlayerIdleDataManager.isIdleGenerationUnlocked(player)).isFalse();

        PlayerIdleDataManager.unlockIdleGeneration(player);

        assertThat(PlayerIdleDataManager.isIdleGenerationUnlocked(player)).isTrue();
        assertThat(PlayerIdleDataManager.isPassiveGenerationEnabled(player)).isTrue();
    }

    @Test
    void passiveGenerationPolicyDependsOnUnlockOnly() {
        var equippedHead = new AtomicReference<>(ItemStack.EMPTY);
        var player = serverPlayerWithPersistentTag(equippedHead);

        assertThat(PlayerIdleDataManager.isPassiveGenerationEnabled(player)).isFalse();
        assertThat(PlayerIdleDataManager.isActiveRewardEligibleNow(player)).isFalse();

        PlayerIdleDataManager.unlockIdleGeneration(player);

        assertThat(PlayerIdleDataManager.isPassiveGenerationEnabled(player)).isTrue();

        equippedHead.set(AEItems.IDLE_VISOR.stack());
        assertThat(PlayerIdleDataManager.isPassiveGenerationEnabled(player)).isTrue();

        equippedHead.set(ItemStack.EMPTY);
        assertThat(PlayerIdleDataManager.isPassiveGenerationEnabled(player)).isTrue();
    }

    @Test
    void activeRewardEligibilityRequiresUnlockAndIdleVisorEquipped() {
        var equippedHead = new AtomicReference<>(AEItems.IDLE_VISOR.stack());
        var player = serverPlayerWithPersistentTag(equippedHead);

        assertThat(PlayerIdleDataManager.isActiveRewardEligibleNow(player)).isFalse();

        PlayerIdleDataManager.unlockIdleGeneration(player);

        equippedHead.set(ItemStack.EMPTY);
        assertThat(PlayerIdleDataManager.isActiveRewardEligibleNow(player)).isFalse();

        equippedHead.set(AEItems.IDLE_VISOR.stack());
        assertThat(PlayerIdleDataManager.isActiveRewardEligibleNow(player)).isTrue();
    }

    private static ServerPlayer serverPlayerWithPersistentTag() {
        return serverPlayerWithPersistentTag(new AtomicReference<>(ItemStack.EMPTY));
    }

    private static ServerPlayer serverPlayerWithPersistentTag(AtomicReference<ItemStack> equippedHead) {
        var player = mock(ServerPlayer.class);
        var level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        when(player.level()).thenReturn(level);
        when(player.getItemBySlot(EquipmentSlot.HEAD)).thenAnswer($ -> equippedHead.get());
        when(player.getPersistentData()).thenReturn(new CompoundTag());
        return player;
    }
}
