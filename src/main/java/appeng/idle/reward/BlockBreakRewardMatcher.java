package appeng.idle.reward;

import java.util.Objects;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.state.BlockState;

import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.idle.reward.natural.NaturalLogTracker;

final class BlockBreakRewardMatcher implements RewardMatcher {
    private static final ResourceLocation BREAK_NATURAL_LOG_REWARD_ID = ResourceLocation
            .fromNamespaceAndPath("ae2", "break_natural_log_idle");

    @Override
    public RewardTriggerType triggerType() {
        return RewardTriggerType.BLOCK_BREAK;
    }

    @Override
    public boolean matches(RewardDefinition reward, RewardTriggerContext context) {
        var brokenState = context.blockState();
        if (brokenState == null || brokenState.isAir()) {
            return false;
        }

        if (!matchesBlockCondition(reward, brokenState)) {
            return false;
        }

        if (requiresNaturalLog(reward)) {
            var natural = NaturalLogTracker.isNaturallyGeneratedLog(context.level(), context.position(), brokenState);
            if (!natural) {
                var provenance = NaturalLogTracker.getProvenanceForDebug(context.level(), context.position(),
                        brokenState);
                AELog.debug(
                        "Rejected idle natural-log reward: reward=%s pos=%s block=%s provenance=%s unknownCounts=%s saplingCounts=%s",
                        reward.id(),
                        context.position(),
                        BuiltInRegistries.BLOCK.getKey(brokenState.getBlock()),
                        provenance,
                        AEConfig.instance().isIdleNaturalLogUnknownCounts(),
                        AEConfig.instance().isIdleNaturalLogSaplingGrownCounts());
                return false;
            }
        }

        return true;
    }

    static boolean matchesBlockCondition(RewardDefinition reward, BlockState brokenState) {
        Objects.requireNonNull(reward, "reward");
        Objects.requireNonNull(brokenState, "brokenState");

        var conditions = reward.conditions();
        if (conditions == null) {
            return false;
        }

        if (conditions.blockId() != null) {
            var brokenBlockId = BuiltInRegistries.BLOCK.getKey(brokenState.getBlock());
            if (!conditions.blockId().equals(brokenBlockId)) {
                return false;
            }
        }

        if (conditions.blockTagId() != null) {
            var blockTag = TagKey.create(Registries.BLOCK, conditions.blockTagId());
            if (!brokenState.is(blockTag)) {
                return false;
            }
        }

        return true;
    }

    private static boolean requiresNaturalLog(RewardDefinition reward) {
        return BREAK_NATURAL_LOG_REWARD_ID.equals(reward.id());
    }
}
