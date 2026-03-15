package appeng.idle.reward;

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

        IdleGenerationProgressService.grantActiveProgressTicks(context.player(), reward.currencyId(),
                reward.progressTicksAwarded(), reward.triggerType().name() + ":" + reward.id());
    }
}
