package com.example.bossprogression;

import com.example.bossprogression.altar.AltarListener;
import com.example.bossprogression.altar.AltarManager;
import com.example.bossprogression.command.AltarCommand;
import com.example.bossprogression.command.BossAdminCommand;
import com.example.bossprogression.command.BossStatusCommand;
import com.example.bossprogression.command.ReviveCommand;
import com.example.bossprogression.config.ConfigManager;
import com.example.bossprogression.data.DataStore;
import com.example.bossprogression.hook.BossHookManager;
import com.example.bossprogression.hardmode.HardmodeListener;
import com.example.bossprogression.permadeath.PermadeathListener;
import com.example.bossprogression.progression.BossDeathListener;
import com.example.bossprogression.progression.PenaltyApplier;
import com.example.bossprogression.progression.ProgressionManager;
import com.example.bossprogression.progression.RaidEventListener;
import com.example.bossprogression.progression.SpawnGuardListener;
import com.example.bossprogression.progression.StructureEventListener;
import com.example.bossprogression.util.Messages;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Logger;

/**
 * Main plugin class. Wires up configuration, data persistence, managers and listeners.
 */
public class BossProgressionPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private DataStore dataStore;
    private Messages messages;
    private ProgressionManager progressionManager;
    private PenaltyApplier penaltyApplier;
    private StructureEventListener structureEventListener;
    private AltarManager altarManager;
    private BossHookManager hookManager;

    @Override
    public void onEnable() {
        Logger logger = getLogger();
        logger.info("Enabling BossProgression v" + getDescription().getVersion());

        // Save default configs on first run.
        saveDefaultConfig();
        saveResource("messages.yml", false);

        this.messages = new Messages(this);
        this.configManager = new ConfigManager(this);
        if (!configManager.load()) {
            logger.severe("Invalid configuration - disabling plugin. Please fix config.yml and restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.dataStore = new DataStore(this);
        dataStore.load();

        this.progressionManager = new ProgressionManager(this);
        this.penaltyApplier = new PenaltyApplier(this);

        // Listeners.
        var pm = getServer().getPluginManager();
        pm.registerEvents(new SpawnGuardListener(this), this);
        pm.registerEvents(new BossDeathListener(this), this);
        pm.registerEvents(new PermadeathListener(this), this);
        pm.registerEvents(new HardmodeListener(this), this);
        pm.registerEvents(new RaidEventListener(this), this);
        this.structureEventListener = new StructureEventListener(this);
        pm.registerEvents(structureEventListener, this);
        structureEventListener.startPolling();

        this.altarManager = new AltarManager(this);
        pm.registerEvents(new AltarListener(this), this);

        // Commands.
        registerCommand("boss", new BossStatusCommand(this));
        registerCommand("altar", new AltarCommand(this));
        registerCommand("revive", new ReviveCommand(this));
        registerCommand("bossadmin", new BossAdminCommand(this));

        // Third-party boss hooks (Phase 2: MythicMobs; currently vanilla-only).
        this.hookManager = new BossHookManager(this);
        hookManager.discoverAndEnable();
        if (!hookManager.hasHooks()) {
            logger.info("Running in Vanilla-only mode (no boss hooks active).");
        }

        logger.info("BossProgression enabled. Current tier: " + progressionManager.getCurrentTier());
    }

    @Override
    public void onDisable() {
        if (structureEventListener != null) {
            structureEventListener.stopPolling();
        }
        if (hookManager != null) {
            hookManager.disableAll();
        }
        if (altarManager != null) {
            altarManager.close();
        }
        if (dataStore != null) {
            // Finalizes persistence (cancel auto-save task + final save) so a /reload
            // cannot overwrite freshly loaded data.yml with a stale auto-save.
            dataStore.shutdown();
        }
        getLogger().info("Disabling BossProgression");
    }

    private void registerCommand(String name, org.bukkit.command.TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    // ---- Accessors ----

    public ConfigManager configManager() {
        return configManager;
    }

    public DataStore dataStore() {
        return dataStore;
    }

    public Messages messages() {
        return messages;
    }

    public ProgressionManager progressionManager() {
        return progressionManager;
    }

    public PenaltyApplier penaltyApplier() {
        return penaltyApplier;
    }

    public StructureEventListener structureEventListener() {
        return structureEventListener;
    }

    public AltarManager altarManager() {
        return altarManager;
    }

    public BossHookManager hookManager() {
        return hookManager;
    }

    /** Convenience: data file location, used by DataStore. */
    public File getDataFile() {
        return new File(getDataFolder(), "data.yml");
    }
}
