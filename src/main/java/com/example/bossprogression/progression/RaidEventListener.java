package com.example.bossprogression.progression;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.config.BossTier;
import com.example.bossprogression.config.TierType;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Raid;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.raid.RaidFinishEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;

/**
 * Handles RAID-tier progression.
 * <ul>
 *   <li>{@link RaidTriggerEvent}: a raid may only start while the next allowed
 *       tier is a RAID tier. Otherwise the trigger is cancelled, the player's
 *       Bad Omen / Raid Omen effects are stripped and the standard penalty applies
 *       (admins are exempt from the penalty but still blocked).</li>
 *   <li>{@link RaidFinishEvent}: a raid victory advances the progression if the
 *       raid reached the configured minimum wave count
 *       ({@code getTotalWaves() >= requiredRaidWaves}, 0 = no threshold).
 *       Each winner receives the revive item in their inventory; overflow is
 *       dropped at the raid center.</li>
 * </ul>
 */
public final class RaidEventListener implements Listener {

    private final BossProgressionPlugin plugin;

    public RaidEventListener(BossProgressionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRaidTrigger(RaidTriggerEvent event) {
        BossTier next = plugin.progressionManager().getNextAllowedBoss();
        if (next != null && next.type() == TierType.RAID) {
            return; // raid is the current progression step - allow it
        }

        Player player = event.getPlayer();
        event.setCancelled(true);
        player.removePotionEffect(PotionEffectType.BAD_OMEN);
        player.removePotionEffect(PotionEffectType.RAID_OMEN);

        if (next != null) {
            plugin.penaltyApplier().applyTo(player, next);
            player.sendActionBar(LegacyComponentSerializer.legacySection()
                    .deserialize("&c" + next.displayName().replace('&', '§') + " is not ready yet!"));
        }
        plugin.messages().send(player, "raid.trigger-blocked");
        plugin.getLogger().info("Raid trigger by " + player.getName() + " blocked (not the current progression step).");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRaidFinish(RaidFinishEvent event) {
        BossTier next = plugin.progressionManager().getNextAllowedBoss();
        if (next == null || next.type() != TierType.RAID) {
            return; // out-of-sequence raid - no progression
        }

        Raid raid = event.getRaid();
        if (raid.getTotalWaves() < next.requiredRaidWaves()) {
            plugin.getLogger().info("Raid finished with " + raid.getTotalWaves() + " waves, required at least "
                    + next.requiredRaidWaves() + " - victory does not count.");
            return;
        }

        BossTier defeated = plugin.progressionManager().advance();
        if (defeated == null) {
            return;
        }

        for (Player winner : event.getWinners()) {
            ItemStack reviveItem = defeated.reviveItem().createItem(plugin, 1);
            Map<Integer, ItemStack> overflow = winner.getInventory().addItem(reviveItem);
            if (!overflow.isEmpty()) {
                for (ItemStack leftover : overflow.values()) {
                    winner.getWorld().dropItemNaturally(raid.getLocation(), leftover);
                }
            }
            plugin.messages().send(winner, "raid-cleared.winner-item",
                    Map.of("item", itemDisplayName(defeated)));
        }

        plugin.getLogger().info("Raid victory processed: tier " + defeated.tierNumber()
                + " (" + next.requiredRaidWaves() + " wave threshold met), revive item delivered to winners.");
    }

    /** Human-readable name of the revive item, used in chat messages. */
    private String itemDisplayName(BossTier tier) {
        return tier.reviveItem().getDisplayName().replace('&', '§');
    }
}
