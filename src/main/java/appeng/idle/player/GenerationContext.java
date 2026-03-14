package appeng.idle.player;

import java.util.Objects;
import java.util.UUID;

import appeng.idle.upgrade.MultiplierBundle;

/**
 * Generation input data for a player in a given tick.
 */
public record GenerationContext(UUID playerId, boolean online, MultiplierBundle multipliers) {
    public GenerationContext {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(multipliers, "multipliers");
    }
}
