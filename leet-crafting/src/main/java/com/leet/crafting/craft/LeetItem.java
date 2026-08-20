package com.leet.crafting.craft;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * A single custom item: an id, a base {@link Material}, display name/lore, an
 * optional hunger/saturation (food), and a {@code leet:item/<id>} client model.
 *
 * <p>Generic and shared: any crafting/recipe feature can register items here and
 * any recipe may reference them by id. The {@code ci} PersistentData tag marks
 * the item so recipes can match it exactly.
 */
public record LeetItem(
    String id,
    String name,
    Material material,
    List<String> lore,
    int hunger,
    int saturation) {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public boolean isFood() {
        return hunger > 0;
    }

    /**
     * Builds the actual held {@link ItemStack}: base material + name/lore +
     * {@code ci} PDC tag + {@code leet:item/<id>} item model. When the item is
     * food, a real FOOD data component is set so it is edible server-side.
     */
    public ItemStack create(NamespacedKey ciKey) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MM.deserialize("<yellow>" + name));
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore.stream().map(l -> MM.deserialize("<gray>" + l)).toList());
        }
        meta.getPersistentDataContainer().set(ciKey, PersistentDataType.STRING, id);
        meta.setItemModel(new NamespacedKey("leet", "item/" + id));
        stack.setItemMeta(meta);
        if (isFood()) {
            stack.setData(io.papermc.paper.datacomponent.DataComponentTypes.FOOD,
                io.papermc.paper.datacomponent.item.FoodProperties.food()
                    .nutrition(hunger)
                    .saturation((float) saturation)
                    .build());
        }
        return stack;
    }
}
