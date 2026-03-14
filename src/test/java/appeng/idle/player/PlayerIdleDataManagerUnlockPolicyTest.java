package appeng.idle.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import net.minecraft.server.level.ServerPlayer;

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
        var player = serverPlayerWithPersistentTag();

        assertThat(PlayerIdleDataManager.isPassiveGenerationEnabled(player)).isFalse();

        PlayerIdleDataManager.unlockIdleGeneration(player);

        assertThat(PlayerIdleDataManager.isPassiveGenerationEnabled(player)).isTrue();
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
