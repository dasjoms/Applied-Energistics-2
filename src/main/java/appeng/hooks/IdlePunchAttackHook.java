package appeng.hooks;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.idle.combat.IdlePunchAnimationComponent;
import appeng.core.definitions.AEItems;
import appeng.core.network.serverbound.IdlePunchRequestPacket;
import appeng.idle.net.IdleCurrencyClientCache;

/**
 * Intercepts attack input for idle unarmed punching and redirects it to a server-authoritative packet.
 */
@OnlyIn(Dist.CLIENT)
public final class IdlePunchAttackHook {
    private static final double TARGET_PICK_RANGE = 5.0D;
    private static final double ATTACK_COOLDOWN_TICKS = 20.0D;
    private static long nextAllowedMainTick;
    private static long nextAllowedOffTick;

    private IdlePunchAttackHook() {
    }

    public static void install() {
        NeoForge.EVENT_BUS.addListener(IdlePunchAttackHook::onInteractionKeyMappingTriggered);
    }

    private static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
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

        if (!shouldSuppressVanillaAttackSwing(player, minecraft.hitResult)) {
            return;
        }

        event.setCanceled(true);
        if (isHandCoolingDown(player, hand)) {
            return;
        }

        markClientPunchStarted(player, hand);
        IdlePunchAnimationComponent.startPredictedSwing(player, hand);
        var target = ((EntityHitResult) minecraft.hitResult).getEntity();
        PacketDistributor.sendToServer(new IdlePunchRequestPacket(target.getId(), hand));
    }

    public static boolean shouldSuppressVanillaAttackSwing(Player player, @Nullable HitResult hitResult) {
        if (!shouldTakeOverAttackInput(player) || !(hitResult instanceof EntityHitResult entityHitResult)) {
            return false;
        }

        var target = entityHitResult.getEntity();
        return target.isAlive() && player.distanceToSqr(target) <= TARGET_PICK_RANGE * TARGET_PICK_RANGE;
    }

    static boolean isHandCoolingDown(Player player, InteractionHand hand) {
        return player.level().getGameTime() < nextAllowedTick(hand);
    }

    static void markClientPunchStarted(Player player, InteractionHand hand) {
        var gameTime = player.level().getGameTime();
        setNextAllowedTick(hand, gameTime + getBaseUnarmedPunchIntervalTicks(player));
    }

    static void resetClientCooldowns() {
        nextAllowedMainTick = 0L;
        nextAllowedOffTick = 0L;
    }

    private static long getBaseUnarmedPunchIntervalTicks(Player player) {
        var attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        if (attackSpeed <= 0.0D || !Double.isFinite(attackSpeed)) {
            return 1L;
        }

        var cooldownPeriodTicks = Math.round(ATTACK_COOLDOWN_TICKS / attackSpeed);
        return Math.max(1L, cooldownPeriodTicks * 2L);
    }

    private static long nextAllowedTick(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? nextAllowedMainTick : nextAllowedOffTick;
    }

    private static void setNextAllowedTick(InteractionHand hand, long nextAllowedTick) {
        if (hand == InteractionHand.MAIN_HAND) {
            nextAllowedMainTick = nextAllowedTick;
        } else {
            nextAllowedOffTick = nextAllowedTick;
        }
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
        return AEItems.IDLE_VISOR.is(player.getItemBySlot(EquipmentSlot.HEAD))
                && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().isEmpty()
                && IdleCurrencyClientCache.isIdlePunchEligible();
    }
}
