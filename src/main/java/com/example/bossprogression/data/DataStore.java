package com.example.bossprogression.data;

import com.example.bossprogression.BossProgressionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Persists runtime state in {@code data.yml}:
 * current boss tier, dead (spectator) players, and the registered altar location.
 * <p>
 * Saves are debounced: a dirty flag schedules a save on the main thread shortly
 * after the last modification, plus a periodic auto-save task.
 */
public final class DataStore {

    private final BossProgressionPlugin plugin;

    private final File file;
    private YamlConfiguration yaml;

    private int currentBossTier = 0;
    private final Set<UUID> deadPlayers = new HashSet<>();
    private Location altarLocation;

    private volatile boolean dirty = false;
    private int autoSaveTaskId = -1;

    public DataStore(BossProgressionPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFile();
    }

    /** Loads state from disk. Call once on enable. */
    public void load() {
        if (!file.exists()) {
            save();
            return;
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        this.currentBossTier = Math.max(0, yaml.getInt("current_boss_tier", 0));
        this.deadPlayers.clear();
        for (String s : yaml.getStringList("dead_players")) {
            try {
                deadPlayers.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid UUID in data.yml: " + s);
            }
        }

        String worldName = yaml.getString("altar_location.world");
        if (worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                this.altarLocation = new Location(world,
                        yaml.getDouble("altar_location.x"),
                        yaml.getDouble("altar_location.y"),
                        yaml.getDouble("altar_location.z"),
                        (float) yaml.getDouble("altar_location.yaw", 0.0),
                        (float) yaml.getDouble("altar_location.pitch", 0.0));
            }
        }

        // Start periodic auto-save.
        int minutes = plugin.configManager().getAutoSaveMinutes();
        autoSaveTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::save,
                minutes * 20L * 60L, minutes * 20L * 60L).getTaskId();

        plugin.getLogger().info(String.format("Loaded data: tier=%d, deadPlayers=%d, altar=%s",
                currentBossTier, deadPlayers.size(),
                altarLocation != null ? altarLocation.getWorld().getName() : "none"));
    }

    /** Writes state to disk synchronously. Safe to call from the main thread only. */
    public synchronized void save() {
        if (yaml == null) {
            yaml = new YamlConfiguration();
        }
        yaml.set("current_boss_tier", currentBossTier);
        yaml.set("dead_players", deadPlayers.stream().map(UUID::toString).toList());
        if (altarLocation != null) {
            yaml.set("altar_location.world", altarLocation.getWorld().getName());
            yaml.set("altar_location.x", altarLocation.getX());
            yaml.set("altar_location.y", altarLocation.getY());
            yaml.set("altar_location.z", altarLocation.getZ());
            yaml.set("altar_location.yaw", altarLocation.getYaw());
            yaml.set("altar_location.pitch", altarLocation.getPitch());
        } else {
            yaml.set("altar_location", null);
        }

        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to save data.yml", e);
        }
    }

    /** Marks the store dirty and schedules a debounced save. */
    public void markDirty() {
        dirty = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (dirty) {
                save();
            }
        });
    }

    /**
     * Stops the periodic auto-save task and performs a final synchronous save.
     * Call exactly once on disable (e.g. from {@code BossProgressionPlugin#onDisable});
     * without the task cancel a stale auto-save could overwrite freshly loaded
     * state after a {@code /reload}.
     */
    public void shutdown() {
        if (autoSaveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(autoSaveTaskId);
            autoSaveTaskId = -1;
        }
        save();
    }

    // ---- currentBossTier ----

    public int getCurrentBossTier() {
        return currentBossTier;
    }

    public void setCurrentBossTier(int tier) {
        this.currentBossTier = Math.max(0, tier);
        markDirty();
    }

    // ---- deadPlayers ----

    public Set<UUID> getDeadPlayers() {
        return new HashSet<>(deadPlayers);
    }

    public boolean isDead(UUID uuid) {
        return deadPlayers.contains(uuid);
    }

    public void markDead(UUID uuid) {
        deadPlayers.add(uuid);
        markDirty();
    }

    public void markAlive(UUID uuid) {
        deadPlayers.remove(uuid);
        markDirty();
    }

    // ---- altarLocation ----

    public Location getAltarLocation() {
        return altarLocation;
    }

    public void setAltarLocation(Location location) {
        this.altarLocation = location == null ? null : location.clone();
        markDirty();
    }
}

