package appeng.idle.reward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import appeng.idle.currency.CurrencyId;

class BlockBreakRewardHandlerTest {
    @Test
    void matchesBlockIdConditionAgainstBrokenState() {
        var reward = new RewardDefinition(
                ResourceLocation.fromNamespaceAndPath("ae2", "break_oak_log_idle"),
                RewardTriggerType.BLOCK_BREAK,
                new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "idle")),
                20,
                new RewardDefinition.BlockFilterCondition(
                        ResourceLocation.fromNamespaceAndPath("minecraft", "oak_log"),
                        null),
                null);

        assertThat(BlockBreakRewardHandler.matchesBlockCondition(reward, Blocks.OAK_LOG.defaultBlockState())).isTrue();
        assertThat(BlockBreakRewardHandler.matchesBlockCondition(reward, Blocks.STONE.defaultBlockState())).isFalse();
    }

    @Test
    void matchesTagConditionAgainstBrokenState() {
        var reward = new RewardDefinition(
                ResourceLocation.fromNamespaceAndPath("ae2", "break_natural_log_idle"),
                RewardTriggerType.BLOCK_BREAK,
                new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "idle")),
                20,
                new RewardDefinition.BlockFilterCondition(
                        null,
                        ResourceLocation.fromNamespaceAndPath("minecraft", "logs")),
                null);

        var state = mock(BlockState.class);
        when(state.is(org.mockito.ArgumentMatchers.<TagKey<Block>>any())).thenReturn(true);

        assertThat(BlockBreakRewardHandler.matchesBlockCondition(reward, state)).isTrue();
    }

    @Test
    void doesNotMatchWhenRewardHasNoConditions() {
        var reward = new RewardDefinition(
                ResourceLocation.fromNamespaceAndPath("ae2", "bad"),
                RewardTriggerType.BLOCK_BREAK,
                new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "idle")),
                20,
                null,
                null);

        assertThat(BlockBreakRewardHandler.matchesBlockCondition(reward, Blocks.OAK_LOG.defaultBlockState())).isFalse();
    }
}
