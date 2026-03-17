package appeng.client.idle.combat;

import appeng.idle.net.IdleCombatHudState;
import appeng.idle.net.IdleCurrencyClientCache;

/**
 * Shared client-side view of whether idle combat mode is active.
 * <p>
 * This intentionally relies on the latest server snapshot as the authority, so all client systems gate behavior on the
 * same predicate source.
 */
public final class IdleCombatClientState {
    private IdleCombatClientState() {
    }

    public static boolean isIdleCombatModeActive() {
        return isIdleCombatModeActive(IdleCurrencyClientCache.getCombatHudState());
    }

    public static boolean isIdleCombatModeActive(IdleCombatHudState snapshot) {
        return snapshot != null && snapshot != IdleCombatHudState.EMPTY && snapshot.inIdleCombatMode();
    }
}
