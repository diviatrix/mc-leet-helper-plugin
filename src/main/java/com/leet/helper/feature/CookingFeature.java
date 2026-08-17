package com.leet.helper.feature;

import com.leet.helper.Core;
import com.leet.helper.feature.skills.SkillsFeature;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The cooking feature: a set of custom food items and the crafting recipes that
 * produce them, plus a "second" way to make vanilla bread. Every recipe is
 * gated per-player by the {@code cook} skill's level (each Cook level unlocks
 * exactly one recipe), so a recipe only crafts once the player has leveled the
 * skill. Dish items are placed on an edible base material and reapply custom
 * hunger/saturation when eaten (they restore more than their raw parts).
 *
 * Recipes are registered as {@link Recipe}s so they appear in the vanilla
 * recipe book; {@link PrepareItemCraftEvent}/{@link CraftItemEvent} blank/cancel
 * them for players whose Cook level (or the cooking permission) is insufficient.
 */
public final class CookingFeature extends AbstractFeature {

    /** A single custom item produced (or, for Dough, consumed) by a recipe. */
    private record ItemDef(String id, String name, Material material, List<String> lore, int hunger, int saturation) {
        boolean isFood() {
            return hunger > 0;
        }
    }

    /** A parsed recipe awaiting registration: level gate + crafting definition. */
    private record RecipeDef(
        String id, int level, int amount, ItemStack result,
        List<RecipeChoice> shapeless, String[] shape, Map<Character, RecipeChoice> shaped,
        List<String> ingredients) {
    }

    /** Read-only view of a recipe for the skill GUI (result icon + ingredient names + dish effect). */
    public record RecipeView(int level, ItemStack result, List<String> ingredients, int hunger, int saturation) {
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Map<String, ItemDef> items = new LinkedHashMap<>();
    private final Map<String, RecipeDef> recipes = new LinkedHashMap<>();
    /** Maps the registered (unique) recipe key back to its definition for gating. */
    private final Map<NamespacedKey, RecipeDef> recipesByKey = new HashMap<>();
    private final List<NamespacedKey> registeredKeys = new ArrayList<>();

    private final NamespacedKey ciKey; // custom-item id tag on every crafted food/ingredient

    /** The skill id whose level gates each recipe (defined in the skills feature). */
    public static final String COOK_SKILL = "cook";

    public CookingFeature(Core plugin) {
        super(plugin);
        this.ciKey = new NamespacedKey(plugin, "ci");
    }

    @Override
    public String featureId() {
        return "cooking";
    }

    @Override
    protected void loadFeatureConfig(YamlConfiguration cfg) {
        items.clear();
        recipes.clear();

        ConfigurationSection itemSection = cfg.getConfigurationSection("feature.items");
        if (itemSection != null) {
            for (String itemId : itemSection.getKeys(false)) {
                ConfigurationSection s = itemSection.getConfigurationSection(itemId);
                if (s == null) continue;
                Material material = parseMaterial(s.getString("material", "STONE"), itemId, "material");
                if (material == null) continue;
                items.put(itemId.toLowerCase(), new ItemDef(
                    itemId.toLowerCase(),
                    s.getString("name", itemId),
                    material,
                    s.getStringList("lore"),
                    Math.max(0, s.getInt("hunger", 0)),
                    Math.max(0, s.getInt("saturation", 0))));
            }
        }

        ConfigurationSection recipeSection = cfg.getConfigurationSection("feature.recipes");
        if (recipeSection != null) {
            for (String recipeId : recipeSection.getKeys(false)) {
                ConfigurationSection s = recipeSection.getConfigurationSection(recipeId);
                if (s == null) continue;
                RecipeDef def = readRecipe(recipeId, s);
                if (def != null) recipes.put(recipeId.toLowerCase(), def);
            }
        }
    }

