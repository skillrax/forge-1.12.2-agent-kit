package kit.gametest;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

/**
 * Cross-checks what the mod actually registered against its resources. The dedicated server
 * never loads models, textures or translations, so these mistakes are otherwise invisible
 * until someone opens the game client.
 */
final class AssetLint {
    private static final Set<String> IGNORED = new HashSet<>(Arrays.asList("minecraft", "forge", "fml", "mcp", "kitharness"));
    private final MinecraftServer server;
    private final JsonArray issues = new JsonArray();
    private final Map<String, Map<String, String>> langs = new HashMap<>();
    private final Set<String> checkedModels = new HashSet<>();
    private final Set<String> reportedKeys = new HashSet<>();
    private final boolean vanillaAssets = exists("assets/minecraft/models/block/cube_all.json");
    private Set<String> modIds;

    AssetLint(MinecraftServer server) { this.server = server; }

    JsonObject run() {
        modIds = new TreeSet<>();
        for (ModContainer mod : Loader.instance().getActiveModList())
            if (!IGNORED.contains(mod.getModId().toLowerCase(Locale.ROOT))) modIds.add(mod.getModId());
        JsonObject counts = new JsonObject();
        Set<String> namespaces = new TreeSet<>(modIds);
        counts.addProperty("blocks", count(ForgeRegistries.BLOCKS, namespaces));
        counts.addProperty("items", count(ForgeRegistries.ITEMS, namespaces));
        counts.addProperty("entities", count(ForgeRegistries.ENTITIES, namespaces));
        counts.addProperty("recipes", count(ForgeRegistries.RECIPES, namespaces));
        counts.addProperty("sounds", count(ForgeRegistries.SOUND_EVENTS, namespaces));
        counts.addProperty("enchantments", count(ForgeRegistries.ENCHANTMENTS, namespaces));
        counts.addProperty("potions", count(ForgeRegistries.POTIONS, namespaces));
        counts.addProperty("biomes", count(ForgeRegistries.BIOMES, namespaces));
        for (String ns : namespaces)
            if (!modIds.contains(ns)) warn("namespace " + ns, "registry entries use namespace '" + ns + "', which is not a loaded mod id; use your modid in setRegistryName");
        for (String ns : modIds) lang(ns);

        // One broken entry must not hide the others.
        for (Block block : ForgeRegistries.BLOCKS) if (mine(block)) guard("block " + block.getRegistryName(), () -> block(block));
        for (Item item : ForgeRegistries.ITEMS) if (mine(item)) guard("item " + item.getRegistryName(), () -> item(item));
        for (EntityEntry entry : ForgeRegistries.ENTITIES) if (mine(entry)) guard("entity " + entry.getRegistryName(),
                () -> langKey(entry.getRegistryName().getNamespace(), "entity." + entry.getName() + ".name", "entity " + entry.getRegistryName()));
        for (SoundEvent sound : ForgeRegistries.SOUND_EVENTS) if (mine(sound)) guard("sound " + sound.getRegistryName(), () -> sound(sound));

        JsonObject report = new JsonObject();
        JsonArray mods = new JsonArray();
        for (String id : modIds) mods.add(new JsonPrimitive(id));
        report.add("mods", mods);
        report.add("registered", counts);
        report.addProperty("vanillaAssetsChecked", vanillaAssets);
        report.add("lint", issues);
        return report;
    }

    // ---- registry entries --------------------------------------------------------------

    private void block(Block block) {
        ResourceLocation id = block.getRegistryName();
        String subject = "block " + id;
        String path = "assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json";
        JsonObject states = json(path, subject);
        if (states == null) {
            if (!exists(path)) warn(subject, "missing " + path + " (block renders as missing model)");
        } else if (states.has("forge_marker")) {
            forgeBlockstate(states, id.getNamespace(), subject);
        } else if (states.has("variants")) {
            JsonObject variants = states.getAsJsonObject("variants");
            List<String> missing = new ArrayList<>();
            for (IBlockState state : block.getBlockState().getValidStates()) {
                String variant = variantName(state);
                if (!variants.has(variant) && missing.size() < 4) missing.add(variant);
            }
            if (!missing.isEmpty())
                warn(subject, path + " lacks variant(s) " + missing + "; add them, or ignore unused properties with a client StateMapper");
            for (Map.Entry<String, JsonElement> variant : variants.entrySet()) variantModels(variant.getValue(), id.getNamespace(), subject);
        } else if (states.has("multipart")) {
            for (JsonElement part : states.getAsJsonArray("multipart"))
                if (part.isJsonObject() && part.getAsJsonObject().has("apply")) variantModels(part.getAsJsonObject().get("apply"), id.getNamespace(), subject);
        } else {
            warn(subject, path + " has neither \"variants\", \"multipart\" nor \"forge_marker\"");
        }
        langKey(id.getNamespace(), block.getTranslationKey() + ".name", subject);
        IBlockState state = block.getDefaultState();
        try {
            if (block.hasTileEntity(state)) {
                TileEntity tile = block.createTileEntity(server.getWorld(0), state);
                if (tile == null) warn(subject, "hasTileEntity is true but createTileEntity returned null");
                else if (TileEntity.getKey(tile.getClass()) == null)
                    error(subject, "tile entity " + tile.getClass().getName() + " is not registered; call GameRegistry.registerTileEntity(" + tile.getClass().getSimpleName() + ".class, new ResourceLocation(MODID, \"...\")) (unregistered tiles crash on world save)");
            }
        } catch (Throwable error) {
            warn(subject, "could not create its tile entity for inspection: " + error);
        }
    }

