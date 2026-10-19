package com.example.bossprogression.progression;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.config.BossTier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Handles the completion of a boss fight.
 * When a configured boss entity dies and its tier is the next allowed one,
 * the progression advances and the boss's revive item is injected into the
 * loot drops (deterministic, one item per kill).
 */
public final class BossDeathListener implements Listener {

    private final BossProgressionPlugin plugin;

    public BossDeathListener(BossProgressionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        BossTier tier = findBossTier(event.getEntityType());
        if (tier == null) {
            return; // not a boss from our config
        }

        if (!plugin.progressionManager().isAllowed(tier.tierNumber())) {
            // Out-of-sequence kill (e.g. admin-spawned, or tier already passed).
            // No progression change, no revive item.
            plugin.getLogger().info("Boss " + tier.entityType() + " died out of sequence - ignoring.");
            return;
        }

        // Notify third-party hooks before the core advances progression.
        plugin.hookManager().onBossDeath(event.getEntity(), tier);

        BossTier defeated = plugin.progressionManager().advance();
        if (defeated == null) {
            return;
        }

        // Inject the revive item into the boss loot.
        ItemStack reviveItem = defeated.reviveItem().createItem(plugin, 1);
        event.getDrops().add(reviveItem);

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            plugin.messages().send(player, "boss-killed.drop-message",
                    Map.of("item", itemDisplayName(defeated)));
        }

        // Hardmode: monster attributes refresh lazily on next spawn via HardmodeManager.
        plugin.getLogger().info("Boss kill processed: tier " + defeated.tierNumber()
                + " (" + defeated.entityType() + "), revive item injected.");
    }

    private BossTier findBossTier(org.bukkit.entity.EntityType type) {
        for (BossTier tier : plugin.configManager().getBossTiers()) {
            // Only entity-backed tiers (VANILLA/MYTHIC_MOB) can match a spawned entity.
            if (tier.isEntityTypeTier() && tier.entityType() == type) {
                return tier;
            }
        }
        return null;
    }

    /** Human-readable name of the revive item, used in chat messages. */
    private String itemDisplayName(BossTier tier) {
        return tier.reviveItem().getDisplayName().replace('&', '\u00a7');
    }
}
