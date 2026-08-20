package com.leet.crafting;

import com.leet.core.CoreApi;
import com.leet.crafting.craft.LeetItemRegistry;
import com.leet.crafting.craft.LeetRecipeRegistry;
import com.leet.core.feature.AbstractFeature;
import com.leet.core.feature.MessagingFeature;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The single merged crafting feature. Owns {@code features/crafting.yml}
 * (items + recipes), registers them into the shared {@link LeetItemRegistry},
 * and contributes recipes to vanilla. Server-wide (no permission, no per-player
 * toggle); gated by {@code base.enabled} and the {@code base.worlds} whitelist.
 */
public final class CraftFeature extends AbstractFeature implements MessagingFeature {

    private static final String FEATURE_ID = "crafting";

    private final LeetItemRegistry items;
    private final LeetRecipeRegistry recipes;
    private boolean recipeBookAutodiscovery;

    public CraftFeature(CoreApi core, JavaPlugin owner, LeetItemRegistry items) {
        super(core, owner);
        this.items = items;
        this.recipes = new LeetRecipeRegistry(owner.getLogger(), items);
    }

    /** Sets whether all recipes are auto-discovered for players on join. */
    public void setRecipeBookAutodiscovery(boolean enabled) {
        this.recipeBookAutodiscovery = enabled;
    }

    @Override
    public String featureId() {
        return FEATURE_ID;
    }

    @Override
    protected void loadFeatureConfig(YamlConfiguration cfg) {
        items.load(FEATURE_ID, cfg.getConfigurationSection("feature.items"));
        recipes.reload(FEATURE_ID, cfg.getConfigurationSection("feature.recipes"));
    }

    @Override
    public void enable() {
        super.enable();
        if (enabled) {
            recipes.register(new NamespacedKey(owner, FEATURE_ID));
        }
    }

    @Override
    public void disable() {
        recipes.unregister();
        super.disable();
    }

    /** Crafting is open to every player (server-enabled toggle + world whitelist). */
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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (enabled && recipeBookAutodiscovery) {
            LeetRecipeRegistry.discoverAll(event.getPlayer());
        }
    }
}