package appeng.client.idle.combat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import appeng.idle.net.IdleCombatHudState;
import appeng.idle.net.IdleCurrencyClientCache;

class IdleCombatClientStateTest {

    @AfterEach
    void resetCombatHudState() {
        IdleCurrencyClientCache.applyCombatHudState(IdleCombatHudState.EMPTY);
    }

    @Test
    void reportsActiveOnlyWhenSnapshotEnablesIdleCombatMode() {
        assertThat(IdleCombatClientState.isIdleCombatModeActive((IdleCombatHudState) null)).isFalse();
        assertThat(IdleCombatClientState.isIdleCombatModeActive(IdleCombatHudState.EMPTY)).isFalse();
        assertThat(IdleCombatClientState.isIdleCombatModeActive(new IdleCombatHudState(0L, 0L, 0L, 0L, 0L, false)))
                .isFalse();
        assertThat(IdleCombatClientState.isIdleCombatModeActive(new IdleCombatHudState(0L, 0L, 0L, 0L, 0L, true)))
                .isTrue();
    }

    @Test
    void noArgOverloadReadsServerSnapshotCache() {
        IdleCurrencyClientCache.applyCombatHudState(new IdleCombatHudState(5L, 1L, 2L, 10L, 10L, true));

        assertThat(IdleCombatClientState.isIdleCombatModeActive()).isTrue();
    }
}
