package appeng.idle.tick;

import appeng.core.AppEng;
import appeng.idle.currency.CurrencyAmount;
import appeng.idle.currency.CurrencyId;
import appeng.idle.player.GenerationContext;

/**
 * Basic implementation that generates 1 unit per tick while the player is online.
 */
public class BasicGenerationRule implements GenerationRule {
    private static final CurrencyId DEFAULT_CURRENCY = new CurrencyId(AppEng.makeId("idle"));

    @Override
    public CurrencyAmount generatePerTick(GenerationContext ctx, CurrencyId currency) {
        if (!ctx.online() || !DEFAULT_CURRENCY.equals(currency)) {
            return CurrencyAmount.ZERO;
        }

        var multiplier = ctx.multipliers().totalMultiplier();
        if (multiplier <= 0.0 || !Double.isFinite(multiplier)) {
            return CurrencyAmount.ZERO;
        }

        var generated = (long) Math.floor(multiplier);
        if (generated <= 0L) {
            return CurrencyAmount.ZERO;
        }

        return new CurrencyAmount(generated);
    }
}
