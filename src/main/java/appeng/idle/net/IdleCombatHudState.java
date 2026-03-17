package appeng.idle.net;

/**
 * Server-authoritative idle combat HUD state for client-side cooldown visualization.
 */
public record IdleCombatHudState(long gameTime,
        long mainRemainingTicks,
        long offRemainingTicks,
        long mainIntervalTicks,
        long offIntervalTicks,
        boolean inIdleCombatMode) {

    public static final IdleCombatHudState EMPTY = new IdleCombatHudState(0L, 0L, 0L, 0L, 0L, false);
}
