package appeng.idle.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import appeng.idle.player.PlayerIdleDataManager;
import appeng.idle.upgrade.IdleUpgradeHooks;

public final class IdleCombatHandler {
    private static final double ATTACK_COOLDOWN_TICKS = 20.0D;
    private static final double TARGET_PICK_RANGE = 5.0D;
    private static final double TARGET_PICK_RANGE_SQUARED = TARGET_PICK_RANGE * TARGET_PICK_RANGE;
    private static final Map<UUID, CombatState> PLAYER_COMBAT_STATES = new HashMap<>();

    private IdleCombatHandler() {
    }

    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.level().isClientSide()) {
            return;
        }

        var target = resolveTarget(player, event.getTarget());
        if (target == null) {
            return;
        }

        if (tryPerformUnarmedPunch(player, target)) {
            event.setCanceled(true);
        }
    }

    public static void handlePunchRequest(ServerPlayer player, int targetEntityId) {
        var target = player.serverLevel().getEntity(targetEntityId);
        if (target == null) {
            return;
        }

        tryPerformUnarmedPunch(player, target);
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYER_COMBAT_STATES.remove(event.getEntity().getUUID());
    }

    private static boolean tryPerformUnarmedPunch(ServerPlayer player, Entity target) {
        if (!PlayerIdleDataManager.isActiveRewardEligibleNow(player)) {
            return false;
        }

        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return false;
        }

        var idleData = PlayerIdleDataManager.get(player);
        if (!IdleUpgradeHooks.hasCombatUpgrade(idleData) || !IdleUpgradeHooks.isUnarmedDualPunchEnabled(idleData)) {
            return false;
        }

        if (!(target instanceof LivingEntity livingTarget)
                || !target.isAlive()
                || target == player
                || target.level() != player.level()
                || player.distanceToSqr(target) > TARGET_PICK_RANGE_SQUARED
                || !player.hasLineOfSight(target)
                || !player.canAttack(livingTarget)) {
            return false;
        }

        var gameTime = player.serverLevel().getGameTime();
        var state = PLAYER_COMBAT_STATES.computeIfAbsent(player.getUUID(), id -> CombatState.initial());
        if (gameTime < state.nextAllowedTick()) {
            return false;
        }

        var attackDamage = Math.max(1.0F, (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        if (!livingTarget.hurt(player.serverLevel().damageSources().playerAttack(player), attackDamage)) {
            return false;
        }

        player.swing(state.nextHand(), true);

        var baseIntervalTicks = getBaseUnarmedPunchIntervalTicks(player);
        var intervalTicks = IdleUpgradeHooks.getUnarmedPunchIntervalTicks(idleData, baseIntervalTicks);
        PLAYER_COMBAT_STATES.put(player.getUUID(), state.advance(gameTime + intervalTicks));
        return true;
    }

    private static long getBaseUnarmedPunchIntervalTicks(ServerPlayer player) {
        var attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        if (attackSpeed <= 0.0D || !Double.isFinite(attackSpeed)) {
            return 1L;
        }

        // Vanilla attack strength recovers over a period of 20 / attackSpeed ticks.
        var cooldownPeriodTicks = Math.round(ATTACK_COOLDOWN_TICKS / attackSpeed);
        return Math.max(1L, cooldownPeriodTicks);
    }

    private static Entity resolveTarget(ServerPlayer player, Entity eventTarget) {
        if (eventTarget != null && eventTarget.isAlive()) {
            return eventTarget;
        }

        var hitResult = player.pick(TARGET_PICK_RANGE, 0.0F, false);
        if (hitResult instanceof EntityHitResult entityHitResult && entityHitResult.getEntity().isAlive()) {
            return entityHitResult.getEntity();
        }

        return null;
    }

    private record CombatState(InteractionHand nextHand, long nextAllowedTick) {
        private static CombatState initial() {
            return new CombatState(InteractionHand.MAIN_HAND, 0);
        }

        private CombatState advance(long nextAllowedTick) {
            var next = nextHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            return new CombatState(next, nextAllowedTick);
        }
    }
}
