package com.leet.core.command;

import com.leet.core.LeetCore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class EconomyCommand implements CommandExecutor, TabCompleter {

    private final LeetCore plugin;

    public EconomyCommand(LeetCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        Economy economy = plugin.economy();
        if (economy == null) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<red>No economy is installed (requires Vault + an economy plugin)."));
            return true;
        }
        if (label.equalsIgnoreCase("pay")) {
            handlePay(player, economy, args);
        } else {
            handleBalance(player, economy);
        }
        return true;
    }

    private void handleBalance(Player player, Economy economy) {
        player.sendMessage(MiniMessage.miniMessage().deserialize(
            "<gray>Balance: <green>" + economy.format(economy.getBalance(player))));
    }

    private void handlePay(Player player, Economy economy, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Usage: /pay <player> <amount>"));
            return;
        }
        Player target = org.bukkit.Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Player not found: " + args[0]));
            return;
        }
        if (target.equals(player)) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>You cannot pay yourself."));
            return;
        }
        double amount = parseAmount(args[1]);
        if (amount <= 0) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Invalid amount: " + args[1]));
            return;
        }
        if (!economy.has(player, amount)) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Insufficient funds."));
            return;
        }
        if (!economy.withdrawPlayer(player, amount).transactionSuccess()
            || !economy.depositPlayer(target, amount).transactionSuccess()) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Transaction failed."));
            return;
        }
        String formatted = economy.format(amount);
        player.sendMessage(MiniMessage.miniMessage().deserialize(
            "<gray>Paid <green>" + formatted + " <gray>to <white>" + target.getName()));
        target.sendMessage(MiniMessage.miniMessage().deserialize(
            "<gray>Received <green>" + formatted + " <gray>from <white>" + player.getName()));
    }

    static double parseAmount(String input) {
        try {
            return Math.round(Double.parseDouble(input) * 100.0) / 100.0;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("pay") && args.length == 1 && sender instanceof Player player) {
            return CommandUtil.filterPrefix(
                org.bukkit.Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player))
                    .map(Player::getName)
                    .toList(),
                args[0]);
        }
        return Collections.emptyList();
    }
}
