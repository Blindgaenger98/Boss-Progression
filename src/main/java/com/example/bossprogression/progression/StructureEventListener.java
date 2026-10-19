package com.example.bossprogression.progression;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.config.BossTier;
import com.example.bossprogression.config.StructureKind;
import com.example.bossprogression.config.TierType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.TrialSpawner;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.TrialSpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Handles {@code STRUCTURE_CLEAR} progression tiers (Phase 2: Trial Chambers only;
 * {@link StructureKind#TEMPLE} is modeled in config but has no event logic in Phase 2).
 *
 * <p><b>API note (Paper 26.3):</b> There is no {@code TrialSpawnerStateChangeEvent} in this
 * build, so spawner state transitions are detected by polling the BlockData of tracked
 * spawner locations. Spawner locations are discovered via {@link TrialSpawnerSpawnEvent}
 * (entities spawned by a spawner) and via player interaction with
 * {@link Material#TRIAL_SPAWNER} blocks. A spawner counts as "cleared" when its state
 * transitions from {@link TrialSpawner.State#ACTIVE} to
 * {@link TrialSpawner.State#WAITING_FOR_REWARD_EJECTION} (or
 * {@link TrialSpawner.State#EJECTING_REWARD}). Counts are kept in memory only and reset
 * when the counted tier is no longer the next allowed tier (documented behavior: a server
 * restart resets progress).</p>
 */
public final class StructureEventListener implements Listener {

    private static final Set<TrialSpawner.State> CLEARED_STATES = EnumSet.of(
            TrialSpawner.State.WAITING_FOR_REWARD_EJECTION,
            TrialSpawner.State.EJECTING_REWARD);

    private static final int POLL_PERIOD_TICKS = 40; // ~2s

    private final BossProgressionPlugin plugin;
    private final Set<Location> trackedSpawners = new HashSet<>();
    private final Map<Location, TrialSpawner.State> lastStates = new HashMap<>();
    private final Map<Integer, Set<Location>> clearedByTier = new HashMap<>();
    private BukkitTask pollTask;

    public StructureEventListener(BossProgressionPlugin plugin) {
        this.plugin = plugin;
    }

    /** Starts the background poll task. Called from {@code BossProgressionPlugin#onEnable}. */
    public void startPolling() {
        if (pollTask != null) {
            return;
        }
        pollTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::pollSpawners,
                POLL_PERIOD_TICKS, POLL_PERIOD_TICKS);
    }

    /** Stops the background poll task. Called from {@code BossProgressionPlugin#onDisable}. */
    public void stopPolling() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrialSpawnerSpawn(TrialSpawnerSpawnEvent event) {
        // Discovery: entities spawned by a trial spawner reveal its location.
        trackSpawner(event.getTrialSpawner().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() == Action.PHYSICAL) {
            return; // not a use-interaction
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.TRIAL_SPAWNER) {
            return;
        }
        trackSpawner(clicked.getLocation());

        BossTier next = plugin.progressionManager().getNextAllowedBoss();
        boolean structureStep = next != null && next.type() == TierType.STRUCTURE_CLEAR
                && next.structureKind() == StructureKind.TRIAL_CHAMBER;
        if (structureStep || next == null) {
            return; // structure is the current progression step (or no progression active) - allow
        }
        if (player.hasPermission("bossplugin.admin")) {
            return; // admins are exempt
        }

        event.setCancelled(true);
        plugin.penaltyApplier().applyTo(player, next);
        plugin.messages().send(player, "structure.interact-blocked");
        plugin.getLogger().info("Trial spawner interaction by " + player.getName() + " blocked (structure tier not active).");
    }

    /**
     * Polls all tracked trial spawner locations for a cleared-state transition and advances
     * progression once the configured number of distinct spawners has been cleared.
     * Package-private for direct invocation in tests.
     */
    void pollSpawners() {
        BossTier next = plugin.progressionManager().getNextAllowedBoss();
        boolean structureStep = next != null && next.type() == TierType.STRUCTURE_CLEAR
                && next.structureKind() == StructureKind.TRIAL_CHAMBER;

        // Reset in-memory counts for tiers that are no longer the current progression step.
        if (!structureStep) {
            clearedByTier.clear();
        }

        for (Iterator<Location> it = trackedSpawners.iterator(); it.hasNext(); ) {
            Location loc = it.next();
            Block block = loc.getBlock();
            if (block.getBlockData() instanceof TrialSpawner data) {
                TrialSpawner.State state = data.getTrialSpawnerState();
                TrialSpawner.State prev = lastStates.put(loc, state);
                if (structureStep && prev != null && prev == TrialSpawner.State.ACTIVE
                        && CLEARED_STATES.contains(state)) {
                    markCleared(next, loc);
                    it.remove(); // done for this tier cycle; re-discovered on next spawn
                }
            } else {
                it.remove(); // no longer a trial spawner
                lastStates.remove(loc);
            }
        }
    }

    private void markCleared(BossTier tier, Location loc) {
        Set<Location> cleared = clearedByTier.computeIfAbsent(tier.tierNumber(), k -> new HashSet<>());
        if (!cleared.add(loc)) {
            return; // already counted
        }
        int count = cleared.size();
        if (count < tier.requiredSpawnerClears()) {
            plugin.messages().broadcast("structure.cleared.progress", Map.of(
                    "count", String.valueOf(count),
                    "required", String.valueOf(tier.requiredSpawnerClears())));
            plugin.getLogger().info("Trial spawner cleared at " + loc.getWorld().getName() + ":" + loc.getBlockX()
                    + "," + loc.getBlockY() + "," + loc.getBlockZ()
                    + " (" + count + "/" + tier.requiredSpawnerClears() + " for tier " + tier.tierNumber() + ").");
            return;
        }

        BossTier defeated = plugin.progressionManager().advance();
        if (defeated == null) {
            return;
        }
        loc.getWorld().dropItemNaturally(loc, defeated.reviveItem().createItem(plugin, 1));
        plugin.messages().broadcast("structure.cleared.item-dropped");
        plugin.getLogger().info("Structure tier " + defeated.tierNumber() + " cleared (" + count
                + " spawners); revive item dropped at " + loc.getWorld().getName() + ":" + loc.getBlockX()
                + "," + loc.getBlockY() + "," + loc.getBlockZ() + ".");
        clearedByTier.clear();
    }

    /** Registers a trial spawner location for state polling. */
    private void trackSpawner(Location loc) {
        trackedSpawners.add(loc);
    }
}
