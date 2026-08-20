package com.leet.crafting;

import com.leet.core.CoreApi;
import com.leet.crafting.craft.LeetItemRegistry;
import com.leet.crafting.craft.LeetRecipeRegistry;
import com.leet.core.feature.AbstractFeature;
import com.leet.core.feature.MessagingFeature;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A reusable custom item + recipe feature (the merged Cooking/Crafting domain).
 * A single feature id owns a {@code features/<id>.yml} file with its own
 * {@code feature.items}/{@code feature.recipes} sections, registered into core's
 * shared {@link LeetItemRegistry}. The LeetCrafting plugin instantiates one for
 * {@code cooking} and one for {@code crafting} so both keep their own /leet info
 * and /leeta toggle, but share the core item registry so recipes may cross-reference
 * each other's items (e.g. a cooking dish requiring crafted salt).
 */
public final class CraftFeature extends AbstractFeature implements MessagingFeature {

    private final String id;
    private final LeetItemRegistry items;
    private final LeetRecipeRegistry recipes;

    public CraftFeature(CoreApi core, JavaPlugin owner, String id, LeetItemRegistry items) {
        super(core, owner);
        this.id = id;
        this.items = items;
        this.recipes = new LeetRecipeRegistry(owner.getLogger(), items);
    }

    @Override
    public String featureId() {
        return id;
    }

    @Override
    protected void loadFeatureConfig(YamlConfiguration cfg) {
        items.load(id, cfg.getConfigurationSection("feature.items"));
        recipes.reload(id, cfg.getConfigurationSection("feature.recipes"));
    }

    /**
     * Loads only the {@code feature.items} section of {@code features/<id>.yml}
     * into the shared registry, without parsing recipes. LeetCrafting pre-loads
     * every domain's items this way before registering any feature, so recipe
     * ingredient resolution is independent of feature load order (e.g. a cooking
     * recipe may reference crafting's salt regardless of registration order).
     */
    public static void preloadItemSections(JavaPlugin owner, LeetItemRegistry items, String... featureIds) {
        for (String id : featureIds) {
            java.io.File file = new java.io.File(owner.getDataFolder(), "features/" + id + ".yml");
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            items.load(id, cfg.getConfigurationSection("feature.items"));
        }
    }

    @Override
    public void enable() {
        super.enable();
        if (enabled) {
            recipes.register(new NamespacedKey(owner, id));
        }
    }

    @Override
    public void disable() {
        recipes.unregister();
        super.disable();
    }

    /** Crafting/cooking is open to every player (server-enabled toggle + world whitelist). */
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
            event.getInventory().setResult(null); // recipe shows as "no result" unless the feature applies
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
