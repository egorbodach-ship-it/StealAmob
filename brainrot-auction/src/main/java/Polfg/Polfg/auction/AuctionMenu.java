package Polfg.Polfg.auction;

import Polfg.Polfg.AuctionBridge;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Builds all auction GUI screens (browse / search / my-lots / confirm). */
public final class AuctionMenu {

    private AuctionMenu() {}

    /** 28 content slots inside a 54-slot chest, framed by glass. */
    public static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public static final int SLOT_MINE = 45;
    public static final int SLOT_CLOSE = 46;
    public static final int SLOT_PREV = 48;
    public static final int SLOT_SORT = 49;
    public static final int SLOT_NEXT = 50;
    public static final int SLOT_REFRESH = 53;

    public static final int CONFIRM_OK_A = 10;
    public static final int CONFIRM_OK_B = 11;
    public static final int CONFIRM_NO_A = 15;
    public static final int CONFIRM_NO_B = 16;
    public static final int CONFIRM_ITEM = 13;

    // ===================== Browse / Search / Mine =====================

    public static void open(Player viewer, AuctionManager mgr, AuctionHolder.Kind kind, int page, int sort, String query) {
        List<AuctionListing> list;
        if (kind == AuctionHolder.Kind.MINE) {
            list = mgr.bySeller(viewer.getUniqueId().toString());
        } else {
            list = mgr.sorted(sort, query);
        }

        int perPage = ITEM_SLOTS.length;
        int maxPage = list.isEmpty() ? 0 : (list.size() - 1) / perPage;
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;

        AuctionHolder holder = new AuctionHolder(kind);
        holder.page = page;
        holder.sort = sort;
        holder.query = query;

        String title;
        if (kind == AuctionHolder.Kind.SEARCH) {
            title = "\u00a78\u041f\u043e\u0438\u0441\u043a: \u00a7f" + (query == null ? "" : query);
        } else if (kind == AuctionHolder.Kind.MINE) {
            title = "\u00a78\u041c\u043e\u0438 \u043b\u043e\u0442\u044b";
        } else {
            title = "\u00a78\u0410\u0443\u043a\u0446\u0438\u043e\u043d \u043c\u043e\u0431\u043e\u0432";
        }

        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        ItemStack frame = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, frame);
        for (int slot : ITEM_SLOTS) inv.setItem(slot, null);

        int start = page * perPage;
        for (int i = 0; i < perPage; i++) {
            int idx = start + i;
            if (idx >= list.size()) break;
            AuctionListing l = list.get(idx);
            inv.setItem(ITEM_SLOTS[i], listingIcon(l, viewer));
            holder.slotToListing.put(ITEM_SLOTS[i], l.id);
        }

        if (list.isEmpty()) {
            inv.setItem(22, named(Material.COBWEB,
                    kind == AuctionHolder.Kind.MINE ? "\u00a7c\u0423 \u0432\u0430\u0441 \u043d\u0435\u0442 \u043b\u043e\u0442\u043e\u0432" : "\u00a7c\u041d\u0435\u0442 \u043b\u043e\u0442\u043e\u0432",
                    "\u00a77\u0412\u044b\u0441\u0442\u0430\u0432\u044c\u0442\u0435 \u043c\u043e\u0431\u0430: \u00a7e/ah sell <\u0446\u0435\u043d\u0430>"));
        }

