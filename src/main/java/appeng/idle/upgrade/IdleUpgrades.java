package appeng.idle.upgrade;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import appeng.idle.currency.IdleCurrencies;

/**
 * Built-in idle upgrade definitions.
 */
public final class IdleUpgrades {
    public static final UpgradeDefinition NO_OP = new UpgradeDefinition(
            id("noop"),
            1,
            CostBundle.EMPTY,
            UpgradeEffects.NO_OP);

    public static final UpgradeDefinition OFFLINE_EFFICIENCY_1 = new UpgradeDefinition(
            id("offline_efficiency_1"),
            5,
            new CostBundle(Map.of(IdleCurrencies.IDLE, 25L)),
            new UpgradeEffects() {
                @Override
                public double offlinePercentBonus() {
                    return 0.10;
                }
            });

    public static final UpgradeDefinition TIMBER_1 = new UpgradeDefinition(
            id("timber_1"),
            5,
            new CostBundle(Map.of(IdleCurrencies.IDLE, 100L)),
            new UpgradeEffects() {
                @Override
                public int timberLogLimitPerLevel() {
                    return 10;
                }
            });

    public static final UpgradeDefinition COMBAT_1 = new UpgradeDefinition(
            id("combat_1"),
            5,
            new CostBundle(Map.of(IdleCurrencies.IDLE, 200L)),
            new UpgradeEffects() {
                @Override
                public boolean enablesUnarmedDualPunch() {
                    return true;
                }

                @Override
                public double unarmedPunchCooldownMultiplier() {
                    return 1.0;
                }
            });

    private static final Map<ResourceLocation, UpgradeDefinition> DEFINITIONS = Map.of(
            NO_OP.id(), NO_OP,
            OFFLINE_EFFICIENCY_1.id(), OFFLINE_EFFICIENCY_1,
            TIMBER_1.id(), TIMBER_1,
            COMBAT_1.id(), COMBAT_1);

    private IdleUpgrades() {
    }

    public static UpgradeDefinition get(ResourceLocation id) {
        return DEFINITIONS.get(id);
    }

    public static Map<ResourceLocation, UpgradeDefinition> all() {
        return DEFINITIONS;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("ae2", path);
    }
}
