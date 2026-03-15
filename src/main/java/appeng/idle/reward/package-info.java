/**
 * Idle reward trigger processing.
 * <p>
 * Extension points:
 * <ul>
 * <li>Add a new {@link appeng.idle.reward.RewardTriggerType} enum constant.</li>
 * <li>Implement a {@link appeng.idle.reward.RewardMatcher} for that trigger.</li>
 * <li>Register the matcher in {@link appeng.idle.reward.RewardMatchers}.</li>
 * <li>Dispatch trigger events by creating a {@link appeng.idle.reward.RewardTriggerContext} and iterating
 * {@link appeng.idle.reward.IdleRewardManager#getByTrigger(appeng.idle.reward.RewardTriggerType)} results.</li>
 * <li>Keep grant flow centralized via {@link appeng.idle.reward.RewardGrantService} so
 * {@link appeng.idle.tick.IdleGenerationProgressService#grantActiveProgressTicks} remains the single grant path.</li>
 * </ul>
 */
package appeng.idle.reward;
