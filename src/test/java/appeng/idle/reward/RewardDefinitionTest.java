package appeng.idle.reward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import appeng.idle.currency.CurrencyId;

class RewardDefinitionTest {
    @Test
    void marksDefinitionWithoutUpgradeGateAsUngated() {
        var definition = new RewardDefinition(
                ResourceLocation.fromNamespaceAndPath("ae2", "break_natural_log_idle"),
                RewardTriggerType.BLOCK_BREAK,
                new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "idle")),
                20,
                new RewardDefinition.BlockFilterCondition(
                        null,
                        ResourceLocation.fromNamespaceAndPath("minecraft", "logs")),
                null);

        assertThat(definition.isUngated()).isTrue();
    }

    @Test
    void rejectsNonPositiveProgressAward() {
        assertThatThrownBy(() -> new RewardDefinition(
                ResourceLocation.fromNamespaceAndPath("ae2", "break_natural_log_idle"),
                RewardTriggerType.BLOCK_BREAK,
                new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "idle")),
                0,
                null,
                null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("progressTicksAwarded must be > 0");
    }

    @Test
    void acceptsCaseInsensitiveTriggerTypeValues() {
        assertThat(RewardTriggerType.fromJson("block_break")).isEqualTo(RewardTriggerType.BLOCK_BREAK);
    }
}
