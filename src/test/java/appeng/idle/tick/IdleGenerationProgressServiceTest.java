package appeng.idle.tick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import appeng.idle.currency.CurrencyDefinition;
import appeng.idle.currency.CurrencyId;
import appeng.idle.currency.IdleCurrencyManager;
import appeng.idle.player.PlayerIdleData;
import appeng.idle.player.PlayerIdleDataManager;

class IdleGenerationProgressServiceTest {
    @Test
    void accruesProgressAndPersistsRemainderUntilWholeUnitIsReady() throws Exception {
        var currency = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "progress_test_currency"));
        var definition = new CurrencyDefinition(
                currency,
                "gui.ae2.idle.currency.progress_test_currency",
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
    void onlineAccrualAppliesOnlineGenerationCapBeforeAwardingUnits() throws Exception {
        var currency = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "online_cap_progress_currency"));
        var definition = new CurrencyDefinition(
                currency,
                "gui.ae2.idle.currency.online_cap_progress_currency",
                "gui.ae2.idle.currency.online_cap_progress_currency",
                ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal"),
                1,
                true,
                new CurrencyDefinition.CurrencyCaps(2L, null));

        withInjectedCurrency(definition, () -> {
            var data = new PlayerIdleData();

            var generated = IdleGenerationProgressService.accrueOnlineProgress(data, 3, java.util.Set.of(currency));
            assertThat(generated).containsEntry(currency, 2L);
        });
    }

    @Test
    void onlineAccrualUsesProgressTicksForFractionalGenerationInsteadOfPerTickFlooring() throws Exception {
        var currency = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "fractional_progress_currency"));
        var definition = new CurrencyDefinition(
                currency,
                "gui.ae2.idle.currency.fractional_progress_currency",
                "gui.ae2.idle.currency.fractional_progress_currency",
                ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal"),
                2,
                true,
                null);

        withInjectedCurrency(definition, () -> {
            var data = new PlayerIdleData();

            var generated = IdleGenerationProgressService.accrueOnlineProgress(data, 20, java.util.Set.of(currency));
            assertThat(generated).containsEntry(currency, 10L);
            assertThat(data.getGenerationProgressTicks(currency)).isZero();
        });
    }

    @Test
    void appliesGenerationAndBalanceCapsBeforeAwardingUnits() throws Exception {
        var currency = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "capped_progress_test_currency"));
        var definition = new CurrencyDefinition(
                currency,
                "gui.ae2.idle.currency.capped_progress_test_currency",
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

    @Test
    void activeAndPassiveProgressUseSameAccumulatorAndKeepRemainder() throws Exception {
        var currency = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "shared_progress_currency"));
        var definition = new CurrencyDefinition(
                currency,
                "gui.ae2.idle.currency.shared_progress_currency",
                "gui.ae2.idle.currency.shared_progress_currency",
                ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal"),
                10,
                true,
                null);

        withInjectedCurrency(definition, () -> {
            var player = mock(ServerPlayer.class);
            when(player.getUUID()).thenReturn(UUID.randomUUID());

            var data = new PlayerIdleData();
            IdleGenerationProgressService.accrueOnlineProgress(data, 6, java.util.Set.of(currency));
            assertThat(data.getGenerationProgressTicks(currency)).isEqualTo(6L);

            try (MockedStatic<PlayerIdleDataManager> manager = mockStatic(PlayerIdleDataManager.class)) {
                manager.when(() -> PlayerIdleDataManager.isActiveRewardEligibleNow(player)).thenReturn(true);
                manager.when(() -> PlayerIdleDataManager.get(player)).thenReturn(data);
                manager.when(() -> PlayerIdleDataManager.addGeneratedBalances(eq(player), any(), eq("ACTIVE_TEST")))
                        .thenReturn(true);

                var granted = IdleGenerationProgressService.grantActiveProgressTicks(player, currency, 9L,
                        "ACTIVE_TEST");

                assertThat(granted).isTrue();
                manager.verify(() -> PlayerIdleDataManager.addGeneratedBalances(
                        eq(player),
                        eq(Map.of(currency, 1L)),
                        eq("ACTIVE_TEST")));
                assertThat(data.getGenerationProgressTicks(currency)).isEqualTo(5L);
            }
        });
    }

    @Test
    void activeProgressDeniedWhenNotEligibleEvenIfPreviouslyUnlocked() throws Exception {
        var currency = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "active_denied_currency"));
        var definition = new CurrencyDefinition(
                currency,
                "gui.ae2.idle.currency.active_denied_currency",
                "gui.ae2.idle.currency.active_denied_currency",
                ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal"),
                10,
                true,
                null);

        withInjectedCurrency(definition, () -> {
            var player = mock(ServerPlayer.class);

            try (MockedStatic<PlayerIdleDataManager> manager = mockStatic(PlayerIdleDataManager.class)) {
                manager.when(() -> PlayerIdleDataManager.isActiveRewardEligibleNow(player)).thenReturn(false);

                var granted = IdleGenerationProgressService.grantActiveProgressTicks(player, currency, 10L,
                        "ACTIVE_DENIED");

                assertThat(granted).isFalse();
                manager.verify(() -> PlayerIdleDataManager.get(player), org.mockito.Mockito.never());
                manager.verify(() -> PlayerIdleDataManager.addGeneratedBalances(eq(player), any(), any()),
                        org.mockito.Mockito.never());
            }
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
