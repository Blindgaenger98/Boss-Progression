package com.example.bossprogression.revive;

import com.example.bossprogression.PluginTestBase;
import com.example.bossprogression.config.BossTier;
import com.example.bossprogression.config.ReviveItemConfig;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviveItemValidatorTest extends PluginTestBase {

    private ReviveItemValidator validator() {
        return new ReviveItemValidator(plugin);
    }

    private ReviveItemConfig witherConfig() {
        return new ReviveItemConfig(Material.NETHER_STAR, 1002, "&3Wither Core", "revive", "tier2");
    }

    @Test
    void latestUnlockedTierIsNullAtTierZero() {
        assertNull(validator().latestUnlockedTier());
        assertFalse(validator().matchesCurrentTier(new ItemStack(Material.NETHER_STAR)));
    }

    @Test
    void createdItemMatchesCurrentTierViaPdc() {
        plugin.progressionManager().setTier(1);
        BossTier tier1 = plugin.configManager().getTier(1);
        assertNotNull(tier1);

        ItemStack item = tier1.reviveItem().createItem(plugin, 1);
        assertTrue(validator().matchesCurrentTier(item));
        assertTrue(validator().matches(item, tier1.reviveItem()));
    }

    @Test
    void wrongMaterialDoesNotMatch() {
        ReviveItemConfig config = witherConfig();
        assertFalse(validator().matches(new ItemStack(Material.DIAMOND), config));
    }

    @Test
    void wrongPdcValueDoesNotMatch() {
        ReviveItemConfig config = witherConfig();
        ItemStack item = config.createItem(plugin, 1);
        // Tamper the PDC value after creation.
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, config.getPdcKey()),
                org.bukkit.persistence.PersistentDataType.STRING,
                "wrong");
        item.setItemMeta(meta);
        assertFalse(validator().matches(item, config));
    }

    @Test
    void cmdOnlyConfigMatchesByCustomModelData() {
        ReviveItemConfig config = new ReviveItemConfig(Material.SCULK_CATALYST, 1001, "&5Soul", null, null);
        ItemStack item = config.createItem(plugin, 1);
        assertTrue(validator().matches(item, config));

        ItemStack wrongCmd = config.createItem(plugin, 1);
        var meta = wrongCmd.getItemMeta();
        meta.setCustomModelData(9999);
        wrongCmd.setItemMeta(meta);
        assertFalse(validator().matches(wrongCmd, config));
    }

    @Test
    void displayNameFallbackMatches() {
        ReviveItemConfig config = new ReviveItemConfig(Material.ENDER_EYE, 0, "&2Eye", null, null);
        ItemStack item = config.createItem(plugin, 1);
        assertTrue(validator().matches(item, config));

        ItemStack vanilla = new ItemStack(Material.ENDER_EYE);
        assertFalse(validator().matches(vanilla, config));
    }

    @Test
    void consumeOneDecrementsStack() {
        ReviveItemConfig config = witherConfig();
        ItemStack[] contents = { config.createItem(plugin, 3), new ItemStack(Material.STONE) };

        assertTrue(validator().consumeOne(contents, config));
        assertEquals(2, contents[0].getAmount());

        assertTrue(validator().consumeOne(contents, config));
        assertTrue(validator().consumeOne(contents, config));
        assertNull(contents[0]);

        assertFalse(validator().consumeOne(contents, config));
    }

    @Test
    void findMatchReturnsFirstMatchingSlot() {
        ReviveItemConfig config = witherConfig();
        ItemStack[] contents = { new ItemStack(Material.STONE), config.createItem(plugin, 1) };
        Optional<Integer> match = validator().findMatch(contents, config);
        assertTrue(match.isPresent());
        assertEquals(1, match.get());
    }
}