    private RecipeDef readRecipe(String recipeId, ConfigurationSection s) {
        int level = Math.max(1, s.getInt("level", 1));
        int amount = Math.max(1, s.getInt("amount", 1));
        ItemStack result = parseResult(s.getString("result"), amount);
        String type = s.getString("type", "SHAPELESS").toUpperCase();

        if ("SHAPED".equals(type)) {
            List<String> shapeRows = s.getStringList("shape");
            if (shapeRows.size() != 3) {
                plugin.getLogger().warning("Cooking recipe " + recipeId + ": shaped recipes need 3 'shape' rows; skipped.");
                return null;
            }
            ConfigurationSection ing = s.getConfigurationSection("ingredients");
            if (ing == null) {
                plugin.getLogger().warning("Cooking recipe " + recipeId + ": missing 'ingredients' for shaped recipe; skipped.");
                return null;
            }
            String[] shape = new String[3];
            Map<Character, RecipeChoice> shaped = new HashMap<>();
            List<String> ingredientLabels = new ArrayList<>();
            for (int r = 0; r < 3; r++) {
                String row = shapeRows.get(r);
                if (row.length() != 3) {
                    plugin.getLogger().warning("Cooking recipe " + recipeId + ": shape rows must each be 3 wide; skipped.");
                    return null;
                }
                shape[r] = row;
                for (int c = 0; c < 3; c++) {
                    char key = row.charAt(c);
                    if (key == ' ' || shaped.containsKey(key)) continue;
                    String raw = ing.getString(String.valueOf(key));
                    RecipeChoice choice = spec(raw);
                    if (choice == null) {
                        plugin.getLogger().warning("Cooking recipe " + recipeId + ": invalid ingredient for '" + key + "'; skipped.");
                        return null;
                    }
                    shaped.put(key, choice);
                    ingredientLabels.add(ingredientLabel(raw));
                }
            }
            if (result == null) return null;
            return new RecipeDef(recipeId.toLowerCase(), level, amount, result, null, shape, shaped, ingredientLabels);
        }

        // SHAPELESS
        List<RecipeChoice> shapeless = new ArrayList<>();
        List<String> ingredientLabels = new ArrayList<>();
        for (String raw : s.getStringList("ingredients")) {
            RecipeChoice choice = spec(raw);
            if (choice == null) {
                plugin.getLogger().warning("Cooking recipe " + recipeId + ": invalid ingredient '" + raw + "'; skipped.");
                return null;
            }
            shapeless.add(choice);
            ingredientLabels.add(ingredientLabel(raw));
        }
        if (shapeless.isEmpty() || result == null) return null;
        return new RecipeDef(recipeId.toLowerCase(), level, amount, result, shapeless, null, null, ingredientLabels);
    }

