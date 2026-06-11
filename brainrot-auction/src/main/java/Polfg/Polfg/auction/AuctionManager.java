package Polfg.Polfg.auction;

import Polfg.Polfg.AuctionBridge;
import Polfg.Polfg.AuctionHook;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory store of auction listings + the steal/removal hook implementation. */
public class AuctionManager implements AuctionHook {

    private final JavaPlugin plugin;
    private final AuctionStorage storage;
    private final Map<String, AuctionListing> listings = new LinkedHashMap<>();

    public AuctionManager(JavaPlugin plugin, AuctionStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    /** Load from disk and re-apply income-pause + holograms for every listed mob. */
    public void load() {
        listings.clear();
        for (AuctionListing l : storage.loadAll()) {
            listings.put(l.id, l);
        }
        for (AuctionListing l : listings.values()) {
            if (l.sellerBase != null && l.mobPoint != null) {
                AuctionBridge.list(l.sellerBase, l.mobPoint);
            }
        }
        plugin.getLogger().info("\u0410\u0443\u043a\u0446\u0438\u043e\u043d: \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d\u043e \u043b\u043e\u0442\u043e\u0432: " + listings.size());
    }

    public void save() {
        storage.saveAll(listings.values());
    }

    public AuctionListing get(String id) {
        return listings.get(id);
    }

    public Collection<AuctionListing> all() {
        return listings.values();
    }

    public void add(AuctionListing l) {
        listings.put(l.id, l);
        save();
    }

    public AuctionListing remove(String id) {
        AuctionListing l = listings.remove(id);
        if (l != null) save();
        return l;
    }

    public AuctionListing findByBasePoint(String base, String mobPoint) {
        if (base == null || mobPoint == null) return null;
        for (AuctionListing l : listings.values()) {
            if (base.equals(l.sellerBase) && mobPoint.equals(l.mobPoint)) return l;
        }
        return null;
    }

    /** Listings filtered by query and sorted by the given mode. */
    public List<AuctionListing> sorted(int mode, String query) {
        List<AuctionListing> list = new ArrayList<>();
        String q = query == null ? null : query.toLowerCase();
        for (AuctionListing l : listings.values()) {
            if (q != null && !l.matches(q)) continue;
            list.add(l);
        }
        Comparator<AuctionListing> cmp;
        switch (mode) {
            case 1:
                cmp = Comparator.comparingInt((AuctionListing l) -> l.rarityOrder).reversed();
                break;
            case 2:
                cmp = Comparator.comparingDouble((AuctionListing l) -> l.price);
                break;
            default:
                cmp = Comparator.comparingDouble((AuctionListing l) -> l.baseIncomePerSec).reversed();
                break;
        }
        list.sort(cmp.thenComparingLong(l -> l.listedAt));
        return list;
    }

    public List<AuctionListing> bySeller(String uuid) {
        List<AuctionListing> list = new ArrayList<>();
        for (AuctionListing l : listings.values()) {
            if (l.isOwnedBy(uuid)) list.add(l);
        }
        list.sort(Comparator.comparingLong(l -> l.listedAt));
        return list;
    }

    // === AuctionHook ===
    @Override
    public void onListedMobRemoved(String base, String mobPoint) {
        AuctionListing l = findByBasePoint(base, mobPoint);
        if (l != null) {
            listings.remove(l.id);
            save();
            plugin.getLogger().info("\u0410\u0443\u043a\u0446\u0438\u043e\u043d: \u043b\u043e\u0442 \u0441\u043d\u044f\u0442 (\u043c\u043e\u0431 \u0443\u043a\u0440\u0430\u0434\u0435\u043d/\u0443\u0434\u0430\u043b\u0451\u043d): " + l.mobDisplayName);
        }
    }
}
