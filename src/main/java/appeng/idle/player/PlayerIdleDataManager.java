package appeng.idle.player;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

import appeng.core.AEConfig;
import appeng.core.definitions.AEItems;
import appeng.idle.currency.CurrencyAmount;
import appeng.idle.currency.CurrencyDefinition;
import appeng.idle.currency.CurrencyId;
import appeng.idle.currency.IdleCurrencyManager;
import appeng.idle.net.IdleCurrencySyncService;
import appeng.idle.tick.IdleGenerationTicker;
import appeng.idle.upgrade.CostBundle;
import appeng.idle.upgrade.CurrencyTransactionService;
import appeng.idle.upgrade.SpendReason;

/**
 * Handles loading/saving player idle data into persistent player NBT and enforces server-authoritative mutations.
 */
public final class PlayerIdleDataManager {
    private static final String IDLE_ROOT_TAG = "appengIdleData";

    private PlayerIdleDataManager() {
    }

    public static PlayerIdleData get(ServerPlayer player) {
        Objects.requireNonNull(player, "player");

        var persistedTag = getPersistedTag(player);
        if (!persistedTag.contains(IDLE_ROOT_TAG, Tag.TAG_COMPOUND)) {
            var created = new PlayerIdleData();
            created.setLastSeenEpochSeconds(Instant.now().getEpochSecond());
            save(player, created);
            return created;
        }

        return PlayerIdleData.fromTag(persistedTag.getCompound(IDLE_ROOT_TAG));
    }

    public static void save(ServerPlayer player, PlayerIdleData data) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(data, "data");

