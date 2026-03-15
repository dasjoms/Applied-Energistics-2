package appeng.idle.tick;

import appeng.idle.currency.CurrencyId;
import appeng.idle.currency.IdleCurrencyManager;
import appeng.idle.generation.IdleGenerationCapService;
import appeng.idle.player.PlayerIdleData;
import appeng.idle.upgrade.IdleUpgradeHooks;

/**
 * Shared idle generation math used by both accrual and server HUD projections.
 */
public final class IdleGenerationMath {
    private IdleGenerationMath() {
    }

    public static double effectiveTicksPerUnit(PlayerIdleData data, CurrencyId currency) {
        var definition = IdleCurrencyManager.get(currency);
        if (definition == null) {
            return 0.0;
        }

        var baseTicksPerUnit = definition.baseTicksPerUnit();
        var multiplier = IdleUpgradeHooks.getOnlineGenerationMultipliers(data, currency).totalMultiplier();
        return effectiveTicksPerUnit(baseTicksPerUnit, multiplier);
    }

    public static double effectiveTicksPerUnit(long baseTicksPerUnit, double totalMultiplier) {
        if (baseTicksPerUnit <= 0L || totalMultiplier <= 0.0 || !Double.isFinite(totalMultiplier)) {
            return 0.0;
        }

        var effectiveTicksPerUnit = baseTicksPerUnit / totalMultiplier;
        return effectiveTicksPerUnit > 0.0 && Double.isFinite(effectiveTicksPerUnit) ? effectiveTicksPerUnit : 0.0;
    }

    public static long ticksPerUnitForDisplay(PlayerIdleData data, CurrencyId currency) {
        return ticksPerUnitForDisplay(effectiveTicksPerUnit(data, currency));
    }

    public static long ticksPerUnitForDisplay(double effectiveTicksPerUnit) {
        if (effectiveTicksPerUnit <= 0.0 || !Double.isFinite(effectiveTicksPerUnit)) {
            return 0L;
        }

        return Math.max(1L, (long) Math.floor(effectiveTicksPerUnit));
    }

    public static long clampAwardByCaps(PlayerIdleData data, CurrencyId currency, long wholeUnits) {
        if (wholeUnits <= 0L) {
            return 0L;
        }

        var cappedByGeneration = IdleGenerationCapService.clampOnlineGenerationCap(currency, wholeUnits);
        if (cappedByGeneration <= 0L) {
            return 0L;
        }

        return Math.min(cappedByGeneration, remainingBalanceCapacity(data, currency));
    }

    public static long remainingBalanceCapacity(PlayerIdleData data, CurrencyId currency) {
        var definition = IdleCurrencyManager.get(currency);
        var caps = definition == null ? null : definition.caps();
        var balanceCap = caps == null ? null : caps.balanceCap();
        if (balanceCap == null) {
            return Long.MAX_VALUE;
        }

        var currentBalance = data.getBalance(currency);
        if (currentBalance >= balanceCap) {
            return 0L;
        }

        return balanceCap - currentBalance;
    }
}
