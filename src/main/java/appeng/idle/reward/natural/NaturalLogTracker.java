package appeng.idle.reward.natural;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import appeng.core.AEConfig;
import appeng.core.AppEng;
import appeng.core.worlddata.AESavedData;

/**
 * Tracks log provenance for idle natural-log rewards.
 * <p>
 * Worldgen logs are accepted, player-placed logs are denied, and unknown or sapling-grown logs follow config policy.
 */
public final class NaturalLogTracker {
    private static final String DATA_NAME = AppEng.MOD_ID + "_natural_log_tracker";

    private NaturalLogTracker() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }

        get(level).initializeChunkIfNeeded(level, chunk);
    }

    @SubscribeEvent
    public static void onSaplingGrow(BlockGrowFeatureEvent event) {
        if (event.isCanceled() || AEConfig.instance().isIdleNaturalLogSaplingGrownCounts()) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        var origin = event.getPos();
        var min = origin.offset(-8, -2, -8);
        var max = origin.offset(8, 24, 8);
        for (var pos : BlockPos.betweenClosed(min, max)) {
            var immutablePos = pos.immutable();
            var state = level.getBlockState(immutablePos);
            if (state.is(BlockTags.LOGS)) {
                mark(level, immutablePos, Provenance.SAPLING_GROWN);
            }
        }
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

        var data = get(level);
        data.ensureSectionTracked(level, pos);
        var provenance = data.getProvenance(pos);
        return isProvenanceNatural(provenance, AEConfig.instance().isIdleNaturalLogUnknownCounts(),
                AEConfig.instance().isIdleNaturalLogSaplingGrownCounts());
    }

    static boolean isProvenanceNatural(Provenance provenance, boolean unknownCounts, boolean saplingGrownCounts) {
        return switch (provenance) {
            case NATURAL_WORLDGEN -> true;
            case UNKNOWN -> unknownCounts;
            case SAPLING_GROWN -> saplingGrownCounts;
            case PLAYER_PLACED -> false;
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
        var data = get(level);
        data.ensureSectionTracked(level, pos);
        return data.getProvenance(pos);
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
        private static final String TAG_TRACKED_SECTIONS = "trackedSections";

        private final Long2ByteMap trackedLogs = new Long2ByteOpenHashMap();
        private final LongSet trackedSections = new LongOpenHashSet();

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
            for (var section : tag.getLongArray(TAG_TRACKED_SECTIONS)) {
                data.trackedSections.add(section);
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
            tag.putLongArray(TAG_TRACKED_SECTIONS, trackedSections.toLongArray());
            return tag;
        }

        Provenance getProvenance(BlockPos pos) {
            if (!trackedLogs.containsKey(pos.asLong())) {
                return Provenance.UNKNOWN;
            }
            return Provenance.fromId(trackedLogs.get(pos.asLong()));
        }

        void ensureSectionTracked(ServerLevel level, BlockPos pos) {
            var sectionX = SectionPos.blockToSectionCoord(pos.getX());
            var sectionY = SectionPos.blockToSectionCoord(pos.getY());
            var sectionZ = SectionPos.blockToSectionCoord(pos.getZ());
            var sectionKey = SectionPos.asLong(sectionX, sectionY, sectionZ);
            if (trackedSections.contains(sectionKey)) {
                return;
            }

            classifyExistingLogsInSectionAsUnknown(level, sectionX, sectionY, sectionZ);
            trackedSections.add(sectionKey);
            setDirty();
        }

        void initializeChunkIfNeeded(ServerLevel level, LevelChunk chunk) {
            var chunkX = chunk.getPos().x;
            var chunkZ = chunk.getPos().z;
            var changed = false;
            for (int sectionY = level.getMinSection(); sectionY < level.getMaxSection(); sectionY++) {
                var sectionKey = SectionPos.asLong(chunkX, sectionY, chunkZ);
                if (trackedSections.contains(sectionKey)) {
                    continue;
                }
                classifyExistingLogsInSectionAsUnknown(level, chunkX, sectionY, chunkZ);
                trackedSections.add(sectionKey);
                changed = true;
            }

            if (changed) {
                setDirty();
            }
        }

        private void classifyExistingLogsInSectionAsUnknown(ServerLevel level, int sectionX, int sectionY,
                int sectionZ) {
            var startX = sectionX << 4;
            var startY = sectionY << 4;
            var startZ = sectionZ << 4;
            for (int x = startX; x < startX + 16; x++) {
                for (int y = startY; y < startY + 16; y++) {
                    for (int z = startZ; z < startZ + 16; z++) {
                        var blockPos = new BlockPos(x, y, z);
                        if (level.getBlockState(blockPos).is(BlockTags.LOGS)) {
                            trackedLogs.putIfAbsent(blockPos.asLong(), Provenance.UNKNOWN.id());
                        }
                    }
                }
            }
        }

        void setProvenance(BlockPos pos, Provenance provenance) {
            trackedLogs.put(pos.asLong(), provenance.id());
            trackedSections.add(SectionPos.asLong(SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getY()), SectionPos.blockToSectionCoord(pos.getZ())));
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
