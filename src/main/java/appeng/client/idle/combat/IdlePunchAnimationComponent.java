package appeng.client.idle.combat;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import appeng.core.AEConfig;
import appeng.idle.net.IdleCurrencyClientCache;

/**
 * Client-only idle combat animation state for unarmed idle punching.
 */
public final class IdlePunchAnimationComponent {
    private enum SwingSource {
        NONE,
        PREDICTED,
        SERVER_CONFIRMED
    }

    private static InteractionHand activeHand = InteractionHand.MAIN_HAND;
    private static InteractionHand pendingPredictedHand;
    private static long swingStartTick = Long.MIN_VALUE;
    private static int swingDurationTicks;
    private static long lastConfirmedSequence = Long.MIN_VALUE;
    private static SwingSource activeSwingSource = SwingSource.NONE;

    private IdlePunchAnimationComponent() {
    }

    public static void update(Player player) {
        if (!isIdlePunchMode(player)) {
            reset();
            return;
        }

        if (isAnimationActive(player) && isAnimationFinished(player.level().getGameTime())) {
            reset();
        }
    }

    public static void applyServerConfirmedSwing(Player player, InteractionHand hand, long sequence) {
        if (sequence < lastConfirmedSequence) {
            return;
        }

        lastConfirmedSequence = sequence;
        var predictedHand = pendingPredictedHand;
        pendingPredictedHand = null;

        if (predictedHand == hand && isAnimationActive(player) && activeHand == hand) {
            activeSwingSource = SwingSource.SERVER_CONFIRMED;
            sendDebugAnimationMessage(player, hand, "server_confirmed_reuse");
            return;
        }

        activeHand = hand;
        swingStartTick = player.level().getGameTime();
        swingDurationTicks = getSwingDurationTicks(player);
        activeSwingSource = SwingSource.SERVER_CONFIRMED;
        sendDebugAnimationMessage(player, hand, "server_confirmed_new");
    }

    public static void startPredictedSwing(Player player, InteractionHand hand) {
        pendingPredictedHand = hand;
        activeHand = hand;
        swingStartTick = player.level().getGameTime();
        swingDurationTicks = getSwingDurationTicks(player);
        activeSwingSource = SwingSource.PREDICTED;
        sendDebugAnimationMessage(player, hand, "predicted_start");
    }

    private static void sendDebugAnimationMessage(Player player, InteractionHand hand, String phase) {
        if (!AEConfig.instance().isDebugToolsEnabled()) {
            return;
        }

        player.displayClientMessage(Component.literal("[AE2 Debug] idle punch animation: phase=" + phase
                + " hand=" + hand + " startTick=" + swingStartTick + " duration=" + swingDurationTicks), false);
    }

    public static void resetServerStateTracking() {
        reset();
    }

    public static boolean isIdlePunchMode(Player player) {
        return IdleCurrencyClientCache.getCombatHudState().inIdleCombatMode();
    }

    public static boolean isSwingActiveForHand(Player player, InteractionHand hand) {
        return isAnimationActive(player) && activeHand == hand;
    }

    public static boolean shouldRenderIdleHand(Player player, InteractionHand hand) {
        if (!isIdlePunchMode(player)) {
            return false;
        }

        return true;
    }

    public static InteractionHand getActiveHand() {
        return activeHand;
    }

    public static long getSwingStartTick() {
        return swingStartTick;
    }

    public static int getSwingDurationTicks() {
        return swingDurationTicks;
    }

    public static boolean isAnimationActive(Player player) {
        return swingStartTick != Long.MIN_VALUE && !isAnimationFinished(player.level().getGameTime());
    }

    private static boolean isAnimationFinished(long gameTime) {
        return swingDurationTicks <= 0 || gameTime - swingStartTick >= swingDurationTicks;
    }

    private static int getSwingDurationTicks(Player player) {
        var duration = 6;
        if (player.hasEffect(MobEffects.DIG_SPEED)) {
            var effect = player.getEffect(MobEffects.DIG_SPEED);
            if (effect != null) {
                duration -= 1 + effect.getAmplifier();
            }
        } else if (player.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            var effect = player.getEffect(MobEffects.DIG_SLOWDOWN);
            if (effect != null) {
                duration += (1 + effect.getAmplifier()) * 2;
            }
        }

        return Math.max(1, duration);
    }

    private static void reset() {
        activeHand = InteractionHand.MAIN_HAND;
        pendingPredictedHand = null;
        swingStartTick = Long.MIN_VALUE;
        swingDurationTicks = 0;
        lastConfirmedSequence = Long.MIN_VALUE;
        activeSwingSource = SwingSource.NONE;
    }
}
