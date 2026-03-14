package appeng.idle.tick;

import appeng.idle.currency.CurrencyAmount;
import appeng.idle.currency.CurrencyId;
import appeng.idle.player.GenerationContext;

/**
 * Placeholder implementation that does not generate currency yet.
 */
public class BasicGenerationRule implements GenerationRule {
    @Override
    public CurrencyAmount generatePerTick(GenerationContext ctx, CurrencyId currency) {
        return CurrencyAmount.ZERO;
    }
}
