package Polfg.Polfg.auction;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom InventoryHolder used to recognise auction menus inside InventoryClickEvent
 * and to carry navigation state (page / sort / query) without parsing titles.
 */
public class AuctionHolder implements InventoryHolder {

    public enum Kind { BROWSE, SEARCH, MINE, CONFIRM }

    public final Kind kind;
    public int page;
    public int sort;      // 0 = income/sec desc, 1 = rarity desc, 2 = price asc
    public String query;  // search query (SEARCH only)

    /** Maps a menu slot to the listing id rendered there. */
    public final Map<Integer, String> slotToListing = new HashMap<>();

    // CONFIRM context
    public String confirmListingId;
    public boolean confirmUnlist;
    public Kind backKind = Kind.BROWSE;

    private Inventory inventory;

    public AuctionHolder(Kind kind) {
        this.kind = kind;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
