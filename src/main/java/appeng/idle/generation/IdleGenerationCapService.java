package appeng.idle.generation;

import appeng.idle.currency.CurrencyId;
import appeng.idle.currency.IdleCurrencyManager;

/**
 * Shared cap logic for idle generation accrual and HUD projections.
 */
public final class IdleGenerationCapService {
    private IdleGenerationCapService() {
    }

    public static long clampOnlineGenerationCap(CurrencyId currency, long generatedAmount) {
        if (generatedAmount <= 0L) {
            return 0L;
        }

        var onlineGenerationCap = onlineGenerationCap(currency);
        return onlineGenerationCap == null ? generatedAmount : Math.min(generatedAmount, onlineGenerationCap);
    }

    public static double clampOnlineGenerationCap(CurrencyId currency, double generatedAmount) {
        if (generatedAmount <= 0.0 || !Double.isFinite(generatedAmount)) {
            return 0.0;
        }

        var onlineGenerationCap = onlineGenerationCap(currency);
        return onlineGenerationCap == null ? generatedAmount : Math.min(generatedAmount, onlineGenerationCap);
    }

    private static Long onlineGenerationCap(CurrencyId currency) {
        var definition = IdleCurrencyManager.get(currency);
        var caps = definition == null ? null : definition.caps();
        return caps == null ? null : caps.onlineGenerationCap();
    }
}
