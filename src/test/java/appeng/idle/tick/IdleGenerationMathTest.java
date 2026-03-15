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

class IdleGenerationMathTest {

    @Test
    void ticksPerUnitForDisplayUsesFlooredEffectiveTicks() {
        assertThat(IdleGenerationMath.ticksPerUnitForDisplay(2.9)).isEqualTo(2L);
        assertThat(IdleGenerationMath.ticksPerUnitForDisplay(0.9)).isEqualTo(1L);
    }

    @Test
    void clampAwardByCapsReturnsZeroWhenBalanceCapReached() throws Exception {
        var currency = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "tick_math_cap_test"));
        var definition = new CurrencyDefinition(
                currency,
                "gui.ae2.idle.currency.tick_math_cap_test",
                "gui.ae2.idle.currency.tick_math_cap_test",
                ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal"),
                20L,
                true,
                new CurrencyDefinition.CurrencyCaps(null, 10L));

        withInjectedCurrency(definition, () -> {
            var data = new PlayerIdleData(Map.of(currency, 10L), 0L, PlayerIdleData.CURRENT_DATA_VERSION, Map.of());

            assertThat(IdleGenerationMath.remainingBalanceCapacity(data, currency)).isZero();
            assertThat(IdleGenerationMath.clampAwardByCaps(data, currency, 5L)).isZero();
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
