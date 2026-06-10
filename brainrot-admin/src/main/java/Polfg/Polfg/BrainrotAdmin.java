package Polfg.Polfg;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

public class BrainrotAdmin extends JavaPlugin implements Listener, CommandExecutor {
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


    private Economy economy;
    private Chat chat;
    private boolean liteBansEnabled = false;
    private File playersFile;
    private FileConfiguration playersConfig;
    private File logsFolder;

    // История переходов: AdminUUID -> Stack<TargetName>
    private final Map<UUID, Stack<String>> history = new HashMap<>();
    
    // Временное хранение для редактирования
    private final Map<UUID, String> editingMobTarget = new HashMap<>();
    private final Map<UUID, Integer> editingMobIndex = new HashMap<>();

    // Список всех мобов
    private static final List<String> ALL_MOBS_SORTED = Arrays.asList(
        "CHICKEN", "COW", "SHEEP", "PIG", "RABBIT", "PARROT", "TURTLE",
        "FOX", "PANDA", "WOLF", "DOLPHIN", "HORSE", "LLAMA", "POLAR_BEAR", "RAVAGER", "CAT_KUZYA",
        "ENDERMAN", "BLAZE", "WITHER_SKELETON", "IRON_GOLEM", "GUARDIAN", "ENDERMITE", "EVOKER", "VINDICATOR", "HUSK", "STRAY", "ELDER_GUARDIAN",
        "MOOSHROOM", "ZOMBIE_HORSE", "SKELETON_HORSE", "STRIDER", "PHANTOM", "VEX", "MAGMA_CUBE", "HOGLIN", "PIGLIN_BRUTE", "PILLAGER", "SILVERFISH", "WITCH", "CAVE_SPIDER", "SPIDER", "ILLUSIONER", "OCELOT", "BAT", "BEE",
        "CAMEL", "BROWN_PANDA", "CREEPER", "DROWNED", "FROG", "SPONGE", "GOAT", "GLOW_SQUID", "WANDERING_TRADER", "SNOW_GOLEM", "PIGLIN", "MYTHIC_SKELETON_HORSE", "ZOMBIFIED_PIGLIN", "ALLAY", "SNIFFER", "ZOGLIN", "AXOLOTL", "WARDEN"
    );

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault не найден!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        setupChat();
        if (Bukkit.getPluginManager().getPlugin("LiteBans") != null) liteBansEnabled = true;

        playersFile = new File(getDataFolder(), "players.yml");
        if (!playersFile.exists()) {
            try { playersFile.getParentFile().mkdirs(); playersFile.createNewFile(); } 
            catch (IOException e) {}
        }
        playersConfig = YamlConfiguration.loadConfiguration(playersFile);

