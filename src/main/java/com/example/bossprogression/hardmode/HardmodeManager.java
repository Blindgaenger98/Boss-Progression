package com.example.bossprogression.hardmode;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.config.BossTier;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;

import java.util.EnumMap;
import java.util.Map;

/**
 * Hardmode+ scaling: monster attributes (max health, attack damage, movement
 * speed) are scaled by the current boss tier.
 * Boss entities themselves are excluded (they have their own progression role).
 */
public final class HardmodeManager {

    private final BossProgressionPlugin plugin;
    private final Map<EntityType, Double> baseValues = new EnumMap<>(EntityType.class);

    public HardmodeManager(BossProgressionPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Applies hardmode scaling to the given monster.
     *
     * @return true when scaling was applied
     */
    public boolean applyTo(LivingEntity entity) {
        if (!plugin.configManager().getHardmodeConfig().isEnabled()) {
            return false;
        }
        if (!(entity instanceof Monster)) {
            return false;
        }
        if (isBossTier(entity.getType())) {
            return false;
        }

        double multiplier = plugin.configManager().getHardmodeConfig()
                .multiplierForTier(plugin.progressionManager().getCurrentTier());
        if (multiplier <= 1.0) {
            return false;
        }

        scale(entity, Attribute.MAX_HEALTH, multiplier);
        scale(entity, Attribute.ATTACK_DAMAGE, multiplier);
        scale(entity, Attribute.MOVEMENT_SPEED, multiplier);
        return true;
    }

    /**
     * Scales one attribute, remembering the base value so repeated calls
     * (e.g. after a config reload) do not compound.
     */
    private void scale(LivingEntity entity, Attribute attribute, double multiplier) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        double current = instance.getValue();
        Double cached = baseValues.get(entity.getType());
        double base;
        if (cached == null || current <= cached) {
            base = current; // first time, or value was reduced by something else
            baseValues.put(entity.getType(), base);
        } else {
            base = cached;
        }
        instance.setBaseValue(base * multiplier);
    }

    /** Resets cached base values, e.g. after /bossadmin settier or a reload. */
    public void reset() {
        baseValues.clear();
    }

    private boolean isBossTier(EntityType type) {
        for (BossTier tier : plugin.configManager().getBossTiers()) {
            if (tier.entityType() == type) {
                return true;
            }
        }
        return false;
    }
}
