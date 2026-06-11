package Polfg.Polfg.auction;

/**
 * Parses and formats money amounts for the auction house.
 * Supports suffixes: k (thousand), m (million), b (billion), t (trillion).
 * Examples: "100k" -> 100000, "1.5m" -> 1500000, "100000" -> 100000.
 */
public final class MoneyParser {

    private MoneyParser() {}

    /**
     * Parse a user-typed amount into a positive whole-number value.
     * @return the parsed amount (floored to a whole number), or -1 if the input is invalid / not positive.
     */
    public static double parse(String input) {
        if (input == null) return -1;
        String s = input.trim().toLowerCase().replace(",", ".").replace(" ", "").replace("_", "");
        if (s.isEmpty()) return -1;

        double mult = 1.0;
        char last = s.charAt(s.length() - 1);
        switch (last) {
            case 'k': mult = 1_000.0; break;
            case 'm': mult = 1_000_000.0; break;
            case 'b': mult = 1_000_000_000.0; break;
            case 't': mult = 1_000_000_000_000.0; break;
            default: break;
        }
        if (mult != 1.0) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty()) return -1;

        double val;
        try {
            val = Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return -1;
        }
        if (Double.isNaN(val) || Double.isInfinite(val) || val <= 0) return -1;

        double result = Math.floor(val * mult);
        if (result <= 0) return -1;
        return result;
    }

    /**
     * Format a number for display: 1500 -> "1.5k", 2500000 -> "2.5M".
     */
    public static String format(double amount) {
        double abs = Math.abs(amount);
        if (abs < 1_000.0) return trim(amount);
        if (abs < 1_000_000.0) return trim(amount / 1_000.0) + "k";
        if (abs < 1_000_000_000.0) return trim(amount / 1_000_000.0) + "M";
        if (abs < 1_000_000_000_000.0) return trim(amount / 1_000_000_000.0) + "B";
        return trim(amount / 1_000_000_000_000.0) + "T";
    }

    private static String trim(double v) {
        double rounded = Math.round(v * 100.0) / 100.0;
        if (rounded == Math.floor(rounded)) {
            return String.valueOf((long) rounded);
        }
        return String.valueOf(rounded);
    }
}
