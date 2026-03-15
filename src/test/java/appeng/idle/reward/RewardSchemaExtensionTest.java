package appeng.idle.reward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import appeng.idle.currency.CurrencyId;
import appeng.idle.player.PlayerIdleData;
import appeng.idle.player.PlayerIdleDataManager;

class RewardSchemaExtensionTest {
    @Test
    void loadsNoopGatedFixtureWithExtendedSchemaFields() throws Exception {
        var parseDefinition = IdleRewardManager.class.getDeclaredMethod("parseDefinition", ResourceLocation.class,
                com.google.gson.JsonObject.class);
        parseDefinition.setAccessible(true);

        com.google.gson.JsonObject json;
        try (var in = getClass().getResourceAsStream("/data/ae2/idle_reward/noop_gated_reward.json")) {
            assertThat(in).isNotNull();
            json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        try (MockedStatic<appeng.idle.currency.IdleCurrencyManager> currencyManager = mockStatic(
                appeng.idle.currency.IdleCurrencyManager.class)) {
            currencyManager.when(() -> appeng.idle.currency.IdleCurrencyManager.get(
                    new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "idle"))))
                    .thenReturn(mock(appeng.idle.currency.CurrencyDefinition.class));

            var parsed = (RewardDefinition) parseDefinition.invoke(null,
                    ResourceLocation.fromNamespaceAndPath("ae2", "noop_gated_reward"), json);

            assertThat(parsed.upgradeGate()).isNotNull();
            assertThat(parsed.upgradeGate().upgradeId())
                    .isEqualTo(ResourceLocation.fromNamespaceAndPath("ae2", "test_gate"));
            assertThat(parsed.upgradeGate().minLevel()).isEqualTo(2);
            assertThat(parsed.cooldownWindowTicks()).isEqualTo(1200L);
            assertThat(parsed.environmentPredicate()).isNotNull();
            assertThat(parsed.environmentPredicate().dimensionId())
                    .isEqualTo(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"));
            assertThat(parsed.environmentPredicate().biomeId())
                    .isEqualTo(ResourceLocation.fromNamespaceAndPath("minecraft", "plains"));
            assertThat(parsed.caps()).isNotNull();
            assertThat(parsed.caps().dailyCap()).isEqualTo(10);
            assertThat(parsed.caps().intervalCap()).isEqualTo(3);
            assertThat(parsed.caps().intervalWindowTicks()).isEqualTo(24000L);
        }
    }

    @Test
    void ungatedRewardsStillWorkAndNewGateHooksAreDormant() {
        var player = mock(ServerPlayer.class);
        var level = mock(ServerLevel.class);
        var ungatedReward = new RewardDefinition(
                ResourceLocation.fromNamespaceAndPath("ae2", "ungated"),
                RewardTriggerType.BLOCK_BREAK,
                new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "idle")),
                5L,
                new RewardDefinition.BlockFilterCondition(ResourceLocation.fromNamespaceAndPath("minecraft", "stone"),
                        null),
                null,
                1200L,
                new RewardDefinition.EnvironmentPredicate(
                        ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether"),
                        ResourceLocation.fromNamespaceAndPath("minecraft", "crimson_forest"),
                        null),
                new RewardDefinition.RewardCaps(1, 1, 24000L));

        try (MockedStatic<PlayerIdleDataManager> manager = mockStatic(PlayerIdleDataManager.class)) {
            manager.when(() -> PlayerIdleDataManager.isActiveRewardEligibleNow(player)).thenReturn(true);
            manager.when(() -> PlayerIdleDataManager.get(player))
                    .thenReturn(new PlayerIdleData(Map.of(), 0L, PlayerIdleData.CURRENT_DATA_VERSION, Map.of()));

            var context = new RewardTriggerContext(player, level, BlockPos.ZERO, null, null, null, 0L);
            assertThat(RewardEligibilityService.canReceiveActiveReward(player, ungatedReward, context)).isTrue();
        }
    }
}
