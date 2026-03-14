package appeng.idle.tick;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import appeng.idle.currency.CurrencyDefinition;
import appeng.idle.currency.CurrencyId;
import appeng.idle.currency.IdleCurrencyManager;
import appeng.idle.player.PlayerIdleData;

class IdleGenerationProgressServiceTest {
    @Test
    void accruesProgressAndPersistsRemainderUntilWholeUnitIsReady() throws Exception {
        var currency = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "progress_test_currency"));
        var definition = new CurrencyDefinition(
                currency,
                "gui.ae2.idle.currency.progress_test_currency",
                ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal"),
                10,
                true,
                null);

        withInjectedCurrency(definition, () -> {
            var data = new PlayerIdleData();

            var first = IdleGenerationProgressService.accrueOnlineProgress(data, 4, java.util.Set.of(currency));
            assertThat(first).isEmpty();
            assertThat(data.getGenerationProgressTicks(currency)).isEqualTo(4L);

            var second = IdleGenerationProgressService.accrueOnlineProgress(data, 7, java.util.Set.of(currency));
            assertThat(second).containsEntry(currency, 1L);
            assertThat(data.getGenerationProgressTicks(currency)).isEqualTo(1L);
        });
    }

    @Test
    void offlineAccrualConvertsSecondsToProgressTicksAndAwardsWholeUnits() throws Exception {
        var currency = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "offline_progress_test_currency"));
        var definition = new CurrencyDefinition(
                currency,
                "gui.ae2.idle.currency.offline_progress_test_currency",
                ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal"),
                10,
                true,
                null);

        withInjectedCurrency(definition, () -> {
            var data = new PlayerIdleData();
            var generated = IdleGenerationProgressService.accrueOfflineProgress(
                    data,
                    10L,
                    100L,
                    0.25,
                    1.0,
                    java.util.Set.of(currency));

            assertThat(generated).containsEntry(currency, 5L);
            assertThat(data.getGenerationProgressTicks(currency)).isZero();
        });
    }

    @Test
    void appliesGenerationAndBalanceCapsBeforeAwardingUnits() throws Exception {
        var currency = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "capped_progress_test_currency"));
        var definition = new CurrencyDefinition(
                currency,
                "gui.ae2.idle.currency.capped_progress_test_currency",
                ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal"),
                1,
                true,
                new CurrencyDefinition.CurrencyCaps(10L, 25L));

        withInjectedCurrency(definition, () -> {
            var data = new PlayerIdleData(Map.of(currency, 20L), 0L, PlayerIdleData.CURRENT_DATA_VERSION, Map.of());

            var generated = IdleGenerationProgressService.accrueOnlineProgress(data, 50, java.util.Set.of(currency));
            assertThat(generated).containsEntry(currency, 5L);
        });
    }

    private static void withInjectedCurrency(CurrencyDefinition definition, ThrowingRunnable assertion)
            throws Exception {
        var managerInstanceField = IdleCurrencyManager.class.getDeclaredField("INSTANCE");
        managerInstanceField.setAccessible(true);
        var managerInstance = managerInstanceField.get(null);

        var currenciesField = IdleCurrencyManager.class.getDeclaredField("currencies");
        currenciesField.setAccessible(true);

        @SuppressWarnings("unchecked")
        var originalCurrencies = (Map<CurrencyId, CurrencyDefinition>) currenciesField.get(managerInstance);

        var modified = new LinkedHashMap<>(originalCurrencies);
        modified.put(definition.id(), definition);

        try {
            currenciesField.set(managerInstance, Map.copyOf(modified));
            assertion.run();
        } finally {
            currenciesField.set(managerInstance, originalCurrencies);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
