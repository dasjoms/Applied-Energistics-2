package appeng.idle.tick;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

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
    void currenciesToGenerateUsesRegisteredCurrenciesNotPlayerBalances() throws Exception {
        var managerInstanceField = IdleCurrencyManager.class.getDeclaredField("INSTANCE");
        managerInstanceField.setAccessible(true);
        var managerInstance = managerInstanceField.get(null);

        var currenciesField = IdleCurrencyManager.class.getDeclaredField("currencies");
        currenciesField.setAccessible(true);

        @SuppressWarnings("unchecked")
        var originalCurrencies = (Map<CurrencyId, CurrencyDefinition>) currenciesField.get(managerInstance);

        var extraId = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "extra_test_currency"));
        var extraDefinition = new CurrencyDefinition(
                extraId,
                "gui.ae2.idle.currency.extra_test_currency",
                ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal"),
                1.0,
                true,
                null);

        var modified = new LinkedHashMap<>(originalCurrencies);
        modified.put(extraId, extraDefinition);

        try {
            currenciesField.set(managerInstance, Map.copyOf(modified));

            var currenciesToGenerate = IdleGenerationTicker.currenciesToGenerate();
            assertThat(currenciesToGenerate)
                    .contains(IdleCurrencies.IDLE, extraId);
        } finally {
            currenciesField.set(managerInstance, originalCurrencies);
        }
    }
}
