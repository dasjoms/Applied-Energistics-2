package appeng.idle.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import appeng.idle.player.PlayerIdleDataManager;
import appeng.idle.upgrade.IdleUpgradeHooks;

public final class IdleCombatHandler {
    private static final long BASE_UNARMED_PUNCH_INTERVAL_TICKS = 10;
    private static final double TARGET_PICK_RANGE = 5.0D;
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

        if (!PlayerIdleDataManager.isActiveRewardEligibleNow(player)) {
            return;
        }

        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return;
        }

        var idleData = PlayerIdleDataManager.get(player);
        if (!IdleUpgradeHooks.hasCombatUpgrade(idleData) || !IdleUpgradeHooks.isUnarmedDualPunchEnabled(idleData)) {
            return;
        }

        var target = resolveTarget(player, event.getTarget());
        if (target == null) {
            return;
        }

        event.setCanceled(true);

        var gameTime = player.serverLevel().getGameTime();
        var state = PLAYER_COMBAT_STATES.computeIfAbsent(player.getUUID(), id -> CombatState.initial());
        if (gameTime < state.nextAllowedTick()) {
            return;
        }

        var attackDamage = Math.max(1.0F, (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        var damaged = target.hurt(player.serverLevel().damageSources().playerAttack(player), attackDamage);
        if (!damaged) {
            return;
        }

        player.swing(state.nextHand(), true);

        var intervalTicks = IdleUpgradeHooks.getUnarmedPunchIntervalTicks(idleData, BASE_UNARMED_PUNCH_INTERVAL_TICKS);
        PLAYER_COMBAT_STATES.put(player.getUUID(), state.advance(gameTime + intervalTicks));
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYER_COMBAT_STATES.remove(event.getEntity().getUUID());
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
