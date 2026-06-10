package Polfg.Polfg;

import com.connorlinfoot.actionbarapi.ActionBarAPI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BrainrotShop extends JavaPlugin implements Listener, CommandExecutor {

    private static BrainrotShop instance;

    // ===== GUI holder =====
    private static final class ShopHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    // ===== Сообщения =====
    private static final String PREFIX =
            ChatColor.DARK_RED + "" + ChatColor.BOLD + "☠ " +
                    ChatColor.RED + "BRAINROT" +
                    ChatColor.DARK_RED + " ☠ " +
                    ChatColor.GRAY + "» " + ChatColor.RESET;

    private void msg(Player p, String text) {
        p.sendMessage(PREFIX + ChatColor.translateAlternateColorCodes('&', text));
    }

    private void ok(Player p, String text) {
        msg(p, "&a" + text);
    }

    private void warn(Player p, String text) {
        msg(p, "&e" + text);
    }

    private void err(Player p, String text) {
        msg(p, "&c" + text);
    }

    private void box(Player p, String title, String... lines) {
        p.sendMessage(ChatColor.DARK_GRAY + "╔════ " +
                ChatColor.translateAlternateColorCodes('&', title) +
                ChatColor.DARK_GRAY + " ════╗");
        for (String line : lines) {
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7" + line));
        }
        p.sendMessage(ChatColor.DARK_GRAY + "╚══════════════════════╝");
    }

    // ===== Данные капкана =====
    private static class TrapData {
        final UUID owner;
        final String uniqueId;
        final long placedAt;
        final Location location;

        TrapData(UUID owner, String uniqueId, Location location) {
            this.owner = owner;
            this.uniqueId = uniqueId;
            this.placedAt = System.currentTimeMillis();
            this.location = location.toBlockLocation();
        }
    }

    // ===== Данные (потокобезопасные) =====
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    // Капканы в мире: key(world_x_y_z) -> data
    private final Map<String, TrapData> placedTraps = new ConcurrentHashMap<>();

    // Двухфазная установка, чтобы не было бага "поставил под себя таймингом -> обычный аметист"
    // key(world_x_y_z) -> uuid кто ставил (ожидаем MONITOR)
    private final Map<String, UUID> pendingTrapPlaces = new ConcurrentHashMap<>();

    private final Map<UUID, Long> trappedUntil = new ConcurrentHashMap<>();
    private final Set<UUID> currentlyTrapped = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> playerTrapCount = new ConcurrentHashMap<>();
    private final Map<UUID, Long> trapImmunity = new ConcurrentHashMap<>();
    private final Set<UUID> purchaseLock = ConcurrentHashMap.newKeySet();

    private final Map<UUID, Long> slamCooldowns = new ConcurrentHashMap<>();

    // ===== Обход защиты регионов =====
    private final Set<UUID> bypassPlaceProtection = ConcurrentHashMap.newKeySet();
    private final Set<UUID> bypassBreakProtection = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> pendingTrapBreaks = new ConcurrentHashMap<>();

    // ===== Настройки =====
    private int COOLDOWN_SECONDS = 2;
    private int MAX_TRAPS_PER_PLAYER = 5;
    private int MAX_PLACED_TRAPS = 2;

    private int TRAP_DURATION = 5;
    private int BLINDNESS_LEVEL = 0;
    private int SLOWNESS_LEVEL = 6;
    private int IMMUNITY_SECONDS = 5;

    private static final int SLAM_PUNISH_SECONDS = 1;

    // ===== Ключи PDC (для красоты предмета; но установка НЕ зависит от PDC) =====
    private NamespacedKey trapKey;
    private NamespacedKey trapOwnerKey;
    private NamespacedKey trapUniqueIdKey;

    // ===== Экономика =====
    private Economy economy = null;
    private boolean vaultEnabled = false;

    // ===== Enable / Disable =====
    @Override
    public void onEnable() {
        instance = this;

        this.trapKey = new NamespacedKey(this, "brainrot_trap");
        this.trapOwnerKey = new NamespacedKey(this, "trap_owner");
        this.trapUniqueIdKey = new NamespacedKey(this, "trap_unique_id");

        if (actionBarAvailable()) {
            getLogger().info(ChatColor.GREEN + "ActionBarAPI подключён!");
        } else {
            getLogger().warning(ChatColor.YELLOW + "ActionBarAPI не найден.");
        }

        this.vaultEnabled = setupEconomy();
        if (this.vaultEnabled) {
            getLogger().info(ChatColor.GREEN + "Vault подключён!");
        } else {
            getLogger().warning(ChatColor.YELLOW + "Vault не найден! Экономика отключена.");
        }

        saveDefaultConfig();
        loadSettings();

        Bukkit.getPluginManager().registerEvents(this, this);

        if (getCommand("shop") != null) getCommand("shop").setExecutor(this);
        if (getCommand("brainrot") != null) getCommand("brainrot").setExecutor(this);

        startTrapEffectTask();
        startTrapCleanupTask();

        getLogger().info(ChatColor.GREEN + "BrainrotShop запущен!");
    }

    @Override
    public void onDisable() {
        // __leakfix__
        try { org.bukkit.Bukkit.getScheduler().cancelTasks(this); } catch (Throwable __t) {}
        try { org.bukkit.event.HandlerList.unregisterAll((org.bukkit.plugin.Plugin) this); } catch (Throwable __t) {}
        for (TrapData data : new ArrayList<>(this.placedTraps.values())) {
            Location loc = data.location;
            if (loc != null && loc.getWorld() != null) {
                loc.getBlock().setType(Material.AIR, false);
            }
        }
        this.placedTraps.clear();
        this.pendingTrapPlaces.clear();
        this.playerTrapCount.clear();

        for (UUID uuid : new HashSet<>(this.currentlyTrapped)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                releaseFromTrap(player);
            }
        }

        this.trapImmunity.clear();
        this.cooldowns.clear();
        this.trappedUntil.clear();
        this.currentlyTrapped.clear();
        this.purchaseLock.clear();
        this.slamCooldowns.clear();
        this.bypassPlaceProtection.clear();
        this.bypassBreakProtection.clear();
        this.pendingTrapBreaks.clear();
    }

    public static BrainrotShop getInstance() {
        return instance;
    }

    // ===== Утилиты =====
    private boolean actionBarAvailable() {
        return Bukkit.getPluginManager().getPlugin("ActionBarAPI") != null;
    }

    private void sendActionBarSafe(Player p, String text) {
        try {
            if (!actionBarAvailable()) return;
            ActionBarAPI.sendActionBar(p, text);
        } catch (Throwable ignored) {
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        this.economy = rsp.getProvider();
        return this.economy != null;
    }

    private void loadSettings() {
        FileConfiguration config = getConfig();
        this.TRAP_DURATION = clamp(config.getInt("trap.duration", 5), 1, 30);
        this.BLINDNESS_LEVEL = clamp(config.getInt("trap.blindness-level", 0), 0, 5);
        this.SLOWNESS_LEVEL = clamp(config.getInt("trap.slowness-level", 6), 0, 10);
        this.MAX_TRAPS_PER_PLAYER = clamp(config.getInt("trap.max-per-player", 5), 1, 64);
        this.MAX_PLACED_TRAPS = clamp(config.getInt("trap.max-placed", 2), 1, 20);
        this.IMMUNITY_SECONDS = clamp(config.getInt("trap.immunity-seconds", 5), 1, 60);
        this.COOLDOWN_SECONDS = clamp(config.getInt("shop.cooldown-seconds", 2), 0, 60);

        getLogger().info("Настройки загружены: duration=" + TRAP_DURATION
                + " maxPlaced=" + MAX_PLACED_TRAPS
                + " slowness=" + SLOWNESS_LEVEL
                + " immunity=" + IMMUNITY_SECONDS);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Надёжный строковый ключ для блока
     */
    private String getBlockKey(Location loc) {
        if (loc == null || loc.getWorld() == null) return "invalid";
        Location b = loc.toBlockLocation();
        return b.getWorld().getName() + "_" + b.getBlockX() + "_" + b.getBlockY() + "_" + b.getBlockZ();
    }

    private int getPendingCount(UUID owner) {
        int c = 0;
        for (UUID u : pendingTrapPlaces.values()) {
            if (owner.equals(u)) c++;
        }
        return c;
    }

    private List<String> buildTrapLore(UUID owner, String uniqueId) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Ставьте на землю!");
        lore.add(ChatColor.GRAY + "Оглушает на " + ChatColor.RED + this.TRAP_DURATION
                + ChatColor.GRAY + " сек.");
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "OWNER: " + owner.toString().substring(0, 8));
        lore.add(ChatColor.DARK_GRAY + "#" + uniqueId.substring(0, 8));
        return lore;
    }

    // ===== Анти-фантом =====
    private void removePhantomBlock(Player player, Location loc) {
        if (player == null || !player.isOnline() || loc == null || loc.getWorld() == null) return;

        Location b = loc.toBlockLocation();
        player.sendBlockChange(b, b.getBlock().getBlockData());

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;

            player.sendBlockChange(b, b.getBlock().getBlockData());

            Location[] neighbors = {
                    b.clone().add(0, 1, 0), b.clone().add(0, -1, 0),
                    b.clone().add(1, 0, 0), b.clone().add(-1, 0, 0),
                    b.clone().add(0, 0, 1), b.clone().add(0, 0, -1)
            };
            for (Location n : neighbors) {
                player.sendBlockChange(n, n.getBlock().getBlockData());
            }

            player.updateInventory();
        }, 2L);
    }

    private void removePhantomBlockForAll(Location loc, int radius) {
        if (loc == null || loc.getWorld() == null) return;
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= (long) radius * radius) {
                removePhantomBlock(p, loc);
            }
        }
    }

    // ===== Ground Slam =====
    private boolean slamPlayerToGround(Player player) {
        Location loc = player.getLocation().clone();

        for (int i = 0; i < 40; i++) {
            loc.subtract(0, 1, 0);

            if (loc.getBlock().getType().isSolid()) {
                Location groundLoc = loc.clone().add(0, 1, 0);
                groundLoc.setYaw(player.getLocation().getYaw());
                groundLoc.setPitch(player.getLocation().getPitch());

                player.teleport(groundLoc);

                player.playSound(groundLoc, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
                player.playSound(groundLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1f);

                if (groundLoc.getWorld() != null) {
                    groundLoc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                            groundLoc, 15, 0.5, 0.1, 0.5, 0.05);
                }
                return true;
            }
        }
        return false;
    }

    // ===== Двухфазная установка капкана (FIX тайминга "под себя") =====

    private PlaceResult canPlaceTrap(UUID owner, Location loc) {
        String key = getBlockKey(loc);

        if (placedTraps.containsKey(key)) return PlaceResult.ALREADY_EXISTS;

        Location below = loc.clone().subtract(0, 1, 0).toBlockLocation();
        if (placedTraps.containsKey(getBlockKey(below))) return PlaceResult.TRAP_ON_TRAP;

        int currentPlaced = playerTrapCount.getOrDefault(owner, 0);
        int currentPending = getPendingCount(owner);
        if ((currentPlaced + currentPending) >= MAX_PLACED_TRAPS) return PlaceResult.LIMIT_REACHED;

        if (!below.getBlock().getType().isSolid()) return PlaceResult.NO_GROUND;

        return PlaceResult.SUCCESS;
    }

    private void registerTrap(UUID owner, Location loc) {
        String key = getBlockKey(loc);
        if (placedTraps.containsKey(key)) return;

        String uniqueId = UUID.randomUUID().toString();
        placedTraps.put(key, new TrapData(owner, uniqueId, loc.toBlockLocation()));
        playerTrapCount.merge(owner, 1, Integer::sum);
    }

    private enum PlaceResult {
        SUCCESS,
        ALREADY_EXISTS,
        TRAP_ON_TRAP,
        LIMIT_REACHED,
        NO_GROUND
    }

    // ===== Команды =====
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("brainrot")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("brainrot.admin")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав!");
                    return true;
                }
                reloadConfig();
                loadSettings();
                sender.sendMessage(ChatColor.GREEN + "Конфиг перезагружен!");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Использование: /brainrot reload");
            }
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Только для игроков!");
            return true;
        }
        Player player = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("shop")) {
            if (!player.hasPermission("brainrot.shop")) {
                err(player, "У вас нет прав!");
                return true;
            }
            if (hasCooldown(player)) {
                err(player, "Подождите &e" + getCooldownRemaining(player) + " &cсек.");
                return true;
            }
            openShop(player);
            setCooldown(player);
            return true;
        }
        return false;
    }

    // ===== Shop GUI =====
    private void openShop(Player player) {
        FileConfiguration config = getConfig();
        String title = ChatColor.translateAlternateColorCodes('&',
                config.getString("shop.menu.title", "&c&lBRAINROT &f&lМагазин"));
        int size = config.getInt("shop.menu.size", 27);

        Inventory shop = Bukkit.createInventory(new ShopHolder(), size, title);

        if (config.getBoolean("shop.menu.border.enabled", true)) {
            fillBorders(shop, size);
        }
        addShopItems(shop, player);

        player.openInventory(shop);
    }

    private void fillBorders(Inventory inv, int size) {
        ItemStack redPane = createGlass(Material.RED_STAINED_GLASS_PANE);
        ItemStack whitePane = createGlass(Material.WHITE_STAINED_GLASS_PANE);

        int rows = size / 9;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = row * 9 + col;
                if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                    inv.setItem(slot, ((row + col) % 2 == 0) ? redPane : whitePane);
                }
            }
        }
    }

    private ItemStack createGlass(Material material) {
        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }
        return glass;
    }

    private void addShopItems(Inventory inv, Player player) {
        ConfigurationSection items = getConfig().getConfigurationSection("shop.items");
        if (items == null) {
            addDefaultShopItems(inv, player);
            return;
        }
        for (String key : items.getKeys(false)) {
            ConfigurationSection sec = items.getConfigurationSection(key);
            if (sec == null) continue;
            int slot = sec.getInt("slot", 13);
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, createShopDisplayItem(sec, player, key));
            }
        }
    }

    private void addDefaultShopItems(Inventory inv, Player player) {
        inv.setItem(11, createKnockbackSwordDisplay(player));
        inv.setItem(12, createTrapDisplay(player));
    }

    private ItemStack createTrapDisplay(Player player) {
        ItemStack item = new ItemStack(Material.SMALL_AMETHYST_BUD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "☠ " +
                    ChatColor.GOLD + ChatColor.BOLD + "Капкан " +
                    ChatColor.RED + ChatColor.BOLD + "☠");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Оглушает на " + ChatColor.RED + this.TRAP_DURATION
                    + ChatColor.GRAY + " сек!");
            lore.add(ChatColor.GRAY + "Только на землю.");
            lore.add(ChatColor.GRAY + "Макс. установлено: " + ChatColor.RED + this.MAX_PLACED_TRAPS);
            lore.add(ChatColor.YELLOW + "Удаляются при рестарте!");
            lore.add("");

            double price = getConfig().getDouble("shop.items.trap.price", 1000.0);
            lore.add(ChatColor.YELLOW + "Цена: " + ChatColor.GOLD + formatMoney(price));
            if (this.vaultEnabled) {
                lore.add(ChatColor.GRAY + "Баланс: " + ChatColor.GREEN + formatMoney(getBalance(player)));
            }

            int currentTraps = this.playerTrapCount.getOrDefault(player.getUniqueId(), 0);
            lore.add("");
            lore.add(ChatColor.GRAY + "Ваши капканы: " + ChatColor.YELLOW + currentTraps
                    + ChatColor.GRAY + "/" + ChatColor.RED + this.MAX_PLACED_TRAPS);
            lore.add("");
            lore.add(ChatColor.GREEN + "▶ ЛКМ - Купить");

            meta.setLore(lore);
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createKnockbackSwordDisplay(Player player) {
        ItemStack item = new ItemStack(Material.WOODEN_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "Деревянный меч");
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Отброс I");
            lore.add("");
            double price = getConfig().getDouble("shop.items.knockback_sword.price", 500.0);
            lore.add(ChatColor.YELLOW + "Цена: " + ChatColor.GOLD + formatMoney(price));
            if (this.vaultEnabled) {
                lore.add(ChatColor.GRAY + "Баланс: " + ChatColor.GREEN + formatMoney(getBalance(player)));
            }
            lore.add("");
            lore.add(ChatColor.GREEN + "▶ ЛКМ - Купить");

            meta.setLore(lore);
            meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createShopDisplayItem(ConfigurationSection section, Player player, String itemKey) {
        String materialName = section.getString("material", "DIAMOND");
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = Material.DIAMOND;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                    section.getString("name", "Предмет")));

            List<String> lore = new ArrayList<>();
            for (String line : section.getStringList("lore")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            double price = section.getDouble("price", 1000.0);
            lore.add("");
            lore.add(ChatColor.YELLOW + "Цена: " + ChatColor.GOLD + formatMoney(price));
            if (this.vaultEnabled) {
                lore.add(ChatColor.GRAY + "Баланс: " + ChatColor.GREEN + formatMoney(getBalance(player)));
            }

            if (itemKey.equalsIgnoreCase("trap")) {
                int currentTraps = this.playerTrapCount.getOrDefault(player.getUniqueId(), 0);
                lore.add("");
                lore.add(ChatColor.GRAY + "Ваши капканы: " + ChatColor.YELLOW + currentTraps
                        + ChatColor.GRAY + "/" + ChatColor.RED + this.MAX_PLACED_TRAPS);
            }

            lore.add("");
            lore.add(ChatColor.GREEN + "▶ ЛКМ - Купить");

            meta.setLore(lore);

            if (price >= 5000.0) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            if (itemKey.equalsIgnoreCase("knockback_sword")) {
                meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // ===== Обработка кликов в GUI =====
    private boolean isShopInventory(Inventory top) {
        return top != null && top.getHolder() instanceof ShopHolder;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!isShopInventory(event.getView().getTopInventory())) return;

        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);

        if (player.getItemOnCursor() != null && player.getItemOnCursor().getType() != Material.AIR) {
            ItemStack cursor = player.getItemOnCursor().clone();
            player.setItemOnCursor(new ItemStack(Material.AIR));
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(cursor);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            return;
        }

        if (event.getClickedInventory() == null) return;
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        if (clickedItem.getType().name().contains("GLASS_PANE")) return;
        if (!event.getClick().isLeftClick()) return;

        int slot = event.getSlot();
        ConfigurationSection items = getConfig().getConfigurationSection("shop.items");
        if (items == null) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(this, () -> handleDefaultPurchase(player, slot), 2L);
            return;
        }

        for (String key : items.getKeys(false)) {
            ConfigurationSection sec = items.getConfigurationSection(key);
            if (sec == null) continue;
            if (sec.getInt("slot") == slot) {
                final ConfigurationSection finalSec = sec;
                final String finalKey = key;
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(this, () -> handlePurchase(player, finalSec, finalKey), 2L);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isShopInventory(event.getView().getTopInventory())) return;
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCreativeInventory(InventoryCreativeEvent event) {
        if (isShopInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
        }
    }

    // ===== Покупки =====
    private void handleDefaultPurchase(Player player, int slot) {
        if (!purchaseLock.add(player.getUniqueId())) {
            err(player, "Подождите завершения предыдущей покупки!");
            return;
        }
        try {
            if (slot == 12) handleTrapPurchaseInternal(player);
            else if (slot == 11) handleSwordPurchaseInternal(player);
        } finally {
            purchaseLock.remove(player.getUniqueId());
        }
    }

    private void handlePurchase(Player player, ConfigurationSection sec, String itemKey) {
        if (!purchaseLock.add(player.getUniqueId())) {
            err(player, "Подождите завершения предыдущей покупки!");
            return;
        }

        try {
            if (itemKey.equalsIgnoreCase("trap")) {
                handleTrapPurchaseInternal(player);
                return;
            }
            if (itemKey.equalsIgnoreCase("knockback_sword")) {
                handleSwordPurchaseInternal(player);
                return;
            }

            String itemName = ChatColor.translateAlternateColorCodes('&',
                    sec.getString("name", "Предмет"));
            double price = sec.getDouble("price", 1000.0);

            if (!hasEnoughMoney(player, price)) {
                err(player, "Недостаточно денег!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }

            if (!takeMoney(player, price)) {
                err(player, "Ошибка транзакции.");
                return;
            }

            giveConfigItem(player, sec);
            ok(player, "Куплен &f" + itemName + " &aза &e" + formatMoney(price));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

        } finally {
            purchaseLock.remove(player.getUniqueId());
        }
    }

    private void handleTrapPurchaseInternal(Player player) {
        double price = getConfig().getDouble("shop.items.trap.price", 1000.0);

        // Раз ты хочешь "все бутоны = капканы", то в инвентаре считаем ВСЕ бутоны
        int inventoryTraps = countBudsInInventory(player);
        if (inventoryTraps >= this.MAX_TRAPS_PER_PLAYER) {
            err(player, "В инвентаре макс. кол-во капканов: &e" + this.MAX_TRAPS_PER_PLAYER);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (!hasEnoughMoney(player, price)) {
            err(player, "Недостаточно денег!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (!takeMoney(player, price)) {
            err(player, "Ошибка транзакции.");
            return;
        }

        giveTrapItem(player);
        ok(player, "Капкан куплен за &e" + formatMoney(price));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    private void handleSwordPurchaseInternal(Player player) {
        double price = getConfig().getDouble("shop.items.knockback_sword.price", 500.0);

        if (!hasEnoughMoney(player, price)) {
            err(player, "Недостаточно денег!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (!takeMoney(player, price)) {
            err(player, "Ошибка транзакции.");
            return;
        }

        giveKnockbackSword(player);
        ok(player, "Меч куплен за &e" + formatMoney(price));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    // ===== Выдача предметов =====
    private void giveTrapItem(Player player) {
        ItemStack trap = new ItemStack(Material.SMALL_AMETHYST_BUD, 1);
        ItemMeta meta = trap.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "☠ " +
                    ChatColor.GOLD + ChatColor.BOLD + "Капкан " +
                    ChatColor.RED + ChatColor.BOLD + "☠");

            String uniqueId = UUID.randomUUID().toString();

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(this.trapKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(this.trapOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            pdc.set(this.trapUniqueIdKey, PersistentDataType.STRING, uniqueId);

            meta.setLore(buildTrapLore(player.getUniqueId(), uniqueId));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            trap.setItemMeta(meta);
        }
        giveItemToPlayer(player, trap);
    }

    private void giveKnockbackSword(Player player) {
        ItemStack sword = new ItemStack(Material.WOODEN_SWORD, 1);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            FileConfiguration config = getConfig();
            String name = config.getString("shop.items.knockback_sword.name", "&e&lДеревянный меч");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

            List<String> lore = new ArrayList<>();
            if (config.contains("shop.items.knockback_sword.lore")) {
                for (String line : config.getStringList("shop.items.knockback_sword.lore")) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
            } else {
                lore.add("");
                lore.add(ChatColor.GRAY + "Отброс I");
                lore.add("");
            }

            meta.setLore(lore);
            meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            sword.setItemMeta(meta);
        }
        giveItemToPlayer(player, sword);
    }

    private void giveConfigItem(Player player, ConfigurationSection section) {
        ConfigurationSection realItem = section.getConfigurationSection("real-item");
        if (realItem == null) return;

        String materialName = realItem.getString("material", "DIAMOND");
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = Material.DIAMOND;

        int amount = realItem.getInt("amount", 1);
        ItemStack item = new ItemStack(material, amount);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                    realItem.getString("name", "Предмет")));
            List<String> lore = new ArrayList<>();
            for (String line : realItem.getStringList("lore")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        giveItemToPlayer(player, item);
    }

    private void giveItemToPlayer(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            warn(player, "Инвентарь полон! Предмет выпал на землю.");
        }
    }

    // ===== "Все бутоны = капканы": просто помечаем предмет на pickup (не обязательно, но красиво) =====
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBudPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        ItemStack stack = event.getItem().getItemStack();
        if (stack == null || stack.getType() != Material.SMALL_AMETHYST_BUD) return;
        if (!stack.hasItemMeta()) return;

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // если уже помечен — ок
        if (pdc.has(trapKey, PersistentDataType.BYTE)) return;

        // помечаем (один uniqueId на стак — нормально, т.к. установка НЕ зависит от него)
        String uniqueId = UUID.randomUUID().toString();
        pdc.set(this.trapKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(this.trapOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        pdc.set(this.trapUniqueIdKey, PersistentDataType.STRING, uniqueId);
        meta.setLore(buildTrapLore(player.getUniqueId(), uniqueId));
        stack.setItemMeta(meta);
    }

    // ===== Обход защиты регионов для УСТАНОВКИ капканов =====
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteractForTrapPlace(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.SMALL_AMETHYST_BUD) return;

        bypassPlaceProtection.add(player.getUniqueId());

        if (event.useItemInHand() == Event.Result.DENY) {
            event.setUseItemInHand(Event.Result.ALLOW);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onBudPlaceRegionBypass(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SMALL_AMETHYST_BUD) return;

        Player player = event.getPlayer();
        if (!bypassPlaceProtection.remove(player.getUniqueId())) return;

        if (event.isCancelled()) {
            event.setCancelled(false);
            event.setBuild(true);
        }
    }

    // ===== УСТАНОВКА БУТОНА: Фаза 1 (проверки) =====
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBudPlacePre(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SMALL_AMETHYST_BUD) return;

        Player player = event.getPlayer();
        UUID owner = player.getUniqueId();
        Location loc = event.getBlockPlaced().getLocation().toBlockLocation();
        String key = getBlockKey(loc);

        // Slam cooldown
        Long releaseTime = slamCooldowns.get(owner);
        if (releaseTime != null && System.currentTimeMillis() < releaseTime) {
            event.setCancelled(true);
            event.setBuild(false);
            bypassPlaceProtection.remove(owner);
            removePhantomBlock(player, loc);
            return;
        } else if (releaseTime != null) {
            slamCooldowns.remove(owner);
        }

        PlaceResult result = canPlaceTrap(owner, loc);

        if (result != PlaceResult.SUCCESS) {
            event.setCancelled(true);
            event.setBuild(false);
            bypassPlaceProtection.remove(owner);

            switch (result) {
                case ALREADY_EXISTS:
                    err(player, "Здесь уже стоит капкан!");
                    break;
                case TRAP_ON_TRAP:
                    err(player, "Нельзя ставить капкан на капкан!");
                    break;
                case LIMIT_REACHED:
                    int current = playerTrapCount.getOrDefault(owner, 0);
                    err(player, "Лимит капканов: &e" + current + "&7/&c" + this.MAX_PLACED_TRAPS);
                    break;
                case NO_GROUND:
                    err(player, "Только на твёрдую поверхность!");
                    boolean slammed = slamPlayerToGround(player);
                    if (slammed) warn(player, "&c⬇ А НУ СПУСТИСЬ НА ЗЕМЛЮ! ⬇");
                    slamCooldowns.put(owner, System.currentTimeMillis() + (SLAM_PUNISH_SECONDS * 1000L));
                    break;
            }

            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);

            // гарантированно убираем фантом
            Bukkit.getScheduler().runTask(this, () -> {
                if (loc.getBlock().getType() == Material.SMALL_AMETHYST_BUD && !placedTraps.containsKey(key)) {
                    loc.getBlock().setType(Material.AIR, false);
                }
                removePhantomBlock(player, loc);
            });

            return;
        }

        // УСПЕХ: НЕ отменяем. Bukkit реально ставит блок.
        pendingTrapPlaces.put(key, owner);
    }

    // ===== УСТАНОВКА БУТОНА: Фаза 2 (коммит, когда Bukkit уже поставил блок) =====
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBudPlacePost(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SMALL_AMETHYST_BUD) return;

        Location loc = event.getBlockPlaced().getLocation().toBlockLocation();
        String key = getBlockKey(loc);

        UUID owner = pendingTrapPlaces.remove(key);
        if (owner == null) return; // не наша установка (или уже обработано)

        // блок должен реально стоять
        if (loc.getBlock().getType() != Material.SMALL_AMETHYST_BUD) return;

        registerTrap(owner, loc);

        Player player = event.getPlayer();
        int newCount = playerTrapCount.getOrDefault(owner, 0);

        ok(player, "Капкан установлен! &7(&e" + newCount + "&7/&c" + MAX_PLACED_TRAPS + "&7)");
        player.playSound(loc, Sound.BLOCK_METAL_PLACE, 1f, 0.5f);

        if (loc.getWorld() != null) {
            loc.getWorld().spawnParticle(Particle.SMOKE,
                    loc.clone().add(0.5, 0.5, 0.5),
                    10, 0.3, 0.3, 0.3, 0.02);
        }
    }

    // ===== Обход защиты регионов для ЛОМКИ капканов =====
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onTrapBreakRegionBypassPre(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation().toBlockLocation();
        String key = getBlockKey(loc);

        TrapData data = this.placedTraps.get(key);
        if (data == null) return;

        Player player = event.getPlayer();
        bypassBreakProtection.add(player.getUniqueId());
        pendingTrapBreaks.put(player.getUniqueId(), key);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onTrapBreakRegionBypass(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!bypassBreakProtection.contains(uuid)) return;

        String expectedKey = pendingTrapBreaks.get(uuid);
        if (expectedKey == null) return;

        Location loc = event.getBlock().getLocation().toBlockLocation();
        String actualKey = getBlockKey(loc);

        if (!expectedKey.equals(actualKey)) return;

        if (!placedTraps.containsKey(actualKey)) {
            bypassBreakProtection.remove(uuid);
            pendingTrapBreaks.remove(uuid);
            return;
        }

        if (event.isCancelled()) {
            event.setCancelled(false);
        }
    }

    // ===== ЛОМКА КАПКАНА =====
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTrapBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation().toBlockLocation();
        String key = getBlockKey(loc);

        TrapData data = this.placedTraps.get(key);
        if (data == null) return;

        Player player = event.getPlayer();

        // Убираем флаги обхода
        bypassBreakProtection.remove(player.getUniqueId());
        pendingTrapBreaks.remove(player.getUniqueId());

        // Если событие всё ещё отменено — принудительно снимаем, т.к. это капкан
        if (event.isCancelled()) {
            event.setCancelled(false);
        }

        event.setCancelled(true);
        event.setDropItems(false);

        this.placedTraps.remove(key);
        int current = this.playerTrapCount.getOrDefault(data.owner, 1);
        this.playerTrapCount.put(data.owner, Math.max(0, current - 1));

        Bukkit.getScheduler().runTask(this, () -> {
            loc.getBlock().setType(Material.AIR, false);
            sendTrapBreakMessages(player, data.owner);

            player.playSound(loc, Sound.ENTITY_ITEM_BREAK, 1f, 0.5f);
            if (loc.getWorld() != null) {
                loc.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                        loc.clone().add(0.5, 0.5, 0.5),
                        15, 0.3, 0.3, 0.3, 0.05);
            }

            removePhantomBlockForAll(loc, 64);
        });
    }
    

    private void sendTrapBreakMessages(Player player, UUID owner) {
        int remaining = this.playerTrapCount.getOrDefault(owner, 0);

        if (player.getUniqueId().equals(owner)) {
            warn(player, "Вы сломали свой капкан. &7(&e" + remaining
                    + "&7/&c" + this.MAX_PLACED_TRAPS + "&7)");
        } else {
            warn(player, "Вы сломали чужой капкан.");
            Player ownerPlayer = Bukkit.getPlayer(owner);
            if (ownerPlayer != null && ownerPlayer.isOnline()) {
                err(ownerPlayer, "Ваш капкан сломан игроком &e" + player.getName());
                msg(ownerPlayer, "&7Осталось: &e" + remaining
                        + "&7/&c" + MAX_PLACED_TRAPS);
            }
        }
    }

    // ===== СРАБАТЫВАНИЕ КАПКАНА =====
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (this.currentlyTrapped.contains(playerUUID)) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null && (from.getX() != to.getX() || from.getZ() != to.getZ())) {
                event.setTo(new Location(from.getWorld(),
                        from.getX(), to.getY(), from.getZ(),
                        to.getYaw(), to.getPitch()));
            }
            return;
        }

        Long immuneUntil = this.trapImmunity.get(playerUUID);
        if (immuneUntil != null) {
            if (System.currentTimeMillis() < immuneUntil) return;
            this.trapImmunity.remove(playerUUID);
        }

        Location playerLoc = player.getLocation();
        Location blockBelow = playerLoc.clone().subtract(0.0, 0.1, 0.0);

        Set<String> checkedKeys = new HashSet<>();
        Location[] locations = {blockBelow, playerLoc};

        for (Location checkLoc : locations) {
            String key = getBlockKey(checkLoc);
            if (!checkedKeys.add(key)) continue;

            TrapData data = this.placedTraps.get(key);
            if (data == null) continue;

            // СВОЙ капкан никогда не триггерим
            if (data.owner.equals(playerUUID)) continue;

            Location trapLoc = data.location;

            // если кто-то убрал блок мимо нас — чистим запись (защита)
            if (trapLoc.getBlock().getType() == Material.AIR) {
                this.placedTraps.remove(key);
                int c = this.playerTrapCount.getOrDefault(data.owner, 1);
                this.playerTrapCount.put(data.owner, Math.max(0, c - 1));
                continue;
            }

            triggerTrap(player, trapLoc, data.owner, key);
            break;
        }
    }

    private void triggerTrap(Player victim, Location trapLoc, UUID ownerUUID, String trapKey) {
        UUID victimUUID = victim.getUniqueId();
        if (this.currentlyTrapped.contains(victimUUID)) return;

        this.currentlyTrapped.add(victimUUID);
        long releaseTime = System.currentTimeMillis() + this.TRAP_DURATION * 1000L;
        this.trappedUntil.put(victimUUID, releaseTime);

        trapLoc.getBlock().setType(Material.AIR, false);
        this.placedTraps.remove(trapKey);

        int currentTraps = this.playerTrapCount.getOrDefault(ownerUUID, 1);
        this.playerTrapCount.put(ownerUUID, Math.max(0, currentTraps - 1));

        applyTrapEffects(victim);

        box(victim, "&c&lКАПКАН",
                "&cВы попали в капкан!",
                "&7Оглушение: &c" + this.TRAP_DURATION + " &7сек.");

        Location effectLoc = victim.getLocation();
        victim.playSound(effectLoc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.5f);
        victim.playSound(effectLoc, Sound.BLOCK_CHAIN_BREAK, 1f, 0.5f);

        if (effectLoc.getWorld() != null) {
            effectLoc.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                    effectLoc.clone().add(0, 1, 0),
                    30, 0.5, 0.5, 0.5, 0.1);
            effectLoc.getWorld().spawnParticle(Particle.CRIT,
                    effectLoc.clone().add(0, 1, 0),
                    20, 0.3, 0.3, 0.3, 0.1);
        }

        removePhantomBlockForAll(trapLoc, 64);

        Player owner = Bukkit.getPlayer(ownerUUID);
        if (owner != null && owner.isOnline()) {
            ok(owner, "Капкан сработал на &e" + victim.getName());
            owner.playSound(owner.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }

        startReleaseCountdown(victim);
    }

    private void applyTrapEffects(Player player) {
        int durationTicks = this.TRAP_DURATION * 20 + 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, durationTicks, this.BLINDNESS_LEVEL));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, this.SLOWNESS_LEVEL));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, durationTicks, 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, durationTicks, 0));
    }

    private void startReleaseCountdown(Player victim) {
        final UUID victimUUID = victim.getUniqueId();

        new BukkitRunnable() {
            int secondsLeft = TRAP_DURATION;

            @Override
            public void run() {
                if (!currentlyTrapped.contains(victimUUID)) {
                    cancel();
                    return;
                }

                Player player = Bukkit.getPlayer(victimUUID);
                if (player == null || !player.isOnline()) {
                    currentlyTrapped.remove(victimUUID);
                    trappedUntil.remove(victimUUID);
                    cancel();
                    return;
                }

                if (this.secondsLeft <= 0) {
                    releaseFromTrap(player);
                    cancel();
                    return;
                }

                String bar = createProgressBar(this.secondsLeft, TRAP_DURATION);
                sendActionBarSafe(player, ChatColor.RED + "ОГЛУШЕНИЕ " + bar
                        + ChatColor.YELLOW + " " + this.secondsLeft + "с");

                if (this.secondsLeft <= 3) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1f);
                }

                this.secondsLeft--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private String createProgressBar(int current, int max) {
        int totalBars = 10;
        int filledBars = (int) ((float) current / (float) max * totalBars);
        int emptyBars = totalBars - filledBars;

        StringBuilder bar = new StringBuilder(ChatColor.GRAY + "[");
        for (int i = 0; i < filledBars; i++) bar.append(ChatColor.RED).append("|");
        for (int i = 0; i < emptyBars; i++) bar.append(ChatColor.DARK_GRAY).append("|");
        bar.append(ChatColor.GRAY).append("]");
        return bar.toString();
    }

    private void releaseFromTrap(Player player) {
        UUID uuid = player.getUniqueId();

        this.currentlyTrapped.remove(uuid);
        this.trappedUntil.remove(uuid);

        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.WEAKNESS);
        player.removePotionEffect(PotionEffectType.GLOWING);

        this.trapImmunity.put(uuid, System.currentTimeMillis() + this.IMMUNITY_SECONDS * 1000L);

        box(player, "&a&lСВОБОДА",
                "&aВы освободились!",
                "&7Иммунитет от капканов: &e" + this.IMMUNITY_SECONDS + " &7сек.");

        sendActionBarSafe(player, "");
        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1f, 1.5f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                player.getLocation().add(0, 1, 0),
                15, 0.5, 0.5, 0.5, 0.1);
    }

    // ===== Фоновые задачи =====
    private void startTrapEffectTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                Iterator<Map.Entry<UUID, Long>> it = trappedUntil.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, Long> entry = it.next();
                    if (now >= entry.getValue()) {
                        Player p = Bukkit.getPlayer(entry.getKey());
                        if (p != null && p.isOnline()) {
                            releaseFromTrap(p);
                        } else {
                            currentlyTrapped.remove(entry.getKey());
                        }
                        it.remove();
                    }
                }

                trapImmunity.entrySet().removeIf(e -> now >= e.getValue());
                slamCooldowns.entrySet().removeIf(e -> now >= e.getValue());
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void startTrapCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Iterator<Map.Entry<String, TrapData>> it = placedTraps.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, TrapData> entry = it.next();
                    TrapData data = entry.getValue();
                    Location loc = data.location;

                    if (loc == null || loc.getWorld() == null) {
                        it.remove();
                        int c = playerTrapCount.getOrDefault(data.owner, 1);
                        playerTrapCount.put(data.owner, Math.max(0, c - 1));
                        continue;
                    }

                    if (loc.getBlock().getType() != Material.SMALL_AMETHYST_BUD) {
                        it.remove();
                        int c = playerTrapCount.getOrDefault(data.owner, 1);
                        playerTrapCount.put(data.owner, Math.max(0, c - 1));
                    }
                }

                // чистим pending, если блок уже не бутон
                pendingTrapPlaces.entrySet().removeIf(e -> {
                    String k = e.getKey();
                    // парсить координаты не хочется; проще не чистить агрессивно
                    return false;
                });

                // чистим устаревшие bypass-флаги
                bypassPlaceProtection.clear();
                bypassBreakProtection.clear();
                pendingTrapBreaks.clear();
            }
        }.runTaskTimer(this, 100L, 100L);
    }

    // ===== Quit =====
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        // удаляем pending установки игрока
        pendingTrapPlaces.entrySet().removeIf(e -> uuid.equals(e.getValue()));

        // удаляем капканы игрока
        Iterator<Map.Entry<String, TrapData>> it = placedTraps.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TrapData> entry = it.next();
            if (entry.getValue().owner.equals(uuid)) {
                Location loc = entry.getValue().location;
                if (loc != null && loc.getWorld() != null) {
                    loc.getBlock().setType(Material.AIR, false);
                    removePhantomBlockForAll(loc, 64);
                }
                it.remove();
            }
        }

        if (currentlyTrapped.contains(uuid)) {
            releaseFromTrap(event.getPlayer());
        }

        this.playerTrapCount.remove(uuid);
        this.currentlyTrapped.remove(uuid);
        this.trappedUntil.remove(uuid);
        this.trapImmunity.remove(uuid);
        this.cooldowns.remove(uuid);
        this.purchaseLock.remove(uuid);
        this.slamCooldowns.remove(uuid);
        this.bypassPlaceProtection.remove(uuid);
        this.bypassBreakProtection.remove(uuid);
        this.pendingTrapBreaks.remove(uuid);
    }

    // ===== Вспомогательные =====
    private int countBudsInInventory(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (item.getType() == Material.SMALL_AMETHYST_BUD) count += item.getAmount();
        }
        return count;
    }

    private boolean hasCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        Long lastUsed = this.cooldowns.get(uuid);
        if (lastUsed == null) return false;
        long currentTime = System.currentTimeMillis() / 1000L;
        return (currentTime - lastUsed) < this.COOLDOWN_SECONDS;
    }

    private long getCooldownRemaining(Player player) {
        UUID uuid = player.getUniqueId();
        Long lastUsed = this.cooldowns.get(uuid);
        if (lastUsed == null) return 0L;
        long currentTime = System.currentTimeMillis() / 1000L;
        return Math.max(0L, this.COOLDOWN_SECONDS - (currentTime - lastUsed));
    }

    private void setCooldown(Player player) {
        this.cooldowns.put(player.getUniqueId(), System.currentTimeMillis() / 1000L);
    }

    private boolean hasEnoughMoney(Player p, double a) {
        return !vaultEnabled || economy == null || economy.has(p, a);
    }

    private boolean takeMoney(Player p, double a) {
        return !vaultEnabled || economy == null || economy.withdrawPlayer(p, a).transactionSuccess();
    }

    private double getBalance(Player p) {
        return (!vaultEnabled || economy == null) ? 0 : economy.getBalance(p);
    }

    private String formatMoney(double a) {
        return (!vaultEnabled || economy == null)
                ? String.format("%.0f$", a)
                : economy.format(a);
    }
}