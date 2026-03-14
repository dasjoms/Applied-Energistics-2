package appeng.idle.tick;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
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
import appeng.idle.player.PlayerIdleDataManager;

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
    void registryCurrencyGeneratesWithoutPreseededBalanceEntry() throws Exception {
        var manager = getIdleCurrencyManagerInstance();
        var originalCurrencies = getManagerCurrencies(manager);

        try {
            var extraCurrencyId = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "new_registry_currency"));
            var definition = new CurrencyDefinition(extraCurrencyId, "new.registry.currency",
                    ResourceLocation.withDefaultNamespace("stone"), 1.0, true, null);

            var testCurrencies = new LinkedHashMap<CurrencyId, CurrencyDefinition>();
            testCurrencies.put(IdleCurrencies.IDLE, IdleCurrencyManager.get(IdleCurrencies.IDLE));
            testCurrencies.put(extraCurrencyId, definition);

            setManagerCurrencies(manager, Collections.unmodifiableMap(testCurrencies));

            assertThat(invokeCurrenciesToGenerate()).contains(extraCurrencyId);
            assertThat(computeOfflineGeneratedForCurrency(extraCurrencyId, 1L, 5L, 5L, 1.0, 1.0)).isEqualTo(100L);
        } finally {
            setManagerCurrencies(manager, originalCurrencies);
        }
    }

    @Test
    void offlineCatchupUsesSameCurrencyUniverseAsOnlineGeneration() throws Exception {
        var manager = getIdleCurrencyManagerInstance();
        var originalCurrencies = getManagerCurrencies(manager);

        try {
            var extraCurrencyId = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "offline_online_universe"));
            var definition = new CurrencyDefinition(extraCurrencyId, "offline.online.universe",
                    ResourceLocation.withDefaultNamespace("stone"), 2.0, true, null);

            var testCurrencies = new LinkedHashMap<CurrencyId, CurrencyDefinition>();
            testCurrencies.put(IdleCurrencies.IDLE, IdleCurrencyManager.get(IdleCurrencies.IDLE));
            testCurrencies.put(extraCurrencyId, definition);

            setManagerCurrencies(manager, Collections.unmodifiableMap(testCurrencies));

            var currencies = invokeCurrenciesToGenerate();
            assertThat(currencies).containsExactlyInAnyOrder(IdleCurrencies.IDLE, extraCurrencyId);
            assertThat(currencies).contains(extraCurrencyId);
        } finally {
            setManagerCurrencies(manager, originalCurrencies);
        }
    }

    @Test
    void onlineGenerationCapClampsPerIntervalAccrual() throws Exception {
        var manager = getIdleCurrencyManagerInstance();
        var originalCurrencies = getManagerCurrencies(manager);

        try {
            var cappedCurrency = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "online_generation_cap"));
            var definition = new CurrencyDefinition(cappedCurrency, "online.generation.cap",
                    ResourceLocation.withDefaultNamespace("stone"), 1.0, true,
                    new CurrencyDefinition.CurrencyCaps(75L, null));

            setManagerCurrencies(manager, Map.of(cappedCurrency, definition));

            assertThat(clampOnlineGenerationCap(cappedCurrency, 100L)).isEqualTo(75L);
        } finally {
            setManagerCurrencies(manager, originalCurrencies);
        }
    }

    @Test
    void onlineGenerationCapZeroBlocksAccrual() throws Exception {
        var manager = getIdleCurrencyManagerInstance();
        var originalCurrencies = getManagerCurrencies(manager);

        try {
            var zeroCapCurrency = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "online_generation_zero_cap"));
            var definition = new CurrencyDefinition(zeroCapCurrency, "online.generation.zero.cap",
                    ResourceLocation.withDefaultNamespace("stone"), 1.0, true,
                    new CurrencyDefinition.CurrencyCaps(0L, null));

            setManagerCurrencies(manager, Map.of(zeroCapCurrency, definition));

            assertThat(clampOnlineGenerationCap(zeroCapCurrency, 1_000_000L)).isZero();
        } finally {
            setManagerCurrencies(manager, originalCurrencies);
        }
    }

    @Test
    void balanceCapClampsFinalStoredAmount() throws Exception {
        var manager = getIdleCurrencyManagerInstance();
        var originalCurrencies = getManagerCurrencies(manager);

        try {
            var cappedCurrency = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "balance_cap"));
            var definition = new CurrencyDefinition(cappedCurrency, "balance.cap", ResourceLocation.withDefaultNamespace("stone"),
                    1.0, true, new CurrencyDefinition.CurrencyCaps(null, 250L));

            setManagerCurrencies(manager, Map.of(cappedCurrency, definition));

            assertThat(clampBalanceCap(cappedCurrency, 300L)).isEqualTo(250L);
        } finally {
            setManagerCurrencies(manager, originalCurrencies);
        }
    }

    @Test
    void balanceCapZeroBlocksStoredAmountEvenAfterLargeAddition() throws Exception {
        var manager = getIdleCurrencyManagerInstance();
        var originalCurrencies = getManagerCurrencies(manager);

        try {
            var zeroCapCurrency = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "balance_zero_cap"));
            var definition = new CurrencyDefinition(zeroCapCurrency, "balance.zero.cap",
                    ResourceLocation.withDefaultNamespace("stone"), 1.0, true,
                    new CurrencyDefinition.CurrencyCaps(null, 0L));

            setManagerCurrencies(manager, Map.of(zeroCapCurrency, definition));

            assertThat(clampBalanceCap(zeroCapCurrency, Long.MAX_VALUE)).isZero();
        } finally {
            setManagerCurrencies(manager, originalCurrencies);
        }
    }

    @Test
    void offlineGenerationHandlesVeryHighElapsedTimeDeterministically() {
        var generated = IdleGenerationTicker.computeOfflineGenerated(3L, Long.MAX_VALUE, 10L, 0.5, 1.0);

        assertThat(generated).isEqualTo(300L);
    }

    @Test
    void offlineGenerationSaturatesForLargeGeneratedValues() {
        var generated = IdleGenerationTicker.computeOfflineGenerated(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, 1.0,
                1.0);

        assertThat(generated).isEqualTo(Long.MAX_VALUE);
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

    private static long computeOfflineGeneratedForCurrency(CurrencyId currency, long generatedPerTick, long elapsedSeconds,
            long maxOfflineSeconds, double offlineBasePercent, double offlinePercentMultiplier) throws Exception {
        if (!invokeCurrenciesToGenerate().contains(currency)) {
            return 0L;
        }

        return IdleGenerationTicker.computeOfflineGenerated(generatedPerTick, elapsedSeconds, maxOfflineSeconds,
                offlineBasePercent, offlinePercentMultiplier);
    }

    private static long clampOnlineGenerationCap(CurrencyId currencyId, long generatedAmount) throws Exception {
        var method = IdleGenerationTicker.class.getDeclaredMethod("clampOnlineGenerationCap", CurrencyId.class, long.class);
        method.setAccessible(true);
        return (long) method.invoke(null, currencyId, generatedAmount);
    }

    private static long clampBalanceCap(CurrencyId currencyId, long balanceAfterAddition) throws Exception {
        var method = PlayerIdleDataManager.class.getDeclaredMethod("clampBalanceCap", CurrencyId.class, long.class);
        method.setAccessible(true);
        return (long) method.invoke(null, currencyId, balanceAfterAddition);
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