    private void item(Item item) {
        ResourceLocation id = item.getRegistryName();
        String subject = "item " + id;
        List<ItemStack> stacks = new ArrayList<>();
        try {
            if (item.getHasSubtypes()) {
                NonNullList<ItemStack> list = NonNullList.create();
                item.getSubItems(CreativeTabs.SEARCH, list);
                for (ItemStack stack : list) if (stacks.size() < 16) stacks.add(stack);
            }
        } catch (Throwable ignored) { }
        if (stacks.isEmpty()) stacks.add(new ItemStack(item));
        for (ItemStack stack : stacks) langKey(id.getNamespace(), item.getTranslationKey(stack) + ".name", subject);

        String itemModel = "assets/" + id.getNamespace() + "/models/item/" + id.getPath() + ".json";
        String blockstate = "assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json";
        if (exists(itemModel)) model(id.getNamespace() + ":item/" + id.getPath(), subject);
        else if (item instanceof ItemBlock && exists(blockstate)) {
            // The item model loader falls back to the blockstate's "inventory" variant.
            JsonObject states = json(blockstate, subject);
            boolean inventory = states != null && states.has("variants") && states.getAsJsonObject("variants").has("inventory");
            if (!inventory) warn(subject, "missing " + itemModel + " and the blockstate has no \"inventory\" variant (item shows as missing texture); add the item model with parent \"" + id.getNamespace() + ":block/" + id.getPath() + "\"");
        } else {
            warn(subject, "missing " + itemModel + " (item shows as missing texture)");
        }
    }

    private void sound(SoundEvent sound) {
        ResourceLocation id = sound.getRegistryName();
        String path = "assets/" + id.getNamespace() + "/sounds.json";
        JsonObject sounds = json(path, "sound " + id);
        // SoundEvent.getSoundName is client-only; mods register sounds under the same name.
        ResourceLocation name = id;
        if (sounds == null) { if (!exists(path)) warn("sound " + id, "missing " + path); return; }
        if (!sounds.has(name.getPath())) { warn("sound " + id, path + " has no entry \"" + name.getPath() + "\""); return; }
        JsonElement entry = sounds.get(name.getPath());
        JsonArray files = entry.isJsonObject() && entry.getAsJsonObject().has("sounds") ? entry.getAsJsonObject().getAsJsonArray("sounds") : new JsonArray();
        for (JsonElement file : files) {
            String fileName = file.isJsonObject() ? file.getAsJsonObject().get("name").getAsString() : file.getAsString();
            ResourceLocation location = new ResourceLocation(fileName);
            String ogg = "assets/" + location.getNamespace() + "/sounds/" + location.getPath() + ".ogg";
            // Vanilla .ogg files come from the asset index, not the client jar, so they cannot be checked here.
            if (!location.getNamespace().equals("minecraft") && !exists(ogg)) warn("sound " + id, "missing " + ogg);
        }
    }

    // ---- models -------------------------------------------------------------------------

    private void forgeBlockstate(JsonObject states, String ns, String subject) {
        if (states.has("defaults") && states.getAsJsonObject("defaults").has("model"))
            model(qualify(states.getAsJsonObject("defaults").get("model").getAsString(), ns, "block/"), subject);
    }

    private void variantModels(JsonElement variant, String ns, String subject) {
        if (variant.isJsonArray()) { for (JsonElement option : variant.getAsJsonArray()) variantModels(option, ns, subject); return; }
        if (variant.isJsonObject() && variant.getAsJsonObject().has("model"))
            model(qualify(variant.getAsJsonObject().get("model").getAsString(), "minecraft", "block/"), subject);
    }

    /** Blockstate variant models are relative to models/block and default to the minecraft namespace. */
    private static String qualify(String model, String defaultNs, String prefix) {
        ResourceLocation location = new ResourceLocation(model.contains(":") ? model : defaultNs + ":" + model);
        return location.getNamespace() + ":" + prefix + location.getPath();
    }

