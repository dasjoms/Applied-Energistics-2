package appeng.idle.reward;

/**
 * Trigger-specific matcher for reward definitions.
 */
public interface RewardMatcher {
    RewardTriggerType triggerType();

    boolean matches(RewardDefinition reward, RewardTriggerContext context);
}
