package Polfg.Polfg;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BrainrotExtras extends JavaPlugin implements Listener, CommandExecutor {
    private final Object ioLock = new Object();
    public void saveConfigAsync(final org.bukkit.configuration.file.FileConfiguration cfg, final java.io.File file) throws java.io.IOException {
        if (cfg == null || file == null) return;
        final String data = cfg.saveToString();
        final byte[] bytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (!isEnabled()) {
            synchronized (ioLock) {
                try { java.nio.file.Files.write(file.toPath(), bytes); }
                catch (java.io.IOException e) { getLogger().warning("Ошибка сохранения " + file.getName() + ": " + e.getMessage()); }
            }
            return;
        }
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            synchronized (ioLock) {
                try { java.nio.file.Files.write(file.toPath(), bytes); }
                catch (java.io.IOException e) { getLogger().warning("Ошибка сохранения " + file.getName() + ": " + e.getMessage()); }
            }
        });
    }


    // --- ДОБАВЛЕНО: Singleton Instance ---
    private static BrainrotExtras instance;

    public static BrainrotExtras get() {
        return instance;
    }
    // -------------------------------------

    private Economy economy;
    private File dataFile;
    private FileConfiguration dataConfig;
    private final Random random = new Random();

    // Паттерн для парсинга сумм: 10k, 1.5m, 2b, 1t, 1000k и т.д.
    private static final Pattern MONEY_PATTERN = Pattern.compile(
            "^([0-9]*\\.?[0-9]+)\\s*([kmbtKMBT])?$"
    );

    private static final Set<String> MENU_TITLES = new HashSet<>(Arrays.asList(
            "§5§lКазино Brainrot",
            "§6§lCoinFlip",
            "§6§lCoinFlip: Розыгрыш",
            "§6§lВыбор стороны",
            "§a§lJackpot",
            "§a§lJackpot: Закрыт",
            "§b§lRPS: КНБ",
            "§bВыбор: К/Н/Б",
            "§bОжидание...",
            "§d§lСлоты",
            "§e§lЕжедневные Награды"
    ));

    // === COINFLIP ===
    private final Map<UUID, CoinFlipBet> cfBets = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> cfCreatingSide = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> cfEntering = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> cfSortDesc = new ConcurrentHashMap<>();

    // === JACKPOT ===
    private final List<JackpotEntry> jackpotEntries = Collections.synchronizedList(new ArrayList<>());
    private double jackpotPool = 0;
    private BukkitRunnable jackpotTimer = null;
    private int jackpotSeconds = 60;
    private boolean jackpotRunning = false;
    private boolean jackpotOpen = false;
    private final Map<UUID, Boolean> jpEntering = new ConcurrentHashMap<>();

    // === RPS ===
    private final Map<UUID, Double> rpsBets = new ConcurrentHashMap<>();
    private final Map<UUID, RPSGame> activeRPSGames = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> rpsEntering = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> rpsSortDesc = new ConcurrentHashMap<>();

    // Блокировки
    private final Set<UUID> animationLock = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> rpsChoiceLock = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> rpsWaitLock = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> switchingMenu = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // === SLOTS ===
    private final Map<UUID, Inventory> slotsInventory = new ConcurrentHashMap<>();

    private enum DailyTier {
        COMMON("§aОбычная", 5000, 50.0),
        UNCOMMON("§9Необычная", 15000, 30.0),
        RARE("§5Редкая", 50000, 15.0),
        EPIC("§6Эпическая", 250000, 4.0),
        LEGENDARY("§d§lЛЕГЕНДАРНАЯ", 1000000, 1.0);

        final String name;
        final int amount;
        final double chance;

        DailyTier(String n, int a, double c) { name = n; amount = a; chance = c; }
    }

    private static class CoinFlipBet {
        final UUID owner;
        final double amount;
        final boolean isHeads;

        CoinFlipBet(UUID o, double a, boolean h) { owner = o; amount = a; isHeads = h; }
    }

    private static class JackpotEntry {
        final UUID player;
        final double amount;

        JackpotEntry(UUID p, double a) { player = p; amount = a; }
    }

    private static class RPSGame {
        final Player p1, p2;
        final double bet;
        volatile int p1Choice = 0, p2Choice = 0;

        RPSGame(Player a, Player b, double m) { p1 = a; p2 = b; bet = m; }
    }

    // --- ДОБАВЛЕНО: Метод отмены ставок для перерождения ---
    public void cancelPlayerBets(Player p) {
        UUID uuid = p.getUniqueId();
        boolean refunded = false;

        // 1. CoinFlip
        if (cfBets.containsKey(uuid)) {
            CoinFlipBet b = cfBets.remove(uuid);
            if (b != null) {
                economy.depositPlayer(p, b.amount);
                refunded = true;
            }
        }

        // 2. RPS (Ожидающие ставки)
        if (rpsBets.containsKey(uuid)) {
            Double bet = rpsBets.remove(uuid);
            if (bet != null) {
                economy.depositPlayer(p, bet);
                refunded = true;
            }
        }

        // 3. Активные игры RPS (если игрок завис в игре)
        if (activeRPSGames.containsKey(uuid)) {
            RPSGame g = activeRPSGames.remove(uuid);
            if (g != null) {
                // Нужно удалить игру и у противника
                Player opponent = g.p1.getUniqueId().equals(uuid) ? g.p2 : g.p1;
                activeRPSGames.remove(opponent.getUniqueId());
                
                // Возвращаем деньги обоим
                economy.depositPlayer(p, g.bet);
                economy.depositPlayer(opponent, g.bet);
                
                if (opponent.isOnline()) {
                    opponent.sendMessage("§c[RPS] Игра отменена (соперник переродился). Ставка возвращена.");
                }
                refunded = true;
            }
        }

        // 4. Jackpot
        synchronized (jackpotEntries) {
            Iterator<JackpotEntry> it = jackpotEntries.iterator();
            while (it.hasNext()) {
                JackpotEntry entry = it.next();
                if (entry.player.equals(uuid)) {
                    economy.depositPlayer(p, entry.amount);
                    jackpotPool -= entry.amount; // Уменьшаем пул
                    it.remove();
                    refunded = true;
                }
            }
        }
        // Если джекпот стал пустым, сбрасываем таймер или оставляем как есть (пул уменьшился)
        if (jackpotPool < 0) jackpotPool = 0;

        // 5. Очистка состояний
        cfEntering.remove(uuid);
        cfCreatingSide.remove(uuid);
        jpEntering.remove(uuid);
        rpsEntering.remove(uuid);

        if (refunded) {
            p.sendMessage("§e⚠ §lВаши активные ставки в казино были возвращены на баланс перед сбросом!");
        }
    }
    // -------------------------------------------------------

    private double parseMoney(String input) {
        input = input.trim().replace(",", "").replace(" ", "");
        Matcher m = MONEY_PATTERN.matcher(input);
        if (!m.matches()) return -1;

        double value = Double.parseDouble(m.group(1));
        String suffix = m.group(2);

        if (suffix != null) {
            switch (suffix.toLowerCase()) {
                case "k": value *= 1_000; break;
                case "m": value *= 1_000_000; break;
                case "b": value *= 1_000_000_000; break;
                case "t": value *= 1_000_000_000_000L; break;
            }
        }
        return value;
    }

    private String format(double amount) {
        if (amount < 0) return "-" + format(-amount);

        if (amount >= 1_000_000_000_000L) {
            double val = amount / 1_000_000_000_000L;
            return (val == (long) val) ? String.format("%,d", (long) val) + "T" : String.format("%.1fT", val);
        }
        if (amount >= 1_000_000_000) {
            double val = amount / 1_000_000_000;
            return (val == (long) val) ? String.format("%,d", (long) val) + "B" : String.format("%.1fB", val);
        }
        if (amount >= 1_000_000) {
            double val = amount / 1_000_000;
            return (val == (long) val) ? String.format("%,d", (long) val) + "M" : String.format("%.1fM", val);
        }
        if (amount >= 10_000) {
            double val = amount / 1_000;
            return (val == (long) val) ? String.format("%,d", (long) val) + "K" : String.format("%.1fK", val);
        }
        return String.format("%,d", (long) amount);
    }

    private String getActiveGameMessage(UUID uuid) {
        if (cfBets.containsKey(uuid)) {
            return "§cУ вас уже есть ставка в CoinFlip! Удалите её сначала.";
        }
        if (rpsBets.containsKey(uuid)) {
            return "§cУ вас уже есть ставка в RPS! Удалите её сначала.";
        }
        if (activeRPSGames.containsKey(uuid)) {
            return "§cВы уже в активной игре RPS!";
        }
        return null;
    }

    private boolean isInAnyGame(Player p) {
        String msg = getActiveGameMessage(p.getUniqueId());
        if (msg != null) {
            p.sendMessage(msg);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 1);
            return true;
        }
        return false;
    }

    @Override
    public void onEnable() {
        instance = this; // <--- ВАЖНО

        if (!setupEconomy()) {
            getLogger().severe("Vault не найден!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().warning("Не удалось создать data.yml");
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        getCommand("casino").setExecutor(this);
        getCommand("daily").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                openJackpotRound();
            }
        }.runTaskTimer(this, 20 * 60 * 20L, 20 * 60 * 20L);

        getLogger().info("BrainrotExtras включён!");
    }

    @Override
    public void onDisable() {
        for (CoinFlipBet b : cfBets.values()) {
            economy.depositPlayer(Bukkit.getOfflinePlayer(b.owner), b.amount);
        }
        synchronized (jackpotEntries) {
            for (JackpotEntry e : jackpotEntries) {
                economy.depositPlayer(Bukkit.getOfflinePlayer(e.player), e.amount);
            }
        }
        for (Map.Entry<UUID, Double> entry : rpsBets.entrySet()) {
            economy.depositPlayer(Bukkit.getOfflinePlayer(entry.getKey()), entry.getValue());
        }
        for (RPSGame g : new HashSet<>(activeRPSGames.values())) {
            economy.depositPlayer(g.p1, g.bet);
            economy.depositPlayer(g.p2, g.bet);
        }
        if (jackpotTimer != null) {
            try { jackpotTimer.cancel(); } catch (Exception ignored) {}
        }
        animationLock.clear();
        rpsChoiceLock.clear();
        rpsWaitLock.clear();
        saveData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        if (cmd.getName().equalsIgnoreCase("daily")) openDailyMenu(p);
        if (cmd.getName().equalsIgnoreCase("casino")) openMainMenu(p);
        return true;
    }

    private void openMainMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§5§lКазино Brainrot");
        double bal = economy.getBalance(p);
        inv.setItem(4, createItem(Material.GOLD_NUGGET, "§6Ваш баланс: §f$" + format(bal)));

        inv.setItem(10, createItem(Material.SUNFLOWER, "§6§lCoinFlip",
                "§7Шанс 50%", "§7Орел или Решка?", "§7Игра 1 на 1",
                cfBets.containsKey(p.getUniqueId()) ? "§e⚠ У вас есть ставка" : "",
                "§eНажмите!"));
        inv.setItem(12, createItem(Material.GOLD_BLOCK, "§a§lJackpot",
                "§7Общий банк", "§7Шанс зависит от вклада",
                "§7Розыгрыш каждые 20 мин",
                jackpotOpen ? "§a● ОТКРЫТ §7(§e" + jackpotSeconds + "с§7)" : "§c● ЗАКРЫТ",
                "§eНажмите!"));
        inv.setItem(14, createItem(Material.SHEARS, "§b§lКамень-Ножницы",
                "§7PvP игра", "§7Ничья = возврат",
                rpsBets.containsKey(p.getUniqueId()) ? "§e⚠ У вас есть ставка" : "",
                "§eНажмите!"));
        inv.setItem(16, createItem(Material.NOTE_BLOCK, "§d§lСлоты",
                "§7Крути барабан", "§7Выигрыш до x50",
                "§7Стоимость: §6$2K", "§eНажмите!"));
        fillGlass(inv);
        p.openInventory(inv);
    }

    private void openCFMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§6§lCoinFlip");
        buildCFInventory(inv, p);
        p.openInventory(inv);
    }

    private void buildCFInventory(Inventory inv, Player viewer) {
        inv.clear();

        List<CoinFlipBet> sorted = new ArrayList<>(cfBets.values());
        boolean desc = cfSortDesc.getOrDefault(viewer.getUniqueId(), true);
        sorted.sort((a, b) -> desc
                ? Double.compare(b.amount, a.amount)
                : Double.compare(a.amount, b.amount));

        int i = 0;
        for (CoinFlipBet b : sorted) {
            if (i >= 44) break;
            OfflinePlayer owner = Bukkit.getOfflinePlayer(b.owner);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta m = (SkullMeta) head.getItemMeta();
            m.setOwningPlayer(owner);
            m.setDisplayName("§eСтавка от " + owner.getName());
            List<String> lore = new ArrayList<>();
            lore.add("§7Сумма: §6$" + format(b.amount));
            lore.add("§7Выбор: " + (b.isHeads ? "§eОрел ☀" : "§7Решка ●"));
            lore.add("");
            if (b.owner.equals(viewer.getUniqueId())) {
                lore.add("§c▶ Нажмите, чтобы удалить");
            } else {
                lore.add("§a▶ Нажмите, чтобы принять!");
            }
            m.setLore(lore);
            head.setItemMeta(m);
            inv.setItem(i++, head);
        }

        for (int s = 45; s < 54; s++) inv.setItem(s, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(45, createItem(Material.ARROW, "§c« Назад"));
        inv.setItem(47, createItem(Material.COMPARATOR, "§eСортировка",
                desc ? "§7Сейчас: §6По убыванию ↓" : "§7Сейчас: §6По возрастанию ↑",
                "§7Нажмите чтобы сменить"));
        inv.setItem(49, createItem(Material.LIME_DYE, "§a§lСоздать ставку", "§7Нажмите, чтобы создать игру"));
        inv.setItem(51, createItem(Material.SUNFLOWER, "§6Обновить", "§7Обновить список ставок"));
        inv.setItem(53, createItem(Material.GOLD_NUGGET, "§6Баланс: §f$" + format(economy.getBalance(viewer))));
    }

    private void openJackpotMenu(Player p) {
        if (!jackpotOpen) {
            Inventory inv = Bukkit.createInventory(null, 27, "§a§lJackpot: Закрыт");
            fillGlass(inv);
            inv.setItem(13, createItem(Material.BARRIER, "§c§lДжекпот закрыт!",
                    "§7Розыгрыш начинается",
                    "§7автоматически каждые 20 минут.",
                    "", "§7Следите за объявлениями в чате!"));
            inv.setItem(22, createItem(Material.ARROW, "§c« Назад"));
            p.openInventory(inv);
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, "§a§lJackpot");
        buildJackpotInventory(inv, p);
        p.openInventory(inv);
    }

    private void buildJackpotInventory(Inventory inv, Player viewer) {
        inv.clear();

        inv.setItem(4, createItem(Material.GOLD_BLOCK,
                "§6§lБанк: $" + format(jackpotPool),
                "§7Таймер: §e" + jackpotSeconds + "с",
                "§7Участников: §e" + countUniqueJackpotPlayers(),
                "§7Ваш шанс: §b" + getJackpotChance(viewer) + "%",
                "", "§7После таймера — розыгрыш!"));

        List<JackpotEntry> sorted;
        synchronized (jackpotEntries) {
            sorted = new ArrayList<>(jackpotEntries);
        }
        sorted.sort((a, b) -> Double.compare(b.amount, a.amount));

        int slot = 9;
        for (JackpotEntry e : sorted) {
            if (slot >= 45) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta m = (SkullMeta) head.getItemMeta();
            OfflinePlayer op = Bukkit.getOfflinePlayer(e.player);
            m.setOwningPlayer(op);
            m.setDisplayName("§e" + op.getName());
            double chance = (jackpotPool > 0) ? (e.amount / jackpotPool * 100) : 0;
            m.setLore(Arrays.asList(
                    "§7Внес: §6$" + format(e.amount),
                    "§7Шанс: §b" + String.format("%.1f", chance) + "%"));
            head.setItemMeta(m);
            inv.setItem(slot++, head);
        }

        for (int s = 45; s < 54; s++) inv.setItem(s, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(45, createItem(Material.ARROW, "§c« Назад"));
        inv.setItem(49, createItem(Material.LIME_DYE, "§a§lВнести деньги", "§7Нажмите и введите сумму"));
        inv.setItem(51, createItem(Material.GOLD_BLOCK, "§6Обновить", "§7Обновить информацию"));
        inv.setItem(53, createItem(Material.GOLD_NUGGET, "§6Баланс: §f$" + format(economy.getBalance(viewer))));
    }

    private int countUniqueJackpotPlayers() {
        Set<UUID> unique = new HashSet<>();
        synchronized (jackpotEntries) {
            for (JackpotEntry e : jackpotEntries) unique.add(e.player);
        }
        return unique.size();
    }

    private void openRPSMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§b§lRPS: КНБ");
        buildRPSInventory(inv, p);
        p.openInventory(inv);
    }

    private void buildRPSInventory(Inventory inv, Player viewer) {
        inv.clear();

        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(rpsBets.entrySet());
        boolean desc = rpsSortDesc.getOrDefault(viewer.getUniqueId(), true);
        sorted.sort((a, b) -> desc
                ? Double.compare(b.getValue(), a.getValue())
                : Double.compare(a.getValue(), b.getValue()));

        int i = 0;
        for (Map.Entry<UUID, Double> entry : sorted) {
            if (i >= 44) break;
            OfflinePlayer owner = Bukkit.getOfflinePlayer(entry.getKey());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta m = (SkullMeta) head.getItemMeta();
            m.setOwningPlayer(owner);
            m.setDisplayName("§e" + owner.getName());
            List<String> lore = new ArrayList<>();
            lore.add("§7Ставка: §6$" + format(entry.getValue()));
            lore.add("");
            if (entry.getKey().equals(viewer.getUniqueId())) {
                lore.add("§c▶ Нажмите, чтобы удалить");
            } else {
                lore.add("§a▶ Нажми, чтобы сыграть!");
            }
            m.setLore(lore);
            head.setItemMeta(m);
            inv.setItem(i++, head);
        }

        for (int s = 45; s < 54; s++) inv.setItem(s, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(45, createItem(Material.ARROW, "§c« Назад"));
        inv.setItem(47, createItem(Material.COMPARATOR, "§eСортировка",
                desc ? "§7Сейчас: §6По убыванию ↓" : "§7Сейчас: §6По возрастанию ↑",
                "§7Нажмите чтобы сменить"));
        inv.setItem(49, createItem(Material.LIME_DYE, "§a§lСоздать игру", "§7Нажмите и введите ставку"));
        inv.setItem(51, createItem(Material.SHEARS, "§bОбновить", "§7Обновить список игр"));
        inv.setItem(53, createItem(Material.GOLD_NUGGET, "§6Баланс: §f$" + format(economy.getBalance(viewer))));
    }

    private void openRPSGameMenu(Player p) {
        switchingMenu.add(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 9, "§bВыбор: К/Н/Б");
        inv.setItem(2, createItem(Material.COBBLESTONE, "§7§lКамень", "§7Бьёт ножницы"));
        inv.setItem(4, createItem(Material.SHEARS, "§c§lНожницы", "§7Режут бумагу"));
        inv.setItem(6, createItem(Material.PAPER, "§f§lБумага", "§7Оборачивает камень"));
        p.openInventory(inv);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            switchingMenu.remove(p.getUniqueId());
            rpsChoiceLock.add(p.getUniqueId());
        }, 2L);
    }

    private void openRPSWaitMenu(Player p) {
        rpsChoiceLock.remove(p.getUniqueId());
        switchingMenu.add(p.getUniqueId());
        Inventory waitInv = Bukkit.createInventory(null, 9, "§bОжидание...");
        waitInv.setItem(4, createItem(Material.CLOCK, "§eОжидание соперника..."));
        p.openInventory(waitInv);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            switchingMenu.remove(p.getUniqueId());
            rpsWaitLock.add(p.getUniqueId());
        }, 2L);
    }

    private void openSlotsMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§d§lСлоты");
        buildSlotsIdle(inv, p);
        slotsInventory.put(p.getUniqueId(), inv);
        p.openInventory(inv);
    }

    private void buildSlotsIdle(Inventory inv, Player p) {
        inv.clear();
        fillGlass(inv);
        inv.setItem(11, createItem(Material.BLACK_STAINED_GLASS_PANE, "§8▬▬▬"));
        inv.setItem(13, createItem(Material.BLACK_STAINED_GLASS_PANE, "§8▬▬▬"));
        inv.setItem(15, createItem(Material.BLACK_STAINED_GLASS_PANE, "§8▬▬▬"));
        inv.setItem(22, createItem(Material.LIME_DYE, "§a§l⟳ КРУТИТЬ",
                "§7Стоимость: §6$2K", "§7Нажмите чтобы крутить!"));
        inv.setItem(18, createItem(Material.ARROW, "§c« Назад"));
        inv.setItem(4, createItem(Material.NOTE_BLOCK, "§d§lСлоты",
                "§7Три одинаковых = §6$25K",
                "§7Два одинаковых = §6$3K",
                "§7Ничего = §c-$2K",
                "",
                "§6Баланс: §f$" + format(economy.getBalance(p))));
    }

    private String getSlotName(Material m) {
        switch (m) {
            case DIAMOND: return "§b§l💎 Алмаз";
            case GOLD_INGOT: return "§6§l🥇 Золото";
            case EMERALD: return "§a§l🟢 Изумруд";
            case IRON_INGOT: return "§7§l⬜ Железо";
            default: return "§e?";
        }
    }

    private void spinSlots(Player p) {
        int cost = 2000;
        if (!economy.has(p, cost)) {
            p.sendMessage("§cНедостаточно денег! Нужно $" + format(cost));
            return;
        }
        economy.withdrawPlayer(p, cost);

        Inventory inv = slotsInventory.get(p.getUniqueId());
        if (inv == null) return;

        animationLock.add(p.getUniqueId());

        inv.setItem(22, createItem(Material.GRAY_DYE, "§7§lКрутится..."));
        inv.setItem(18, createItem(Material.PURPLE_STAINED_GLASS_PANE, " "));
        inv.setItem(4, createItem(Material.NOTE_BLOCK, "§d§lКрутится...", "§7Удачи!"));

        Material[] items = {Material.DIAMOND, Material.GOLD_INGOT, Material.EMERALD, Material.IRON_INGOT};

        new BukkitRunnable() {
            int tick = 0;

            public void run() {
                if (!p.isOnline()) {
                    animationLock.remove(p.getUniqueId());
                    slotsInventory.remove(p.getUniqueId());
                    cancel();
                    return;
                }

                if (tick < 20) {
                    inv.setItem(11, createItem(items[random.nextInt(items.length)], "§e?"));
                    inv.setItem(13, createItem(items[random.nextInt(items.length)], "§e?"));
                    inv.setItem(15, createItem(items[random.nextInt(items.length)], "§e?"));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1, 2);
                }

                if (tick == 20) {
                    Material m1 = items[random.nextInt(items.length)];
                    Material m2 = items[random.nextInt(items.length)];
                    Material m3 = items[random.nextInt(items.length)];

                    inv.setItem(11, createItem(m1, getSlotName(m1)));
                    inv.setItem(13, createItem(m2, getSlotName(m2)));
                    inv.setItem(15, createItem(m3, getSlotName(m3)));

                    String resultText;
                    if (m1 == m2 && m2 == m3) {
                        economy.depositPlayer(p, 25000);
                        resultText = "§a§lJACKPOT! +$25K!";
                        p.sendTitle("§a§lJACKPOT!", "§6+$25K", 10, 40, 10);
                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1);
                    } else if (m1 == m2 || m2 == m3 || m1 == m3) {
                        economy.depositPlayer(p, 3000);
                        resultText = "§aПара! +$3K!";
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1.5f);
                    } else {
                        resultText = "§cНе повезло! -$2K";
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                    }

                    inv.setItem(22, createItem(Material.LIME_DYE, "§a§l⟳ КРУТИТЬ СНОВА",
                            "§7Стоимость: §6$2K", "§7Нажмите чтобы крутить!"));
                    inv.setItem(18, createItem(Material.ARROW, "§c« Назад"));
                    inv.setItem(4, createItem(Material.NOTE_BLOCK, "§d§lРезультат:",
                            resultText,
                            "",
                            "§6Баланс: §f$" + format(economy.getBalance(p))));

                    animationLock.remove(p.getUniqueId());
                    cancel();
                }
                tick++;
            }
        }.runTaskTimer(this, 0, 2);
    }

    private void startCF(Player p1, Player p2, CoinFlipBet bet) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6§lCoinFlip: Розыгрыш");
        fillGlass(inv);

        ItemStack head1 = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm1 = (SkullMeta) head1.getItemMeta();
        sm1.setOwningPlayer(p1);
        sm1.setDisplayName("§e" + p1.getName());
        sm1.setLore(Arrays.asList(
                bet.isHeads ? "§eОрел ☀" : "§7Решка ●",
                "§7Ставка: §6$" + format(bet.amount)));
        head1.setItemMeta(sm1);
        inv.setItem(10, head1);

        ItemStack head2 = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm2 = (SkullMeta) head2.getItemMeta();
        sm2.setOwningPlayer(p2);
        sm2.setDisplayName("§e" + p2.getName());
        sm2.setLore(Arrays.asList(
                bet.isHeads ? "§7Решка ●" : "§eОрел ☀",
                "§7Ставка: §6$" + format(bet.amount)));
        head2.setItemMeta(sm2);
        inv.setItem(16, head2);

        animationLock.add(p1.getUniqueId());
        animationLock.add(p2.getUniqueId());

        if (p1.isOnline()) p1.openInventory(inv);
        if (p2.isOnline()) p2.openInventory(inv);

        final boolean winHeads = random.nextBoolean();
        final Player winner = (bet.isHeads == winHeads) ? p1 : p2;
        final Player loser = (winner == p1) ? p2 : p1;

        new BukkitRunnable() {
            int tick = 0;
            boolean frame = true;

            @Override
            public void run() {
                if (!p1.isOnline() || !p2.isOnline()) {
                    if (p1.isOnline()) { economy.depositPlayer(p1, bet.amount); animationLock.remove(p1.getUniqueId()); }
                    if (p2.isOnline()) { economy.depositPlayer(p2, bet.amount); animationLock.remove(p2.getUniqueId()); }
                    cancel();
                    return;
                }

                if (tick < 40 && tick % 2 == 0) {
                    inv.setItem(13, frame
                            ? createItem(Material.SUNFLOWER, "§e§lОРЕЛ ☀")
                            : createItem(Material.IRON_NUGGET, "§7§lРЕШКА ●"));
                    frame = !frame;
                    p1.playSound(p1.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1, 1);
                    p2.playSound(p2.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1, 1);
                }

                if (tick == 40) {
                    double winAmount = bet.amount * 2;
                    inv.setItem(13, winHeads
                            ? createItem(Material.SUNFLOWER, "§e§l>>> ОРЕЛ ☀ <<<")
                            : createItem(Material.IRON_NUGGET, "§7§l>>> РЕШКА ● <<<"));

                    economy.depositPlayer(winner, winAmount);

                    Bukkit.broadcastMessage("§6[CoinFlip] §e" + winner.getName()
                            + " §fобыграл §c" + loser.getName()
                            + " §fна §6$" + format(winAmount));

                    winner.sendTitle("§a§lПОБЕДА!", "§6+$" + format(winAmount), 10, 40, 10);
                    loser.sendTitle("§c§lПОРАЖЕНИЕ", "§c-$" + format(bet.amount), 10, 40, 10);
                    winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                    loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                }

                if (tick == 60) {
                    animationLock.remove(p1.getUniqueId());
                    animationLock.remove(p2.getUniqueId());
                    if (p1.isOnline()) p1.closeInventory();
                    if (p2.isOnline()) p2.closeInventory();
                    cancel();
                }
                tick++;
            }
        }.runTaskTimer(this, 0, 1);
    }

    private void openJackpotRound() {
        jackpotOpen = true;
        jackpotRunning = true;
        jackpotSeconds = 60;
        jackpotPool = 0;
        synchronized (jackpotEntries) {
            jackpotEntries.clear();
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§a§l╔══════════════════════════════╗");
        Bukkit.broadcastMessage("§a§l║   §6§l⚡ JACKPOT ОТКРЫТ! ⚡      §a§l║");
        Bukkit.broadcastMessage("§a§l║   §fВноси ставки! §e/casino     §a§l║");
        Bukkit.broadcastMessage("§a§l║   §7Розыгрыш через §e60 секунд  §a§l║");
        Bukkit.broadcastMessage("§a§l╚══════════════════════════════╝");
        Bukkit.broadcastMessage("");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
        }

        jackpotTimer = new BukkitRunnable() {
            @Override
            public void run() {
                jackpotSeconds--;
                if (jackpotSeconds == 30) {
                    Bukkit.broadcastMessage("§a[Jackpot] §e30 секунд! Банк: §6$" + format(jackpotPool));
                }
                if (jackpotSeconds == 10) {
                    Bukkit.broadcastMessage("§a[Jackpot] §c10 СЕКУНД! Банк: §6$" + format(jackpotPool));
                }
                if (jackpotSeconds <= 5 && jackpotSeconds > 0) {
                    Bukkit.broadcastMessage("§a[Jackpot] §c" + jackpotSeconds + "...");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 2);
                    }
                }
                if (jackpotSeconds <= 0) {
                    rollJackpot();
                    cancel();
                }
            }
        };
        jackpotTimer.runTaskTimer(this, 20, 20);
    }

    private void addJackpotBet(Player p, double amount) {
        if (!jackpotOpen) {
            p.sendMessage("§cДжекпот сейчас закрыт! Ожидайте следующий раунд.");
            return;
        }
        if (amount < 2000) { p.sendMessage("§cМинимум $2K!"); return; }
        if (!economy.has(p, amount)) { p.sendMessage("§cНедостаточно денег!"); return; }

        economy.withdrawPlayer(p, amount);
        synchronized (jackpotEntries) {
            jackpotEntries.add(new JackpotEntry(p.getUniqueId(), amount));
            jackpotPool += amount;
        }

        Bukkit.broadcastMessage("§a[Jackpot] §f" + p.getName()
                + " внес §6$" + format(amount) + "§f! Банк: §6$" + format(jackpotPool));
    }

    private void rollJackpot() {
        jackpotOpen = false;
        jackpotRunning = false;

        synchronized (jackpotEntries) {
            if (jackpotEntries.isEmpty()) {
                Bukkit.broadcastMessage("§a[Jackpot] §7Никто не участвовал. Раунд отменён.");
                jackpotPool = 0;
                jackpotEntries.clear();
                return;
            }

            if (countUniqueJackpotPlayers() <= 1) {
                for (JackpotEntry e : jackpotEntries) {
                    economy.depositPlayer(Bukkit.getOfflinePlayer(e.player), e.amount);
                }
                Player solo = Bukkit.getPlayer(jackpotEntries.get(0).player);
                if (solo != null) solo.sendMessage("§a[Jackpot] §fНикто больше не участвовал. Деньги возвращены.");
                Bukkit.broadcastMessage("§a[Jackpot] §7Недостаточно участников. Деньги возвращены.");
                jackpotPool = 0;
                jackpotEntries.clear();
                return;
            }

            double total = jackpotPool;
            double r = random.nextDouble() * total;
            double current = 0;
            UUID winnerUUID = jackpotEntries.get(0).player;

            for (JackpotEntry e : jackpotEntries) {
                current += e.amount;
                if (r <= current) { winnerUUID = e.player; break; }
            }

            OfflinePlayer w = Bukkit.getOfflinePlayer(winnerUUID);
            economy.depositPlayer(w, total);

            double winnerBet = 0;
            for (JackpotEntry e : jackpotEntries) {
                if (e.player.equals(winnerUUID)) winnerBet += e.amount;
            }
            double winChance = (winnerBet / total) * 100;

            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("§a§l╔══════════════════════════════╗");
            Bukkit.broadcastMessage("§a§l║   §6§l⚡ JACKPOT ПОБЕДИТЕЛЬ! ⚡  §a§l║");
            Bukkit.broadcastMessage("§a§l║   §e" + w.getName());
            Bukkit.broadcastMessage("§a§l║   §fВыиграл: §6$" + format(total));
            Bukkit.broadcastMessage("§a§l║   §7Шанс: §b" + String.format("%.1f", winChance) + "%");
            Bukkit.broadcastMessage("§a§l╚══════════════════════════════╝");
            Bukkit.broadcastMessage("");

            if (w.isOnline()) {
                Player wp = w.getPlayer();
                wp.sendTitle("§a§lJACKPOT!", "§6+$" + format(total), 10, 60, 20);
                wp.playSound(wp.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1);
            }

            for (Player op : Bukkit.getOnlinePlayers()) {
                op.playSound(op.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1, 1);
            }

            jackpotPool = 0;
            jackpotEntries.clear();
        }
    }

    private String getJackpotChance(Player p) {
        if (jackpotPool == 0) return "0.0";
        double myBet = 0;
        synchronized (jackpotEntries) {
            for (JackpotEntry e : jackpotEntries) {
                if (e.player.equals(p.getUniqueId())) myBet += e.amount;
            }
        }
        return String.format("%.1f", (myBet / jackpotPool) * 100);
    }

    private void finishRPS(RPSGame g) {
        if (g.p1Choice == 0 || g.p2Choice == 0) return;

        UUID u1 = g.p1.getUniqueId();
        UUID u2 = g.p2.getUniqueId();
        rpsChoiceLock.remove(u1);
        rpsChoiceLock.remove(u2);
        rpsWaitLock.remove(u1);
        rpsWaitLock.remove(u2);
        animationLock.remove(u1);
        animationLock.remove(u2);

        String[] names = {"", "Камень", "Бумага", "Ножницы"};
        Player w = null;
        boolean draw = (g.p1Choice == g.p2Choice);

        if (!draw) {
            if ((g.p1Choice == 1 && g.p2Choice == 3)
                    || (g.p1Choice == 2 && g.p2Choice == 1)
                    || (g.p1Choice == 3 && g.p2Choice == 2)) {
                w = g.p1;
            } else {
                w = g.p2;
            }
        }

        if (draw) {
            economy.depositPlayer(g.p1, g.bet);
            economy.depositPlayer(g.p2, g.bet);
            String msg = "§e[RPS] Ничья! (" + names[g.p1Choice] + " vs " + names[g.p2Choice] + ") Деньги возвращены.";
            if (g.p1.isOnline()) { g.p1.sendMessage(msg); g.p1.playSound(g.p1.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 1); }
            if (g.p2.isOnline()) { g.p2.sendMessage(msg); g.p2.playSound(g.p2.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 1); }
        } else {
            double winAmount = g.bet * 2;
            economy.depositPlayer(w, winAmount);
            Player loser = (w == g.p1) ? g.p2 : g.p1;

            Bukkit.broadcastMessage("§b[RPS] §e" + w.getName()
                    + " §7(" + names[w == g.p1 ? g.p1Choice : g.p2Choice] + ")"
                    + " §fпобедил §c" + loser.getName()
                    + " §7(" + names[loser == g.p1 ? g.p1Choice : g.p2Choice] + ")"
                    + " §fна §6$" + format(winAmount));

            if (w.isOnline()) {
                w.sendTitle("§a§lПОБЕДА!", "§6+$" + format(winAmount), 10, 40, 10);
                w.playSound(w.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
            }
            if (loser.isOnline()) {
                loser.sendTitle("§c§lПОРАЖЕНИЕ", "§c-$" + format(g.bet), 10, 40, 10);
                loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            }
        }

        if (g.p1.isOnline()) g.p1.closeInventory();
        if (g.p2.isOnline()) g.p2.closeInventory();

        activeRPSGames.remove(u1);
        activeRPSGames.remove(u2);
    }

    private void openDailyMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§e§lЕжедневные Награды");
        long last = dataConfig.getLong("daily." + p.getUniqueId(), 0);
        long now = System.currentTimeMillis();

        ItemStack item;
        // OP может забирать всегда
        if (p.isOp() || now - last >= 86400000L) {
            List<String> l = new ArrayList<>();
            l.add("§a▶ Нажми чтобы забрать!");
            if (p.isOp()) l.add("§c(Вы OP: Таймер игнорируется)");
            l.add("");
            for (DailyTier tier : DailyTier.values()) {
                l.add(tier.name + ": §6$" + format(tier.amount) + " §8(" + tier.chance + "%)");
            }
            item = createItem(Material.CHEST_MINECART, "§a§lЗАБРАТЬ", l);
        } else {
            long left = (last + 86400000L) - now;
            long h = left / 3600000;
            long m = (left % 3600000) / 60000;
            item = createItem(Material.MINECART, "§c§lОЖИДАНИЕ...",
                    "§7Осталось: §e" + h + "ч " + m + "м", "", "§7Возвращайтесь позже!");
        }
        inv.setItem(13, item);
        fillGlass(inv);
        p.openInventory(inv);
    }

    private void claimDaily(Player p) {
        long last = dataConfig.getLong("daily." + p.getUniqueId(), 0);

        if (!p.isOp() && System.currentTimeMillis() - last < 86400000L) {
            p.sendMessage("§cНаграда еще не доступна!");
            return;
        }

        double r = random.nextDouble() * 100;
        double c = 0;
        DailyTier won = DailyTier.COMMON;
        for (DailyTier tier : DailyTier.values()) {
            c += tier.chance;
            if (r <= c) { won = tier; break; }
        }

        economy.depositPlayer(p, won.amount);
        dataConfig.set("daily." + p.getUniqueId(), System.currentTimeMillis());
        saveData();
        p.closeInventory();
        p.sendTitle(won.name, "§7+$" + format(won.amount), 10, 40, 10);

        if (won == DailyTier.LEGENDARY || won == DailyTier.EPIC) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            Bukkit.broadcastMessage("§e§lBrainrot §8>> §fИгрок §6" + p.getName()
                    + " §fвыбил " + won.name + " §fнаграду в §a/daily§f!");
        } else {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        Player p = (Player) e.getPlayer();
        UUID uuid = p.getUniqueId();
        String title = e.getView().getTitle();

        if (switchingMenu.contains(uuid)) return;

        if (title.equals("§d§lСлоты") && !animationLock.contains(uuid)) {
            slotsInventory.remove(uuid);
        }

        if (animationLock.contains(uuid)) {
            final Inventory inv = e.getInventory();
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (animationLock.contains(uuid) && p.isOnline() && !switchingMenu.contains(uuid)) {
                    p.openInventory(inv);
                }
            }, 1L);
            return;
        }

        if (rpsChoiceLock.contains(uuid)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (rpsChoiceLock.contains(uuid) && p.isOnline() && !switchingMenu.contains(uuid)) {
                    RPSGame g = activeRPSGames.get(uuid);
                    if (g != null) {
                        boolean madeChoice = uuid.equals(g.p1.getUniqueId()) ? g.p1Choice != 0 : g.p2Choice != 0;
                        if (!madeChoice) {
                            openRPSGameMenu(p);
                        }
                    }
                }
            }, 1L);
            return;
        }

        if (rpsWaitLock.contains(uuid)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (rpsWaitLock.contains(uuid) && p.isOnline() && !switchingMenu.contains(uuid)) {
                    Inventory waitInv = Bukkit.createInventory(null, 9, "§bОжидание...");
                    waitInv.setItem(4, createItem(Material.CLOCK, "§eОжидание соперника..."));
                    p.openInventory(waitInv);
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();

        animationLock.remove(uuid);
        rpsChoiceLock.remove(uuid);
        rpsWaitLock.remove(uuid);
        switchingMenu.remove(uuid);
        cfEntering.remove(uuid);
        cfCreatingSide.remove(uuid);
        cfSortDesc.remove(uuid);
        jpEntering.remove(uuid);
        rpsEntering.remove(uuid);
        rpsSortDesc.remove(uuid);
        slotsInventory.remove(uuid);

        CoinFlipBet cfBet = cfBets.remove(uuid);
        if (cfBet != null) economy.depositPlayer(e.getPlayer(), cfBet.amount);

        Double rpsBet = rpsBets.remove(uuid);
        if (rpsBet != null) economy.depositPlayer(e.getPlayer(), rpsBet);

        RPSGame g = activeRPSGames.remove(uuid);
        if (g != null) {
            UUID otherUUID = uuid.equals(g.p1.getUniqueId()) ? g.p2.getUniqueId() : g.p1.getUniqueId();
            Player other = uuid.equals(g.p1.getUniqueId()) ? g.p2 : g.p1;

            activeRPSGames.remove(otherUUID);
            rpsChoiceLock.remove(otherUUID);
            rpsWaitLock.remove(otherUUID);
            animationLock.remove(otherUUID);

            economy.depositPlayer(e.getPlayer(), g.bet);
            economy.depositPlayer(other, g.bet);

            if (other.isOnline()) {
                other.sendMessage("§c[RPS] Соперник вышел с сервера. Деньги возвращены.");
                other.closeInventory();
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        boolean handling = cfEntering.containsKey(uuid)
                || jpEntering.containsKey(uuid)
                || rpsEntering.containsKey(uuid);

        if (!handling) return;

        e.setCancelled(true);
        final String msg = e.getMessage().trim();

        Bukkit.getScheduler().runTask(this, () -> {

            // === COINFLIP ===
            if (cfEntering.remove(uuid) != null) {
                double amount = parseMoney(msg);
                if (amount < 0) {
                    p.sendMessage("§cНеверный формат! Примеры: §e10000§c, §e10k§c, §e1.5m§c, §e2b");
                    cfCreatingSide.remove(uuid);
                    return;
                }
                if (amount < 2000) { p.sendMessage("§cМинимум $2K!"); cfCreatingSide.remove(uuid); return; }
                if (!economy.has(p, amount)) { p.sendMessage("§cНедостаточно денег! Баланс: §6$" + format(economy.getBalance(p))); cfCreatingSide.remove(uuid); return; }

                // ПРОВЕРКА НА БЛОКИРОВКУ
                if (isInAnyGame(p)) {
                    cfCreatingSide.remove(uuid);
                    return;
                }

                economy.withdrawPlayer(p, amount);
                boolean side = cfCreatingSide.getOrDefault(uuid, true);
                cfCreatingSide.remove(uuid);
                cfBets.put(uuid, new CoinFlipBet(uuid, amount, side));
                openCFMenu(p);
                Bukkit.broadcastMessage("§6[CoinFlip] §e" + p.getName()
                        + " §fсоздал ставку §6$" + format(amount) + "§f! §a/casino");
                return;
            }

            // === JACKPOT ===
            if (jpEntering.remove(uuid) != null) {
                double amount = parseMoney(msg);
                if (amount < 0) {
                    p.sendMessage("§cНеверный формат! Примеры: §e10000§c, §e10k§c, §e1.5m");
                    return;
                }
                addJackpotBet(p, amount);
                if (jackpotOpen) openJackpotMenu(p);
                return;
            }

            // === RPS ===
            if (rpsEntering.remove(uuid) != null) {
                double amount = parseMoney(msg);
                if (amount < 0) {
                    p.sendMessage("§cНеверный формат! Примеры: §e10000§c, §e10k§c, §e1.5m");
                    return;
                }
                if (amount < 2000) { p.sendMessage("§cМинимум $2K!"); return; }
                if (!economy.has(p, amount)) { p.sendMessage("§cНедостаточно денег! Баланс: §6$" + format(economy.getBalance(p))); return; }

                // ПРОВЕРКА НА БЛОКИРОВКУ
                if (isInAnyGame(p)) return;

                economy.withdrawPlayer(p, amount);
                rpsBets.put(uuid, amount);
                openRPSMenu(p);
                Bukkit.broadcastMessage("§b[RPS] §e" + p.getName()
                        + " §fсоздал ставку §6$" + format(amount));
            }
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;

        String title = e.getView().getTitle();
        if (!MENU_TITLES.contains(title)) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.PURPLE_STAINED_GLASS_PANE
                || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        Player p = (Player) e.getWhoClicked();

        if (animationLock.contains(p.getUniqueId())
                && !title.equals("§bВыбор: К/Н/Б")
                && !title.equals("§bОжидание...")) return;

        switch (title) {
            case "§5§lКазино Brainrot":
                handleMainMenu(p, e.getSlot());
                break;
            case "§6§lCoinFlip":
                handleCFMenu(p, e);
                break;
            case "§6§lВыбор стороны":
                handleCFSideChoice(p, e.getSlot());
                break;
            case "§a§lJackpot":
                handleJackpotMenu(p, e);
                break;
            case "§a§lJackpot: Закрыт":
                if (e.getSlot() == 22) openMainMenu(p);
                break;
            case "§b§lRPS: КНБ":
                handleRPSMenu(p, e);
                break;
            case "§bВыбор: К/Н/Б":
                handleRPSChoice(p, e.getSlot());
                break;
            case "§d§lСлоты":
                handleSlotsMenu(p, e);
                break;
            case "§e§lЕжедневные Награды":
                if (e.getSlot() == 13 && clicked.getType() == Material.CHEST_MINECART) {
                    claimDaily(p);
                }
                break;
            case "§6§lCoinFlip: Розыгрыш":
            case "§bОжидание...":
                break;
        }
    }

    private void handleMainMenu(Player p, int slot) {
        switch (slot) {
            case 10: openCFMenu(p); break;
            case 12: openJackpotMenu(p); break;
            case 14: openRPSMenu(p); break;
            case 16: openSlotsMenu(p); break;
        }
    }

    private void handleCFMenu(Player p, InventoryClickEvent e) {
        int slot = e.getSlot();
        ItemStack clicked = e.getCurrentItem();

        if (slot == 45) { openMainMenu(p); return; }

        if (slot == 47) {
            boolean current = cfSortDesc.getOrDefault(p.getUniqueId(), true);
            cfSortDesc.put(p.getUniqueId(), !current);
            openCFMenu(p);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return;
        }

        if (slot == 49) {
            if (isInAnyGame(p)) return;

            Inventory sideInv = Bukkit.createInventory(null, 9, "§6§lВыбор стороны");
            sideInv.setItem(2, createItem(Material.SUNFLOWER, "§e§lОРЕЛ ☀", "§7Нажмите чтобы выбрать"));
            sideInv.setItem(6, createItem(Material.IRON_NUGGET, "§7§lРЕШКА ●", "§7Нажмите чтобы выбрать"));
            p.openInventory(sideInv);
            return;
        }

        if (slot == 51) {
            openCFMenu(p);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return;
        }

        if (clicked != null && clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() != null) {
            String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            String ownerName = displayName.replace("Ставка от ", "");

            if (p.getName().equals(ownerName)) {
                CoinFlipBet b = cfBets.remove(p.getUniqueId());
                if (b != null) {
                    economy.depositPlayer(p, b.amount);
                    p.sendMessage("§aСтавка $" + format(b.amount) + " возвращена.");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 1);
                }
                openCFMenu(p);
            } else {
                if (isInAnyGame(p)) return;

                Player owner = Bukkit.getPlayer(ownerName);
                if (owner == null || !owner.isOnline()) {
                    p.sendMessage("§cИгрок оффлайн! Ставка удалена.");
                    for (UUID key : new ArrayList<>(cfBets.keySet())) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(key);
                        if (op.getName() != null && op.getName().equals(ownerName)) {
                            CoinFlipBet removed = cfBets.remove(key);
                            if (removed != null) economy.depositPlayer(op, removed.amount);
                            break;
                        }
                    }
                    openCFMenu(p);
                    return;
                }

                CoinFlipBet b = cfBets.get(owner.getUniqueId());
                if (b == null) {
                    p.sendMessage("§cСтавка уже не активна!");
                    openCFMenu(p);
                    return;
                }

                if (!economy.has(p, b.amount)) {
                    p.sendMessage("§cНедостаточно денег! Нужно $" + format(b.amount) + " | Баланс: $" + format(economy.getBalance(p)));
                    return;
                }

                cfBets.remove(owner.getUniqueId());
                economy.withdrawPlayer(p, b.amount);
                p.closeInventory();
                if (owner.isOnline()) owner.closeInventory();
                startCF(owner, p, b);
            }
        }
    }

    private void handleCFSideChoice(Player p, int slot) {
        if (slot == 2) {
            cfCreatingSide.put(p.getUniqueId(), true);
            cfEntering.put(p.getUniqueId(), true);
            p.closeInventory();
            p.sendMessage("§a§lВведите сумму ставки в чат §7(Вы — ОРЕЛ ☀)");
            p.sendMessage("§7Минимум: $2K | Примеры: §e10000§7, §e10k§7, §e1.5m§7, §e2b");
            p.sendMessage("§6Баланс: §f$" + format(economy.getBalance(p)));
        } else if (slot == 6) {
            cfCreatingSide.put(p.getUniqueId(), false);
            cfEntering.put(p.getUniqueId(), true);
            p.closeInventory();
            p.sendMessage("§a§lВведите сумму ставки в чат §7(Вы — РЕШКА ●)");
            p.sendMessage("§7Минимум: $2K | Примеры: §e10000§7, §e10k§7, §e1.5m§7, §e2b");
            p.sendMessage("§6Баланс: §f$" + format(economy.getBalance(p)));
        }
    }

    private void handleJackpotMenu(Player p, InventoryClickEvent e) {
        int slot = e.getSlot();

        if (slot == 45) { openMainMenu(p); return; }

        if (slot == 49) {
            if (!jackpotOpen) {
                p.sendMessage("§cДжекпот закрыт!");
                return;
            }
            jpEntering.put(p.getUniqueId(), true);
            p.closeInventory();
            p.sendMessage("§a§lВведите сумму в чат:");
            p.sendMessage("§7Минимум: $2K | Примеры: §e10000§7, §e10k§7, §e1.5m§7, §e2b");
            p.sendMessage("§6Баланс: §f$" + format(economy.getBalance(p)));
            return;
        }

        if (slot == 51) {
            openJackpotMenu(p);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        }
    }

    private void handleRPSMenu(Player p, InventoryClickEvent e) {
        int slot = e.getSlot();
        ItemStack clicked = e.getCurrentItem();

        if (slot == 45) { openMainMenu(p); return; }

        if (slot == 47) {
            boolean current = rpsSortDesc.getOrDefault(p.getUniqueId(), true);
            rpsSortDesc.put(p.getUniqueId(), !current);
            openRPSMenu(p);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return;
        }

        if (slot == 49) {
            if (isInAnyGame(p)) return;

            rpsEntering.put(p.getUniqueId(), true);
            p.closeInventory();
            p.sendMessage("§a§lВведите сумму ставки в чат:");
            p.sendMessage("§7Минимум: $2K | Примеры: §e10000§7, §e10k§7, §e1.5m§7, §e2b");
            p.sendMessage("§6Баланс: §f$" + format(economy.getBalance(p)));
            return;
        }

        if (slot == 51) {
            openRPSMenu(p);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return;
        }

        if (clicked != null && clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() != null) {
            String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

            if (name.equals(p.getName())) {
                Double bet = rpsBets.remove(p.getUniqueId());
                if (bet != null) {
                    economy.depositPlayer(p, bet);
                    p.sendMessage("§aСтавка $" + format(bet) + " возвращена.");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 1);
                }
                openRPSMenu(p);
                return;
            }

            if (isInAnyGame(p)) return;

            Player owner = Bukkit.getPlayer(name);
            if (owner == null || !owner.isOnline()) {
                p.sendMessage("§cИгрок оффлайн!");
                for (UUID key : new ArrayList<>(rpsBets.keySet())) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(key);
                    if (op.getName() != null && op.getName().equals(name)) {
                        Double removed = rpsBets.remove(key);
                        if (removed != null) economy.depositPlayer(op, removed);
                        break;
                    }
                }
                openRPSMenu(p);
                return;
            }

            Double bet = rpsBets.get(owner.getUniqueId());
            if (bet == null) {
                p.sendMessage("§cСтавка уже не активна!");
                openRPSMenu(p);
                return;
            }

            if (p.getUniqueId().equals(owner.getUniqueId())) {
                p.sendMessage("§cВы не можете играть сами с собой!");
                return;
            }

            if (!economy.has(p, bet)) {
                p.sendMessage("§cНедостаточно денег! Нужно $" + format(bet) + " | Баланс: $" + format(economy.getBalance(p)));
                return;
            }

            rpsBets.remove(owner.getUniqueId());
            economy.withdrawPlayer(p, bet);

            RPSGame g = new RPSGame(owner, p, bet);
            activeRPSGames.put(owner.getUniqueId(), g);
            activeRPSGames.put(p.getUniqueId(), g);

            openRPSGameMenu(owner);
            openRPSGameMenu(p);
        }
    }

    private void handleRPSChoice(Player p, int slot) {
        RPSGame g = activeRPSGames.get(p.getUniqueId());
        if (g == null) return;

        int choice = 0;
        if (slot == 2) choice = 1;
        else if (slot == 4) choice = 3;
        else if (slot == 6) choice = 2;

        if (choice == 0) return;

        boolean isP1 = p.getUniqueId().equals(g.p1.getUniqueId());

        if (isP1 && g.p1Choice != 0) return;
        if (!isP1 && g.p2Choice != 0) return;

        if (isP1) g.p1Choice = choice;
        else g.p2Choice = choice;

        p.sendMessage("§aВыбор сделан!");
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);

        openRPSWaitMenu(p);

        if (g.p1Choice != 0 && g.p2Choice != 0) {
            Bukkit.getScheduler().runTaskLater(this, () -> finishRPS(g), 5L);
        }
    }

    private void handleSlotsMenu(Player p, InventoryClickEvent e) {
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null) return;
        int slot = e.getSlot();

        if (slot == 22 && clicked.getType() == Material.LIME_DYE) {
            spinSlots(p);
            return;
        }

        if (slot == 18 && clicked.getType() == Material.ARROW) {
            slotsInventory.remove(p.getUniqueId());
            openMainMenu(p);
        }
    }

    private void fillGlass(Inventory inv) {
        ItemStack g = createItem(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, g);
        }
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        return createItem(mat, name, Arrays.asList(lore));
    }

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(name);
        if (lore != null && !lore.isEmpty()) {
            List<String> filtered = new ArrayList<>();
            for (String s : lore) {
                if (s != null && !s.isEmpty()) filtered.add(s);
            }
            if (!filtered.isEmpty()) m.setLore(filtered);
        }
        i.setItemMeta(m);
        return i;
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> r = getServer().getServicesManager().getRegistration(Economy.class);
        if (r != null) economy = r.getProvider();
        return economy != null;
    }

    private void saveData() {
        try {
            saveConfigAsync(dataConfig, dataFile);
        } catch (IOException e) {
            getLogger().warning("Не удалось сохранить data.yml");
        }
    }
}