        var persistedTag = getPersistedTag(player);
        persistedTag.put(IDLE_ROOT_TAG, data.toTag());
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistedTag);
    }

    public static long addBalance(ServerPlayer player, CurrencyId currencyId, CurrencyAmount amount) {
        ensureServerPlayer(player);
        if (amount.units() < 0) {
            throw new IllegalArgumentException("Cannot add a negative amount.");
        }

        var data = get(player);
        var updatedBalance = Math.addExact(data.getBalance(currencyId), amount.units());
        data.setBalance(currencyId, updatedBalance);
        data.setLastSeenEpochSeconds(Instant.now().getEpochSecond());
        data.setDataVersion(PlayerIdleData.CURRENT_DATA_VERSION);
        save(player, data);
        IdleCurrencySyncService.sendDelta(player, Map.of(currencyId, updatedBalance));
        return updatedBalance;
    }

    public static boolean trySpend(ServerPlayer player, CurrencyId currencyId, CurrencyAmount amount) {
        ensureServerPlayer(player);
        if (amount.units() < 0) {
            throw new IllegalArgumentException("Cannot spend a negative amount.");
        }

        var data = get(player);
        var currentBalance = data.getBalance(currencyId);
        if (currentBalance < amount.units()) {
            return false;
        }

        data.setBalance(currencyId, currentBalance - amount.units());
        data.setLastSeenEpochSeconds(Instant.now().getEpochSecond());
        data.setDataVersion(PlayerIdleData.CURRENT_DATA_VERSION);
        save(player, data);
        IdleCurrencySyncService.sendDelta(player, Map.of(currencyId, currentBalance - amount.units()));
        return true;
    }

    public static boolean trySpend(ServerPlayer player, CostBundle costBundle, SpendReason reason) {
        ensureServerPlayer(player);
        Objects.requireNonNull(costBundle, "costBundle");
        Objects.requireNonNull(reason, "reason");

        var data = get(player);
        var spent = CurrencyTransactionService.trySpend(data, costBundle, reason);
        if (!spent) {
            return false;
        }

        data.setLastSeenEpochSeconds(Instant.now().getEpochSecond());
        data.setDataVersion(PlayerIdleData.CURRENT_DATA_VERSION);
        save(player, data);
        IdleCurrencySyncService.sendSnapshot(player);
        return true;
    }

    public static void setUpgradeLevel(ServerPlayer player, ResourceLocation upgradeId, int level) {
        ensureServerPlayer(player);

        var data = get(player);
        data.setUpgradeLevel(upgradeId, level);
        data.setLastSeenEpochSeconds(Instant.now().getEpochSecond());
        data.setDataVersion(PlayerIdleData.CURRENT_DATA_VERSION);
        save(player, data);
        IdleCurrencySyncService.sendSnapshot(player);
    }

    public static void setLastSeenEpochSeconds(ServerPlayer player, long epochSeconds) {
        ensureServerPlayer(player);

        var data = get(player);
        data.setLastSeenEpochSeconds(Math.max(0L, epochSeconds));
        data.setDataVersion(PlayerIdleData.CURRENT_DATA_VERSION);
        save(player, data);
    }

    public static void simulateOfflineCatchup(ServerPlayer player, long elapsedSeconds) {
        ensureServerPlayer(player);

        var cappedElapsedSeconds = Math.min(Math.max(0L, elapsedSeconds),
                AEConfig.instance().getIdleOfflineMaxSeconds());
        IdleGenerationTicker.accrueOfflineCatchup(player, cappedElapsedSeconds);

        var data = get(player);
        data.setLastSeenEpochSeconds(Instant.now().getEpochSecond());
        data.setDataVersion(PlayerIdleData.CURRENT_DATA_VERSION);
        save(player, data);
    }

    public static boolean addGeneratedBalances(ServerPlayer player, Map<CurrencyId, Long> generatedAmounts,
            String reason) {
        ensureServerPlayer(player);
        Objects.requireNonNull(generatedAmounts, "generatedAmounts");
        Objects.requireNonNull(reason, "reason");

        if (generatedAmounts.isEmpty()) {
            return false;
        }

        var data = get(player);
        var changedBalances = new LinkedHashMap<CurrencyId, Long>();

        for (var entry : generatedAmounts.entrySet()) {
            var currencyId = Objects.requireNonNull(entry.getKey(), "currencyId");
            var generated = entry.getValue() == null ? 0L : entry.getValue();
            if (generated <= 0L) {
                continue;
            }

            var currentBalance = data.getBalance(currencyId);
            // Clamp order: generation cap -> addition -> balance cap (with Long.MAX_VALUE as final safety).
            var postAdditionBalance = currentBalance > Long.MAX_VALUE - generated
                    ? Long.MAX_VALUE
                    : currentBalance + generated;
            var updatedBalance = clampBalanceCap(currencyId, postAdditionBalance);

            if (updatedBalance != currentBalance) {
                data.setBalance(currencyId, updatedBalance);
                changedBalances.put(currencyId, updatedBalance);
            }
        }

        if (changedBalances.isEmpty()) {
            return false;
        }

        data.setLastSeenEpochSeconds(Instant.now().getEpochSecond());
        data.setDataVersion(PlayerIdleData.CURRENT_DATA_VERSION);
        save(player, data);
        IdleCurrencySyncService.sendDelta(player, changedBalances);
        return true;
    }

    private static long clampBalanceCap(CurrencyId currencyId, long balanceAfterAddition) {
        if (balanceAfterAddition <= 0L) {
            return 0L;
        }

        var definition = IdleCurrencyManager.get(currencyId);
        CurrencyDefinition.CurrencyCaps caps = definition == null ? null : definition.caps();
        var balanceCap = caps == null ? null : caps.balanceCap();

        return balanceCap == null ? balanceAfterAddition : Math.min(balanceAfterAddition, balanceCap);
    }

    public static boolean isIdleGenerationUnlocked(ServerPlayer player) {
        ensureServerPlayer(player);
        return get(player).isIdleVisorUnlocked();
    }

    public static void unlockIdleGeneration(ServerPlayer player) {
        ensureServerPlayer(player);

        var data = get(player);
        if (data.isIdleVisorUnlocked()) {
            return;
        }

        data.setIdleVisorUnlocked(true);
        data.setLastSeenEpochSeconds(Instant.now().getEpochSecond());
        data.setDataVersion(PlayerIdleData.CURRENT_DATA_VERSION);
        save(player, data);
    }

    public static void handleEquipmentChanged(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }

        if (!AEItems.IDLE_VISOR.is(event.getTo())) {
            return;
        }

        unlockIdleGeneration(player);
    }

    public static void handlePlayerLoggedIn(PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        var nowEpochSeconds = Instant.now().getEpochSecond();
        var data = get(player);
        if (data.getLastSeenEpochSeconds() <= 0L) {
            data.setLastSeenEpochSeconds(nowEpochSeconds);
            data.setDataVersion(PlayerIdleData.CURRENT_DATA_VERSION);
            save(player, data);
            return;
        }

        var elapsedSeconds = Math.max(0L, nowEpochSeconds - data.getLastSeenEpochSeconds());
        var cappedElapsedSeconds = Math.min(elapsedSeconds, AEConfig.instance().getIdleOfflineMaxSeconds());

        IdleGenerationTicker.accrueOfflineCatchup(player, cappedElapsedSeconds);

        data = get(player);
        data.setLastSeenEpochSeconds(nowEpochSeconds);
        data.setDataVersion(PlayerIdleData.CURRENT_DATA_VERSION);
        save(player, data);
    }

    public static void handlePlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        var data = get(player);
        data.setLastSeenEpochSeconds(Instant.now().getEpochSecond());
        data.setDataVersion(PlayerIdleData.CURRENT_DATA_VERSION);
        save(player, data);
    }

    public static void handlePlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)
                || !(event.getOriginal() instanceof ServerPlayer oldPlayer)) {
            return;
        }

        var oldPersistedTag = oldPlayer.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!oldPersistedTag.contains(IDLE_ROOT_TAG, Tag.TAG_COMPOUND)) {
            return;
        }

        var newPersistedTag = newPlayer.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        newPersistedTag.put(IDLE_ROOT_TAG, oldPersistedTag.getCompound(IDLE_ROOT_TAG).copy());
        newPlayer.getPersistentData().put(Player.PERSISTED_NBT_TAG, newPersistedTag);
    }

    private static void ensureServerPlayer(ServerPlayer player) {
        if (player.level().isClientSide()) {
            throw new IllegalStateException("Idle player data may only be mutated on the server.");
        }
    }

    private static CompoundTag getPersistedTag(ServerPlayer player) {
        var persistentData = player.getPersistentData();
        if (!persistentData.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            persistentData.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return persistentData.getCompound(Player.PERSISTED_NBT_TAG);
    }
}
