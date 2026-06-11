package Polfg.Polfg;

/**
 * Primitive snapshot of a base mob, handed to the brainrot-auction plugin.
 *
 * Contains ONLY primitives / Strings (no package-private MobType/Mutation/Rarity),
 * so the separate auction plugin (different jar/classloader) can read it safely.
 */
public class AuctionMobInfo {
    /** Base id the mob currently lives on. */
    public String base;
    /** Stable mob-point key on that base (survives restarts). */
    public String mobPoint;
    /** MobType enum name (e.g. "COW"). */
    public String mobType;
    /** Human friendly display name (e.g. "\u041a\u043e\u0440\u043e\u0432\u0430"). */
    public String displayName;
    /** Mutation enum name (NONE/GOLD/DIAMOND/RAINBOW/SNOWY). */
    public String mutationName;
    /** Snowy flag (separate stackable mutation). */
    public boolean snowy;
    /** Intrinsic income/sec = baseIncome x mutation multiplier (no buyer rebirth bonus). */
    public double baseIncomePerSec;
    /** Bukkit Material name used for the menu icon. */
    public String iconMaterial;
    /** Rarity ordinal (higher = rarer) for sorting. */
    public int rarityOrder;
    /** Current owner / seller name. */
    public String sellerName;
}
