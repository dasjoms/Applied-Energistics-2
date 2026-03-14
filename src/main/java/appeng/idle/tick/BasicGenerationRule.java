package appeng.idle.tick;

import appeng.idle.currency.CurrencyAmount;
import appeng.idle.currency.CurrencyId;
import appeng.idle.currency.IdleCurrencyManager;
import appeng.idle.player.GenerationContext;

/**
 * Basic implementation that grants one unit after each configured baseTicksPerUnit interval while online.
 */
public class BasicGenerationRule implements GenerationRule {

    @Override
    public CurrencyAmount generatePerTick(GenerationContext ctx, CurrencyId currency) {
        if (!ctx.online()) {
            return CurrencyAmount.ZERO;
        }

        var definition = IdleCurrencyManager.get(currency);
        if (definition == null) {
            return CurrencyAmount.ZERO;
        }

        var multiplier = ctx.multipliers().totalMultiplier();
        if (multiplier <= 0.0 || !Double.isFinite(multiplier)) {
            return CurrencyAmount.ZERO;
        }

        var generated = (long) Math.floor(multiplier / definition.baseTicksPerUnit());
        if (generated <= 0L) {
            return CurrencyAmount.ZERO;
        }

        return new CurrencyAmount(generated);
    }
}
