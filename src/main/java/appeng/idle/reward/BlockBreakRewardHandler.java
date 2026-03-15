package appeng.idle.reward;

import java.util.Objects;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import appeng.idle.reward.natural.NaturalLogTracker;
import appeng.idle.tick.IdleGenerationProgressService;

/**
 * Awards active idle progress when eligible players break blocks matching reward definitions.
 */
public final class BlockBreakRewardHandler {
    private static final ResourceLocation BREAK_NATURAL_LOG_REWARD_ID = ResourceLocation
            .fromNamespaceAndPath("ae2", "break_natural_log_idle");

    private BlockBreakRewardHandler() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) {
            return;
        }

        var level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        var brokenState = event.getState();
        if (brokenState == null || brokenState.isAir()) {
            return;
        }

        var rewards = IdleRewardManager.getByTrigger(RewardTriggerType.BLOCK_BREAK);
        if (rewards.isEmpty()) {
            return;
        }

        for (var reward : rewards) {
            if (!matchesBlockCondition(reward, brokenState)) {
                continue;
            }

            if (requiresNaturalLog(reward)
                    && (!(level instanceof ServerLevel serverLevel)
                            || !NaturalLogTracker.isNaturallyGeneratedLog(serverLevel, event.getPos(), brokenState))) {
                continue;
            }

            if (!RewardEligibilityService.canReceiveActiveReward(player, reward)) {
                continue;
            }

            IdleGenerationProgressService.grantActiveProgressTicks(player, reward.currencyId(),
                    reward.progressTicksAwarded(), "BLOCK_BREAK:" + reward.id());
        }

        if (level instanceof ServerLevel serverLevel) {
            NaturalLogTracker.onBlockRemovedOrChanged(serverLevel, event.getPos(), brokenState);
        }
    }

    private static boolean requiresNaturalLog(RewardDefinition reward) {
        return BREAK_NATURAL_LOG_REWARD_ID.equals(reward.id());
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
}
