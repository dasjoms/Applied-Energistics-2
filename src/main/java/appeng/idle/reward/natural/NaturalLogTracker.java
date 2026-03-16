package appeng.idle.reward.natural;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;

import appeng.core.AppEng;
import appeng.core.worlddata.AESavedData;

/**
 * Tracks log provenance for idle natural-log rewards.
 * <p>
 * Player-placed logs are denied; unknown, worldgen, sapling-grown, and non-player placements are accepted.
 */
public final class NaturalLogTracker {
    private static final String DATA_NAME = AppEng.MOD_ID + "_natural_log_tracker";

    private NaturalLogTracker() {
    }

    @SubscribeEvent
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || event.getLevel().isClientSide()) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        var state = event.getPlacedBlock();
        if (shouldMarkPlayerPlaced(state, event.getEntity())) {
            mark(level, event.getPos(), Provenance.PLAYER_PLACED);
        } else {
            clear(level, event.getPos());
        }
    }

    static boolean shouldMarkPlayerPlaced(BlockState state, Entity entity) {
        return state.is(BlockTags.LOGS) && entity instanceof Player;
    }

    public static void onBlockRemovedOrChanged(ServerLevel level, BlockPos pos, BlockState oldState) {
        if (oldState.is(BlockTags.LOGS)) {
            clear(level, pos);
        }
    }

    public static void markNaturalWorldgen(ServerLevel level, BlockPos pos, BlockState state) {
        // Natural/worldgen logs are eligible by default, so no explicit provenance entry is needed.
    }

    public static boolean isEligibleLogForReward(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.is(BlockTags.LOGS)) {
            return false;
        }

        var data = get(level);
        var provenance = data.getProvenance(pos);
        return isProvenanceEligibleForReward(provenance);
    }

    static boolean isProvenanceEligibleForReward(Provenance provenance) {
        return switch (provenance) {
            case PLAYER_PLACED -> false;
            case UNKNOWN, NATURAL_WORLDGEN, SAPLING_GROWN -> true;
        };
    }

    private static void mark(ServerLevel level, BlockPos pos, Provenance provenance) {
        get(level).setProvenance(pos, provenance);
    }

    private static void clear(ServerLevel level, BlockPos pos) {
        get(level).clear(pos);
    }

    public static Provenance getProvenanceForDebug(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.is(BlockTags.LOGS)) {
            return Provenance.UNKNOWN;
        }
        return get(level).getProvenance(pos);
    }

    private static NaturalLogTrackerData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        NaturalLogTrackerData::new,
                        (tag, provider) -> NaturalLogTrackerData.load(tag),
                        null),
                DATA_NAME);
    }

    public enum Provenance {
        UNKNOWN((byte) 0),
        PLAYER_PLACED((byte) 1),
        NATURAL_WORLDGEN((byte) 2),
        SAPLING_GROWN((byte) 3);

        private final byte id;

        Provenance(byte id) {
            this.id = id;
        }

        byte id() {
            return id;
        }

        static Provenance fromId(byte id) {
            for (var value : values()) {
                if (value.id == id) {
                    return value;
                }
            }
            return UNKNOWN;
        }
    }

    static final class NaturalLogTrackerData extends AESavedData {
        private static final String TAG_POSITIONS = "positions";
        private static final String TAG_PROVENANCE = "provenance";

        private final Long2ByteMap deniedLogs = new Long2ByteOpenHashMap();

        NaturalLogTrackerData() {
        }

        static NaturalLogTrackerData load(CompoundTag tag) {
            var data = new NaturalLogTrackerData();
            var positions = tag.getLongArray(TAG_POSITIONS);
            var provenance = tag.getByteArray(TAG_PROVENANCE);
            var count = Math.min(positions.length, provenance.length);
            for (int i = 0; i < count; i++) {
                if (provenance[i] == Provenance.PLAYER_PLACED.id()) {
                    data.deniedLogs.put(positions[i], provenance[i]);
                }
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            var positions = new long[deniedLogs.size()];
            var provenance = new byte[deniedLogs.size()];

            int i = 0;
            for (var entry : deniedLogs.long2ByteEntrySet()) {
                positions[i] = entry.getLongKey();
                provenance[i] = entry.getByteValue();
                i++;
            }

            tag.putLongArray(TAG_POSITIONS, positions);
            tag.putByteArray(TAG_PROVENANCE, provenance);
            return tag;
        }

        Provenance getProvenance(BlockPos pos) {
            if (!deniedLogs.containsKey(pos.asLong())) {
                return Provenance.UNKNOWN;
            }
            return Provenance.fromId(deniedLogs.get(pos.asLong()));
        }

        void setProvenance(BlockPos pos, Provenance provenance) {
            var key = pos.asLong();
            if (provenance == Provenance.PLAYER_PLACED) {
                deniedLogs.put(key, provenance.id());
                setDirty();
                return;
            }

            if (deniedLogs.remove(key) != 0) {
                setDirty();
            }
        }

        void clear(BlockPos pos) {
            var key = pos.asLong();
            if (deniedLogs.containsKey(key)) {
                deniedLogs.remove(key);
                setDirty();
            }
        }
    }
}
