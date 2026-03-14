package appeng.idle.net;

/**
 * Immutable per-currency HUD state.
 */
public record IdleCurrencyHudValue(long balance, long gainPerSecond, long progressTicks, long ticksPerUnit,
        Long secondsToNext) {
}