        logsFolder = new File(getDataFolder(), "logs");
        if (!logsFolder.exists()) logsFolder.mkdirs();

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("brainrotadmin").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("brainrotadmin.use")) return true;
        if (!(sender instanceof Player)) return true;
        if (args.length < 1) return false;
        
        history.remove(((Player) sender).getUniqueId());
        openProfile((Player) sender, args[0]);
        return true;
    }

    private void openProfile(Player admin, String targetName) {
        history.putIfAbsent(admin.getUniqueId(), new Stack<>());
        if (history.get(admin.getUniqueId()).isEmpty() || !history.get(admin.getUniqueId()).peek().equals(targetName)) {
            history.get(admin.getUniqueId()).push(targetName);
        }

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            
            String group = (chat != null) ? chat.getPrimaryGroup(null, target) : "N/A";
            
            List<String> alts = new ArrayList<>();
            String ip = playersConfig.getString("players." + targetName.toLowerCase());
            if (target.isOnline()) ip = target.getPlayer().getAddress().getAddress().getHostAddress();
            
            if (ip != null) {
                List<String> found = playersConfig.getStringList("ips." + ip.replace(".", "_"));
                for (String alt : found) if (!alt.equalsIgnoreCase(targetName)) alts.add(alt);
            }

            List<String> bans = liteBansEnabled ? getLiteBansHistory(target.getUniqueId()) : new ArrayList<>();

            final String fGroup = group;
            final List<String> fAlts = alts;
            final String fIp = ip;
            final List<String> fBans = bans;

            Bukkit.getScheduler().runTask(this, () -> openInfoGui(admin, target, fGroup, fAlts, fIp, fBans));
        });
    }

    private void openInfoGui(Player admin, OfflinePlayer target, String group, List<String> alts, String ip, List<String> bans) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8Инфо: §0" + target.getName());

        Stack<String> h = history.get(admin.getUniqueId());
        if (h != null && h.size() > 1) {
            inv.setItem(0, createItem(Material.ARROW, "§c<- Назад", "§7К предыдущему игроку"));
        }

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(target);
        meta.setDisplayName("§e" + target.getName());
        List<String> lore = new ArrayList<>();
        lore.add("§7IP: §f" + ip);
        lore.add("§7Статус: " + (target.isOnline() ? "§aON" : "§cOFF"));
        meta.setLore(lore);
        head.setItemMeta(meta);
        inv.setItem(4, head);

        int[] slots = {3, 2, 1, 5, 6, 7, 8};
        int i = 0;
        for (String alt : alts) {
            if (i >= slots.length) break;
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta m = (SkullMeta) item.getItemMeta();
            m.setOwningPlayer(Bukkit.getOfflinePlayer(alt));
            m.setDisplayName("§bТвинк: §f" + alt);
            item.setItemMeta(m);
            inv.setItem(slots[i++], item);
        }

        inv.setItem(19, createItem(Material.EMERALD, "§aФинансы", 
            "§7Баланс: §6" + (int)economy.getBalance(target), "§7Группа: §b" + group));
        
        int rebirths = getRebirths(target);
        inv.setItem(21, createItem(Material.NETHER_STAR, "§6Статистика", 
            "§7Перерождения: §e" + rebirths));

        ItemStack banItem = createItem(Material.BARRIER, "§cНаказания");
        ItemMeta bm = banItem.getItemMeta();
        bm.setLore(bans.isEmpty() ? List.of("§aЧисто") : bans);
        banItem.setItemMeta(bm);
        inv.setItem(23, banItem);

        inv.setItem(25, createItem(Material.PAPER, "§fЛоги", "§7Нажми для просмотра"));

        ItemStack glass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int k = 27; k < 36; k++) inv.setItem(k, glass);

        loadMobs(inv, target);

        admin.openInventory(inv);
    }

    private void loadMobs(Inventory inv, OfflinePlayer target) {
        forceSavePlayerMobs(target.getName()); // Сохраняем текущее состояние перед чтением

        File f = new File("plugins/brainrotBases/mobs.yml");
        if (!f.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection sec = cfg.getConfigurationSection("mobs." + target.getName());
        
        for (int i = 36; i < 54; i++) {
            inv.setItem(i, createItem(Material.LIME_STAINED_GLASS_PANE, "§a[+] Добавить моба"));
        }

        if (sec != null) {
            int slot = 36;
            List<String> keys = new ArrayList<>(sec.getKeys(false));
            try { keys.sort(Comparator.comparingInt(Integer::parseInt)); } catch (Exception e) {}

            for (String key : keys) {
                if (slot >= 54) break;
                String type = sec.getString(key + ".mobType");
                String mut = sec.getString(key + ".mutation", "NONE");
                boolean snow = sec.getBoolean(key + ".snowy", false);
                long lbTimer = sec.getLong(key + ".luckyBlockTimer", -1);
                boolean lbReady = sec.getBoolean(key + ".luckyBlockReady", false);
                
                Material mat = Material.GHAST_SPAWN_EGG;
                try {
                    if (type.equals("SPONGE")) mat = Material.SPONGE;
                    else if (type.equals("IRON_GOLEM")) mat = Material.IRON_BLOCK;
                    else mat = Material.valueOf(type + "_SPAWN_EGG");
                } catch (Exception e) {}

                ItemStack item = new ItemStack(mat);
                ItemMeta m = item.getItemMeta();
                m.setDisplayName("§e" + type);
                List<String> l = new ArrayList<>();
                if (!mut.equals("NONE")) l.add("§7Мутация: §d" + mut);
                if (snow) l.add("§b❄ Снежный");
                if (type.equals("SPONGE")) {
                    l.add("§7Лаки-Блок:");
                    l.add("  Готов: " + (lbReady ? "§aДа" : "§cНет"));
                    if (lbTimer > 0) l.add("  Таймер: " + (lbTimer/1000) + "с");
                }
                l.add("");
                l.add("§eЛКМ - Редактировать");
                m.setLore(l);
                
                item.setItemMeta(m);
                inv.setItem(slot++, item);
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        Player admin = (Player) e.getWhoClicked();

        if (title.startsWith("§8Инфо: ")) {
            e.setCancelled(true);
            String targetName = title.replace("§8Инфо: §0", "");
            int slot = e.getRawSlot();

            if (slot == 0 && e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.ARROW) {
                Stack<String> h = history.get(admin.getUniqueId());
                if (h != null && h.size() > 1) {
                    h.pop();
                    String prev = h.pop();
                    openProfile(admin, prev);
                } else {
                    admin.closeInventory();
                }
                return;
            }

            if ((slot >= 1 && slot <= 3) || (slot >= 5 && slot <= 8)) {
                ItemStack it = e.getCurrentItem();
                if (it != null && it.getType() == Material.PLAYER_HEAD) {
                    String alt = it.getItemMeta().getDisplayName().replace("§bТвинк: §f", "");
                    openProfile(admin, alt);
                }
            }
            if (slot == 25) {
                admin.closeInventory();
                printLogs(admin, targetName);
            }
            if (slot >= 36 && slot < 54) {
                ItemStack it = e.getCurrentItem();
                if (it != null && it.getType() != Material.LIME_STAINED_GLASS_PANE && it.getType() != Material.AIR) {
                    int mobIndex = slot - 36;
                    editingMobTarget.put(admin.getUniqueId(), targetName);
                    editingMobIndex.put(admin.getUniqueId(), mobIndex);
                    openMobEditMenu(admin, mobIndex);
                } else if (it != null && it.getType() == Material.LIME_STAINED_GLASS_PANE) {
                    editingMobTarget.put(admin.getUniqueId(), targetName);
                    openMobAddMenu(admin, 0);
                }
            }
        }
        
        else if (title.startsWith("§8Редактор моба")) {
            e.setCancelled(true);
            String targetName = editingMobTarget.get(admin.getUniqueId());
            int mobIndex = editingMobIndex.get(admin.getUniqueId());
            
            if (e.getSlot() == 11) { modifyMob(admin, targetName, mobIndex, "DELETE", null); }
            if (e.getSlot() == 13) { modifyMob(admin, targetName, mobIndex, "CLEAR_MUTATION", null); }
            if (e.getSlot() == 14) { modifyMob(admin, targetName, mobIndex, "TOGGLE_LB", null); }
            if (e.getSlot() == 15) { modifyMob(admin, targetName, mobIndex, "SET_MUTATION", "GOLD"); }
            if (e.getSlot() == 16) { modifyMob(admin, targetName, mobIndex, "SET_MUTATION", "DIAMOND"); }
            if (e.getSlot() == 17) { modifyMob(admin, targetName, mobIndex, "SET_MUTATION", "RAINBOW"); }
            if (e.getSlot() == 22) { openProfile(admin, targetName); return; }
            
            Bukkit.getScheduler().runTaskLater(this, () -> openProfile(admin, targetName), 10L);
        }

        else if (title.startsWith("§8Добавить моба")) {
            e.setCancelled(true);
            String targetName = editingMobTarget.get(admin.getUniqueId());
            int page = Integer.parseInt(title.replace("§8Добавить моба (Стр. ", "").replace(")", ""));
            
            if (e.getSlot() == 45 && page > 0) { openMobAddMenu(admin, page - 1); return; }
            if (e.getSlot() == 53 && (page + 1) * 45 < ALL_MOBS_SORTED.size()) { openMobAddMenu(admin, page + 1); return; }
            if (e.getSlot() == 49) { openProfile(admin, targetName); return; }

            if (e.getCurrentItem() != null && e.getCurrentItem().getType() != Material.AIR && e.getCurrentItem().getType() != Material.ARROW && e.getCurrentItem().getType() != Material.BARRIER) {
                String mobType = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
                addMob(admin, targetName, mobType);
                Bukkit.getScheduler().runTaskLater(this, () -> openProfile(admin, targetName), 10L);
            }
        }
    }

    private void openMobEditMenu(Player admin, int index) {
        String targetName = editingMobTarget.get(admin.getUniqueId());
        File f = new File("plugins/brainrotBases/mobs.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        String type = cfg.getString("mobs." + targetName + "." + index + ".mobType", "CHICKEN");
        
        Inventory inv = Bukkit.createInventory(null, 27, "§8Редактор моба (" + index + ")");
        inv.setItem(11, createItem(Material.REDSTONE_BLOCK, "§cУдалить моба"));
        inv.setItem(13, createItem(Material.MILK_BUCKET, "§fСнять мутации"));
        inv.setItem(15, createItem(Material.GOLD_INGOT, "§6Сделать Золотым"));
        inv.setItem(16, createItem(Material.DIAMOND, "§bСделать Алмазным"));
        inv.setItem(17, createItem(Material.NETHER_STAR, "§dСделать Радужным"));
        
        if (type.equals("SPONGE")) {
            boolean isReady = cfg.getBoolean("mobs." + targetName + "." + index + ".luckyBlockReady", false);
            inv.setItem(14, createItem(Material.CLOCK, "§eСостояние Лаки-Блока", 
                "§7Сейчас: " + (isReady ? "§aГОТОВ" : "§cТАЙМЕР"),
                "§eНажмите, чтобы переключить"
            ));
        }
        
        inv.setItem(22, createItem(Material.ARROW, "§cНазад"));
        admin.openInventory(inv);
    }

    private void openMobAddMenu(Player admin, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8Добавить моба (Стр. " + page + ")");
        int start = page * 45;
        int end = Math.min(start + 45, ALL_MOBS_SORTED.size());
        
        for (int i = start; i < end; i++) {
            String t = ALL_MOBS_SORTED.get(i);
            Material mat = Material.GHAST_SPAWN_EGG;
            try {
                if (t.equals("SPONGE")) mat = Material.SPONGE;
                else if (t.equals("IRON_GOLEM")) mat = Material.IRON_BLOCK;
                else if (t.equals("SNOW_GOLEM")) mat = Material.CARVED_PUMPKIN;
                else if (t.equals("MOOSHROOM")) mat = Material.RED_MUSHROOM_BLOCK;
                else mat = Material.valueOf(t + "_SPAWN_EGG");
            } catch (Exception e) {}
            inv.addItem(createItem(mat, "§e" + t));
        }

        if (page > 0) inv.setItem(45, createItem(Material.ARROW, "§e<- Назад"));
        if (end < ALL_MOBS_SORTED.size()) inv.setItem(53, createItem(Material.ARROW, "§eВперед ->"));
        inv.setItem(49, createItem(Material.BARRIER, "§cОтмена"));
        admin.openInventory(inv);
    }

    // ========== API BRAINROTBASES ==========

    private void modifyMob(Player admin, String targetName, int index, String action, String arg) {
        Plugin bb = Bukkit.getPluginManager().getPlugin("BrainrotBases");
        if (bb == null) return;

        if (action.equals("TOGGLE_LB")) {
            forceSavePlayerMobs(targetName);
            File f = new File("plugins/brainrotBases/mobs.yml");
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            String path = "mobs." + targetName + "." + index;
            if (cfg.contains(path)) {
                boolean current = cfg.getBoolean(path + ".luckyBlockReady");
                if (current) {
                    cfg.set(path + ".luckyBlockReady", false);
                    cfg.set(path + ".luckyBlockTimer", 900000L);
                    admin.sendMessage("§aЛаки-Блок поставлен на таймер.");
                } else {
                    cfg.set(path + ".luckyBlockReady", true);
                    cfg.set(path + ".luckyBlockTimer", 0L);
                    admin.sendMessage("§aЛаки-Блок теперь готов к открытию.");
                }
                try { saveConfigAsync(cfg, f); } catch (IOException e) {}
                forceReloadFromDisk(targetName);
            }
            return;
        }

        try {
            if (action.equals("DELETE")) {
                Method m = bb.getClass().getMethod("adminDeleteMob", String.class, int.class);
                m.invoke(bb, targetName, index);
                admin.sendMessage("§aМоб удален.");
            }
            else if (action.equals("CLEAR_MUTATION")) {
                Method m = bb.getClass().getMethod("adminSetMutation", String.class, int.class, String.class, boolean.class);
                m.invoke(bb, targetName, index, "NONE", false);
                admin.sendMessage("§aМутации сняты.");
            }
            else if (action.equals("SET_MUTATION")) {
                Method m = bb.getClass().getMethod("adminSetMutation", String.class, int.class, String.class, boolean.class);
                m.invoke(bb, targetName, index, arg, false);
                admin.sendMessage("§aМутация " + arg + " установлена.");
            }
            
            Player target = Bukkit.getPlayer(targetName);
            if (target != null) target.sendMessage("§eВаши мобы были изменены администратором.");

        } catch (Exception e) {
            admin.sendMessage("§cОшибка API: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void addMob(Player admin, String targetName, String type) {
        Plugin bb = Bukkit.getPluginManager().getPlugin("BrainrotBases");
        if (bb == null) return;

        try {
            Method m = bb.getClass().getMethod("adminAddMob", String.class, String.class);
            boolean success = (boolean) m.invoke(bb, targetName, type);
            
            if (success) {
                admin.sendMessage("§aМоб " + type + " успешно добавлен!");
                Player target = Bukkit.getPlayer(targetName);
                if (target != null) target.sendMessage("§aАдминистратор выдал вам моба: " + type);
            } else {
                admin.sendMessage("§cНе удалось добавить моба (нет базы или мест).");
            }
        } catch (Exception e) {
            admin.sendMessage("§cОшибка API: " + e.getMessage());
        }
    }

    // Принудительная перезагрузка мобов игрока из файла
    private void forceReloadFromDisk(String targetName) {
        Plugin bb = Bukkit.getPluginManager().getPlugin("BrainrotBases");
        if (bb == null) return;
        
        try {
            // 1. Сначала удаляем ВСЁ
            Player target = Bukkit.getPlayer(targetName);
            if (target != null) {
                // ХАК: Удаление старых AJ моделей
                try {
                    String base = getPlayerBaseFromAPI(targetName);
                    if (base != null) {
                        Method getMobPoints = bb.getClass().getMethod("getMobPoints", String.class);
                        List<String> points = (List<String>) getMobPoints.invoke(bb, base);
                        if (points != null) {
                            for (String point : points) {
                                String[] s = point.split("_");
                                if (s.length == 4) {
                                    World w = Bukkit.getWorld(s[0]);
                                    if (w != null) {
                                        Location loc = new Location(w, Double.parseDouble(s[1])+0.5, Double.parseDouble(s[2])+0.5, Double.parseDouble(s[3])+0.5);
                                        for (Entity e : w.getNearbyEntities(loc, 1, 1, 1)) {
                                            if (e.getScoreboardTags().contains("aj.luckyblock.root")) {
                                                String uniq = null;
                                                for (String tag : e.getScoreboardTags()) if (tag.startsWith("base_lb_")) uniq = tag;
                                                if (uniq != null) {
                                                    String dim = w.getKey().toString();
                                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "execute in " + dim + " as @e[tag=" + uniq + ",limit=1] run function animated_java:luckyblock/remove");
                                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "execute in " + dim + " run kill @e[tag=" + uniq + "]");
                                                } else e.remove();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}

                // Штатное удаление (BrainrotBases)
                Method removeMethod = bb.getClass().getDeclaredMethod("removeAllPlayerMobs", Player.class);
                removeMethod.setAccessible(true);
                removeMethod.invoke(bb, target);
            }

            // 2. Очищаем кэш памяти
            java.lang.reflect.Field savedMobsField = bb.getClass().getDeclaredField("savedPlayerMobs");
            savedMobsField.setAccessible(true);
            Map<String, List<Object>> savedMobsMap = (Map<String, List<Object>>) savedMobsField.get(bb);
            savedMobsMap.remove(targetName);

            // 3. Заставляем перечитать конфиг
            Method loadMethod = bb.getClass().getDeclaredMethod("loadMobsFromConfig");
            loadMethod.setAccessible(true);
            loadMethod.invoke(bb);

            // 4. Восстанавливаем мобов С ЗАДЕРЖКОЙ
            Bukkit.getScheduler().runTaskLater(this, () -> {
                try {
                    Method restoreMethod = bb.getClass().getDeclaredMethod("restorePlayerMobs", String.class);
                    restoreMethod.setAccessible(true);
                    restoreMethod.invoke(bb, targetName);
                } catch (Exception e) {
                    getLogger().warning("Ошибка при отложенном восстановлении: " + e.getMessage());
                }
            }, 5L); // 5 тиков задержки (0.25 сек)
            
        } catch (Exception e) {
            getLogger().warning("Ошибка ReloadFromDisk: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void forceSavePlayerMobs(String targetName) {
        Plugin bb = Bukkit.getPluginManager().getPlugin("BrainrotBases");
        if (bb == null) return;
        Player p = Bukkit.getPlayer(targetName);
        if (p != null) {
            try {
                Method saveMethod = bb.getClass().getDeclaredMethod("savePlayerMobsInstantly", String.class);
                saveMethod.setAccessible(true);
                saveMethod.invoke(bb, targetName);
            } catch (Exception e) {}
        }
    }

    private String getPlayerBaseFromAPI(String playerName) {
        Plugin bb = Bukkit.getPluginManager().getPlugin("BrainrotBases");
        if (bb == null) return null;
        try {
            Method m = bb.getClass().getMethod("getBases");
            Map<String, String> bases = (Map<String, String>) m.invoke(bb);
            for (Map.Entry<String, String> e : bases.entrySet()) {
                if (e.getValue().equals(playerName)) return e.getKey();
            }
        } catch (Exception e) {}
        return null;
    }

    private String getBaseFromConfig(String playerName) {
        File f = new File("plugins/brainrotBases/config.yml");
        if (!f.exists()) return null;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection bases = cfg.getConfigurationSection("bases");
        if (bases == null) return null;
        for (String key : bases.getKeys(false)) {
            if (bases.getString(key + ".owner", "").equals(playerName)) return key;
        }
        return null;
    }

    private String getFreePointFromAPI(String base) {
        Plugin bb = Bukkit.getPluginManager().getPlugin("BrainrotBases");
        if (bb == null) return null;
        try {
            Method m = bb.getClass().getMethod("findFreeMobPoint", String.class);
            return (String) m.invoke(bb, base);
        } catch (Exception e) {}
        return null;
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(name);
        m.setLore(Arrays.asList(lore));
        item.setItemMeta(m);
        return item;
    }

    private void printLogs(Player admin, String target) {
        File f = new File(logsFolder, target.toLowerCase() + ".log");
        if (f.exists()) {
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String l;
                List<String> lines = new ArrayList<>();
                while ((l = r.readLine()) != null) lines.add(l);
                int start = Math.max(0, lines.size() - 20);
                for (int i = start; i < lines.size(); i++) admin.sendMessage("§7" + lines.get(i));
            } catch (Exception e) {}
        } else admin.sendMessage("§cЛогов нет.");
    }

    private int getRebirths(OfflinePlayer p) {
        File f = new File("plugins/brainrotBases/rebirths.yml");
        if (!f.exists()) return 0;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        String uuid = p.getUniqueId().toString();
        int r = cfg.getInt("rebirths." + uuid + ".count", -1);
        if (r == -1) {
            for (String k : cfg.getConfigurationSection("rebirths").getKeys(false)) {
                if (cfg.getString("rebirths." + k + ".name", "").equalsIgnoreCase(p.getName())) 
                    return cfg.getInt("rebirths." + k + ".count", 0);
            }
        }
        return Math.max(0, r);
    }

    private List<String> getLiteBansHistory(UUID uuid) {
        List<String> h = new ArrayList<>();
        try {
            Class<?> db = Class.forName("litebans.api.Database");
            Object connObj = db.getMethod("get").invoke(null);
            if (connObj instanceof Connection conn) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT reason, banned_by_name FROM litebans_bans WHERE uuid=? AND active=1")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        h.add("§cАКТИВНЫЙ БАН:");
                        h.add("§7Кем: §f" + rs.getString("banned_by_name"));
                        h.add("§7Причина: §f" + rs.getString("reason"));
                    } else h.add("§aАктивных банов нет");
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT reason, banned_by_name FROM litebans_mutes WHERE uuid=? AND active=1")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        h.add("§6АКТИВНЫЙ МУТ:");
                        h.add("§7Кем: §f" + rs.getString("banned_by_name"));
                        h.add("§7Причина: §f" + rs.getString("reason"));
                    } else h.add("§aАктивных мутов нет");
                }
                int totalBans = 0, totalMutes = 0;
                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM litebans_bans WHERE uuid=?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) totalBans = rs.getInt(1);
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM litebans_mutes WHERE uuid=?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) totalMutes = rs.getInt(1);
                }
                h.add("");
                h.add("§eИстория нарушений:");
                h.add("§7Всего банов: §c" + totalBans);
                h.add("§7Всего мутов: §6" + totalMutes);
            }
        } catch (Exception e) { h.add("§cОшибка LiteBans"); e.printStackTrace(); }
        return h;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        String ip = e.getPlayer().getAddress().getAddress().getHostAddress();
        playersConfig.set("players." + e.getPlayer().getName().toLowerCase(), ip);
        List<String> list = playersConfig.getStringList("ips." + ip.replace(".", "_"));
        if (!list.contains(e.getPlayer().getName())) {
            list.add(e.getPlayer().getName());
            playersConfig.set("ips." + ip.replace(".", "_"), list);
        }
        try { saveConfigAsync(playersConfig, playersFile); } catch (IOException ex) {}
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        logToFile(e.getPlayer().getName(), e.getMessage());
    }

    private void logToFile(String name, String msg) {
        File f = new File(logsFolder, name.toLowerCase() + ".log");
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM HH:mm");
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8))) {
            w.write("[" + sdf.format(new Date()) + "] " + msg);
            w.newLine();
        } catch (IOException e) {}
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    private boolean setupChat() {
        RegisteredServiceProvider<Chat> rsp = getServer().getServicesManager().getRegistration(Chat.class);
        if (rsp != null) chat = rsp.getProvider();
        return chat != null;
    }
}