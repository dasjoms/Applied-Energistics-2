package appeng.client.idle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * Shared client-side visibility checks for the idle HUD and future idle-related toggles.
 */
public final class IdleHudVisibility {

    public static final VisibilityPolicy DEFAULT_POLICY = new VisibilityPolicy(true, true);

    private IdleHudVisibility() {
    }

    /**
     * Checks if the idle HUD should be visible using the default policy.
     */
    public static boolean shouldShow() {
        return shouldShow(DEFAULT_POLICY);
    }

    /**
     * Checks if the idle HUD should be visible based on optional policy toggles.
     */
    public static boolean shouldShow(VisibilityPolicy policy) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) {
            return false;
        }

        if (policy.hideWhileDebugScreenOpen() && minecraft.gui.getDebugOverlay().showDebugScreen()) {
            return false;
        }

        if (policy.hideWhileChatOpen() && minecraft.screen instanceof ChatScreen) {
            return false;
        }

        return true;
    }

    public record VisibilityPolicy(boolean hideWhileDebugScreenOpen, boolean hideWhileChatOpen) {
    }
}
