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
    private static volatile Map<CurrencyId, Long> rates = Map.of();
    private static volatile Map<CurrencyId, IdleCurrencyHudValue> hudValues = Map.of();
    private static volatile boolean idlePunchEligible;
    private static volatile IdleCombatHudState combatHudSnapshot = IdleCombatHudState.EMPTY;

    private IdleCurrencyClientCache() {
    }

    public static Map<CurrencyId, Long> getBalances() {
        return balances;
    }

    public static Map<CurrencyId, Long> getBalanceMap() {
        return balances;
    }

    public static Map<CurrencyId, Long> getRates() {
        return rates;
    }

    public static Map<CurrencyId, Long> getRateMap() {
        return rates;
    }

    public static boolean isIdlePunchEligible() {
        return idlePunchEligible;
    }

    public static void applySnapshot(Map<CurrencyId, Long> snapshot, Map<CurrencyId, Long> rateSnapshot,
            boolean idlePunchEligibility) {
        balances = Collections.unmodifiableMap(new LinkedHashMap<>(snapshot));
        rates = Collections.unmodifiableMap(new LinkedHashMap<>(rateSnapshot));
        idlePunchEligible = idlePunchEligibility;
    }

    public static Map<CurrencyId, IdleCurrencyHudValue> getHudValues() {
        return hudValues;
    }

    public static void applyHudSnapshot(Map<CurrencyId, IdleCurrencyHudValue> snapshot) {
        hudValues = Collections.unmodifiableMap(new LinkedHashMap<>(snapshot));
    }

    public static IdleCombatHudState getCombatHudSnapshot() {
        return combatHudSnapshot;
    }

    public static IdleCombatHudState getCombatHudState() {
        return getCombatHudSnapshot();
    }

    public static void applyCombatHudSnapshot(IdleCombatHudState snapshot) {
        combatHudSnapshot = snapshot;
    }

    public static void applyCombatHudState(IdleCombatHudState snapshot) {
        applyCombatHudSnapshot(snapshot);
    }

    public static void clearCombatHudSnapshot() {
        combatHudSnapshot = IdleCombatHudState.EMPTY;
    }

    public static void applyDelta(Map<CurrencyId, Long> delta, Map<CurrencyId, Long> refreshedRates,
            boolean idlePunchEligibility) {
        idlePunchEligible = idlePunchEligibility;

        if (delta.isEmpty() && refreshedRates.isEmpty()) {
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

        if (!refreshedRates.isEmpty()) {
            rates = Collections.unmodifiableMap(new LinkedHashMap<>(refreshedRates));
        }
    }
}
