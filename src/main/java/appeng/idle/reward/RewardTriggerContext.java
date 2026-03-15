package appeng.idle.reward;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Runtime context supplied to {@link RewardMatcher}s for trigger-specific matching.
 */
public record RewardTriggerContext(
        ServerPlayer player,
        ServerLevel level,
        BlockPos position,
        @Nullable BlockState blockState,
        @Nullable ItemStack itemStack,
        @Nullable Entity entity,
        long timestampTicks) {
}
