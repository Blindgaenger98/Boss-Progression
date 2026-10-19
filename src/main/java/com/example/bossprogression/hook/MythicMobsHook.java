package com.example.bossprogression.hook;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.config.BossTier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

/**
 * Phase 2 placeholder hook for MythicMobs.
 *
 * <p>Phase 1 (vanilla only) ships this skeleton without any MythicMobs API calls:
 * it registers itself, logs the integration status and delegates everything to the
 * core vanilla tier list. When the MythicMobs dependency is added in Phase 2,
 * implement the following here:</p>
 *
 * <ul>
 *   <li>Parse boss mobs from MythicMobs config (mobs marked as bosses via the
 *       {@code BossProgression} tag) and register them as additional {@code BossTier}s.</li>
 *   <li>Override {@link #isBossType(EntityType)} for MM-spawned entity types.</li>
 *   <li>Listen to MythicMobs' {@code MythicMobSpawnEvent}/{@code MythicMobDeathEvent}
 *       (via reflection or the API) and forward to {@link #onBossSpawn}/{@link #onBossDeath}.</li>
 *   <li>Optionally veto out-of-sequence MM boss spawns (return {@code true} in
 *       {@link #onBossSpawn(Entity, BossTier)}).</li>
 * </ul>
 */
public final class MythicMobsHook implements BossHook {

    public static final String ID = "mythicmobs";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void onEnable(BossProgressionPlugin plugin) {
        plugin.getLogger().info("MythicMobsHook registered (Phase 2 placeholder - vanilla tier list is authoritative).");
        // Phase 2: register MythicMobSpawnEvent/MythicMobDeathEvent listeners here.
    }

    @Override
    public boolean isBossType(EntityType type) {
        // Phase 2: return true for MythicMobs boss entity types once they are parsed.
        return false;
    }

    @Override
    public boolean onBossSpawn(Entity entity, BossTier tier) {
        // Phase 2: veto out-of-sequence MythicMobs boss spawns here.
        return false;
    }

    @Override
    public void onBossDeath(Entity entity, BossTier tier) {
        // Phase 2: handle custom MythicMobs boss death logic here.
    }
}
