package com.example.bossprogression.config;

import org.bukkit.Material;

/**
 * Settings for the physical revive altar.
 */
public final class AltarConfig {

    private final boolean enabled;
    private final Material altarBlock;
    private final String particle;
    private final String sound;

    public AltarConfig(boolean enabled, Material altarBlock, String particle, String sound) {
        this.enabled = enabled;
        this.altarBlock = altarBlock;
        this.particle = particle;
        this.sound = sound;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Material getAltarBlock() {
        return altarBlock;
    }

    public String getParticle() {
        return particle;
    }

    public String getSound() {
        return sound;
    }
}