    private void model(String location, String subject) {
        for (int depth = 0; depth < 10 && location != null; depth++) {
            if (!checkedModels.add(location)) return;
            if (location.startsWith("minecraft:builtin/") || location.startsWith("builtin/")) return;
            ResourceLocation id = new ResourceLocation(location);
            if (id.getNamespace().equals("minecraft") && !vanillaAssets) return;
            String path = "assets/" + id.getNamespace() + "/models/" + id.getPath() + ".json";
            if (!exists(path)) {
                warn(subject, "model " + location + " does not exist (" + path + ")" + hintOwnNamespace(id, "models/", ".json"));
                return;
            }
            JsonObject model = json(path, subject);
            if (model == null) return;
            if (model.has("textures") && model.get("textures").isJsonObject()) {
                for (Map.Entry<String, JsonElement> texture : model.getAsJsonObject("textures").entrySet()) {
                    String value = texture.getValue().getAsString();
                    if (value.startsWith("#")) continue;
                    if (value.contains("textures/") || value.endsWith(".png")) {
                        warn(subject, path + " texture \"" + value + "\" must be written as \"modid:blocks/name\" or \"modid:items/name\" (no textures/ prefix, no .png)");
                        continue;
                    }
                    ResourceLocation tex = new ResourceLocation(value);
                    if (tex.getNamespace().equals("minecraft") && !vanillaAssets) continue;
                    String png = "assets/" + tex.getNamespace() + "/textures/" + tex.getPath() + ".png";
                    if (!exists(png)) warn(subject, path + " texture \"" + value + "\" not found (" + png + ")" + hintOwnNamespace(tex, "textures/", ".png"));
                }
            }
            location = model.has("parent") ? model.get("parent").getAsString() : null;
            if (location != null && !location.contains(":")) location = "minecraft:" + location;
        }
    }

    /** A bare path resolves to minecraft:, which is the classic missing-modid mistake. */
    private String hintOwnNamespace(ResourceLocation id, String folder, String extension) {
        if (!id.getNamespace().equals("minecraft")) return "";
        for (String ns : modIds)
            if (exists("assets/" + ns + "/" + folder + id.getPath() + extension)) return "; did you mean \"" + ns + ":" + id.getPath() + "\"?";
        return "";
    }

    // ---- language -----------------------------------------------------------------------

    private Map<String, String> lang(String ns) {
        return langs.computeIfAbsent(ns, key -> {
            Map<String, String> entries = new HashMap<>();
            String path = "assets/" + key + "/lang/en_us.lang";
            String text = read(path);
            if (text == null) {
                if (exists("assets/" + key + "/lang/en_US.lang")) warn("lang " + key, "rename lang/en_US.lang to lang/en_us.lang (1.12 resource packs use lowercase names)");
                return entries;
            }
            for (String line : text.split("\\r?\\n")) {
                int split = line.indexOf('=');
                if (split > 0 && !line.startsWith("#")) entries.put(line.substring(0, split).trim(), line.substring(split + 1));
            }
            return entries;
        });
    }

    private void langKey(String ns, String key, String subject) {
        if (!reportedKeys.add(key)) return;
        Map<String, String> entries = lang(ns);
        if (!entries.containsKey(key))
            warn(subject, "missing translation \"" + key + "=...\" in assets/" + ns + "/lang/en_us.lang (shows raw key in game)");
    }

    // ---- helpers ------------------------------------------------------------------------

    private static String variantName(IBlockState state) {
        StringBuilder name = new StringBuilder();
        for (Map.Entry<IProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
            if (name.length() > 0) name.append(',');
            name.append(entry.getKey().getName()).append('=').append(valueName(entry.getKey(), entry.getValue()));
        }
        return name.length() == 0 ? "normal" : name.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueName(IProperty property, Comparable value) { return property.getName(value); }

    private static <T extends IForgeRegistryEntry<T>> int count(IForgeRegistry<T> registry, Set<String> namespaces) {
        int count = 0;
        for (T entry : registry) {
            String ns = entry.getRegistryName().getNamespace();
            if (!IGNORED.contains(ns)) { namespaces.add(ns); count++; }
        }
        return count;
    }

    private boolean mine(IForgeRegistryEntry<?> entry) { return !IGNORED.contains(entry.getRegistryName().getNamespace()); }

    private static boolean exists(String path) { return AssetLint.class.getClassLoader().getResource(path) != null; }

    private static String read(String path) {
        InputStream stream = AssetLint.class.getClassLoader().getResourceAsStream(path);
        if (stream == null) return null;
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[4096];
            for (int read; (read = reader.read(buffer)) > 0; ) text.append(buffer, 0, read);
            return text.toString();
        } catch (IOException error) { return null; }
    }

    private JsonObject json(String path, String subject) {
        String text = read(path);
        if (text == null) return null;
        try {
            JsonElement parsed = new JsonParser().parse(text);
            if (parsed.isJsonObject()) return parsed.getAsJsonObject();
            warn(subject, path + " is not a JSON object");
        } catch (JsonParseException error) {
            error(subject, path + " is invalid JSON: " + error.getMessage());
        }
        return null;
    }

    private void guard(String subject, Runnable check) {
        try { check.run(); }
        catch (Throwable error) { warn(subject, "could not be inspected: " + error); }
    }

    private void warn(String subject, String message) { add("WARN", subject, message); }
    private void error(String subject, String message) { add("ERROR", subject, message); }
    private void add(String level, String subject, String message) {
        if (issues.size() >= 200) return;
        JsonObject issue = new JsonObject();
        issue.addProperty("level", level);
        issue.addProperty("subject", subject);
        issue.addProperty("message", message);
        issues.add(issue);
        System.out.println("KIT_LINT " + level + " " + subject + ": " + message);
    }
}
