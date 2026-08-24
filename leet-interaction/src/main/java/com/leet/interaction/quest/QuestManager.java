package com.leet.interaction.quest;

import com.leet.core.gui.GuiManager;
import com.leet.interaction.LeetInteraction;
import com.leet.interaction.definition.DefinitionRegistry;
import com.leet.interaction.reputation.ReputationManager;
import com.leet.core.reactor.ItemSpec;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quest accept / turn-in flow. Per-player state (active / done + timestamp)
 * lives in the plugin's own SQLite store under keys {@code quest:<id>}.
 *
 * <p>Click flow: not started -> show description and accept; accepted with all
 * requirements in hand -> confirm GUI -> consume requirements and grant rewards;
 * otherwise -> report what is still missing.
 */
public final class QuestManager {

    private static final String FEATURE = "interaction";

    private final LeetInteraction plugin;
    private final DefinitionRegistry definitions;
    private final ReputationManager reputation;

    public QuestManager(LeetInteraction plugin, DefinitionRegistry definitions, ReputationManager reputation) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.reputation = reputation;
    }

    public QuestDefinition quest(String id) {
        return definitions.quest(id);
    }

    public void handle(Player player, String questId) {
        var feature = plugin.feature();
        if (questId == null) return;
        QuestDefinition quest = quest(questId);
        if (quest == null) {
            feature.message(player, "definition-unknown", "<id>", questId);
            return;
        }

        UUID uuid = player.getUniqueId();
        String key = "quest:" + quest.id;
        String state = plugin.storage().getPersistent(FEATURE, key, uuid);

        if (state == null) {
            if (quest.description != null && !quest.description.isBlank()) {
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<yellow>" + quest.name + "</yellow> <gray>— " + quest.description));
            }
            plugin.storage().setPersistent(FEATURE, key, uuid, "active");
            feature.message(player, "quest-accepted", "<quest>", quest.name);
            return;
        }

        if (state.equals("active")) {
            List<String> missing = missing(player, quest);
            if (!missing.isEmpty()) {
                feature.message(player, "quest-not-ready");
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<gray>" + String.join("<white>, </white>", missing)));
                return;
            }

            GuiManager gui = plugin.core() == null ? null : plugin.core().guiManager();
            if (gui == null) return;
            ItemStack icon = new ItemStack(Material.BOOK);
            var meta = icon.getItemMeta();
            meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize("<yellow>" + quest.name));
            icon.setItemMeta(meta);
            gui.openConfirm(player, "<dark_gray>Complete " + quest.name + "?", icon, confirmed -> {
                if (!confirmed) return;
                List<String> stillMissing = missing(player, quest);
                if (!stillMissing.isEmpty()) {
                    feature.message(player, "quest-not-ready");
                    return;
                }
                consume(player, quest);
                grant(player, quest);
                plugin.storage().setPersistent(FEATURE, key, uuid,
                    quest.repeatable ? "cooldown:" + System.currentTimeMillis() : "done");
                feature.message(player, "quest-complete", "<quest>", quest.name);
            });
            return;
        }

        if (state.startsWith("done")) {
            feature.message(player, "quest-complete", "<quest>", quest.name);
            return;
        }

        if (state.startsWith("cooldown:")) {
            long ts = parseTs(state);
            long remaining = quest.cooldownSeconds * 1000L - (System.currentTimeMillis() - ts);
            if (remaining <= 0) {
                plugin.storage().setPersistent(FEATURE, key, uuid, "active");
                feature.message(player, "quest-accepted", "<quest>", quest.name);
            } else {
                feature.message(player, "quest-cooldown", "<seconds>", String.valueOf(remaining / 1000));
            }
        }
    }

    private static long parseTs(String state) {
        try {
            return Long.parseLong(state.substring("cooldown:".length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private List<String> missing(Player player, QuestDefinition quest) {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<ItemStack, Integer> need : requirementsAsStacks(quest)) {
            int have = ItemSpec.count(player.getInventory(), need.getKey());
            if (have < need.getValue()) {
                missing.add(need.getValue() + "x " + ItemSpec.describe(
                    need.getKey().getType().getKey().getKey()) + " (have " + have + ")");
            }
        }
        Economy eco = plugin.core() == null ? null : plugin.core().economy();
        if (quest.requiredMoney > 0 && eco != null && !eco.has(player, quest.requiredMoney)) {
            missing.add("$" + quest.requiredMoney);
        }
        if (quest.requiredReputation > 0
            && reputation.get(player.getUniqueId()) < quest.requiredReputation) {
            missing.add(quest.requiredReputation + " reputation");
        }
        return missing;
    }

    private List<Map.Entry<ItemStack, Integer>> requirementsAsStacks(QuestDefinition quest) {
        List<Map.Entry<ItemStack, Integer>> stacks = new ArrayList<>();
        for (var entry : quest.requiredItems) {
            ItemStack stack = ItemSpec.build(String.valueOf(entry.get("item")),
                entry.get("amount") instanceof Number n ? n.intValue() : 1);
            if (stack != null) {
                stacks.add(Map.entry(stack, entry.get("amount") instanceof Number n ? n.intValue() : 1));
            }
        }
        return stacks;
    }

    private void consume(Player player, QuestDefinition quest) {
        for (Map.Entry<ItemStack, Integer> need : requirementsAsStacks(quest)) {
            int left = need.getValue();
            while (left > 0) {
                ItemStack part = need.getKey().clone();
                part.setAmount(Math.min(left, need.getKey().getMaxStackSize()));
                ItemSpec.remove(player.getInventory(), part, part.getAmount());
                left -= part.getAmount();
            }
        }
        Economy eco = plugin.core() == null ? null : plugin.core().economy();
        if (quest.requiredMoney > 0 && eco != null) {
            eco.withdrawPlayer(player, quest.requiredMoney);
        }
    }

    private void grant(Player player, QuestDefinition quest) {
        for (var entry : quest.rewardItems) {
            ItemStack stack = ItemSpec.build(String.valueOf(entry.get("item")),
                entry.get("amount") instanceof Number n ? n.intValue() : 1);
            if (stack != null) {
                ItemSpec.give(player, stack);
            }
        }
        Economy eco = plugin.core() == null ? null : plugin.core().economy();
        if (quest.rewardMoney > 0 && eco != null) {
            eco.depositPlayer(player, quest.rewardMoney);
        }
        if (quest.rewardExp != 0) {
            player.giveExp(quest.rewardExp);
        }
        if (quest.rewardReputation != 0) {
            reputation.add(player.getUniqueId(), quest.rewardReputation);
        }
        for (String cmd : quest.rewardCommands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                cmd.replace("%player%", player.getName()));
        }
    }
}
