package com.example.bossprogression.revive;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.config.BossTier;
import com.example.bossprogression.config.ReviveItemConfig;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/**
 * Validates whether an item matches the revive item of a given boss tier.
 * Matching priority (per item): PDC tag > custom model data > display name.
 * The item must always correspond to the <b>latest unlocked boss tier</b>
 * (the most recently defeated boss), not any arbitrary tier.
 */
public final class ReviveItemValidator {

    private final BossProgressionPlugin plugin;

    public ReviveItemValidator(BossProgressionPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns true when the given item matches the revive item of the latest
     * unlocked tier (the highest tier number <= current progression tier).
     */
    public boolean matchesCurrentTier(ItemStack item) {
        BossTier tier = latestUnlockedTier();
        return tier != null && matches(item, tier.reviveItem());
    }

    /**
     * @return the highest tier number that has been defeated, or null when
     *         no boss has been defeated yet (preparation phase).
     */
    public BossTier latestUnlockedTier() {
        int current = plugin.progressionManager().getCurrentTier();
        if (current <= 0) {
            return null;
        }
        return plugin.configManager().getTier(current);
    }

    /**
     * Core matching logic against a specific tier's revive item config.
     * Priority: PDC > CMD > display name.
     */
    public boolean matches(ItemStack item, ReviveItemConfig expected) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (item.getType() != expected.getMaterial()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            // Bare material without meta: only valid if the config expects no meta at all.
            return expected.getCustomModelData() == 0
                    && (expected.getPdcKey() == null || expected.getPdcKey().isEmpty())
                    && expected.getDisplayName() == null;
        }

        // 1) PDC tag (strongest signal, plugin-owned).
        if (expected.getPdcKey() != null && !expected.getPdcKey().isEmpty()) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            String actual = container.get(new NamespacedKey(plugin, expected.getPdcKey()),
                    PersistentDataType.STRING);
            return expected.getPdcValue().equals(actual);
        }

        // 2) Custom model data.
        if (expected.getCustomModelData() > 0) {
            return meta.hasCustomModelData() && meta.getCustomModelData() == expected.getCustomModelData();
        }

        // 3) Display name (fallback, most fragile).
        String expectedName = ChatColor.translateAlternateColorCodes('&',
                expected.getDisplayName() == null ? "" : expected.getDisplayName());
        return expectedName.equals(meta.getDisplayName());
    }

    /**
     * Consumes one matching item from the player's inventory (main inventory only).
     *
     * @return true when an item was found and removed
     */
    public boolean consumeOne(ItemStack[] contents, ReviveItemConfig expected) {
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && matches(item, expected)) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    contents[i] = null;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Locates the stack index of a matching item in the given contents.
     *
     * @return Optional index of the first matching stack
     */
    public Optional<Integer> findMatch(ItemStack[] contents, ReviveItemConfig expected) {
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && matches(contents[i], expected)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }
}