        // Nav row
        if (kind == AuctionHolder.Kind.MINE) {
            inv.setItem(SLOT_MINE, named(Material.CHEST, "\u00a7e\u25c0 \u041a \u0430\u0443\u043a\u0446\u0438\u043e\u043d\u0443"));
        } else {
            inv.setItem(SLOT_MINE, named(Material.ENDER_CHEST, "\u00a7e\u041c\u043e\u0438 \u043b\u043e\u0442\u044b"));
        }
        inv.setItem(SLOT_CLOSE, named(Material.BARRIER, "\u00a7c\u0417\u0430\u043a\u0440\u044b\u0442\u044c"));
        if (page > 0) inv.setItem(SLOT_PREV, named(Material.ARROW, "\u00a7a\u25c0 \u041d\u0430\u0437\u0430\u0434"));
        if (kind != AuctionHolder.Kind.MINE) {
            inv.setItem(SLOT_SORT, sortItem(sort, list.size(), page, maxPage));
        } else {
            inv.setItem(SLOT_SORT, named(Material.PAPER, "\u00a7f\u041b\u043e\u0442\u043e\u0432: \u00a7e" + list.size()));
        }
        if (page < maxPage) inv.setItem(SLOT_NEXT, named(Material.ARROW, "\u00a7a\u0412\u043f\u0435\u0440\u0451\u0434 \u25b6"));
        inv.setItem(SLOT_REFRESH, named(Material.SUNFLOWER, "\u00a7e\u041e\u0431\u043d\u043e\u0432\u0438\u0442\u044c"));

