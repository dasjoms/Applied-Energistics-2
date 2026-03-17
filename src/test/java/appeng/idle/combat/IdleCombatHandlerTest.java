package appeng.idle.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import appeng.core.AEConfig;
import appeng.idle.player.PlayerIdleData;
import appeng.idle.player.PlayerIdleDataManager;
import appeng.idle.upgrade.IdleUpgradeHooks;

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
            upgradeHooks.when(() -> IdleUpgradeHooks.getUnarmedPunchIntervalTicks(any(PlayerIdleData.class), anyLong()))
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
            upgradeHooks.when(() -> IdleUpgradeHooks.getUnarmedPunchIntervalTicks(any(PlayerIdleData.class), anyLong()))
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
    void offHandCanAttackWhileMainHandCoolingAndViceVersa() {
        var fixture = combatFixture();
        when(fixture.level().getGameTime()).thenReturn(100L, 102L, 103L, 106L);

        try (MockedStatic<PlayerIdleDataManager> dataManager = Mockito.mockStatic(PlayerIdleDataManager.class);
                MockedStatic<IdleUpgradeHooks> upgradeHooks = Mockito.mockStatic(IdleUpgradeHooks.class)) {
            dataManager.when(() -> PlayerIdleDataManager.isActiveRewardEligibleNow(fixture.player())).thenReturn(true);
            dataManager.when(() -> PlayerIdleDataManager.get(fixture.player())).thenReturn(new PlayerIdleData());
            upgradeHooks.when(() -> IdleUpgradeHooks.hasCombatUpgrade(any(PlayerIdleData.class))).thenReturn(true);
            upgradeHooks.when(() -> IdleUpgradeHooks.isUnarmedDualPunchEnabled(any(PlayerIdleData.class)))
                    .thenReturn(true);
            upgradeHooks.when(() -> IdleUpgradeHooks.getUnarmedPunchIntervalTicks(any(PlayerIdleData.class), anyLong()))
                    .thenReturn(5L);

            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId(), InteractionHand.MAIN_HAND);
            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId(), InteractionHand.MAIN_HAND);
            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId(), InteractionHand.OFF_HAND);
            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId(), InteractionHand.MAIN_HAND);

            verify(fixture.target(), times(3)).hurt(any(), anyFloat());
            verify(fixture.player(), times(2)).swing(InteractionHand.MAIN_HAND, true);
            verify(fixture.player()).swing(InteractionHand.OFF_HAND, true);
        }
    }

    @Test
    void repeatedClicksOnSameHandRespectDoubledBaseCooldown() {
        var fixture = combatFixture();
        when(fixture.player().getAttributeValue(Attributes.ATTACK_SPEED)).thenReturn(4.0);
        when(fixture.level().getGameTime()).thenReturn(100L, 109L, 110L);

        try (MockedStatic<PlayerIdleDataManager> dataManager = Mockito.mockStatic(PlayerIdleDataManager.class);
                MockedStatic<IdleUpgradeHooks> upgradeHooks = Mockito.mockStatic(IdleUpgradeHooks.class)) {
            dataManager.when(() -> PlayerIdleDataManager.isActiveRewardEligibleNow(fixture.player())).thenReturn(true);
            dataManager.when(() -> PlayerIdleDataManager.get(fixture.player())).thenReturn(new PlayerIdleData());
            upgradeHooks.when(() -> IdleUpgradeHooks.hasCombatUpgrade(any(PlayerIdleData.class))).thenReturn(true);
            upgradeHooks.when(() -> IdleUpgradeHooks.isUnarmedDualPunchEnabled(any(PlayerIdleData.class)))
                    .thenReturn(true);
            upgradeHooks.when(() -> IdleUpgradeHooks.getUnarmedPunchIntervalTicks(any(PlayerIdleData.class), anyLong()))
                    .thenAnswer(invocation -> invocation.getArgument(1));

            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId(), InteractionHand.MAIN_HAND);
            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId(), InteractionHand.MAIN_HAND);
            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId(), InteractionHand.MAIN_HAND);

            verify(fixture.target(), times(2)).hurt(any(), anyFloat());
            verify(fixture.player(), times(2)).swing(InteractionHand.MAIN_HAND, true);
            verify(fixture.player(), never()).swing(InteractionHand.OFF_HAND, true);
        }
    }

    @Test
    void derivesVanillaBaselineFromAttackSpeedAndAppliesUpgradeReduction() {
        var fixture = combatFixture();
        when(fixture.player().getAttributeValue(Attributes.ATTACK_SPEED)).thenReturn(4.0);

        try (MockedStatic<PlayerIdleDataManager> dataManager = Mockito.mockStatic(PlayerIdleDataManager.class);
                MockedStatic<IdleUpgradeHooks> upgradeHooks = Mockito.mockStatic(IdleUpgradeHooks.class)) {
            dataManager.when(() -> PlayerIdleDataManager.isActiveRewardEligibleNow(fixture.player())).thenReturn(true);
            dataManager.when(() -> PlayerIdleDataManager.get(fixture.player())).thenReturn(new PlayerIdleData());
            upgradeHooks.when(() -> IdleUpgradeHooks.hasCombatUpgrade(any(PlayerIdleData.class))).thenReturn(true);
            upgradeHooks.when(() -> IdleUpgradeHooks.isUnarmedDualPunchEnabled(any(PlayerIdleData.class)))
                    .thenReturn(true);
            upgradeHooks.when(() -> IdleUpgradeHooks.getUnarmedPunchIntervalTicks(any(PlayerIdleData.class), anyLong()))
                    .thenReturn(4L);

            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId());

            var baselineCaptor = ArgumentCaptor.forClass(Long.class);
            upgradeHooks.verify(
                    () -> IdleUpgradeHooks.getUnarmedPunchIntervalTicks(any(PlayerIdleData.class),
                            baselineCaptor.capture()));
            assertThat(baselineCaptor.getValue()).isEqualTo(10L);
            assertThat(4L).isLessThan(baselineCaptor.getValue());
        }
    }

    @Test
    void resetsCooldownAfterDeathRespawnCloneForNewPlayerInstance() {
        var fixture = combatFixture();
        var clonedPlayer = clonePlayer(fixture, fixture.player().getUUID());
        when(fixture.level().getGameTime()).thenReturn(100L, 110L);

        try (MockedStatic<PlayerIdleDataManager> dataManager = Mockito.mockStatic(PlayerIdleDataManager.class);
                MockedStatic<IdleUpgradeHooks> upgradeHooks = Mockito.mockStatic(IdleUpgradeHooks.class)) {
            stubCombatPrerequisites(dataManager, upgradeHooks, fixture.player());
            stubCombatPrerequisites(dataManager, upgradeHooks, clonedPlayer);

            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId());

            var cloneEvent = mock(PlayerEvent.Clone.class);
            when(cloneEvent.isWasDeath()).thenReturn(true);
            when(cloneEvent.getOriginal()).thenReturn(fixture.player());
            when(cloneEvent.getEntity()).thenReturn(clonedPlayer);
            IdleCombatHandler.onPlayerClone(cloneEvent);

            IdleCombatHandler.handlePunchRequest(clonedPlayer, fixture.targetEntityId());

            verify(fixture.target(), times(2)).hurt(any(), anyFloat());
        }
    }

    @Test
    void keepsCooldownStateOnNonDeathCloneForNewPlayerInstance() {
        var fixture = combatFixture();
        var clonedPlayer = clonePlayer(fixture, fixture.player().getUUID());
        when(fixture.level().getGameTime()).thenReturn(100L, 110L);

        try (MockedStatic<PlayerIdleDataManager> dataManager = Mockito.mockStatic(PlayerIdleDataManager.class);
                MockedStatic<IdleUpgradeHooks> upgradeHooks = Mockito.mockStatic(IdleUpgradeHooks.class)) {
            stubCombatPrerequisites(dataManager, upgradeHooks, fixture.player());
            stubCombatPrerequisites(dataManager, upgradeHooks, clonedPlayer);

            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId());

            var cloneEvent = mock(PlayerEvent.Clone.class);
            when(cloneEvent.isWasDeath()).thenReturn(false);
            when(cloneEvent.getOriginal()).thenReturn(fixture.player());
            when(cloneEvent.getEntity()).thenReturn(clonedPlayer);
            IdleCombatHandler.onPlayerClone(cloneEvent);

            IdleCombatHandler.handlePunchRequest(clonedPlayer, fixture.targetEntityId());

            verify(fixture.target(), times(2)).hurt(any(), anyFloat());
        }
    }

    @Test
    void resetsCooldownStateOnDimensionChange() {
        var fixture = combatFixture();
        when(fixture.level().getGameTime()).thenReturn(100L, 110L);

        try (MockedStatic<PlayerIdleDataManager> dataManager = Mockito.mockStatic(PlayerIdleDataManager.class);
                MockedStatic<IdleUpgradeHooks> upgradeHooks = Mockito.mockStatic(IdleUpgradeHooks.class)) {
            dataManager.when(() -> PlayerIdleDataManager.isActiveRewardEligibleNow(fixture.player())).thenReturn(true);
            dataManager.when(() -> PlayerIdleDataManager.get(fixture.player())).thenReturn(new PlayerIdleData());
            upgradeHooks.when(() -> IdleUpgradeHooks.hasCombatUpgrade(any(PlayerIdleData.class))).thenReturn(true);
            upgradeHooks.when(() -> IdleUpgradeHooks.isUnarmedDualPunchEnabled(any(PlayerIdleData.class)))
                    .thenReturn(true);
            upgradeHooks.when(() -> IdleUpgradeHooks.getUnarmedPunchIntervalTicks(any(PlayerIdleData.class), anyLong()))
                    .thenReturn(5L);

            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId());

            var dimensionChangeEvent = mock(PlayerEvent.PlayerChangedDimensionEvent.class);
            when(dimensionChangeEvent.getEntity()).thenReturn(fixture.player());
            IdleCombatHandler.onPlayerChangedDimension(dimensionChangeEvent);

            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId());

            verify(fixture.target(), times(2)).hurt(any(), anyFloat());
        }
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

    @Test
    void sendsDebugChatMessageForExecutedCombat1PunchWhenDebugEnabled() {
        var fixture = combatFixture();
        var config = mock(AEConfig.class);

        try (MockedStatic<PlayerIdleDataManager> dataManager = Mockito.mockStatic(PlayerIdleDataManager.class);
                MockedStatic<IdleUpgradeHooks> upgradeHooks = Mockito.mockStatic(IdleUpgradeHooks.class);
                MockedStatic<AEConfig> aeConfig = Mockito.mockStatic(AEConfig.class)) {
            stubCombatPrerequisites(dataManager, upgradeHooks, fixture.player());
            aeConfig.when(AEConfig::instance).thenReturn(config);
            when(config.isDebugToolsEnabled()).thenReturn(true);

            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId(), InteractionHand.OFF_HAND);

            verify(fixture.player())
                    .sendSystemMessage(argThat(component -> component.getString().contains("combat_1 punch executed")
                            && component.getString().contains("OFF_HAND")));
        }
    }

    @Test
    void doesNotSendDebugChatMessageForExecutedCombat1PunchWhenDebugDisabled() {
        var fixture = combatFixture();
        var config = mock(AEConfig.class);

        try (MockedStatic<PlayerIdleDataManager> dataManager = Mockito.mockStatic(PlayerIdleDataManager.class);
                MockedStatic<IdleUpgradeHooks> upgradeHooks = Mockito.mockStatic(IdleUpgradeHooks.class);
                MockedStatic<AEConfig> aeConfig = Mockito.mockStatic(AEConfig.class)) {
            stubCombatPrerequisites(dataManager, upgradeHooks, fixture.player());
            aeConfig.when(AEConfig::instance).thenReturn(config);
            when(config.isDebugToolsEnabled()).thenReturn(false);

            IdleCombatHandler.handlePunchRequest(fixture.player(), fixture.targetEntityId(), InteractionHand.MAIN_HAND);

            verify(fixture.player(), never()).sendSystemMessage(any());
        }
    }

    private static ServerPlayer clonePlayer(CombatFixture fixture, UUID uuid) {
        var clonedPlayer = mock(ServerPlayer.class);
        initializePlayer(clonedPlayer, fixture.level(), fixture.target(), uuid);
        return clonedPlayer;
    }

    private static void stubCombatPrerequisites(MockedStatic<PlayerIdleDataManager> dataManager,
            MockedStatic<IdleUpgradeHooks> upgradeHooks,
            ServerPlayer player) {
        dataManager.when(() -> PlayerIdleDataManager.isActiveRewardEligibleNow(player)).thenReturn(true);
        dataManager.when(() -> PlayerIdleDataManager.get(player)).thenReturn(new PlayerIdleData());
        upgradeHooks.when(() -> IdleUpgradeHooks.hasCombatUpgrade(any(PlayerIdleData.class))).thenReturn(true);
        upgradeHooks.when(() -> IdleUpgradeHooks.isUnarmedDualPunchEnabled(any(PlayerIdleData.class))).thenReturn(true);
        upgradeHooks.when(() -> IdleUpgradeHooks.getUnarmedPunchIntervalTicks(any(PlayerIdleData.class), anyLong()))
                .thenReturn(5L);
    }

    private static CombatFixture combatFixture() {
        var level = mock(ServerLevel.class);
        var player = mock(ServerPlayer.class);
        var target = mock(LivingEntity.class);
        var damageSources = mock(DamageSources.class);
        var damageSource = mock(DamageSource.class);
        var uuid = UUID.randomUUID();
        var targetEntityId = 1234;

        when(level.isClientSide()).thenReturn(false);
        initializePlayer(player, level, target, uuid);

        when(target.isAlive()).thenReturn(true);
        when(target.level()).thenReturn((Level) level);
        when(target.hurt(any(), anyFloat())).thenReturn(true);

        when(level.getEntity(targetEntityId)).thenReturn(target);
        when(level.damageSources()).thenReturn(damageSources);
        when(damageSources.playerAttack(player)).thenReturn(damageSource);

        return new CombatFixture(level, player, target, targetEntityId);
    }

    private static void initializePlayer(ServerPlayer player, ServerLevel level, LivingEntity target, UUID uuid) {
        when(player.level()).thenReturn(level);
        when(player.serverLevel()).thenReturn(level);
        when(player.getUUID()).thenReturn(uuid);
        when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        when(player.getAttributeValue(Attributes.ATTACK_DAMAGE)).thenReturn(4.0);
        when(player.distanceToSqr((Entity) target)).thenReturn(4.0);
        when(player.hasLineOfSight(target)).thenReturn(true);
        when(player.canAttack(target)).thenReturn(true);
    }

    private record CombatFixture(ServerLevel level, ServerPlayer player, LivingEntity target, int targetEntityId) {
    }
}
