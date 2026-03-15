package appeng.idle.reward;

import net.minecraft.network.chat.Component;

import appeng.core.AEConfig;
import appeng.idle.tick.IdleGenerationProgressService;

/**
 * Centralizes reward-eligibility + progress granting flow.
 */
final class RewardGrantService {
    private RewardGrantService() {
    }

    static void grantIfEligible(RewardTriggerContext context, RewardDefinition reward) {
        if (!RewardEligibilityService.canReceiveActiveReward(context.player(), reward, context)) {
            return;
        }

        if (AEConfig.instance().isDebugToolsEnabled()) {
            context.player().sendSystemMessage(Component.literal(
                    "[AE2 Debug] Idle reward applied: id=" + reward.id() + ", currency=" + reward.currencyId().id()
                            + ", progressTicks=" + reward.progressTicksAwarded()));
        }

        IdleGenerationProgressService.grantActiveProgressTicks(context.player(), reward.currencyId(),
                reward.progressTicksAwarded(), reward.triggerType().name() + ":" + reward.id());
    }
}
