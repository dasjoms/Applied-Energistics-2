package appeng.idle.upgrade;

import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

/**
 * Declarative definition of an idle upgrade.
 */
public record UpgradeDefinition(ResourceLocation id, int maxLevel, CostBundle costPerLevel, UpgradeEffects effects) {
    public UpgradeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(costPerLevel, "costPerLevel");
        Objects.requireNonNull(effects, "effects");

        if (maxLevel <= 0) {
            throw new IllegalArgumentException("maxLevel must be > 0");
        }
    }
}
