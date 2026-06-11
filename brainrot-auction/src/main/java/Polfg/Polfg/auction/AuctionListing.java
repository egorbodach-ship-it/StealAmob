package Polfg.Polfg.auction;

import org.bukkit.configuration.ConfigurationSection;

/**
 * One mob listed on the auction house.
 *
 * Stores only PRIMITIVE data (no brainrot-bases internal classes) so the auction
 * plugin stays fully decoupled. All mob facts are supplied by AuctionBridge.
 */
public class AuctionListing {

    /** Unique listing id (UUID string). */
    public final String id;

    /** Seller identity. */
    public String sellerUuid;
    public String sellerName;

    /** Stable location of the listed mob (used to pause income / remove on sale). */
    public String sellerBase;
    public String mobPoint;

    /** Mob identity (MobType enum name + a human-friendly display name). */
    public String mobType;
    public String mobDisplayName;

    /** Mutation enum name (NONE/GOLD/DIAMOND/RAINBOW) and snowy flag. */
    public String mutationName;
    public boolean snowy;

    /** Price set by the seller (whole number). */
    public double price;

    /** Intrinsic income/sec = baseIncome x mutation multiplier (no buyer rebirth bonus). */
    public double baseIncomePerSec;

    /** Bukkit Material name used to render the menu icon. */
    public String iconMaterial;

    /** Rarity ordinal used for sorting (higher = rarer). */
    public int rarityOrder;

    /** Epoch millis when the mob was listed. */
    public long listedAt;

    public AuctionListing(String id) {
        this.id = id;
    }

    /** Write this listing into a YAML section. */
    public void save(ConfigurationSection s) {
        s.set("seller-uuid", sellerUuid);
        s.set("seller-name", sellerName);
        s.set("seller-base", sellerBase);
        s.set("mob-point", mobPoint);
        s.set("mob-type", mobType);
        s.set("mob-display-name", mobDisplayName);
        s.set("mutation", mutationName);
        s.set("snowy", snowy);
        s.set("price", price);
        s.set("base-income-per-sec", baseIncomePerSec);
        s.set("icon-material", iconMaterial);
        s.set("rarity-order", rarityOrder);
        s.set("listed-at", listedAt);
    }

    /** Read a listing from a YAML section. Returns null if essential data is missing. */
    public static AuctionListing load(String id, ConfigurationSection s) {
        if (s == null) return null;
        AuctionListing l = new AuctionListing(id);
        l.sellerUuid = s.getString("seller-uuid");
        l.sellerName = s.getString("seller-name", "???");
        l.sellerBase = s.getString("seller-base");
        l.mobPoint = s.getString("mob-point");
        l.mobType = s.getString("mob-type");
        l.mobDisplayName = s.getString("mob-display-name", l.mobType);
        l.mutationName = s.getString("mutation", "NONE");
        l.snowy = s.getBoolean("snowy", false);
        l.price = s.getDouble("price", 0);
        l.baseIncomePerSec = s.getDouble("base-income-per-sec", 0);
        l.iconMaterial = s.getString("icon-material", "SPAWNER");
        l.rarityOrder = s.getInt("rarity-order", 0);
        l.listedAt = s.getLong("listed-at", 0L);
        if (l.sellerUuid == null || l.mobType == null) return null;
        return l;
    }

    public boolean isOwnedBy(String uuid) {
        return sellerUuid != null && sellerUuid.equals(uuid);
    }

    /** True if mobDisplayName contains the given (lowercased) query. */
    public boolean matches(String loweredQuery) {
        if (loweredQuery == null || loweredQuery.isEmpty()) return true;
        String name = mobDisplayName == null ? "" : mobDisplayName.toLowerCase();
        return name.contains(loweredQuery);
    }
}
