package com.leet.core.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generic, tag-driven inventory GUI manager, shared across any plugin's GUIs
 * (skills tree, NPC shops, quests, marriages, ...). It owns the cross-cutting
 * concerns of every clickable inventory:
 *
 * <ul>
 *   <li>tracking which inventory each player currently has open,</li>
 *   <li>swallowing clicks inside managed inventories and routing them to a
 *       per-open click handler via a tag stored on the clicked item,</li>
 *   <li>cleaning up per-player state on inventory close / quit.</li>
 * </ul>
 *
 * <p>A feature that wants a GUI simply builds an {@link Inventory}, tags its
 * clickable buttons with {@link #button(ItemStack, String)} (or {@link
 * #actionIcon(Material, String, String, String)}), and opens it via
 * {@link #open(Player, Inventory, GuiHandler)}. The handler receives the tag of
 * whatever the player clicked. Navigating screens = calling {@link #open} again
 * with a new inventory + handler. Nothing skill-specific lives here.
 */
public final class GuiManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin owner;
    private final NamespacedKey tagKey;

    /** Registered click action code: {@code action:} + this removes + closes. */
    private static final String CLOSE = "close";

    /** A click handler for one open inventory. */
    public interface GuiHandler {
        /** Invoked when the player clicks a tagged button in this inventory. */
        void onClick(Player player, String tag);
    }

    private static final class State {
        Inventory inventory;
        GuiHandler handler;
    }

    private final Map<UUID, State> open = new HashMap<>();

    public GuiManager(JavaPlugin owner) {
        this.owner = owner;
        this.tagKey = new NamespacedKey(owner, "gui");
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, owner);
    }

    public void stop() {
        HandlerList.unregisterAll(this);
        open.clear();
    }

    /**
     * Opens {@code inv} for the player and routes subsequent clicks in it to
     * {@code handler}. Any previously-open managed inventory is forgotten.
     */
    public void open(Player player, Inventory inv, GuiHandler handler) {
        State state = open.computeIfAbsent(player.getUniqueId(), k -> new State());
        state.inventory = inv;
        state.handler = handler;
        player.openInventory(inv);
    }

    /** Closes the player's managed inventory (runs the CLOSE tag behaviour). */
    public void closeFor(Player player) {
        open.remove(player.getUniqueId());
        player.closeInventory();
    }

    /** Drops per-player state (used on quit). */
    public void drop(Player player) {
        open.remove(player.getUniqueId());
    }

    /**
     * Opens a small generic confirm dialog (item + Yes / No buttons) and invokes
     * {@code onResult} with the player's choice. Useful for irreversible actions
     * across GUIs (buying, marriages, quest turn-ins).
     */
    public void openConfirm(Player player, String title, ItemStack subject,
                            java.util.function.Consumer<Boolean> onResult) {
        Inventory inv = Bukkit.createInventory(null, 9, MM.deserialize(title));
        var filler = filler(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, filler.clone());
        }
        inv.setItem(1, actionIcon(Material.RED_WOOL, "<red><bold>No",
            "<gray>Click to cancel", "confirm:no"));
        inv.setItem(4, subject == null ? filler : subject);
        inv.setItem(7, actionIcon(Material.LIME_WOOL, "<green><bold>Yes",
            "<gray>Click to confirm", "confirm:yes"));
        open(player, inv, (p, tag) -> {
            switch (tag) {
                case "confirm:yes" -> {
                    p.closeInventory();
                    onResult.accept(true);
                }
                case "confirm:no" -> {
                    p.closeInventory();
                    onResult.accept(false);
                }
                default -> { /* ignore */ }
            }
        });
    }

    /**
     * Opens a generic paginated list of tagged buttons. Each page is a list of
     * already-tagged {@link ItemStack}s laid out across every slot (rows * 9); a
     * Previous/Next footer and a Close button are added automatically. Clicking a
     * page item / footer routes to {@code onItem} with its tag. Common for shop
     * stock, quest logs and NPC menus.
     */
    public void openPages(Player player, String title, int rows,
                          List<List<ItemStack>> pages, java.util.function.BiConsumer<Player, String> onItem) {
        if (pages.isEmpty()) {
            player.sendMessage(MM.deserialize("<gray>Nothing here."));
            return;
        }
        int[] pageHolder = {0};
        openPage(player, title, rows, pages, onItem, pageHolder);
    }

    private void openPage(Player player, String title, int rows, List<List<ItemStack>> pages,
                          java.util.function.BiConsumer<Player, String> onItem, int[] pageHolder) {
        int page = pageHolder[0];
        Inventory inv = Bukkit.createInventory(null, rows * 9, MM.deserialize(title + " (" + (page + 1) + "/" + pages.size() + ")"));
        var filler = filler(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler.clone());
        }
        int capacity = rows * 9;
        int slot = 0;
        for (ItemStack item : pages.get(page)) {
            if (slot >= capacity) break; // clamp page to inventory size
            inv.setItem(slot++, item);
        }
        int footer = (rows - 1) * 9;
        inv.setItem(footer, actionIcon(Material.ARROW, "<gray><bold>\u2190 Previous", "", "page:prev"));
        // Use the action-prefixed form so GuiManager's built-in close handling fires.
        inv.setItem(footer + 4, action(Material.OAK_DOOR, "<red><bold>\u2715 Close", "", "close"));
        inv.setItem(footer + 8, actionIcon(Material.ARROW, "<gray><bold>Next \u2192", "", "page:next"));
        open(player, inv, (p, tag) -> {
            switch (tag) {
                case "page:prev" -> {
                    if (pageHolder[0] > 0) {
                        pageHolder[0]--;
                        openPage(p, title, rows, pages, onItem, pageHolder);
                    }
                }
                case "page:next" -> {
                    if (pageHolder[0] < pages.size() - 1) {
                        pageHolder[0]++;
                        openPage(p, title, rows, pages, onItem, pageHolder);
                    }
                }
                default -> onItem.accept(p, tag);
            }
        });
    }

    // --- item tagging helpers ---

    /** Tags an item so a click on it is routed to the open handler with {@code tag}. */
    public ItemStack tag(ItemStack item, String tag) {
        if (item == null || item.getType() == Material.AIR) return item;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, tag);
        item.setItemMeta(meta);
        return item;
    }

    /** A clickable button: material + name + lore + a route tag. */
    public ItemStack actionIcon(Material material, String name, String lore, String tag) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name));
        meta.lore(List.of(MM.deserialize(lore)));
        tag(item, tag);
        return item;
    }

    /** A clickable button with an {@code action:} tag on it. */
    public ItemStack action(Material material, String name, String lore, String action) {
        return actionIcon(material, name, lore, "action:" + action);
    }

    /** Background filler for every slot (not clickable). */
    public ItemStack filler(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name));
        item.setItemMeta(meta);
        return item;
    }

    // --- event routing ---

    @EventHandler(priority = EventPriority.NORMAL)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        State state = open.get(player.getUniqueId());
        if (state == null) return; // not one of our managed inventories

        event.setCancelled(true); // swallow every click while a managed GUI is open

        // Do not act on the player's own bottom inventory.
        if (!event.getView().getTopInventory().equals(state.inventory)) return;

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return;
        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;
        String tag = meta.getPersistentDataContainer().get(tagKey, PersistentDataType.STRING);
        if (tag == null) return;

        if (tag.equals("action:" + CLOSE)) {
            drop(player);
            player.closeInventory();
            return;
        }
        state.handler.onClick(player, tag);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        State state = open.get(player.getUniqueId());
        if (state != null && state.inventory.equals(event.getInventory())) {
            open.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        open.remove(event.getPlayer().getUniqueId());
    }
}
