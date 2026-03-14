package appeng.idle.currency;

import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

/**
 * Unique identifier for a currency type.
 */
public record CurrencyId(ResourceLocation id) {
    public CurrencyId {
        Objects.requireNonNull(id, "id");
    }
}
