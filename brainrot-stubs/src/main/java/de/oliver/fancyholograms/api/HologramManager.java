package de.oliver.fancyholograms.api;

import de.oliver.fancyholograms.api.data.HologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;

import java.util.Collection;
import java.util.Optional;

/** Compile-time stub for FancyHolograms (provided scope, not bundled). */
public interface HologramManager {
    Hologram create(HologramData data);
    void addHologram(Hologram hologram);
    void removeHologram(Hologram hologram);
    Optional<Hologram> getHologram(String name);
    Collection<Hologram> getHolograms();
}
