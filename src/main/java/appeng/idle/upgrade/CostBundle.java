package appeng.idle.upgrade;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import appeng.idle.currency.CurrencyId;

/**
 * Immutable map of required amounts per currency for a transaction.
 */
public record CostBundle(Map<CurrencyId, Long> costs) {
    public static final CostBundle EMPTY = new CostBundle(Map.of());

    public CostBundle {
        Objects.requireNonNull(costs, "costs");

        var normalized = new LinkedHashMap<CurrencyId, Long>();
        for (var entry : costs.entrySet()) {
            var currencyId = Objects.requireNonNull(entry.getKey(), "currencyId");
            var amount = Objects.requireNonNull(entry.getValue(), "amount");
            if (amount < 0L) {
                throw new IllegalArgumentException("Cost values must be >= 0");
            }
            if (amount == 0L) {
                continue;
            }
            normalized.put(currencyId, amount);
        }

        costs = Collections.unmodifiableMap(normalized);
    }

    public boolean isEmpty() {
        return costs.isEmpty();
    }

    public long requiredAmount(CurrencyId currencyId) {
        return costs.getOrDefault(currencyId, 0L);
    }
}
