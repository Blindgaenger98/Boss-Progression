package com.example.bossprogression.config;

import com.example.bossprogression.BossProgressionPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Loads and validates {@code config.yml}. All configuration objects are immutable.
 *
 * <p>Reloads are atomic: every part of the configuration is parsed and validated
 * into local variables first; only when ALL parts loaded successfully are the
 * fields published (volatile) in a single step. On any failure the previously
 * loaded configuration is retained unchanged and {@link #load()} returns
 * {@code false}.
 */
public final class ConfigManager {

    private final BossProgressionPlugin plugin;

    private volatile List<BossTier> bossTiers = Collections.emptyList();
    private volatile AltarConfig altarConfig;
    private volatile PenaltyConfig penaltyConfig;
    private volatile HardmodeConfig hardmodeConfig;
    private volatile boolean clearInventoryOnDeath = false;
    private volatile int autoSaveMinutes = 5;

    public ConfigManager(BossProgressionPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads the configuration from disk. The reload is atomic: all parts are
     * parsed and validated into local variables first and only published as a
     * whole when every part loaded successfully. If any part fails, the
     * previously loaded configuration is retained unchanged (no hybrid state)
     * and the error is logged with context.
     *
     * @return true if the configuration was loaded successfully, false if
     *         validation failed (previous values are retained)
     */
    public boolean load() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        List<BossTier> newBossTiers;
        AltarConfig newAltarConfig;
        PenaltyConfig newPenaltyConfig;
        HardmodeConfig newHardmodeConfig;
        boolean newClearInventoryOnDeath;
        int newAutoSaveMinutes;
        try {
            newBossTiers = loadBossTiers(cfg);
            newAltarConfig = loadAltar(cfg);
            newPenaltyConfig = loadPenalty(cfg);
            newHardmodeConfig = loadHardmode(cfg);
            newClearInventoryOnDeath = cfg.getBoolean("permadeath.clear-inventory-on-death", false);
            newAutoSaveMinutes = Math.max(1, cfg.getInt("data.auto-save-minutes", 5));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().severe("Configuration reload failed (" + e.getMessage()
                    + ") - previously loaded configuration retained.");
            return false;
        }

        // Publish atomically: every value above is fully validated, so the
        // fields can be replaced in one step without any hybrid state.
        this.bossTiers = newBossTiers;
        this.altarConfig = newAltarConfig;
        this.penaltyConfig = newPenaltyConfig;
        this.hardmodeConfig = newHardmodeConfig;
        this.clearInventoryOnDeath = newClearInventoryOnDeath;
        this.autoSaveMinutes = newAutoSaveMinutes;

        plugin.getLogger().info(String.format("Loaded %d boss tier(s), altar=%s, penalty=%s, hardmode=%s",
                newBossTiers.size(), newAltarConfig.isEnabled(),
                newPenaltyConfig.isEnabled(), newHardmodeConfig.isEnabled()));
        return true;
    }

    private List<BossTier> loadBossTiers(FileConfiguration cfg) {
        ConfigurationSection section = cfg.getConfigurationSection("boss-tiers");
        if (section == null || section.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("config.yml: 'boss-tiers' section is missing or empty");
        }

        List<BossTier> tiers = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection t = section.getConfigurationSection(key);
            if (t == null) {
                continue;
            }

            int tierNumber = t.getInt("tier", -1);
            if (tierNumber <= 0) {
                throw new IllegalArgumentException("boss-tiers." + key + ": 'tier' must be a positive integer");
            }

            // Phase 2: 'type' is optional; missing = VANILLA (backward compatible).
            TierType type;
            if (t.contains("type")) {
                try {
                    type = TierType.valueOf(t.getString("type", "").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "boss-tiers." + key + ": unknown type '" + t.getString("type") + "'");
                }
            } else {
                type = TierType.VANILLA;
            }

            EntityType entityType = null;
            if (type == TierType.VANILLA || type == TierType.MYTHIC_MOB) {
                try {
                    entityType = EntityType.valueOf(t.getString("entity-type", "").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "boss-tiers." + key + ": unknown entity-type '" + t.getString("entity-type") + "'");
                }
            } else if (t.contains("entity-type")) {
                throw new IllegalArgumentException(
                        "boss-tiers." + key + ": 'entity-type' is not allowed for type " + type);
            }

            int requiredRaidWaves = 0;
            if (type == TierType.RAID) {
                requiredRaidWaves = Math.max(0, t.getInt("required-raid-waves", 0));
            }

            StructureKind structureKind = null;
            int requiredSpawnerClears = 0;
            if (type == TierType.STRUCTURE_CLEAR) {
                requiredSpawnerClears = t.getInt("required-spawner-clears", -1);
                if (requiredSpawnerClears < 1) {
                    throw new IllegalArgumentException(
                            "boss-tiers." + key + ": 'required-spawner-clears' must be at least 1 for STRUCTURE_CLEAR tiers");
                }
                try {
                    structureKind = StructureKind.valueOf(
                            t.getString("structure-kind", "TRIAL_CHAMBER").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "boss-tiers." + key + ": unknown structure-kind '" + t.getString("structure-kind") + "'");
                }
            }

            String id = t.getString("id", key.toLowerCase(Locale.ROOT));
            String displayName = t.getString("display-name", "&cBoss");

            ConfigurationSection itemSec = t.getConfigurationSection("revive-item");
            if (itemSec == null) {
                throw new IllegalArgumentException("boss-tiers." + key + ": 'revive-item' section is missing");
            }
            Material material;
            try {
                material = Material.valueOf(itemSec.getString("material", "").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "boss-tiers." + key + ": unknown revive-item material '" + itemSec.getString("material") + "'");
            }
            int cmd = itemSec.getInt("custom-model-data", 0);
            String itemName = itemSec.getString("display-name", "&aRevive Item");
            String pdcKey = itemSec.getString("pdc-key", "");
            String pdcValue = itemSec.getString("pdc-value", "tier" + tierNumber);

            tiers.add(new BossTier(tierNumber, type, entityType, id, displayName,
                    requiredRaidWaves, structureKind, requiredSpawnerClears,
                    new ReviveItemConfig(material, cmd, itemName, pdcKey, pdcValue)));
        }

        tiers.sort((a, b) -> Integer.compare(a.tierNumber(), b.tierNumber()));
        if (tiers.size() != tiers.stream().map(BossTier::tierNumber).distinct().count()) {
            throw new IllegalArgumentException("boss-tiers: duplicate tier numbers detected");
        }
        return Collections.unmodifiableList(tiers);
    }

    private AltarConfig loadAltar(FileConfiguration cfg) {
        ConfigurationSection s = cfg.getConfigurationSection("altar");
        if (s == null) {
            s = cfg.getConfigurationSection("altar-system");
        }
        if (s == null) {
            s = new org.bukkit.configuration.MemoryConfiguration();
        }
        boolean enabled = s.getBoolean("enabled", true);
        Material block;
        try {
            block = Material.valueOf(s.getString("block", "CRYING_OBSIDIAN").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("altar: unknown block '" + s.getString("block") + "'");
        }
        String particle = s.getString("particle", "SOUL");
        String sound = s.getString("sound", "BLOCK_BEACON_ACTIVATE");
        return new AltarConfig(enabled, block, particle, sound);
    }

    private PenaltyConfig loadPenalty(FileConfiguration cfg) {
        ConfigurationSection s = cfg.getConfigurationSection("penalty");
        if (s == null) {
            s = new org.bukkit.configuration.MemoryConfiguration();
        }
        return new PenaltyConfig(
                s.getBoolean("enabled", true),
                s.getDouble("radius", 16.0),
                s.getInt("wither-amplifier", 1),
                s.getInt("slowness-amplifier", 3),
                s.getInt("effect-duration-ticks", 100),
                s.getDouble("lightning-damage-percent", 25.0),
                Math.max(0, s.getInt("cooldown-seconds", 5)));
    }

    private HardmodeConfig loadHardmode(FileConfiguration cfg) {
        ConfigurationSection s = cfg.getConfigurationSection("hardmode");
        if (s == null) {
            s = new org.bukkit.configuration.MemoryConfiguration();
        }
        return new HardmodeConfig(
                s.getBoolean("enabled", true),
                s.getDouble("base-multiplier", 1.0),
                s.getDouble("per-tier-multiplier", 0.5),
                s.getBoolean("nerf-health-regeneration", true),
                s.getBoolean("nerf-golden-apple", false));
    }

    // ---- Accessors ----

    public List<BossTier> getBossTiers() {
        return bossTiers;
    }

    public BossTier getTier(int tierNumber) {
        return bossTiers.stream()
                .filter(t -> t.tierNumber() == tierNumber)
                .findFirst()
                .orElse(null);
    }

    public AltarConfig getAltarConfig() {
        return altarConfig;
    }

    public PenaltyConfig getPenaltyConfig() {
        return penaltyConfig;
    }

    public HardmodeConfig getHardmodeConfig() {
        return hardmodeConfig;
    }

    public boolean isClearInventoryOnDeath() {
        return clearInventoryOnDeath;
    }

    public int getAutoSaveMinutes() {
        return autoSaveMinutes;
    }
}
