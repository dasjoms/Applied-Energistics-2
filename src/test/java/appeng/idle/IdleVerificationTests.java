package appeng.idle;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import appeng.idle.currency.CurrencyId;
import appeng.idle.currency.IdleCurrencies;
import appeng.idle.player.GenerationContext;
import appeng.idle.player.PlayerIdleData;
import appeng.idle.tick.BasicGenerationRule;
import appeng.idle.tick.IdleGenerationTicker;
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
    void onlineGenerationMathUsesBaseRateAndMultiplier() {
        var rule = new BasicGenerationRule();
        var context = new GenerationContext(UUID.randomUUID(), true, new MultiplierBundle(2.75));

        var generatedPerTick = rule.generatePerTick(context, IDLE);

        assertThat(generatedPerTick.units()).isEqualTo(2L);
    }

    @Test
    void offlineCatchupMathAppliesCap() {
        var generated = computeOfflineGenerated(2L, 120L, 30L, 0.5, 1.0);

        assertThat(generated).isEqualTo(600L);
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
    void upgradeMultiplierApplicationAffectsOfflineCatchup() {
        var withUpgrade = new PlayerIdleData(
                Map.of(),
                0L,
                PlayerIdleData.CURRENT_DATA_VERSION,
                Map.of(IdleUpgrades.OFFLINE_EFFICIENCY_1.id(), 3));

        var multiplier = IdleUpgradeHooks.getOfflinePercentMultiplier(withUpgrade);
        var generated = computeOfflineGenerated(1L, 10L, 100L, 0.25, multiplier);

        assertThat(multiplier).isEqualTo(1.3);
        assertThat(generated).isEqualTo(65L);
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

    private static long computeOfflineGenerated(long generatedPerTick, long elapsedSeconds, long maxOfflineSeconds,
            double offlineBasePercent, double offlinePercentMultiplier) {
        try {
            var method = IdleGenerationTicker.class.getDeclaredMethod(
                    "computeOfflineGenerated",
                    long.class,
                    long.class,
                    long.class,
                    double.class,
                    double.class);
            method.setAccessible(true);
            return (long) method.invoke(
                    null,
                    generatedPerTick,
                    elapsedSeconds,
                    maxOfflineSeconds,
                    offlineBasePercent,
                    offlinePercentMultiplier);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError("Unable to invoke offline catch-up calculator", e);
        }
    }

}
