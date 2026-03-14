package appeng.idle.currency;

import java.util.Map;

/**
 * Core contract for currency balance storage and mutation.
 */
public interface CurrencyLedger {
    CurrencyAmount get(CurrencyId currency);

    void add(CurrencyId currency, CurrencyAmount amount, String reason);

    boolean trySpend(CurrencyId currency, CurrencyAmount amount, String reason);

    Map<CurrencyId, CurrencyAmount> snapshot();
}
