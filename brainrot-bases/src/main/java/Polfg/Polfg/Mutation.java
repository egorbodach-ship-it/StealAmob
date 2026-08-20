package Polfg.Polfg;

import org.bukkit.entity.Entity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

enum Mutation {
    NONE("", "", 1.0),
    GOLD("Золотой", "§6", 1.25),
    DIAMOND("Алмазный", "§b", 1.5),
    RAINBOW("Радужный", "§f", 10.0),
    SNOWY("Снежный", "§b", 5.0),
    ELECTRIC("Электрический", "§e", 3.0),
    METEOR("Метеоритный", "§c", 4.0),
    EXPLOSIVE("Взрывной", "§a", 3.5);
    final String displayName;
    final String format;
    double incomeMultiplier;
    Mutation(String displayName, String format, double incomeMultiplier) {
        this.displayName = displayName;
        this.format = format;
        this.incomeMultiplier = incomeMultiplier;
    }

    /** Мутации, которые стакаются поверх базовой (SNOWY хранится отдельным флагом, см. isSnowy). */
    static final List<Mutation> STACKABLE = Arrays.asList(ELECTRIC, METEOR, EXPLOSIVE);

    static Mutation fromEntity(Entity entity) {
        if (entity == null) return NONE;
        Set<String> tags = entity.getScoreboardTags();
        if (tags.contains("MUTATION_RAINBOW")) return RAINBOW;
        if (tags.contains("MUTATION_DIAMOND")) return DIAMOND;
        if (tags.contains("MUTATION_GOLD")) return GOLD;
        return NONE;
    }

    /**
     * Базовая мутация из строки. Поддерживает стакнутую запись вида "GOLD+ELECTRIC+METEOR" —
     * берётся первый токен, который не является стакающейся мутацией.
     */
    static Mutation fromName(String name) {
        if (name == null || name.isEmpty()) return NONE;
        String first = name.split("\\+")[0].trim();
        if (first.isEmpty()) return NONE;
        try {
            return Mutation.valueOf(first.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }

    /** Стакающиеся мутации, закодированные в строке "GOLD+ELECTRIC+METEOR". */
    static List<Mutation> extrasFromName(String name) {
        List<Mutation> out = new ArrayList<>();
        if (name == null || name.isEmpty()) return out;
        for (String part : name.split("\\+")) {
            String token = part.trim().toUpperCase();
            if (token.isEmpty()) continue;
            for (Mutation m : STACKABLE) {
                if (m.name().equals(token) && !out.contains(m)) out.add(m);
            }
        }
        return out;
    }

    /** Стакающиеся мутации, навешенные тегами на самого моба. */
    static List<Mutation> extrasFromEntity(Entity entity) {
        List<Mutation> out = new ArrayList<>();
        if (entity == null) return out;
        Set<String> tags = entity.getScoreboardTags();
        for (Mutation m : STACKABLE) {
            if (tags.contains("MUTATION_" + m.name())) out.add(m);
        }
        return out;
    }

    /** Обратная сборка строки для сохранения: "GOLD+ELECTRIC". */
    static String serialize(Mutation base, List<Mutation> extras) {
        StringBuilder sb = new StringBuilder(base == null ? NONE.name() : base.name());
        if (extras != null) {
            for (Mutation m : extras) {
                if (m != null && STACKABLE.contains(m)) sb.append('+').append(m.name());
            }
        }
        return sb.toString();
    }

    static boolean isSnowy(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains("MUTATION_SNOWY");
    }
}
