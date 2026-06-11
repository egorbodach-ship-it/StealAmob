package Polfg.Polfg.auction;

import Polfg.Polfg.AuctionBridge;
import Polfg.Polfg.AuctionMobInfo;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BrainrotAuction extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    private AuctionStorage storage;
    private AuctionManager mgr;

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("BrainrotBases") == null) {
            getLogger().severe("BrainrotBases \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d! \u041e\u0442\u043a\u043b\u044e\u0447\u0430\u044e\u0441\u044c.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        storage = new AuctionStorage(this);
        mgr = new AuctionManager(this, storage);
        AuctionBridge.setHook(mgr);

        if (getCommand("ah") != null) {
            getCommand("ah").setExecutor(this);
            getCommand("ah").setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);

        // Re-apply income pause + holograms once BrainrotBases has spawned base mobs.
        Bukkit.getScheduler().runTaskLater(this, () -> mgr.load(), 60L);
        getLogger().info("BrainrotAuction \u0432\u043a\u043b\u044e\u0447\u0451\u043d.");
    }

    @Override
    public void onDisable() {
        if (mgr != null) mgr.save();
    }

    // ===================== Commands =====================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u042d\u0442\u0430 \u043a\u043e\u043c\u0430\u043d\u0434\u0430 \u0442\u043e\u043b\u044c\u043a\u043e \u0434\u043b\u044f \u0438\u0433\u0440\u043e\u043a\u043e\u0432!");
            return true;
        }
        Player p = (Player) sender;

        if (args.length == 0) {
            AuctionMenu.open(p, mgr, AuctionHolder.Kind.BROWSE, 0, 0, null);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("sell")) {
            if (args.length < 2) {
                p.sendMessage("\u00a7c\u0418\u0441\u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u043d\u0438\u0435: \u00a7e/ah sell <\u0446\u0435\u043d\u0430> \u00a77(\u043d\u0430\u043f\u0440. 100k, 1.5m, 100b)");
                return true;
            }
            double price = MoneyParser.parse(args[1]);
            if (price < 0) {
                p.sendMessage("\u00a7c\u041d\u0435\u0432\u0435\u0440\u043d\u0430\u044f \u0446\u0435\u043d\u0430. \u041f\u0440\u0438\u043c\u0435\u0440\u044b: \u00a7f100000, 100k, 1.5m, 100b");
                return true;
            }
            // Robust mob detection: ray-trace first, then nearest owned base mob in look direction.
            AuctionMobInfo info = null;
            Entity looked = p.getTargetEntity(6);
            if (looked != null) info = AuctionBridge.getListableMobInfo(p, looked);
            if (info == null) {
                org.bukkit.util.Vector dir = p.getEyeLocation().getDirection();
                org.bukkit.util.Vector eye = p.getEyeLocation().toVector();
                double bestDot = 0.55;
                for (Entity e : p.getNearbyEntities(6, 6, 6)) {
                    if (!(e instanceof org.bukkit.entity.LivingEntity)) continue;
                    org.bukkit.util.Vector to = e.getLocation().add(0, e.getHeight() / 2.0, 0).toVector().subtract(eye);
                    double dist = to.length();
                    if (dist < 0.1 || dist > 6.5) continue;
                    double dot = to.clone().normalize().dot(dir);
                    if (dot <= bestDot) continue;
                    AuctionMobInfo cand = AuctionBridge.getListableMobInfo(p, e);
                    if (cand != null) { info = cand; bestDot = dot; }
                }
            }
            if (info == null) {
                p.sendMessage("§cПодойдите ближе и смотрите на своего моба на базе (не лаки-блок/гнилоход, не уже на аукционе).");
                return true;
            }

            AuctionListing l = new AuctionListing(UUID.randomUUID().toString());
            l.sellerUuid = p.getUniqueId().toString();
            l.sellerName = p.getName();
            l.sellerBase = info.base;
            l.mobPoint = info.mobPoint;
            l.mobType = info.mobType;
            l.mobDisplayName = info.displayName;
            l.mutationName = info.mutationName;
            l.snowy = info.snowy;
            l.price = price;
            l.baseIncomePerSec = info.baseIncomePerSec;
            l.iconMaterial = info.iconMaterial;
            l.rarityOrder = info.rarityOrder;
            l.listedAt = System.currentTimeMillis();

            AuctionBridge.list(info.base, info.mobPoint);
            mgr.add(l);
            p.sendMessage("\u00a7a\u2714 \u0412\u044b\u0441\u0442\u0430\u0432\u043b\u0435\u043d\u043e \u043d\u0430 \u0430\u0443\u043a\u0446\u0438\u043e\u043d: \u00a7f" + l.mobDisplayName + " \u00a7a\u0437\u0430 \u00a76" + MoneyParser.format(price) + "$");
            p.sendMessage("\u00a77\u041f\u043e\u043a\u0430 \u043c\u043e\u0431 \u043d\u0430 \u0430\u0443\u043a\u0446\u0438\u043e\u043d\u0435, \u043e\u043d \u043d\u0435 \u043f\u0440\u0438\u043d\u043e\u0441\u0438\u0442 \u0434\u043e\u0445\u043e\u0434.");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            return true;
        }

        if (sub.equals("search")) {
            if (args.length < 2) {
                p.sendMessage("\u00a7c\u0418\u0441\u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u043d\u0438\u0435: \u00a7e/ah search <\u043d\u0430\u0437\u0432\u0430\u043d\u0438\u0435 \u043c\u043e\u0431\u0430>");
                return true;
            }
            StringBuilder q = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) q.append(' ');
                q.append(args[i]);
            }
            AuctionMenu.open(p, mgr, AuctionHolder.Kind.SEARCH, 0, 0, q.toString());
            return true;
        }

        p.sendMessage("\u00a7e/ah \u00a77\u2014 \u043e\u0442\u043a\u0440\u044b\u0442\u044c \u0430\u0443\u043a\u0446\u0438\u043e\u043d");
        p.sendMessage("\u00a7e/ah sell <\u0446\u0435\u043d\u0430> \u00a77\u2014 \u043f\u0440\u043e\u0434\u0430\u0442\u044c \u043c\u043e\u0431\u0430 (\u0441\u043c\u043e\u0442\u0440\u0438\u0442\u0435 \u043d\u0430 \u043d\u0435\u0433\u043e)");
        p.sendMessage("\u00a7e/ah search <\u043c\u043e\u0431> \u00a77\u2014 \u043f\u043e\u0438\u0441\u043a");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : new String[]{"sell", "search"}) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            out.add("100k");
            out.add("1m");
            out.add("10m");
            out.add("1b");
        }
        return out;
    }

    // ===================== Menu clicks =====================

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof AuctionHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        AuctionHolder holder = (AuctionHolder) e.getInventory().getHolder();
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getInventory().getSize()) return; // clicked own inventory

        if (holder.kind == AuctionHolder.Kind.CONFIRM) {
            handleConfirm(p, holder, slot);
        } else {
            handleBrowse(p, holder, slot);
        }
    }

    private void handleBrowse(Player p, AuctionHolder holder, int slot) {
        switch (slot) {
            case AuctionMenu.SLOT_CLOSE:
                p.closeInventory();
                return;
            case AuctionMenu.SLOT_REFRESH:
                AuctionMenu.open(p, mgr, holder.kind, holder.page, holder.sort, holder.query);
                return;
            case AuctionMenu.SLOT_PREV:
                AuctionMenu.open(p, mgr, holder.kind, holder.page - 1, holder.sort, holder.query);
                return;
            case AuctionMenu.SLOT_NEXT:
                AuctionMenu.open(p, mgr, holder.kind, holder.page + 1, holder.sort, holder.query);
                return;
            case AuctionMenu.SLOT_SORT:
                if (holder.kind != AuctionHolder.Kind.MINE) {
                    AuctionMenu.open(p, mgr, holder.kind, 0, (holder.sort + 1) % 3, holder.query);
                }
                return;
            case AuctionMenu.SLOT_MINE:
                if (holder.kind == AuctionHolder.Kind.MINE) {
                    AuctionMenu.open(p, mgr, AuctionHolder.Kind.BROWSE, 0, holder.sort, null);
                } else {
                    AuctionMenu.open(p, mgr, AuctionHolder.Kind.MINE, 0, holder.sort, null);
                }
                return;
            default:
                break;
        }
        String id = holder.slotToListing.get(slot);
        if (id == null) return;
        AuctionListing l = mgr.get(id);
        if (l == null) {
            p.sendMessage("\u00a7c\u042d\u0442\u043e\u0442 \u043b\u043e\u0442 \u0443\u0436\u0435 \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u0435\u043d.");
            AuctionMenu.open(p, mgr, holder.kind, holder.page, holder.sort, holder.query);
            return;
        }
        boolean own = l.isOwnedBy(p.getUniqueId().toString());
        AuctionMenu.openConfirm(p, mgr, id, own, holder.sort, holder.page, holder.query, holder.kind);
    }

    private void handleConfirm(Player p, AuctionHolder holder, int slot) {
        if (slot == AuctionMenu.CONFIRM_OK_A || slot == AuctionMenu.CONFIRM_OK_B) {
            AuctionListing l = mgr.get(holder.confirmListingId);
            if (l == null) {
                p.sendMessage("\u00a7c\u041b\u043e\u0442 \u0443\u0436\u0435 \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u0435\u043d.");
                p.closeInventory();
                return;
            }
            if (holder.confirmUnlist) {
                doUnlist(p, l);
            } else {
                doBuy(p, l);
            }
            return;
        }
        if (slot == AuctionMenu.CONFIRM_NO_A || slot == AuctionMenu.CONFIRM_NO_B) {
            AuctionMenu.open(p, mgr, holder.backKind, holder.page, holder.sort, holder.query);
        }
    }

    private void doUnlist(Player p, AuctionListing l) {
        if (!l.isOwnedBy(p.getUniqueId().toString())) {
            p.sendMessage("\u00a7c\u042d\u0442\u043e \u043d\u0435 \u0432\u0430\u0448 \u043b\u043e\u0442.");
            p.closeInventory();
            return;
        }
        AuctionBridge.unlist(l.sellerBase, l.mobPoint);
        mgr.remove(l.id);
        p.sendMessage("\u00a7a\u2714 \u041b\u043e\u0442 \u0441\u043d\u044f\u0442. \u041c\u043e\u0431 \u0441\u043d\u043e\u0432\u0430 \u043f\u0440\u0438\u043d\u043e\u0441\u0438\u0442 \u0434\u043e\u0445\u043e\u0434.");
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
        p.closeInventory();
    }

    private void doBuy(Player p, AuctionListing l) {
        String buyerUuid = p.getUniqueId().toString();
        if (l.isOwnedBy(buyerUuid)) {
            p.sendMessage("\u00a7c\u041d\u0435\u043b\u044c\u0437\u044f \u043a\u0443\u043f\u0438\u0442\u044c \u0441\u0432\u043e\u0439 \u043b\u043e\u0442.");
            p.closeInventory();
            return;
        }
        if (!AuctionBridge.hasFreeSlot(p.getName())) {
            p.sendMessage("\u00a7c\u041d\u0430 \u0432\u0430\u0448\u0435\u0439 \u0431\u0430\u0437\u0435 \u043d\u0435\u0442 \u0441\u0432\u043e\u0431\u043e\u0434\u043d\u044b\u0445 \u043c\u0435\u0441\u0442!");
            p.closeInventory();
            return;
        }
        double bal = AuctionBridge.getBalance(p);
        if (bal < l.price) {
            p.sendMessage("\u00a7c\u041d\u0435\u0434\u043e\u0441\u0442\u0430\u0442\u043e\u0447\u043d\u043e \u0434\u0435\u043d\u0435\u0433! \u041d\u0443\u0436\u043d\u043e \u00a76" + MoneyParser.format(l.price) + "$");
            p.closeInventory();
            return;
        }
        if (mgr.get(l.id) == null) {
            p.sendMessage("\u00a7c\u041b\u043e\u0442 \u0443\u0436\u0435 \u043a\u0443\u043f\u043b\u0435\u043d.");
            p.closeInventory();
            return;
        }
        if (!AuctionBridge.withdraw(p, l.price)) {
            p.sendMessage("\u00a7c\u041e\u0448\u0438\u0431\u043a\u0430 \u0441\u043f\u0438\u0441\u0430\u043d\u0438\u044f \u0441\u0440\u0435\u0434\u0441\u0442\u0432.");
            p.closeInventory();
            return;
        }
        OfflinePlayer seller = Bukkit.getOfflinePlayer(UUID.fromString(l.sellerUuid));
        AuctionBridge.deposit(seller, l.price);

        AuctionBridge.removeListedMob(l.sellerBase, l.mobPoint);
        boolean given = AuctionBridge.giveMob(p.getName(), l.mobType, l.mutationName, l.snowy);
        mgr.remove(l.id);

        p.sendMessage("\u00a7a\u2714 \u041a\u0443\u043f\u043b\u0435\u043d\u043e: \u00a7f" + l.mobDisplayName + " \u00a7a\u0437\u0430 \u00a76" + MoneyParser.format(l.price) + "$");
        if (!given) {
            p.sendMessage("\u00a7e\u041c\u043e\u0431\u0430 \u043d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0440\u0430\u0437\u043c\u0435\u0441\u0442\u0438\u0442\u044c \u2014 \u043e\u0441\u0432\u043e\u0431\u043e\u0434\u0438\u0442\u0435 \u043c\u0435\u0441\u0442\u043e.");
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);

        Player sp = seller.getPlayer();
        if (sp != null) {
            sp.sendMessage("\u00a7a\ud83d\udcb0 \u0412\u0430\u0448 \u043b\u043e\u0442 \u00a7f" + l.mobDisplayName + " \u00a7a\u043a\u0443\u043f\u043b\u0435\u043d \u0437\u0430 \u00a76" + MoneyParser.format(l.price) + "$ \u00a77\u0438\u0433\u0440\u043e\u043a\u043e\u043c " + p.getName());
        }
        p.closeInventory();
    }
}
