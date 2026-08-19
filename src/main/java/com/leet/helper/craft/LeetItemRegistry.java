package com.leet.helper.craft;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Shared registry of custom items across the crafting/recipe features (food,
 * condiments, crop products). Items are declared per-feature in each YAML
 * {@code feature.items} section but collected into one map so any recipe in any
 * feature can reference any item id.
 */
public final class LeetItemRegistry {

    private final Logger logger;
    private final NamespacedKey ciKey;
    private final Map<String, LeetItem> items = new LinkedHashMap<>();

    /** Materials that should match a broader tag instead of the exact material. */
    private static final Map<Material, Tag<Material>> MATERIAL_TAGS = Map.of(
        Material.EGG, Tag.ITEMS_EGGS
    );

    public LeetItemRegistry(Logger logger, NamespacedKey ciKey) {
        this.logger = logger;
        this.ciKey = ciKey;
    }

    public NamespacedKey ciKey() {
        return ciKey;
    }

    public void clear() {
        items.clear();
    }

    /** Loads a {@code feature.items} section (if present) into the registry. */
    public void load(String featureId, ConfigurationSection section) {
        if (section == null) return;
        for (String itemId : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(itemId);
            if (s == null) continue;
            Material material = parseMaterial(s.getString("material", "STONE"), itemId);
            if (material == null) continue;
            items.put(itemId.toLowerCase(), new LeetItem(
                itemId.toLowerCase(),
                s.getString("name", itemId),
                material,
                s.getStringList("lore"),
                Math.max(0, s.getInt("hunger", 0)),
                Math.max(0, s.getInt("saturation", 0))));
        }
    }

    private Material parseMaterial(String name, String itemId) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid material in item " + itemId + ": " + name);
            return null;
        }
    }

    public LeetItem get(String id) {
        return id == null ? null : items.get(id.toLowerCase());
    }

    public boolean contains(String id) {
        return get(id) != null;
    }

    /** All registered item ids (for command tab-completion and discovery). */
    public java.util.Collection<String> ids() {
        return java.util.Collections.unmodifiableCollection(items.keySet());
    }

    public ItemStack create(String id) {
        LeetItem item = get(id);
        return item == null ? null : item.create(ciKey);
    }

    /**
     * Resolves a recipe ingredient string: a vanilla {@link Material} name, or a
     * registered custom item id (matched exactly via {@code ci} tag). Returns
     * null for an unknown value.
     */
    public RecipeChoice spec(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            Material mat = Material.valueOf(raw.toUpperCase());
            Tag<Material> tag = MATERIAL_TAGS.get(mat);
            return tag != null ? new RecipeChoice.MaterialChoice(tag) : new RecipeChoice.MaterialChoice(mat);
        } catch (IllegalArgumentException ignored) {
        }
        LeetItem item = get(raw);
        return item == null ? null : new RecipeChoice.ExactChoice(item.create(ciKey));
    }
}
