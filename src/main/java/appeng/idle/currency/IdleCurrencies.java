package appeng.idle.currency;

import java.util.List;

import appeng.core.AppEng;

/**
 * Built-in currencies that are always available before datapack overrides are applied.
 */
public final class IdleCurrencies {
    public static final CurrencyId IDLE = new CurrencyId(AppEng.makeId("idle"));

    private static final List<CurrencyDefinition> DEFAULTS = List.of(
            new CurrencyDefinition(
                    IDLE,
                    "gui.ae2.idle.currency.idle",
                    AppEng.makeId("certus_quartz_crystal"),
                    1.0,
                    true,
                    null));

    public static List<CurrencyDefinition> defaults() {
        return DEFAULTS;
    }

    private IdleCurrencies() {
    }
}
