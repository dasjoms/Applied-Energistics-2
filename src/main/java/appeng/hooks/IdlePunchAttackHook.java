package appeng.hooks;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.idle.combat.IdleCombatClientState;
import appeng.client.idle.combat.IdlePunchAnimationComponent;
import appeng.core.network.serverbound.IdlePunchRequestPacket;
import appeng.idle.net.IdleCurrencyClientCache;

/**
 * Intercepts attack input for idle unarmed punching and redirects it to a server-authoritative packet.
 */
@OnlyIn(Dist.CLIENT)
public final class IdlePunchAttackHook {
    private static final double TARGET_PICK_RANGE = 5.0D;
    private static long pendingStartAttackSuppressionTick = Long.MIN_VALUE;
    private static boolean pendingStartAttackSuppression;

    private IdlePunchAttackHook() {
    }

    public static void install() {
        NeoForge.EVENT_BUS.addListener(IdlePunchAttackHook::onInteractionKeyMappingTriggered);
    }

    private static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack()) {
            clearPendingStartAttackSuppression();
        }

        var hand = getIdlePunchHand(event);
        if (hand == null) {
            return;
        }

        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || !shouldTakeOverAttackInput(player)) {
            resetClientCooldowns();
            return;
        }

        var hitResult = minecraft.hitResult;
        if (hitResult instanceof EntityHitResult entityHitResult) {
            if (!shouldSuppressVanillaAttackSwing(player, entityHitResult)) {
                return;
            }

            event.setCanceled(true);
            markStartAttackSuppressionForTakeoverClick(player, event.isAttack());
            if (isHandCoolingDown(player, hand)) {
                return;
            }

            IdlePunchAnimationComponent.startPredictedSwing(player, hand);
            var target = entityHitResult.getEntity();
            PacketDistributor.sendToServer(new IdlePunchRequestPacket(target.getId(), hand));
            return;
        }

        if (hitResult instanceof BlockHitResult blockHitResult && blockHitResult.getType() == HitResult.Type.BLOCK) {
            triggerLocalSwingForNonEntityInteraction(player, hand);
            return;
        }

        if (shouldSuppressTakeoverMissAttack(player, event.isAttack(), hitResult)) {
            event.setCanceled(true);
            markStartAttackSuppressionForTakeoverClick(player, true);
        }
        triggerLocalSwingForNonEntityInteraction(player, hand);
    }

    private static void triggerLocalSwingForNonEntityInteraction(Player player, InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND && player.swinging && player.swingingArm == InteractionHand.MAIN_HAND
                && player.swingTime > 0) {
            return;
        }

        IdlePunchAnimationComponent.startPredictedSwing(player, hand);
    }

    public static boolean consumeStartAttackSuppressionForCurrentClick(Player player) {
        var shouldSuppress = pendingStartAttackSuppression
                && pendingStartAttackSuppressionTick == player.level().getGameTime();
        clearPendingStartAttackSuppression();
        return shouldSuppress;
    }

    public static boolean shouldSuppressVanillaAttackSwing(Player player, @Nullable HitResult hitResult) {
        if (!shouldTakeOverAttackInput(player) || !(hitResult instanceof EntityHitResult entityHitResult)) {
            return false;
        }

        var target = entityHitResult.getEntity();
        return target.isAlive() && player.distanceToSqr(target) <= TARGET_PICK_RANGE * TARGET_PICK_RANGE;
    }

    static boolean shouldSuppressTakeoverMissAttack(Player player, boolean attackInput, @Nullable HitResult hitResult) {
        if (!attackInput || !shouldTakeOverAttackInput(player)) {
            return false;
        }

        return hitResult == null || hitResult.getType() == HitResult.Type.MISS;
    }

    static boolean isHandCoolingDown(Player player, InteractionHand hand) {
        var state = IdleCurrencyClientCache.getCombatHudState();
        var remainingAtSnapshot = hand == InteractionHand.MAIN_HAND
                ? state.mainRemainingTicks()
                : state.offRemainingTicks();
        var elapsedSinceSnapshot = Math.max(0L, player.level().getGameTime() - state.gameTime());
        return Math.max(0L, remainingAtSnapshot - elapsedSinceSnapshot) > 0L;
    }

    static void resetClientCooldowns() {
        clearPendingStartAttackSuppression();
    }

    static void markStartAttackSuppressionForCurrentClick(Player player) {
        pendingStartAttackSuppression = true;
        pendingStartAttackSuppressionTick = player.level().getGameTime();
    }

    static void markStartAttackSuppressionForTakeoverClick(Player player, boolean attackInput) {
        if (attackInput) {
            markStartAttackSuppressionForCurrentClick(player);
        }
    }

    private static void clearPendingStartAttackSuppression() {
        pendingStartAttackSuppression = false;
        pendingStartAttackSuppressionTick = Long.MIN_VALUE;
    }

    private static InteractionHand getIdlePunchHand(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack()) {
            return InteractionHand.OFF_HAND;
        }

        if (event.isUseItem()) {
            return InteractionHand.MAIN_HAND;
        }

        return null;
    }

    static boolean shouldTakeOverAttackInput(Player player) {
        return IdleCombatClientState.isIdleCombatModeActive();
    }
}
