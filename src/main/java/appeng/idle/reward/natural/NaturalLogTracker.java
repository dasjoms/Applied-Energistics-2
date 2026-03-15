package appeng.idle.reward.natural;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;

import appeng.core.AppEng;
import appeng.core.worlddata.AESavedData;

/**
 * Tracks player-placed log blocks so naturally generated logs can be distinguished for rewards.
 */
public final class NaturalLogTracker {
    private static final String DATA_NAME = AppEng.MOD_ID + "_natural_log_tracker";

    // Strict anti-exploit default: untracked logs are not considered natural.
    private static final boolean ALLOW_UNKNOWN_AS_NATURAL = false;

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
        if (state.is(BlockTags.LOGS)) {
            mark(level, event.getPos(), Provenance.PLAYER_PLACED);
        } else {
            clear(level, event.getPos());
        }
    }

    public static void onBlockRemovedOrChanged(ServerLevel level, BlockPos pos, BlockState oldState) {
        if (oldState.is(BlockTags.LOGS)) {
            clear(level, pos);
        }
    }

    public static void markNaturalWorldgen(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(BlockTags.LOGS)) {
            mark(level, pos, Provenance.NATURAL_WORLDGEN);
        }
    }

    public static boolean isNaturallyGeneratedLog(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.is(BlockTags.LOGS)) {
            return false;
        }

        var provenance = get(level).getProvenance(pos);
        if (provenance == Provenance.PLAYER_PLACED) {
            return false;
        }

        if (provenance == Provenance.UNKNOWN) {
            return ALLOW_UNKNOWN_AS_NATURAL;
        }

        return true;
    }

    private static void mark(ServerLevel level, BlockPos pos, Provenance provenance) {
        get(level).setProvenance(pos, provenance);
    }

    private static void clear(ServerLevel level, BlockPos pos) {
        get(level).clear(pos);
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
        NATURAL_WORLDGEN((byte) 2);

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

        private final Long2ByteMap trackedLogs = new Long2ByteOpenHashMap();

        NaturalLogTrackerData() {
        }

        static NaturalLogTrackerData load(CompoundTag tag) {
            var data = new NaturalLogTrackerData();
            var positions = tag.getLongArray(TAG_POSITIONS);
            var provenance = tag.getByteArray(TAG_PROVENANCE);
            var count = Math.min(positions.length, provenance.length);
            for (int i = 0; i < count; i++) {
                data.trackedLogs.put(positions[i], provenance[i]);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            var positions = new long[trackedLogs.size()];
            var provenance = new byte[trackedLogs.size()];

            int i = 0;
            for (var entry : trackedLogs.long2ByteEntrySet()) {
                positions[i] = entry.getLongKey();
                provenance[i] = entry.getByteValue();
                i++;
            }

            tag.putLongArray(TAG_POSITIONS, positions);
            tag.putByteArray(TAG_PROVENANCE, provenance);
            return tag;
        }

        Provenance getProvenance(BlockPos pos) {
            if (!trackedLogs.containsKey(pos.asLong())) {
                return Provenance.UNKNOWN;
            }
            return Provenance.fromId(trackedLogs.get(pos.asLong()));
        }

        void setProvenance(BlockPos pos, Provenance provenance) {
            trackedLogs.put(pos.asLong(), provenance.id());
            setDirty();
        }

        void clear(BlockPos pos) {
            var key = pos.asLong();
            if (trackedLogs.containsKey(key)) {
                trackedLogs.remove(key);
                setDirty();
            }
        }
    }
}
