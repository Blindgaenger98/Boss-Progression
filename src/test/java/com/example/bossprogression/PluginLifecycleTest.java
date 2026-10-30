package com.example.bossprogression;

import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void commandRegistrationFailureIsLoggedWhenCommandMissingFromPluginYml() throws Exception {
        // F2: a missing plugin.yml entry used to fail registration silently.
        // registerCommand is private and all four declared commands exist in
        // plugin.yml, so the test invokes the private method reflectively with a
        // name that is not declared - the pragmatic approach for covering the
        // failure path (documented deviation).
        List<LogRecord> records = new ArrayList<>();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
                // no-op
            }

            @Override
            public void close() {
                // no-op
            }
        };
        Logger logger = plugin.getLogger();
        logger.addHandler(capture);
        try {
            Method registerCommand = BossProgressionPlugin.class
                    .getDeclaredMethod("registerCommand", String.class, TabExecutor.class);
            registerCommand.setAccessible(true);
            registerCommand.invoke(plugin, "definitely-missing-command", null);
        } finally {
            logger.removeHandler(capture);
        }

        assertTrue(records.stream().anyMatch(r ->
                        r.getLevel().intValue() >= Level.SEVERE.intValue()
                                && String.valueOf(r.getMessage()).contains("definitely-missing-command")),
                "a severe log entry naming the missing command must be emitted");
    }

    @Test
    void reviveManagerIsAvailableThroughCentralAccessorAfterOnEnable() {
        // F3: the ReviveManager is created once in onEnable and exposed through
        // the bare-method accessor instead of being re-instantiated per command.
        assertNotNull(plugin.reviveManager(), "the central ReviveManager must be instantiated in onEnable");
        assertSame(plugin.reviveManager(), plugin.reviveManager(),
                "the accessor must return the stable instance created in onEnable");
        assertNotNull(plugin.reviveManager().validator(),
                "the central manager must expose its revive item validator");
    }

    private List<BukkitTask> pendingPluginTasks() {
        return server.getScheduler().getPendingTasks().stream()
                .filter(task -> task.getOwner() == plugin)
                .toList();
    }
}
