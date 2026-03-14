package appeng.idle.currency;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import appeng.core.AELog;
import appeng.idle.net.IdleCurrencySyncService;

/**
 * Loads and serves idle currency definitions from built-ins plus datapack JSON files.
 */
public final class IdleCurrencyManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final IdleCurrencyManager INSTANCE = new IdleCurrencyManager();

    private volatile Map<CurrencyId, CurrencyDefinition> currencies = loadBuiltIns();

    private IdleCurrencyManager() {
        super(GSON, "idle_currency");
    }

    public static Map<CurrencyId, CurrencyDefinition> getCurrencies() {
        return INSTANCE.currencies;
    }

    public static CurrencyDefinition get(CurrencyId id) {
        return INSTANCE.currencies.get(id);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsonEntries, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        var merged = new LinkedHashMap<>(loadBuiltIns());

        for (var entry : jsonEntries.entrySet()) {
            var sourceFile = entry.getKey();

            try {
                var json = GsonHelper.convertToJsonObject(entry.getValue(), "currency");
                var definition = parseDefinition(sourceFile, json);
                if (definition == null) {
                    continue;
                }

                var previous = merged.put(definition.id(), definition);
                if (previous == null) {
                    AELog.info("Loaded idle currency {} from datapack entry {}", definition.id().id(), sourceFile);
                } else {
                    AELog.info("Overrode idle currency {} from datapack entry {}", definition.id().id(), sourceFile);
                }
            } catch (Exception ex) {
                AELog.warn(ex, "Failed to load idle currency from " + sourceFile);
            }
        }

        currencies = Collections.unmodifiableMap(merged);
        AELog.info("Loaded {} idle currency definitions (including built-ins)", currencies.size());

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (var player : server.getPlayerList().getPlayers()) {
                IdleCurrencySyncService.sendSnapshot(player);
            }
        }
    }

    private static Map<CurrencyId, CurrencyDefinition> loadBuiltIns() {
        var map = new LinkedHashMap<CurrencyId, CurrencyDefinition>();
        for (var definition : IdleCurrencies.defaults()) {
            map.put(definition.id(), definition);
        }
        return Collections.unmodifiableMap(map);
    }

    private static CurrencyDefinition parseDefinition(ResourceLocation sourceFile, JsonObject json) {
        var id = parseCurrencyId(sourceFile, json);
        if (id == null) {
            return null;
        }

        var displayNameKey = GsonHelper.getAsString(json, "displayNameKey");

        var iconItemString = GsonHelper.getAsString(json, "iconItem");
        var iconItem = ResourceLocation.tryParse(iconItemString);
        if (iconItem == null) {
            throw new JsonParseException("Invalid iconItem resource location: " + iconItemString);
        }

        var baseTicksPerUnit = parseBaseTicksPerUnit(sourceFile, json);
        var visibleByDefault = GsonHelper.getAsBoolean(json, "visibleByDefault", true);

        CurrencyDefinition.CurrencyCaps caps = null;
        if (json.has("caps")) {
            var capsJson = GsonHelper.getAsJsonObject(json, "caps");
            Long onlineGenerationCap = capsJson.has("onlineGenerationCap")
                    ? GsonHelper.getAsLong(capsJson, "onlineGenerationCap")
                    : null;
            Long balanceCap = capsJson.has("balanceCap") ? GsonHelper.getAsLong(capsJson, "balanceCap") : null;
            caps = new CurrencyDefinition.CurrencyCaps(onlineGenerationCap, balanceCap);
        }

        try {
            return new CurrencyDefinition(id, displayNameKey, iconItem, baseTicksPerUnit, visibleByDefault, caps);
        } catch (IllegalArgumentException ex) {
            throw new JsonParseException(ex.getMessage());
        }
    }

    private static long parseBaseTicksPerUnit(ResourceLocation sourceFile, JsonObject json) {
        if (json.has("baseTicksPerUnit")) {
            return GsonHelper.getAsLong(json, "baseTicksPerUnit");
        }

        if (json.has("baseOnlineRate")) {
            var baseOnlineRate = GsonHelper.getAsDouble(json, "baseOnlineRate");
            if (!Double.isFinite(baseOnlineRate) || baseOnlineRate <= 0.0) {
                throw new JsonParseException("baseOnlineRate must be finite and > 0 when used as fallback");
            }

            var baseTicksPerUnit = Math.round(1.0 / baseOnlineRate);
            if (baseTicksPerUnit < 1L) {
                baseTicksPerUnit = 1L;
            }

            AELog.warn(
                    "Idle currency {} uses deprecated field 'baseOnlineRate'; converted to baseTicksPerUnit={} (remove baseOnlineRate and set baseTicksPerUnit explicitly)",
                    sourceFile, baseTicksPerUnit);
            return baseTicksPerUnit;
        }

        throw new JsonParseException(
                "Missing required field 'baseTicksPerUnit' (or deprecated fallback 'baseOnlineRate')");
    }

    private static CurrencyId parseCurrencyId(ResourceLocation sourceFile, JsonObject json) {
        var rawId = GsonHelper.getAsString(json, "id", "").trim();
        if (rawId.isEmpty()) {
            AELog.warn("Rejected idle currency definition from {} because it is missing a non-empty 'id' field",
                    sourceFile);
            return null;
        }

        var location = ResourceLocation.tryParse(rawId);
        if (location == null) {
            AELog.warn("Rejected idle currency definition from {} because '{}' is not a valid resource location",
                    sourceFile, rawId);
            return null;
        }

        return new CurrencyId(location);
    }
}
