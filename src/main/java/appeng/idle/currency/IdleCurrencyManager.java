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

import appeng.core.AELog;

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

        var baseOnlineRate = GsonHelper.getAsDouble(json, "baseOnlineRate");
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
            return new CurrencyDefinition(id, displayNameKey, iconItem, baseOnlineRate, visibleByDefault, caps);
        } catch (IllegalArgumentException ex) {
            throw new JsonParseException(ex.getMessage());
        }
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
