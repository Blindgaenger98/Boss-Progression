package com.example.bossprogression.hook;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.config.BossTier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;

/**
 * Registry and lifecycle manager for {@link BossHook} implementations.
 *
 * <p>Phase 1 (vanilla only) registers no hooks; this manager is the extension point
 * for Phase 2 (MythicMobs). All hook invocations are guarded so that a failing hook
 * never interrupts core gameplay.</p>
 */
public final class BossHookManager {

    private final BossProgressionPlugin plugin;
    private final List<BossHook> hooks = new java.util.ArrayList<>();

    public BossHookManager(BossProgressionPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Discovers and enables all available hooks based on installed plugins.
     */
    public void discoverAndEnable() {
        PluginManager pm = plugin.getServer().getPluginManager();

        if (pm.isPluginEnabled("MythicMobs")) {
            hooks.add(new MythicMobsHook());
        }
        // Future hooks: check other soft-depends here.

        for (BossHook hook : hooks) {
            try {
                hook.onEnable(plugin);
                plugin.getLogger().info("Enabled boss hook: " + hook.id());
            } catch (Throwable t) {
                plugin.getLogger().severe("Failed to enable boss hook " + hook.id() + ": " + t.getMessage());
            }
        }
    }

    public void disableAll() {
        for (BossHook hook : hooks) {
            try {
                hook.onDisable();
            } catch (Throwable t) {
                plugin.getLogger().warning("Error disabling boss hook " + hook.id() + ": " + t.getMessage());
            }
        }
        hooks.clear();
    }

    /** @return the first hook that claims the given entity type, if any. */
    public Optional<BossHook> hookFor(EntityType type) {
        return hooks.stream().filter(h -> h.isBossType(type)).findFirst();
    }

    /** @return {@code true} if any hook vetoes the spawn of the given boss entity. */
    public boolean anyVetoSpawn(Entity entity, BossTier tier) {
        for (BossHook hook : hooks) {
            if (hook.isBossType(entity.getType())) {
                try {
                    if (hook.onBossSpawn(entity, tier)) {
                        return true;
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Hook " + hook.id() + " threw on boss spawn: " + t.getMessage());
                }
            }
        }
        return false;
    }

    /** Notifies all hooks that a boss entity died. */
    public void onBossDeath(Entity entity, BossTier tier) {
        for (BossHook hook : hooks) {
            if (hook.isBossType(entity.getType())) {
                try {
                    hook.onBossDeath(entity, tier);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Hook " + hook.id() + " threw on boss death: " + t.getMessage());
                }
            }
        }
    }

    public boolean hasHooks() {
        return !hooks.isEmpty();
    }
}
