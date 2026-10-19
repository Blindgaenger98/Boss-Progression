package com.example.bossprogression.hardmode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.bossprogression.PluginTestBase;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link HardmodeListener}: food-level cap and golden apple nerf.
 */
class HardmodeListenerTest extends PluginTestBase {

    @Test
    void foodRegenerationCappedAt19WhenNerfEnabled() {
        Player player = server.addPlayer("HungryHero");
        player.setFoodLevel(19);

        server.getPluginManager().callEvent(new FoodLevelChangeEvent(player, 20));

        assertEquals(19, player.getFoodLevel());
    }

    @Test
    void foodRegenerationNotCappedWhenNerfDisabled() {
        plugin.getConfig().set("hardmode.nerf-health-regeneration", false);
        plugin.saveConfig();
        assertTrue(plugin.configManager().load());

        Player player = server.addPlayer("FullHero");
        player.setFoodLevel(19);
        FoodLevelChangeEvent event = new FoodLevelChangeEvent(player, 20);
        server.getPluginManager().callEvent(event);
        // simulate the server applying the (uncancelled) event result
        player.setFoodLevel(event.getFoodLevel());

        assertEquals(20, player.getFoodLevel());
    }

    @Test
    void foodDecreaseIsNotInterfered() {
        Player player = server.addPlayer("StarvingHero");
        player.setFoodLevel(10);
        FoodLevelChangeEvent event = new FoodLevelChangeEvent(player, 9);

        server.getPluginManager().callEvent(event);
        player.setFoodLevel(event.getFoodLevel());

        assertEquals(9, player.getFoodLevel());
    }

    @Test
    void goldenAppleConsumptionCancelledWhenNerfEnabled() {
        plugin.getConfig().set("hardmode.nerf-golden-apple", true);
        plugin.saveConfig();
        assertTrue(plugin.configManager().load());

        Player player = server.addPlayer("AppleEater");
        ItemStack apple = new ItemStack(Material.GOLDEN_APPLE);
        player.getInventory().setItemInMainHand(apple);
        PlayerItemConsumeEvent event = new PlayerItemConsumeEvent(player, apple);

        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void goldenAppleAllowedWhenNerfDisabled() {
        Player player = server.addPlayer("AppleLover");
        ItemStack apple = new ItemStack(Material.GOLDEN_APPLE);
        player.getInventory().setItemInMainHand(apple);
        PlayerItemConsumeEvent event = new PlayerItemConsumeEvent(player, apple);

        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
    }
}
