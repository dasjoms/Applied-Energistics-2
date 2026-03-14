package appeng.idle.tick;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import appeng.idle.currency.CurrencyDefinition;
import appeng.idle.currency.CurrencyId;
import appeng.idle.currency.IdleCurrencies;
import appeng.idle.currency.IdleCurrencyManager;

class IdleGenerationTickerTest {
    @Test
    void computesOfflineGeneratedUsingBaseRateAndPercent() {
        var generated = IdleGenerationTicker.computeOfflineGenerated(1L, 10L, 100L, 0.25, 1.0);

        assertThat(generated).isEqualTo(50L);
    }

    @Test
    void capsOfflineSecondsBeforeApplyingCatchup() {
        var generated = IdleGenerationTicker.computeOfflineGenerated(2L, 120L, 30L, 0.5, 1.0);

        assertThat(generated).isEqualTo(600L);
    }

    @Test
    void returnsZeroForInvalidInputs() {
        assertThat(IdleGenerationTicker.computeOfflineGenerated(0L, 10L, 10L, 0.25, 1.0)).isZero();
        assertThat(IdleGenerationTicker.computeOfflineGenerated(1L, 0L, 10L, 0.25, 1.0)).isZero();
        assertThat(IdleGenerationTicker.computeOfflineGenerated(1L, 10L, 0L, 0.25, 1.0)).isZero();
        assertThat(IdleGenerationTicker.computeOfflineGenerated(1L, 10L, 10L, 0.0, 1.0)).isZero();
        assertThat(IdleGenerationTicker.computeOfflineGenerated(1L, 10L, 10L, 0.25, 0.0)).isZero();
    }

    @Test
    void currenciesToGenerateUsesLoadedRegistryCurrencies() throws Exception {
        var manager = getIdleCurrencyManagerInstance();
        var originalCurrencies = getManagerCurrencies(manager);

        try {
            var extraCurrencyId = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "test_currency"));
            var definition = new CurrencyDefinition(extraCurrencyId, "test.currency", ResourceLocation.withDefaultNamespace("stone"),
                    1.0, true, null);

            var testCurrencies = new LinkedHashMap<CurrencyId, CurrencyDefinition>();
            testCurrencies.put(IdleCurrencies.IDLE, IdleCurrencyManager.get(IdleCurrencies.IDLE));
            testCurrencies.put(extraCurrencyId, definition);

            setManagerCurrencies(manager, Collections.unmodifiableMap(testCurrencies));

            var generatedCurrencies = invokeCurrenciesToGenerate();
            assertThat(generatedCurrencies).containsExactlyInAnyOrder(IdleCurrencies.IDLE, extraCurrencyId);
        } finally {
            setManagerCurrencies(manager, originalCurrencies);
        }
    }

    @Test
    void currenciesToGenerateFallsBackToDefaultWhenRegistryIsEmpty() throws Exception {
        var manager = getIdleCurrencyManagerInstance();
        var originalCurrencies = getManagerCurrencies(manager);

        try {
            setManagerCurrencies(manager, Collections.emptyMap());

            var generatedCurrencies = invokeCurrenciesToGenerate();
            assertThat(generatedCurrencies).containsExactly(IdleCurrencies.IDLE);
        } finally {
            setManagerCurrencies(manager, originalCurrencies);
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<CurrencyId> invokeCurrenciesToGenerate() throws Exception {
        var method = IdleGenerationTicker.class.getDeclaredMethod("currenciesToGenerate");
        method.setAccessible(true);
        return (Set<CurrencyId>) method.invoke(null);
    }

    private static Object getIdleCurrencyManagerInstance() throws Exception {
        Field instanceField = IdleCurrencyManager.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        return instanceField.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<CurrencyId, CurrencyDefinition> getManagerCurrencies(Object manager) throws Exception {
        Field currenciesField = IdleCurrencyManager.class.getDeclaredField("currencies");
        currenciesField.setAccessible(true);
        return (Map<CurrencyId, CurrencyDefinition>) currenciesField.get(manager);
    }

    private static void setManagerCurrencies(Object manager, Map<CurrencyId, CurrencyDefinition> currencies) throws Exception {
        Field currenciesField = IdleCurrencyManager.class.getDeclaredField("currencies");
        currenciesField.setAccessible(true);
        currenciesField.set(manager, currencies);
    }
}
