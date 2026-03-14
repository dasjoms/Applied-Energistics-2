package appeng.idle.tick;

import appeng.idle.currency.CurrencyAmount;
import appeng.idle.currency.CurrencyId;
import appeng.idle.player.GenerationContext;

/**
 * Contract for generating currency each game tick.
 */
public interface GenerationRule {
    CurrencyAmount generatePerTick(GenerationContext ctx, CurrencyId currency);
}
