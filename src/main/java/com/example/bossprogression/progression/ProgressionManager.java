package com.example.bossprogression.progression;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.config.BossTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;

/**
 * Owns the server-wide boss progression state and all transitions.
 * The authoritative value lives in {@link com.example.bossprogression.data.DataStore};
 * this manager is the only component allowed to mutate it.
 */
public final class ProgressionManager {

    private final BossProgressionPlugin plugin;

    public ProgressionManager(BossProgressionPlugin plugin) {
        this.plugin = plugin;
    }

    /** Current number of bosses defeated (0 = preparation phase). */
    public int getCurrentTier() {
        return plugin.dataStore().getCurrentBossTier();
    }

    /**
     * The next boss that is allowed to be fought, or null if the progression is complete.
     */
    public BossTier getNextAllowedBoss() {
        int next = getCurrentTier() + 1;
        return plugin.configManager().getTier(next);
    }

    /** Whether the given tier is the next allowed one. */
    public boolean isAllowed(int tierNumber) {
        return tierNumber == getCurrentTier() + 1;
    }

    /** True when all configured bosses have been defeated. */
    public boolean isProgressionComplete() {
        return getNextAllowedBoss() == null;
    }

    /**
     * Advances the progression by one tier and announces it globally (chat + actionbar).
     * Only called when a valid boss kill is detected.
     *
     * @return the defeated tier, or null if the progression was already complete
     */
    public BossTier advance() {
        int next = getCurrentTier() + 1;
        BossTier defeated = plugin.configManager().getTier(next);
        if (defeated == null) {
            plugin.getLogger().warning("Attempted to advance beyond the last configured tier.");
            return null;
        }
        plugin.dataStore().setCurrentBossTier(next);

        String bossName = defeated.displayName().replace('&', '\u00a7');
        String message = plugin.messages().raw(globalMessageKey(defeated),
                Map.of("boss", bossName, "tier", String.valueOf(next)));

        for (var player : plugin.getServer().getOnlinePlayers()) {
            player.sendMessage(message);
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(message));
        }

        plugin.getLogger().info("Progression advanced to tier " + next + " (" + describe(defeated) + ")");
        return defeated;
    }

    /** Announce key per tier type; entity tiers keep the Phase 1 key. */
    private static String globalMessageKey(BossTier tier) {
        return switch (tier.type()) {
            case RAID -> "raid-cleared.global";
            case STRUCTURE_CLEAR -> "structure-cleared.global";
            default -> "boss-killed.global";
        };
    }

    /** Null-safe description for logging (non-entity tiers have no entity type). */
    private static String describe(BossTier tier) {
        if (tier.isEntityTypeTier()) {
            return String.valueOf(tier.entityType());
        }
        return tier.type() + (tier.structureKind() != null ? "/" + tier.structureKind() : "");
    }

    /**
     * Admin override: set the tier manually. Returns false when the tier is out of range.
     * <p>
     * Every tier change invalidates the hardmode base-value cache
     * (see {@link com.example.bossprogression.hardmode.HardmodeManager#reset()});
     * a reset to tier 0 additionally clears all dead players, which would
     * otherwise be stuck without a revive item.
     */
    public boolean setTier(int tier) {
        int max = plugin.configManager().getBossTiers().size();
        if (tier < 0 || tier > max) {
            return false;
        }
        plugin.dataStore().setCurrentBossTier(tier);
        if (tier == 0) {
            plugin.dataStore().clearDeadPlayers();
        }
        plugin.hardmodeManager().reset();
        plugin.getLogger().info("Tier manually set to " + tier);
        return true;
    }
}
