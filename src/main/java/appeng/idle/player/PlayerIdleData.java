package appeng.idle.player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import appeng.idle.currency.CurrencyId;

/**
 * Server-authoritative idle data stored per player.
 */
public final class PlayerIdleData {
    private static final String TAG_DATA_VERSION = "dataVersion";
    private static final String TAG_LAST_SEEN = "lastSeenEpochSeconds";
    private static final String TAG_BALANCES = "balances";
    private static final String TAG_GENERATION_PROGRESS_TICKS = "generationProgressTicks";
    private static final String TAG_ONLINE_PROGRESS_BASELINE_TICK = "onlineProgressBaselineTick";
    private static final String TAG_OWNED_UPGRADES = "ownedUpgradeLevels";
    private static final String TAG_VISOR_UNLOCKED = "idleVisorUnlocked";
    private static final String TAG_ID = "id";
    private static final String TAG_AMOUNT = "amount";
    private static final String TAG_LEVEL = "level";

    public static final int CURRENT_DATA_VERSION = 4;

    private final Map<CurrencyId, Long> balances;
    private final Map<CurrencyId, Long> generationProgressTicks;
    private final Map<ResourceLocation, Integer> ownedUpgradeLevels;
    private long lastSeenEpochSeconds;
    private long onlineProgressBaselineTick;
    private int dataVersion;
    private boolean idleVisorUnlocked;

    public PlayerIdleData() {
        this(new HashMap<>(), new HashMap<>(), 0L, CURRENT_DATA_VERSION, new HashMap<>(), false, -1L);
    }

    public PlayerIdleData(Map<CurrencyId, Long> balances, long lastSeenEpochSeconds, int dataVersion,
            Map<ResourceLocation, Integer> ownedUpgradeLevels) {
        this(balances, new HashMap<>(), lastSeenEpochSeconds, dataVersion, ownedUpgradeLevels, false, -1L);
    }

    public PlayerIdleData(Map<CurrencyId, Long> balances, long lastSeenEpochSeconds, int dataVersion,
            Map<ResourceLocation, Integer> ownedUpgradeLevels, boolean idleVisorUnlocked) {
        this(balances, new HashMap<>(), lastSeenEpochSeconds, dataVersion, ownedUpgradeLevels, idleVisorUnlocked, -1L);
    }

    public PlayerIdleData(Map<CurrencyId, Long> balances, Map<CurrencyId, Long> generationProgressTicks,
            long lastSeenEpochSeconds, int dataVersion,
            Map<ResourceLocation, Integer> ownedUpgradeLevels, boolean idleVisorUnlocked) {
        this(balances, generationProgressTicks, lastSeenEpochSeconds, dataVersion, ownedUpgradeLevels,
                idleVisorUnlocked, -1L);
    }

    public PlayerIdleData(Map<CurrencyId, Long> balances, Map<CurrencyId, Long> generationProgressTicks,
            long lastSeenEpochSeconds, int dataVersion,
            Map<ResourceLocation, Integer> ownedUpgradeLevels, boolean idleVisorUnlocked,
            long onlineProgressBaselineTick) {
        this.balances = new HashMap<>(balances);
        this.generationProgressTicks = new HashMap<>(generationProgressTicks);
        this.lastSeenEpochSeconds = lastSeenEpochSeconds;
        this.dataVersion = dataVersion;
        this.ownedUpgradeLevels = new HashMap<>(ownedUpgradeLevels);
        this.idleVisorUnlocked = idleVisorUnlocked;
        this.onlineProgressBaselineTick = Math.max(-1L, onlineProgressBaselineTick);
    }

    public long getBalance(CurrencyId currencyId) {
        return balances.getOrDefault(currencyId, 0L);
    }

    public Map<CurrencyId, Long> balancesView() {
        return Collections.unmodifiableMap(balances);
    }

    public long getGenerationProgressTicks(CurrencyId currencyId) {
        return generationProgressTicks.getOrDefault(currencyId, 0L);
    }

    public Map<CurrencyId, Long> generationProgressTicksView() {
        return Collections.unmodifiableMap(generationProgressTicks);
    }

    public long getLastSeenEpochSeconds() {
        return lastSeenEpochSeconds;
    }

    public int getDataVersion() {
        return dataVersion;
    }

    public long getOnlineProgressBaselineTick() {
        return onlineProgressBaselineTick;
    }

    public Map<ResourceLocation, Integer> ownedUpgradeLevelsView() {
        return Collections.unmodifiableMap(ownedUpgradeLevels);
    }

    public boolean isIdleVisorUnlocked() {
        return idleVisorUnlocked;
    }

    void setLastSeenEpochSeconds(long lastSeenEpochSeconds) {
        this.lastSeenEpochSeconds = lastSeenEpochSeconds;
    }

    void setDataVersion(int dataVersion) {
        this.dataVersion = dataVersion;
    }

    public void setOnlineProgressBaselineTick(long onlineProgressBaselineTick) {
        this.onlineProgressBaselineTick = Math.max(-1L, onlineProgressBaselineTick);
    }

    void setIdleVisorUnlocked(boolean idleVisorUnlocked) {
        this.idleVisorUnlocked = idleVisorUnlocked;
    }

