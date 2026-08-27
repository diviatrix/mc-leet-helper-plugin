package com.leet.core.reactor;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The generic built-in actions every plugin gets through the reactor:
 * teleport, give-items, take-items, sell, buy, enchant, open-disposal,
 * run-command, message, sound and give-exp. Domain actions (kit, open-chest,
 * quest, ...) are contributed by the feature plugins that own them.
 */
public final class BuiltInActions {

    private BuiltInActions() {
    }

    public static void registerAll(Reactor reactor, Economy economy) {
        reactor.actions().register(new Teleport());
        reactor.actions().register(new GiveItems());
        reactor.actions().register(new TakeItems());
        reactor.actions().register(new Sell(economy));
        reactor.actions().register(new Buy(economy));
        reactor.actions().register(new Enchant(economy));
        reactor.actions().register(new OpenDisposal());
        reactor.actions().register(new RunCommand());
        reactor.actions().register(new Message());
        reactor.actions().register(new Sound());
        reactor.actions().register(new GiveExp());
    }

    public static final class Teleport implements Action {
        @Override public String type() {
            return "teleport";
        }

        @Override public void execute(Player player, Map<String, Object> params) {
            Location target = parse(Params.str(params.get("location")));
            if (target != null) {
                player.teleport(target);
            }
        }

        private static Location parse(String spec) {
            if (spec == null) return null;
            String[] parts = spec.split(",");
            if (parts.length < 4) return null;
            World world = Bukkit.getWorld(parts[0].trim());
            if (world == null) return null;
            try {
                Location loc = new Location(world,
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()),
                    Double.parseDouble(parts[3].trim()));
                if (parts.length >= 6) {
                    loc.setYaw(Float.parseFloat(parts[4].trim()));
                    loc.setPitch(Float.parseFloat(parts[5].trim()));
                }
                return loc;
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    public static final class GiveItems implements Action {
        @Override public String type() {
            return "give-items";
        }

        @Override public void execute(Player player, Map<String, Object> params) {
            giveOrTake(player, params, true);
        }
    }

    public static final class TakeItems implements Action {
        @Override public String type() {
            return "take-items";
        }

        @Override public void execute(Player player, Map<String, Object> params) {
            giveOrTake(player, params, false);
        }
    }

    private static void giveOrTake(Player player, Map<String, Object> params, boolean give) {
        for (Map<String, Object> entry : Params.itemMaps(params.get("items"))) {
            String spec = Params.str(entry.get("item"));
            int amount = Params.intVal(entry.get("amount"), 1);
            if (spec == null) continue;
            ItemStack stack = ItemSpec.build(spec, Math.max(1, amount));
            if (stack == null) continue;
            if (give) {
                int left = amount;
                while (left > 0) {
                    int n = Math.min(stack.getMaxStackSize(), left);
                    ItemStack part = stack.clone();
                    part.setAmount(n);
                    ItemSpec.give(player, part);
                    left -= n;
                }
                Params.send(player, "<green>Gave " + amount + "x " + ItemSpec.describe(spec) + ".");
            } else {
                int removed = ItemSpec.remove(player.getInventory(), stack, amount);
                Params.send(player, removed == 0
                    ? "<red>You don't have " + amount + "x " + ItemSpec.describe(spec) + "."
                    : "<green>Took " + removed + "x " + ItemSpec.describe(spec) + ".");
            }
        }
    }

    public static class Sell implements Action {
        private final Economy economy;

        public Sell(Economy economy) {
            this.economy = economy;
        }

        @Override public String type() {
            return "sell";
        }

        @Override public void execute(Player player, Map<String, Object> params) {
            trade(player, params, true);
        }

        protected void trade(Player player, Map<String, Object> params, boolean sell) {
            String spec = Params.str(params.get("item"));
            int amount = Params.intVal(params.get("amount"), 1);
            Double parsedPrice = Params.money(params.get("price"));
            if (parsedPrice == null) {
                Params.send(player, "<red>Invalid or missing price on this sign.");
                return;
            }
            double price = parsedPrice;
            if (spec == null) return;
            ItemStack stack = ItemSpec.build(spec, Math.max(1, amount));
            if (stack == null) return;

            if (price > 0 && economy == null) {
                Params.send(player, "<red>No economy is available for this transaction.");
                return;
            }

            if (sell) {
                if (ItemSpec.count(player.getInventory(), stack) < amount) {
                    Params.send(player, "<red>You don't have " + amount + "x " + ItemSpec.describe(spec) + ".");
                    return;
                }
                ItemSpec.remove(player.getInventory(), stack, amount);
                if (price > 0) {
                    economy.depositPlayer(player, price);
                }
                Params.send(player, "<green>Sold " + amount + "x " + ItemSpec.describe(spec)
                    + " for <yellow>" + price + "</yellow>.");
            } else {
                if (price > 0 && !economy.has(player, price)) {
                    Params.send(player, "<red>You need <yellow>" + price + "</yellow> for this.");
                    return;
                }
                if (price > 0) {
                    economy.withdrawPlayer(player, price);
                }
                ItemSpec.give(player, stack);
                Params.send(player, "<green>Bought " + amount + "x " + ItemSpec.describe(spec)
                    + " for <yellow>" + price + "</yellow>.");
            }
        }
    }

    public static final class Buy extends Sell {
        public Buy(Economy economy) {
            super(economy);
        }

        @Override public String type() {
            return "buy";
        }

        @Override public void execute(Player player, Map<String, Object> params) {
            trade(player, params, false);
        }
    }

    public static final class Enchant implements Action {
        private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("damage_all", "sharpness"),
            Map.entry("damage_undead", "smite"),
            Map.entry("damage_arthropods", "bane_of_arthropods"),
            Map.entry("loot_bonus_mobs", "looting"),
            Map.entry("loot_bonus_blocks", "fortune"),
            Map.entry("dig_speed", "efficiency"),
            Map.entry("durability", "unbreaking"),
            Map.entry("arrow_damage", "power"),
            Map.entry("arrow_knockback", "punch"),
            Map.entry("arrow_fire", "flame"),
            Map.entry("arrow_infinite", "infinity"),
            Map.entry("protection_environmental", "protection"),
            Map.entry("protection_fire", "fire_protection"),
            Map.entry("protection_fall", "feather_falling"),
            Map.entry("protection_explosions", "blast_protection"),
            Map.entry("protection_projectile", "projectile_protection"),
            Map.entry("oxygen", "respiration"),
            Map.entry("water_worker", "aqua_affinity"),
            Map.entry("binding_curse", "binding_curse"),
            Map.entry("vanishing_curse", "vanishing_curse")
        );

        private final Economy economy;

        public Enchant(Economy economy) {
            this.economy = economy;
        }

        @Override public String type() {
            return "enchant";
        }

        @Override public void execute(Player player, Map<String, Object> params) {
            String name = Params.str(params.get("enchantment"));
            int level = Params.intVal(params.get("level"), 1);
            Double parsedPrice = Params.money(params.get("price"));
            if (params.get("price") != null && !String.valueOf(params.get("price")).trim().isEmpty()
                    && parsedPrice == null) {
                Params.send(player, "<red>Invalid or missing price on this sign.");
                return;
            }
            double price = parsedPrice == null ? 0 : parsedPrice;
            if (name == null) return;

            Enchantment enchantment = enchantment(name);
            if (enchantment == null) {
                Params.send(player, "<red>Unknown enchantment: <yellow>" + name + "</yellow>.");
                return;
            }

            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                Params.send(player, "<red>Hold the item you want to enchant.");
                return;
            }

            if (price > 0) {
                if (economy == null) {
                    Params.send(player, "<red>No economy is available for this.");
                    return;
                }
                if (!economy.has(player, price)) {
                    Params.send(player, "<red>You need <yellow>" + price + "</yellow> for this.");
                    return;
                }
                economy.withdrawPlayer(player, price);
            }

            hand.addUnsafeEnchantment(enchantment, Math.max(1, level));
            Params.send(player, "<green>Enchanted your "
                + hand.getType().name().toLowerCase(Locale.ROOT) + ".");
        }

