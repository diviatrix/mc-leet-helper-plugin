package com.leet.crafting.craft;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Generic crafting-recipe engine: parses SHAPED/SHAPELESS recipes from a
 * {@code feature.recipes} section and registers/unregisters them with Bukkit.
 * Results are a custom item id (from a {@link LeetItemRegistry}) or a vanilla
 * material via {@code material:<MATERIAL>}. Ingredients are resolved through the
 * shared item registry.
 */
public final class LeetRecipeRegistry {

    /** A parsed recipe awaiting registration. */
    public record RecipeDef(String id, int amount, ItemStack result,
                            List<RecipeChoice> shapeless, String[] shape,
                            Map<Character, RecipeChoice> shaped,
                            RecipeChoice smelt, float smeltExp, int smeltTime) {
    }

    private final Logger logger;
    private final LeetItemRegistry items;
    private final Map<String, RecipeDef> recipes = new HashMap<>();
    private final Map<NamespacedKey, RecipeDef> recipesByKey = new HashMap<>();
    private final List<NamespacedKey> registeredKeys = new ArrayList<>();

    public LeetRecipeRegistry(Logger logger, LeetItemRegistry items) {
        this.logger = logger;
        this.items = items;
    }

    public void reload(String featureId, ConfigurationSection craftSection) {
        recipes.clear();
        if (craftSection == null) return;
        for (String recipeId : craftSection.getKeys(false)) {
            ConfigurationSection s = craftSection.getConfigurationSection(recipeId);
            if (s == null) continue;
            RecipeDef def = readRecipe(featureId, recipeId, s);
            if (def != null) recipes.put(recipeId.toLowerCase(), def);
        }
    }

    private RecipeDef readRecipe(String featureId, String recipeId, ConfigurationSection s) {
        int amount = Math.max(1, s.getInt("amount", 1));
        ItemStack result = parseResult(s.getString("result"), amount);
        String type = s.getString("type", "SHAPELESS").toUpperCase();

        if ("SHAPED".equals(type)) {
            List<String> shapeRows = s.getStringList("shape");
            if (shapeRows.size() != 3) {
                warn(featureId, recipeId, "shaped recipes need 3 'shape' rows; skipped.");
                return null;
            }
            ConfigurationSection ing = s.getConfigurationSection("ingredients");
            if (ing == null) {
                warn(featureId, recipeId, "missing 'ingredients' for shaped recipe; skipped.");
                return null;
            }
            String[] shape = new String[3];
            Map<Character, RecipeChoice> shaped = new HashMap<>();
            for (int r = 0; r < 3; r++) {
                String row = shapeRows.get(r);
                if (row.length() != 3) {
                    warn(featureId, recipeId, "shape rows must each be 3 wide; skipped.");
                    return null;
                }
                shape[r] = row;
                for (int c = 0; c < 3; c++) {
                    char key = row.charAt(c);
                    if (key == ' ' || key == '.' || shaped.containsKey(key)) continue;
                    RecipeChoice choice = items.spec(ing.getString(String.valueOf(key)));
                    if (choice == null) {
                        warn(featureId, recipeId, "invalid ingredient for '" + key + "'; skipped.");
                        return null;
                    }
                    shaped.put(key, choice);
                }
            }
            if (result == null) return null;
            return new RecipeDef(recipeId.toLowerCase(), amount, result, null, shape, shaped, null, 0, 0);
        }

        if ("SMELT".equals(type)) {
            RecipeChoice choice = items.spec(s.getString("ingredient"));
            if (choice == null) {
                warn(featureId, recipeId, "invalid smelt ingredient '" + s.getString("ingredient") + "'; skipped.");
                return null;
            }
            if (result == null) return null;
            float exp = (float) Math.max(0.0, s.getDouble("experience", 0.35));
            int time = Math.max(1, s.getInt("cooking-time", 200));
            return new RecipeDef(recipeId.toLowerCase(), amount, result, null, null, null, choice, exp, time);
        }

        // SHAPELESS
        List<RecipeChoice> shapeless = new ArrayList<>();
        for (String raw : s.getStringList("ingredients")) {
            RecipeChoice choice = items.spec(raw);
            if (choice == null) {
                warn(featureId, recipeId, "invalid ingredient '" + raw + "'; skipped.");
                return null;
            }
            shapeless.add(choice);
        }
        if (shapeless.isEmpty() || result == null) return null;
        return new RecipeDef(recipeId.toLowerCase(), amount, result, shapeless, null, null, null, 0, 0);
    }

    private void warn(String featureId, String recipeId, String msg) {
        logger.warning(featureId + " recipe " + recipeId + ": " + msg);
    }

    private ItemStack parseResult(String raw, int amount) {
        if (raw == null || raw.isEmpty()) return null;
        if (raw.regionMatches(true, 0, "material:", 0, "material:".length())) {
            String mat = raw.substring("material:".length());
            try {
                return new ItemStack(Material.valueOf(mat.toUpperCase()), amount);
            } catch (IllegalArgumentException e) {
                logger.warning("Recipe result: invalid material '" + mat + "'.");
                return null;
            }
        }
        ItemStack stack = items.create(raw);
        if (stack == null) {
            logger.warning("Recipe result: unknown custom item '" + raw + "'.");
            return null;
        }
        stack.setAmount(amount);
        return stack;
    }

    public void register(NamespacedKey prefix) {
        for (RecipeDef def : recipes.values()) {
            NamespacedKey key = new NamespacedKey(prefix.getNamespace(), prefix.getKey() + "/" + def.id());
            Recipe recipe;
            if (def.smelt() != null) {
                recipe = new FurnaceRecipe(key, def.result(),
                    def.smelt(), def.smeltExp(), def.smeltTime());
            } else if (def.shape() != null) {
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
                logger.warning("Failed to register recipe: " + def.id());
            }
        }
    }

    public void unregister() {
        for (NamespacedKey key : registeredKeys) {
            Bukkit.removeRecipe(key);
        }
        registeredKeys.clear();
        recipesByKey.clear();
    }

    /** Discovers every recipe on the server for the given player (recipe book). */
    public static void discoverAll(Player player) {
        var it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            if (recipe instanceof Keyed keyed) {
                player.discoverRecipe(keyed.getKey());
            }
        }
    }

    public RecipeDef defFor(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) return null;
        return recipesByKey.get(keyed.getKey());
    }
}
