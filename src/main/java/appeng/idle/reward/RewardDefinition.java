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
        @Nullable UpgradeGate upgradeGate,
        @Nullable Long cooldownWindowTicks,
        @Nullable EnvironmentPredicate environmentPredicate,
        @Nullable RewardCaps caps) {

    public RewardDefinition(
            ResourceLocation id,
            RewardTriggerType triggerType,
            CurrencyId currencyId,
            long progressTicksAwarded,
            @Nullable BlockFilterCondition conditions,
            @Nullable ResourceLocation upgradeGateId) {
        this(id,
                triggerType,
                currencyId,
                progressTicksAwarded,
                conditions,
                upgradeGateId == null ? null : new UpgradeGate(upgradeGateId, 1),
                null,
                null,
                null);
    }

    public RewardDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(triggerType, "triggerType");
        Objects.requireNonNull(currencyId, "currencyId");

        if (progressTicksAwarded <= 0L) {
            throw new IllegalArgumentException("progressTicksAwarded must be > 0");
        }
    }

    public boolean isUngated() {
        return upgradeGate == null;
    }

    public @Nullable ResourceLocation upgradeGateId() {
        return upgradeGate == null ? null : upgradeGate.upgradeId();
    }

    public int upgradeGateMinLevel() {
        return upgradeGate == null ? 1 : upgradeGate.minLevel();
    }

    public record BlockFilterCondition(@Nullable ResourceLocation blockId, @Nullable ResourceLocation blockTagId) {
        public BlockFilterCondition {
            if (blockId == null && blockTagId == null) {
                throw new IllegalArgumentException("blockId or blockTagId must be set");
            }
        }
    }

    public record UpgradeGate(ResourceLocation upgradeId, int minLevel) {
        public UpgradeGate {
            Objects.requireNonNull(upgradeId, "upgradeId");
            if (minLevel <= 0) {
                throw new IllegalArgumentException("minLevel must be > 0");
            }
        }
    }

    public record EnvironmentPredicate(@Nullable ResourceLocation dimensionId, @Nullable ResourceLocation biomeId,
            @Nullable ResourceLocation biomeTagId) {
    }

    public record RewardCaps(@Nullable Integer dailyCap, @Nullable Integer intervalCap,
            @Nullable Long intervalWindowTicks) {
    }
}
