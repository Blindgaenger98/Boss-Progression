package com.example.bossprogression.hook;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.config.BossTier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

/**
 * Service Provider Interface for third-party boss integrations (e.g. MythicMobs).
 *
 * <p>Implementations are registered with the {@link BossHookManager} at plugin enable.
 * Hooks may extend which entities count as bosses and react to spawn/death of boss
 * entities. The core plugin never calls hook code directly except through the
 * manager's iteration helpers, so a missing or broken hook can not crash the core.</p>
 */
public interface BossHook {

    /** Stable unique identifier of this hook, e.g. {@code "mythicmobs"}. */
    String id();

    /** Called when the plugin enables and the hook should start listening. */
    void onEnable(BossProgressionPlugin plugin);

    /** Called when the plugin disables; release resources and listeners. */
    default void onDisable() {
    }

    /**
     * @return {@code true} if the given entity type is a boss managed by this hook,
     *         overriding the vanilla tier list for spawn-guard and death detection.
     */
    default boolean isBossType(EntityType type) {
        return false;
    }

    /**
     * Called when a boss entity managed by this hook spawns.
     *
     * @return {@code true} to veto the spawn (core will cancel the event).
     */
    default boolean onBossSpawn(Entity entity, BossTier tier) {
        return false;
    }

    /**
     * Called when a boss entity managed by this hook dies, right before the core
     * advances the progression tier.
     */
    default void onBossDeath(Entity entity, BossTier tier) {
    }
}
