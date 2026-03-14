package appeng.idle.net;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import appeng.idle.currency.CurrencyId;

/**
 * Client-side read-only cache for server-authoritative idle currency balances.
 */
public final class IdleCurrencyClientCache {
    private static volatile Map<CurrencyId, Long> balances = Map.of();
    private static volatile Map<CurrencyId, IdleCurrencyHudValue> hudValues = Map.of();

    private IdleCurrencyClientCache() {
    }

    public static Map<CurrencyId, Long> getBalances() {
        return balances;
    }

    public static void applySnapshot(Map<CurrencyId, Long> snapshot) {
        balances = Collections.unmodifiableMap(new LinkedHashMap<>(snapshot));
    }

    public static Map<CurrencyId, IdleCurrencyHudValue> getHudValues() {
        return hudValues;
    }

    public static void applyHudSnapshot(Map<CurrencyId, IdleCurrencyHudValue> snapshot) {
        hudValues = Collections.unmodifiableMap(new LinkedHashMap<>(snapshot));
    }

    public static void applyDelta(Map<CurrencyId, Long> delta) {
        if (delta.isEmpty()) {
            return;
        }

        var merged = new LinkedHashMap<>(balances);
        for (var entry : delta.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0L) {
                merged.remove(entry.getKey());
            } else {
                merged.put(entry.getKey(), entry.getValue());
            }
        }

        balances = Collections.unmodifiableMap(merged);
    }
}