    /** Human-readable name of an ingredient: a vanilla material or a custom item. */
    private String ingredientLabel(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        try {
            return friendly(Material.valueOf(raw.toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            // fall through to custom item name
        }
        ItemDef item = items.get(raw.toLowerCase());
        return item != null ? item.name() : raw;
    }

    /** "COOKED_BEEF" -> "Cooked Beef" (for ingredient hover lines). */
    private static String friendly(Material material) {
        String[] words = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    /** One view per recipe, in config order, for the Cook skill's detail screen. */
    public List<RecipeView> recipeViews() {
        List<RecipeView> views = new ArrayList<>();
        for (RecipeDef def : recipes.values()) {
            int hunger = 0;
            int saturation = 0;
            // Custom dish results carry their item id in the PDC tag; pull the
            // configured effect so the GUI can show it once the recipe is learnt.
            ItemMeta resultMeta = def.result().getItemMeta();
            if (resultMeta != null) {
                String itemId = resultMeta.getPersistentDataContainer().get(ciKey, PersistentDataType.STRING);
                if (itemId != null) {
                    ItemDef itemDef = items.get(itemId);
                    if (itemDef != null) {
                        hunger = itemDef.hunger();
                        saturation = itemDef.saturation();
                    }
                }
            }
            views.add(new RecipeView(def.level(), def.result().clone(), List.copyOf(def.ingredients()),
                hunger, saturation));
        }
        return views;
    }

    /** A single crafting ingredient: a vanilla Material, or a custom item id (ExactChoice). */
    private RecipeChoice spec(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            return new RecipeChoice.MaterialChoice(Material.valueOf(raw.toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            // not a vanilla material -> fall through to custom item lookup
        }
        ItemDef item = items.get(raw.toLowerCase());
        return item == null ? null : new RecipeChoice.ExactChoice(item(item));
    }

    /** A recipe result: `material:<MATERIAL>` for vanilla output, otherwise a custom item id. */
    private ItemStack parseResult(String raw, int amount) {
        if (raw == null || raw.isEmpty()) return null;
        if (raw.regionMatches(true, 0, "material:", 0, "material:".length())) {
            String mat = raw.substring("material:".length());
            try {
                return new ItemStack(Material.valueOf(mat.toUpperCase()), amount);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Cooking recipe result: invalid material '" + mat + "'.");
                return null;
            }
        }
        ItemDef item = items.get(raw.toLowerCase());
        if (item == null) {
            plugin.getLogger().warning("Cooking recipe result: unknown custom item '" + raw + "'.");
            return null;
        }
        ItemStack stack = item(item);
        stack.setAmount(amount);
        return stack;
    }

    private Material parseMaterial(String name, String itemId, String key) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material in cooking item " + itemId + "." + key + ": " + name);
            return null;
        }
    }

    /** Builds the item the player actually holds: base material + name/lore + custom-id PDC tag. */
    private ItemStack item(ItemDef def) {
        ItemStack stack = new ItemStack(def.material());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MM.deserialize("<yellow>" + def.name()));
        if (!def.lore().isEmpty()) {
            meta.lore(def.lore().stream().map(l -> MM.deserialize("<gray>" + l)).toList());
        }
        meta.getPersistentDataContainer().set(ciKey, PersistentDataType.STRING, def.id());
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public void enable() {
        super.enable();
        if (enabled) {
            registerRecipes();
        }
    }

    @Override
    public void disable() {
        unregisterRecipes();
        super.disable();
    }

    private void registerRecipes() {
        for (RecipeDef def : recipes.values()) {
            NamespacedKey key = new NamespacedKey(plugin, "cooking/" + def.id());
            Recipe recipe;
            if (def.shape() != null) {
                ShapedRecipe shaped = new ShapedRecipe(key, def.result());
                shaped.shape(def.shape());
                for (Map.Entry<Character, RecipeChoice> e : def.shaped().entrySet()) {
                    shaped.setIngredient(e.getKey(), e.getValue());
                }
                recipe = shaped;
            } else {
                ShapelessRecipe shapeless = new ShapelessRecipe(key, def.result());
                for (RecipeChoice choice : def.shapeless()) {
                    shapeless.addIngredient(choice);
                }
                recipe = shapeless;
            }
            if (Bukkit.addRecipe(recipe)) {
                recipesByKey.put(key, def);
                registeredKeys.add(key);
            } else {
                plugin.getLogger().warning("Failed to register cooking recipe: " + def.id());
            }
        }
    }

    private void unregisterRecipes() {
        for (NamespacedKey key : registeredKeys) {
            Bukkit.removeRecipe(key);
        }
        registeredKeys.clear();
        recipesByKey.clear();
    }

    // --- per-player gating: a recipe only crafts at / above its Cook level ---

    /** The player's current "cook" skill level (0 when the skills feature is absent). */
    private int cookLevel(Player player) {
        AbstractFeature skills = plugin.featureManager().get("skills").orElse(null);
        return skills instanceof SkillsFeature skillsFeature ? skillsFeature.currentLevel(player, COOK_SKILL) : 0;
    }

    /** True when this player may craft the given recipe (permission + Cook level). */
    private boolean canCraft(Player player, RecipeDef def) {
        if (!check(player)) return false;
        return cookLevel(player) >= def.level();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPrepare(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        RecipeDef def = recipeDef(recipe);
        if (def == null) return;
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        if (!canCraft(player, def)) {
            event.getInventory().setResult(null); // recipe shows as "no result"/uncraftable until unlocked
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        RecipeDef def = recipeDef(event.getRecipe());
        if (def == null) return;
        if (canCraft(player, def)) return;
        event.setCancelled(true);
        sendMessage(player, "recipe-locked", "<level>", String.valueOf(def.level()));
    }

    private RecipeDef recipeDef(Recipe recipe) {
        if (recipe instanceof Keyed keyed) {
            return recipesByKey.get(keyed.getKey());
        }
        return null;
    }

    // --- custom nutrition on eat: dishes restore more than their raw parts ---

    @EventHandler(priority = EventPriority.NORMAL)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!check(player)) return;
        ItemStack item = event.getItem();
        if (item.getType() == Material.AIR) return;
        String meta = item.getItemMeta() == null ? null
            : item.getItemMeta().getPersistentDataContainer().get(ciKey, PersistentDataType.STRING);
        if (meta == null) return;
        ItemDef def = items.get(meta);
        if (def == null || !def.isFood()) return;

        // Cancel the base material's own nutrition and apply the dish's values.
        event.setCancelled(true);
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + def.hunger()));
        player.setSaturation(Math.min(20, player.getSaturation() + def.saturation()));

        // Consume one matching item from the player's inventory.
        for (var entry : player.getInventory().all(item.getType()).entrySet()) {
            ItemStack held = entry.getValue();
            if (held.isSimilar(item)) {
                int amount = held.getAmount();
                if (amount <= 1) {
                    player.getInventory().setItem(entry.getKey(), null);
                } else {
                    held.setAmount(amount - 1);
                }
                break;
            }
        }
    }
}