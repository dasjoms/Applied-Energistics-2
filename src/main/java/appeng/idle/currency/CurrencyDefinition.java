package appeng.idle.currency;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * Describes a single currency type and its display and generation defaults.
 */
public record CurrencyDefinition(
        CurrencyId id,
        String displayNameKey,
        ResourceLocation iconItem,
        double baseOnlineRate,
        boolean visibleByDefault,
        @Nullable CurrencyCaps caps) {

    public CurrencyDefinition {
        if (displayNameKey == null || displayNameKey.isBlank()) {
            throw new IllegalArgumentException("displayNameKey must not be blank");
        }
        if (iconItem == null) {
            throw new IllegalArgumentException("iconItem must not be null");
        }
        if (!Double.isFinite(baseOnlineRate) || baseOnlineRate < 0) {
            throw new IllegalArgumentException("baseOnlineRate must be finite and >= 0");
        }
    }

    public record CurrencyCaps(@Nullable Long onlineGenerationCap, @Nullable Long balanceCap) {
        public CurrencyCaps {
            if (onlineGenerationCap != null && onlineGenerationCap < 0) {
                throw new IllegalArgumentException("onlineGenerationCap must be >= 0 when set");
            }
            if (balanceCap != null && balanceCap < 0) {
                throw new IllegalArgumentException("balanceCap must be >= 0 when set");
            }
        }
    }
}
