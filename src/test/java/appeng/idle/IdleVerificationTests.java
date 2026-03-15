package appeng.idle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import appeng.idle.currency.CurrencyDefinition;
import appeng.idle.currency.CurrencyId;
import appeng.idle.currency.IdleCurrencies;
import appeng.idle.currency.IdleCurrencyManager;
import appeng.idle.player.GenerationContext;
import appeng.idle.player.PlayerIdleData;
import appeng.idle.tick.BasicGenerationRule;
import appeng.idle.tick.IdleGenerationProgressService;
import appeng.idle.upgrade.CostBundle;
import appeng.idle.upgrade.CurrencyTransactionService;
import appeng.idle.upgrade.IdleUpgradeHooks;
import appeng.idle.upgrade.IdleUpgrades;
import appeng.idle.upgrade.MultiplierBundle;
import appeng.idle.upgrade.SpendReason;

class IdleVerificationTests {
    private static final CurrencyId IDLE = IdleCurrencies.IDLE;
    private static final CurrencyId MATTER = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "matter"));

    @Test
    void serializationRoundTripPreservesBalancesAndMetadata() {
        var data = new PlayerIdleData(
                Map.of(IDLE, 42L),
                1_720_000_000L,
                7,
                Map.of(IdleUpgrades.OFFLINE_EFFICIENCY_1.id(), 2));

        var restored = PlayerIdleData.fromTag(data.toTag());

        assertThat(restored.getBalance(IDLE)).isEqualTo(42L);
        assertThat(restored.getLastSeenEpochSeconds()).isEqualTo(1_720_000_000L);
        assertThat(restored.getDataVersion()).isEqualTo(7);
        assertThat(restored.ownedUpgradeLevelsView()).containsEntry(IdleUpgrades.OFFLINE_EFFICIENCY_1.id(), 2);
    }

    @Test
    void onlineGenerationMathUsesTicksPerUnitAndMultiplier() throws Exception {
        var rule = new BasicGenerationRule();
        var testCurrency = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "online_math_test"));
        var baseTicksPerUnit = 4L;
        var definition = new CurrencyDefinition(
                testCurrency,
                "gui.ae2.idle.currency.online_math_test",
                "gui.ae2.idle.currency.online_math_test",
                ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal"),
                baseTicksPerUnit,
                true,
                null);

        withInjectedCurrency(definition, () -> {
            var belowThreshold = new GenerationContext(UUID.randomUUID(), true,
                    new MultiplierBundle(baseTicksPerUnit - 0.01));
            var atThreshold = new GenerationContext(UUID.randomUUID(), true, new MultiplierBundle(baseTicksPerUnit));
            var aboveThresholdMultiplier = 10.9;
            var aboveThreshold = new GenerationContext(UUID.randomUUID(), true,
                    new MultiplierBundle(aboveThresholdMultiplier));

            assertThat(rule.generatePerTick(belowThreshold, testCurrency).units()).isEqualTo(0L);
            assertThat(rule.generatePerTick(atThreshold, testCurrency).units()).isEqualTo(1L);
            assertThat(rule.generatePerTick(aboveThreshold, testCurrency).units())
                    .isEqualTo((long) Math.floor(aboveThresholdMultiplier / baseTicksPerUnit));
        });
    }

    @Test
    void offlineCatchupMathAppliesCap() throws Exception {
        withInjectedTestCurrency(60L, testCurrency -> {
            var data = new PlayerIdleData();
            var generated = IdleGenerationProgressService.accrueOfflineProgress(
                    data,
                    120L,
                    30L,
                    0.5,
                    1.0,
                    java.util.Set.of(testCurrency));

            assertThat(generated).containsEntry(testCurrency, 5L);
        });
    }

    @Test
    void atomicSpendBehaviorDoesNotPartiallyMutateBalances() {
        var data = new PlayerIdleData(
                Map.of(IDLE, 50L, MATTER, 10L),
                0L,
                PlayerIdleData.CURRENT_DATA_VERSION,
                Map.of());
        var impossibleBundle = new CostBundle(Map.of(IDLE, 20L, MATTER, 11L));

        var spent = CurrencyTransactionService.trySpend(data, impossibleBundle, SpendReason.MANUAL_TEST);

        assertThat(spent).isFalse();
        assertThat(data.getBalance(IDLE)).isEqualTo(50L);
        assertThat(data.getBalance(MATTER)).isEqualTo(10L);
    }

    @Test
    void upgradeMultiplierApplicationAffectsOfflineCatchup() throws Exception {
        var withUpgrade = new PlayerIdleData(
                Map.of(),
                0L,
                PlayerIdleData.CURRENT_DATA_VERSION,
                Map.of(IdleUpgrades.OFFLINE_EFFICIENCY_1.id(), 3));

        withInjectedTestCurrency(40L, testCurrency -> {
            var multiplier = IdleUpgradeHooks.getOfflinePercentMultiplier(withUpgrade);
            var generated = IdleGenerationProgressService.accrueOfflineProgress(
                    withUpgrade,
                    10L,
                    100L,
                    0.25,
                    multiplier,
                    java.util.Set.of(testCurrency));

            assertThat(multiplier).isEqualTo(1.3);
            assertThat(generated).containsEntry(testCurrency, 1L);
            assertThat(withUpgrade.getGenerationProgressTicks(testCurrency)).isEqualTo(25L);
        });
    }

    @Test
    void upgradeLevelChangesScaleOfflineGainRateAsExpected() throws Exception {
        var levelOne = new PlayerIdleData(
                Map.of(),
                0L,
                PlayerIdleData.CURRENT_DATA_VERSION,
                Map.of(IdleUpgrades.OFFLINE_EFFICIENCY_1.id(), 1));
        var levelThree = new PlayerIdleData(
                Map.of(),
                0L,
                PlayerIdleData.CURRENT_DATA_VERSION,
                Map.of(IdleUpgrades.OFFLINE_EFFICIENCY_1.id(), 3));

        var multiplierOne = IdleUpgradeHooks.getOfflinePercentMultiplier(levelOne);
        var multiplierThree = IdleUpgradeHooks.getOfflinePercentMultiplier(levelThree);

        withInjectedTestCurrency(100L, testCurrency -> {
            var generatedOne = IdleGenerationProgressService.accrueOfflineProgress(
                    levelOne,
                    200L,
                    1000L,
                    0.5,
                    multiplierOne,
                    java.util.Set.of(testCurrency));
            var generatedThree = IdleGenerationProgressService.accrueOfflineProgress(
                    levelThree,
                    200L,
                    1000L,
                    0.5,
                    multiplierThree,
                    java.util.Set.of(testCurrency));

            assertThat(multiplierOne).isEqualTo(1.1);
            assertThat(multiplierThree).isEqualTo(1.3);
            assertThat(generatedThree.getOrDefault(testCurrency, 0L))
                    .isGreaterThan(generatedOne.getOrDefault(testCurrency, 0L));
        });
    }

    @Test
    void integrationStyleTwoPlayerLedgersMutateIndependently() {
        var playerOne = new PlayerIdleData(Map.of(IDLE, 100L), 0L, PlayerIdleData.CURRENT_DATA_VERSION, Map.of());
        var playerTwo = new PlayerIdleData(Map.of(IDLE, 25L), 0L, PlayerIdleData.CURRENT_DATA_VERSION, Map.of());

        var spent = CurrencyTransactionService.trySpend(
                playerOne,
                new CostBundle(Map.of(IDLE, 40L)),
                SpendReason.MANUAL_TEST);

        assertThat(spent).isTrue();
        assertThat(playerOne.getBalance(IDLE)).isEqualTo(60L);
        assertThat(playerTwo.getBalance(IDLE)).isEqualTo(25L);
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

    private static void withInjectedTestCurrency(long baseTicksPerUnit, ThrowingCurrencyAssertion assertion)
            throws Exception {
        var testCurrency = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "verification_math_test"));
        var definition = new CurrencyDefinition(
                testCurrency,
                "gui.ae2.idle.currency.verification_math_test",
                "gui.ae2.idle.currency.verification_math_test",
                ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal"),
                baseTicksPerUnit,
                true,
                null);

        withInjectedCurrency(definition, () -> assertion.run(testCurrency));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingCurrencyAssertion {
        void run(CurrencyId currencyId) throws Exception;
    }

}
