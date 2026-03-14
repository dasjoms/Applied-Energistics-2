package appeng.idle.upgrade;

import appeng.idle.currency.CurrencyId;

/**
 * Effects provided by an owned idle upgrade level.
 */
public interface UpgradeEffects {
    UpgradeEffects NO_OP = new UpgradeEffects() {
    };

    default double onlineGenerationMultiplier(CurrencyId currency) {
        return 1.0;
    }

    default double offlinePercentBonus() {
        return 0.0;
    }
}
