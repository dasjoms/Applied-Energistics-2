package appeng.client.idle.combat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

import appeng.core.definitions.AEItems;
import appeng.idle.net.IdleCurrencyClientCache;

/**
 * Client-only idle combat animation state for unarmed idle punching.
 */
public final class IdlePunchAnimationComponent {
    private static InteractionHand activeHand = InteractionHand.MAIN_HAND;
    private static long swingStartTick = Long.MIN_VALUE;
    private static int swingDurationTicks;

    private IdlePunchAnimationComponent() {
    }

    public static void update(Player player) {
        if (!isIdlePunchMode(player)) {
            reset();
            return;
        }

        if (player.swinging) {
            var swingingHand = player.swingingArm == null ? InteractionHand.MAIN_HAND : player.swingingArm;
            if (!isAnimationActive(player) || player.swingTime == 0 || swingingHand != activeHand) {
                activeHand = swingingHand;
                swingStartTick = player.level().getGameTime();
                swingDurationTicks = getSwingDurationTicks(player);
            }
            return;
        }

        if (isAnimationActive(player) && isAnimationFinished(player.level().getGameTime())) {
            reset();
        }
    }

    public static boolean shouldRenderForHand(Player player, InteractionHand hand) {
        return isIdlePunchMode(player) && isAnimationActive(player) && activeHand == hand;
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

    private static boolean isIdlePunchMode(Player player) {
        return AEItems.IDLE_VISOR.is(player.getItemBySlot(EquipmentSlot.HEAD))
                && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().isEmpty()
                && IdleCurrencyClientCache.isIdlePunchEligible();
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
        swingStartTick = Long.MIN_VALUE;
        swingDurationTicks = 0;
    }
}
