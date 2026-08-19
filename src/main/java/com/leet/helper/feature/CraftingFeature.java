package com.leet.helper.feature;

import com.leet.helper.Core;
import com.leet.helper.craft.LeetItemRegistry;
import com.leet.helper.craft.LeetRecipeRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Recipe;

/**
 * The crafting feature: the non-food item domain (condiments and crop products).
 * Adds Salt, Soy Seed, Soy Oil and Soy Sauce as custom items plus their crafting
 * and smelting recipes (e.g. evaporating a water bucket in a furnace yields salt).
 * Uses the shared {@link LeetItemRegistry} / {@link LeetRecipeRegistry} engines;
 * the {@code ci} registration is shared so items produced here (e.g. Soy Seed)
 * are the same items the crop feature drops and any recipe in any feature may use.
 */
public final class CraftingFeature extends AbstractFeature {

    private final LeetItemRegistry items;
    private final LeetRecipeRegistry recipes;

    public CraftingFeature(Core plugin, LeetItemRegistry items) {
        super(plugin);
        this.items = items;
        this.recipes = new LeetRecipeRegistry(plugin.getLogger(), items);
    }

    @Override
    public String featureId() {
        return "crafting";
    }

    @Override
    protected void loadFeatureConfig(YamlConfiguration cfg) {
        items.load("crafting", cfg.getConfigurationSection("feature.items"));
        recipes.reload("crafting", cfg.getConfigurationSection("feature.recipes"));
    }

    @Override
    public void enable() {
        super.enable();
        if (enabled) {
            recipes.register(new NamespacedKey(plugin, "crafting"));
        }
    }

    @Override
    public void disable() {
        recipes.unregister();
        super.disable();
    }

    /** Crafting is open to every player (same policy as cooking). */
    @Override
    protected boolean check(Player player) {
        if (!enabled) return false;
        if (worlds != null && !worlds.isEmpty()) {
            if (!worlds.contains(player.getWorld().getName())) return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (recipes.defFor(event.getRecipe()) == null) return;
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        if (!check(player)) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (recipes.defFor(event.getRecipe()) == null) return;
        if (check(player)) return;
        event.setCancelled(true);
        sendMessage(player, "feature-off");
    }
}
