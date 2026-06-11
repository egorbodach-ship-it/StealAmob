package Polfg.Polfg;

import org.bukkit.entity.Entity;
import java.util.Set;

enum Mutation {
    NONE("", "", 1.0),
    GOLD("Золотой", "§6", 1.25),
    DIAMOND("Алмазный", "§b", 1.5),
    RAINBOW("Радужный", "§f", 10.0),
    SNOWY("Снежный", "§b", 5.0);
    final String displayName;
    final String format;
    double incomeMultiplier;
    Mutation(String displayName, String format, double incomeMultiplier) {
        this.displayName = displayName;
        this.format = format;
        this.incomeMultiplier = incomeMultiplier;
    }
    static Mutation fromEntity(Entity entity) {
        if (entity == null) return NONE;
        Set<String> tags = entity.getScoreboardTags();
        if (tags.contains("MUTATION_RAINBOW")) return RAINBOW;
        if (tags.contains("MUTATION_DIAMOND")) return DIAMOND;
        if (tags.contains("MUTATION_GOLD")) return GOLD;
        return NONE;
    }
    static Mutation fromName(String name) {
        if (name == null || name.isEmpty()) return NONE;
        try {
            return Mutation.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
    static boolean isSnowy(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains("MUTATION_SNOWY");
    }
}
