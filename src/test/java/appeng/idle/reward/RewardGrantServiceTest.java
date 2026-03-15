package appeng.idle.reward;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import appeng.core.AEConfig;
import appeng.idle.currency.CurrencyId;
import appeng.idle.tick.IdleGenerationProgressService;

class RewardGrantServiceTest {
    @Test
    void sendsDebugChatMessageWhenDebugToolsEnabled() {
        var player = mock(ServerPlayer.class);
        var reward = rewardDefinition();
        var context = new RewardTriggerContext(player, mock(ServerLevel.class), BlockPos.ZERO,
                Blocks.OAK_LOG.defaultBlockState(), null, null, 100L);
        var config = mock(AEConfig.class);

        try (MockedStatic<RewardEligibilityService> eligibility = mockStatic(RewardEligibilityService.class);
                MockedStatic<IdleGenerationProgressService> progressService = mockStatic(
                        IdleGenerationProgressService.class);
                MockedStatic<AEConfig> aeConfig = mockStatic(AEConfig.class)) {
            eligibility.when(() -> RewardEligibilityService.canReceiveActiveReward(player, reward, context))
                    .thenReturn(true);
            aeConfig.when(AEConfig::instance).thenReturn(config);
            org.mockito.Mockito.when(config.isDebugToolsEnabled()).thenReturn(true);

            RewardGrantService.grantIfEligible(context, reward);

            verify(player).sendSystemMessage(any(Component.class));
            progressService.verify(() -> IdleGenerationProgressService.grantActiveProgressTicks(player,
                    reward.currencyId(), reward.progressTicksAwarded(), "BLOCK_BREAK:" + reward.id()));
        }
    }

    @Test
    void doesNotSendDebugChatMessageWhenDebugToolsDisabled() {
        var player = mock(ServerPlayer.class);
        var reward = rewardDefinition();
        var context = new RewardTriggerContext(player, mock(ServerLevel.class), BlockPos.ZERO,
                Blocks.OAK_LOG.defaultBlockState(), null, null, 100L);
        var config = mock(AEConfig.class);

        try (MockedStatic<RewardEligibilityService> eligibility = mockStatic(RewardEligibilityService.class);
                MockedStatic<IdleGenerationProgressService> progressService = mockStatic(
                        IdleGenerationProgressService.class);
                MockedStatic<AEConfig> aeConfig = mockStatic(AEConfig.class)) {
            eligibility.when(() -> RewardEligibilityService.canReceiveActiveReward(player, reward, context))
                    .thenReturn(true);
            aeConfig.when(AEConfig::instance).thenReturn(config);
            org.mockito.Mockito.when(config.isDebugToolsEnabled()).thenReturn(false);

            RewardGrantService.grantIfEligible(context, reward);

            verify(player, never()).sendSystemMessage(any(Component.class));
            progressService.verify(() -> IdleGenerationProgressService.grantActiveProgressTicks(any(ServerPlayer.class),
                    any(CurrencyId.class), anyLong(), anyString()));
        }
    }

    private static RewardDefinition rewardDefinition() {
        return new RewardDefinition(
                ResourceLocation.fromNamespaceAndPath("ae2", "break_natural_log_idle"),
                RewardTriggerType.BLOCK_BREAK,
                new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "idle")),
                20L,
                new RewardDefinition.BlockFilterCondition(
                        ResourceLocation.fromNamespaceAndPath("minecraft", "oak_log"),
                        null),
                null);
    }
}
