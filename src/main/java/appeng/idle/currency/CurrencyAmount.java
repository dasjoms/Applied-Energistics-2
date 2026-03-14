package appeng.idle.currency;

/**
 * Fixed-point currency amount stored as integer units.
 */
public record CurrencyAmount(long units) implements Comparable<CurrencyAmount> {
    public static final CurrencyAmount ZERO = new CurrencyAmount(0);

    public CurrencyAmount plus(CurrencyAmount other) {
        return new CurrencyAmount(Math.addExact(units, other.units));
    }

    public CurrencyAmount minus(CurrencyAmount other) {
        return new CurrencyAmount(Math.subtractExact(units, other.units));
    }

    public boolean isNegative() {
        return units < 0;
    }

    @Override
    public int compareTo(CurrencyAmount other) {
        return Long.compare(units, other.units);
    }
}
