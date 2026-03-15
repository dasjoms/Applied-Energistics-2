package appeng.idle.reward;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;

import appeng.idle.currency.CurrencyId;

/**
 * Declarative definition of an idle reward entry loaded from data packs.
 */
public record RewardDefinition(
        ResourceLocation id,
        RewardTriggerType triggerType,
        CurrencyId currencyId,
        long progressTicksAwarded,
        @Nullable BlockFilterCondition conditions,
        @Nullable ResourceLocation upgradeGateId) {

    public RewardDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(triggerType, "triggerType");
        Objects.requireNonNull(currencyId, "currencyId");

        if (progressTicksAwarded <= 0L) {
            throw new IllegalArgumentException("progressTicksAwarded must be > 0");
        }
    }

    public boolean isUngated() {
        return upgradeGateId == null;
    }

    public record BlockFilterCondition(@Nullable ResourceLocation blockId, @Nullable ResourceLocation blockTagId) {
        public BlockFilterCondition {
            if (blockId == null && blockTagId == null) {
                throw new IllegalArgumentException("blockId or blockTagId must be set");
            }
        }
    }
}
