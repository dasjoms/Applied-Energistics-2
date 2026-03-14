package appeng.idle.net;

/**
 * Immutable per-currency HUD state.
 */
public record IdleCurrencyHudValue(long balance, double gainPerSecond, long progressTicks, long ticksPerUnit,
        Long secondsToNext) {
}
