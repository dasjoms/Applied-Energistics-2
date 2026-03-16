package appeng.idle.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import appeng.idle.player.PlayerIdleData;
import appeng.idle.player.PlayerIdleDataManager;
import appeng.idle.upgrade.IdleUpgradeHooks;
import appeng.idle.upgrade.IdleUpgrades;

class IdleCombatHandlerTest {

    @Test
    void requiresVisorEquippedAndUnlockPolicyEligibility() {
        var fixture = combatFixture();

        try (MockedStatic<PlayerIdleDataManager> dataManager = Mockito.mockStatic(PlayerIdleDataManager.class);
                MockedStatic<IdleUpgradeHooks> upgradeHooks = Mockito.mockStatic(IdleUpgradeHooks.class)) {
            dataManager.when(() -> PlayerIdleDataManager.isActiveRewardEligibleNow(fixture.player())).thenReturn(false);
            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId());

            verify(fixture.target(), never()).hurt(any(), anyFloat());

            dataManager.when(() -> PlayerIdleDataManager.isActiveRewardEligibleNow(fixture.player())).thenReturn(true);
            dataManager.when(() -> PlayerIdleDataManager.get(fixture.player())).thenReturn(new PlayerIdleData());
            upgradeHooks.when(() -> IdleUpgradeHooks.hasCombatUpgrade(any(PlayerIdleData.class))).thenReturn(true);
            upgradeHooks.when(() -> IdleUpgradeHooks.isUnarmedDualPunchEnabled(any(PlayerIdleData.class)))
                    .thenReturn(true);
            upgradeHooks.when(() -> IdleUpgradeHooks.getUnarmedPunchIntervalTicks(any(PlayerIdleData.class), eq(10L)))
                    .thenReturn(5L);

            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId());

