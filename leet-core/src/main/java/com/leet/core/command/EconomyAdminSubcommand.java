package com.leet.core.command;

import com.leet.core.LeetCore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class EconomyAdminSubcommand implements AdminSubcommand {

    private static final List<String> ACTIONS = List.of("give", "take", "set", "balance");

    private final LeetCore plugin;

    public EconomyAdminSubcommand(LeetCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(CommandSender sender, String[] args) {
        Economy economy = plugin.economy();
        if (economy == null) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<red>No economy is installed (requires Vault + an economy plugin)."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<yellow>Usage: /leeta eco <give|take|set|balance> <player> [amount]"));
            return;
        }
        String action = args[0].toLowerCase();
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Player not found: " + args[1]));
            return;
        }
        if (action.equals("balance")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<gray>" + target.getName() + "'s balance: <green>" + economy.format(economy.getBalance(target))));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<yellow>Usage: /leeta eco " + action + " <player> <amount>"));
            return;
        }
        double amount = EconomyCommand.parseAmount(args[2]);
        if (amount < 0) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Invalid amount: " + args[2]));
            return;
        }
        switch (action) {
            case "give" -> economy.depositPlayer(target, amount);
            case "take" -> economy.withdrawPlayer(target, amount);
            case "set" -> {
                double current = economy.getBalance(target);
                if (current > amount) {
                    economy.withdrawPlayer(target, current - amount);
                } else {
                    economy.depositPlayer(target, amount - current);
                }
            }
            default -> {
                sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<yellow>Usage: /leeta eco <give|take|set|balance> <player> [amount]"));
                return;
            }
        }
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
            "<green>" + action + " <white>" + economy.format(amount) + " <green>for <white>" + target.getName()
                + " <gray>(now <green>" + economy.format(economy.getBalance(target)) + "<gray>)"));
    }

    @Override
    public List<String> tab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return ACTIONS;
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