        viewer.openInventory(inv);
    }

    // ===================== Confirm =====================

    public static void openConfirm(Player viewer, AuctionManager mgr, String listingId, boolean unlist,
                                   int backSort, int backPage, String backQuery, AuctionHolder.Kind backKind) {
        AuctionListing l = mgr.get(listingId);
        if (l == null) {
            viewer.closeInventory();
            return;
        }
        AuctionHolder holder = new AuctionHolder(AuctionHolder.Kind.CONFIRM);
        holder.confirmListingId = listingId;
        holder.confirmUnlist = unlist;
        holder.sort = backSort;
        holder.page = backPage;
        holder.query = backQuery;
        holder.backKind = backKind;

        String title = unlist ? "\u00a78\u0421\u043d\u044f\u0442\u044c \u0441 \u043f\u0440\u043e\u0434\u0430\u0436\u0438?" : "\u00a78\u041f\u043e\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u0442\u0435 \u043f\u043e\u043a\u0443\u043f\u043a\u0443";
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        holder.setInventory(inv);

        ItemStack frame = pane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, frame);

        String okLabel = unlist
                ? "\u00a7a\u2714 \u0421\u043d\u044f\u0442\u044c \u0441 \u043f\u0440\u043e\u0434\u0430\u0436\u0438"
                : "\u00a7a\u2714 \u041a\u0443\u043f\u0438\u0442\u044c \u0437\u0430 \u00a76" + MoneyParser.format(l.price) + "$";
        ItemStack ok = pane(Material.LIME_STAINED_GLASS_PANE, okLabel);
        ItemStack no = pane(Material.RED_STAINED_GLASS_PANE, "\u00a7c\u2716 \u041e\u0442\u043c\u0435\u043d\u0430");
        inv.setItem(CONFIRM_OK_A, ok);
        inv.setItem(CONFIRM_OK_B, ok);
        inv.setItem(CONFIRM_NO_A, no);
        inv.setItem(CONFIRM_NO_B, no);
        inv.setItem(CONFIRM_ITEM, listingIcon(l, viewer));

        viewer.openInventory(inv);
    }

    // ===================== Item builders =====================

    public static ItemStack listingIcon(AuctionListing l, Player viewer) {
        Material mat = Material.matchMaterial(l.iconMaterial == null ? "" : l.iconMaterial);
        if (mat == null) mat = Material.SPAWNER;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(mutPrefix(l.mutationName, l.snowy) + "\u00a7f" + l.mobDisplayName);
            List<String> lore = new ArrayList<>();
            lore.add("\u00a77\u041f\u0440\u043e\u0434\u0430\u0432\u0435\u0446: \u00a7f" + l.sellerName);
            lore.add("\u00a77\u0420\u0435\u0434\u043a\u043e\u0441\u0442\u044c: " + rarityLabel(l.rarityOrder));
            lore.add("\u00a77\u0426\u0435\u043d\u0430: \u00a76" + MoneyParser.format(l.price) + "$");
            lore.add("\u00a77\u0414\u043e\u0445\u043e\u0434: \u00a7a+" + MoneyParser.format(l.baseIncomePerSec) + "$/\u0441\u0435\u043a");
            double mult = AuctionBridge.getEarnMultiplier(viewer);
            if (mult > 1.0) {
                lore.add("\u00a77\u0423 \u0432\u0430\u0441: \u00a7a+" + MoneyParser.format(l.baseIncomePerSec * mult)
                        + "$/\u0441\u0435\u043a \u00a78(x" + trimMult(mult) + ")");
            }
            lore.add("");
            if (l.isOwnedBy(viewer.getUniqueId().toString())) {
                lore.add("\u00a7c\u25ba \u041d\u0430\u0436\u043c\u0438\u0442\u0435, \u0447\u0442\u043e\u0431\u044b \u0441\u043d\u044f\u0442\u044c");
            } else {
                lore.add("\u00a7a\u25ba \u041d\u0430\u0436\u043c\u0438\u0442\u0435, \u0447\u0442\u043e\u0431\u044b \u043a\u0443\u043f\u0438\u0442\u044c");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack sortItem(int sort, int total, int page, int maxPage) {
        String mode;
        switch (sort) {
            case 1: mode = "\u0420\u0435\u0434\u043a\u043e\u0441\u0442\u044c \u2193"; break;
            case 2: mode = "\u0426\u0435\u043d\u0430 \u2191"; break;
            default: mode = "\u0414\u043e\u0445\u043e\u0434/\u0441\u0435\u043a \u2193"; break;
        }
        return named(Material.COMPASS, "\u00a7e\u0421\u043e\u0440\u0442\u0438\u0440\u043e\u0432\u043a\u0430: \u00a7f" + mode,
                "\u00a77\u041b\u043e\u0442\u043e\u0432: \u00a7f" + total,
                "\u00a77\u0421\u0442\u0440\u0430\u043d\u0438\u0446\u0430: \u00a7f" + (page + 1) + "/" + (maxPage + 1),
                "",
                "\u00a7e\u041d\u0430\u0436\u043c\u0438\u0442\u0435, \u0447\u0442\u043e\u0431\u044b \u0441\u043c\u0435\u043d\u0438\u0442\u044c");
    }

    private static String mutPrefix(String mut, boolean snowy) {
        StringBuilder sb = new StringBuilder();
        if (snowy) sb.append("\u00a7b\u0421\u043d\u0435\u0436\u043d\u044b\u0439 ");
        if (mut != null) {
            switch (mut.toUpperCase()) {
                case "GOLD": sb.append("\u00a76\u0417\u043e\u043b\u043e\u0442\u043e\u0439 "); break;
                case "DIAMOND": sb.append("\u00a7b\u0410\u043b\u043c\u0430\u0437\u043d\u044b\u0439 "); break;
                case "RAINBOW": sb.append("\u00a7d\u0420\u0430\u0434\u0443\u0436\u043d\u044b\u0439 "); break;
                default: break;
            }
        }
        return sb.toString();
    }

    private static String rarityLabel(int o) {
        switch (o) {
            case 1: return "\u00a79\u0420\u0435\u0434\u043a\u0438\u0439";
            case 2: return "\u00a75\u042d\u043f\u0438\u0447\u0435\u0441\u043a\u0438\u0439";
            case 3: return "\u00a76\u041b\u0435\u0433\u0435\u043d\u0434\u0430\u0440\u043d\u044b\u0439";
            case 4: return "\u00a7d\u041c\u0438\u0444\u0438\u0447\u0435\u0441\u043a\u0438\u0439";
            default: return "\u00a7a\u041e\u0431\u044b\u0447\u043d\u044b\u0439";
        }
    }

    private static String trimMult(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.valueOf(Math.round(v * 100.0) / 100.0);
    }

    private static ItemStack pane(Material mat, String name) {
        return named(mat, name);
    }

    private static ItemStack named(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                List<String> l = new ArrayList<>();
                for (String s : lore) l.add(s);
                meta.setLore(l);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
