package de.oliver.fancyholograms.api.data;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.joml.Vector3f;

import java.util.List;

/** Compile-time stub for FancyHolograms (provided scope, not bundled). */
public class TextHologramData extends HologramData {
    public TextHologramData(String name, Location location) {}
    public void setText(List<?> text) {}
    public List<String> getText() { return null; }
    public void setLocation(Location location) {}
    public void setBackground(Object background) {}
    public void setBillboard(Display.Billboard billboard) {}
    public void setScale(Vector3f scale) {}
    public void setSeeThrough(boolean seeThrough) {}
    public void setVisibilityDistance(int distance) {}
    public void setTextAlignment(TextDisplay.TextAlignment alignment) {}
}
