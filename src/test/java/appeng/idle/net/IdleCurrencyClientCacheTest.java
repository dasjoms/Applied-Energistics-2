package appeng.idle.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import appeng.idle.currency.CurrencyId;

class IdleCurrencyClientCacheTest {
    private static final CurrencyId IDLE = currency("idle");
    private static final CurrencyId MATTER = currency("matter");

    @AfterEach
    void resetCache() {
        IdleCurrencyClientCache.applySnapshot(Map.of(), Map.of());
    }

    @Test
    void snapshotReplacesCacheContents() {
        IdleCurrencyClientCache.applySnapshot(Map.of(IDLE, 10L), Map.of(IDLE, 2L));
        IdleCurrencyClientCache.applySnapshot(Map.of(MATTER, 5L), Map.of(MATTER, 7L));

        assertThat(IdleCurrencyClientCache.getBalances())
                .containsExactlyEntriesOf(Map.of(MATTER, 5L));
        assertThat(IdleCurrencyClientCache.getRates())
                .containsExactlyEntriesOf(Map.of(MATTER, 7L));
    }

    @Test
    void snapshotAppliesBalancesAndRatesTogether() {
        IdleCurrencyClientCache.applySnapshot(
                Map.of(IDLE, 40L, MATTER, 15L),
                Map.of(IDLE, 3L, MATTER, 9L));

        assertThat(IdleCurrencyClientCache.getBalanceMap())
                .containsExactlyEntriesOf(Map.of(IDLE, 40L, MATTER, 15L));
        assertThat(IdleCurrencyClientCache.getRateMap())
                .containsExactlyEntriesOf(Map.of(IDLE, 3L, MATTER, 9L));
    }

    @Test
    void deltaMergesBalanceUpdatesAndCanRefreshRates() {
        IdleCurrencyClientCache.applySnapshot(Map.of(IDLE, 10L, MATTER, 3L), Map.of(IDLE, 1L));

        IdleCurrencyClientCache.applyDelta(Map.of(
                IDLE, 12L,
                MATTER, 0L), Map.of(IDLE, 4L, MATTER, 2L));

        assertThat(IdleCurrencyClientCache.getBalances())
                .containsExactlyEntriesOf(Map.of(IDLE, 12L));
        assertThat(IdleCurrencyClientCache.getRates())
                .containsExactlyEntriesOf(Map.of(IDLE, 4L, MATTER, 2L));
    }

    @Test
    void deltaRemovesBalancesForZeroNegativeAndNullValues() {
        IdleCurrencyClientCache.applySnapshot(Map.of(IDLE, 10L, MATTER, 3L), Map.of(IDLE, 1L, MATTER, 2L));

        var delta = new LinkedHashMap<CurrencyId, Long>();
        delta.put(IDLE, 0L);
        delta.put(MATTER, null);
        delta.put(currency("flux"), -5L);

        IdleCurrencyClientCache.applyDelta(delta, Map.of());

        assertThat(IdleCurrencyClientCache.getBalances()).isEmpty();
        assertThat(IdleCurrencyClientCache.getRates())
                .containsExactlyEntriesOf(Map.of(IDLE, 1L, MATTER, 2L));
    }

    @Test
    void mapAccessorsAreReadOnly() {
        IdleCurrencyClientCache.applySnapshot(Map.of(IDLE, 10L), Map.of(IDLE, 2L));

        assertThatThrownBy(() -> IdleCurrencyClientCache.getBalanceMap().put(MATTER, 5L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> IdleCurrencyClientCache.getRateMap().put(MATTER, 1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void emptyHudSnapshotClearsClientHudPayload() {
        IdleCurrencyClientCache.applyHudSnapshot(Map.of(IDLE, new IdleCurrencyHudValue(11L, 0.5, 3L, 20L, 1L)));

        IdleCurrencyClientCache.applyHudSnapshot(Map.of());

        assertThat(IdleCurrencyClientCache.getHudValues()).isEmpty();
    }

    private static CurrencyId currency(String path) {
        return new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", path));
    }
}
