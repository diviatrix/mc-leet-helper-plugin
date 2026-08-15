package com.leet.helper.feature;

import com.leet.helper.HelperPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class TreeFellerFeature extends AbstractFeature {

    private static final int[] DX = {0, 0, 0, 0, 1, -1};
    private static final int[] DY = {1, -1, 0, 0, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1, 0, 0};

    private Set<Material> logs;
    private int maxBlocks;
    private boolean felling;

    public TreeFellerFeature(HelperPlugin plugin) {
        super(plugin);
    }

    @Override
    public String featureId() {
        return "tree_feller";
    }

    @Override
    protected void loadFeatureConfig(YamlConfiguration cfg) {
        logs = new HashSet<>();
        for (String name : cfg.getStringList("feature.logs")) {
            try {
                logs.add(Material.valueOf(name));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in tree_feller logs: " + name);
            }
        }
        maxBlocks = cfg.getInt("feature.max-blocks", 100);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        // Respect protection plugins: run late (MONITOR) so any claim/region
        // cancellation is already visible, and bail if the original break was
        // blocked. Each connected log is then broken through breakIfAllowed,
        // which fires a BlockBreakEvent so claims/regions apply per block too.
        if (event.isCancelled()) return;
        if (felling) return; // guard against the synthetic per-block events below

        Player player = event.getPlayer();
        if (!check(player)) return;

        Block broken = event.getBlock();
        if (!logs.contains(broken.getType())) return;

        if (!chargeUse(player)) return;

        ItemStack tool = player.getInventory().getItemInMainHand();

        // Component search over the log blocks so the whole trunk and any
        // branches come down together, not just the single broken log. The
        // origin block (already handled by the vanilla break) is excluded so it
        // is not re-broken / re-dropped.
        Set<Block> toBreak = findConnectedLogs(broken);

        felling = true;
        try {
            for (Block block : toBreak) {
                breakIfAllowed(player, block, tool);
            }
        } finally {
            felling = false;
        }
    }

    /**
     * Breadth-first search that collects every adjacent log block connected to
     * the broken one (6-directional), excluding the origin. Stops early once
     * the configured max-blocks cap is reached so a giant tree can't trigger an
     * unbounded chain of block breaks.
     */
    private Set<Block> findConnectedLogs(Block start) {
        Set<Block> result = new HashSet<>();
        Set<Block> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();

        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            if (result.size() >= maxBlocks) break;
            Block current = queue.poll();
            for (int i = 0; i < 6; i++) {
                Block neighbor = current.getRelative(DX[i], DY[i], DZ[i]);
                if (!logs.contains(neighbor.getType())) continue;
                if (result.size() >= maxBlocks) break;
                if (visited.add(neighbor)) {
                    result.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return result;
    }
}