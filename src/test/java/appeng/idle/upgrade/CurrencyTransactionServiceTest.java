package appeng.idle.upgrade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import appeng.idle.currency.CurrencyId;
import appeng.idle.player.PlayerIdleData;

class CurrencyTransactionServiceTest {
    private static final CurrencyId MATTER = currency("matter");
    private static final CurrencyId FLUIX = currency("fluix");

    @Test
    void canAffordOnlyWhenAllCurrenciesAreAvailable() {
        var data = dataWithBalances(100L, 25L);
        var affordable = new CostBundle(Map.of(MATTER, 30L, FLUIX, 25L));
        var notAffordable = new CostBundle(Map.of(MATTER, 101L));

        assertThat(CurrencyTransactionService.canAfford(data, affordable)).isTrue();
        assertThat(CurrencyTransactionService.canAfford(data, notAffordable)).isFalse();
    }

    @Test
    void trySpendIsAtomicAcrossCurrencies() {
        var data = dataWithBalances(50L, 10L);
        var impossibleBundle = new CostBundle(Map.of(MATTER, 20L, FLUIX, 11L));

        var spent = CurrencyTransactionService.trySpend(data, impossibleBundle, SpendReason.MANUAL_TEST);

        assertThat(spent).isFalse();
        assertThat(data.getBalance(MATTER)).isEqualTo(50L);
        assertThat(data.getBalance(FLUIX)).isEqualTo(10L);
    }

    @Test
    void successfulSpendAndRefundMutateAllBalances() {
        var data = dataWithBalances(50L, 10L);
        var bundle = new CostBundle(Map.of(MATTER, 20L, FLUIX, 5L));

        var spent = CurrencyTransactionService.trySpend(data, bundle, SpendReason.UPGRADE_PURCHASE);

        assertThat(spent).isTrue();
        assertThat(data.getBalance(MATTER)).isEqualTo(30L);
        assertThat(data.getBalance(FLUIX)).isEqualTo(5L);

        CurrencyTransactionService.refund(data, bundle, SpendReason.ADMIN_ADJUST);

        assertThat(data.getBalance(MATTER)).isEqualTo(50L);
        assertThat(data.getBalance(FLUIX)).isEqualTo(10L);
    }

    private static CurrencyId currency(String path) {
        return new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", path));
    }

    private static PlayerIdleData dataWithBalances(long matterAmount, long fluixAmount) {
        return new PlayerIdleData(
                Map.of(MATTER, matterAmount, FLUIX, fluixAmount),
                0L,
                PlayerIdleData.CURRENT_DATA_VERSION,
                Map.of());
    }
}
