package appeng.idle.tick;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import appeng.idle.currency.CurrencyDefinition;
import appeng.idle.currency.CurrencyId;
import appeng.idle.currency.IdleCurrencyManager;
import appeng.idle.player.PlayerIdleData;

class IdleRateCalculatorTest {
    @Test
    void calculateGeneratedForIntervalAppliesGenerationCap() throws Exception {
        var cappedCurrency = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "capped_test_currency"));
        var definition = new CurrencyDefinition(
                cappedCurrency,
                "gui.ae2.idle.currency.capped_test_currency",
                ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal"),
                7.0,
                true,
                new CurrencyDefinition.CurrencyCaps(10L, null));

        withInjectedCurrency(definition, () -> {
            var generated = IdleRateCalculator.calculateGeneratedForInterval(
                    UUID.randomUUID(),
                    new PlayerIdleData(),
                    3,
                    Set.of(cappedCurrency));

            assertThat(generated).containsEntry(cappedCurrency, 10L);
        });
    }

    @Test
    void calculateGeneratedForIntervalUsesFlooredPerTickFromGenerationRule() throws Exception {
        var currency = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "fractional_test_currency"));
        var definition = new CurrencyDefinition(
                currency,
                "gui.ae2.idle.currency.fractional_test_currency",
                ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal"),
                1.9,
                true,
                null);

        withInjectedCurrency(definition, () -> {
            var generated = IdleRateCalculator.calculateGeneratedForInterval(
                    UUID.randomUUID(),
                    new PlayerIdleData(),
                    20,
                    Set.of(currency));

            assertThat(generated).containsEntry(currency, 20L);
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
