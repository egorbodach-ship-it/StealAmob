package Polfg.Polfg;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Thin, fully public static facade over BrainrotBases for the separate
 * brainrot-auction plugin. All heavy lifting lives in BrainrotBases (which has
 * full access to its own private state); this class only delegates.
 *
 * The auction plugin never touches package-private MobType/Mutation/Rarity:
 * everything here is primitives + Bukkit types.
 */
public final class AuctionBridge {
    private AuctionBridge() {}

    private static BrainrotBases plugin() {
        return BrainrotBases.getInstance();
    }

    /** True once BrainrotBases has finished enabling. */
    public static boolean isReady() {
        return BrainrotBases.getInstance() != null;
    }

    /** Register the auction plugin's steal/removal callback. */
    public static void setHook(AuctionHook hook) {
        BrainrotBases p = plugin();
        if (p != null) p.setAuctionHook(hook);
    }

    /**
     * Returns sellable info for the mob the player is looking at, or null if it
     * cannot be listed (not the player's own base mob, a lucky-block/rotwalker,
     * or already listed).
     */
    public static AuctionMobInfo getListableMobInfo(Player player, Entity mob) {
        BrainrotBases p = plugin();
        return p == null ? null : p.getListableMobInfo(player, mob);
    }

    public static boolean isListed(String base, String mobPoint) {
        BrainrotBases p = plugin();
        return p != null && p.isAuctionListed(base, mobPoint);
    }

    /** Pause income + show the red "on auction" hologram for this mob. */
    public static boolean list(String base, String mobPoint) {
        BrainrotBases p = plugin();
        return p != null && p.listMobForAuction(base, mobPoint);
    }

    /** Resume income + remove the auction hologram (mob stays on the base). */
    public static void unlist(String base, String mobPoint) {
        BrainrotBases p = plugin();
        if (p != null) p.unlistMobFromAuction(base, mobPoint);
    }

    /** Remove the mob entity from the seller's base after a completed sale. */
    public static boolean removeListedMob(String base, String mobPoint) {
        BrainrotBases p = plugin();
        return p != null && p.removeListedMobForSale(base, mobPoint);
    }

    /** True if the named player's base has at least one free mob slot. */
    public static boolean hasFreeSlot(String ownerName) {
        BrainrotBases p = plugin();
        return p != null && p.hasFreeSlot(ownerName);
    }

    public static String getPlayerBase(String ownerName) {
        BrainrotBases p = plugin();
        return p == null ? null : p.getPlayerBase(ownerName);
    }

    /** Spawn a bought mob on the buyer's base (with the same mutation). */
    public static boolean giveMob(String ownerName, String mobTypeName, String mutationName, boolean snowy) {
        BrainrotBases p = plugin();
        return p != null && p.giveAuctionMob(ownerName, mobTypeName, mutationName, snowy);
    }

    public static double getBalance(OfflinePlayer player) {
        BrainrotBases p = plugin();
        return p == null ? 0.0 : p.auctionGetBalance(player);
    }

    public static boolean withdraw(OfflinePlayer player, double amount) {
        BrainrotBases p = plugin();
        return p != null && p.auctionWithdraw(player, amount);
    }

    public static boolean deposit(OfflinePlayer player, double amount) {
        BrainrotBases p = plugin();
        return p != null && p.auctionDeposit(player, amount);
    }

    /** Buyer's rebirth earn multiplier, for the "\u0443 \u0432\u0430\u0441" income preview. */
    public static double getEarnMultiplier(Player player) {
        BrainrotBases p = plugin();
        return p == null ? 1.0 : p.auctionGetEarnMultiplier(player);
    }
}
