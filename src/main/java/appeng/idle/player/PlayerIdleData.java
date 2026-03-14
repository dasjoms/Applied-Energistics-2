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
    private static final String TAG_OWNED_UPGRADES = "ownedUpgradeLevels";
    private static final String TAG_ID = "id";
    private static final String TAG_AMOUNT = "amount";
    private static final String TAG_LEVEL = "level";

    public static final int CURRENT_DATA_VERSION = 1;

    private final Map<CurrencyId, Long> balances;
    private final Map<ResourceLocation, Integer> ownedUpgradeLevels;
    private long lastSeenEpochSeconds;
    private int dataVersion;

    public PlayerIdleData() {
        this(new HashMap<>(), 0L, CURRENT_DATA_VERSION, new HashMap<>());
    }

    public PlayerIdleData(Map<CurrencyId, Long> balances, long lastSeenEpochSeconds, int dataVersion,
            Map<ResourceLocation, Integer> ownedUpgradeLevels) {
        this.balances = new HashMap<>(balances);
        this.lastSeenEpochSeconds = lastSeenEpochSeconds;
        this.dataVersion = dataVersion;
        this.ownedUpgradeLevels = new HashMap<>(ownedUpgradeLevels);
    }

    public long getBalance(CurrencyId currencyId) {
        return balances.getOrDefault(currencyId, 0L);
    }

    public Map<CurrencyId, Long> balancesView() {
        return Collections.unmodifiableMap(balances);
    }

    public long getLastSeenEpochSeconds() {
        return lastSeenEpochSeconds;
    }

    public int getDataVersion() {
        return dataVersion;
    }

    public Map<ResourceLocation, Integer> ownedUpgradeLevelsView() {
        return Collections.unmodifiableMap(ownedUpgradeLevels);
    }

    void setLastSeenEpochSeconds(long lastSeenEpochSeconds) {
        this.lastSeenEpochSeconds = lastSeenEpochSeconds;
    }

    void setDataVersion(int dataVersion) {
        this.dataVersion = dataVersion;
    }

    void setBalance(CurrencyId currencyId, long amount) {
        if (amount == 0L) {
            balances.remove(currencyId);
        } else {
            balances.put(currencyId, amount);
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

        var upgradesTag = new ListTag();
        for (var entry : ownedUpgradeLevels.entrySet()) {
            var upgradeTag = new CompoundTag();
            upgradeTag.putString(TAG_ID, entry.getKey().toString());
            upgradeTag.putInt(TAG_LEVEL, entry.getValue());
            upgradesTag.add(upgradeTag);
        }
        tag.put(TAG_OWNED_UPGRADES, upgradesTag);

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

        return new PlayerIdleData(
                balances,
                tag.getLong(TAG_LAST_SEEN),
                tag.contains(TAG_DATA_VERSION, Tag.TAG_INT) ? tag.getInt(TAG_DATA_VERSION) : CURRENT_DATA_VERSION,
                ownedUpgradeLevels);
    }
}
