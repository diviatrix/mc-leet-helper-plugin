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

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!check(player)) return;

        Block broken = event.getBlock();
        if (!logs.contains(broken.getType())) return;

        ItemStack tool = player.getInventory().getItemInMainHand();

        // Component search over the log blocks so the whole trunk and any
        // branches come down together, not just the single broken log. The
        // original block is already air after the vanilla break, so calling
        // breakNaturally on it is a no-op (no duplicate drop).
        Set<Block> toBreak = findConnectedLogs(broken);

        for (Block block : toBreak) {
            block.breakNaturally(tool);
        }
    }

    /**
     * Breadth-first search that collects every adjacent log block connected to
     * the broken one (6-directional). Stops early once the configured
     * max-blocks cap is reached so a giant tree can't trigger an unbounded
     * chain of breakNaturally calls.
     */
    private Set<Block> findConnectedLogs(Block start) {
        Set<Block> result = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        result.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            if (result.size() >= maxBlocks) break;
            Block current = queue.poll();
            for (int i = 0; i < 6; i++) {
                Block neighbor = current.getRelative(DX[i], DY[i], DZ[i]);
                if (!logs.contains(neighbor.getType())) continue;
                if (result.add(neighbor)) {
                    queue.add(neighbor);
                    if (result.size() >= maxBlocks) break;
                }
            }
        }
        return result;
    }
}