package com.leet.core;

import com.leet.core.command.AdminSubcommand;
import com.leet.core.craft.CustomItemView;
import com.leet.core.feature.AbstractFeature;
import com.leet.core.feature.FeatureRegistry;
import com.leet.core.gui.GuiManager;
import com.leet.core.reactor.Reactor;
import com.leet.core.storage.StorageManager;
import net.milkbowl.vault.economy.Economy;

import java.util.Map;

/**
 * The cross-plugin service contract exposed by LeetCore. The skills and crafting
 * plugins look this up at runtime (via Bukkit's ServicesManager) and drive the
 * genuinely shared seams here — the feature registry, the /leet toggle + cooldown
 * store, the custom-item view, and the generic GUI manager. Domain engines (item
 * parsing, recipes, the resource pack) live in the plugin that owns them, not here.
 */
public interface CoreApi {

    /** The shared feature registry (every plugin registers its features here). */
    FeatureRegistry featureRegistry();

    /** Storage for the shared /leet toggle + cooldown gating, namespaced by feature id. */
    StorageManager storageManager();

    /** Read-only view of the custom-item registry (for /leeta give and item lookups). */
    CustomItemView itemRegistry();

    /** Generic tag-driven GUI manager (skills trees, NPC shops, quests, marriages). */
    GuiManager guiManager();

    /** The optional Vault economy provider, or null when Vault is absent. */
    Economy economy();

    /**
     * The shared trigger -> conditions -> actions kernel. Any plugin registers
     * its domain actions/conditions here and runs definitions through it.
     */
    Reactor reactor();

    /** Registers a feature contributed by any plugin into the shared registry,
     * adds its permission node, and enables it. Returns true when registered.
     * The feature must already be wired to its owning plugin via its constructor.
     */
    boolean registerFeature(AbstractFeature feature);

    /**
     * Registers a /leeta subcommand contributed by another plugin (e.g.
     * LeetInteraction's bind/unbind). Returns false when the name is taken.
     */
    boolean registerAdminSubcommand(String name, AdminSubcommand subcommand);

    /** Registered contributed /leeta subcommands (name -> handler). */
    Map<String, AdminSubcommand> adminSubcommands();

    /** Logs a prefixed message to the server console. */
    void log(String message);
}
