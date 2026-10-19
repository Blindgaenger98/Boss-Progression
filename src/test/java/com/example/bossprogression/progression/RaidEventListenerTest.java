package com.example.bossprogression.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.bossprogression.PluginTestBase;
import com.example.bossprogression.config.TierType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.raid.RaidFinishEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Raid;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.event.RaidMock;

import java.util.List;
import java.util.Map;

/**
 * Tests for {@link RaidEventListener}: raid trigger gating and raid-finish victory.
 */
class RaidEventListenerTest extends PluginTestBase {

    private static final int RAID_TIER = 4;

    private void setupRaidTier() {
        plugin.getConfig().set("boss-tiers.town-raid", Map.of(
                "tier", RAID_TIER,
                "type", "RAID",
                "required-raid-waves", 6,
                "revive-item", Map.of(
                        "material", "EMERALD",
                        "pdc-key", "revive",
                        "pdc-value", "tier4")));
        plugin.saveConfig();
        assertTrue(plugin.configManager().load());
    }

    private Raid raidAtCenter() {
        return new RaidMock(1, new org.bukkit.Location(world, 0, 64, 0));
    }

    @Test
    void raidTriggerAllowedWhenRaidTierIsNext() {
        setupRaidTier();
        assertTrue(plugin.progressionManager().setTier(RAID_TIER - 1));
        Player player = server.addPlayer("RaidHero");
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.BAD_OMEN, 120000, 0));

        Raid raid = raidAtCenter();
        RaidTriggerEvent event = new RaidTriggerEvent(raid, world, player);
        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
        assertTrue(player.hasPotionEffect(PotionEffectType.BAD_OMEN));
    }

    @Test
    void raidTriggerBlockedWhenRaidTierIsNotNext() {
        setupRaidTier();
        assertTrue(plugin.progressionManager().setTier(0));
        Player player = server.addPlayer("RaidHero");
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.BAD_OMEN, 120000, 0));

        RaidTriggerEvent event = new RaidTriggerEvent(raidAtCenter(), world, player);
        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        assertFalse(player.hasPotionEffect(PotionEffectType.BAD_OMEN));
        assertFalse(player.hasPotionEffect(PotionEffectType.RAID_OMEN));
        assertTrue(player.hasPotionEffect(PotionEffectType.WITHER));
    }

    @Test
    void raidTriggerBlockedButAdminExemptFromPenalty() {
        setupRaidTier();
        assertTrue(plugin.progressionManager().setTier(0));
        Player player = server.addPlayer("Admin");
        player.addAttachment(plugin, "bossplugin.admin", true);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.BAD_OMEN, 120000, 0));

        RaidTriggerEvent event = new RaidTriggerEvent(raidAtCenter(), world, player);
        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        assertFalse(player.hasPotionEffect(PotionEffectType.WITHER));
    }

    @Test
    void raidFinishBelowWaveThresholdDoesNotAdvance() {
        setupRaidTier();
        assertTrue(plugin.progressionManager().setTier(RAID_TIER - 1));
        Player winner = server.addPlayer("RaidHero");

        Raid raid = raidAtCenter();
        raid.setTotalWaves(3);
        server.getPluginManager().callEvent(new RaidFinishEvent(raid, world, List.of(winner)));

        assertEquals(RAID_TIER - 1, plugin.dataStore().getCurrentBossTier());
        assertFalse(winner.getInventory().contains(Material.EMERALD));
    }

    @Test
    void raidFinishAtThresholdAdvancesAndGivesReviveItem() {
        setupRaidTier();
        assertTrue(plugin.progressionManager().setTier(RAID_TIER - 1));
        Player winner = server.addPlayer("RaidHero");

        Raid raid = raidAtCenter();
        raid.setTotalWaves(6);
        server.getPluginManager().callEvent(new RaidFinishEvent(raid, world, List.of(winner)));

        assertEquals(RAID_TIER, plugin.dataStore().getCurrentBossTier());

        NamespacedKey pdcKey = new NamespacedKey(plugin, "revive");
        boolean found = false;
        for (ItemStack item : winner.getInventory().getContents()) {
            if (item == null || item.getType() != Material.EMERALD) {
                continue;
            }
            String value = item.getItemMeta().getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
            assertNotNull(value);
            if ("tier4".equals(value)) {
                found = true;
            }
        }
        assertTrue(found, "winner inventory should contain the RAID tier revive item with PDC value tier4");
    }

    @Test
    void raidFinishIgnoredWhenNextTierIsNotRaid() {
        setupRaidTier();
        assertTrue(plugin.progressionManager().setTier(0));
        Player winner = server.addPlayer("RaidHero");

        Raid raid = raidAtCenter();
        raid.setTotalWaves(6);
        server.getPluginManager().callEvent(new RaidFinishEvent(raid, world, List.of(winner)));

        assertEquals(0, plugin.dataStore().getCurrentBossTier());
        assertFalse(winner.getInventory().contains(Material.EMERALD));
    }
}