        private Enchantment enchantment(String name) {
            String normalized = normalize(name);
            String mapped = ALIASES.getOrDefault(normalized, normalized);
            Key key = mapped.contains(":") ? Key.key(mapped) : Key.key("minecraft", mapped);
            return RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .get(key);
        }

        private String normalize(String name) {
            return name.toLowerCase(Locale.ROOT)
                .replace("minecraft:", "")
                .replace(' ', '_')
                .replace('-', '_');
        }
    }

    public static final class OpenDisposal implements Action {
        @Override public String type() {
            return "open-disposal";
        }

        @Override public void execute(Player player, Map<String, Object> params) {
            player.openInventory(Bukkit.createInventory(player, 54,
                MiniMessage.miniMessage().deserialize("<dark_gray>Disposal")));
        }
    }

    public static final class RunCommand implements Action {
        @Override public String type() {
            return "run-command";
        }

        @Override public void execute(Player player, Map<String, Object> params) {
            boolean asPlayer = Boolean.parseBoolean(String.valueOf(params.getOrDefault("as-player", "false")));
            if (!(params.get("commands") instanceof List<?> commands)) return;
            for (Object raw : commands) {
                if (raw == null) continue;
                String cmd = String.valueOf(raw).replace("%player%", player.getName());
                if (asPlayer) {
                    player.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
                } else {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
                }
            }
        }
    }

    public static final class Message implements Action {
        @Override public String type() {
            return "message";
        }

        @Override public void execute(Player player, Map<String, Object> params) {
            String text = Params.str(params.get("text"));
            if (text == null || text.isBlank()) return;
            Params.send(player, text.replace("%player%", player.getName()));
        }
    }

    public static final class Sound implements Action {
        @Override public String type() {
            return "sound";
        }

        @Override public void execute(Player player, Map<String, Object> params) {
            String name = Params.str(params.get("sound"));
            if (name == null) return;
            org.bukkit.Sound sound = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.SOUND_EVENT)
                .get(Key.key(name.toLowerCase(Locale.ROOT).replace(' ', '_')));
            if (sound == null) return;
            player.playSound(player.getLocation(), sound,
                (float) Params.doubleVal(params.get("volume"), 1.0),
                (float) Params.doubleVal(params.get("pitch"), 1.0));
        }
    }

    public static final class GiveExp implements Action {
        @Override public String type() {
            return "give-exp";
        }

        @Override public void execute(Player player, Map<String, Object> params) {
            player.giveExp(Params.intVal(params.get("amount"), 0));
        }
    }
}
