package com.example.bossprogression;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lifecycle coverage for the {@code /reload} data-loss fix: disabling the plugin
 * must cancel the periodic auto-save task (via
 * {@link com.example.bossprogression.data.DataStore#shutdown()}) so a stale
 * auto-save cannot overwrite a freshly loaded {@code data.yml}, and the final
 * save must persist the current tier.
 *
 * <p>Note on setup: on a fresh install {@code DataStore#load} takes its
 * early-return path (file missing) and never schedules the auto-save timer.
 * Disabling and re-enabling the plugin therefore simulates a real second boot:
 * with {@code data.yml} present, {@code load()} starts the timer.
 */
class PluginLifecycleTest extends PluginTestBase {

    @Test
    void onDisableCancelsAutoSaveTaskAndPersistsData() {
        // Fresh (first) load: data.yml is created, but no auto-save timer runs.
        server.getPluginManager().disablePlugin(plugin);

        // Second boot (as after /reload): data.yml exists, so DataStore#load
        // schedules the auto-save timer. Paper's JavaPlugin#setEnabled(true)
        // invokes onEnable() itself, so a single call re-runs the whole startup.
        plugin.setEnabled(true);

        // Exactly two plugin-owned tasks pending: the auto-save timer
        // (scheduled by DataStore#load) and the structure poll task
        // (StructureEventListener#startPolling).
        assertEquals(2, pendingPluginTasks().size(),
                "expected the auto-save timer and the structure poll task to be pending after (re)enable");

        // Mutate state the way gameplay would, then disable (the /reload path).
        plugin.dataStore().setCurrentBossTier(3);
        server.getPluginManager().disablePlugin(plugin);

        // No plugin-owned task may remain pending: auto-save timer, poll task and
        // the dirty-save task scheduled by markDirty are all cancelled.
        assertTrue(pendingPluginTasks().isEmpty(),
                "expected no pending scheduler tasks owned by the plugin after onDisable");

        // Data must survive the reload: the final save in DataStore#shutdown wrote tier 3.
        File dataFile = plugin.getDataFile();
        assertTrue(dataFile.exists(), "data.yml must exist after the final save in onDisable");
        YamlConfiguration reloaded = YamlConfiguration.loadConfiguration(dataFile);
        assertEquals(3, reloaded.getInt("current_boss_tier", -1),
                "final save in shutdown must persist the current tier");
    }

    private List<BukkitTask> pendingPluginTasks() {
        return server.getScheduler().getPendingTasks().stream()
                .filter(task -> task.getOwner() == plugin)
                .toList();
    }
}
