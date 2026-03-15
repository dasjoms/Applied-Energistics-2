package appeng.idle.reward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import appeng.idle.currency.CurrencyId;
import appeng.idle.player.PlayerIdleData;
import appeng.idle.player.PlayerIdleDataManager;

class RewardEligibilityServiceTest {
    @Test
    void ungatedRewardOnlyRequiresActiveEligibility() {
        var player = mock(ServerPlayer.class);
        var reward = blockBreakReward(ResourceLocation.fromNamespaceAndPath("ae2", "ungated_reward"), null);

        try (MockedStatic<PlayerIdleDataManager> manager = mockStatic(PlayerIdleDataManager.class)) {
            manager.when(() -> PlayerIdleDataManager.isActiveRewardEligibleNow(player)).thenReturn(true);

            assertThat(RewardEligibilityService.canReceiveActiveReward(player, reward)).isTrue();
        }
    }

    @Test
    void notActivelyEligiblePlayerCannotReceiveRewardEvenWhenUngated() {
        var player = mock(ServerPlayer.class);
        var reward = blockBreakReward(ResourceLocation.fromNamespaceAndPath("ae2", "ungated_reward"), null);

        try (MockedStatic<PlayerIdleDataManager> manager = mockStatic(PlayerIdleDataManager.class)) {
            manager.when(() -> PlayerIdleDataManager.isActiveRewardEligibleNow(player)).thenReturn(false);

            assertThat(RewardEligibilityService.canReceiveActiveReward(player, reward)).isFalse();
        }
    }

    @Test
    void gatedRewardRequiresOwnedUpgradeLevel() {
        var player = mock(ServerPlayer.class);
        var gateId = ResourceLocation.fromNamespaceAndPath("ae2", "gate_upgrade");
        var reward = blockBreakReward(ResourceLocation.fromNamespaceAndPath("ae2", "gated_reward"), gateId);

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

    @Test
    void applySkipsMalformedRewardWithMissingCurrencyIdAndKeepsValidEntries() throws Exception {
        var managerClass = IdleRewardManager.class;
        var instanceField = managerClass.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        var instance = instanceField.get(null);

        var rewardsField = managerClass.getDeclaredField("rewards");
        rewardsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var originalRewards = (Map<ResourceLocation, RewardDefinition>) rewardsField.get(instance);

        var rewardsByTriggerField = managerClass.getDeclaredField("rewardsByTrigger");
        rewardsByTriggerField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var originalRewardsByTrigger = (Map<RewardTriggerType, List<RewardDefinition>>) rewardsByTriggerField
                .get(instance);

        var apply = managerClass.getDeclaredMethod("apply", Map.class, ResourceManager.class, ProfilerFiller.class);
        apply.setAccessible(true);

        var validJson = new JsonObject();
        validJson.addProperty("id", "ae2:valid_reward");
        validJson.addProperty("triggerType", "BLOCK_BREAK");
        validJson.addProperty("currencyId", "ae2:idle");
        validJson.addProperty("progressTicksAwarded", 5L);
        var validConditions = new JsonObject();
        validConditions.addProperty("block", "minecraft:stone");
        validJson.add("conditions", validConditions);

        var malformedJson = new JsonObject();
        malformedJson.addProperty("id", "ae2:missing_currency_reward");
        malformedJson.addProperty("triggerType", "BLOCK_BREAK");
        malformedJson.addProperty("progressTicksAwarded", 5L);
        var malformedConditions = new JsonObject();
        malformedConditions.addProperty("block", "minecraft:oak_log");
        malformedJson.add("conditions", malformedConditions);

        var entries = new LinkedHashMap<ResourceLocation, com.google.gson.JsonElement>();
        entries.put(ResourceLocation.fromNamespaceAndPath("ae2", "valid_reward"), validJson);
        entries.put(ResourceLocation.fromNamespaceAndPath("ae2", "missing_currency_reward"), malformedJson);

        try {
            apply.invoke(instance, entries, mock(ResourceManager.class), mock(ProfilerFiller.class));

            assertThat(IdleRewardManager.get(ResourceLocation.fromNamespaceAndPath("ae2", "valid_reward"))).isNotNull();
            assertThat(IdleRewardManager.get(ResourceLocation.fromNamespaceAndPath("ae2", "missing_currency_reward")))
                    .isNull();
        } finally {
            rewardsField.set(instance, originalRewards);
            rewardsByTriggerField.set(instance, originalRewardsByTrigger);
        }
    }

    private static RewardDefinition blockBreakReward(ResourceLocation rewardId, ResourceLocation gateId) {
        return new RewardDefinition(
                rewardId,
                RewardTriggerType.BLOCK_BREAK,
                new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "test_currency")),
                5L,
                new RewardDefinition.BlockFilterCondition(
                        ResourceLocation.fromNamespaceAndPath("minecraft", "stone"),
                        null),
                gateId);
    }
}
