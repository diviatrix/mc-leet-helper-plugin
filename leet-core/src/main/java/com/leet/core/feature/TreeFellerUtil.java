package com.leet.core.feature;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Shared whole-tree felling logic, used by both {@code TreeFellerFeature} and
 * the lumberjack skill (SkillsFeature). Collected here so the two don't drift.
 * The {@link #fell} method is the single choke point at which a whole connected
 * log cluster is broken, so it is also where the tree-fell state is tracked
 * (see {@link #isFelling}) for skills that should roll once per tree rather
 * than once per log.
 */
public final class TreeFellerUtil {

    private static final int[] DX = {0, 0, 0, 0, 1, -1};
    private static final int[] DY = {1, -1, 0, 0, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1, 0, 0};

    private static UUID fellingPlayer;

    private TreeFellerUtil() {
    }

    /**
     * Fells the connected log cluster reachable from {@code start}, breaking
     * each block via {@link AbstractFeature#breakIfAllowed} so protection
     * plugins are consulted per block. Returns how many blocks were broken.
     * While running, the felling player is exposed via {@link #isFelling};
     * other features can consult it to avoid applying a once-per-tree effect
     * on every log of the sweep.
     */
    public static int fell(BlockBreakerFeature feature, Player player, Block start,
                           Set<Material> logs, int maxBlocks, ItemStack tool) {
        UUID prior = fellingPlayer;
        fellingPlayer = player.getUniqueId();
        int broken = 0;
        try {
            for (Block block : findConnectedLogs(start, logs, maxBlocks)) {
                if (feature.breakIfAllowed(player, block, tool)) {
                    broken++;
                }
            }
        } finally {
            fellingPlayer = prior;
        }
        return broken;
    }

    /**
     * Whether the given player is currently the target of a whole-tree felling
     * sweep (i.e. one of their logs is being broken programmatically right
     * now, as opposed to the single log break that started the sweep).
     */
    public static boolean isFelling(Player player) {
        return player.getUniqueId().equals(fellingPlayer);
    }

    /**
     * Breadth-first search collecting every adjacent log block connected to
     * the given one (6-directional), excluding the origin. Stops early once
     * {@code maxBlocks} is reached so a giant tree cannot trigger an unbounded
     * chain of block breaks.
     */
    public static Set<Block> findConnectedLogs(Block start, Set<Material> logs, int maxBlocks) {
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