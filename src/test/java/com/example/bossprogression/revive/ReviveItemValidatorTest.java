package com.example.bossprogression.revive;

import com.example.bossprogression.PluginTestBase;
import com.example.bossprogression.config.BossTier;
import com.example.bossprogression.config.ReviveItemConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

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
    void displayNameFallbackMatchesComponentSetManually() {
        // Paper 26.x: ItemMeta#displayName() is an Adventure Component — the
        // validator must compare Component to Component.
        ReviveItemConfig config = new ReviveItemConfig(Material.ENDER_EYE, 0, "&2Eye", null, null);

        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        // '&2' in the config is dark green — the manually built Component must
        // use the same color to match.
        meta.displayName(Component.text("Eye").color(NamedTextColor.DARK_GREEN));
        item.setItemMeta(meta);
        assertTrue(validator().matches(item, config),
                "manually set Component display name must match the config display name");

        // Deviating display name: no match via the display-name path.
        ItemStack wrong = new ItemStack(Material.ENDER_EYE);
        ItemMeta wrongMeta = wrong.getItemMeta();
        assertNotNull(wrongMeta);
        wrongMeta.displayName(Component.text("Soul").color(NamedTextColor.GREEN));
        wrong.setItemMeta(wrongMeta);
        assertFalse(validator().matches(wrong, config),
                "deviating display name must not match");
    }

    @Test
    void nullDisplayNameOnItemFallsBackWithoutCrash() {
        ReviveItemConfig config = new ReviveItemConfig(Material.ENDER_EYE, 0, "&2Eye", null, null);
        // Item with meta but no display name: must not crash, simply no match.
        ItemStack unnamed = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = unnamed.getItemMeta();
        assertNotNull(meta);
        unnamed.setItemMeta(meta); // ensure meta is non-null but has no display name
        assertFalse(validator().matches(unnamed, config),
                "item without display name must not match a config expecting one");

        // Config expecting no display name: unnamed item matches, named item does not.
        ReviveItemConfig bare = new ReviveItemConfig(Material.ENDER_EYE, 0, null, null, null);
        assertTrue(validator().matches(unnamed, bare),
                "item without display name must match a config expecting none");

        ItemStack named = config.createItem(plugin, 1);
        assertFalse(validator().matches(named, bare),
                "item with display name must not match a config expecting none");
    }

    @Test
    void createItemProducesExpectedDisplayNameComponent() {
        ReviveItemConfig config = new ReviveItemConfig(Material.NETHER_STAR, 1002, "&3Wither Core", "revive", "tier2");
        ItemStack item = config.createItem(plugin, 1);

        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        assertTrue(meta.hasDisplayName());
        Component actual = meta.displayName();
        assertNotNull(actual);
        // Serialize back to legacy: the config's '&'-codes must round-trip.
        assertEquals(ChatColor.translateAlternateColorCodes('&', config.getDisplayName()),
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection().serialize(actual));

        // Null display name in config: item is created without a display name (no NPE).
        ReviveItemConfig noName = new ReviveItemConfig(Material.NETHER_STAR, 0, null, "revive", "tier2");
        ItemStack bareItem = noName.createItem(plugin, 1);
        ItemMeta bareMeta = bareItem.getItemMeta();
        assertNotNull(bareMeta);
        assertFalse(bareMeta.hasDisplayName());
    }

    @Test
    void consumeOneDecrementsStackInRealInventory() {
        ReviveItemConfig config = witherConfig();
        PlayerMock player = server.addPlayer("Looter");
        player.getInventory().setItem(0, config.createItem(plugin, 3));
        player.getInventory().setItem(1, new ItemStack(Material.STONE));

        assertTrue(validator().consumeOne(player.getInventory(), config));
        ItemStack stack = player.getInventory().getItem(0);
        assertNotNull(stack);
        assertEquals(2, stack.getAmount());

        assertTrue(validator().consumeOne(player.getInventory(), config));
        assertTrue(validator().consumeOne(player.getInventory(), config));
        assertNull(player.getInventory().getItem(0));

        assertFalse(validator().consumeOne(player.getInventory(), config));
    }

    @Test
    void consumeSingleItemClearsSlot() {
        ReviveItemConfig config = witherConfig();
        PlayerMock player = server.addPlayer("Solo");
        player.getInventory().setItem(0, config.createItem(plugin, 1));
        player.getInventory().setItem(5, new ItemStack(Material.STONE));

        assertTrue(validator().consumeOne(player.getInventory(), config));
        assertNull(player.getInventory().getItem(0));

        // Rest of the inventory stays untouched.
        ItemStack stone = player.getInventory().getItem(5);
        assertNotNull(stone);
        assertEquals(Material.STONE, stone.getType());
        assertEquals(1, stone.getAmount());
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
