package appeng.idle.reward;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import appeng.idle.reward.natural.NaturalLogTracker;

/**
 * Awards active idle progress when eligible players break blocks matching reward definitions.
 */
public final class BlockBreakRewardHandler {
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

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        var rewards = IdleRewardManager.getByTrigger(RewardTriggerType.BLOCK_BREAK);
        if (rewards.isEmpty()) {
            NaturalLogTracker.onBlockRemovedOrChanged(serverLevel, event.getPos(), brokenState);
            return;
        }

        var matcher = RewardMatchers.forTrigger(RewardTriggerType.BLOCK_BREAK);
        var context = new RewardTriggerContext(player, serverLevel, event.getPos(), brokenState, null, null,
                serverLevel.getGameTime());

        for (var reward : rewards) {
            if (!matcher.matches(reward, context)) {
                continue;
            }

            RewardGrantService.grantIfEligible(context, reward);
        }

        NaturalLogTracker.onBlockRemovedOrChanged(serverLevel, event.getPos(), brokenState);
    }

    static boolean matchesBlockCondition(RewardDefinition reward,
            net.minecraft.world.level.block.state.BlockState brokenState) {
        return BlockBreakRewardMatcher.matchesBlockCondition(reward, brokenState);
    }
}
