package appeng.idle.upgrade;

/**
 * Aggregated generation multiplier state for a player.
 */
public record MultiplierBundle(double totalMultiplier) {
    public static final MultiplierBundle IDENTITY = new MultiplierBundle(1.0);
}
