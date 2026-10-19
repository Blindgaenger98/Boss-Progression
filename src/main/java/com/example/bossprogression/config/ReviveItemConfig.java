package com.example.bossprogression.config;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.ChatColor;

/**
 * Immutable description of the revive item a boss tier drops.
 * Matching priority: PDC tag > custom model data > display name.
 */
public final class ReviveItemConfig {

    private final Material material;
    private final int customModelData;
    private final String displayName;
    private final String pdcKey;
    private final String pdcValue;

    public ReviveItemConfig(Material material, int customModelData, String displayName,
                            String pdcKey, String pdcValue) {
        this.material = material;
        this.customModelData = customModelData;
        this.displayName = displayName;
        this.pdcKey = pdcKey;
        this.pdcValue = pdcValue;
    }

    public Material getMaterial() {
        return material;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPdcKey() {
        return pdcKey;
    }

    public String getPdcValue() {
        return pdcValue;
    }

    /**
     * Creates the actual item instance, stamped with PDC tag, CMD and display name.
     *
     * @param plugin owning plugin used to construct the PDC NamespacedKey
     * @param amount stack size
     */
    public ItemStack createItem(Plugin plugin, int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            if (pdcKey != null && !pdcKey.isEmpty()) {
                meta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, pdcKey), PersistentDataType.STRING, pdcValue);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }
}
