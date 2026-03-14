package appeng.idle.upgrade;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import net.neoforged.bus.api.Event;

import appeng.idle.currency.CurrencyId;

/**
 * Event emitted for idle currency transactions for debugging/analytics hooks.
 */
public class CurrencyTransactionEvent extends Event {
    private final TransactionType type;
    private final SpendReason reason;
    private final CostBundle bundle;
    private final Map<CurrencyId, Long> balancesBefore;
    private final Map<CurrencyId, Long> balancesAfter;
    private final String detail;

    public CurrencyTransactionEvent(TransactionType type, SpendReason reason, CostBundle bundle,
            Map<CurrencyId, Long> balancesBefore, Map<CurrencyId, Long> balancesAfter, String detail) {
        this.type = Objects.requireNonNull(type, "type");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.bundle = Objects.requireNonNull(bundle, "bundle");
        this.balancesBefore = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(balancesBefore)));
        this.balancesAfter = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(balancesAfter)));
        this.detail = Objects.requireNonNullElse(detail, "");
    }

    public TransactionType type() {
        return type;
    }

    public SpendReason reason() {
        return reason;
    }

    public CostBundle bundle() {
        return bundle;
    }

    public Map<CurrencyId, Long> balancesBefore() {
        return balancesBefore;
    }

    public Map<CurrencyId, Long> balancesAfter() {
        return balancesAfter;
    }

    public String detail() {
        return detail;
    }

    public enum TransactionType {
        SPEND_SUCCESS,
        SPEND_FAILED,
        REFUND
    }
}
