package appeng.idle.reward;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import appeng.core.AELog;
import appeng.idle.currency.CurrencyId;
import appeng.idle.currency.IdleCurrencyManager;

/**
 * Loads and serves reward definitions for idle progress advancement triggers.
 */
public final class IdleRewardManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final IdleRewardManager INSTANCE = new IdleRewardManager();

    private volatile Map<ResourceLocation, RewardDefinition> rewards = Map.of();
    private volatile Map<RewardTriggerType, List<RewardDefinition>> rewardsByTrigger = emptyTriggerMap();

    private IdleRewardManager() {
        super(GSON, "idle_reward");
    }

    public static @Nullable RewardDefinition get(ResourceLocation rewardId) {
        return INSTANCE.rewards.get(rewardId);
    }

    public static List<RewardDefinition> getByTrigger(RewardTriggerType trigger) {
        return INSTANCE.rewardsByTrigger.getOrDefault(trigger, List.of());
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsonEntries, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        var byId = new LinkedHashMap<ResourceLocation, RewardDefinition>();
        var byTrigger = new EnumMap<RewardTriggerType, List<RewardDefinition>>(RewardTriggerType.class);

        for (var entry : jsonEntries.entrySet()) {
            var sourceFile = entry.getKey();

            try {
                var json = GsonHelper.convertToJsonObject(entry.getValue(), "idle reward");
                var definition = parseDefinition(sourceFile, json);
                if (definition == null) {
                    continue;
                }

                var previous = byId.put(definition.id(), definition);
                if (previous == null) {
                    AELog.info("Loaded idle reward {} from datapack entry {}", definition.id(), sourceFile);
                } else {
                    AELog.info("Overrode idle reward {} from datapack entry {}", definition.id(), sourceFile);
                }
            } catch (Exception ex) {
                AELog.warn(ex, "Failed to load idle reward from " + sourceFile);
            }
        }

        for (var reward : byId.values()) {
            byTrigger.computeIfAbsent(reward.triggerType(), ignored -> new java.util.ArrayList<>())
                    .add(reward);
        }

        var immutableByTrigger = new EnumMap<RewardTriggerType, List<RewardDefinition>>(RewardTriggerType.class);
        for (var entry : byTrigger.entrySet()) {
            immutableByTrigger.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        rewards = Collections.unmodifiableMap(byId);
        rewardsByTrigger = Collections.unmodifiableMap(immutableByTrigger);

        AELog.info("Loaded {} idle reward definitions", rewards.size());
    }

    private static RewardDefinition parseDefinition(ResourceLocation sourceFile, JsonObject json) {
        var id = parseId(sourceFile, json);
        if (id == null) {
            return null;
        }

        var triggerType = RewardTriggerType.fromJson(GsonHelper.getAsString(json, "triggerType"));

        var currencyRaw = GsonHelper.getAsString(json, "currencyId");
        var currencyLocation = ResourceLocation.tryParse(currencyRaw);
        if (currencyLocation == null) {
            throw new JsonParseException("Invalid currencyId resource location: " + currencyRaw);
        }

        var currencyId = new CurrencyId(currencyLocation);
        if (IdleCurrencyManager.get(currencyId) == null) {
            throw new JsonParseException("Unknown currencyId: " + currencyRaw);
        }

        var progressTicksAwarded = GsonHelper.getAsLong(json, "progressTicksAwarded");
        if (progressTicksAwarded <= 0L) {
            throw new JsonParseException("progressTicksAwarded must be > 0");
        }

        var conditions = parseConditions(triggerType, json);
        var upgradeGate = parseUpgradeGate(json);
        var cooldownWindowTicks = parseOptionalPositiveLong(json, "cooldownWindowTicks");
        var environmentPredicate = parseEnvironmentPredicate(json);
        var caps = parseRewardCaps(json);

        return new RewardDefinition(id, triggerType, currencyId, progressTicksAwarded, conditions, upgradeGate,
                cooldownWindowTicks, environmentPredicate, caps);
    }

    private static @Nullable RewardDefinition.UpgradeGate parseUpgradeGate(JsonObject json) {
        if (!json.has("upgradeGateId")) {
            return null;
        }

        var gateId = parseOptionalResourceLocation(json, "upgradeGateId");
        if (gateId == null) {
            return null;
        }

        var minLevel = GsonHelper.getAsInt(json, "upgradeGateMinLevel", 1);
        if (minLevel <= 0) {
            throw new JsonParseException("upgradeGateMinLevel must be > 0");
        }

        return new RewardDefinition.UpgradeGate(gateId, minLevel);
    }

    private static @Nullable Long parseOptionalPositiveLong(JsonObject json, String fieldName) {
        if (!json.has(fieldName)) {
            return null;
        }

        var value = GsonHelper.getAsLong(json, fieldName);
        if (value <= 0L) {
            throw new JsonParseException(fieldName + " must be > 0");
        }

        return value;
    }

    private static @Nullable Integer parseOptionalPositiveInt(JsonObject json, String fieldName) {
        if (!json.has(fieldName)) {
            return null;
        }

        var value = GsonHelper.getAsInt(json, fieldName);
        if (value <= 0) {
            throw new JsonParseException(fieldName + " must be > 0");
        }

        return value;
    }

    private static @Nullable RewardDefinition.EnvironmentPredicate parseEnvironmentPredicate(JsonObject json) {
        if (!json.has("environment")) {
            return null;
        }

        var environment = GsonHelper.getAsJsonObject(json, "environment");
        var dimensionId = parseOptionalResourceLocation(environment, "dimension");
        var biomeId = parseOptionalResourceLocation(environment, "biome");
        var biomeTagId = parseOptionalResourceLocation(environment, "biomeTag");
        if (dimensionId == null && biomeId == null && biomeTagId == null) {
            return null;
        }

        return new RewardDefinition.EnvironmentPredicate(dimensionId, biomeId, biomeTagId);
    }

    private static @Nullable RewardDefinition.RewardCaps parseRewardCaps(JsonObject json) {
        if (!json.has("caps")) {
            return null;
        }

        var caps = GsonHelper.getAsJsonObject(json, "caps");
        var dailyCap = parseOptionalPositiveInt(caps, "daily");
        var intervalCap = parseOptionalPositiveInt(caps, "interval");
        var intervalWindowTicks = parseOptionalPositiveLong(caps, "intervalWindowTicks");
        if (dailyCap == null && intervalCap == null && intervalWindowTicks == null) {
            return null;
        }

        return new RewardDefinition.RewardCaps(dailyCap, intervalCap, intervalWindowTicks);
    }

    private static @Nullable RewardDefinition.BlockFilterCondition parseConditions(RewardTriggerType triggerType,
            JsonObject json) {
        if (!json.has("conditions")) {
            if (triggerType == RewardTriggerType.BLOCK_BREAK) {
                throw new JsonParseException(
                        "Missing required 'conditions' object for BLOCK_BREAK rewards (must define block or tag)");
            }
            return null;
        }

        var conditionsJson = GsonHelper.getAsJsonObject(json, "conditions");
        return switch (triggerType) {
            case BLOCK_BREAK -> {
                var blockId = parseOptionalResourceLocation(conditionsJson, "block");
                var blockTagId = parseOptionalResourceLocation(conditionsJson, "tag");
                if (blockId == null && blockTagId == null) {
                    throw new JsonParseException(
                            "BLOCK_BREAK reward conditions must define at least one of 'block' or 'tag'");
                }

                yield new RewardDefinition.BlockFilterCondition(blockId, blockTagId);
            }
        };
    }

    private static @Nullable ResourceLocation parseOptionalResourceLocation(JsonObject json, String fieldName) {
        if (!json.has(fieldName)) {
            return null;
        }

        var raw = GsonHelper.getAsString(json, fieldName, "").trim();
        if (raw.isEmpty()) {
            return null;
        }

        var value = ResourceLocation.tryParse(raw);
        if (value == null) {
            throw new JsonParseException("Invalid resource location for '" + fieldName + "': " + raw);
        }

        return value;
    }

    private static @Nullable ResourceLocation parseId(ResourceLocation sourceFile, JsonObject json) {
        var rawId = GsonHelper.getAsString(json, "id", "").trim();
        if (rawId.isEmpty()) {
            AELog.warn("Rejected idle reward definition from {} because it is missing a non-empty 'id' field",
                    sourceFile);
            return null;
        }

        var id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            AELog.warn("Rejected idle reward definition from {} because '{}' is not a valid resource location",
                    sourceFile, rawId);
            return null;
        }

        return id;
    }

    private static Map<RewardTriggerType, List<RewardDefinition>> emptyTriggerMap() {
        var map = new EnumMap<RewardTriggerType, List<RewardDefinition>>(RewardTriggerType.class);
        for (var triggerType : RewardTriggerType.values()) {
            map.put(triggerType, List.of());
        }
        return Collections.unmodifiableMap(map);
    }
}