    public void setBalance(CurrencyId currencyId, long amount) {
        if (amount == 0L) {
            balances.remove(currencyId);
        } else {
            balances.put(currencyId, amount);
        }
    }

    public void setGenerationProgressTicks(CurrencyId currencyId, long ticks) {
        if (ticks < 0L) {
            throw new IllegalArgumentException("generationProgressTicks must be non-negative.");
        }

        if (ticks == 0L) {
            generationProgressTicks.remove(currencyId);
        } else {
            generationProgressTicks.put(currencyId, ticks);
        }
    }

    void setUpgradeLevel(ResourceLocation upgradeId, int level) {
        if (level <= 0) {
            ownedUpgradeLevels.remove(upgradeId);
        } else {
            ownedUpgradeLevels.put(upgradeId, level);
        }
    }

    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putInt(TAG_DATA_VERSION, dataVersion);
        tag.putLong(TAG_LAST_SEEN, lastSeenEpochSeconds);

        var balancesTag = new ListTag();
        for (var entry : balances.entrySet()) {
            var balanceTag = new CompoundTag();
            balanceTag.putString(TAG_ID, entry.getKey().id().toString());
            balanceTag.putLong(TAG_AMOUNT, entry.getValue());
            balancesTag.add(balanceTag);
        }
        tag.put(TAG_BALANCES, balancesTag);

        var progressTag = new ListTag();
        for (var entry : generationProgressTicks.entrySet()) {
            var progressEntryTag = new CompoundTag();
            progressEntryTag.putString(TAG_ID, entry.getKey().id().toString());
            progressEntryTag.putLong(TAG_AMOUNT, entry.getValue());
            progressTag.add(progressEntryTag);
        }
        tag.put(TAG_GENERATION_PROGRESS_TICKS, progressTag);
        tag.putLong(TAG_ONLINE_PROGRESS_BASELINE_TICK, onlineProgressBaselineTick);

        var upgradesTag = new ListTag();
        for (var entry : ownedUpgradeLevels.entrySet()) {
            var upgradeTag = new CompoundTag();
            upgradeTag.putString(TAG_ID, entry.getKey().toString());
            upgradeTag.putInt(TAG_LEVEL, entry.getValue());
            upgradesTag.add(upgradeTag);
        }
        tag.put(TAG_OWNED_UPGRADES, upgradesTag);
        tag.putBoolean(TAG_VISOR_UNLOCKED, idleVisorUnlocked);

        return tag;
    }

    public static PlayerIdleData fromTag(CompoundTag tag) {
        var balances = new HashMap<CurrencyId, Long>();
        var balancesTag = tag.getList(TAG_BALANCES, Tag.TAG_COMPOUND);
        for (var balanceEntryTag : balancesTag) {
            if (!(balanceEntryTag instanceof CompoundTag balanceEntry)) {
                continue;
            }

            var currencyId = ResourceLocation.tryParse(balanceEntry.getString(TAG_ID));
            if (currencyId == null) {
                continue;
            }

            var amount = balanceEntry.getLong(TAG_AMOUNT);
            if (amount != 0L) {
                balances.put(new CurrencyId(currencyId), amount);
            }
        }

        var ownedUpgradeLevels = new HashMap<ResourceLocation, Integer>();
        var upgradesTag = tag.getList(TAG_OWNED_UPGRADES, Tag.TAG_COMPOUND);
        for (var upgradeEntryTag : upgradesTag) {
            if (!(upgradeEntryTag instanceof CompoundTag upgradeEntry)) {
                continue;
            }

            var upgradeId = ResourceLocation.tryParse(upgradeEntry.getString(TAG_ID));
            if (upgradeId == null) {
                continue;
            }

            var level = upgradeEntry.getInt(TAG_LEVEL);
            if (level > 0) {
                ownedUpgradeLevels.put(upgradeId, level);
            }
        }

        var generationProgressTicks = new HashMap<CurrencyId, Long>();
        var progressTag = tag.getList(TAG_GENERATION_PROGRESS_TICKS, Tag.TAG_COMPOUND);
        for (var progressEntryTag : progressTag) {
            if (!(progressEntryTag instanceof CompoundTag progressEntry)) {
                continue;
            }

            var currencyId = ResourceLocation.tryParse(progressEntry.getString(TAG_ID));
            if (currencyId == null) {
                continue;
            }

            var ticks = progressEntry.getLong(TAG_AMOUNT);
            if (ticks > 0L) {
                generationProgressTicks.put(new CurrencyId(currencyId), ticks);
            }
        }

        return new PlayerIdleData(
                balances,
                generationProgressTicks,
                tag.getLong(TAG_LAST_SEEN),
                tag.contains(TAG_DATA_VERSION, Tag.TAG_INT) ? tag.getInt(TAG_DATA_VERSION) : CURRENT_DATA_VERSION,
                ownedUpgradeLevels,
                tag.getBoolean(TAG_VISOR_UNLOCKED),
                tag.contains(TAG_ONLINE_PROGRESS_BASELINE_TICK, Tag.TAG_LONG)
                        ? tag.getLong(TAG_ONLINE_PROGRESS_BASELINE_TICK)
                        : -1L);
    }
}
