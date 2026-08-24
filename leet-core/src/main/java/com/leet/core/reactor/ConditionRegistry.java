package com.leet.core.reactor;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Type name -> Condition, with generic built-ins ({@code world}, {@code chance},
 * {@code has-item}). Feature plugins register domain conditions (reputation,
 * skill-level, ...) through the same registry at boot.
 */
public final class ConditionRegistry {

    private final Map<String, Condition> conditions = new LinkedHashMap<>();

    public ConditionRegistry() {
        register(new WorldCondition());
        register(new ChanceCondition());
        register(new HasItemCondition());
    }

    public void register(Condition condition) {
        conditions.put(condition.type(), condition);
    }

    public Condition get(String type) {
        return type == null ? null : conditions.get(type.toLowerCase());
    }

    private static final class WorldCondition implements Condition {
        @Override public String type() {
            return "world";
        }

        @Override @SuppressWarnings("unchecked")
        public boolean passes(Player player, Map<String, Object> params) {
            Object o = params.get("worlds");
            List<String> worlds = o instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of();
            return worlds.isEmpty() || worlds.contains(player.getWorld().getName());
        }
    }

    private static final class ChanceCondition implements Condition {
        @Override public String type() {
            return "chance";
        }

        @Override public boolean passes(Player player, Map<String, Object> params) {
            double chance = Params.doubleVal(params.get("value"), 1.0);
            return chance >= 1.0 || Math.random() < chance;
        }
    }

    private static final class HasItemCondition implements Condition {
        @Override public String type() {
            return "has-item";
        }

        @Override public boolean passes(Player player, Map<String, Object> params) {
            String spec = Params.str(params.get("item"));
            int amount = Params.intVal(params.get("amount"), 1);
            if (spec == null) return true;
            ItemStack ref = ItemSpec.build(spec, Math.max(1, amount));
            return ref != null && ItemSpec.count(player.getInventory(), ref) >= amount;
        }
    }
}
