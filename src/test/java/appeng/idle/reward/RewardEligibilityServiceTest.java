package appeng.idle.reward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import appeng.idle.currency.CurrencyId;
import appeng.idle.player.PlayerIdleData;
import appeng.idle.player.PlayerIdleDataManager;

class RewardEligibilityServiceTest {
    @Test
    void ungatedRewardOnlyRequiresActiveEligibility() {
        var player = mock(ServerPlayer.class);
        var reward = new RewardDefinition(
                ResourceLocation.fromNamespaceAndPath("ae2", "ungated_reward"),
                RewardTriggerType.BLOCK_BREAK,
                new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "test_currency")),
                5L,
                new RewardDefinition.BlockFilterCondition(
                        ResourceLocation.fromNamespaceAndPath("minecraft", "stone"),
                        null),
                null);

        try (MockedStatic<PlayerIdleDataManager> manager = mockStatic(PlayerIdleDataManager.class)) {
            manager.when(() -> PlayerIdleDataManager.isActiveRewardEligibleNow(player)).thenReturn(true);

            assertThat(RewardEligibilityService.canReceiveActiveReward(player, reward)).isTrue();
        }
    }

    @Test
    void gatedRewardRequiresOwnedUpgradeLevel() {
        var player = mock(ServerPlayer.class);
        var gateId = ResourceLocation.fromNamespaceAndPath("ae2", "gate_upgrade");
        var reward = new RewardDefinition(
                ResourceLocation.fromNamespaceAndPath("ae2", "gated_reward"),
                RewardTriggerType.BLOCK_BREAK,
                new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "test_currency")),
                5L,
                new RewardDefinition.BlockFilterCondition(
                        ResourceLocation.fromNamespaceAndPath("minecraft", "stone"),
                        null),
                gateId);

        try (MockedStatic<PlayerIdleDataManager> manager = mockStatic(PlayerIdleDataManager.class)) {
            manager.when(() -> PlayerIdleDataManager.isActiveRewardEligibleNow(player)).thenReturn(true);
            manager.when(() -> PlayerIdleDataManager.get(player))
                    .thenReturn(new PlayerIdleData(Map.of(), 0L, PlayerIdleData.CURRENT_DATA_VERSION, Map.of()));

            assertThat(RewardEligibilityService.canReceiveActiveReward(player, reward)).isFalse();
        }

        try (MockedStatic<PlayerIdleDataManager> manager = mockStatic(PlayerIdleDataManager.class)) {
            manager.when(() -> PlayerIdleDataManager.isActiveRewardEligibleNow(player)).thenReturn(true);
            manager.when(() -> PlayerIdleDataManager.get(player)).thenReturn(
                    new PlayerIdleData(Map.of(), 0L, PlayerIdleData.CURRENT_DATA_VERSION, Map.of(gateId, 1)));

            assertThat(RewardEligibilityService.canReceiveActiveReward(player, reward)).isTrue();
        }
    }
}
