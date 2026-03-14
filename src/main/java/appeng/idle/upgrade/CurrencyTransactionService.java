package appeng.idle.upgrade;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import net.neoforged.neoforge.common.NeoForge;

import appeng.core.AELog;
import appeng.idle.currency.CurrencyId;
import appeng.idle.player.PlayerIdleData;

/**
 * Performs atomic multi-currency spends/refunds against {@link PlayerIdleData}.
 */
public final class CurrencyTransactionService {
    private CurrencyTransactionService() {
    }

    public static boolean canAfford(PlayerIdleData data, CostBundle costBundle) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(costBundle, "costBundle");

        synchronized (data) {
            return canAffordLocked(data, costBundle);
        }
    }

    public static boolean trySpend(PlayerIdleData data, CostBundle costBundle, SpendReason reason) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(costBundle, "costBundle");
        Objects.requireNonNull(reason, "reason");

        synchronized (data) {
            var before = snapshotBalances(data, costBundle);

            if (!canAffordLocked(data, costBundle)) {
                emit(CurrencyTransactionEvent.TransactionType.SPEND_FAILED, reason, costBundle, before, before,
                        "insufficient_funds");
                return false;
            }

            for (var cost : costBundle.costs().entrySet()) {
                var currentBalance = data.getBalance(cost.getKey());
                data.setBalance(cost.getKey(), currentBalance - cost.getValue());
            }

            var after = snapshotBalances(data, costBundle);
            emit(CurrencyTransactionEvent.TransactionType.SPEND_SUCCESS, reason, costBundle, before, after, "ok");
            return true;
        }
    }

    public static void refund(PlayerIdleData data, CostBundle costBundle, SpendReason reason) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(costBundle, "costBundle");
        Objects.requireNonNull(reason, "reason");

        synchronized (data) {
            var before = snapshotBalances(data, costBundle);
            for (var refund : costBundle.costs().entrySet()) {
                var updated = Math.addExact(data.getBalance(refund.getKey()), refund.getValue());
                data.setBalance(refund.getKey(), updated);
            }

            var after = snapshotBalances(data, costBundle);
            emit(CurrencyTransactionEvent.TransactionType.REFUND, reason, costBundle, before, after, "ok");
        }
    }

    private static boolean canAffordLocked(PlayerIdleData data, CostBundle costBundle) {
        for (var cost : costBundle.costs().entrySet()) {
            if (data.getBalance(cost.getKey()) < cost.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static Map<CurrencyId, Long> snapshotBalances(PlayerIdleData data, CostBundle costBundle) {
        var snapshot = new LinkedHashMap<CurrencyId, Long>();
        for (var currencyId : costBundle.costs().keySet()) {
            snapshot.put(currencyId, data.getBalance(currencyId));
        }
        return snapshot;
    }

    private static void emit(CurrencyTransactionEvent.TransactionType type, SpendReason reason, CostBundle bundle,
            Map<CurrencyId, Long> before, Map<CurrencyId, Long> after, String detail) {
        AELog.info(
                "Idle currency transaction type={} reason={} detail={} bundle={} before={} after={}",
                type,
                reason,
                detail,
                bundle.costs(),
                before,
                after);

        NeoForge.EVENT_BUS.post(new CurrencyTransactionEvent(type, reason, bundle, before, after, detail));
    }
}
