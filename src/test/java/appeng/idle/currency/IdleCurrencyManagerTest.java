package appeng.idle.currency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdleCurrencyManagerTest {
    @Test
    void builtInIdleCurrencyIsAvailable() {
        var currencies = IdleCurrencyManager.getCurrencies();

        assertThat(currencies).containsKey(IdleCurrencies.IDLE);
        assertThat(currencies.get(IdleCurrencies.IDLE).baseTicksPerUnit()).isEqualTo(200L);
    }

    @Test
    void currenciesMapIsImmutableSnapshot() {
        var currencies = IdleCurrencyManager.getCurrencies();

        assertThat(currencies).isUnmodifiable();
    }
}
