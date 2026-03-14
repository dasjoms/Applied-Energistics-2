package appeng.idle.net;

import static org.assertj.core.api.Assertions.assertThat;

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
        IdleCurrencyClientCache.applySnapshot(Map.of());
    }

    @Test
    void snapshotReplacesCacheContents() {
        IdleCurrencyClientCache.applySnapshot(Map.of(IDLE, 10L));
        IdleCurrencyClientCache.applySnapshot(Map.of(MATTER, 5L));

        assertThat(IdleCurrencyClientCache.getBalances())
                .containsExactlyEntriesOf(Map.of(MATTER, 5L));
    }

    @Test
    void deltaMergesUpdatesAndRemovesNonPositiveEntries() {
        IdleCurrencyClientCache.applySnapshot(Map.of(IDLE, 10L, MATTER, 3L));

        IdleCurrencyClientCache.applyDelta(Map.of(
                IDLE, 12L,
                MATTER, 0L));

        assertThat(IdleCurrencyClientCache.getBalances())
                .containsExactlyEntriesOf(Map.of(IDLE, 12L));
    }

    private static CurrencyId currency(String path) {
        return new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", path));
    }
}