            verify(fixture.target(), times(1)).hurt(any(), anyFloat());
        }
    }

    @Test
    void requiresBothHandsEmpty() {
        var fixture = combatFixture();

        try (MockedStatic<PlayerIdleDataManager> dataManager = Mockito.mockStatic(PlayerIdleDataManager.class);
                MockedStatic<IdleUpgradeHooks> upgradeHooks = Mockito.mockStatic(IdleUpgradeHooks.class)) {
            dataManager.when(() -> PlayerIdleDataManager.isActiveRewardEligibleNow(fixture.player())).thenReturn(true);
            dataManager.when(() -> PlayerIdleDataManager.get(fixture.player())).thenReturn(new PlayerIdleData());
            upgradeHooks.when(() -> IdleUpgradeHooks.hasCombatUpgrade(any(PlayerIdleData.class))).thenReturn(true);
            upgradeHooks.when(() -> IdleUpgradeHooks.isUnarmedDualPunchEnabled(any(PlayerIdleData.class)))
                    .thenReturn(true);
            upgradeHooks.when(() -> IdleUpgradeHooks.getUnarmedPunchIntervalTicks(any(PlayerIdleData.class), eq(10L)))
                    .thenReturn(5L);

            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId());

            when(fixture.player().getMainHandItem()).thenReturn(new ItemStack(Items.STICK));
            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId());

            when(fixture.player().getMainHandItem()).thenReturn(ItemStack.EMPTY);
            when(fixture.player().getOffhandItem()).thenReturn(new ItemStack(Items.STICK));
            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId());

            verify(fixture.target(), times(1)).hurt(any(), anyFloat());
        }
    }

    @Test
    void requiresOwnedCombatUpgrade() {
        var fixture = combatFixture();

        try (MockedStatic<PlayerIdleDataManager> dataManager = Mockito.mockStatic(PlayerIdleDataManager.class);
                MockedStatic<IdleUpgradeHooks> upgradeHooks = Mockito.mockStatic(IdleUpgradeHooks.class)) {
            dataManager.when(() -> PlayerIdleDataManager.isActiveRewardEligibleNow(fixture.player())).thenReturn(true);
            dataManager.when(() -> PlayerIdleDataManager.get(fixture.player())).thenReturn(new PlayerIdleData());
            upgradeHooks.when(() -> IdleUpgradeHooks.hasCombatUpgrade(any(PlayerIdleData.class))).thenReturn(false);
            upgradeHooks.when(() -> IdleUpgradeHooks.isUnarmedDualPunchEnabled(any(PlayerIdleData.class)))
                    .thenReturn(true);

            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId());

            verify(fixture.target(), never()).hurt(any(), anyFloat());
        }
    }

    @Test
    void alternatesHandsMainOffMainAndBlocksBeforeCooldownExpires() {
        var fixture = combatFixture();
        when(fixture.level().getGameTime()).thenReturn(100L, 102L, 105L, 110L);

        try (MockedStatic<PlayerIdleDataManager> dataManager = Mockito.mockStatic(PlayerIdleDataManager.class);
                MockedStatic<IdleUpgradeHooks> upgradeHooks = Mockito.mockStatic(IdleUpgradeHooks.class)) {
            dataManager.when(() -> PlayerIdleDataManager.isActiveRewardEligibleNow(fixture.player())).thenReturn(true);
            dataManager.when(() -> PlayerIdleDataManager.get(fixture.player())).thenReturn(new PlayerIdleData());
            upgradeHooks.when(() -> IdleUpgradeHooks.hasCombatUpgrade(any(PlayerIdleData.class))).thenReturn(true);
            upgradeHooks.when(() -> IdleUpgradeHooks.isUnarmedDualPunchEnabled(any(PlayerIdleData.class)))
                    .thenReturn(true);
            upgradeHooks.when(() -> IdleUpgradeHooks.getUnarmedPunchIntervalTicks(any(PlayerIdleData.class), eq(10L)))
                    .thenReturn(5L);

            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId());
            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId());
            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId());
            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId());

            verify(fixture.target(), times(3)).hurt(any(), anyFloat());
            verify(fixture.player(), times(2)).swing(InteractionHand.MAIN_HAND, true);
            verify(fixture.player()).swing(InteractionHand.OFF_HAND, true);
        }
    }

    @Test
    void usesConfiguredMultiplierCooldownShorterThanVanillaBaseline() {
        var data = new PlayerIdleData(Map.of(), 0L, PlayerIdleData.CURRENT_DATA_VERSION,
                Map.of(IdleUpgrades.COMBAT_1.id(), 1));

        var interval = IdleUpgradeHooks.getUnarmedPunchIntervalTicks(data, 10L);

        assertThat(interval).isLessThan(10L).isEqualTo(9L);
    }

    @Test
    void fallbackWhenFeatureDisabledDoesNotCancelNormalAttack() {
        var fixture = combatFixture();
        var attackEvent = mock(AttackEntityEvent.class);
        when(attackEvent.getEntity()).thenReturn(fixture.player());
        when(attackEvent.getTarget()).thenReturn(fixture.target());

        try (MockedStatic<PlayerIdleDataManager> dataManager = Mockito.mockStatic(PlayerIdleDataManager.class)) {
            dataManager.when(() -> PlayerIdleDataManager.isActiveRewardEligibleNow(fixture.player())).thenReturn(false);

            IdleCombatHandler.onAttackEntity(attackEvent);

            verify(attackEvent, never()).setCanceled(true);
            verify(fixture.target(), never()).hurt(any(), anyFloat());
        }
    }

    private static CombatFixture combatFixture() {
        var level = mock(ServerLevel.class);
        var player = mock(ServerPlayer.class);
        var target = mock(LivingEntity.class);
        var damageSources = mock(DamageSources.class);
        var damageSource = mock(DamageSource.class);
        var uuid = UUID.randomUUID();
        var targetEntityId = 1234;

        when(player.level()).thenReturn(level);
        when(player.serverLevel()).thenReturn(level);
        when(level.isClientSide()).thenReturn(false);
        when(player.getUUID()).thenReturn(uuid);
        when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        when(player.getAttributeValue(Attributes.ATTACK_DAMAGE)).thenReturn(4.0);
        when(player.distanceToSqr((Entity) target)).thenReturn(4.0);
        when(player.hasLineOfSight(target)).thenReturn(true);
        when(player.canAttack(target)).thenReturn(true);

        when(target.isAlive()).thenReturn(true);
        when(target.level()).thenReturn((Level) level);
        when(target.hurt(any(), anyFloat())).thenReturn(true);

        when(level.getEntity(targetEntityId)).thenReturn(target);
        when(level.damageSources()).thenReturn(damageSources);
        when(damageSources.playerAttack(player)).thenReturn(damageSource);

        return new CombatFixture(level, player, target, targetEntityId);
    }

    private record CombatFixture(ServerLevel level, ServerPlayer player, LivingEntity target, int targetEntityId) {
    }
}
