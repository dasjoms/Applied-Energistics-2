package appeng.idle.reward;

import java.util.Locale;

import com.google.gson.JsonParseException;

/**
 * Trigger categories that can award idle progress.
 */
public enum RewardTriggerType {
    BLOCK_BREAK;

    public static RewardTriggerType fromJson(String rawValue) {
        try {
            return RewardTriggerType.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new JsonParseException("Unknown reward trigger type: " + rawValue);
        }
    }
}
