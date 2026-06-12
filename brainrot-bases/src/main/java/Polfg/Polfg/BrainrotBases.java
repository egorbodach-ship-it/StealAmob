package Polfg.Polfg;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import java.util.function.Consumer;
import de.oliver.fancyholograms.api.HologramManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.Action;
import java.util.stream.Collectors;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ItemFrame;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.ElderGuardian;
import org.bukkit.command.CommandSender;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.joml.Vector3f;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public class BrainrotBases extends JavaPlugin implements Listener {
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

    private final Map<String, String> bases = new HashMap<>();
    private final Map<String, Hologram> holograms = new HashMap<>();
    private final Map<String, List<String>> baseSubmitPoints = new HashMap<>();
    private final Map<String, List<String>> baseMobSpawnPoints = new HashMap<>();
    private final Map<String, Set<String>> occupiedMobPoints = new HashMap<>();
    private final Map<Entity, String> entityToPointMap = new ConcurrentHashMap<>();
    private final Map<Entity, Mutation> baseMobMutations = new ConcurrentHashMap<>();
    private final Map<Entity, Boolean> baseMobSnowy = new ConcurrentHashMap<>();
    private final Map<Entity, BukkitRunnable> baseRainbowAnimations = new ConcurrentHashMap<>();
    private final Map<String, List<String>> baseCollectorPoints = new HashMap<>();
    private final Map<String, Double> collectorMoney = new ConcurrentHashMap<>();
    private final Map<Player, Entity> sellMenuEntity = new ConcurrentHashMap<>();
    private final Map<String, Long> collectorLastUpdate = new ConcurrentHashMap<>();
    private final Map<String, Hologram> collectorHolograms = new ConcurrentHashMap<>();
    private final Map<String, String> collectorToEntityMap = new ConcurrentHashMap<>();
    private final Map<Entity, Long> luckyBlockOpenTime = new ConcurrentHashMap<>();
    private final Map<Entity, Boolean> luckyBlockReady = new ConcurrentHashMap<>();
    private static long LUCKY_BLOCK_TIMER = 15 * 60 * 1000L;
    private final Map<String, String> entityToCollectorMap = new ConcurrentHashMap<>();
    private static final MobType[] LUCKY_BLOCK_LOOT = {
    	    MobType.FROG,
    	    MobType.GLOW_SQUID,
    	    MobType.PIGLIN,
    	    MobType.SNIFFER,
    	    MobType.WARDEN
    	};
    private static final int[] LUCKY_BLOCK_WEIGHTS = {
    	    37,
    	    30,
    	    20,
    	    10,
    	    3
    	};
    private MobType rollLuckyBlockLoot() {
        int totalWeight = 0;
        for (int w : LUCKY_BLOCK_WEIGHTS) {
            totalWeight += w;
        }
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < LUCKY_BLOCK_LOOT.length; i++) {
            cumulative += LUCKY_BLOCK_WEIGHTS[i];
            if (roll < cumulative) {
                return LUCKY_BLOCK_LOOT[i];
            }
        }
        return LUCKY_BLOCK_LOOT[0];
    }
    private final Map<String, Boolean> baseLocked = new ConcurrentHashMap<>();
    private final Map<String, Integer> baseLockTime = new ConcurrentHashMap<>();
    private final Map<String, Hologram> lockHolograms = new HashMap<>();
    private final Map<String, String> baseLockPoints = new HashMap<>();
    private final Map<UUID, Double> playerEarnMultipliers = new ConcurrentHashMap<>();
    private final Set<String> animatingPoints = ConcurrentHashMap.newKeySet();
    private final Map<String, String> baseParticlePoints = new HashMap<>();
    private final Map<String, Stage2Config> stage2Configs = new HashMap<>();
    private final Map<String, Boolean> stage2Active = new ConcurrentHashMap<>();
    private static class Stage2Config {
        boolean enabled;
        String world;
        String schematic;
        String emptySchematic;
        String pos1;
        String pos2;
        String mobPoint;
        String collectorPoint;
        String laserPoint;
        int requiredRebirths = 2;
    }
 private final Map<Entity, Entity> luckyBlockHitboxMap = new ConcurrentHashMap<>();
 private final Map<Entity, String> luckyBlockTags = new ConcurrentHashMap<>();
 private final Map<Entity, BukkitRunnable> luckyBlockAnimations = new ConcurrentHashMap<>();
 private final Map<Entity, Entity> rotWalkerHitboxMap = new ConcurrentHashMap<>();
 private final Map<Entity, String> rotWalkerTags = new ConcurrentHashMap<>();
    private final Map<UUID, Long> resetCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> pendingResets = new HashSet<>();
    private final Map<String, String> baseRegionNames = new ConcurrentHashMap<>();
    private final Map<String, List<String>> baseKickPoints = new HashMap<>();
    private final Map<Player, Long> lastLockMessage = new ConcurrentHashMap<>();
    private final Map<Player, Long> lastCollectMessage = new ConcurrentHashMap<>();
    private final Map<Player, StealingData> stealingPlayers = new ConcurrentHashMap<>();
    private final Map<Player, Long> lastStealMessage = new ConcurrentHashMap<>();
    private final Map<Player, BukkitRunnable> stealProgressTasks = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> collectorCooldowns = new ConcurrentHashMap<>();
    private HologramManager hologramManager;
    private Economy economy;
    private double hologramHeight;
    private double lockHologramHeight = 1.2;
    private File mobsFile;
    private FileConfiguration mobsConfig;
    private File rebirthsFile;
    private FileConfiguration rebirthsConfig;
    private File friendsFile;
    private FileConfiguration friendsConfig;
    private final Map<String, Set<String>> playerFriends = new ConcurrentHashMap<>();
    private static final String BASE_MOB_TAG = "BASE_MOB";
    private static final String MOB_TAG_PREFIX = "MOB_";
    private static final String RESET_ITEM_TAG = "BRAINROT_RESET_ITEM";
    private static final int RESET_ITEM_SLOT = 8;
    private static int MIN_COLLECT_AMOUNT = 2;
    private static int STEAL_TIME = 5;
    private static final double STEAL_HOLOGRAM_HEIGHT = 2.5;
    private static int LOCK_DURATION = 60;
    private static int REBIRTH_LOCK_DURATION_1 = 70;
    private static int REBIRTH_LOCK_DURATION_2 = 80;
    private static int REBIRTH_LOCK_DURATION_3 = 90;
    private static final int RESET_COOLDOWN_SECONDS = 10;
    private static int REBIRTH_LOCK_DURATION_4 = 100;
    private static long COLLECT_COOLDOWN = 2000L;
    private static final long GENERAL_MESSAGE_COOLDOWN = 2000L;
    private static double INITIAL_BALANCE = 500.0;
    private static double[] REBIRTH_EARN_MULTIPLIERS = {1.0, 1.2, 1.4, 1.6, 1.8, 2.0, 3.0, 4.0, 5.0};
    private static double REBIRTH_STARTING_MONEY_1 = 5000.0;
    private static int REBIRTH_LOCK_DURATION_7 = 130;
    private static int REBIRTH_LOCK_DURATION_8 = 140;
    private static double REBIRTH_STARTING_MONEY_7 = 500000.0;
    private static double REBIRTH_STARTING_MONEY_8 = 1000000.0;
    private static double REBIRTH_STARTING_MONEY_2 = 5000.0;
    private static double REBIRTH_STARTING_MONEY_3 = 25000.0;
    private static int REBIRTH_LOCK_DURATION_5 = 110;
    private static int REBIRTH_LOCK_DURATION_6 = 120;
    private static double REBIRTH_STARTING_MONEY_5 = 100000.0;
    private static double REBIRTH_STARTING_MONEY_6 = 250000.0;
    private static double REBIRTH_STARTING_MONEY_4 = 50000.0;
    private static final String TAG_LUCKY_BLOCK = "LUCKY_BLOCK";
    private static final String TAG_LUCKY_BLOCK_HITBOX = "LUCKY_BLOCK_HITBOX";
    private static final String TAG_CARRYING_LUCKY_BLOCK = "CARRYING_LUCKY_BLOCK";
    private static final String TAG_LUCKY_BLOCK_ROOT = "aj.luckyblock.root";
    private static final String TAG_ROT_WALKER_ROOT = "aj.rotwalker.root";
    private static final String TAG_ROT_WALKER_ENTITY = "aj.rotwalker.entity";
    private static final String TAG_ROT_WALKER_BASE = "ROT_WALKER_BASE";
    private boolean moneyTimerRunning = false;
    private final Object moneyLock = new Object();
    private final Set<String> playersWithInitialBalance = ConcurrentHashMap.newKeySet();
    private final Map<String, List<SavedMobData>> savedPlayerMobs = new ConcurrentHashMap<>();
    private final Map<Entity, Long> mobSpawnTime = new ConcurrentHashMap<>();
    private static final long MOB_MIN_LIFETIME = 30000L;
    private final Random random = new Random();
    private static class StealingData {
        Player player;
        Entity mob;
        String originalBase;
        Location originalLocation;
        BukkitRunnable stealTimer;
        int timeLeft;
        boolean isCarrying;
        String collectorId;
        Mutation mutation;
        boolean snowy;
        StealingData(Player player, Entity mob, String originalBase, Location originalLocation) {
            this.player = player;
            this.mob = mob;
            this.originalBase = originalBase;
            this.originalLocation = originalLocation.clone();
            this.timeLeft = STEAL_TIME;
            this.isCarrying = false;
            this.collectorId = null;
            this.mutation = Mutation.NONE;
            this.snowy = false;
        }
    }
    private boolean debug = false;

    /** Пишет отладочный лог только при settings.debug=true. */
    private void debugLog(String msg) {
        if (debug) getLogger().info(msg);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        debug = getConfig().getBoolean("settings.debug", false);
        initRebirthsFile();
        migrateRebirthsFromConfig();
        mobsFile = new File(getDataFolder(), "mobs.yml");
        if (!mobsFile.exists()) {
            try {
                mobsFile.createNewFile();
            } catch (IOException e) {
                getLogger().warning("Не удалось создать файл mobs.yml: " + e.getMessage());
            }
        }
        mobsConfig = YamlConfiguration.loadConfiguration(mobsFile);
        hologramManager = FancyHologramsPlugin.get().getHologramManager();
        ensureWorldsLoaded();
        registerRebirthCommand();
        registerCleanCommand();
        registerDebugCommand();
        loadFriends();
        registerReloadHologramsCommand();
        registerDebugCommand();
        registerClearOccupiedCommand();
        registerFriendCommand();
        registerFixCommand();
        registerSaveCommand();
        registerResetRebirthCommand();
        startLuckyBlockTimerUpdater();
        hologramHeight = getConfig().getDouble("settings.hologram_height", 1.8);
        lockHologramHeight = getConfig().getDouble("settings.lock_hologram_height", 1.2);
        getConfig().addDefault("settings.mob_hologram_base_height", 2.0);
        getConfig().addDefault("settings.collector_hologram_height", 0.3);
        getConfig().addDefault("settings.debug", false);
        getConfig().options().copyDefaults(true);
        loadEconomyConfig();
        saveConfig();
        setupEconomy();
        getLogger().info("Очистка всех мобов при запуске...");
        for (Entity entity : new ArrayList<>(entityToPointMap.keySet())) {
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        entityToPointMap.clear();
        mobSpawnTime.clear();
        for (Hologram holo : new ArrayList<>(collectorHolograms.values())) {
            hologramManager.removeHologram(holo);
        }
        collectorHolograms.clear();
        collectorMoney.clear();
        collectorLastUpdate.clear();
        collectorToEntityMap.clear();
        entityToCollectorMap.clear();
        collectorCooldowns.clear();
        for (Set<String> occupied : occupiedMobPoints.values()) {
            occupied.clear();
        }
        getLogger().info("Очистка завершена. Загружаем конфигурацию...");
        loadBases();
        cleanUpSavedMobs();
        loadMobsFromConfig();
            Bukkit.getScheduler().runTaskLater(this, () -> {
                cleanupAllDuplicateHolograms();
            }, 60L);
        createBaseHolograms();
        createLockHolograms();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("§a✅ BrainrotBases ЗАПУЩЕН!");
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BrainrotPlaceholder(this).register();
            getLogger().info("§a✅ PlaceholderAPI найден! Плейсхолдеры зарегистрированы.");
        } else {
            getLogger().warning("§cPlaceholderAPI не найден! Плейсхолдеры не будут работать.");
        }
        synchronized (moneyLock) {
            long currentTime = System.currentTimeMillis();
            collectorMoney.clear();
            collectorLastUpdate.clear();
            collectorToEntityMap.clear();
            entityToCollectorMap.clear();
            collectorCooldowns.clear();
            for (String base : bases.keySet()) {
                List<String> collectorPoints = baseCollectorPoints.get(base);
                if (collectorPoints != null) {
                    for (String collectorPoint : collectorPoints) {
                        String collectorId = base + "_" + collectorPoint;
                        collectorLastUpdate.put(collectorId, currentTime);
                        collectorMoney.put(collectorId, 0.0);
                    }
                }
            }
            getLogger().info("§aИнициализировано " + collectorMoney.size() + " счетчиков денег");
        }
        startMobCheckTimer();
        startAggressiveMobCheckTimer();
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                startMoneyAccumulationTimer();
                startLockTimer();
                startParticleTimer();
                startUnifiedKickTimer();
                startStealingCheckTimer();
                startLuckyBlockTimerUpdater();
                startMutationParticleTimer();
                Bukkit.getScheduler().runTaskLater(BrainrotBases.this, () -> {
                    cleanupOrphanedLuckyBlocks();
                }, 60L);
            }
        }, 40L);
    }
    private void openLuckyBlockMenu(Player player, Entity luckyBlock, String base) {
        Boolean ready = luckyBlockReady.get(luckyBlock);
        if (ready == null || !ready) {
            openSellMenu(player, luckyBlock, base);
            return;
        }
        Inventory menu = Bukkit.createInventory(null, 27, color("§d§lЛаки-Блок"));
        ItemStack glass = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 27; i++) {
            menu.setItem(i, glass);
        }
        ItemStack info = new ItemStack(Material.SPONGE);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(color("§e§l⭐ §d§lЛаки-Блок §e§l⭐"));
        infoMeta.setLore(Arrays.asList(
            color("§7Лаки-Блок готов к открытию!"),
            color(""),
            color("§7Возможный лут:"),
            color("§a  Лягушка §7- §f37%"),
            color("§a  Светящийся кальмар §7- §f30%"),
            color("§a  Пиглин §7- §f20%"),
            color("§a  Сниффер §7- §f10%"),
            color("§c  Варден §7- §f3%"),
            color(""),
            color("§eВыберите действие:")
        ));
        info.setItemMeta(infoMeta);
        menu.setItem(4, info);
        ItemStack openBtn = new ItemStack(Material.ENDER_CHEST);
        ItemMeta openMeta = openBtn.getItemMeta();
        openMeta.setDisplayName(color("§a§lОТКРЫТЬ"));
        openMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
        openMeta.setLore(Arrays.asList(
            color("§7Откройте Лаки-Блок и"),
            color("§7получите случайного моба!"),
            color(""),
            color("§a➤ Нажмите для открытия")
        ));
        openBtn.setItemMeta(openMeta);
        menu.setItem(11, openBtn);
        ItemStack sellBtn = new ItemStack(Material.GOLD_INGOT);
        ItemMeta sellMeta = sellBtn.getItemMeta();
        sellMeta.setDisplayName(color("§6§lПРОДАТЬ"));
        sellMeta.setLore(Arrays.asList(
            color("§7Продайте Лаки-Блок за"),
            color("§6$" + formatNumber(MobType.SPONGE.sellPrice)),
            color(""),
            color("§6➤ Нажмите для продажи")
        ));
        sellBtn.setItemMeta(sellMeta);
        menu.setItem(15, sellBtn);
        player.openInventory(menu);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1.2f);
        sellMenuEntity.put(player, luckyBlock);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Bukkit.getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onInventoryClick(InventoryClickEvent event) {
                    if (!event.getInventory().equals(menu) || !event.getWhoClicked().equals(player)) return;
                    event.setCancelled(true);
                    if (event.getSlot() == 11) {
                        player.closeInventory();
                        startLuckyBlockOpenAnimation(player, luckyBlock, base);
                    } else if (event.getSlot() == 15) {
                        player.closeInventory();
                        sellEntity(player, luckyBlock);
                    }
                }
                @EventHandler
                public void onInventoryClose(InventoryCloseEvent event) {
                    if (event.getInventory().equals(menu) && event.getPlayer().equals(player)) {
                        sellMenuEntity.remove(player);
                        HandlerList.unregisterAll(this);
                    }
                }
            }, BrainrotBases.this);
        }, 1L);
    }
    private double getMobMutationMultiplier(Entity mob) {
        if (mob == null) return 1.0;
        Mutation base = baseMobMutations.getOrDefault(mob, Mutation.NONE);
        boolean snowy = baseMobSnowy.getOrDefault(mob, false);
        double mult = base.incomeMultiplier;
        if (snowy) mult *= Mutation.SNOWY.incomeMultiplier;
        return mult;
    }
    private String getMutationDisplayLine(Mutation mutation, boolean snowy) {
        StringBuilder sb = new StringBuilder();
        if (snowy) {
            sb.append(Mutation.SNOWY.format).append(Mutation.SNOWY.displayName);
            if (mutation != null && mutation != Mutation.NONE) {
                sb.append(" §7+ ");
            }
        }
        if (mutation != null && mutation != Mutation.NONE) {
            if (mutation == Mutation.RAINBOW) {
                sb.append("§fРадужный");
            } else {
                sb.append(mutation.format).append(mutation.displayName);
            }
        }
        return sb.toString();
    }
    private void startLuckyBlockOpenAnimation(Player player, Entity luckyBlock, String base) {
        if (luckyBlock == null || luckyBlock.isDead()) {
            player.sendMessage(color("§c❌ Лаки-Блок не найден!"));
            return;
        }
        String mobPoint = entityToPointMap.get(luckyBlock);
        if (mobPoint == null) {
            player.sendMessage(color("§c❌ Ошибка!"));
            return;
        }
        Location spawnLoc = getLocationFromPoint(mobPoint);
        if (spawnLoc == null) {
            player.sendMessage(color("§c❌ Ошибка локации!"));
            return;
        }
        final Mutation lbMutation = baseMobMutations.getOrDefault(luckyBlock, Mutation.NONE);
        final boolean lbSnowy = baseMobSnowy.getOrDefault(luckyBlock, false);
        final float fixedYaw = luckyBlock.getLocation().getYaw();
        animatingPoints.add(mobPoint);
        String uniqTag = luckyBlockTags.remove(luckyBlock);
        Entity hitbox = luckyBlockHitboxMap.remove(luckyBlock);
        if (hitbox != null && hitbox.isValid()) hitbox.remove();
        BukkitRunnable animTask = luckyBlockAnimations.remove(luckyBlock);
        if (animTask != null) try { animTask.cancel(); } catch (Exception ignored) {}
        removeMobHologram(luckyBlock);
        luckyBlockOpenTime.remove(luckyBlock);
        luckyBlockReady.remove(luckyBlock);
        baseMobMutations.remove(luckyBlock);
        baseMobSnowy.remove(luckyBlock);
        String collectorId = entityToCollectorMap.get(mobPoint);
        String savedCollectorPoint = null;
        if (collectorId != null) {
            String[] parts = collectorId.split("_", 2);
            if (parts.length == 2) savedCollectorPoint = parts[1];
            removeCollectorHologram(collectorId);
            collectorToEntityMap.remove(collectorId);
            entityToCollectorMap.remove(mobPoint);
            synchronized (moneyLock) {
                collectorMoney.remove(collectorId);
                collectorLastUpdate.remove(collectorId);
            }
        }
        entityToPointMap.remove(luckyBlock);
        mobSpawnTime.remove(luckyBlock);
        Set<String> occupied = occupiedMobPoints.get(base);
        if (occupied != null) occupied.remove(mobPoint);
        String playerName = player.getName();
        if (savedPlayerMobs.containsKey(playerName)) {
            savedPlayerMobs.get(playerName).removeIf(sm -> sm.base.equals(base) && sm.mobPoint.equals(mobPoint));
        }
        killLuckyBlockByTag(luckyBlock, uniqTag);
        if (!luckyBlock.isDead()) luckyBlock.remove();
        World world = spawnLoc.getWorld();
        for (Entity nearby : world.getNearbyEntities(spawnLoc, 3, 3, 3)) {
            if (nearby.getScoreboardTags().contains("LUCKY_BLOCK_HITBOX")) {
                boolean hasOwner = false;
                for (Entity owner : luckyBlockHitboxMap.keySet()) {
                    if (luckyBlockHitboxMap.get(owner) != null && luckyBlockHitboxMap.get(owner).equals(nearby)) {
                        hasOwner = true;
                        break;
                    }
                }
                if (!hasOwner) nearby.remove();
            }
        }
        MobType wonMob = rollLuckyBlockLoot();
        world.playSound(spawnLoc, Sound.BLOCK_ENDER_CHEST_OPEN, 1.5f, 0.5f);
        world.spawnParticle(Particle.EXPLOSION, spawnLoc, 3, 0.3, 0.3, 0.3, 0.1);
        player.sendTitle(color("§d§l⭐ ОТКРЫТИЕ ⭐"), color("§7Лаки-Блок определяет моба..."), 10, 80, 10);
        final Location animLoc = spawnLoc.clone();
        animLoc.setYaw(fixedYaw);
        final String finalBase = base;
        final String finalMobPoint = mobPoint;
        final String finalCollectorPoint = savedCollectorPoint;
        final String animHoloName = "lb_anim_" + player.getUniqueId().toString().substring(0, 8);
        TextHologramData initialHoloData = new TextHologramData(animHoloName, animLoc.clone().add(0, 1.0, 0));
        initialHoloData.setText(List.of("§eЗагрузка..."));
        initialHoloData.setScale(new Vector3f(0.9f, 0.9f, 0.9f));
        initialHoloData.setBackground(Hologram.TRANSPARENT);
        initialHoloData.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        Hologram animHolo = hologramManager.create(initialHoloData);
        hologramManager.addHologram(animHolo);
        new BukkitRunnable() {
            int tick = 0;
            int totalTicks = 100;
            int pauseAfter = 40;
            Entity currentDisplayMob = null;
            int lastShowTick = -999;
            @Override
            public void run() {
                tick++;
                int interval;
                if (tick < 20) interval = 2;
                else if (tick < 40) interval = 3;
                else if (tick < 60) interval = 5;
                else if (tick < 75) interval = 8;
                else if (tick < 90) interval = 12;
                else interval = 20;
                boolean shouldShow = (tick - lastShowTick) >= interval || tick >= totalTicks;
                if (shouldShow && tick <= totalTicks) {
                    lastShowTick = tick;
                    if (currentDisplayMob != null && !currentDisplayMob.isDead()) {
                        currentDisplayMob.remove();
                    }
                    MobType showMob;
                    if (tick >= totalTicks) showMob = wonMob;
                    else showMob = LUCKY_BLOCK_LOOT[random.nextInt(LUCKY_BLOCK_LOOT.length)];
                    Location mobSpawnLoc = animLoc.clone();
                    mobSpawnLoc.setYaw(fixedYaw);
                    mobSpawnLoc.setPitch(0.0f);
                    try {
                        currentDisplayMob = world.spawnEntity(mobSpawnLoc, showMob.type);
                    } catch (Exception e) { return; }
                    if (currentDisplayMob instanceof LivingEntity living) {
                        living.setAI(false);
                        living.setInvulnerable(true);
                        living.setGravity(false);
                        living.setCollidable(false);
                        living.setSilent(true);
                        living.setRemoveWhenFarAway(false);
                        if (living instanceof Mob mobEntity) {
                            mobEntity.setTarget(null);
                            mobEntity.setAware(false);
                        }
                        if (living instanceof Ageable ageable) ageable.setAgeLock(true);
                        if (showMob == MobType.FROG && currentDisplayMob instanceof Frog frog) frog.setVariant(Frog.Variant.WARM);
                        if (showMob == MobType.PIGLIN && currentDisplayMob instanceof Piglin piglin) {
                            piglin.setImmuneToZombification(true);
                            piglin.setIsAbleToHunt(false);
                        }
                        if (showMob == MobType.WARDEN && currentDisplayMob instanceof Warden warden) {
                            warden.setAware(false);
                            warden.clearAnger(currentDisplayMob);
                        }
                    }
                    currentDisplayMob.setPersistent(false);
                    currentDisplayMob.setCustomNameVisible(false);
                    currentDisplayMob.setSilent(true);
                    currentDisplayMob.setGravity(false);
                    currentDisplayMob.setInvulnerable(true);
                    currentDisplayMob.teleport(mobSpawnLoc);
                    double holoHeight = 0.8;
                    try { holoHeight = currentDisplayMob.getBoundingBox().getHeight() + 0.3; } catch (Exception ignored) {}
                    if (holoHeight < 0.8) holoHeight = 0.8;
                    Location holoLoc = animLoc.clone().add(0, holoHeight, 0);
                    if (animHolo.getData() instanceof TextHologramData textData) {
                        textData.setLocation(holoLoc);
                        List<String> lines = new ArrayList<>();
                        if (lbMutation != Mutation.NONE || lbSnowy) {
                            lines.add(color(getMutationDisplayLine(lbMutation, lbSnowy)));
                        }
                        if (showMob.isMythical()) lines.add(color("§d✦ §f" + showMob.name + " §d✦"));
                        else lines.add(color("§f" + showMob.name));
                        lines.add(color(showMob.getRarityDisplay()));
                        if (tick >= totalTicks) {
                            double mult = lbMutation.incomeMultiplier;
                            if (lbSnowy) mult *= Mutation.SNOWY.incomeMultiplier;
                            double finalIncome = showMob.baseIncome * mult;
                            lines.add(color("§a+" + formatNumber(finalIncome) + "§2$/сек"));
                            lines.add(color("§6" + formatNumber(showMob.sellPrice) + "$"));
                            if (lbMutation == Mutation.RAINBOW) {
                                world.spawnParticle(Particle.NOTE, animLoc.clone().add(0, 1, 0), 10, 0.5, 0.5, 0.5);
                            }
                        }
                        textData.setText(lines);
                        animHolo.queueUpdate();
                    }
                    if (tick < totalTicks) {
                        float pitch = 0.5f + (tick / (float) totalTicks) * 1.5f;
                        world.playSound(animLoc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, pitch);
                    } else {
                        world.playSound(animLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        world.spawnParticle(Particle.TOTEM_OF_UNDYING, animLoc, 30, 0.5, 1, 0.5, 0.2);
                    }
                }
                if (tick >= totalTicks + pauseAfter) {
                    if (currentDisplayMob != null && !currentDisplayMob.isDead()) {
                        currentDisplayMob.remove();
                    }
                    hologramManager.removeHologram(animHolo);
                    animatingPoints.remove(finalMobPoint);
                    List<String> colPoints = baseCollectorPoints.get(finalBase);
                    String freeCol = null;
                    if (finalCollectorPoint != null) {
                        freeCol = finalCollectorPoint;
                    } else {
                        freeCol = findFreeCollectorForBase(finalBase, colPoints);
                    }
                    spawnMobAtPointExact(finalBase, finalMobPoint, freeCol, wonMob);
                    if (lbMutation != Mutation.NONE || lbSnowy) {
                        applyMutationToPoint(finalBase, finalMobPoint, lbMutation.name(), lbSnowy);
                        String mutName = lbMutation == Mutation.NONE ? "" : lbMutation.displayName;
                        if (lbSnowy) mutName += (mutName.isEmpty() ? "" : " ") + Mutation.SNOWY.displayName;
                        player.sendMessage(color("§a✨ Моб унаследовал мутацию: " + mutName + "!"));
                    }
                    if (wonMob == MobType.WARDEN) {
                        world.playSound(animLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1f);
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendMessage(color("§d§l✦ " + player.getName() + " §fоткрыл §c§lВАРДЕНА §fиз Лаки-Блока! §d§l✦"));
                        }
                    } else if (wonMob == MobType.SNIFFER) {
                        world.playSound(animLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 1f);
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendMessage(color("§d✦ " + player.getName() + " §fоткрыл §d§lСниффера §fиз Лаки-Блока! §d✦"));
                        }
                    } else {
                        world.playSound(animLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f, 1f);
                    }
                    player.sendTitle(color("§d§l⭐ " + wonMob.name + " ⭐"), color(wonMob.getRarityDisplay() + " §7— Новый моб на базе!"), 10, 60, 20);
                    player.sendMessage(color("§d§l✦ §fВы открыли Лаки-Блок и получили: " + wonMob.getRarityDisplay() + " " + wonMob.name + "§f! §d§l✦"));
                    savePlayerMobsInstantly(player.getName());
                    cancel();
                }
            }
        }.runTaskTimer(this, 10L, 1L);
    }
    private void startLuckyBlockTimerUpdater() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long currentTime = System.currentTimeMillis();
            for (Map.Entry<Entity, Long> entry : new HashMap<>(luckyBlockOpenTime).entrySet()) {
                Entity mob = entry.getKey();
                long openTime = entry.getValue();
                if (mob == null || mob.isDead() || !mob.isValid()) {
                    luckyBlockOpenTime.remove(mob);
                    luckyBlockReady.remove(mob);
                    continue;
                }
                if (!entityToPointMap.containsKey(mob)) {
                    luckyBlockOpenTime.remove(mob);
                    luckyBlockReady.remove(mob);
                    continue;
                }
                String hologramName = "mob_" + mob.getUniqueId();
                Optional<Hologram> holoOpt = hologramManager.getHologram(hologramName);
                if (holoOpt.isEmpty()) continue;
                Hologram holo = holoOpt.get();
                if (!(holo.getData() instanceof TextHologramData data)) continue;
                long remaining = openTime - currentTime;
                Mutation mutation = baseMobMutations.getOrDefault(mob, Mutation.NONE);
                boolean snowy = baseMobSnowy.getOrDefault(mob, false);
                List<String> lines = new ArrayList<>();
                if (mutation != Mutation.NONE || snowy) {
                    lines.add(color(getMutationDisplayLine(mutation, snowy)));
                }
                if (remaining <= 0) {
                    luckyBlockReady.put(mob, true);
                    lines.add(color("§a§l✔ ГОТОВ К ОТКРЫТИЮ"));
                    lines.add(color("§e§l⭐ §d§lЛаки-Блок §e§l⭐"));
                    lines.add(color(MobType.SPONGE.getRarityDisplay()));
                    lines.add(color("§aНажмите ПКМ чтобы открыть!"));
                } else {
                    int totalSeconds = (int) (remaining / 1000);
                    int minutes = totalSeconds / 60;
                    int seconds = totalSeconds % 60;
                    String timerColor;
                    if (totalSeconds > 300) {
                        timerColor = "§e";
                    } else if (totalSeconds > 60) {
                        timerColor = "§6";
                    } else {
                        timerColor = "§c";
                    }
                    lines.add(color(timerColor + "⏳ " + String.format("%02d:%02d", minutes, seconds)));
                    lines.add(color("§e§l⭐ §d§lЛаки-Блок §e§l⭐"));
                    lines.add(color(MobType.SPONGE.getRarityDisplay()));
                    lines.add(color("§6" + formatNumber(MobType.SPONGE.sellPrice) + "$"));
                }
                data.setText(lines);
                holo.queueUpdate();
            }
        }, 20L, 20L);
    }
    private void cleanupAllDuplicateHolograms() {
        if (hologramManager == null) return;
        debugLog("[CLEANUP] ========== ОЧИСТКА ДУБЛИКАТОВ ==========");
        Map<String, List<Hologram>> hologramsByName = new HashMap<>();
        for (Hologram holo : hologramManager.getHolograms()) {
            String name = holo.getData().getName();
            hologramsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(holo);
        }
        int totalRemoved = 0;
        for (Map.Entry<String, List<Hologram>> entry : hologramsByName.entrySet()) {
            List<Hologram> holos = entry.getValue();
            if (holos.size() > 1) {
                debugLog("[CLEANUP] Найдено " + holos.size() + " дубликатов: " + entry.getKey());
                for (int i = 1; i < holos.size(); i++) {
                    hologramManager.removeHologram(holos.get(i));
                    totalRemoved++;
                }
            }
        }
        Set<String> validMobUUIDs = new HashSet<>();
        for (Entity mob : entityToPointMap.keySet()) {
            if (mob != null && !mob.isDead() && mob.isValid()) {
                validMobUUIDs.add(mob.getUniqueId().toString());
            }
        }
        for (Hologram holo : new ArrayList<>(hologramManager.getHolograms())) {
            String name = holo.getData().getName();
            if (name.startsWith("mob_")) {
                String uuid = name.substring("mob_".length());
                if (!validMobUUIDs.contains(uuid)) {
                    hologramManager.removeHologram(holo);
                    totalRemoved++;
                    debugLog("[CLEANUP] Удалена осиротевшая голограмма моба: " + name);
                }
            }
        }
        Set<String> validCollectorIds = new HashSet<>(collectorToEntityMap.keySet());
        for (Hologram holo : new ArrayList<>(hologramManager.getHolograms())) {
            String name = holo.getData().getName();
            if (name.startsWith("collector_")) {
                String collectorId = name.substring("collector_".length());
                if (!validCollectorIds.contains(collectorId)) {
                    hologramManager.removeHologram(holo);
                    collectorHolograms.remove(collectorId);
                    totalRemoved++;
                    debugLog("[CLEANUP] Удалена осиротевшая голограмма коллектора: " + name);
                }
            }
        }
        debugLog("[CLEANUP] Всего удалено дубликатов: " + totalRemoved);
        debugLog("[CLEANUP] ==========================================");
    }
    private void registerResetRebirthCommand() {
        PluginCommand command = getCommand("resetrebirth");
        if (command != null) {
            command.setExecutor(new CommandExecutor() {
                @Override
                public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                    if (!sender.hasPermission("brainrotbases.admin")) {
                        sender.sendMessage("§cНет прав!");
                        return true;
                    }
                    if (args.length < 1) {
                        sender.sendMessage("§cИспользование: /resetrebirth <игрок>");
                        return true;
                    }
                    String targetName = args[0];
                    OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                    FileConfiguration config = getConfig();
                    config.set("rebirths." + target.getUniqueId(), null);
                    saveConfig();
                    Player onlineTarget = target.getPlayer();
                    if (onlineTarget != null) {
                        playerEarnMultipliers.put(onlineTarget.getUniqueId(), 1.0);
                    }
                    sender.sendMessage("§aПерерождения игрока " + targetName + " сброшены!");
                    return true;
                }
            });
        }
    }
    private void cleanUpSavedMobs() {
        FileConfiguration cfg = getConfig();
        if (cfg.contains("saved_mobs")) {
            List<String> playersToRemove = new ArrayList<>();
            for (String playerName : cfg.getConfigurationSection("saved_mobs").getKeys(false)) {
                boolean hasBase = false;
                for (String base : bases.keySet()) {
                    if (playerName.equals(bases.get(base))) {
                        hasBase = true;
                        break;
                    }
                }
                if (!hasBase) {
                    playersToRemove.add(playerName);
                    debugLog("Очищены устаревшие мобы игрока без базы: " + playerName);
                }
            }
            for (String playerName : playersToRemove) {
                cfg.set("saved_mobs." + playerName, null);
            }
            if (!playersToRemove.isEmpty()) {
                saveConfig();
            }
        }
    }
    private void startMobCheckTimer() {
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<Entity, String> entry : new HashMap<>(entityToPointMap).entrySet()) {
                    Entity mob = entry.getKey();
                    String mobPoint = entry.getValue();
                    if (animatingPoints.contains(mobPoint)) continue;
                    if (mob == null || mob.isDead()) {
                        getLogger().warning("Моб был удален! Восстанавливаем...");
                        handleMobDespawn(mob, entry.getValue());
                        continue;
                    }
                    if (mob.getScoreboardTags().contains("LUCKY_BLOCK") ||
                        luckyBlockTags.containsKey(mob)) {
                        continue;
                    }
                    Location expectedLocation = getLocationFromPoint(mobPoint);
                    if (expectedLocation != null) {
                        double distance = mob.getLocation().distance(expectedLocation);
                        if (distance > 2.0) {
                            getLogger().warning("Моб " + mob.getType() + " сдвинулся на " + distance + " блоков. Возвращаем.");
                            mob.teleport(expectedLocation);
                            disableMobPhysics(mob);
                        }
                    }
                    if (!mob.getScoreboardTags().contains(BASE_MOB_TAG)) {
                        mob.addScoreboardTag(BASE_MOB_TAG);
                    }
                    disableMobPhysics(mob);
                    String hologramName = "mob_" + mob.getUniqueId();
                    Optional<Hologram> mobHologram = hologramManager.getHologram(hologramName);
                    if (mobHologram.isEmpty()) {
                        getLogger().warning("Голограмма моба пропала! Создаем заново.");
                        MobType type = MobType.fromEntity(mob);
                        if (type != null) {
                            createMobHologram(mob, type);
                        }
                    }
                }
            }
        }, 20L, 20L);
    }
    private void startAggressiveMobCheckTimer() {
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                for (Map.Entry<String, List<String>> entry : baseMobSpawnPoints.entrySet()) {
                    String base = entry.getKey();
                    List<String> mobPoints = entry.getValue();
                    for (String mobPoint : mobPoints) {
                        Set<String> occupied = occupiedMobPoints.get(base);
                        if (occupied != null && occupied.contains(mobPoint)) {
                            if (animatingPoints.contains(mobPoint)) {
                                continue;
                            }
                            boolean mobFound = false;
                            for (Map.Entry<Entity, String> mobEntry : new HashMap<>(entityToPointMap).entrySet()) {
                                if (mobPoint.equals(mobEntry.getValue())) {
                                    Entity mob = mobEntry.getKey();
                                    if (mob != null && !mob.isDead() && mob.getScoreboardTags().contains("STOLEN_MOB")) {
                                        mobFound = true;
                                        break;
                                    }
                                    if (mob != null && !mob.isDead() &&
                                        (mob.getScoreboardTags().contains("LUCKY_BLOCK") ||
                                         luckyBlockTags.containsKey(mob))) {
                                        mobFound = true;
                                        break;
                                    }
                                    if (mob == null || mob.isDead()) {
                                        getLogger().warning("Моб на точке " + mobPoint + " мёртв или null!");
                                        handleMobDespawn(mob, mobPoint);
                                        mobFound = false;
                                    } else {
                                        Location expectedLoc = getLocationFromPoint(mobPoint);
                                        if (expectedLoc != null) {
                                            double distance = mob.getLocation().distance(expectedLoc);
                                            if (distance > 1.0) {
                                                getLogger().warning("Моб сместился на " + distance + " блоков. Возвращаем.");
                                                mob.teleport(expectedLoc);
                                                disableMobPhysics(mob);
                                            }
                                        }
                                        mobFound = true;
                                    }
                                    break;
                                }
                            }
                            if (!mobFound) {
                                boolean isBeingStolen = false;
                                for (StealingData data : stealingPlayers.values()) {
                                    if (data.originalBase.equals(base)) {
                                        String originalPoint = findOriginalMobPoint(base, data.originalLocation);
                                        if (mobPoint.equals(originalPoint)) {
                                            isBeingStolen = true;
                                            break;
                                        }
                                    }
                                }
                                if (isBeingStolen) {
                                    continue;
                                }
                                String owner = bases.get(base);
                                if (owner != null && !owner.equals("none")) {
                                    Player ownerPlayer = Bukkit.getPlayer(owner);
                                    if (ownerPlayer != null) {
                                        int rebirthCount = getRebirthCount(ownerPlayer);
                                        long lastRebirthTime = getLastRebirthTime(ownerPlayer);
                                        long timeSinceRebirth = currentTime - lastRebirthTime;
                                        if (rebirthCount > 0 && timeSinceRebirth < 10 * 60 * 1000) {
                                            if (occupied != null) {
                                                occupied.remove(mobPoint);
                                            }
                                            continue;
                                        }
                                    }
                                }
                                getLogger().warning("КРИТИЧЕСКАЯ ОШИБКА: Моб пропал на занятой точке " + mobPoint);
                                restoreMobFromSavedData(base, mobPoint);
                            }
                        }
                    }
                }
            }
        }, 100L, 100L);
    }
    private void restoreMobFromSavedData(String base, String mobPoint) {
        debugLog("Восстановление моба на точке " + mobPoint);
        String owner = bases.get(base);
        if (owner == null || owner.equals("none")) {
            getLogger().warning("База без владельца");
            Set<String> occupied = occupiedMobPoints.get(base);
            if (occupied != null) occupied.remove(mobPoint);
            return;
        }
        Player ownerPlayer = Bukkit.getPlayer(owner);
        if (ownerPlayer != null) {
            long lastRebirthTime = getLastRebirthTime(ownerPlayer);
            if (lastRebirthTime > 0 && System.currentTimeMillis() - lastRebirthTime < 5 * 60 * 1000) {
                debugLog("Недавнее перерождение, пропускаем");
                Set<String> occupied = occupiedMobPoints.get(base);
                if (occupied != null) occupied.remove(mobPoint);
                return;
            }
        }
        List<SavedMobData> savedMobs = savedPlayerMobs.get(owner);
        if (savedMobs == null || savedMobs.isEmpty()) {
            getLogger().warning("Нет сохраненных мобов для " + owner);
            Set<String> occupied = occupiedMobPoints.get(base);
            if (occupied != null) occupied.remove(mobPoint);
            return;
        }
        for (SavedMobData savedMob : savedMobs) {
            if (savedMob.base.equals(base) && savedMob.mobPoint.equals(mobPoint)) {
                debugLog("Найден моб " + savedMob.mobType.name + " для точки " + mobPoint);
                spawnMobAtPointExact(base, mobPoint, savedMob.collectorPoint, savedMob.mobType);
                if (ownerPlayer != null && ownerPlayer.isOnline()) {
                    ownerPlayer.sendMessage("§a⚡ Моб §e" + savedMob.mobType.name + "§a восстановлен!");
                }
                return;
            }
        }
        getLogger().warning("Моб для точки " + mobPoint + " не найден в сохранениях");
        Set<String> occupied = occupiedMobPoints.get(base);
        if (occupied != null) occupied.remove(mobPoint);
    }
    private void disableMobPhysics(Entity mob) {
        if (mob instanceof LivingEntity living) {
            living.setAI(false);
            living.setInvulnerable(true);
            living.setGravity(false);
            living.setCollidable(false);
            living.setSilent(true);
            living.setRemoveWhenFarAway(false);
            if (mob instanceof Monster monster) {
                monster.setAware(false);
            }
            if (mob instanceof Ageable ageable) {
                ageable.setAgeLock(true);
            }
            if (mob instanceof Mob mobEntity) {
                mobEntity.setTarget(null);
            }
        }
        mob.setCustomNameVisible(false);
        mob.setPersistent(true);
        mob.addScoreboardTag("BASE_MOB_PERSISTENT");
        mob.addScoreboardTag("NO_DESPAWN");
    }
    private void ensureChunkLoaded(Location location) {
        if (location == null) return;
        Chunk chunk = location.getChunk();
        if (!chunk.isLoaded()) {
            chunk.load();
            debugLog("Чанк загружен: " + chunk.getX() + "," + chunk.getZ());
        }
        try {
            chunk.setForceLoaded(true);
        } catch (Exception e) { org.bukkit.Bukkit.getLogger().warning("Brainrot[brainrot-bases]: " + e.getMessage()); }
    }
    private void handleMobDespawn(Entity mob, String mobPoint) {
        if (mobPoint == null) return;
        removeMobHologram(mob);
        String base = null;
        for (Map.Entry<String, List<String>> entry : baseMobSpawnPoints.entrySet()) {
            if (entry.getValue().contains(mobPoint)) {
                base = entry.getKey();
                break;
            }
        }
        if (base == null) return;
        entityToPointMap.remove(mob);
        mobSpawnTime.remove(mob);
        String collectorId = entityToCollectorMap.get(mobPoint);
        if (collectorId != null) {
            removeCollectorHologram(collectorId);
        }
        Set<String> occupied = occupiedMobPoints.get(base);
        if (occupied != null) {
            occupied.remove(mobPoint);
        }
        getLogger().warning("Моб на точке " + mobPoint + " пропал и был удален из системы");
        final String finalBase = base;
        final String finalMobPoint = mobPoint;
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                restoreMobIfMissing(finalBase, finalMobPoint);
            }
        }, 20L);
    }
    private void restoreMobIfMissing(String base, String mobPoint) {
        for (StealingData data : stealingPlayers.values()) {
            if (data.originalBase.equals(base)) {
                String originalPoint = findOriginalMobPoint(base, data.originalLocation);
                if (mobPoint.equals(originalPoint)) {
                    return;
                }
            }
        }
        boolean mobExists = false;
        for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
            if (mobPoint.equals(entry.getValue())) {
                mobExists = true;
                break;
            }
        }
        if (!mobExists) {
            getLogger().warning("Моб на точке " + mobPoint + " отсутствует. Восстанавливаем из сохраненных данных.");
            for (Map.Entry<String, List<SavedMobData>> entry : savedPlayerMobs.entrySet()) {
                for (SavedMobData savedMob : entry.getValue()) {
                    if (savedMob.base.equals(base) && savedMob.mobPoint.equals(mobPoint)) {
                        spawnMobAtPoint(base, mobPoint, savedMob.collectorPoint, savedMob.mobType);
                        return;
                    }
                }
            }
        }
    }
    private Location getLocationFromPoint(String mobPoint) {
        if (mobPoint == null) return null;
        String[] s = mobPoint.split("_");
        if (s.length != 4) return null;
        try {
            World world = Bukkit.getWorld(s[0]);
            if (world == null) return null;
            int x = Integer.parseInt(s[1]);
            int y = Integer.parseInt(s[2]);
            int z = Integer.parseInt(s[3]);
            return new Location(world, x + 0.5, y + 0.5, z + 0.5);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    @Override
    public void onDisable() {
        // __leakfix__
        try { org.bukkit.Bukkit.getScheduler().cancelTasks(this); } catch (Throwable __t) {}
        try { org.bukkit.event.HandlerList.unregisterAll((org.bukkit.plugin.Plugin) this); } catch (Throwable __t) {}
        moneyTimerRunning = false;
        getLogger().info("=== ВЫКЛЮЧЕНИЕ ПЛАГИНА ===");
        getLogger().info("Мобы уже сохранены в реальном времени, пропускаем сохранение");
        for (StealingData data : new ArrayList<>(stealingPlayers.values())) {
            cancelStealing(data.player, false);
        }
        for (BukkitRunnable task : stealProgressTasks.values()) {
            task.cancel();
        }
        stealProgressTasks.clear();
        luckyBlockOpenTime.clear();
        luckyBlockReady.clear();
        for (Entity hitbox : rotWalkerHitboxMap.values()) {
            if (hitbox != null && hitbox.isValid()) hitbox.remove();
        }
        rotWalkerHitboxMap.clear();
        rotWalkerTags.clear();
        for (BukkitRunnable anim : baseRainbowAnimations.values()) {
            if (anim != null) try { anim.cancel(); } catch (Exception ignored) {}
        }
        baseRainbowAnimations.clear();
        baseMobMutations.clear();
        baseMobSnowy.clear();
        for (Entity entity : new ArrayList<>(entityToPointMap.keySet())) {
            removeMobHologram(entity);
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        entityToPointMap.clear();
        mobSpawnTime.clear();
        for (Hologram holo : collectorHolograms.values()) {
            hologramManager.removeHologram(holo);
        }
        collectorHolograms.clear();
        for (Hologram holo : lockHolograms.values()) {
            hologramManager.removeHologram(holo);
        }
        lockHolograms.clear();
        for (Hologram holo : new ArrayList<>(hologramManager.getHolograms())) {
            if (holo.getData().getName().startsWith("lb_anim_")) {
                hologramManager.removeHologram(holo);
            }
        }
        animatingPoints.clear();
        saveInitialBalancePlayers();
        saveRebirthsFile();
        saveFriends();
        getLogger().info("=== ПЛАГИН ВЫКЛЮЧЕН ===");
    }
    private void savePlayerMobsForShutdown(String playerName, String base) {
        if (mobsConfig == null || playerName == null || base == null) {
            getLogger().warning("savePlayerMobsForShutdown: некорректные параметры");
            return;
        }
        List<String> basePoints = baseMobSpawnPoints.get(base);
        if (basePoints == null || basePoints.isEmpty()) {
            getLogger().warning("База " + base + " не имеет точек спавна");
            return;
        }
        mobsConfig.set("mobs." + playerName, null);
        int savedCount = 0;
        for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
            Entity mob = entry.getKey();
            String mobPoint = entry.getValue();
            if (mob == null || mob.isDead() || !mob.isValid()) {
                continue;
            }
            if (!basePoints.contains(mobPoint)) {
                continue;
            }
            MobType type = MobType.fromEntity(mob);
            if (type == null) {
                getLogger().warning("Не удалось определить тип моба на точке " + mobPoint);
                continue;
            }
            String collectorId = entityToCollectorMap.get(mobPoint);
            String collectorPoint = null;
            if (collectorId != null) {
                String[] parts = collectorId.split("_", 2);
                if (parts.length == 2 && parts[0].equals(base)) {
                    collectorPoint = parts[1];
                }
            }
            String path = "mobs." + playerName + "." + savedCount;
            mobsConfig.set(path + ".base", base);
            mobsConfig.set(path + ".mobPoint", mobPoint);
            mobsConfig.set(path + ".mobType", type.name());
            mobsConfig.set(path + ".collectorPoint", collectorPoint);
            savedCount++;
            getLogger().info("  ✓ " + type.name + " на точке " + mobPoint);
        }
        debugLog("Сохранено " + savedCount + " мобов для игрока " + playerName);
    }
    public Map<String, String> getBases() {
        return new HashMap<>(bases);
    }
    public boolean hasFreeMobSlots(String playerName) {
        String playerBase = null;
        for (Map.Entry<String, String> entry : bases.entrySet()) {
            if (entry.getValue().equals(playerName)) {
                playerBase = entry.getKey();
                break;
            }
        }
        if (playerBase == null) {
            getLogger().info("У игрока " + playerName + " нет базы");
            return false;
        }
        return hasFreeMobSlotsForBase(playerBase);
    }
    public boolean hasFreeMobSlotsForBase(String baseName) {
        List<String> mobPoints = baseMobSpawnPoints.get(baseName);
        Set<String> occupied = occupiedMobPoints.get(baseName);
        if (mobPoints == null || occupied == null) {
            getLogger().warning("База " + baseName + " не найдена в конфигурации");
            return false;
        }
        boolean hasFreeSlots = occupied.size() < mobPoints.size();
        debugLog("База " + baseName + ": " + occupied.size() + "/" + mobPoints.size() + " слотов занято. Свободно: " + hasFreeSlots);
        return hasFreeSlots;
    }
    public String getBaseInfo(String playerName) {
        String playerBase = null;
        for (Map.Entry<String, String> entry : bases.entrySet()) {
            if (entry.getValue().equals(playerName)) {
                playerBase = entry.getKey();
                break;
            }
        }
        if (playerBase == null) return "§cУ вас нет базы";
        List<String> mobPoints = baseMobSpawnPoints.get(playerBase);
        Set<String> occupied = occupiedMobPoints.get(playerBase);
        if (mobPoints == null || occupied == null) return "§cОшибка базы";
        return String.format("§7База: §e%s §7| §f%d§7/§a%d §7слотов занято",
            playerBase, occupied.size(), mobPoints.size());
    }
    private List<String> getFreeBases() {
        List<String> freeBases = new ArrayList<>();
        for (Map.Entry<String, String> entry : bases.entrySet()) {
            String baseName = entry.getKey();
            String owner = entry.getValue();
            if (owner.equals("none")) {
                FileConfiguration cfg = getConfig();
                String worldName = cfg.getString("bases." + baseName + ".world", "world");
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    freeBases.add(baseName);
                }
            }
        }
        freeBases.sort(String::compareTo);
        return freeBases;
    }
    private String getRandomFreeBase() {
        List<String> freeBases = getFreeBases();
        if (freeBases.isEmpty()) {
            return null;
        }
        List<String> worldBases = new ArrayList<>();
        List<String> world2Bases = new ArrayList<>();
        List<String> otherBases = new ArrayList<>();
        FileConfiguration cfg = getConfig();
        for (String baseName : freeBases) {
            String worldName = cfg.getString("bases." + baseName + ".world", "world");
            if (worldName.equals("world")) {
                worldBases.add(baseName);
            } else if (worldName.equals("world2")) {
                world2Bases.add(baseName);
            } else {
                otherBases.add(baseName);
            }
        }
        debugLog("========== ВЫБОР БАЗЫ ==========");
        debugLog("Свободные базы в world: " + worldBases.size() + " " + worldBases);
        debugLog("Свободные базы в world2: " + world2Bases.size() + " " + world2Bases);
        if (!otherBases.isEmpty()) {
            debugLog("Свободные базы в других мирах: " + otherBases.size() + " " + otherBases);
        }
        if (!worldBases.isEmpty()) {
            int randomIndex = random.nextInt(worldBases.size());
            String selectedBase = worldBases.get(randomIndex);
            debugLog("✓ Выбрана база из world: " + selectedBase);
            debugLog("=================================");
            return selectedBase;
        }
        debugLog("⚠ Все базы в world заняты! Переходим к world2...");
        if (!world2Bases.isEmpty()) {
            int randomIndex = random.nextInt(world2Bases.size());
            String selectedBase = world2Bases.get(randomIndex);
            debugLog("✓ Выбрана база из world2: " + selectedBase);
            debugLog("=================================");
            return selectedBase;
        }
        debugLog("⚠ Все базы в world2 тоже заняты!");
        if (!otherBases.isEmpty()) {
            int randomIndex = random.nextInt(otherBases.size());
            String selectedBase = otherBases.get(randomIndex);
            debugLog("✓ Выбрана база из другого мира: " + selectedBase);
            debugLog("=================================");
            return selectedBase;
        }
        debugLog("✗ Нет свободных баз ни в одном мире!");
        debugLog("=================================");
        return null;
    }
    private void startUnifiedKickTimer() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long currentTime = System.currentTimeMillis();
            for (Map.Entry<String, Boolean> entry : new HashMap<>(baseLocked).entrySet()) {
                String base = entry.getKey();
                boolean locked = entry.getValue();
                if (!locked) continue;
                String owner = bases.get(base);
                if (owner == null || owner.equals("none")) continue;
                String particlePoint = baseParticlePoints.get(base);
                FileConfiguration cfg = getConfig();
                String worldName = cfg.getString("bases." + base + ".world");
                if (worldName == null) continue;
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;
                for (Player player : world.getPlayers()) {
                    if (player.getName().equals(owner)) continue;
                    if (particlePoint != null && isPlayerNearParticleWall(player, particlePoint)) {
                        executeKick(player, base, currentTime, KickReason.PARTICLE_WALL);
                    }
                }
            }
            for (Map.Entry<String, Boolean> st2e : new HashMap<>(stage2Active).entrySet()) {
                if (!Boolean.TRUE.equals(st2e.getValue())) continue;
                String st2base = st2e.getKey();
                Stage2Config sc = stage2Configs.get(st2base);
                if (sc == null || sc.laserPoint == null || sc.laserPoint.isEmpty()) continue;
                String st2owner = bases.get(st2base);
                if (st2owner == null || st2owner.equals("none")) continue;
                Location laserLoc = getLocationFromPoint(sc.laserPoint);
                if (laserLoc == null || laserLoc.getWorld() == null) continue;
                for (Player player : laserLoc.getWorld().getPlayers()) {
                    if (player.getName().equals(st2owner)) continue;
                    if (isPlayerNearParticleWall(player, sc.laserPoint)) {
                        executeKick(player, st2base, currentTime, KickReason.PARTICLE_WALL);
                    }
                }
            }
        }, 0L, 5L);
    }
    private void startStealingCheckTimer() {
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                for (StealingData data : new ArrayList<>(stealingPlayers.values())) {
                    if (data == null) continue;
                    if (!data.player.isOnline()) {
                        cancelStealing(data.player, true);
                        continue;
                    }
                    if (data.isCarrying) {
                        Player player = data.player;
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false, true));
                        if (player.isSprinting()) {
                            player.setSprinting(false);
                        }
                        if (player.isSneaking()) {
                            player.setSneaking(false);
                        }
                        checkSubmitZoneForCarrier(data);
                        continue;
                    }
                    boolean isLuckyBlock = data.mob != null &&
                        (data.mob.getScoreboardTags().contains("LUCKY_BLOCK") ||
                         luckyBlockTags.containsKey(data.mob));
                    if (isLuckyBlock) {
                        if (data.mob != null && !entityToPointMap.containsKey(data.mob)) {
                            if (!data.isCarrying) {
                                cancelStealing(data.player, true);
                            }
                        }
                    } else {
                        if (data.mob == null || data.mob.isDead() || !data.mob.isValid()) {
                            cancelStealing(data.player, true);
                        }
                    }
                }
            }
        }, 0L, 2L);
    }
    private void startMoneyAccumulationTimer() {
        moneyTimerRunning = true;
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                if (!moneyTimerRunning) return;
                long currentTime = System.currentTimeMillis();
                synchronized (moneyLock) {
                    for (String collectorId : new ArrayList<>(collectorToEntityMap.keySet())) {
                        String mobPoint = collectorToEntityMap.get(collectorId);
                        if (mobPoint == null) continue;
                        Entity mob = null;
                        for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
                            if (mobPoint.equals(entry.getValue())) {
                                mob = entry.getKey();
                                break;
                            }
                        }
                        if (mob == null || mob.isDead()) {
                            collectorLastUpdate.put(collectorId, currentTime);
                            continue;
                        }
                        Long lastUpdate = collectorLastUpdate.get(collectorId);
                        if (lastUpdate == null) {
                            collectorLastUpdate.put(collectorId, currentTime);
                            collectorMoney.put(collectorId, 0.0);
                            continue;
                        }
                        long secondsPassed = (currentTime - lastUpdate) / 1000;
                        if (secondsPassed > 0) {
                            MobType type = MobType.fromEntity(mob);
                            if (type == null) continue;
                            double baseIncome = type.baseIncome;
                            double mutationMultiplier = getMobMutationMultiplier(mob);
                            baseIncome *= mutationMultiplier;
                            String base = findBaseByCollectorId(collectorId);
                            if (base == null) continue;
                            if (isAuctionListed(base, mobPoint)) {
                                collectorLastUpdate.put(collectorId, lastUpdate + (secondsPassed * 1000));
                                continue;
                            }
                            // __fix__ earn-multiplier is applied once at collection time (handleMoneyCollection).
                            // Applying it here too caused double-multiplication (e.g. rebirth x5 became x25).
                            double income = baseIncome * secondsPassed;
                            double currentMoney = collectorMoney.getOrDefault(collectorId, 0.0);
                            double newMoney = currentMoney + income;
                            newMoney = Math.round(newMoney * 10000.0) / 10000.0;
                            collectorMoney.put(collectorId, newMoney);
                            collectorLastUpdate.put(collectorId, lastUpdate + (secondsPassed * 1000));
                            updateCollectorHologram(collectorId);
                        }
                    }
                }
            }
        }, 20L, 20L);
    }
    private void killLuckyBlockByTag(Entity entity, String uniqTag) {
        if (uniqTag == null) return;
        World world = (entity != null && !entity.isDead() && entity.getWorld() != null)
                      ? entity.getWorld() : null;
        if (world == null) {
            for (World w : Bukkit.getWorlds()) {
                for (Entity e : w.getEntities()) {
                    if (e.getScoreboardTags().contains(uniqTag)) {
                        world = w;
                        break;
                    }
                }
                if (world != null) break;
            }
        }
        if (world == null) return;
        String dim = world.getKey().toString();
        final String tag = uniqTag;
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "execute in " + dim + " run minecraft:kill @e[tag=" + tag + "]");
            } catch (Exception ignored) {}
        });
    }
    private String findBaseByCollectorId(String collectorId) {
        if (collectorId == null) return null;
        String[] parts = collectorId.split("_", 2);
        return parts.length == 2 ? parts[0] : null;
    }
    private void startLockTimer() {
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                for (String base : new HashSet<>(baseLocked.keySet())) {
                    if (baseLocked.getOrDefault(base, false)) {
                        int timeLeft = baseLockTime.getOrDefault(base, 0);
                        if (timeLeft > 0) {
                            baseLockTime.put(base, timeLeft - 1);
                            updateLockHologram(base);
                            if (timeLeft == 30 || timeLeft == 10 || timeLeft == 5 || timeLeft <= 3) {
                                String owner = bases.get(base);
                                if (owner != null && !owner.equals("none")) {
                                    Player ownerPlayer = Bukkit.getPlayer(owner);
                                    if (ownerPlayer != null && ownerPlayer.isOnline()) {
                                        sendCooldownMessage(ownerPlayer, "§e⏰ До разблокировки базы: §6" + timeLeft + "§e сек.", lastLockMessage);
                                    }
                                }
                            }
                        } else {
                            unlockBase(base);
                        }
                    }
                }
            }
        }, 20L, 20L);
    }
    private void startParticleTimer() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<String, Boolean> entry : new HashMap<>(baseLocked).entrySet()) {
                    String base = entry.getKey();
                    boolean locked = entry.getValue();
                    if (locked) {
                        String particlePoint = baseParticlePoints.get(base);
                        if (particlePoint != null) {
                            String[] s = particlePoint.split("_");
                            if (s.length == 4) {
                                try {
                                    World world = Bukkit.getWorld(s[0]);
                                    if (world == null) continue;
                                    double startX = Integer.parseInt(s[1]) + 0.5;
                                    double startY = Integer.parseInt(s[2]);
                                    double startZ = Integer.parseInt(s[3]) + 0.5;
                                    int rowLength = 15;
                                    double height = 6;
                                    for (int i = 0; i <= rowLength; i++) {
                                        double currentX = startX + i;
                                        double currentZ = startZ;
                                        for (double y = startY; y <= startY + height; y += 0.5) {
                                            world.spawnParticle(Particle.DUST, currentX, y, currentZ, 1,
                                                new Particle.DustOptions(org.bukkit.Color.RED, 1.0f));
                                            if (y % 1 == 0) {
                                                world.spawnParticle(Particle.DUST, currentX, y, currentZ, 1,
                                                    new Particle.DustOptions(org.bukkit.Color.WHITE, 0.8f));
                                            }
                                        }
                                    }
                                } catch (NumberFormatException e) {
                                }
                            }
                        }
                    }
                }
                for (Map.Entry<String, Boolean> st2e : new HashMap<>(stage2Active).entrySet()) {
                    if (!Boolean.TRUE.equals(st2e.getValue())) continue;
                    Stage2Config sc = stage2Configs.get(st2e.getKey());
                    if (sc == null || sc.laserPoint == null) continue;
                    String[] s = sc.laserPoint.split("_");
                    if (s.length != 4) continue;
                    try {
                        World world = Bukkit.getWorld(s[0]);
                        if (world == null) continue;
                        double startX = Integer.parseInt(s[1]) + 0.5;
                        double startY = Integer.parseInt(s[2]);
                        double startZ = Integer.parseInt(s[3]) + 0.5;
                        int rowLength = 15;
                        double height = 6;
                        for (int i = 0; i <= rowLength; i++) {
                            double currentX = startX + i;
                            double currentZ = startZ;
                            for (double y = startY; y <= startY + height; y += 0.5) {
                                world.spawnParticle(Particle.DUST, currentX, y, currentZ, 1,
                                    new Particle.DustOptions(org.bukkit.Color.RED, 1.0f));
                            }
                        }
                    } catch (NumberFormatException e) {
                    }
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }
    private boolean isPlayerNearParticleWall(Player player, String particlePoint) {
        if (player == null || particlePoint == null) return false;
        String[] s = particlePoint.split("_");
        if (s.length != 4) return false;
        try {
            World world = Bukkit.getWorld(s[0]);
            if (world == null || !world.equals(player.getWorld())) return false;
            double startX = Integer.parseInt(s[1]) + 0.5;
            double startY = Integer.parseInt(s[2]);
            double startZ = Integer.parseInt(s[3]) + 0.5;
            int rowLength = 15;
            double height = 6;
            double playerX = player.getLocation().getX();
            double playerY = player.getLocation().getY();
            double playerZ = player.getLocation().getZ();
            if (playerY < startY - 0.5 || playerY > startY + height + 0.5) return false;
            for (int i = 0; i <= rowLength; i++) {
                double lineX = startX + i;
                double lineZ = startZ;
                double dx = Math.abs(playerX - lineX);
                double dz = Math.abs(playerZ - lineZ);
                if (dx < 1.5 && dz < 1.5) {
                    return true;
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return false;
    }
    private void executeKick(Player player, String base, long currentTime, KickReason reason) {
        if (player == null || base == null) return;
        if (reason != KickReason.PARTICLE_WALL) {
            return;
        }
        String owner = bases.get(base);
        if (owner == null || owner.equals("none")) return;
        if (isFriend(owner, player.getName())) {
            return;
        }
        Location teleportLoc = getKickLocation(base);
        if (teleportLoc == null) {
            return;
        }
        StealingData stealData = stealingPlayers.get(player);
        boolean isCarryingMob = stealData != null && stealData.isCarrying;
        if (isCarryingMob) {
            List<Entity> passengers = new ArrayList<>(player.getPassengers());
            for (Entity p : passengers) {
                player.removePassenger(p);
            }
            player.teleport(teleportLoc);
            player.setNoDamageTicks(0);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!player.isOnline()) return;
                for (Entity p : passengers) {
                    if (!p.isDead()) {
                        p.teleport(teleportLoc);
                        player.addPassenger(p);
                    }
                }
                player.setNoDamageTicks(0);
            }, 2L);
        } else {
            player.teleport(teleportLoc);
            player.setNoDamageTicks(0);
        }
        Long lastMsg = lastLockMessage.get(player);
        if (lastMsg == null || currentTime - lastMsg > GENERAL_MESSAGE_COOLDOWN) {
            player.sendMessage("§c⛔ База заблокирована!");
            if (isCarryingMob) {
                player.sendMessage("§e⚠ Вы сохранили украденного моба!");
            }
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 20, 0.5, 1, 0.5, 0.1);
            lastLockMessage.put(player, currentTime);
        }
    }
    private Location getKickLocation(String base) {
        List<String> kickPoints = baseKickPoints.get(base);
        if (kickPoints == null || kickPoints.isEmpty()) {
            getLogger().warning("У базы " + base + " нет kick_points!");
            return null;
        }
        FileConfiguration cfg = getConfig();
        String worldName = cfg.getString("bases." + base + ".world");
        if (worldName == null) {
            getLogger().warning("У базы " + base + " не указан мир!");
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().warning("Мир " + worldName + " не найден!");
            return null;
        }
        String kickPoint = kickPoints.get(random.nextInt(kickPoints.size()));
        String[] s = kickPoint.split("_");
        if (s.length != 4) {
            getLogger().warning("Неверный формат kick_point: " + kickPoint);
            return null;
        }
        try {
            int x = Integer.parseInt(s[1]);
            int y = Integer.parseInt(s[2]);
            int z = Integer.parseInt(s[3]);
            Location loc = findSafeLocation(world, x, y, z);
            if (loc == null) {
                loc = new Location(world, x + 0.5, y + 1, z + 0.5);
                getLogger().warning("Не найдена безопасная позиция, используем fallback: " + loc);
            }
            if (isReversedBase(base)) {
                loc.setYaw(0.0f);
            } else {
                loc.setYaw(180.0f);
            }
            loc.setPitch(0.0f);
            debugLog("Kick location для базы " + base + ": " + loc + " (reversed: " + isReversedBase(base) + ")");
            return loc;
        } catch (NumberFormatException e) {
            getLogger().warning("Ошибка парсинга координат kick_point: " + kickPoint);
            return null;
        }
    }
    private Location findSafeLocation(World world, int baseX, int baseY, int baseZ) {
        int[][] offsets = {
            {0, 0, 0},
            {0, 1, 0},
            {0, 2, 0},
            {1, 0, 0},
            {-1, 0, 0},
            {0, 0, 1},
            {0, 0, -1},
            {1, 1, 0},
            {-1, 1, 0},
            {0, 1, 1},
            {0, 1, -1},
        };
        for (int[] offset : offsets) {
            int x = baseX + offset[0];
            int y = baseY + offset[1];
            int z = baseZ + offset[2];
            if (isSafeLocation(world, x, y, z)) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        for (int yOffset = 0; yOffset <= 10; yOffset++) {
            if (isSafeLocation(world, baseX, baseY + yOffset, baseZ)) {
                return new Location(world, baseX + 0.5, baseY + yOffset, baseZ + 0.5);
            }
        }
        return null;
    }
    private boolean isSafeLocation(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block ground = world.getBlockAt(x, y - 1, z);
        boolean feetClear = feet.isPassable() && !feet.isLiquid();
        boolean headClear = head.isPassable() && !head.isLiquid();
        boolean groundSolid = ground.getType().isSolid() && !ground.isPassable();
        boolean notInBlock = feet.getType() == Material.AIR ||
                             feet.getType() == Material.CAVE_AIR ||
                             feet.getType() == Material.VOID_AIR ||
                             feet.getType().name().contains("GRASS") ||
                             feet.getType().name().contains("FLOWER") ||
                             feet.getType().name().contains("FERN");
        return feetClear && headClear && groundSolid && notInBlock;
    }
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX() &&
            from.getBlockY() == to.getBlockY() &&
            from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        String pointKeyX_Z = to.getWorld().getName() + "_" + to.getBlockX() + "_ANY_" + to.getBlockZ();
        for (Map.Entry<String, List<String>> entry : baseSubmitPoints.entrySet()) {
            String base = entry.getKey();
            List<String> points = entry.getValue();
            if (points.contains(pointKeyX_Z)) {
                handleMobCheck(player, base);
                return;
            }
        }
        String collectorPointKey = to.getWorld().getName() + "_" + to.getBlockX() + "_" + to.getBlockY() + "_" + to.getBlockZ();
        for (Map.Entry<String, List<String>> entry : baseCollectorPoints.entrySet()) {
            String base = entry.getKey();
            List<String> points = entry.getValue();
            if (points.contains(collectorPointKey)) {
                handleMoneyCollection(player, base, collectorPointKey);
                return;
            }
        }
        String lockPointKey = to.getWorld().getName() + "_" + to.getBlockX() + "_" + to.getBlockY() + "_" + to.getBlockZ();
        for (Map.Entry<String, String> entry : baseLockPoints.entrySet()) {
            String base = entry.getKey();
            String lockPoint = entry.getValue();
            if (lockPoint.equals(lockPointKey)) {
                handleLockPoint(player, base);
                return;
            }
        }
    }
    @EventHandler
    public void onEntityDamageByEntityEvent(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        StealingData data = stealingPlayers.get(victim);
        if (data == null || !data.isCarrying) return;
        event.setCancelled(true);
        returnStolenMob(victim, data, attacker);
    }
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity entity = event.getRightClicked();
        if (entity.getScoreboardTags().contains("LUCKY_BLOCK_HITBOX")) {
            Entity rootEntity = null;
            for (Map.Entry<Entity, Entity> entry : luckyBlockHitboxMap.entrySet()) {
                if (entry.getValue().equals(entity)) {
                    rootEntity = entry.getKey();
                    break;
                }
            }
            if (rootEntity != null) {
                entity = rootEntity;
            } else {
                event.setCancelled(true);
                return;
            }
        }
        if (entity.getScoreboardTags().contains("LUCKY_BLOCK") ||
            entity.getScoreboardTags().contains("aj.luckyblock.root") ||
            entity.getScoreboardTags().contains("aj.luckyblock.entity")) {
            if (!entity.getScoreboardTags().contains("aj.luckyblock.root") &&
                !luckyBlockHitboxMap.containsKey(entity)) {
                String commonTag = null;
                for (String tag : entity.getScoreboardTags()) {
                    if (tag.startsWith("base_lb_")) {
                        commonTag = tag;
                        break;
                    }
                }
                if (commonTag != null) {
                    for (Entity e : luckyBlockHitboxMap.keySet()) {
                        if (e.getScoreboardTags().contains(commonTag)) {
                            entity = e;
                            break;
                        }
                    }
                }
            }
        }
        boolean isBaseMob = entityToPointMap.containsKey(entity) ||
                           entity.getScoreboardTags().contains(BASE_MOB_TAG) ||
                           entity.getScoreboardTags().contains("LUCKY_BLOCK");
        if (isBaseMob) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            String base = findBaseByEntity(entity);
            if (base == null && entity.getScoreboardTags().contains("LUCKY_BLOCK")) {
                String mobPoint = entityToPointMap.get(entity);
                if (mobPoint != null) {
                    base = findBaseByMobPoint(mobPoint);
                }
            }
            if (base != null) {
                String owner = bases.get(base);
                if (owner != null && owner.equals(player.getName())) {
                    boolean isLuckyBlock = entity.getScoreboardTags().contains("LUCKY_BLOCK") ||
                                          luckyBlockTags.containsKey(entity);
                    if (isLuckyBlock) {
                        openLuckyBlockMenu(player, entity, base);
                    } else {
                        openSellMenu(player, entity, base);
                    }
                } else {
                    attemptToStealMob(player, entity, base);
                }
            } else {
                getLogger().warning("Не удалось найти базу для моба: " + entity.getType() +
                                  " теги: " + entity.getScoreboardTags());
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (!title.equals("Продажа моба")) return;
        event.setCancelled(true);
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= 27) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        Entity entity = sellMenuEntity.get(player);
        if (rawSlot == 11 && clicked.getType() == Material.LIME_WOOL) {
            player.closeInventory();
            if (entity != null && !entity.isDead()) {
                sellEntity(player, entity);
            } else {
                player.sendMessage(color("&c❌ Моб исчез!"));
                sellMenuEntity.remove(player);
            }
        } else if (rawSlot == 15 && clicked.getType() == Material.RED_WOOL) {
            player.closeInventory();
            player.sendMessage(color("&e⚠ Продажа отменена."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            sellMenuEntity.remove(player);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title.equals("Продажа моба")) {
            event.setCancelled(true);
        }
    }
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title.equals("Продажа моба")) {
            sellMenuEntity.remove(player);
        }
    }
    private void openSellMenu(Player player, Entity entity, String base) {
        sellMenuEntity.put(player, entity);
        MobType type = MobType.fromEntity(entity);
        if (type == null) return;
        Mutation mutation = baseMobMutations.getOrDefault(entity, Mutation.NONE);
        boolean snowy = baseMobSnowy.getOrDefault(entity, false);
        double mutMult = getMobMutationMultiplier(entity);
        double actualIncome = type.baseIncome * mutMult;
        boolean hasMutation = (mutation != Mutation.NONE) || snowy;
        Inventory menu = Bukkit.createInventory(null, 27, color("&6&lПродажа моба"));
        ItemStack grayGlass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta grayMeta = grayGlass.getItemMeta();
        grayMeta.setDisplayName(" ");
        grayGlass.setItemMeta(grayMeta);
        for (int i = 0; i < 27; i++) {
            menu.setItem(i, grayGlass);
        }
        ItemStack info = new ItemStack(type.getCorrectIcon());
        ItemMeta infoMeta = info.getItemMeta();
        String title = "&f&l" + type.name;
        if (hasMutation) {
            String mutPrefix = getMutationDisplayLine(mutation, snowy);
            if (!mutPrefix.isEmpty()) title = mutPrefix + " " + title;
        }
        infoMeta.setDisplayName(color(title));
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Тип: &a" + type.name));
        lore.add(color("&7Редкость: " + type.getRarityDisplay()));
        if (hasMutation) {
            String mutDisplay = "";
            if (mutation != Mutation.NONE) mutDisplay += mutation.format + mutation.displayName;
            if (snowy) {
                if (!mutDisplay.isEmpty()) mutDisplay += " §7+ ";
                mutDisplay += Mutation.SNOWY.format + Mutation.SNOWY.displayName;
            }
            lore.add(color("&7Мутация: " + mutDisplay));
            lore.add(color("&7Множитель: &e×" + String.format("%.1f", mutMult)));
        }
        lore.add(color("&7Доход: &a+" + formatNumber(actualIncome) + "$/сек"));
        lore.add(color(""));
        lore.add(color("&eЦена продажи: &6$" + formatNumber(type.sellPrice)));
        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        menu.setItem(4, info);
        ItemStack sellButton = new ItemStack(Material.LIME_WOOL);
        ItemMeta sellMeta = sellButton.getItemMeta();
        sellMeta.setDisplayName(color("&a&lПРОДАТЬ"));
        sellMeta.setLore(Arrays.asList(
                color("&7Нажмите, чтобы продать"),
                color("&7моба за &6$" + formatNumber(type.sellPrice)),
                color(""),
                color("&a➤ Клик для продажи")
        ));
        sellButton.setItemMeta(sellMeta);
        menu.setItem(11, sellButton);
        ItemStack cancelButton = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancelButton.getItemMeta();
        cancelMeta.setDisplayName(color("&c&lОТМЕНИТЬ"));
        cancelMeta.setLore(Arrays.asList(
                color("&7Нажмите, чтобы отменить"),
                color("&7продажу моба"),
                color(""),
                color("&c➤ Клик для отмены")
        ));
        cancelButton.setItemMeta(cancelMeta);
        menu.setItem(15, cancelButton);
        player.openInventory(menu);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1.2f);
    }
    private void sellEntity(Player player, Entity entity) {
        sellMenuEntity.remove(player);
        if (entity == null || entity.isDead()) {
            player.sendMessage(color("&c❌ Ошибка: моб не найден!"));
            return;
        }
        String base = findBaseByEntity(entity);
        if (base == null) {
            player.sendMessage(color("&c❌ Ошибка: база не найдена!"));
            return;
        }
        String owner = bases.get(base);
        if (owner == null || !owner.equals(player.getName())) {
            player.sendMessage(color("&c❌ Это не ваш моб!"));
            return;
        }
        MobType type = MobType.fromEntity(entity);
        if (type == null) return;
        String mobPoint = entityToPointMap.get(entity);
        if (isMobAuctionListed(entity)) {
            player.sendMessage(color("&c❌ Этот моб выставлен на аукцион! Сначала снимите его с аукциона, чтобы продать."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        String playerName = player.getName();
        if (savedPlayerMobs.containsKey(playerName)) {
            List<SavedMobData> mobs = savedPlayerMobs.get(playerName);
            mobs.removeIf(savedMob ->
                savedMob.base.equals(base) && savedMob.mobPoint.equals(mobPoint));
            if (mobs.isEmpty()) {
                savedPlayerMobs.remove(playerName);
            }
        }
        boolean isLuckyBlock = entity.getScoreboardTags().contains("LUCKY_BLOCK") ||
                              entity.getScoreboardTags().contains("LUCKY_BLOCK_ANIMATED") ||
                              luckyBlockTags.containsKey(entity);
        if (isLuckyBlock) {
            String uniqTag = luckyBlockTags.remove(entity);
            Entity hitbox = luckyBlockHitboxMap.remove(entity);
            if (hitbox != null && hitbox.isValid()) {
                hitbox.remove();
            }
            BukkitRunnable anim = luckyBlockAnimations.remove(entity);
            if (anim != null) {
                try { anim.cancel(); } catch (Exception ignored) {}
            }
            removeMobHologram(entity);
            if (mobPoint != null) {
                String collectorId = entityToCollectorMap.get(mobPoint);
                if (collectorId != null) {
                    removeCollectorHologram(collectorId);
                    collectorToEntityMap.remove(collectorId);
                    entityToCollectorMap.remove(mobPoint);
                    synchronized (moneyLock) {
                        collectorMoney.remove(collectorId);
                        collectorLastUpdate.remove(collectorId);
                    }
                }
                Set<String> occupied = occupiedMobPoints.get(base);
                if (occupied != null) {
                    occupied.remove(mobPoint);
                }
            }
            entityToPointMap.remove(entity);
            mobSpawnTime.remove(entity);
            if (uniqTag != null && entity.getWorld() != null) {
                String dim = entity.getWorld().getKey().toString();
                final String tag = uniqTag;
                Bukkit.getScheduler().runTask(this, () -> {
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            "execute in " + dim + " as @e[tag=" + tag + ",limit=1] " +
                            "run function animated_java:luckyblock/remove");
                    } catch (Exception ignored) {}
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        try {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                "execute in " + dim + " run kill @e[tag=" + tag + "]");
                        } catch (Exception ignored) {}
                    }, 5L);
                });
            }
            World world = entity.getWorld();
            Location entityLoc = entity.getLocation();
            if (world != null) {
                for (Entity nearby : world.getNearbyEntities(entityLoc, 2, 2, 2)) {
                    if (nearby.getScoreboardTags().contains("LUCKY_BLOCK_HITBOX") ||
                        nearby.getScoreboardTags().contains("aj.luckyblock.entity")) {
                        if (uniqTag != null && nearby.getScoreboardTags().contains(uniqTag)) {
                            nearby.remove();
                        }
                    }
                }
            }
            if (!entity.isDead()) {
                entity.remove();
            }
            debugLog("[SELL] Lucky Block полностью удалён при продаже");
        } else if (rotWalkerTags.containsKey(entity) ||
                   entity.getScoreboardTags().contains(TAG_ROT_WALKER_BASE) ||
                   entity.getScoreboardTags().contains("ROT_WALKER_ANIMATED")) {
            String uniqTag = rotWalkerTags.remove(entity);
            Entity hitbox = rotWalkerHitboxMap.remove(entity);
            if (hitbox != null && hitbox.isValid()) hitbox.remove();
            removeMobHologram(entity);
            if (mobPoint != null) {
                String collectorId = entityToCollectorMap.get(mobPoint);
                if (collectorId != null) {
                    removeCollectorHologram(collectorId);
                    collectorToEntityMap.remove(collectorId);
                    entityToCollectorMap.remove(mobPoint);
                    synchronized (moneyLock) {
                        collectorMoney.remove(collectorId);
                        collectorLastUpdate.remove(collectorId);
                    }
                }
                Set<String> occupied = occupiedMobPoints.get(base);
                if (occupied != null) occupied.remove(mobPoint);
            }
            entityToPointMap.remove(entity);
            mobSpawnTime.remove(entity);
            if (uniqTag != null && entity.getWorld() != null) {
                String dim = entity.getWorld().getKey().toString();
                final String tag = uniqTag;
                Bukkit.getScheduler().runTask(this, () -> {
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            "execute in " + dim + " as @e[tag=" + tag + ",limit=1] " +
                            "run function animated_java:rotwalker/remove");
                    } catch (Exception ignored) {}
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        try {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                "execute in " + dim + " run kill @e[tag=" + tag + "]");
                        } catch (Exception ignored) {}
                    }, 5L);
                });
            }
            if (!entity.isDead()) entity.remove();
            debugLog("[SELL] Гнилоход полностью удалён при продаже");
        } else {
            removeMobHologram(entity);
            if (mobPoint != null) {
                String collectorId = entityToCollectorMap.get(mobPoint);
                if (collectorId != null) {
                    removeCollectorHologram(collectorId);
                    collectorToEntityMap.remove(collectorId);
                    entityToCollectorMap.remove(mobPoint);
                    synchronized (moneyLock) {
                        collectorMoney.remove(collectorId);
                        collectorLastUpdate.remove(collectorId);
                    }
                }
                Set<String> occupied = occupiedMobPoints.get(base);
                if (occupied != null) {
                    occupied.remove(mobPoint);
                }
            }
            entityToPointMap.remove(entity);
            mobSpawnTime.remove(entity);
            entity.remove();
        }
        int sellPrice = type.sellPrice;
        if (economy != null) {
            economy.depositPlayer(player, sellPrice);
            player.sendMessage(color("&a✔ Моб продан за &6$" + formatNumber(sellPrice) + "&a! Баланс: &6$" + formatNumber((int)economy.getBalance(player))));
        } else {
            player.sendMessage(color("&a✔ Моб продан за &6$" + formatNumber(sellPrice) + "&a!"));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
        savePlayerMobsToFile(playerName);
        savePlayerMobsInstantly(player.getName());
    }
    private void sendCooldownMessage(Player player, String message, Map<Player, Long> cooldownMap) {
        if (player == null || message == null) return;
        long currentTime = System.currentTimeMillis();
        Long lastMessageTime = cooldownMap.get(player);
        if (lastMessageTime == null || currentTime - lastMessageTime > GENERAL_MESSAGE_COOLDOWN) {
            player.sendMessage(color(message));
            cooldownMap.put(player, currentTime);
        }
    }
    private boolean isPlayerCarryingPurchasedMob(Player player) {
        for (Entity passenger : player.getPassengers()) {
            if (passenger.getScoreboardTags().contains(BASE_MOB_TAG) ||
                entityToPointMap.containsKey(passenger)) {
                return true;
            }
        }
        for (Entity entity : player.getNearbyEntities(3.0, 3.0, 3.0)) {
            if (entity.getLocation().distance(player.getLocation()) < 2.0 &&
                (entity.getScoreboardTags().contains(BASE_MOB_TAG) ||
                 entityToPointMap.containsKey(entity))) {
                return true;
            }
        }
        return false;
    }
    private void attemptToStealMob(Player player, Entity mob, String base) {
        String thiefBase = null;
        for (Map.Entry<String, String> entry : bases.entrySet()) {
            if (entry.getValue().equals(player.getName())) {
                thiefBase = entry.getKey();
                break;
            }
        }
        if (thiefBase == null) {
            sendCooldownMessage(player, "§c❌ У вас нет базы! Воровать некуда.", lastStealMessage);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        List<String> mobPoints = baseMobSpawnPoints.get(thiefBase);
        Set<String> occupied = occupiedMobPoints.get(thiefBase);
        debugLog("[STEAL-DEBUG] База вора: " + thiefBase);
        debugLog("[STEAL-DEBUG] Всего точек: " + (mobPoints != null ? mobPoints.size() : 0));
        debugLog("[STEAL-DEBUG] Занято: " + (occupied != null ? occupied.size() : 0));
        if (!hasFreeMobSlotsForBase(thiefBase)) {
            sendCooldownMessage(player, "§c❌ На вашей базе нет свободных мест! Освободите место перед кражей.", lastStealMessage);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            debugLog("[STEAL-DEBUG] ЗАБЛОКИРОВАНО — нет свободных мест!");
            return;
        }
        debugLog("[STEAL-DEBUG] РАЗРЕШЕНО — есть свободное место");
        String freePoint = findFreeMobPoint(thiefBase);
        if (freePoint == null) {
            sendCooldownMessage(player, "§c❌ На вашей базе нет свободных мест! Освободите место перед кражей.", lastStealMessage);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        StealingData existingSteal = stealingPlayers.get(player);
        if (existingSteal != null && existingSteal.mob != null && existingSteal.mob.equals(mob)) {
            sendCooldownMessage(player, "§cВы уже крадете этого моба!", lastStealMessage);
            return;
        }
        for (StealingData data : stealingPlayers.values()) {
            if (data.mob != null && data.mob.equals(mob)) {
                sendCooldownMessage(player, "§cЭтот моб уже крадется!", lastStealMessage);
                return;
            }
        }
        String owner = bases.get(base);
        if (owner != null && owner.equals(player.getName())) {
            return;
        }
        if (owner != null) {
            OfflinePlayer ownerPlayer = Bukkit.getOfflinePlayer(owner);
            if (ownerPlayer.isOp()) {
                sendCooldownMessage(player, "§c🛡 Кража у администратора невозможна!", lastStealMessage);
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
                return;
            }
        }
        if (player.getScoreboardTags().contains("CARRYING_LUCKY_BLOCK")) {
            sendCooldownMessage(player, "§c❌ Вы уже несёте Лаки-Блок!", lastStealMessage);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            return;
        }
        if (baseLocked.getOrDefault(base, false)) {
            if (!isFriend(owner, player.getName())) {
                sendCooldownMessage(player, "§cБаза заблокирована, нельзя украсть моба!", lastStealMessage);
                return;
            }
        }
        double distance = player.getLocation().distance(mob.getLocation());
        boolean isLuckyBlock = mob.getScoreboardTags().contains("LUCKY_BLOCK") ||
                              luckyBlockTags.containsKey(mob);
        double minDistance = isLuckyBlock ? 0.8 : 0.5;
        if (distance < minDistance) {
            return;
        }
        if (distance > 1.0) {
            if (!hasClearInteractionPath(player, mob, 4.5)) {
                sendCooldownMessage(player, "§c❌ Нельзя красть моба через блоки!", lastStealMessage);
                cancelStealing(player, true);
                return;
            }
        }
        if (distance > 3.5) {
            sendCooldownMessage(player, "§c❌ Вы слишком далеко от моба!", lastStealMessage);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            return;
        }
        boolean carryingOtherMob = false;
        for (Entity passenger : player.getPassengers()) {
            if (passenger.getScoreboardTags().contains(BASE_MOB_TAG) ||
                entityToPointMap.containsKey(passenger)) {
                if (!passenger.equals(mob)) {
                    carryingOtherMob = true;
                    break;
                }
            }
        }
        if (carryingOtherMob) {
            sendCooldownMessage(player, "§c❌ Вы уже несете другого моба!", lastStealMessage);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            return;
        }
        if (existingSteal != null) {
            cancelStealing(player, false);
        }
        if (isLuckyBlock) {
            player.sendTitle(
                color("§e⚡ КРАЖА ЛАКИ-БЛОКА"),
                color("§7Де��житесь рядом 5 секунд!"),
                10, 70, 10
            );
            sendCooldownMessage(player, "§e⚡ Начата кража Лаки-Блока!", lastStealMessage);
        } else {
            player.sendTitle(
                color("§e⚡ КРАЖА МОБА"),
                color("§7Держитесь рядом 5 секунд!"),
                10, 70, 10
            );
            sendCooldownMessage(player, "§e⚡ Начата кража моба!", lastStealMessage);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        player.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, mob.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);
        startStealing(player, mob, base);
    }
    private void startStealing(Player player, Entity mob, String originalBase) {
        Location originalLoc = mob.getLocation().clone();
        player.setSprinting(false);
        player.setFlySpeed(0.1f);
        StealingData data = new StealingData(player, mob, originalBase, originalLoc);
        data.mutation = baseMobMutations.getOrDefault(mob, Mutation.NONE);
        data.snowy = baseMobSnowy.getOrDefault(mob, false);
        stealingPlayers.put(player, data);
        {
            String __apoint = entityToPointMap.get(mob);
            if (__apoint != null && isAuctionListed(originalBase, __apoint)) {
                auctionNotifyRemoved(originalBase, __apoint);
            }
        }
        startStealProgressTask(player, data);
        data.stealTimer = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || mob.isDead()) {
                    cancelStealing(player, true);
                    return;
                }
                data.timeLeft--;
                double distance = player.getLocation().distance(mob.getLocation());
                if (distance > 3.0) {
                    sendCooldownMessage(player, "§c❌ Вы отошли слишком далеко от моба!", lastStealMessage);
                    cancelStealing(player, true);
                    return;
                }
                if (player.isSprinting()) {
                    player.setSprinting(false);
                }
                if (data.timeLeft <= 0) {
                    completeStealing(player, data);
                    this.cancel();
                }
            }
        };
        data.stealTimer.runTaskTimer(this, 0L, 20L);
    }
    private void startStealProgressTask(Player player, StealingData data) {
        BukkitRunnable progressTask = new BukkitRunnable() {
            Hologram progressHologram = null;
            @Override
            public void run() {
                if (!player.isOnline() || !stealingPlayers.containsKey(player)) {
                    if (progressHologram != null) {
                        hologramManager.removeHologram(progressHologram);
                    }
                    this.cancel();
                    return;
                }
                if (player.isSprinting()) {
                    player.setSprinting(false);
                }
                StringBuilder progressBar = new StringBuilder("§a[");
                for (int i = 0; i < STEAL_TIME; i++) {
                    if (i < STEAL_TIME - data.timeLeft) {
                        progressBar.append("█");
                    } else {
                        progressBar.append("§7█");
                    }
                }
                progressBar.append("§a] §e").append(data.timeLeft).append("с");
                Location hologramLoc = player.getLocation().add(0, STEAL_HOLOGRAM_HEIGHT, 0);
                if (progressHologram == null) {
                    TextHologramData hologramData = new TextHologramData(
                        "steal_progress_" + player.getUniqueId() + "_" + System.currentTimeMillis(),
                        hologramLoc
                    );
                    hologramData.setText(List.of(color(progressBar.toString())));
                    hologramData.setScale(new Vector3f(0.8f, 0.8f, 0.8f));
                    hologramData.setBackground(Hologram.TRANSPARENT);
                    hologramData.setSeeThrough(true);
                    hologramData.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                    progressHologram = hologramManager.create(hologramData);
                    hologramManager.addHologram(progressHologram);
                } else {
                    if (progressHologram.getData() instanceof TextHologramData hologramData) {
                        hologramData.setLocation(hologramLoc);
                        hologramData.setText(List.of(color(progressBar.toString())));
                        progressHologram.queueUpdate();
                    }
                }
                if (data.timeLeft <= 2) {
                    player.getWorld().spawnParticle(Particle.FLAME,
                        hologramLoc, 3, 0.2, 0.2, 0.2, 0.01);
                }
            }
            @Override
            public void cancel() {
                if (progressHologram != null) {
                    hologramManager.removeHologram(progressHologram);
                }
                super.cancel();
            }
        };
        progressTask.runTaskTimer(this, 0L, 5L);
        stealProgressTasks.put(player, progressTask);
    }
    private void stopStealProgressTask(Player player) {
        BukkitRunnable task = stealProgressTasks.remove(player);
        if (task != null) {
            task.cancel();
        }
    }
    private void completeStealing(Player player, StealingData data) {
        stopStealProgressTask(player);
        String mobPoint = entityToPointMap.get(data.mob);
        boolean isLuckyBlock = data.mob != null &&
                              (data.mob.getScoreboardTags().contains("LUCKY_BLOCK") ||
                               data.mob.getScoreboardTags().contains("LUCKY_BLOCK_ANIMATED") ||
                               luckyBlockTags.containsKey(data.mob));
        if (isLuckyBlock) {
            World mobWorld = data.mob.getWorld();
            String uniqTag = luckyBlockTags.remove(data.mob);
            Entity hitbox = luckyBlockHitboxMap.remove(data.mob);
            if (hitbox != null && hitbox.isValid()) {
                hitbox.remove();
            }
            if (mobWorld != null && data.originalLocation != null) {
                for (Entity nearby : mobWorld.getNearbyEntities(data.originalLocation, 3, 3, 3)) {
                    if (nearby.getScoreboardTags().contains("LUCKY_BLOCK_HITBOX")) {
                        boolean hasLiveLB = false;
                        for (Entity e : luckyBlockHitboxMap.keySet()) {
                            if (luckyBlockHitboxMap.get(e) != null && luckyBlockHitboxMap.get(e).equals(nearby)) {
                                hasLiveLB = true;
                                break;
                            }
                        }
                        if (!hasLiveLB) {
                            nearby.remove();
                            debugLog("[STEAL] Удалён оставшийся хитбокс: " + nearby.getUniqueId());
                        }
                    }
                }
            }
            BukkitRunnable anim = luckyBlockAnimations.remove(data.mob);
            if (anim != null) {
                try { anim.cancel(); } catch (Exception ignored) {}
            }
            removeMobHologram(data.mob);
            mobSpawnTime.remove(data.mob);
            if (mobPoint != null) {
                String collectorId = entityToCollectorMap.get(mobPoint);
                if (collectorId != null) {
                    data.collectorId = collectorId;
                    collectorToEntityMap.remove(collectorId);
                    Hologram holo = collectorHolograms.remove(collectorId);
                    if (holo != null) hologramManager.removeHologram(holo);
                    synchronized (moneyLock) {
                        collectorMoney.remove(collectorId);
                        collectorLastUpdate.remove(collectorId);
                    }
                    entityToCollectorMap.remove(mobPoint);
                }
            }
            entityToPointMap.remove(data.mob);
            if (uniqTag != null && mobWorld != null) {
                String dim = mobWorld.getKey().toString();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "execute in " + dim + " as @e[tag=" + uniqTag + ",limit=1] " +
                        "run function animated_java:luckyblock/remove");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "execute in " + dim + " run minecraft:kill @e[tag=" + uniqTag + "]");
            }
            if (data.mob != null && !data.mob.isDead()) {
                data.mob.remove();
            }
            data.isCarrying = true;
            ArmorStand carryDisplay = (ArmorStand) player.getWorld().spawnEntity(
                    player.getLocation(), EntityType.ARMOR_STAND);
            carryDisplay.setVisible(false);
            carryDisplay.setSmall(true);
            carryDisplay.setGravity(false);
            carryDisplay.setInvulnerable(true);
            carryDisplay.setCollidable(false);
            carryDisplay.setSilent(true);
            carryDisplay.setMarker(true);
            carryDisplay.setRemoveWhenFarAway(false);
            carryDisplay.setPersistent(false);
            carryDisplay.setBasePlate(false);
            ItemStack spongeHead = new ItemStack(Material.SPONGE);
            ItemMeta spongeMeta = spongeHead.getItemMeta();
            spongeMeta.setDisplayName("§d§l✦ Лаки-Блок ✦");
            spongeHead.setItemMeta(spongeMeta);
            carryDisplay.getEquipment().setHelmet(spongeHead);
            carryDisplay.addScoreboardTag("LUCKY_BLOCK_CARRY_DISPLAY");
            carryDisplay.addScoreboardTag("CARRYING_BY_" + player.getUniqueId());
            player.addPassenger(carryDisplay);
            player.addScoreboardTag("CARRYING_LUCKY_BLOCK");
            String owner = bases.get(data.originalBase);
            if (owner != null && !owner.equals("none")) {
                savePlayerMobsInstantly(owner);
            }
            Player ownerPlayer = (owner != null) ? Bukkit.getPlayer(owner) : null;
            if (ownerPlayer != null && ownerPlayer.isOnline()) {
                ownerPlayer.sendTitle(color("§c⚠ ВНИМАНИЕ"), color("§fУ вас украли Лаки-Блок!"), 10, 60, 10);
                ownerPlayer.playSound(ownerPlayer.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
            }
            player.sendTitle(
                color("§a✔ ЛАКИ-БЛОК УКРАДЕН"),
                color("§7Несите его к своей базе!"),
                10, 60, 10
            );
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation(), 30, 0.5, 1, 0.5, 0.2);
            return;
        }
        removeMobHologram(data.mob);
        mobSpawnTime.remove(data.mob);
        if (mobPoint != null) {
            String collectorId = entityToCollectorMap.get(mobPoint);
            if (collectorId != null) {
                data.collectorId = collectorId;
                collectorToEntityMap.remove(collectorId);
                Hologram holo = collectorHolograms.remove(collectorId);
                if (holo != null) hologramManager.removeHologram(holo);
                synchronized (moneyLock) {
                    collectorMoney.remove(collectorId);
                    collectorLastUpdate.remove(collectorId);
                }
                entityToCollectorMap.remove(mobPoint);
            }
        }
        entityToPointMap.remove(data.mob);
        data.isCarrying = true;
        if (data.mob instanceof LivingEntity living) {
            living.setAI(false);
            living.setInvulnerable(true);
            living.setGravity(false);
            living.setCollidable(false);
            living.setSilent(true);
            if (data.mob instanceof Enderman enderman) {
                enderman.setTarget(null);
                enderman.setCarriedBlock(null);
            }
            if (data.mob instanceof Mob mobEntity) {
                mobEntity.setTarget(null);
                mobEntity.setAware(false);
            }
        }
        data.mob.setSilent(true);
        data.mob.setCustomNameVisible(false);
        data.mob.addScoreboardTag("STOLEN_MOB");
        data.mob.addScoreboardTag("CARRYING_" + player.getUniqueId());
        player.addPassenger(data.mob);
        String owner = bases.get(data.originalBase);
        Player ownerPlayer = Bukkit.getPlayer(owner);
        if (ownerPlayer != null && ownerPlayer.isOnline()) {
            ownerPlayer.sendTitle(color("§c⚠ ВНИМАНИЕ"), color("§fУ вас украли моба!"), 10, 60, 10);
            ownerPlayer.playSound(ownerPlayer.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
        }
        player.sendTitle(
            color("§a✔ МОБ УКРАДЕН"),
            color("§7Несите его к своей базе!"),
            10, 60, 10
        );
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation(), 30, 0.5, 1, 0.5, 0.2);
    }
    private void removeLuckyBlockCarryDisplay(Player player) {
        if (player == null) return;
        player.removeScoreboardTag("CARRYING_LUCKY_BLOCK");
        for (Entity passenger : new ArrayList<>(player.getPassengers())) {
            if (passenger.getScoreboardTags().contains("LUCKY_BLOCK_CARRY_DISPLAY")) {
                player.removePassenger(passenger);
                passenger.remove();
            }
        }
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet != null && helmet.getType() == Material.SPONGE) {
            if (helmet.hasItemMeta()) {
                String name = helmet.getItemMeta().getDisplayName();
                if (name != null && name.contains("Лаки-Блок")) {
                    player.getInventory().setHelmet(null);
                }
            }
        }
    }
    private void checkSubmitZoneForCarrier(StealingData data) {
        Player player = data.player;
        Location loc = player.getLocation();
        String pointKey = loc.getWorld().getName() + "_" + loc.getBlockX() + "_ANY_" + loc.getBlockZ();
        String playerBase = null;
        for (Map.Entry<String, String> entry : bases.entrySet()) {
            if (entry.getValue().equals(player.getName())) {
                playerBase = entry.getKey();
                break;
            }
        }
        if (playerBase == null) {
            sendCooldownMessage(player, "§c❌ У вас нет базы!", lastStealMessage);
            cancelStealing(player, true);
            return;
        }
        String freePoint = findFreeMobPoint(playerBase);
        if (freePoint == null) {
            return;
        }
        List<String> submitPoints = baseSubmitPoints.get(playerBase);
        if (submitPoints != null && submitPoints.contains(pointKey)) {
            handleSuccessfulSubmit(player, data, playerBase);
        }
    }
    private void handleSuccessfulSubmit(Player player, StealingData data, String playerBase) {
        stopStealProgressTask(player);
        boolean isLuckyBlock = player.getScoreboardTags().contains("CARRYING_LUCKY_BLOCK");
        if (isLuckyBlock) {
            removeLuckyBlockCarryDisplay(player);
            String originalMobPoint = findOriginalMobPoint(data.originalBase, data.originalLocation);
            if (originalMobPoint != null) {
                Set<String> origOccupied = occupiedMobPoints.get(data.originalBase);
                if (origOccupied != null) {
                    origOccupied.remove(originalMobPoint);
                    debugLog("[STEAL] Точка " + originalMobPoint + " освобождена при доставке LB");
                }
            }
            String freePoint = findFreeMobPoint(playerBase);
            if (freePoint == null) {
                player.sendMessage(color("§c❌ Нет свободных мест на базе!"));
                player.setWalkSpeed(0.2f);
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                stealingPlayers.remove(player);
                if (data.stealTimer != null) data.stealTimer.cancel();
                return;
            }
            List<String> colPoints = baseCollectorPoints.get(playerBase);
            String freeCol = findFreeCollectorForBase(playerBase, colPoints);
            spawnMobAtPoint(playerBase, freePoint, freeCol, MobType.SPONGE);
            if (data.mutation != Mutation.NONE || data.snowy) {
                final String fPoint = freePoint;
                final Mutation fMut = data.mutation;
                final boolean fSnowy = data.snowy;
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    applyMutationToPoint(playerBase, fPoint, fMut.name(), fSnowy);
                }, 5L);
            }
            player.setWalkSpeed(0.2f);
            player.setSprinting(false);
            player.setFlySpeed(0.2f);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            stealingPlayers.remove(player);
            if (data.stealTimer != null) data.stealTimer.cancel();
            String origOwner = bases.get(data.originalBase);
            if (origOwner != null && !origOwner.equals("none")) {
                savePlayerMobsInstantly(origOwner);
            }
            player.sendTitle(color("§a🎉 УСПЕХ"), color("§dЛаки-Блок доставлен!"), 10, 60, 10);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            savePlayerMobsInstantly(player.getName());
            return;
        }
        if (data.mob == null || data.mob.isDead()) {
            player.sendMessage(color("§c❌ Моб потерян!"));
            player.setWalkSpeed(0.2f);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            stealingPlayers.remove(player);
            if (data.stealTimer != null) data.stealTimer.cancel();
            return;
        }
        player.removePassenger(data.mob);
        data.mob.remove();
        String origBase = data.originalBase;
        Set<String> origOccupied = occupiedMobPoints.get(origBase);
        if (origOccupied != null) {
            String origMobPoint = findOriginalMobPoint(origBase, data.originalLocation);
            if (origMobPoint != null) {
                origOccupied.remove(origMobPoint);
                debugLog("[STEAL] Точка " + origMobPoint + " освобождена при доставке моба");
            }
        }
        String origOwner = bases.get(origBase);
        if (origOwner != null && !origOwner.equals("none")) {
            savePlayerMobsInstantly(origOwner);
        }
        MobType type = MobType.fromEntity(data.mob);
        if (type == null) type = MobType.CHICKEN;
        String freePoint = findFreeMobPoint(playerBase);
        if (freePoint == null) {
            player.sendMessage(color("§c❌ Нет свободных мест на базе!"));
            player.setWalkSpeed(0.2f);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            stealingPlayers.remove(player);
            if (data.stealTimer != null) data.stealTimer.cancel();
            return;
        }
        List<String> colPoints = baseCollectorPoints.get(playerBase);
        String freeCol = findFreeCollectorForBase(playerBase, colPoints);
        spawnMobAtPoint(playerBase, freePoint, freeCol, type);
        if (data.mutation != Mutation.NONE || data.snowy) {
            final String fPoint = freePoint;
            final Mutation fMut = data.mutation;
            final boolean fSnowy = data.snowy;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                applyMutationToPoint(playerBase, fPoint, fMut.name(), fSnowy);
            }, 5L);
        }
        player.setWalkSpeed(0.2f);
        player.setSprinting(false);
        player.setFlySpeed(0.2f);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        stealingPlayers.remove(player);
        if (data.stealTimer != null) data.stealTimer.cancel();
        player.sendTitle(color("§a🎉 УСПЕХ"), color("§7Моб доставлен!"), 10, 60, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f);
        savePlayerMobsInstantly(player.getName());
    }
    private void returnStolenMob(Player victim, StealingData data, Player attacker) {
        if (victim == null || data == null || attacker == null) return;
        if (data.mob == null || data.mob.isDead()) {
            getLogger().warning("Попытка вернуть мёртвого или null моба!");
            cancelStealing(victim, true);
            return;
        }
        debugLog("Возврат моба: " + data.mob.getType() + " на базу " + data.originalBase);
        victim.removePassenger(data.mob);
        data.mob.removeScoreboardTag("STOLEN_MOB");
        data.mob.removeScoreboardTag("CARRYING_" + victim.getUniqueId());
        victim.setWalkSpeed(0.2f);
        victim.setSprinting(false);
        victim.setFlySpeed(0.2f);
        victim.removePotionEffect(PotionEffectType.SLOWNESS);
        stopStealProgressTask(victim);
        stealingPlayers.remove(victim);
        if (data.stealTimer != null) {
            data.stealTimer.cancel();
        }
        Location returnLoc = data.originalLocation.clone();
        if (data.mob instanceof Flying || data.mob instanceof Phantom ||
            data.mob instanceof Ghast || data.mob instanceof Bee ||
            data.mob instanceof Bat || data.mob instanceof Vex) {
            returnLoc.setY(returnLoc.getY() + 0.5);
        }
        data.mob.teleport(returnLoc);
        if (data.mob instanceof LivingEntity living) {
            living.setAI(false);
            living.setInvulnerable(true);
            living.setGravity(false);
            living.setCollidable(false);
            living.setSilent(true);
            living.setRemoveWhenFarAway(false);
            if (data.mob instanceof Enderman enderman) {
                enderman.setTarget(null);
                enderman.setCarriedBlock(null);
                data.mob.addScoreboardTag("ENDERMAN_NO_TELEPORT");
            }
            if (living instanceof Mob mobEntity) {
                mobEntity.setTarget(null);
                mobEntity.setAware(false);
            }
        }
        data.mob.setSilent(true);
        data.mob.setCustomNameVisible(false);
        data.mob.setPersistent(true);
        data.mob.addScoreboardTag(BASE_MOB_TAG);
        String mobPoint = findOriginalMobPoint(data.originalBase, data.originalLocation);
        if (mobPoint == null) {
            getLogger().warning("Не удалось найти точку для возврата моба! Используем ближайшую свободную.");
            mobPoint = findFreeMobPoint(data.originalBase);
            if (mobPoint == null) {
                getLogger().severe("Нет свободных точек на базе " + data.originalBase + "!");
                data.mob.remove();
                sendCooldownMessage(victim, "§c❌ Моб потерян - нет свободных мест!", lastStealMessage);
                sendCooldownMessage(attacker, "§e⚠ Моб уничтожен - нет свободных мест на базе!", lastStealMessage);
                return;
            }
            Location newLoc = getLocationFromPoint(mobPoint);
            if (newLoc != null) {
                data.mob.teleport(newLoc);
            }
        }
        entityToPointMap.put(data.mob, mobPoint);
        mobSpawnTime.put(data.mob, System.currentTimeMillis());
        Set<String> occupied = occupiedMobPoints.get(data.originalBase);
        if (occupied != null) {
            occupied.add(mobPoint);
        }
        MobType type = MobType.fromEntity(data.mob);
        if (type != null) {
            createMobHologram(data.mob, type);
        }
        String collectorId = data.collectorId;
        if (collectorId != null) {
            collectorToEntityMap.put(collectorId, mobPoint);
            entityToCollectorMap.put(mobPoint, collectorId);
            String[] parts = collectorId.split("_", 2);
            if (parts.length == 2) {
                String baseFromCollector = parts[0];
                String collectorPoint = parts[1];
                createCollectorHologramForMob(baseFromCollector, mobPoint, collectorPoint);
            }
            synchronized (moneyLock) {
                collectorMoney.put(collectorId, 0.0);
                collectorLastUpdate.put(collectorId, System.currentTimeMillis());
            }
        } else {
            List<String> collectorPoints = baseCollectorPoints.get(data.originalBase);
            if (collectorPoints != null && !collectorPoints.isEmpty()) {
                String collectorPoint = findCollectorForMobPoint(data.originalBase, mobPoint);
                if (collectorPoint != null) {
                    collectorId = data.originalBase + "_" + collectorPoint;
                    collectorToEntityMap.put(collectorId, mobPoint);
                    entityToCollectorMap.put(mobPoint, collectorId);
                    synchronized (moneyLock) {
                        collectorMoney.put(collectorId, 0.0);
                        collectorLastUpdate.put(collectorId, System.currentTimeMillis());
                    }
                    createCollectorHologramForMob(data.originalBase, mobPoint, collectorPoint);
                }
            }
        }
        String owner = bases.get(data.originalBase);
        Player ownerPlayer = Bukkit.getPlayer(owner);
        if (ownerPlayer != null && ownerPlayer.isOnline()) {
            ownerPlayer.sendTitle(
                color("§a✅ МОБ ВОЗВРАЩЕН"),
                color("§7Игрок вернул вашего моба!"),
                10, 60, 10
            );
            sendCooldownMessage(ownerPlayer, "§a✅ Игрок §e" + attacker.getName() + "§a вернул вашего моба!", lastLockMessage);
            ownerPlayer.playSound(ownerPlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
        sendCooldownMessage(victim, "§c❌ Моб отбит игроком §e" + attacker.getName() + "§c!", lastStealMessage);
        sendCooldownMessage(attacker, "§a✔ Вы вернули моб на базу!", lastStealMessage);
        victim.playSound(victim.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        data.originalLocation.getWorld().spawnParticle(Particle.HEART,
            data.originalLocation, 10, 0.5, 0.5, 0.5, 0.1);
        debugLog("Моб успешно возвращен на точку " + mobPoint);
    }
 private void loadFriends() {
     friendsFile = new File(getDataFolder(), "friends.yml");
     if (!friendsFile.exists()) {
         try {
             friendsFile.createNewFile();
             debugLog("Создан файл friends.yml");
         } catch (IOException e) {
             getLogger().severe("Не удалось создать friends.yml: " + e.getMessage());
             return;
         }
     }
     friendsConfig = YamlConfiguration.loadConfiguration(friendsFile);
     if (!friendsConfig.contains("friends")) {
         getLogger().info("Нет сохранённых друзей в friends.yml");
         return;
     }
     var section = friendsConfig.getConfigurationSection("friends");
     if (section == null) return;
     int totalLoaded = 0;
     for (String playerName : section.getKeys(false)) {
         List<String> friendsList = friendsConfig.getStringList("friends." + playerName);
         if (friendsList != null && !friendsList.isEmpty()) {
             playerFriends.put(playerName, new HashSet<>(friendsList));
             totalLoaded += friendsList.size();
         }
     }
     getLogger().info("Загружено " + totalLoaded + " связей дружбы для " + playerFriends.size() + " игроков");
 }
private void initRebirthsFile() {
  rebirthsFile = new File(getDataFolder(), "rebirths.yml");
  if (!rebirthsFile.exists()) {
      try {
          rebirthsFile.getParentFile().mkdirs();
          rebirthsFile.createNewFile();
      } catch (IOException e) {
          getLogger().severe("Не удалось создать rebirths.yml: " + e.getMessage());
      }
  }
  rebirthsConfig = YamlConfiguration.loadConfiguration(rebirthsFile);
  if (!rebirthsConfig.isConfigurationSection("rebirths")) {
      rebirthsConfig.createSection("rebirths");
      saveRebirthsFile();
  }
}
private void saveRebirthsFile() {
  if (rebirthsConfig == null || rebirthsFile == null) return;
  try {
      saveConfigAsync(rebirthsConfig, rebirthsFile);
  } catch (IOException e) {
      getLogger().severe("Ошибка сохранения rebirths.yml: " + e.getMessage());
  }
}
private void reloadRebirthsFile() {
  if (rebirthsFile == null) {
      initRebirthsFile();
      return;
  }
  rebirthsConfig = YamlConfiguration.loadConfiguration(rebirthsFile);
  if (!rebirthsConfig.isConfigurationSection("rebirths")) {
      rebirthsConfig.createSection("rebirths");
      saveRebirthsFile();
  }
}
private void migrateRebirthsFromConfig() {
	if (rebirthsConfig == null) initRebirthsFile();
  FileConfiguration cfg = getConfig();
  if (!cfg.isConfigurationSection("rebirths")) return;
  ConfigurationSection sec = cfg.getConfigurationSection("rebirths");
  if (sec == null) return;
  int moved = 0;
  for (String uuidStr : sec.getKeys(false)) {
      int count = cfg.getInt("rebirths." + uuidStr + ".count", 0);
      long last = cfg.getLong("rebirths." + uuidStr + ".last", 0L);
      rebirthsConfig.set("rebirths." + uuidStr + ".count", count);
      rebirthsConfig.set("rebirths." + uuidStr + ".last", last);
      moved++;
  }
  cfg.set("rebirths", null);
  saveConfig();
  saveRebirthsFile();
  getLogger().info("✅ Rebirths перенесены из config.yml в rebirths.yml: " + moved);
}
 private void saveFriends() {
     if (friendsConfig == null || friendsFile == null) return;
     friendsConfig.set("friends", null);
     for (Map.Entry<String, Set<String>> entry : playerFriends.entrySet()) {
         String playerName = entry.getKey();
         Set<String> friends = entry.getValue();
         if (friends != null && !friends.isEmpty()) {
             friendsConfig.set("friends." + playerName, new ArrayList<>(friends));
         }
     }
     try {
         saveConfigAsync(friendsConfig, friendsFile);
     } catch (IOException e) {
         getLogger().severe("Ошибка сохранения friends.yml: " + e.getMessage());
     }
 }
 private boolean addFriend(String playerName, String friendName) {
     if (playerName.equalsIgnoreCase(friendName)) {
         return false;
     }
     Set<String> friends = playerFriends.computeIfAbsent(playerName, k -> new HashSet<>());
     for (String existingFriend : friends) {
         if (existingFriend.equalsIgnoreCase(friendName)) {
             return false;
         }
     }
     friends.add(friendName);
     saveFriends();
     return true;
 }
 private boolean removeFriend(String playerName, String friendName) {
     Set<String> friends = playerFriends.get(playerName);
     if (friends == null || friends.isEmpty()) {
         return false;
     }
     String toRemove = null;
     for (String existingFriend : friends) {
         if (existingFriend.equalsIgnoreCase(friendName)) {
             toRemove = existingFriend;
             break;
         }
     }
     if (toRemove != null) {
         friends.remove(toRemove);
         if (friends.isEmpty()) {
             playerFriends.remove(playerName);
         }
         saveFriends();
         return true;
     }
     return false;
 }
@EventHandler(priority = EventPriority.HIGHEST)
public void onPlayerDamageByBaseMob(EntityDamageByEntityEvent event) {
  if (!(event.getEntity() instanceof Player player)) return;
  Entity damager = event.getDamager();
  if (isBaseMob(damager)) {
      event.setCancelled(true);
      return;
  }
  if (damager instanceof Projectile projectile) {
      if (projectile.getShooter() instanceof Entity shooter) {
          if (isBaseMob(shooter)) {
              event.setCancelled(true);
              return;
          }
      }
  }
  if (damager instanceof AreaEffectCloud cloud) {
      if (cloud.getSource() instanceof Entity source) {
          if (isBaseMob(source)) {
              event.setCancelled(true);
              return;
          }
      }
  }
}
@EventHandler(priority = EventPriority.HIGHEST)
public void onPlayerDamage(EntityDamageEvent event) {
  if (!(event.getEntity() instanceof Player player)) return;
  if (event.getCause() == EntityDamageEvent.DamageCause.MAGIC ||
      event.getCause() == EntityDamageEvent.DamageCause.THORNS) {
      for (Entity nearby : player.getNearbyEntities(20, 20, 20)) {
          if (isBaseMob(nearby)) {
              if (nearby instanceof Guardian || nearby instanceof ElderGuardian) {
                  event.setCancelled(true);
                  return;
              }
          }
      }
  }
}
@EventHandler(priority = EventPriority.HIGHEST)
public void onPotionEffect(EntityPotionEffectEvent event) {
  if (!(event.getEntity() instanceof Player player)) return;
  if (event.getNewEffect() != null &&
      event.getNewEffect().getType().equals(PotionEffectType.MINING_FATIGUE)) {
      for (Entity nearby : player.getNearbyEntities(50, 50, 50)) {
          if (isBaseMob(nearby) && nearby instanceof ElderGuardian) {
              event.setCancelled(true);
              player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
              return;
          }
      }
  }
}
private boolean isBaseMob(Entity entity) {
  if (entity == null) return false;
  if (entity.getScoreboardTags().contains(BASE_MOB_TAG)) return true;
  if (entity.getScoreboardTags().contains("BASE_MOB_PERSISTENT")) return true;
  if (entity.getScoreboardTags().contains("NO_DESPAWN")) return true;
  if (entityToPointMap.containsKey(entity)) return true;
  for (String tag : entity.getScoreboardTags()) {
      if (tag.startsWith(MOB_TAG_PREFIX)) return true;
  }
  return false;
}
 private boolean isFriend(String ownerName, String playerName) {
     Set<String> friends = playerFriends.get(ownerName);
     if (friends == null || friends.isEmpty()) {
         return false;
     }
     for (String friend : friends) {
         if (friend.equalsIgnoreCase(playerName)) {
             return true;
         }
     }
     return false;
 }
 private Set<String> getFriends(String playerName) {
     return playerFriends.getOrDefault(playerName, new HashSet<>());
 }
 private void registerFriendCommand() {
     PluginCommand command = getCommand("friend");
     if (command != null) {
         command.setExecutor((sender, cmd, label, args) -> {
             if (!(sender instanceof Player player)) {
                 sender.sendMessage("§cТолько для игроков!");
                 return true;
             }
             if (args.length == 0) {
                 showFriendHelp(player);
                 return true;
             }
             String subCommand = args[0].toLowerCase();
             switch (subCommand) {
                 case "add" -> {
                     if (args.length < 2) {
                         player.sendMessage(color("&c❌ Использование: /friend add <ник>"));
                         return true;
                     }
                     handleFriendAdd(player, args[1]);
                 }
                 case "remove", "delete", "del" -> {
                     if (args.length < 2) {
                         player.sendMessage(color("&c❌ Использование: /friend remove <ник>"));
                         return true;
                     }
                     handleFriendRemove(player, args[1]);
                 }
                 case "list" -> handleFriendList(player);
                 case "help" -> showFriendHelp(player);
                 default -> {
                     player.sendMessage(color("&c❌ Неизвестная команда! Используйте /friend help"));
                 }
             }
             return true;
         });
         command.setTabCompleter((sender, cmd, alias, args) -> {
             List<String> completions = new ArrayList<>();
             if (args.length == 1) {
                 completions.addAll(Arrays.asList("add", "remove", "list", "help"));
                 return completions.stream()
                     .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                     .collect(Collectors.toList());
             }
             if (args.length == 2) {
                 if (args[0].equalsIgnoreCase("add")) {
                     for (Player p : Bukkit.getOnlinePlayers()) {
                         if (!p.getName().equalsIgnoreCase(sender.getName())) {
                             completions.add(p.getName());
                         }
                     }
                 } else if (args[0].equalsIgnoreCase("remove") ||
                           args[0].equalsIgnoreCase("delete") ||
                           args[0].equalsIgnoreCase("del")) {
                     if (sender instanceof Player player) {
                         completions.addAll(getFriends(player.getName()));
                     }
                 }
                 return completions.stream()
                     .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                     .collect(Collectors.toList());
             }
             return completions;
         });
     }
 }
 private void showFriendHelp(Player player) {
     player.sendMessage(color("&6&l══════ ДРУЗЬЯ ══════"));
     player.sendMessage(color("&e/friend add <ник> &7- Добавить друга"));
     player.sendMessage(color("&e/friend remove <ник> &7- Удалить друга"));
     player.sendMessage(color("&e/friend list &7- Список друзей"));
     player.sendMessage(color(""));
     player.sendMessage(color("&7Друзья могут проходить через"));
     player.sendMessage(color("&7вашу базу когда она заблокирована!"));
     player.sendMessage(color("&6&l═══════════════════"));
 }
 private void handleFriendAdd(Player player, String friendName) {
     String playerName = player.getName();
     if (playerName.equalsIgnoreCase(friendName)) {
         player.sendMessage(color("&c❌ Вы не можете добавить себя в друзья!"));
         player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
         return;
     }
     @SuppressWarnings("deprecation")
     OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(friendName);
     if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
         player.sendMessage(color("&c❌ Игрок &e" + friendName + " &cникогда не был на сервере!"));
         player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
         return;
     }
     String correctName = targetPlayer.getName();
     if (correctName == null) correctName = friendName;
     Set<String> currentFriends = getFriends(playerName);
     if (currentFriends.size() >= 10) {
         player.sendMessage(color("&c❌ У вас максимум друзей (10)! Удалите кого-нибудь."));
         player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
         return;
     }
     if (addFriend(playerName, correctName)) {
         player.sendMessage(color("&a✔ Игрок &e" + correctName + " &aдобавлен в друзья!"));
         player.sendMessage(color("&7Теперь он может проходить через вашу базу."));
         player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
         Player friendPlayer = Bukkit.getPlayer(correctName);
         if (friendPlayer != null && friendPlayer.isOnline()) {
             friendPlayer.sendMessage(color("&a✔ Игрок &e" + playerName + " &aдобавил вас в друзья!"));
             friendPlayer.sendMessage(color("&7Теперь вы можете проходить через его базу."));
             friendPlayer.playSound(friendPlayer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
         }
     } else {
         player.sendMessage(color("&c❌ Игрок &e" + correctName + " &cуже в вашем списке друзей!"));
         player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
     }
 }
 private void handleFriendRemove(Player player, String friendName) {
     String playerName = player.getName();
     if (removeFriend(playerName, friendName)) {
         player.sendMessage(color("&a✔ Игрок &e" + friendName + " &aудалён из друзей!"));
         player.sendMessage(color("&7Теперь он не может проходить через вашу базу."));
         player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.5f);
         Player friendPlayer = Bukkit.getPlayer(friendName);
         if (friendPlayer != null && friendPlayer.isOnline()) {
             friendPlayer.sendMessage(color("&c❌ Игрок &e" + playerName + " &cудалил вас из друзей!"));
             friendPlayer.playSound(friendPlayer.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
         }
     } else {
         player.sendMessage(color("&c❌ Игрок &e" + friendName + " &cне найден в вашем списке друзей!"));
         player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
     }
 }
 private void handleFriendList(Player player) {
     String playerName = player.getName();
     Set<String> friends = getFriends(playerName);
     player.sendMessage(color("&6&l══════ ВАШИ ДРУЗЬЯ ══════"));
     if (friends.isEmpty()) {
         player.sendMessage(color("&7У вас пока нет друзей."));
         player.sendMessage(color("&7Используйте &e/friend add <ник> &7чтобы добавить!"));
     } else {
         player.sendMessage(color("&7Всего друзей: &e" + friends.size() + "/10"));
         player.sendMessage(color(""));
         int index = 1;
         for (String friendName : friends) {
             Player friendPlayer = Bukkit.getPlayer(friendName);
             boolean isOnline = friendPlayer != null && friendPlayer.isOnline();
             String status = isOnline ? "&a●" : "&c●";
             player.sendMessage(color("&e" + index + ". " + status + " &f" + friendName));
             index++;
         }
     }
     player.sendMessage(color("&6&l═══════════════════════"));
 }
    private String findOriginalMobPoint(String base, Location location) {
        if (base == null || location == null) return null;
        List<String> mobPoints = baseMobSpawnPoints.get(base);
        if (mobPoints == null) return null;
        for (String mobPoint : mobPoints) {
            String[] s = mobPoint.split("_");
            if (s.length != 4) continue;
            try {
                World world = Bukkit.getWorld(s[0]);
                if (world == null || !world.equals(location.getWorld())) continue;
                int x = Integer.parseInt(s[1]);
                int y = Integer.parseInt(s[2]);
                int z = Integer.parseInt(s[3]);
                if (Math.abs(x - location.getBlockX()) <= 1 &&
                    Math.abs(y - location.getBlockY()) <= 1 &&
                    Math.abs(z - location.getBlockZ()) <= 1) {
                    return mobPoint;
                }
            } catch (NumberFormatException e) {
                continue;
            }
        }
        return null;
    }
    private String findCollectorForMobPoint(String base, String mobPoint) {
        if (base == null || mobPoint == null) return null;
        List<String> collectorPoints = baseCollectorPoints.get(base);
        if (collectorPoints == null) return null;
        for (String collectorPoint : collectorPoints) {
            String collectorId = base + "_" + collectorPoint;
            if (!collectorToEntityMap.containsKey(collectorId)) {
                return collectorPoint;
            }
        }
        return collectorPoints.isEmpty() ? null : collectorPoints.get(0);
    }
    public String findFreeMobPoint(String base) {
        List<String> mobPoints = baseMobSpawnPoints.get(base);
        Set<String> occupied = occupiedMobPoints.get(base);
        if (mobPoints == null) {
            debugLog("findFreeMobPoint: mobPoints = null для базы " + base);
            return null;
        }
        if (occupied == null) {
            debugLog("findFreeMobPoint: occupied = null для базы " + base + ", создаём новый набор");
            occupied = new HashSet<>();
            occupiedMobPoints.put(base, occupied);
        }
        debugLog("findFreeMobPoint: база " + base + " | всего точек: " + mobPoints.size() + " | занято: " + occupied.size());
        for (String mobPoint : mobPoints) {
            if (!occupied.contains(mobPoint)) {
                boolean actuallyOccupied = false;
                for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
                    if (mobPoint.equals(entry.getValue())) {
                        Entity mob = entry.getKey();
                        if (mob != null && !mob.isDead() && mob.isValid()) {
                            actuallyOccupied = true;
                            if (!occupied.contains(mobPoint)) {
                                getLogger().warning("Точка " + mobPoint + " занята мобом но не отмечена! Исправляем.");
                                occupied.add(mobPoint);
                            }
                            break;
                        }
                    }
                }
                if (!actuallyOccupied) {
                    debugLog("findFreeMobPoint: найдена свободная точка " + mobPoint);
                    return mobPoint;
                }
            }
        }
        debugLog("findFreeMobPoint: все " + mobPoints.size() + " точек заняты на базе " + base);
        return null;
    }
    private void registerClearOccupiedCommand() {
        PluginCommand command = getCommand("clearoccupied");
        if (command != null) {
            command.setExecutor((sender, cmd, label, args) -> {
                if (!sender.hasPermission("brainrotbases.admin")) {
                    sender.sendMessage("§cНет прав!");
                    return true;
                }
                String targetBase = args.length > 0 ? args[0] : null;
                if (targetBase != null) {
                    Set<String> occupied = occupiedMobPoints.get(targetBase);
                    if (occupied != null) {
                        int before = occupied.size();
                        Set<String> toRemove = new HashSet<>();
                        for (String point : occupied) {
                            boolean hasMob = false;
                            for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
                                if (point.equals(entry.getValue())) {
                                    Entity mob = entry.getKey();
                                    if (mob != null && !mob.isDead() && mob.isValid()) {
                                        hasMob = true;
                                        break;
                                    }
                                }
                            }
                            if (!hasMob) {
                                toRemove.add(point);
                            }
                        }
                        occupied.removeAll(toRemove);
                        sender.sendMessage("§aБаза " + targetBase + ": очищено " + toRemove.size() + " точек (было " + before + ", стало " + occupied.size() + ")");
                    } else {
                        sender.sendMessage("§cБаза " + targetBase + " не найдена!");
                    }
                } else {
                    int total = 0;
                    for (String base : occupiedMobPoints.keySet()) {
                        Set<String> occupied = occupiedMobPoints.get(base);
                        if (occupied != null) {
                            Set<String> toRemove = new HashSet<>();
                            for (String point : occupied) {
                                boolean hasMob = false;
                                for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
                                    if (point.equals(entry.getValue())) {
                                        Entity mob = entry.getKey();
                                        if (mob != null && !mob.isDead() && mob.isValid()) {
                                            hasMob = true;
                                            break;
                                        }
                                    }
                                }
                                if (!hasMob) {
                                    toRemove.add(point);
                                }
                            }
                            occupied.removeAll(toRemove);
                            total += toRemove.size();
                        }
                    }
                    sender.sendMessage("§aОчищено " + total + " некорректных точек на всех базах");
                }
                return true;
            });
        }
    }
    private String findFreeCollectorForBase(String base, List<String> collectorPoints) {
        if (base == null || collectorPoints == null || collectorPoints.isEmpty()) {
            return null;
        }
        List<String> availableCollectors = new ArrayList<>(collectorPoints);
        for (String collectorPoint : new ArrayList<>(availableCollectors)) {
            String collectorId = base + "_" + collectorPoint;
            String mobPointForThisCollector = collectorToEntityMap.get(collectorId);
            if (mobPointForThisCollector != null) {
                boolean hasRealMob = false;
                for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
                    if (mobPointForThisCollector.equals(entry.getValue())) {
                        Entity mob = entry.getKey();
                        if (mob != null && !mob.isDead()) {
                            hasRealMob = true;
                            break;
                        }
                    }
                }
                if (hasRealMob) {
                    availableCollectors.remove(collectorPoint);
                    debugLog("Коллектор " + collectorPoint + " занят мобом на точке " + mobPointForThisCollector);
                } else {
                    getLogger().warning("Коллектор " + collectorPoint + " имеет недействительную связь. Очищаем.");
                    removeCollectorHologram(collectorId);
                    availableCollectors.add(collectorPoint);
                }
            }
        }
        if (!availableCollectors.isEmpty()) {
            String freeCollector = availableCollectors.get(0);
            debugLog("Найден свободный коллектор для базы " + base + ": " + freeCollector);
            return freeCollector;
        }
        getLogger().warning("Нет свободных коллекторов на базе " + base);
        return null;
    }
    private void cancelStealing(Player player, boolean notify) {
        StealingData data = stealingPlayers.remove(player);
        if (data == null) return;
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.2f);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        if (data.stealTimer != null) {
            data.stealTimer.cancel();
        }
        stopStealProgressTask(player);
        boolean isLuckyBlock = player.getScoreboardTags().contains("CARRYING_LUCKY_BLOCK");
        if (isLuckyBlock) {
        	removeLuckyBlockCarryDisplay(player);;
            if (data.isCarrying) {
                String mobPoint = findOriginalMobPoint(data.originalBase, data.originalLocation);
                if (mobPoint == null) {
                    mobPoint = findFreeMobPoint(data.originalBase);
                }
                if (mobPoint != null) {
                    List<String> collectorPoints = baseCollectorPoints.get(data.originalBase);
                    String collectorPoint = null;
                    if (data.collectorId != null) {
                        String[] parts = data.collectorId.split("_", 2);
                        if (parts.length == 2) collectorPoint = parts[1];
                    }
                    if (collectorPoint == null && collectorPoints != null) {
                        collectorPoint = findFreeCollectorForBase(data.originalBase, collectorPoints);
                    }
                    spawnMobAtPoint(data.originalBase, mobPoint, collectorPoint, MobType.SPONGE);
                    debugLog("Lucky Block возвращён на базу " + data.originalBase);
                }
            }
            if (notify) {
                sendCooldownMessage(player, "§c❌ Кража Лаки-Блока отменена!", lastStealMessage);
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 0.5f, 1f);
            }
            return;
        }
        if (data.isCarrying && data.mob != null && !data.mob.isDead()) {
            debugLog("Отмена кражи - возврат моба на место");
            player.removePassenger(data.mob);
            data.mob.removeScoreboardTag("STOLEN_MOB");
            data.mob.removeScoreboardTag("CARRYING_" + player.getUniqueId());
            data.mob.teleport(data.originalLocation);
            if (data.mob instanceof LivingEntity living) {
                living.setAI(false);
                living.setInvulnerable(true);
                living.setGravity(false);
                living.setCollidable(false);
                living.setSilent(true);
                if (data.mob instanceof Enderman enderman) {
                    enderman.setTarget(null);
                    enderman.setCarriedBlock(null);
                }
                if (living instanceof Mob mobEntity) {
                    mobEntity.setTarget(null);
                    mobEntity.setAware(false);
                }
            }
            data.mob.setSilent(true);
            data.mob.setCustomNameVisible(false);
            data.mob.setPersistent(true);
            data.mob.addScoreboardTag(BASE_MOB_TAG);
            MobType type = MobType.fromEntity(data.mob);
            if (type != null) {
                createMobHologram(data.mob, type);
            }
            mobSpawnTime.put(data.mob, System.currentTimeMillis());
            String mobPoint = findOriginalMobPoint(data.originalBase, data.originalLocation);
            if (mobPoint != null) {
                entityToPointMap.put(data.mob, mobPoint);
                Set<String> occupied = occupiedMobPoints.get(data.originalBase);
                if (occupied != null) {
                    occupied.add(mobPoint);
                }
                if (data.collectorId != null) {
                    collectorToEntityMap.put(data.collectorId, mobPoint);
                    entityToCollectorMap.put(mobPoint, data.collectorId);
                    String[] parts = data.collectorId.split("_", 2);
                    if (parts.length == 2) {
                        createCollectorHologramForMob(parts[0], mobPoint, parts[1]);
                    }
                    synchronized (moneyLock) {
                        collectorMoney.put(data.collectorId, 0.0);
                        collectorLastUpdate.put(data.collectorId, System.currentTimeMillis());
                    }
                } else {
                    List<String> collectorPoints = baseCollectorPoints.get(data.originalBase);
                    if (collectorPoints != null && !collectorPoints.isEmpty()) {
                        String collectorPoint = findCollectorForMobPoint(data.originalBase, mobPoint);
                        if (collectorPoint != null) {
                            String collectorId = data.originalBase + "_" + collectorPoint;
                            collectorToEntityMap.put(collectorId, mobPoint);
                            entityToCollectorMap.put(mobPoint, collectorId);
                            synchronized (moneyLock) {
                                collectorMoney.put(collectorId, 0.0);
                                collectorLastUpdate.put(collectorId, System.currentTimeMillis());
                            }
                            createCollectorHologramForMob(data.originalBase, mobPoint, collectorPoint);
                        }
                    }
                }
            }
        }
        if (notify) {
            sendCooldownMessage(player, "§c❌ Кража отменена!", lastStealMessage);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 0.5f, 1f);
        }
    }
    private String findBaseByMobPoint(String mobPoint) {
        if (mobPoint == null) return null;
        for (Map.Entry<String, List<String>> entry : baseMobSpawnPoints.entrySet()) {
            if (entry.getValue().contains(mobPoint)) {
                return entry.getKey();
            }
        }
        return null;
    }
    private void cleanupCollectorLinksForBase(String base) {
        if (base == null) return;
        List<String> collectorsToRemove = new ArrayList<>();
        synchronized (moneyLock) {
            for (String collectorId : new ArrayList<>(collectorToEntityMap.keySet())) {
                if (collectorId.startsWith(base + "_")) {
                    collectorsToRemove.add(collectorId);
                }
            }
        }
        for (String collectorId : collectorsToRemove) {
            removeCollectorHologram(collectorId);
            debugLog("Очищен коллектор при восстановлении: " + collectorId);
        }
        List<String> mobPoints = baseMobSpawnPoints.get(base);
        if (mobPoints != null) {
            for (String mobPoint : new ArrayList<>(entityToCollectorMap.keySet())) {
                if (mobPoints.contains(mobPoint)) {
                    entityToCollectorMap.remove(mobPoint);
                }
            }
        }
    }
    private void restorePlayerMobs(String playerName) {
        List<SavedMobData> savedMobs = savedPlayerMobs.get(playerName);
        if (savedMobs == null || savedMobs.isEmpty()) {
            getLogger().info("Нет сохраненных мобов для " + playerName);
            return;
        }
        String playerBase = null;
        for (Map.Entry<String, String> entry : bases.entrySet()) {
            if (entry.getValue().equals(playerName)) {
                playerBase = entry.getKey();
                break;
            }
        }
        if (playerBase == null) return;
        List<String> validMobPoints = baseMobSpawnPoints.get(playerBase);
        if (validMobPoints == null || validMobPoints.isEmpty()) return;
        Set<String> occupied = occupiedMobPoints.computeIfAbsent(playerBase, k -> new HashSet<>());
        occupied.clear();
        List<String[]> pendingMutations = new ArrayList<>();
        for (SavedMobData savedMob : savedMobs) {
            String targetMobPoint = savedMob.mobPoint;
            String targetCollectorPoint = savedMob.collectorPoint;
            MobType mobType = savedMob.mobType;
            if (!validMobPoints.contains(targetMobPoint)) {
                targetMobPoint = null;
                for (String point : validMobPoints) {
                    if (!occupied.contains(point)) {
                        targetMobPoint = point;
                        break;
                    }
                }
                if (targetMobPoint == null) continue;
                List<String> collectorPoints = baseCollectorPoints.get(playerBase);
                targetCollectorPoint = findFreeCollectorForBase(playerBase, collectorPoints);
            }
            if (occupied.contains(targetMobPoint)) continue;
            final String fMut = savedMob.mutation;
            final boolean fSnowy = savedMob.snowy;
            final String fBase = playerBase;
            final String fPoint = targetMobPoint;
            if (mobType.isLuckyBlock()) {
                Location lbLoc = getLocationFromPoint(targetMobPoint);
                if (lbLoc != null) {
                    ensureChunkLoaded(lbLoc);
                    final String fcp = targetCollectorPoint;
                    final long timer = savedMob.luckyBlockRemainingMs;
                    final boolean ready = savedMob.luckyBlockReady;
                    spawnLuckyBlockAnimated(lbLoc, playerBase, targetMobPoint, (Entity rootEntity) -> {
                        if (rootEntity == null) {
                            return;
                        }
                        applyMutationDirect(rootEntity, fMut, fSnowy);
                        if (fcp != null && !fcp.isEmpty()) {
                            createCollectorHologramForMob(fBase, fPoint, fcp);
                        } else {
                            List<String> cps = baseCollectorPoints.get(fBase);
                            if (cps != null && !cps.isEmpty()) {
                                String fc = findFreeCollectorForBase(fBase, cps);
                                if (fc != null) createCollectorHologramForMob(fBase, fPoint, fc);
                            }
                        }
                    }, false, timer, ready);
                    continue;
                }
            }
            spawnMobAtPointExact(playerBase, targetMobPoint, targetCollectorPoint, mobType);
            boolean hasMutation = (fMut != null && !fMut.equals("NONE")) || fSnowy;
            if (hasMutation) {
                pendingMutations.add(new String[]{
                    fPoint,
                    fMut != null ? fMut : "NONE",
                    String.valueOf(fSnowy)
                });
            }
        }
        if (!pendingMutations.isEmpty()) {
            debugLog("[MUT-DEBUG] === PENDING MUTATIONS: " + pendingMutations.size() + " шт. ===");
            for (String[] md : pendingMutations) {
                debugLog("[MUT-DEBUG]   point=" + md[0] + " mut=" + md[1] + " snowy=" + md[2]);
            }
            final String finalBase = playerBase;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                debugLog("[MUT-DEBUG] === TIMER FIRED! Применяем мутации... ===");
                debugLog("[MUT-DEBUG] entityToPointMap size: " + entityToPointMap.size());
                for (String[] mutData : pendingMutations) {
                    String mobPoint = mutData[0];
                    String mutName = mutData[1];
                    boolean snowy = Boolean.parseBoolean(mutData[2]);
                    debugLog("[MUT-DEBUG] Обрабатываем: point=" + mobPoint + " mut=" + mutName + " snowy=" + snowy);
                    Entity targetMob = null;
                    for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
                        if (mobPoint.equals(entry.getValue())) {
                            Entity mob = entry.getKey();
                            if (mob != null && !mob.isDead() && mob.isValid()) {
                                targetMob = mob;
                            }
                            break;
                        }
                    }
                    if (targetMob == null) {
                        debugLog("[MUT-DEBUG] ❌ Не найден моб на точке " + mobPoint);
                        continue;
                    }
                    debugLog("[MUT-DEBUG] Найден моб: " + targetMob.getType() + " UUID=" + targetMob.getUniqueId());
                    Mutation existingMut = baseMobMutations.getOrDefault(targetMob, Mutation.NONE);
                    boolean existingSnowy = baseMobSnowy.getOrDefault(targetMob, false);
                    debugLog("[MUT-DEBUG] Текущая мутация: " + existingMut + " snowy=" + existingSnowy);
                    Mutation newMut = Mutation.fromName(mutName);
                    if (existingMut == Mutation.NONE && !existingSnowy) {
                        applyMutationDirect(targetMob, mutName, snowy);
                        debugLog("[MUT-DEBUG] ✅ Мутация " + mutName +
                            (snowy ? "+SNOWY" : "") + " применена к мобу на " + mobPoint);
                    } else if (existingMut != newMut || existingSnowy != snowy) {
                        applyMutationDirect(targetMob, mutName, snowy);
                        debugLog("[MUT-DEBUG] ✅ Мутация обновлена: " + mutName +
                            (snowy ? "+SNOWY" : "") + " на " + mobPoint);
                    } else {
                        debugLog("[MUT-DEBUG] Мутация уже применена на " + mobPoint);
                    }
                }
                debugLog("[MUT-DEBUG] === ЗАВЕРШЕНО ===");
            }, 10L);
        }
    }
    private void cleanupOrphanedLuckyBlocks() {
        debugLog("[LB CLEANUP] ========== ЗАПУСК ОЧИСТКИ ==========");
        int rootsRemoved = 0;
        int hitboxesRemoved = 0;
        int ajPartsRemoved = 0;
        int carryDisplaysRemoved = 0;
        Set<UUID> registeredLBs = new HashSet<>();
        for (Entity mob : entityToPointMap.keySet()) {
            if (mob != null && !mob.isDead()) {
                if (mob.getScoreboardTags().contains("LUCKY_BLOCK") ||
                    mob.getScoreboardTags().contains("aj.luckyblock.root") ||
                    luckyBlockTags.containsKey(mob)) {
                    registeredLBs.add(mob.getUniqueId());
                }
            }
        }
        Set<UUID> registeredHitboxes = new HashSet<>();
        for (Entity hitbox : luckyBlockHitboxMap.values()) {
            if (hitbox != null && !hitbox.isDead()) {
                registeredHitboxes.add(hitbox.getUniqueId());
            }
        }
        Set<String> registeredTags = new HashSet<>();
        for (String tag : luckyBlockTags.values()) {
            if (tag != null) registeredTags.add(tag);
        }
        Set<String> currentAnimating = new HashSet<>(animatingPoints);
        debugLog("[LB CLEANUP] Зарегистрировано LB: " + registeredLBs.size());
        debugLog("[LB CLEANUP] Зарегистрировано хитбоксов: " + registeredHitboxes.size());
        debugLog("[LB CLEANUP] Точки в анимации: " + currentAnimating.size());
        for (World world : Bukkit.getWorlds()) {
            String dim = world.getKey().toString();
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (entity == null || entity.isDead()) continue;
                if (entity.getType() == EntityType.ITEM_DISPLAY &&
                    (entity.getScoreboardTags().contains("aj.luckyblock.root") ||
                     entity.getScoreboardTags().contains("LUCKY_BLOCK"))) {
                    if (registeredLBs.contains(entity.getUniqueId())) continue;
                    if (entityToPointMap.containsKey(entity)) continue;
                    boolean isSpawning = false;
                    for (String tag : entity.getScoreboardTags()) {
                        if (tag.startsWith("LB_EXISTING_")) {
                            isSpawning = true;
                            break;
                        }
                    }
                    if (isSpawning) continue;
                    String uniqTag = null;
                    for (String tag : entity.getScoreboardTags()) {
                        if (tag.startsWith("base_lb_")) {
                            uniqTag = tag;
                            break;
                        }
                    }
                    if (uniqTag != null && registeredTags.contains(uniqTag)) continue;
                    debugLog("[LB CLEANUP] Найдена осиротевшая модель: " +
                        entity.getUniqueId() + " тег=" + uniqTag +
                        " loc=" + entity.getLocation().getBlockX() + "," +
                        entity.getLocation().getBlockY() + "," +
                        entity.getLocation().getBlockZ());
                    if (uniqTag != null) {
                        final String tag = uniqTag;
                        try {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                "execute in " + dim + " as @e[tag=" + tag + ",limit=1] " +
                                "run function animated_java:luckyblock/remove");
                        } catch (Exception ignored) {}
                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            try {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                    "execute in " + dim + " run kill @e[tag=" + tag + "]");
                            } catch (Exception ignored) {}
                        }, 3L);
                    }
                    if (!entity.isDead()) entity.remove();
                    rootsRemoved++;
                }
                if (entity.getScoreboardTags().contains("LUCKY_BLOCK_HITBOX")) {
                    if (registeredHitboxes.contains(entity.getUniqueId())) continue;
                    boolean belongsToLive = false;
                    for (String tag : entity.getScoreboardTags()) {
                        if (tag.startsWith("base_lb_") && registeredTags.contains(tag)) {
                            belongsToLive = true;
                            break;
                        }
                    }
                    if (belongsToLive) continue;
                    debugLog("[LB CLEANUP] Найден осиротевший хитбокс: " +
                        entity.getUniqueId() +
                        " loc=" + entity.getLocation().getBlockX() + "," +
                        entity.getLocation().getBlockY() + "," +
                        entity.getLocation().getBlockZ());
                    entity.remove();
                    hitboxesRemoved++;
                }
                if (entity.getScoreboardTags().contains("aj.luckyblock.entity") &&
                    !entity.getScoreboardTags().contains("aj.luckyblock.root")) {
                    if (entity.getVehicle() != null) continue;
                    boolean belongsToLive = false;
                    for (String tag : entity.getScoreboardTags()) {
                        if (tag.startsWith("base_lb_") && registeredTags.contains(tag)) {
                            belongsToLive = true;
                            break;
                        }
                    }
                    if (belongsToLive) continue;
                    debugLog("[LB CLEANUP] Найдена осиротевшая часть AJ: " +
                        entity.getUniqueId() + " тип=" + entity.getType());
                    entity.remove();
                    ajPartsRemoved++;
                }
                if (entity.getScoreboardTags().contains("LUCKY_BLOCK_CARRY_DISPLAY")) {
                    if (entity.getVehicle() == null || !(entity.getVehicle() instanceof Player)) {
                        debugLog("[LB CLEANUP] Найден осиротевший carry display: " +
                            entity.getUniqueId());
                        entity.remove();
                        carryDisplaysRemoved++;
                    } else {
                        Player carrier = (Player) entity.getVehicle();
                        if (!carrier.getScoreboardTags().contains("CARRYING_LUCKY_BLOCK")) {
                            debugLog("[LB CLEANUP] Carry display на игроке " +
                                carrier.getName() + " без тега CARRYING — удаляем");
                            carrier.removePassenger(entity);
                            entity.remove();
                            carryDisplaysRemoved++;
                        }
                    }
                }
            }
        }
        int cachesCleaned = 0;
        for (Entity mob : new ArrayList<>(luckyBlockTags.keySet())) {
            if (mob == null || mob.isDead() || !mob.isValid()) {
                if (!entityToPointMap.containsKey(mob)) {
                    luckyBlockTags.remove(mob);
                    cachesCleaned++;
                }
            }
        }
        for (Map.Entry<Entity, Entity> entry : new HashMap<>(luckyBlockHitboxMap).entrySet()) {
            Entity root = entry.getKey();
            Entity hitbox = entry.getValue();
            if (root == null || root.isDead() || !entityToPointMap.containsKey(root)) {
                luckyBlockHitboxMap.remove(root);
                if (hitbox != null && !hitbox.isDead()) {
                    hitbox.remove();
                    hitboxesRemoved++;
                }
                cachesCleaned++;
            } else if (hitbox == null || hitbox.isDead()) {
                Location hitboxLoc = root.getLocation().clone().add(0, 0.5, 0);
                Interaction newHitbox = (Interaction) root.getWorld().spawnEntity(
                    hitboxLoc, EntityType.INTERACTION);
                newHitbox.setInteractionWidth(1.2f);
                newHitbox.setInteractionHeight(1.2f);
                newHitbox.addScoreboardTag("LUCKY_BLOCK_HITBOX");
                newHitbox.addScoreboardTag("NO_DESPAWN");
                newHitbox.addScoreboardTag(BASE_MOB_TAG);
                newHitbox.setPersistent(true);
                String uniq = luckyBlockTags.get(root);
                if (uniq != null) newHitbox.addScoreboardTag(uniq);
                luckyBlockHitboxMap.put(root, newHitbox);
                debugLog("[LB CLEANUP] Пересоздан хитбокс для LB: " + root.getUniqueId());
            }
        }
        for (Entity mob : new ArrayList<>(luckyBlockOpenTime.keySet())) {
            if (mob == null || mob.isDead() || !entityToPointMap.containsKey(mob)) {
                luckyBlockOpenTime.remove(mob);
                luckyBlockReady.remove(mob);
                cachesCleaned++;
            }
        }
        for (Entity mob : new ArrayList<>(luckyBlockReady.keySet())) {
            if (mob == null || mob.isDead() || !entityToPointMap.containsKey(mob)) {
                luckyBlockReady.remove(mob);
                cachesCleaned++;
            }
        }
        for (Entity mob : new ArrayList<>(luckyBlockAnimations.keySet())) {
            if (mob == null || mob.isDead() || !entityToPointMap.containsKey(mob)) {
                BukkitRunnable anim = luckyBlockAnimations.remove(mob);
                if (anim != null) {
                    try { anim.cancel(); } catch (Exception ignored) {}
                }
                cachesCleaned++;
            }
        }
        debugLog("[LB CLEANUP] ========== ИТОГО ==========");
        debugLog("[LB CLEANUP] Моделей удалено: " + rootsRemoved);
        debugLog("[LB CLEANUP] Хитбоксов удалено: " + hitboxesRemoved);
        debugLog("[LB CLEANUP] Частей AJ удалено: " + ajPartsRemoved);
        debugLog("[LB CLEANUP] Carry display удалено: " + carryDisplaysRemoved);
        debugLog("[LB CLEANUP] Кэшей очищено: " + cachesCleaned);
        debugLog("[LB CLEANUP] ============================");
    }
    private void moveSavedMobsToNewBase(String playerName, String newBase) {
        List<SavedMobData> savedMobs = savedPlayerMobs.remove(playerName);
        if (savedMobs == null || savedMobs.isEmpty()) {
            getLogger().info("Нет сохраненных мобов для перемещения игрока " + playerName);
            return;
        }
        debugLog("Перемещение " + savedMobs.size() + " мобов игрока " + playerName + " на базу " + newBase);
        cleanupOldCollectorHologramsForPlayer(playerName);
        List<SavedMobData> updatedMobs = new ArrayList<>();
        List<String> newMobPoints = baseMobSpawnPoints.get(newBase);
        List<String> newCollectorPoints = baseCollectorPoints.get(newBase);
        if (newMobPoints == null || newCollectorPoints == null) {
            getLogger().warning("Новая база " + newBase + " не имеет точек спавна или коллекторов");
            return;
        }
        int mobPointIndex = 0;
        int collectorIndex = 0;
        for (SavedMobData savedMob : savedMobs) {
            if (mobPointIndex >= newMobPoints.size()) {
                mobPointIndex = 0;
            }
            if (collectorIndex >= newCollectorPoints.size()) {
                collectorIndex = 0;
            }
            String mobPoint = newMobPoints.get(mobPointIndex);
            String collectorPoint = newCollectorPoints.get(collectorIndex);
            updatedMobs.add(new SavedMobData(
                newBase, mobPoint, collectorPoint, savedMob.mobType,
                savedMob.mobType.isLuckyBlock() ? savedMob.luckyBlockRemainingMs : -1L,
                savedMob.mobType.isLuckyBlock() ? savedMob.luckyBlockReady : false,
                savedMob.mutation,
                savedMob.snowy
            ));
            debugLog("  Перемещён: " + savedMob.mobType.name +
                " mut=" + savedMob.mutation + " snowy=" + savedMob.snowy +
                " -> " + mobPoint);
            mobPointIndex++;
            collectorIndex++;
        }
        savedPlayerMobs.put(playerName, updatedMobs);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            restorePlayerMobs(playerName);
        }, 10L);
        getLogger().info("Мобы игрока " + playerName + " успешно перемещены на базу " + newBase);
    }
    private void cleanupOldCollectorHologramsForPlayer(String playerName) {
        List<String> oldBases = new ArrayList<>();
        for (Map.Entry<String, String> entry : bases.entrySet()) {
            if (playerName.equals(entry.getValue())) {
                oldBases.add(entry.getKey());
            }
        }
        for (String oldBase : oldBases) {
            cleanupOldCollectorHolograms(oldBase, "temp");
        }
    }
    private void cleanupOldCollectorHolograms(String oldBase, String newBase) {
        for (String collectorId : new ArrayList<>(collectorHolograms.keySet())) {
            if (collectorId.startsWith(oldBase + "_")) {
                Hologram holo = collectorHolograms.remove(collectorId);
                if (holo != null) {
                    hologramManager.removeHologram(holo);
                }
            }
        }
    }
    private void savePlayerMobsToFile(String playerName) {
        if (mobsConfig == null) {
            getLogger().warning("Файл mobs.yml не загружен!");
            return;
        }
        mobsConfig.set("mobs." + playerName, null);
        String playerBase = null;
        for (Map.Entry<String, String> entry : bases.entrySet()) {
            if (entry.getValue().equals(playerName)) {
                playerBase = entry.getKey();
                break;
            }
        }
        if (playerBase == null) {
            getLogger().warning("У игрока " + playerName + " нет базы для сохранения мобов.");
            savedPlayerMobs.remove(playerName);
            return;
        }
        List<String> basePoints = baseMobSpawnPoints.get(playerBase);
        if (basePoints == null || basePoints.isEmpty()) {
            getLogger().warning("На базе " + playerBase + " нет точек мобов!");
            return;
        }
        getLogger().info("=== Сохранение мобов для " + playerName + " (база " + playerBase + ") ===");
        List<SavedMobData> currentMobs = new ArrayList<>();
        for (Map.Entry<Entity, String> entry : new HashMap<>(entityToPointMap).entrySet()) {
            Entity mob = entry.getKey();
            String mobPoint = entry.getValue();
            if (mob == null || !mob.isValid() || mob.isDead()) {
                continue;
            }
            if (!basePoints.contains(mobPoint)) {
                continue;
            }
            MobType type = MobType.fromEntity(mob);
            if (type == null) {
                getLogger().warning("Не удалось определить тип моба на точке " + mobPoint);
                continue;
            }
            String collectorId = entityToCollectorMap.get(mobPoint);
            String collectorPoint = null;
            if (collectorId != null) {
                String[] parts = collectorId.split("_", 2);
                if (parts.length == 2 && parts[0].equals(playerBase)) {
                    collectorPoint = parts[1];
                }
            }
            SavedMobData savedData = new SavedMobData(playerBase, mobPoint, collectorPoint, type);
            currentMobs.add(savedData);
            getLogger().info("  ✓ Сохранен: " + type.name + " | Точка: " + mobPoint + " | Коллектор: " + collectorPoint);
        }
        if (!currentMobs.isEmpty()) {
            for (int i = 0; i < currentMobs.size(); i++) {
                SavedMobData data = currentMobs.get(i);
                String path = "mobs." + playerName + "." + i;
                mobsConfig.set(path + ".base", data.base);
                mobsConfig.set(path + ".mobPoint", data.mobPoint);
                mobsConfig.set(path + ".mobType", data.mobType.name());
                mobsConfig.set(path + ".collectorPoint", data.collectorPoint);
                mobsConfig.set(path + ".mutation", data.mutation);
                mobsConfig.set(path + ".snowy", data.snowy);
            }
            savedPlayerMobs.put(playerName, currentMobs);
            try {
                saveConfigAsync(mobsConfig, mobsFile);
                debugLog("=== Сохранено " + currentMobs.size() + " мобов для " + playerName + " ===");
            } catch (IOException e) {
                getLogger().severe("Ошибка сохранения mobs.yml: " + e.getMessage());
            }
        } else {
            getLogger().info("У игрока " + playerName + " нет мобов для сохранения.");
            savedPlayerMobs.remove(playerName);
            try {
                saveConfigAsync(mobsConfig, mobsFile);
            } catch (IOException e) {
                getLogger().severe("Ошибка сохранения mobs.yml: " + e.getMessage());
            }
        }
    }
    private void loadMobsFromConfig() {
        if (mobsConfig == null) {
            getLogger().warning("Файл mobs.yml не загружен!");
            return;
        }
        if (!mobsConfig.contains("mobs")) {
            getLogger().info("Нет сохраненных мобов в mobs.yml");
            return;
        }
        int totalLoaded = 0;
        for (String playerName : mobsConfig.getConfigurationSection("mobs").getKeys(false)) {
            List<SavedMobData> mobs = new ArrayList<>();
            ConfigurationSection playerSection = mobsConfig.getConfigurationSection("mobs." + playerName);
            if (playerSection == null) continue;
            for (String index : playerSection.getKeys(false)) {
                String base = mobsConfig.getString("mobs." + playerName + "." + index + ".base");
                String mobPoint = mobsConfig.getString("mobs." + playerName + "." + index + ".mobPoint");
                String collectorPoint = mobsConfig.getString("mobs." + playerName + "." + index + ".collectorPoint");
                String mobTypeName = mobsConfig.getString("mobs." + playerName + "." + index + ".mobType");
                long lbTimer = mobsConfig.getLong("mobs." + playerName + "." + index + ".luckyBlockTimer", -1L);
                boolean lbReady = mobsConfig.getBoolean("mobs." + playerName + "." + index + ".luckyBlockReady", false);
                String mutationName = mobsConfig.getString("mobs." + playerName + "." + index + ".mutation", "NONE");
                boolean snowyMob = mobsConfig.getBoolean("mobs." + playerName + "." + index + ".snowy", false);
                if (base == null || mobPoint == null || mobTypeName == null) {
                    getLogger().warning("Неполные данные моба для игрока " + playerName + ", индекс " + index);
                    continue;
                }
                try {
                    MobType mobType = MobType.valueOf(mobTypeName);
                    mobs.add(new SavedMobData(base, mobPoint, collectorPoint, mobType, lbTimer, lbReady, mutationName, snowyMob));
                    totalLoaded++;
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Неизвестный тип моба: " + mobTypeName + " для игрока " + playerName);
                }
            }
            if (!mobs.isEmpty()) {
                savedPlayerMobs.put(playerName, mobs);
            }
        }
        getLogger().info("Всего загружено " + totalLoaded + " мобов из mobs.yml");
    }
    private void saveAllMobsToFile() {
        if (mobsConfig == null) {
            getLogger().warning("Файл mobs.yml не загружен!");
            return;
        }
        getLogger().info("Сохранение всех мобов в mobs.yml...");
        mobsConfig.set("mobs", null);
        for (String playerName : savedPlayerMobs.keySet()) {
            List<SavedMobData> mobs = savedPlayerMobs.get(playerName);
            if (mobs == null || mobs.isEmpty()) continue;
            for (int i = 0; i < mobs.size(); i++) {
                SavedMobData mob = mobs.get(i);
                String path = "mobs." + playerName + "." + i;
                mobsConfig.set(path + ".base", mob.base);
                mobsConfig.set(path + ".mobPoint", mob.mobPoint);
                mobsConfig.set(path + ".collectorPoint", mob.collectorPoint);
                mobsConfig.set(path + ".mobType", mob.mobType.name());
                mobsConfig.set(path + ".mutation", mob.mutation);
                mobsConfig.set(path + ".snowy", mob.snowy);
                if (mob.mobType.isLuckyBlock()) {
                    mobsConfig.set(path + ".luckyBlockTimer", mob.luckyBlockRemainingMs);
                    mobsConfig.set(path + ".luckyBlockReady", mob.luckyBlockReady);
                }
            }
            debugLog("Сохранено " + mobs.size() + " мобов для игрока " + playerName);
        }
        try {
            saveConfigAsync(mobsConfig, mobsFile);
            getLogger().info("Все мобы сохранены в mobs.yml!");
        } catch (IOException e) {
            getLogger().warning("Ошибка сохранения mobs.yml: " + e.getMessage());
        }
    }
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault не найден! Деньги не будут выдаваться.");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("Economy провайдер не найден!");
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }
    private void createLockHolograms() {
        if (hologramManager == null) {
            getLogger().warning("HologramManager не инициализирован!");
            return;
        }
        FileConfiguration cfg = getConfig();
        for (String base : bases.keySet()) {
            String lockPoint = cfg.getString("bases." + base + ".lock_point");
            if (lockPoint == null || lockPoint.isEmpty()) {
                getLogger().warning("У базы " + base + " нет точки блокировки (lock_point)!");
                continue;
            }
            baseLockPoints.put(base, lockPoint);
            String[] s = lockPoint.split("_");
            if (s.length != 4) {
                getLogger().warning("Неправильный формат lock_point для базы " + base + ": " + lockPoint);
                continue;
            }
            World world = Bukkit.getWorld(s[0]);
            if (world == null) {
                getLogger().warning("Мир не найден для базы " + base + ": " + s[0]);
                continue;
            }
            int x, y, z;
            try {
                x = Integer.parseInt(s[1]);
                y = Integer.parseInt(s[2]);
                z = Integer.parseInt(s[3]);
            } catch (NumberFormatException e) {
                getLogger().warning("Неправильные координаты lock_point для базы " + base + ": " + lockPoint);
                continue;
            }
            Location loc = new Location(world, x + 0.5, y + lockHologramHeight, z + 0.5);
            String hologramName = "lock_" + base;
            Optional<Hologram> existingHolo = hologramManager.getHologram(hologramName);
            if (existingHolo.isPresent()) {
                hologramManager.removeHologram(existingHolo.get());
            }
            TextHologramData data = new TextHologramData(hologramName, loc);
            data.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            data.setScale(new Vector3f(0.8f, 0.8f, 0.8f));
            data.setBackground(Hologram.TRANSPARENT);
            data.setSeeThrough(true);
            data.setText(Arrays.asList(
                color("&6[ЗАБЛОКИРОВАТЬ]"),
                color("&7Встаньте на блок"),
                color("&7чтобы заблокировать базу")
            ));
            Hologram holo = hologramManager.create(data);
            hologramManager.addHologram(holo);
            lockHolograms.put(base, holo);
            baseLocked.put(base, false);
            baseLockTime.put(base, 0);
            getLogger().info("Создана голограмма блокировки для базы " + base + " на " + loc);
        }
        getLogger().info("Всего создано " + lockHolograms.size() + " голограмм блокировки");
    }
    private void updateLockHologram(String base) {
        Hologram holo = lockHolograms.get(base);
        if (holo == null || !(holo.getData() instanceof TextHologramData data)) return;
        boolean locked = baseLocked.getOrDefault(base, false);
        int timeLeft = baseLockTime.getOrDefault(base, 0);
        if (locked) {
            data.setText(Arrays.asList(
                color("&c[ЗАБЛОКИРОВАНО]"),
                color("&7Осталось: &e" + timeLeft + "&7 сек."),
                color("&7Вход в базу временно недоступен другим игрокам.")
            ));
        } else {
            data.setText(Arrays.asList(
                color("&6[ЗАБЛОКИРОВАТЬ]"),
                color("&7Встаньте на блок"),
                color("&7чтобы заблокировать базу")
            ));
        }
        holo.queueUpdate();
    }
    private void lockBase(String base, Player player, boolean afterRebirth) {
        if (baseLocked.getOrDefault(base, false)) {
            return;
        }
        String owner = bases.get(base);
        if (owner == null || (!owner.equals("none") && !owner.equals(player.getName()))) {
            return;
        }
        baseLocked.put(base, true);
        int rebirthCount = getRebirthCount(player);
        if (rebirthCount >= 8) {
            baseLockTime.put(base, REBIRTH_LOCK_DURATION_8);
            sendCooldownMessage(player, "§a✔ База заблокирована на §e140§a секунд (у вас " + rebirthCount + " перерождений)!", lastLockMessage);
        } else if (rebirthCount >= 7) {
            baseLockTime.put(base, REBIRTH_LOCK_DURATION_7);
            sendCooldownMessage(player, "§a✔ База заблокирована на §e130§a секунд (у вас " + rebirthCount + " перерождений)!", lastLockMessage);
        } else if (rebirthCount >= 6) {
            baseLockTime.put(base, REBIRTH_LOCK_DURATION_6);
            sendCooldownMessage(player, "§a✔ База заблокирована на §e120§a секунд (у вас " + rebirthCount + " перерождений)!", lastLockMessage);
        } else if (rebirthCount >= 5) {
            baseLockTime.put(base, REBIRTH_LOCK_DURATION_5);
            sendCooldownMessage(player, "§a✔ База заблокирована на §e110§a секунд (у вас " + rebirthCount + " перерождений)!", lastLockMessage);
        } else if (rebirthCount >= 4) {
            baseLockTime.put(base, REBIRTH_LOCK_DURATION_4);
            sendCooldownMessage(player, "§a✔ База заблокирована на §e100§a секунд (у вас " + rebirthCount + " перерождений)!", lastLockMessage);
        } else if (rebirthCount >= 3) {
            baseLockTime.put(base, REBIRTH_LOCK_DURATION_3);
            sendCooldownMessage(player, "§a✔ База заблокирована на §e90§a секунд (у вас " + rebirthCount + " перерождений)!", lastLockMessage);
        } else if (rebirthCount >= 2) {
            baseLockTime.put(base, REBIRTH_LOCK_DURATION_2);
            sendCooldownMessage(player, "§a✔ База заблокирована на §e80§a секунд (у вас " + rebirthCount + " перерождений)!", lastLockMessage);
        } else if (rebirthCount >= 1) {
            baseLockTime.put(base, REBIRTH_LOCK_DURATION_1);
            sendCooldownMessage(player, "§a✔ База заблокирована на §e70§a секунд (у вас " + rebirthCount + " перерождений)!", lastLockMessage);
        } else {
            baseLockTime.put(base, LOCK_DURATION);
            sendCooldownMessage(player, "§a✔ База заблокирована на §e60§a секунд!", lastLockMessage);
        }
        updateLockHologram(base);
        updateHologram(base);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, 1f);
        long currentTime = System.currentTimeMillis();
        String regionName = baseRegionNames.get(base);
        String particlePoint = baseParticlePoints.get(base);
        FileConfiguration cfg = getConfig();
        String worldName = cfg.getString("bases." + base + ".world");
        World world = worldName != null ? Bukkit.getWorld(worldName) : null;
        if (world != null) {
            for (Player p : world.getPlayers()) {
                if (p.equals(player)) continue;
                if (p.getName().equals(owner)) continue;
                if (regionName != null && isPlayerInRegion(p, regionName)) {
                    executeKick(p, base, currentTime, KickReason.REGION);
                    continue;
                }
                if (particlePoint != null && isPlayerNearParticleWall(p, particlePoint)) {
                    executeKick(p, base, currentTime, KickReason.PARTICLE_WALL);
                }
            }
        }
    }
    public void adminDeleteMob(String playerName, int index) {
        if (mobsConfig == null || savedPlayerMobs == null) return;
        List<SavedMobData> mobs = savedPlayerMobs.get(playerName);
        if (mobs == null || index < 0 || index >= mobs.size()) return;
        SavedMobData mobToDelete = mobs.get(index);
        for (Map.Entry<Entity, String> entry : new HashMap<>(entityToPointMap).entrySet()) {
            if (entry.getValue().equals(mobToDelete.mobPoint)) {
                if (mobToDelete.mobType.isLuckyBlock()) {
                    removeLuckyBlockFromBase(entry.getKey());
                } else {
                    removeMobFromSystem(entry.getKey());
                }
                break;
            }
        }
        mobs.remove(index);
        savedPlayerMobs.put(playerName, mobs);
        mobsConfig.set("mobs." + playerName, null);
        for (int i = 0; i < mobs.size(); i++) {
            SavedMobData m = mobs.get(i);
            String path = "mobs." + playerName + "." + i;
            mobsConfig.set(path + ".base", m.base);
            mobsConfig.set(path + ".mobPoint", m.mobPoint);
            mobsConfig.set(path + ".mobType", m.mobType.name());
            mobsConfig.set(path + ".collectorPoint", m.collectorPoint);
            mobsConfig.set(path + ".mutation", m.mutation);
            mobsConfig.set(path + ".snowy", m.snowy);
            if (m.mobType.isLuckyBlock()) {
                mobsConfig.set(path + ".luckyBlockTimer", m.luckyBlockRemainingMs);
                mobsConfig.set(path + ".luckyBlockReady", m.luckyBlockReady);
            }
        }
        try { saveConfigAsync(mobsConfig, mobsFile); } catch (IOException e) {}
    }
    public void adminSetMutation(String playerName, int index, String mutationName, boolean snowy) {
        if (mobsConfig == null || savedPlayerMobs == null) return;
        List<SavedMobData> mobs = savedPlayerMobs.get(playerName);
        if (mobs == null || index < 0 || index >= mobs.size()) return;
        SavedMobData oldData = mobs.get(index);
        SavedMobData newData = new SavedMobData(
            oldData.base, oldData.mobPoint, oldData.collectorPoint, oldData.mobType,
            oldData.luckyBlockRemainingMs, oldData.luckyBlockReady,
            mutationName, snowy
        );
        mobs.set(index, newData);
        savedPlayerMobs.put(playerName, mobs);
        String path = "mobs." + playerName + "." + index;
        mobsConfig.set(path + ".mutation", mutationName);
        mobsConfig.set(path + ".snowy", snowy);
        try { saveConfigAsync(mobsConfig, mobsFile); } catch (IOException e) {}
        for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
            if (entry.getValue().equals(oldData.mobPoint)) {
                Entity mob = entry.getKey();
                if (mob != null && !mob.isDead()) {
                    for (String tag : new HashSet<>(mob.getScoreboardTags())) {
                        if (tag.startsWith("MUTATION_")) {
                            mob.removeScoreboardTag(tag);
                        }
                    }
                    baseMobMutations.remove(mob);
                    baseMobSnowy.remove(mob);
                    BukkitRunnable anim = baseRainbowAnimations.remove(mob);
                    if (anim != null) try { anim.cancel(); } catch (Exception ignored) {}
                    if (!mutationName.equals("NONE") || snowy) {
                        applyMutationDirect(mob, mutationName, snowy);
                    } else {
                        MobType type = MobType.fromEntity(mob);
                        if (type != null) {
                            removeMobHologram(mob);
                            createMobHologram(mob, type);
                        }
                    }
                    getLogger().info("Админ изменил мутацию на " + mutationName + " (snowy=" + snowy + ") для моба на " + oldData.mobPoint);
                }
                break;
            }
        }
    }
    public boolean adminAddMob(String playerName, String mobTypeStr) {
        String base = null;
        for (Map.Entry<String, String> entry : bases.entrySet()) {
            if (entry.getValue().equals(playerName)) {
                base = entry.getKey();
                break;
            }
        }
        if (base == null) return false;
        String freePoint = findFreeMobPoint(base);
        if (freePoint == null) return false;
        List<String> cols = baseCollectorPoints.get(base);
        String freeCol = findFreeCollectorForBase(base, cols);
        if (freeCol == null && cols != null && !cols.isEmpty()) freeCol = cols.get(0);
        MobType type;
        try { type = MobType.valueOf(mobTypeStr); }
        catch (Exception e) { return false; }
        spawnMobAtPoint(base, freePoint, freeCol, type);
        savePlayerMobsInstantly(playerName);
        return true;
    }
    private void lockBase(String base, Player player) {
        lockBase(base, player, false);
    }
    private void removeMobFromSystem(Entity mob) {
        if (mob == null) return;
        String mobPoint = entityToPointMap.get(mob);
        removeMobHologram(mob);
        if (mobPoint != null) {
            String collectorId = entityToCollectorMap.get(mobPoint);
            if (collectorId != null) {
                removeCollectorHologram(collectorId);
                collectorToEntityMap.remove(collectorId);
                entityToCollectorMap.remove(mobPoint);
                synchronized (moneyLock) {
                    collectorMoney.remove(collectorId);
                    collectorLastUpdate.remove(collectorId);
                }
            }
            String baseName = findBaseByMobPoint(mobPoint);
            if (baseName != null) {
                Set<String> occupied = occupiedMobPoints.get(baseName);
                if (occupied != null) {
                    occupied.remove(mobPoint);
                }
            }
        }
        entityToPointMap.remove(mob);
        mobSpawnTime.remove(mob);
        baseMobMutations.remove(mob);
        baseMobSnowy.remove(mob);
        BukkitRunnable rainbowAnim = baseRainbowAnimations.remove(mob);
        if (rainbowAnim != null) {
            try { rainbowAnim.cancel(); } catch (Exception ignored) {}
        }
        if (!mob.isDead()) {
            mob.remove();
        }
    }
    private boolean isPlayerInRegion(Player player, String regionName) {
        try {
            Location loc = player.getLocation();
            World world = loc.getWorld();
            if (world == null) return false;
            RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
            if (regionManager == null) return false;
            ProtectedRegion region = regionManager.getRegion(regionName);
            if (region == null) return false;
            return region.contains(
                BukkitAdapter.asBlockVector(loc).getBlockX(),
                BukkitAdapter.asBlockVector(loc).getBlockY(),
                BukkitAdapter.asBlockVector(loc).getBlockZ()
            );
        } catch (Exception e) {
            getLogger().warning("Ошибка при проверке региона WorldGuard: " + e.getMessage());
            return false;
        }
    }
    private void unlockBase(String base) {
        baseLocked.put(base, false);
        baseLockTime.put(base, 0);
        updateLockHologram(base);
        updateHologram(base);
        String owner = bases.get(base);
        if (owner != null && !owner.equals("none")) {
            Player ownerPlayer = Bukkit.getPlayer(owner);
            if (ownerPlayer != null && ownerPlayer.isOnline()) {
                sendCooldownMessage(ownerPlayer, "§a✅ Ваша база разблокирована!", lastLockMessage);
                ownerPlayer.playSound(ownerPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
            }
        }
    }
    private void loadStage2(String base) {
        Stage2Config sc = stage2Configs.get(base);
        if (sc == null || !sc.enabled) return;
        if (Boolean.TRUE.equals(stage2Active.get(base))) return;
        stage2Active.put(base, true);
        getLogger().info("[Stage2] loading for base " + base);
        pasteStage2Schematic(base, sc.schematic, () -> {
            if (sc.mobPoint != null && !sc.mobPoint.isEmpty()) {
                List<String> mp = baseMobSpawnPoints.get(base);
                if (mp != null && !mp.contains(sc.mobPoint)) mp.add(sc.mobPoint);
            }
            if (sc.collectorPoint != null && !sc.collectorPoint.isEmpty()) {
                List<String> cp = baseCollectorPoints.get(base);
                if (cp != null && !cp.contains(sc.collectorPoint)) cp.add(sc.collectorPoint);
            }
            getLogger().info("[Stage2] slot+collector added for base " + base);
        });
    }
    private void unloadStage2(String base) {
        Stage2Config sc = stage2Configs.get(base);
        if (sc == null) return;
        if (!Boolean.TRUE.equals(stage2Active.get(base))) return;
        stage2Active.put(base, false);
        if (sc.mobPoint != null && !sc.mobPoint.isEmpty()) {
            List<String> mp = baseMobSpawnPoints.get(base);
            if (mp != null) mp.remove(sc.mobPoint);
            Set<String> occ = occupiedMobPoints.get(base);
            if (occ != null) occ.remove(sc.mobPoint);
            animatingPoints.remove(sc.mobPoint);
        }
        if (sc.collectorPoint != null && !sc.collectorPoint.isEmpty()) {
            List<String> cp = baseCollectorPoints.get(base);
            if (cp != null) cp.remove(sc.collectorPoint);
        }
        getLogger().info("[Stage2] unloading for base " + base);
        pasteStage2Schematic(base, sc.emptySchematic, null);
    }
    private File resolveSchematicFile(String name) {
        if (name == null || name.isEmpty()) return null;
        String fn = (name.endsWith(".schem") || name.endsWith(".schematic")) ? name : name + ".schem";
        File local = new File(new File(getDataFolder(), "schematics"), fn);
        if (local.exists()) return local;
        File parent = getDataFolder().getParentFile();
        if (parent != null) {
            File fawe = new File(parent, "FastAsyncWorldEdit/schematics/" + fn);
            if (fawe.exists()) return fawe;
            File we = new File(parent, "WorldEdit/schematics/" + fn);
            if (we.exists()) return we;
        }
        return local;
    }
    private void pasteStage2Schematic(String base, String schemName, Runnable afterMain) {
        Stage2Config sc = stage2Configs.get(base);
        if (sc == null) return;
        if (schemName == null || schemName.isEmpty()) {
            if (afterMain != null) Bukkit.getScheduler().runTask(this, afterMain);
            return;
        }
        Location l1 = getLocationFromPoint(sc.pos1);
        Location l2 = getLocationFromPoint(sc.pos2);
        if (l1 == null || l2 == null || l1.getWorld() == null) {
            getLogger().warning("[Stage2] base " + base + ": invalid pos1/pos2");
            return;
        }
        final World world = l1.getWorld();
        final int minX = Math.min(l1.getBlockX(), l2.getBlockX());
        final int minY = Math.min(l1.getBlockY(), l2.getBlockY());
        final int minZ = Math.min(l1.getBlockZ(), l2.getBlockZ());
        final File file = resolveSchematicFile(schemName);
        if (file == null || !file.exists()) {
            getLogger().warning("[Stage2] base " + base + ": schematic file not found: " + schemName);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                ClipboardFormat format = ClipboardFormats.findByFile(file);
                if (format == null) {
                    getLogger().warning("[Stage2] base " + base + ": unknown schematic format " + file.getName());
                    return;
                }
                Clipboard clipboard;
                try (java.io.FileInputStream fis = new java.io.FileInputStream(file);
                     ClipboardReader reader = format.getReader(fis)) {
                    clipboard = reader.read();
                }
                // Reset origin to the schematic's minimum corner so the paste
                // aligns exactly to pos1/pos2 regardless of the //copy position.
                clipboard.setOrigin(clipboard.getRegion().getMinimumPoint());
                com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
                try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).build()) {
                    Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(BlockVector3.at(minX, minY, minZ))
                        .ignoreAirBlocks(false)
                        .build();
                    Operations.complete(operation);
                }
                getLogger().info("[Stage2] base " + base + ": pasted '" + schemName + "' at " + minX + "," + minY + "," + minZ);
            } catch (Throwable t) {
                getLogger().severe("[Stage2] base " + base + ": paste error '" + schemName + "': " + t);
            }
            if (afterMain != null) Bukkit.getScheduler().runTask(this, afterMain);
        });
    }
    private void loadBases() {
        FileConfiguration cfg = getConfig();
        if (!cfg.contains("bases")) return;
        for (String key : cfg.getConfigurationSection("bases").getKeys(false)) {
            bases.put(key, cfg.getString("bases." + key + ".owner", "none"));
            String particlePoint = cfg.getString("bases." + key + ".particle_point");
            if (particlePoint != null && !particlePoint.isEmpty()) {
                baseParticlePoints.put(key, particlePoint);
            }
            String regionName = cfg.getString("bases." + key + ".region_name");
            if (regionName != null && !regionName.isEmpty()) {
                baseRegionNames.put(key, regionName);
            }
            List<String> mobPoints = cfg.getStringList("bases." + key + ".mob_spawn_points");
            if (mobPoints.isEmpty()) {
                getLogger().warning("База " + key + " не имеет mob_spawn_points в конфиге!");
            } else {
                getLogger().info("База " + key + ": загружено " + mobPoints.size() + " точек мобов");
            }
            baseMobSpawnPoints.put(key, new ArrayList<>(mobPoints));
            occupiedMobPoints.put(key, new HashSet<>());
            List<String> submitPoints = cfg.getStringList("bases." + key + ".submit_points");
            baseSubmitPoints.put(key, new ArrayList<>(submitPoints));
            List<String> collectorPoints = cfg.getStringList("bases." + key + ".collector_points");
            if (collectorPoints.isEmpty()) {
                getLogger().warning("База " + key + " не имеет collector_points в конфиге!");
            } else {
                getLogger().info("База " + key + ": загружено " + collectorPoints.size() + " точек коллекторов");
            }
            baseCollectorPoints.put(key, new ArrayList<>(collectorPoints));
            List<String> kickPoints = cfg.getStringList("bases." + key + ".kick_points");
            baseKickPoints.put(key, new ArrayList<>(kickPoints));
            if (kickPoints.isEmpty()) {
                getLogger().warning("У базы " + key + " нет точек кика (kick_points) в конфиге!");
            }
            ConfigurationSection st2sec = cfg.getConfigurationSection("bases." + key + ".stage2");
            if (st2sec != null && st2sec.getBoolean("enabled", false)) {
                Stage2Config sc = new Stage2Config();
                sc.enabled = true;
                sc.world = st2sec.getString("world", cfg.getString("bases." + key + ".world", "world"));
                sc.schematic = st2sec.getString("schematic");
                sc.emptySchematic = st2sec.getString("empty_schematic");
                sc.pos1 = st2sec.getString("pos1");
                sc.pos2 = st2sec.getString("pos2");
                sc.mobPoint = st2sec.getString("mob_point");
                sc.collectorPoint = st2sec.getString("collector_point");
                sc.laserPoint = st2sec.getString("laser_point");
                sc.requiredRebirths = st2sec.getInt("required_rebirths", 2);
                stage2Configs.put(key, sc);
                getLogger().info("[Stage2] base " + key + " configured (schem=" + sc.schematic + ", req=" + sc.requiredRebirths + ")");
            }
            stage2Active.put(key, false);
            baseLocked.put(key, false);
            baseLockTime.put(key, 0);
        }
        loadInitialBalancePlayers();
        getLogger().info("Загружено " + bases.size() + " баз");
    }
    private void loadInitialBalancePlayers() {
        FileConfiguration cfg = getConfig();
        if (cfg.contains("initial_balance_players")) {
            playersWithInitialBalance.addAll(cfg.getStringList("initial_balance_players"));
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamageByAnyMob(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Entity damager = event.getDamager();
        if (damager instanceof LivingEntity && !(damager instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof LivingEntity shooter) {
                if (!(shooter instanceof Player)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }
    private void saveInitialBalancePlayers() {
        FileConfiguration cfg = getConfig();
        cfg.set("initial_balance_players", new ArrayList<>(playersWithInitialBalance));
        saveConfig();
    }
    private String formatNumber(double number) {
        if (number >= 1_000_000_000) {
            double value = number / 1_000_000_000.0;
            if (value == (long) value) {
                return (long) value + "КК";
            }
            return String.format("%.1f", value).replace(",", ".") + "КК";
        }
        else if (number >= 1_000_000) {
            double value = number / 1_000_000.0;
            if (value == (long) value) {
                return (long) value + "М";
            }
            return String.format("%.1f", value).replace(",", ".") + "М";
        }
        else if (number >= 1_000) {
            double value = number / 1_000.0;
            if (value == (long) value) {
                return (long) value + "К";
            }
            return String.format("%.1f", value).replace(",", ".") + "К";
        }
        return String.valueOf((long) number);
    }
    private String formatNumber(int number) {
        return formatNumber((double) number);
    }
    private void createBaseHolograms() {
        if (hologramManager == null) {
            getLogger().warning("HologramManager не инициализирован!");
            return;
        }
        FileConfiguration cfg = getConfig();
        for (String base : bases.keySet()) {
            String worldName = cfg.getString("bases." + base + ".world");
            if (worldName == null) {
                getLogger().warning("У базы " + base + " не указан мир!");
                continue;
            }
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                getLogger().warning("Мир " + worldName + " не найден для базы " + base);
                continue;
            }
            double x = cfg.getDouble("bases." + base + ".x");
            double y = cfg.getDouble("bases." + base + ".y");
            double z = cfg.getDouble("bases." + base + ".z");
            Location loc = new Location(world, x, y + hologramHeight, z);
            float baseYaw = (float) cfg.getDouble("bases." + base + ".yaw");
            if (isReversedBase(base)) {
                loc.setYaw(baseYaw + 90f);
            } else {
                loc.setYaw(baseYaw + 270f);
            }
            String hologramName = "base_" + base;
            Optional<Hologram> existingHolo = hologramManager.getHologram(hologramName);
            if (existingHolo.isPresent()) {
                hologramManager.removeHologram(existingHolo.get());
            }
            TextHologramData data = new TextHologramData(hologramName, loc);
            data.setBillboard(Display.Billboard.FIXED);
            data.setScale(new Vector3f(3f, 3f, 3f));
            data.setBackground(Hologram.TRANSPARENT);
            data.setSeeThrough(false);
            data.setText(buildText(base));
            data.setVisibilityDistance(100);
            Hologram holo = hologramManager.create(data);
            hologramManager.addHologram(holo);
            holograms.put(base, holo);
            getLogger().info("Создана голограмма для базы " + base + " на " + loc + " (reversed: " + isReversedBase(base) + ")");
        }
        getLogger().info("Всего создано " + holograms.size() + " голограмм баз");
    }
    private void createCollectorHologramForMob(String base, String mobPoint, String collectorPoint) {
        if (hologramManager == null || base == null || mobPoint == null || collectorPoint == null) {
            debugLog("[COLLECTOR] Некорректные параметры");
            return;
        }
        String collectorId = base + "_" + collectorPoint;
        String hologramName = "collector_" + collectorId;
        debugLog("[COLLECTOR] Создание коллектора: " + collectorId + " для моба на " + mobPoint);
        int removed = 0;
        for (Hologram holo : new ArrayList<>(hologramManager.getHolograms())) {
            if (holo.getData().getName().equals(hologramName)) {
                hologramManager.removeHologram(holo);
                removed++;
            }
        }
        if (removed > 0) {
            debugLog("[COLLECTOR] Удалено " + removed + " дубликатов голограммы: " + hologramName);
        }
        Hologram existingHolo = collectorHolograms.remove(collectorId);
        if (existingHolo != null) {
            hologramManager.removeHologram(existingHolo);
            debugLog("[COLLECTOR] Удалена голограмма из кэша: " + collectorId);
        }
        String oldCollectorId = entityToCollectorMap.get(mobPoint);
        if (oldCollectorId != null && !oldCollectorId.equals(collectorId)) {
            debugLog("[COLLECTOR] Очистка старой связи: " + oldCollectorId + " -> " + mobPoint);
            Hologram oldHolo = collectorHolograms.remove(oldCollectorId);
            if (oldHolo != null) {
                hologramManager.removeHologram(oldHolo);
            }
            collectorToEntityMap.remove(oldCollectorId);
            synchronized (moneyLock) {
                collectorMoney.remove(oldCollectorId);
                collectorLastUpdate.remove(oldCollectorId);
            }
        }
        String existingMobPoint = collectorToEntityMap.get(collectorId);
        if (existingMobPoint != null && !existingMobPoint.equals(mobPoint)) {
            boolean otherMobAlive = false;
            for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
                if (existingMobPoint.equals(entry.getValue())) {
                    Entity otherMob = entry.getKey();
                    if (otherMob != null && !otherMob.isDead() && otherMob.isValid()) {
                        otherMobAlive = true;
                        break;
                    }
                }
            }
            if (otherMobAlive) {
                debugLog("[COLLECTOR] Коллектор " + collectorId + " занят живым мобом на " + existingMobPoint);
                return;
            } else {
                debugLog("[COLLECTOR] Коллектор " + collectorId + " был связан с мёртвым мобом, очищаем");
                collectorToEntityMap.remove(collectorId);
                entityToCollectorMap.remove(existingMobPoint);
            }
        }
        FileConfiguration cfg = getConfig();
        World world = Bukkit.getWorld(cfg.getString("bases." + base + ".world"));
        if (world == null) {
            debugLog("[COLLECTOR] Мир не найден для базы " + base);
            return;
        }
        String[] s = collectorPoint.split("_");
        if (s.length != 4) {
            debugLog("[COLLECTOR] Неверный формат точки: " + collectorPoint);
            return;
        }
        int x, y, z;
        try {
            x = Integer.parseInt(s[1]);
            y = Integer.parseInt(s[2]);
            z = Integer.parseInt(s[3]);
        } catch (NumberFormatException e) {
            debugLog("[COLLECTOR] Неверные координаты: " + collectorPoint);
            return;
        }
        double collectorHologramHeight = getConfig().getDouble("settings.collector_hologram_height", 0.3);
        Location loc = new Location(world, x + 0.5, y + collectorHologramHeight, z + 0.5);
        TextHologramData data = new TextHologramData(hologramName, loc);
        data.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        data.setScale(new Vector3f(0.6f, 0.6f, 0.6f));
        data.setBackground(Hologram.TRANSPARENT);
        data.setSeeThrough(true);
        data.setTextAlignment(org.bukkit.entity.TextDisplay.TextAlignment.CENTER);
        data.setText(Arrays.asList(color("&a$0")));
        Hologram holo = hologramManager.create(data);
        hologramManager.addHologram(holo);
        collectorHolograms.put(collectorId, holo);
        synchronized (moneyLock) {
            collectorToEntityMap.put(collectorId, mobPoint);
            entityToCollectorMap.put(mobPoint, collectorId);
            collectorMoney.put(collectorId, 0.0);
            collectorLastUpdate.put(collectorId, System.currentTimeMillis());
        }
        debugLog("[COLLECTOR] ✅ Создан коллектор " + collectorId + " для " + mobPoint);
    }
    private void removeCollectorHologram(String collectorId) {
        Hologram holo = collectorHolograms.remove(collectorId);
        if (holo != null) {
            hologramManager.removeHologram(holo);
        }
        synchronized (moneyLock) {
            String mobPoint = collectorToEntityMap.remove(collectorId);
            if (mobPoint != null) {
                entityToCollectorMap.remove(mobPoint);
            }
            collectorMoney.remove(collectorId);
            collectorLastUpdate.remove(collectorId);
        }
    }
    private void updateCollectorHologram(String collectorId) {
        Hologram holo = collectorHolograms.get(collectorId);
        if (holo == null || !(holo.getData() instanceof TextHologramData data)) return;
        double money;
        synchronized (moneyLock) {
            money = collectorMoney.getOrDefault(collectorId, 0.0);
        }
        int displayMoney = (int) Math.floor(money + 0.000001);
        if (displayMoney < 0) displayMoney = 0;
        data.setText(Arrays.asList(color("&a$" + formatNumber(displayMoney))));
        holo.queueUpdate();
    }
    private List<String> buildText(String base) {
        FileConfiguration cfg = getConfig();
        String owner = bases.get(base);
        List<String> lines = new ArrayList<>();
        boolean isLocked = baseLocked.getOrDefault(base, false);
        if (owner == null || owner.equals("none")) {
            lines.add(color(cfg.getString("texts.free", "&a[СВОБОДНА]")));
            lines.add(color(cfg.getString("texts.status_open", "&aОткрыта")));
        } else {
            String ownerDisplay = owner;
            OfflinePlayer offlineOwner = Bukkit.getOfflinePlayer(owner);
            if (offlineOwner.isOp()) {
                ownerDisplay = "§c§lАдмина §f" + owner;
            }
            lines.add(color(cfg.getString("texts.owner", "&eВладелец: &f%player%").replace("%player%", ownerDisplay)));
            if (isLocked) {
                lines.add(color("&c🔒 ЗАБЛОКИРОВАНА"));
            } else {
                lines.add(color(cfg.getString("texts.status_closed", "&cЗанято")));
            }
        }
        return lines;
    }
    private void updateHologram(String base) {
        Hologram holo = holograms.get(base);
        if (hologramManager == null || holo == null || !(holo.getData() instanceof TextHologramData data)) return;
        data.setText(buildText(base));
        holo.queueUpdate();
    }
    private void fixCollectorRelationships(String base) {
        getLogger().info("Исправление связей коллекторов на базе " + base);
        List<String> collectorPoints = baseCollectorPoints.get(base);
        List<String> mobPoints = baseMobSpawnPoints.get(base);
        if (collectorPoints == null || mobPoints == null) {
            getLogger().warning("У базы " + base + " нет точек коллекторов или мобов");
            return;
        }
        Set<String> occupied = occupiedMobPoints.get(base);
        if (occupied == null) {
            getLogger().warning("На базе " + base + " нет занятых точек");
            return;
        }
        Map<String, String> assignedCollectors = new HashMap<>();
        int collectorIndex = 0;
        for (String mobPoint : occupied) {
            String existingCollectorId = entityToCollectorMap.get(mobPoint);
            if (existingCollectorId != null) {
                Hologram existingHologram = collectorHolograms.get(existingCollectorId);
                if (existingHologram != null) {
                    assignedCollectors.put(mobPoint, existingCollectorId);
                    continue;
                }
            }
            if (collectorIndex >= collectorPoints.size()) {
                collectorIndex = 0;
            }
            String collectorPoint = collectorPoints.get(collectorIndex);
            String collectorId = base + "_" + collectorPoint;
            boolean collectorInUse = false;
            for (Map.Entry<String, String> entry : assignedCollectors.entrySet()) {
                if (entry.getValue().equals(collectorId)) {
                    collectorInUse = true;
                    break;
                }
            }
            if (!collectorInUse) {
                createCollectorHologramForMob(base, mobPoint, collectorPoint);
                assignedCollectors.put(mobPoint, collectorId);
                collectorIndex++;
                getLogger().info("Назначен коллектор " + collectorPoint + " для моба на точке " + mobPoint);
            } else {
                for (String point : collectorPoints) {
                    String testCollectorId = base + "_" + point;
                    boolean isUsed = false;
                    for (Map.Entry<String, String> entry : assignedCollectors.entrySet()) {
                        if (entry.getValue().equals(testCollectorId)) {
                            isUsed = true;
                            break;
                        }
                    }
                    if (!isUsed) {
                        createCollectorHologramForMob(base, mobPoint, point);
                        assignedCollectors.put(mobPoint, testCollectorId);
                        getLogger().info("Назначен альтернативный коллектор " + point + " для моба на точке " + mobPoint);
                        break;
                    }
                }
            }
        }
        getLogger().info("Исправление связей завершено для базы " + base);
    }
    private void registerFixCommand() {
        PluginCommand command = getCommand("fixcollectors");
        if (command != null) {
            command.setExecutor(new CommandExecutor() {
                @Override
                public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                    if (!(sender instanceof Player)) return true;
                    Player player = (Player) sender;
                    String base = findPlayerBase(player);
                    if (base == null) {
                        player.sendMessage("§cУ вас нет базы!");
                        return true;
                    }
                    player.sendMessage("§eИсправление связей коллекторов на вашей базе...");
                    fixCollectorRelationships(base);
                    player.sendMessage("§aСвязи коллекторов исправлены!");
                    return true;
                }
            });
        }
    }
    private void registerReloadHologramsCommand() {
        PluginCommand command = getCommand("reloadholograms");
        if (command != null) {
            command.setExecutor(new CommandExecutor() {
                @Override
                public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                    if (!sender.hasPermission("brainrotbases.admin")) {
                        sender.sendMessage("§cНет прав!");
                        return true;
                    }
                    sender.sendMessage("§eПересоздание голограмм...");
                    for (Hologram holo : holograms.values()) {
                        hologramManager.removeHologram(holo);
                    }
                    holograms.clear();
                    for (Hologram holo : lockHolograms.values()) {
                        hologramManager.removeHologram(holo);
                    }
                    lockHolograms.clear();
                    createBaseHolograms();
                    createLockHolograms();
                    sender.sendMessage("§aГолограммы пересозданы!");
                    sender.sendMessage("§7Баз: " + holograms.size() + ", блокировок: " + lockHolograms.size());
                    return true;
                }
            });
        }
    }
    private void registerReloadCommand() {
        PluginCommand command = getCommand("brreload");
        if (command != null) {
            command.setExecutor(new CommandExecutor() {
                @Override
                public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                    if (!sender.hasPermission("brainrotbases.admin")) {
                        sender.sendMessage("§cНет прав!");
                        return true;
                    }
                    sender.sendMessage("§eПолная перезагрузка плагина...");
                    sender.sendMessage("§7Удаление голограмм...");
                    for (Hologram holo : new ArrayList<>(holograms.values())) {
                        hologramManager.removeHologram(holo);
                    }
                    holograms.clear();
                    for (Hologram holo : new ArrayList<>(lockHolograms.values())) {
                        hologramManager.removeHologram(holo);
                    }
                    lockHolograms.clear();
                    for (Hologram holo : new ArrayList<>(collectorHolograms.values())) {
                        hologramManager.removeHologram(holo);
                    }
                    collectorHolograms.clear();
                    sender.sendMessage("§7Очистка кэша...");
                    bases.clear();
                    baseSubmitPoints.clear();
                    baseMobSpawnPoints.clear();
                    baseCollectorPoints.clear();
                    baseKickPoints.clear();
                    baseLockPoints.clear();
                    baseParticlePoints.clear();
                    baseRegionNames.clear();
                    baseLocked.clear();
                    baseLockTime.clear();
                    occupiedMobPoints.clear();
                    sender.sendMessage("§7Загрузка конфига...");
                    reloadConfig();
                    sender.sendMessage("§7Загрузка баз...");
                    loadBases();
                    sender.sendMessage("§7Создание голограмм...");
                    createBaseHolograms();
                    createLockHolograms();
                    sender.sendMessage("§a✔ Плагин полностью перезагружен!");
                    sender.sendMessage("§7Загружено баз: §e" + bases.size());
                    sender.sendMessage("§7Голограмм баз: §e" + holograms.size());
                    sender.sendMessage("§7Голограмм блокировки: §e" + lockHolograms.size());
                    for (String base : bases.keySet()) {
                        String lockPoint = baseLockPoints.get(base);
                        boolean hasLockHolo = lockHolograms.containsKey(base);
                        boolean hasBaseHolo = holograms.containsKey(base);
                        sender.sendMessage("§8- §f" + base +
                            " §7| Lock: " + (lockPoint != null ? "§a" + lockPoint : "§cНЕТ") +
                            " §7| Holo: " + (hasBaseHolo ? "§a✓" : "§c✗") +
                            " §7| LockHolo: " + (hasLockHolo ? "§a✓" : "§c✗"));
                    }
                    return true;
                }
            });
        }
    }
    private void spawnMobAtPoint(String base, String mobPoint, String collectorPoint, MobType type) {
        if (base == null || mobPoint == null || type == null) return;
        Set<String> occupied = occupiedMobPoints.get(base);
        if (occupied == null) return;
        if (occupied.contains(mobPoint)) {
            boolean hasRealMob = false;
            for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
                if (mobPoint.equals(entry.getValue())) {
                    Entity mob = entry.getKey();
                    if (mob != null && !mob.isDead()) {
                        hasRealMob = true;
                        break;
                    }
                }
            }
            if (!hasRealMob) {
                getLogger().warning("Точка " + mobPoint + " отмечена как занятая, но моба нет. Освобождаем.");
                occupied.remove(mobPoint);
            } else {
                getLogger().warning("Точка " + mobPoint + " уже занята! Ищем свободную...");
                mobPoint = findFreeMobPoint(base);
                if (mobPoint == null) {
                    getLogger().warning("Все точки на базе " + base + " заняты!");
                    return;
                }
            }
        }
        if (collectorPoint == null || collectorPoint.isEmpty()) {
            List<String> collectorPoints = baseCollectorPoints.get(base);
            if (collectorPoints != null && !collectorPoints.isEmpty()) {
                collectorPoint = findFreeCollectorForBase(base, collectorPoints);
                if (collectorPoint == null) {
                    collectorPoint = collectorPoints.get(0);
                    getLogger().warning("Все коллекторы на базе " + base + " заняты, используем первый: " + collectorPoint);
                }
            } else {
                getLogger().warning("На базе " + base + " нет точек коллекторов!");
            }
        }
        if (collectorPoint != null) {
            String collectorId = base + "_" + collectorPoint;
            String existingMobPoint = collectorToEntityMap.get(collectorId);
            if (existingMobPoint != null && !existingMobPoint.equals(mobPoint)) {
                boolean hasRealMob = false;
                for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
                    if (existingMobPoint.equals(entry.getValue())) {
                        Entity mob = entry.getKey();
                        if (mob != null && !mob.isDead()) {
                            hasRealMob = true;
                            break;
                        }
                    }
                }
                if (!hasRealMob) {
                    getLogger().warning("Коллектор " + collectorId + " имеет недействительную связь. Очищаем.");
                    removeCollectorHologram(collectorId);
                    collectorToEntityMap.remove(collectorId);
                    if (existingMobPoint != null) {
                        entityToCollectorMap.remove(existingMobPoint);
                    }
                }
            }
        }
        String[] s = mobPoint.split("_");
        if (s.length != 4) return;
        World world = Bukkit.getWorld(s[0]);
        if (world == null) return;
        int x, y, z;
        try {
            x = Integer.parseInt(s[1]);
            y = Integer.parseInt(s[2]);
            z = Integer.parseInt(s[3]);
        } catch (NumberFormatException e) {
            return;
        }
        Location loc = new Location(world, x + 0.5, y + 0.5, z + 0.5);
        ensureChunkLoaded(loc);
        float yaw;
        List<String> allMobPoints = baseMobSpawnPoints.get(base);
        if (allMobPoints == null) {
            yaw = isReversedBase(base) ? 90.0f : -90.0f;
        } else {
            int pointIndex = allMobPoints.indexOf(mobPoint);
            if (pointIndex == -1) {
                pointIndex = 0;
            }
            if (isReversedBase(base)) {
                if (pointIndex >= 0 && pointIndex < 5) {
                    yaw = 90.0f;
                } else if (pointIndex >= 5 && pointIndex < 10) {
                    yaw = -90.0f;
                } else {
                    yaw = 90.0f;
                }
            } else {
                if (pointIndex >= 0 && pointIndex < 5) {
                    yaw = -90.0f;
                } else if (pointIndex >= 5 && pointIndex < 10) {
                    yaw = 90.0f;
                } else {
                    yaw = -90.0f;
                }
            }
        }
        loc.setYaw(yaw);
        loc.setPitch(0.0f);
        removeMobAtPoint(mobPoint);
        Chunk chunk = loc.getChunk();
        if (!chunk.isLoaded()) {
            chunk.load();
        }
        if (type.isLuckyBlock()) {
            final String finalMobPoint = mobPoint;
            final String finalCollectorPoint = collectorPoint;
            final String finalBase = base;
            spawnLuckyBlockAnimated(loc, base, mobPoint, (Entity rootEntity) -> {
                if (rootEntity == null) {
                    getLogger().warning("Не удалось создать Lucky Block на базе " + finalBase);
                    return;
                }
                if (finalCollectorPoint != null) {
                    createCollectorHologramForMob(finalBase, finalMobPoint, finalCollectorPoint);
                }
                String owner = bases.get(finalBase);
                if (owner != null && !owner.equals("none")) {
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        savePlayerMobsInstantly(owner);
                    }, 5L);
                }
                debugLog("Создан Lucky Block на точке " + finalMobPoint);
            }, true);
            return;
        }
        if (type.isRotWalker()) {
            final String finalMobPoint = mobPoint;
            final String finalCollectorPoint = collectorPoint;
            final String finalBase = base;
            spawnRotWalkerAnimated(loc, base, mobPoint, (Entity rootEntity) -> {
                if (rootEntity == null) {
                    getLogger().warning("Не удалось создать Гнилохода на базе " + finalBase);
                    return;
                }
                if (finalCollectorPoint != null)
                    createCollectorHologramForMob(finalBase, finalMobPoint, finalCollectorPoint);
                String owner = bases.get(finalBase);
                if (owner != null && !owner.equals("none"))
                    Bukkit.getScheduler().runTaskLater(this, () -> savePlayerMobsInstantly(owner), 5L);
                debugLog("Создан Гнилоход на точке " + finalMobPoint);
            }, true);
            return;
        }
        Entity mob = world.spawnEntity(loc, type.type);
        mob.setPersistent(true);
        mob.setSilent(true);
        mob.setInvulnerable(true);
        mob.setGravity(false);
        mob.setCustomNameVisible(false);
        if (mob instanceof LivingEntity living) {
            living.setAI(false);
            living.setCollidable(false);
            living.setRemoveWhenFarAway(false);
            living.setCanPickupItems(false);
            if (living instanceof Mob mobEntity) {
                mobEntity.setTarget(null);
                mobEntity.setAware(false);
            }
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 999999, 0, false, false));
            if (living instanceof Ageable ageable) {
                ageable.setAgeLock(true);
            }
            if (mob instanceof Enderman enderman) {
                enderman.setTarget(null);
                enderman.setCarriedBlock(null);
                mob.addScoreboardTag("ENDERMAN_NO_TELEPORT");
            }
            if (type == MobType.STRIDER && mob instanceof org.bukkit.entity.Strider strider) {
                strider.setShivering(false);
                strider.setSaddle(false);
            }
            if (type == MobType.CAT_KUZYA && mob instanceof Cat cat) {
                cat.setCatType(Cat.Type.RED);
                cat.setCollarColor(DyeColor.RED);
                cat.setTamed(true);
            }
            if (type == MobType.FOX && mob instanceof Fox fox) {
                fox.setFoxType(Fox.Type.RED);
            }
            if (type == MobType.BROWN_PANDA && mob instanceof Panda panda) {
                panda.setMainGene(Panda.Gene.BROWN);
                panda.setHiddenGene(Panda.Gene.BROWN);
            }
            if (type == MobType.HUSK && mob instanceof Husk husk) {
                husk.setConversionTime(-1);
            }
            if (type == MobType.STRAY && mob instanceof Stray stray) {
            }
            if (type == MobType.PANDA && mob instanceof Panda panda) {
                panda.setMainGene(Panda.Gene.NORMAL);
            }
            if (mob instanceof Wolf wolf) {
                wolf.setAngry(false);
                wolf.setCollarColor(DyeColor.RED);
            }
            if (mob instanceof Horse horse) {
                horse.setColor(Horse.Color.BROWN);
                horse.setStyle(Horse.Style.WHITE);
            }
            if (mob instanceof Llama llama) {
                llama.setColor(Llama.Color.CREAMY);
            }
            if (mob instanceof Hoglin hoglin) {
                hoglin.setImmuneToZombification(true);
            }
            if (mob instanceof PiglinBrute piglinBrute) {
                piglinBrute.setImmuneToZombification(true);
            }
            if (type == MobType.PIGLIN && mob instanceof Piglin piglin) {
                piglin.setImmuneToZombification(true);
                piglin.setIsAbleToHunt(false);
            }
            if (mob instanceof Bat bat) {
                bat.setAwake(true);
            }
            if (mob instanceof Bee bee) {
                bee.setAnger(0);
                bee.setCannotEnterHiveTicks(999999);
            }
            if (type == MobType.CREEPER && mob instanceof Creeper creeper) {
                creeper.setPowered(true);
            }
            if (type == MobType.DROWNED && mob instanceof Drowned drowned) {
                drowned.setConversionTime(-1);
            }
            if (type == MobType.ZOMBIFIED_PIGLIN && mob instanceof PigZombie pigZombie) {
                pigZombie.setAngry(false);
                pigZombie.setAnger(0);
            }
            if (type == MobType.GOAT && mob instanceof Goat goat) {
                goat.setScreaming(random.nextBoolean());
            }
            if (type == MobType.SNOW_GOLEM && mob instanceof Snowman snowman) {
                snowman.setDerp(false);
            }
            if (type == MobType.WARDEN && mob instanceof Warden warden) {
                warden.setAware(false);
                for (Player p : warden.getWorld().getPlayers()) {
                    warden.setAnger(p, 0);
                }
                mob.addScoreboardTag("NO_WARDEN_EFFECTS");
            }
            if (type == MobType.AXOLOTL && mob instanceof Axolotl axolotl) {
                axolotl.setVariant(Axolotl.Variant.BLUE);
            }
            if (type == MobType.FROG && mob instanceof Frog frog) {
                frog.setVariant(Frog.Variant.WARM);
            }
            if (type == MobType.ZOGLIN && mob instanceof Zoglin zoglin) {
            }
        }
        mob.addScoreboardTag("BASE_MOB_PERSISTENT");
        mob.addScoreboardTag("NO_DESPAWN");
        mob.addScoreboardTag(BASE_MOB_TAG);
        mob.addScoreboardTag(MOB_TAG_PREFIX + type.name());
        mob.addScoreboardTag("MOB_RARITY_" + type.rarity.name());
        if (type.isMythical()) {
            mob.addScoreboardTag("MYTHICAL_MOB");
            world.spawnParticle(Particle.END_ROD, loc, 50, 0.5, 0.5, 0.5, 0.1);
            world.spawnParticle(Particle.FIREWORK, loc, 30, 0.5, 0.5, 0.5, 0.2);
            world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
            if (type == MobType.WARDEN) {
                world.spawnParticle(Particle.SCULK_SOUL, loc, 20, 0.5, 0.5, 0.5, 0.05);
                world.playSound(loc, Sound.ENTITY_WARDEN_EMERGE, 0.3f, 1.0f);
            }
        } else if (type.isLegendary()) {
            mob.addScoreboardTag("LEGENDARY_MOB");
            world.spawnParticle(Particle.FLAME, loc, 15, 0.3, 0.3, 0.3, 0.05);
            if (type == MobType.PHANTOM) {
                world.spawnParticle(Particle.DRAGON_BREATH, loc, 20, 0.5, 0.5, 0.5, 0.1);
            }
        } else if (type.isEpic()) {
            mob.addScoreboardTag("EPIC_MOB");
            world.spawnParticle(Particle.ENCHANT, loc, 20, 0.3, 0.3, 0.3, 0.5);
        }
        if (type.isLegendary() && (type.type == EntityType.GHAST ||
                                  type.type == EntityType.PHANTOM)) {
            mob.teleport(loc);
            if (mob instanceof LivingEntity living) {
                living.setAI(false);
                living.setSilent(true);
                living.setInvulnerable(true);
                living.setGravity(false);
                living.setCollidable(false);
            }
        }
        if (type.isMythical() && (type == MobType.ALLAY || type == MobType.GLOW_SQUID)) {
            mob.teleport(loc);
            if (mob instanceof LivingEntity living) {
                living.setAI(false);
                living.setGravity(false);
            }
        }
        mob.setCustomNameVisible(false);
        mob.setPersistent(true);
        removeMobHologram(mob);
        createMobHologram(mob, type);
        entityToPointMap.put(mob, mobPoint);
        mobSpawnTime.put(mob, System.currentTimeMillis());
        occupied.add(mobPoint);
        applyMutationToBaseMob(mob, base);
        if (collectorPoint != null) {
            createCollectorHologramForMob(base, mobPoint, collectorPoint);
        }
        debugLog("Создан " + type.name + " (редкость: " + type.rarity + ") на точке " + mobPoint + " с доходом " + type.baseIncome + "/сек");
        final Entity finalMob = mob;
        String owner = bases.get(base);
        if (owner != null && !owner.equals("none")) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                savePlayerMobsInstantly(owner);
            }, 5L);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (finalMob != null && !finalMob.isDead()) {
                disableMobPhysics(finalMob);
            }
        }, 20L);
    }
    public void applyMutationToPoint(String baseName, String mobPoint, String mutationName, boolean snowy) {
        if (baseName == null || mobPoint == null) return;
        for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
            if (mobPoint.equals(entry.getValue())) {
                Entity mob = entry.getKey();
                if (mob == null || mob.isDead()) continue;
                Mutation mutation = Mutation.fromName(mutationName);
                if (mutation != Mutation.NONE) {
                    baseMobMutations.put(mob, mutation);
                    mob.addScoreboardTag("MUTATION_" + mutation.name());
                }
                if (snowy) {
                    baseMobSnowy.put(mob, true);
                    mob.addScoreboardTag("MUTATION_SNOWY");
                }
                MobType type = MobType.fromEntity(mob);
                if (type != null) {
                    removeMobHologram(mob);
                    createMobHologram(mob, type);
                }
                String owner = bases.get(baseName);
                if (owner != null && !owner.equals("none")) {
                    savePlayerMobsInstantly(owner);
                }
                return;
            }
        }
    }
    private void spawnMobAtPointExact(String base, String mobPoint, String collectorPoint, MobType type) {
        if (base == null || mobPoint == null || type == null) {
            getLogger().warning("spawnMobAtPointExact: некорректные параметры");
            return;
        }
        if (type.isLuckyBlock()) {
            String[] s = mobPoint.split("_");
            if (s.length != 4) return;
            World world = Bukkit.getWorld(s[0]);
            if (world == null) return;
            try {
                int x = Integer.parseInt(s[1]);
                int y = Integer.parseInt(s[2]);
                int z = Integer.parseInt(s[3]);
                Location loc = new Location(world, x + 0.5, y + 0.5, z + 0.5);
                ensureChunkLoaded(loc);
                final String finalCollectorPoint = collectorPoint;
                final String finalBase = base;
                final String finalMobPoint = mobPoint;
                spawnLuckyBlockAnimated(loc, finalBase, finalMobPoint, (Entity rootEntity) -> {
                    if (rootEntity == null) {
                        getLogger().warning("Не удалось восстановить Lucky Block на точке " + finalMobPoint);
                        return;
                    }
                    if (finalCollectorPoint != null && !finalCollectorPoint.isEmpty()) {
                        createCollectorHologramForMob(finalBase, finalMobPoint, finalCollectorPoint);
                    } else {
                        List<String> collectorPoints = baseCollectorPoints.get(finalBase);
                        if (collectorPoints != null && !collectorPoints.isEmpty()) {
                            String freeCollector = findFreeCollectorForBase(finalBase, collectorPoints);
                            if (freeCollector != null) {
                                createCollectorHologramForMob(finalBase, finalMobPoint, freeCollector);
                            }
                        }
                    }
                    String owner = bases.get(finalBase);
                    if (owner != null && !owner.equals("none")) {
                        Bukkit.getScheduler().runTaskLater(BrainrotBases.this, () -> {
                            savePlayerMobsInstantly(owner);
                        }, 5L);
                    }
                }, false);
            } catch (NumberFormatException e) {
                getLogger().warning("Неверные координаты: " + mobPoint);
            }
            return;
        }
        if (type.isRotWalker()) {
            String[] sr = mobPoint.split("_");
            if (sr.length != 4) return;
            World worldR = Bukkit.getWorld(sr[0]);
            if (worldR == null) return;
            try {
                int rx = Integer.parseInt(sr[1]);
                int ry = Integer.parseInt(sr[2]);
                int rz = Integer.parseInt(sr[3]);
                Location locR = new Location(worldR, rx + 0.5, ry + 0.5, rz + 0.5);
                ensureChunkLoaded(locR);
                final String finalCollectorPoint = collectorPoint;
                final String finalBase = base;
                final String finalMobPoint = mobPoint;
                spawnRotWalkerAnimated(locR, finalBase, finalMobPoint, (Entity rootEntity) -> {
                    if (rootEntity == null) {
                        getLogger().warning("Не удалось восстановить Гнилохода на точке " + finalMobPoint);
                        return;
                    }
                    if (finalCollectorPoint != null && !finalCollectorPoint.isEmpty()) {
                        createCollectorHologramForMob(finalBase, finalMobPoint, finalCollectorPoint);
                    } else {
                        List<String> collectorPoints = baseCollectorPoints.get(finalBase);
                        if (collectorPoints != null && !collectorPoints.isEmpty()) {
                            String freeCollector = findFreeCollectorForBase(finalBase, collectorPoints);
                            if (freeCollector != null)
                                createCollectorHologramForMob(finalBase, finalMobPoint, freeCollector);
                        }
                    }
                    String owner = bases.get(finalBase);
                    if (owner != null && !owner.equals("none"))
                        Bukkit.getScheduler().runTaskLater(BrainrotBases.this, () -> savePlayerMobsInstantly(owner), 5L);
                }, false);
            } catch (NumberFormatException e) {
                getLogger().warning("Неверные координаты: " + mobPoint);
            }
            return;
        }
        String[] s = mobPoint.split("_");
        if (s.length != 4) {
            getLogger().warning("Неверный формат точки: " + mobPoint);
            return;
        }
        World world = Bukkit.getWorld(s[0]);
        if (world == null) {
            getLogger().warning("Мир не найден: " + s[0]);
            return;
        }
        int x, y, z;
        try {
            x = Integer.parseInt(s[1]);
            y = Integer.parseInt(s[2]);
            z = Integer.parseInt(s[3]);
        } catch (NumberFormatException e) {
            getLogger().warning("Неверные координаты точки: " + mobPoint);
            return;
        }
        Location loc = new Location(world, x + 0.5, y + 0.5, z + 0.5);
        ensureChunkLoaded(loc);
        List<String> allMobPoints = baseMobSpawnPoints.get(base);
        float yaw = -90.0f;
        if (allMobPoints != null) {
            int pointIndex = allMobPoints.indexOf(mobPoint);
            if (isReversedBase(base)) {
                yaw = (pointIndex >= 0 && pointIndex < 5) ? 90.0f : -90.0f;
            } else {
                yaw = (pointIndex >= 0 && pointIndex < 5) ? -90.0f : 90.0f;
            }
        }
        loc.setYaw(yaw);
        loc.setPitch(0.0f);
        removeMobAtPoint(mobPoint);
        Entity mob = world.spawnEntity(loc, type.type);
        mob.setPersistent(true);
        mob.setSilent(true);
        mob.setInvulnerable(true);
        mob.setGravity(false);
        mob.setCustomNameVisible(false);
        if (mob instanceof LivingEntity living) {
            living.setAI(false);
            living.setCollidable(false);
            living.setRemoveWhenFarAway(false);
            living.setCanPickupItems(false);
            if (living instanceof Mob mobEntity) {
                mobEntity.setTarget(null);
                mobEntity.setAware(false);
            }
            if (living instanceof Ageable ageable) {
                ageable.setAgeLock(true);
            }
            if (mob instanceof Enderman enderman) {
                enderman.setTarget(null);
                enderman.setCarriedBlock(null);
            }
        }
        mob.addScoreboardTag("BASE_MOB_PERSISTENT");
        mob.addScoreboardTag("NO_DESPAWN");
        mob.addScoreboardTag(BASE_MOB_TAG);
        mob.addScoreboardTag(MOB_TAG_PREFIX + type.name());
        if (type.isLegendary()) {
            mob.addScoreboardTag("LEGENDARY_MOB");
        }
        if (type.isMythical()) {
            mob.addScoreboardTag("MYTHICAL_MOB");
            mob.addScoreboardTag("MOB_RARITY_MYTHICAL");
        }
        createMobHologram(mob, type);
        entityToPointMap.put(mob, mobPoint);
        mobSpawnTime.put(mob, System.currentTimeMillis());
        Set<String> occupied = occupiedMobPoints.computeIfAbsent(base, k -> new HashSet<>());
        occupied.add(mobPoint);
        applyMutationToBaseMob(mob, base);
        if (collectorPoint != null && !collectorPoint.isEmpty()) {
            createCollectorHologramForMob(base, mobPoint, collectorPoint);
        } else {
            List<String> collectorPoints = baseCollectorPoints.get(base);
            if (collectorPoints != null && !collectorPoints.isEmpty()) {
                String freeCollector = findFreeCollectorForBase(base, collectorPoints);
                if (freeCollector != null) {
                    createCollectorHologramForMob(base, mobPoint, freeCollector);
                }
            }
        }
        debugLog("✓ Моб " + type.name + " восстановлен на точке " + mobPoint);
        final Entity finalMob = mob;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (finalMob != null && !finalMob.isDead()) {
                disableMobPhysics(finalMob);
            }
        }, 5L);
    }
    private void applyMutationToBaseMob(Entity mob, String base) {
        if (mob == null || base == null) return;
        String owner = bases.get(base);
        if (owner == null || owner.equals("none")) {
            debugLog("[MUT-DEBUG] applyMutationToBaseMob: owner=null или none для базы " + base);
            return;
        }
        String mobPoint = entityToPointMap.get(mob);
        if (mobPoint == null) {
            debugLog("[MUT-DEBUG] applyMutationToBaseMob: mobPoint=NULL! Моб ещё не в entityToPointMap!");
            return;
        }
        List<SavedMobData> savedMobs = savedPlayerMobs.get(owner);
        if (savedMobs == null) {
            debugLog("[MUT-DEBUG] applyMutationToBaseMob: savedMobs=null для " + owner);
            return;
        }
        debugLog("[MUT-DEBUG] applyMutationToBaseMob: ищем мутацию для " + mobPoint + " среди " + savedMobs.size() + " записей");
        for (SavedMobData saved : savedMobs) {
            debugLog("[MUT-DEBUG]   проверяем: base=" + saved.base + " point=" + saved.mobPoint + " mut=" + saved.mutation + " snowy=" + saved.snowy);
            if (saved.base.equals(base) && saved.mobPoint.equals(mobPoint)) {
                Mutation mutation = Mutation.fromName(saved.mutation);
                boolean snowy = saved.snowy;
                debugLog("[MUT-DEBUG]   НАЙДЕНО! mutation=" + mutation + " snowy=" + snowy);
                if (mutation == Mutation.NONE && !snowy) {
                    debugLog("[MUT-DEBUG]   Нет мутации, пропускаем");
                    return;
                }
                if (mutation != Mutation.NONE) {
                    baseMobMutations.put(mob, mutation);
                    mob.addScoreboardTag("MUTATION_" + mutation.name());
                }
                if (snowy) {
                    baseMobSnowy.put(mob, true);
                    mob.addScoreboardTag("MUTATION_SNOWY");
                }
                MobType type = MobType.fromEntity(mob);
                if (type != null) {
                    removeMobHologram(mob);
                    createMobHologram(mob, type);
                }
                debugLog("[MUT-DEBUG]   ✅ Мутация применена!");
                return;
            }
        }
        debugLog("[MUT-DEBUG] Мутация НЕ НАЙДЕНА для точки " + mobPoint);
    }
    public void applyMutationDirect(Entity mob, String mutationName, boolean snowy) {
        if (mob == null) {
            debugLog("[MUT-DEBUG] applyMutationDirect: mob=null!");
            return;
        }
        debugLog("[MUT-DEBUG] applyMutationDirect: mutationName=" + mutationName + " snowy=" + snowy + " mob=" + mob.getType() + " dead=" + mob.isDead());
        Mutation mutation = Mutation.fromName(mutationName);
        if (mutation == Mutation.NONE && !snowy) {
            debugLog("[MUT-DEBUG] applyMutationDirect: нет мутации, пропускаем");
            return;
        }
        if (mutation != Mutation.NONE) {
            baseMobMutations.put(mob, mutation);
            mob.addScoreboardTag("MUTATION_" + mutation.name());
            debugLog("[MUT-DEBUG] applyMutationDirect: установлена мутация " + mutation);
        }
        if (snowy) {
            baseMobSnowy.put(mob, true);
            mob.addScoreboardTag("MUTATION_SNOWY");
            debugLog("[MUT-DEBUG] applyMutationDirect: установлен snowy");
        }
        MobType type = MobType.fromEntity(mob);
        if (type != null) {
            removeMobHologram(mob);
            createMobHologram(mob, type);
            debugLog("[MUT-DEBUG] applyMutationDirect: голограмма пересоздана");
        }
    }
    private void removeMobAtPoint(String mobPoint) {
        for (Map.Entry<Entity, String> entry : new HashMap<>(entityToPointMap).entrySet()) {
            if (!entry.getValue().equals(mobPoint)) continue;
            Entity mob = entry.getKey();
            boolean isLB = mob != null &&
                    (mob.getScoreboardTags().contains("LUCKY_BLOCK") ||
                     luckyBlockTags.containsKey(mob));
            if (isLB) {
                String uniqTag = luckyBlockTags.remove(mob);
                Entity hitbox = luckyBlockHitboxMap.remove(mob);
                if (hitbox != null && hitbox.isValid()) hitbox.remove();
                BukkitRunnable anim = luckyBlockAnimations.remove(mob);
                if (anim != null) {
                    try { anim.cancel(); } catch (Exception ignored) {}
                }
                removeMobHologram(mob);
                String collectorId = entityToCollectorMap.get(mobPoint);
                if (collectorId != null) {
                    removeCollectorHologram(collectorId);
                    collectorToEntityMap.remove(collectorId);
                    entityToCollectorMap.remove(mobPoint);
                    synchronized (moneyLock) {
                        collectorMoney.remove(collectorId);
                        collectorLastUpdate.remove(collectorId);
                    }
                }
                entityToPointMap.remove(mob);
                mobSpawnTime.remove(mob);
                String baseName = findBaseByMobPoint(mobPoint);
                if (baseName != null) {
                    Set<String> occupied = occupiedMobPoints.get(baseName);
                    if (occupied != null) occupied.remove(mobPoint);
                }
                if (uniqTag != null && mob.getWorld() != null) {
                    String dim = mob.getWorld().getKey().toString();
                    final String tag = uniqTag;
                    Bukkit.getScheduler().runTask(this, () -> {
                        try {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                    "execute in " + dim + " as @e[tag=" + tag + ",limit=1] " +
                                    "run function animated_java:luckyblock/remove");
                        } catch (Exception ignored) {}
                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            try {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                        "execute in " + dim + " run kill @e[tag=" + tag + "]");
                            } catch (Exception ignored) {}
                        }, 5L);
                    });
                }
                if (!mob.isDead()) mob.remove();
                debugLog("[LUCKY BLOCK] Удалён с точки " + mobPoint);
            } else if (mob != null && rotWalkerTags.containsKey(mob)) {
                String uniqTag = rotWalkerTags.remove(mob);
                Entity hitbox = rotWalkerHitboxMap.remove(mob);
                if (hitbox != null && hitbox.isValid()) hitbox.remove();
                removeMobHologram(mob);
                String collectorId = entityToCollectorMap.get(mobPoint);
                if (collectorId != null) {
                    removeCollectorHologram(collectorId);
                    collectorToEntityMap.remove(collectorId);
                    entityToCollectorMap.remove(mobPoint);
                    synchronized (moneyLock) {
                        collectorMoney.remove(collectorId);
                        collectorLastUpdate.remove(collectorId);
                    }
                }
                entityToPointMap.remove(mob);
                mobSpawnTime.remove(mob);
                String baseName = findBaseByMobPoint(mobPoint);
                if (baseName != null) {
                    Set<String> occupied = occupiedMobPoints.get(baseName);
                    if (occupied != null) occupied.remove(mobPoint);
                }
                if (uniqTag != null && mob.getWorld() != null) {
                    String dim = mob.getWorld().getKey().toString();
                    final String tag = uniqTag;
                    Bukkit.getScheduler().runTask(this, () -> {
                        try {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                    "execute in " + dim + " as @e[tag=" + tag + ",limit=1] " +
                                    "run function animated_java:rotwalker/remove");
                        } catch (Exception ignored) {}
                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            try {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                        "execute in " + dim + " run kill @e[tag=" + tag + "]");
                            } catch (Exception ignored) {}
                        }, 5L);
                    });
                }
                if (!mob.isDead()) mob.remove();
                debugLog("[ROT WALKER] Удалён с точки " + mobPoint);
            } else {
                removeMobHologram(mob);
                String collectorId = entityToCollectorMap.get(mobPoint);
                if (collectorId != null) {
                    removeCollectorHologram(collectorId);
                    collectorToEntityMap.remove(collectorId);
                    entityToCollectorMap.remove(mobPoint);
                    synchronized (moneyLock) {
                        collectorMoney.remove(collectorId);
                        collectorLastUpdate.remove(collectorId);
                    }
                }
                entityToPointMap.remove(mob);
                mobSpawnTime.remove(mob);
                String baseName = findBaseByMobPoint(mobPoint);
                if (baseName != null) {
                    Set<String> occupied = occupiedMobPoints.get(baseName);
                    if (occupied != null) occupied.remove(mobPoint);
                }
                if (mob != null && !mob.isDead()) mob.remove();
            }
            break;
        }
    }
    private ItemStack createResetItem() {
        ItemStack resetItem = new ItemStack(Material.BARRIER);
        ItemMeta meta = resetItem.getItemMeta();
        meta.setDisplayName(color("&c&lРЕСЕТ"));
        meta.setLore(Arrays.asList(
            color(""),
            color("&7Нажмите &fПКМ&7, чтобы"),
            color("&7вернуться на спавн базы"),
            color(""),
            color("&c⚠ Вы умрёте!"),
            color(""),
            color("&8Нельзя выбросить")
        ));
        meta.getPersistentDataContainer().set(
            new NamespacedKey(this, RESET_ITEM_TAG),
            org.bukkit.persistence.PersistentDataType.BYTE,
            (byte) 1
        );
        resetItem.setItemMeta(meta);
        return resetItem;
    }
    private boolean isResetItem(ItemStack item) {
        if (item == null || item.getType() != Material.BARRIER) return false;
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(
            new NamespacedKey(this, RESET_ITEM_TAG),
            org.bukkit.persistence.PersistentDataType.BYTE
        );
    }
    private void giveResetItem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isResetItem(item)) {
                return;
            }
        }
        ItemStack currentItem = player.getInventory().getItem(RESET_ITEM_SLOT);
        if (currentItem != null && currentItem.getType() != Material.AIR) {
            player.getInventory().addItem(currentItem);
        }
        player.getInventory().setItem(RESET_ITEM_SLOT, createResetItem());
    }
    private void ensureResetItem(Player player) {
        ItemStack itemInSlot = player.getInventory().getItem(RESET_ITEM_SLOT);
        if (!isResetItem(itemInSlot)) {
            boolean foundReset = false;
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                if (i == RESET_ITEM_SLOT) continue;
                ItemStack item = player.getInventory().getItem(i);
                if (isResetItem(item)) {
                    player.getInventory().setItem(i, itemInSlot);
                    player.getInventory().setItem(RESET_ITEM_SLOT, item);
                    foundReset = true;
                    break;
                }
            }
            if (!foundReset) {
                if (itemInSlot != null && itemInSlot.getType() != Material.AIR) {
                    player.getInventory().addItem(itemInSlot);
                }
                player.getInventory().setItem(RESET_ITEM_SLOT, createResetItem());
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onResetItemUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!isResetItem(item)) return;
        event.setCancelled(true);
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        StealingData stealData = stealingPlayers.get(player);
        if (stealData != null) {
            if (stealData.isCarrying) {
                player.sendMessage(color("&c❌ Нельзя использовать ресет пока вы несёте моба!"));
                player.sendMessage(color("&7Сначала доставьте моба на свою базу или верните его."));
            } else {
                player.sendMessage(color("&c❌ Нельзя использовать ресет во время кражи!"));
                player.sendMessage(color("&7Отойдите от моба, чтобы отменить кражу."));
            }
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        long currentTime = System.currentTimeMillis();
        if (resetCooldowns.containsKey(player.getUniqueId())) {
            long lastUse = resetCooldowns.get(player.getUniqueId());
            long secondsLeft = ((lastUse + (RESET_COOLDOWN_SECONDS * 1000)) - currentTime) / 1000;
            if (secondsLeft > 0) {
                player.sendMessage(color("&c⏳ Подождите " + secondsLeft + " сек. перед следующим ресетом!"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                return;
            }
        }
        String playerBase = findPlayerBase(player);
        if (playerBase == null) {
            player.sendMessage(color("&c❌ У вас нет базы!"));
            return;
        }
        resetCooldowns.put(player.getUniqueId(), currentTime);
        pendingResets.add(player.getUniqueId());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.5f, 2.0f);
        player.getWorld().spawnParticle(Particle.GUST_EMITTER_LARGE, player.getLocation().add(0, 1, 0), 1);
        player.sendMessage(color("&c☠ Выполнен экстренный возврат на базу..."));
        player.setHealth(0);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (pendingResets.contains(player.getUniqueId())) {
            pendingResets.remove(player.getUniqueId());
            String playerBase = findPlayerBase(player);
            if (playerBase != null) {
                FileConfiguration cfg = getConfig();
                String worldName = cfg.getString("bases." + playerBase + ".world");
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Location baseSpawn = new Location(world,
                        cfg.getDouble("bases." + playerBase + ".spawn.x"),
                        cfg.getDouble("bases." + playerBase + ".spawn.y"),
                        cfg.getDouble("bases." + playerBase + ".spawn.z"),
                        (float) cfg.getDouble("bases." + playerBase + ".spawn.yaw", 0f),
                        (float) cfg.getDouble("bases." + playerBase + ".spawn.pitch", 0f)
                    );
                    event.setRespawnLocation(baseSpawn);
                }
            }
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                giveResetItem(player);
            }
        }, 2L);
    }
    private void spawnLuckyBlockAnimated(Location loc, String base, String mobPoint,
            Consumer<Entity> callback, boolean playEffects, Long customTimer, Boolean customReady) {
        if (loc == null || base == null || mobPoint == null) {
            getLogger().severe("[LUCKY BLOCK] Null параметры при спавне!");
            if (callback != null) callback.accept(null);
            return;
        }
        World world = loc.getWorld();
        if (world == null) {
            getLogger().severe("[LUCKY BLOCK] Мир null!");
            if (callback != null) callback.accept(null);
            return;
        }
        Player nearestPlayer = null;
        double nearestDist = Double.MAX_VALUE;
        for (Player p : world.getPlayers()) {
            double d = p.getLocation().distanceSquared(loc);
            if (d < nearestDist) {
                nearestDist = d;
                nearestPlayer = p;
            }
        }
        if (nearestPlayer == null) {
            getLogger().warning("[LUCKY BLOCK] Нет игроков рядом для спавна!");
            if (callback != null) callback.accept(null);
            return;
        }
        List<String> allMobPoints = baseMobSpawnPoints.get(base);
        float yaw;
        if (allMobPoints != null) {
            int pointIndex = allMobPoints.indexOf(mobPoint);
            if (pointIndex == -1) pointIndex = 0;
            if (isReversedBase(base)) {
                yaw = (pointIndex >= 0 && pointIndex < 5) ? 90.0f : -90.0f;
            } else {
                yaw = (pointIndex >= 0 && pointIndex < 5) ? -90.0f : 90.0f;
            }
        } else {
            yaw = isReversedBase(base) ? 90.0f : -90.0f;
        }
        final float finalYaw = yaw;
        final String dim = world.getKey().toString();
        final boolean doEffects = playEffects;
        final Long finalCustomTimer = customTimer;
        final Boolean finalCustomReady = customReady;
        animatingPoints.add(mobPoint);
        String spawnMarker = "LB_EXISTING_" + System.currentTimeMillis();
        for (Entity e : world.getEntities()) {
            if (e.getType() == EntityType.ITEM_DISPLAY &&
                e.getScoreboardTags().contains("aj.luckyblock.root")) {
                e.addScoreboardTag(spawnMarker);
            }
        }
        Set<UUID> existingAJEntities = new HashSet<>();
        for (Entity e : world.getEntities()) {
            if (e.getScoreboardTags().contains("aj.luckyblock.entity")) {
                existingAJEntities.add(e.getUniqueId());
            }
        }
        String summonCmd = String.format(Locale.US,
                "execute as %s positioned %.3f %.3f %.3f run function animated_java:luckyblock/summon {args:{}}",
                nearestPlayer.getName(), loc.getX(), loc.getY(), loc.getZ());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), summonCmd);
        final Location spawnLoc = loc.clone();
        final String finalBase = base;
        final String finalMobPoint = mobPoint;
        final String finalSpawnMarker = spawnMarker;
        final Set<UUID> finalExistingAJ = existingAJEntities;
        new BukkitRunnable() {
            int tries = 0;
            @Override
            public void run() {
                tries++;
                String currentOwner = bases.get(finalBase);
                if (currentOwner == null || currentOwner.equals("none")) {
                    getLogger().warning("[LUCKY BLOCK] База " + finalBase +
                        " больше не имеет владельца, отменяем спавн");
                    cleanupSpawnMarker(world, finalSpawnMarker);
                    animatingPoints.remove(finalMobPoint);
                    Bukkit.getScheduler().runTask(BrainrotBases.this, () -> {
                        try {
                            for (Entity e : world.getNearbyEntities(spawnLoc, 3, 3, 3)) {
                                if (e.getType() == EntityType.ITEM_DISPLAY &&
                                    e.getScoreboardTags().contains("aj.luckyblock.root") &&
                                    !e.getScoreboardTags().contains(finalSpawnMarker) &&
                                    !luckyBlockTags.containsKey(e) &&
                                    !entityToPointMap.containsKey(e)) {
                                    String killTag = null;
                                    for (String tag : e.getScoreboardTags()) {
                                        if (tag.startsWith("base_lb_")) {
                                            killTag = tag;
                                            break;
                                        }
                                    }
                                    if (killTag != null) {
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                            "execute in " + dim + " run kill @e[tag=" + killTag + "]");
                                    }
                                    e.remove();
                                }
                            }
                        } catch (Exception ignored) {}
                    });
                    if (callback != null) callback.accept(null);
                    cancel();
                    return;
                }
                Entity root = null;
                double bestDist = Double.MAX_VALUE;
                for (Entity e : world.getNearbyEntities(spawnLoc, 5, 5, 5)) {
                    if (e.getType() != EntityType.ITEM_DISPLAY) continue;
                    if (!e.getScoreboardTags().contains("aj.luckyblock.root")) continue;
                    if (e.getPassengers().isEmpty()) continue;
                    if (e.getScoreboardTags().contains(finalSpawnMarker)) continue;
                    if (luckyBlockTags.containsKey(e)) continue;
                    if (entityToPointMap.containsKey(e)) continue;
                    double d = e.getLocation().distanceSquared(spawnLoc);
                    if (d < bestDist) {
                        bestDist = d;
                        root = e;
                    }
                }
                if (root == null) {
                    if (tries >= 60) {
                        getLogger().warning("[LUCKY BLOCK] Timeout!");
                        cleanupSpawnMarker(world, finalSpawnMarker);
                        animatingPoints.remove(finalMobPoint);
                        Set<String> occ = occupiedMobPoints.get(finalBase);
                        if (occ != null) occ.remove(finalMobPoint);
                        if (callback != null) callback.accept(null);
                        cancel();
                    }
                    return;
                }
                final Entity foundRoot = root;
                cleanupSpawnMarker(world, finalSpawnMarker);
                if (foundRoot instanceof Display dr) dr.setTeleportDuration(0);
                for (Entity pass : foundRoot.getPassengers()) {
                    if (pass instanceof Display dp) dp.setTeleportDuration(0);
                }
                String uniq = "base_lb_" + foundRoot.getUniqueId().toString().replace("-", "");
                foundRoot.addScoreboardTag(uniq);
                for (Entity e : world.getNearbyEntities(spawnLoc, 2, 2, 2)) {
                    if (e.getScoreboardTags().contains("aj.luckyblock.entity") &&
                        !finalExistingAJ.contains(e.getUniqueId())) {
                        e.addScoreboardTag(uniq);
                    }
                }
                for (Entity pass : foundRoot.getPassengers()) {
                    pass.addScoreboardTag(uniq);
                    for (Entity subPass : pass.getPassengers()) {
                        subPass.addScoreboardTag(uniq);
                    }
                }
                foundRoot.addScoreboardTag("LUCKY_BLOCK");
                foundRoot.addScoreboardTag("LUCKY_BLOCK_ANIMATED");
                foundRoot.addScoreboardTag("NO_DESPAWN");
                foundRoot.addScoreboardTag(BASE_MOB_TAG);
                foundRoot.addScoreboardTag("BASE_MOB_PERSISTENT");
                foundRoot.addScoreboardTag(MOB_TAG_PREFIX + "SPONGE");
                foundRoot.addScoreboardTag("MOB_RARITY_MYTHICAL");
                foundRoot.addScoreboardTag("MYTHICAL_MOB");
                foundRoot.setPersistent(true);
                luckyBlockTags.put(foundRoot, uniq);
                long currentTime = System.currentTimeMillis();
                if (finalCustomReady != null && finalCustomReady) {
                    luckyBlockReady.put(foundRoot, true);
                    luckyBlockOpenTime.put(foundRoot, 0L);
                } else if (finalCustomTimer != null && finalCustomTimer > 0) {
                    luckyBlockOpenTime.put(foundRoot, currentTime + finalCustomTimer);
                    luckyBlockReady.put(foundRoot, false);
                } else if (finalCustomTimer != null && finalCustomTimer <= 0) {
                    luckyBlockReady.put(foundRoot, true);
                    luckyBlockOpenTime.put(foundRoot, 0L);
                } else {
                    luckyBlockOpenTime.put(foundRoot, currentTime + LUCKY_BLOCK_TIMER);
                    luckyBlockReady.put(foundRoot, false);
                }
                final String rotateRootCmd = String.format(Locale.US,
                        "execute in %s run minecraft:tp @e[type=item_display,tag=aj.luckyblock.root,tag=%s,limit=1] %.3f %.3f %.3f %.1f 0",
                        dim, uniq,
                        spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(),
                        finalYaw);
                final String rotateAllCmd = String.format(Locale.US,
                        "execute in %s as @e[tag=%s] at @s run minecraft:tp @s ~ ~ ~ %.1f 0",
                        dim, uniq, finalYaw);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rotateRootCmd);
                for (long delay : new long[]{3L, 8L, 15L, 25L, 40L}) {
                    Bukkit.getScheduler().runTaskLater(BrainrotBases.this, () -> {
                        try {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rotateRootCmd);
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rotateAllCmd);
                        } catch (Exception ignored) {}
                    }, delay);
                }
                Interaction hitbox = (Interaction) world.spawnEntity(
                        spawnLoc.clone().add(0, 0.5, 0), EntityType.INTERACTION);
                hitbox.setInteractionWidth(1.2f);
                hitbox.setInteractionHeight(1.2f);
                hitbox.addScoreboardTag("LUCKY_BLOCK_HITBOX");
                hitbox.addScoreboardTag("NO_DESPAWN");
                hitbox.addScoreboardTag(BASE_MOB_TAG);
                hitbox.addScoreboardTag(uniq);
                hitbox.setPersistent(true);
                luckyBlockHitboxMap.put(foundRoot, hitbox);
                entityToPointMap.put(foundRoot, finalMobPoint);
                mobSpawnTime.put(foundRoot, System.currentTimeMillis());
                Set<String> occupied = occupiedMobPoints.get(finalBase);
                if (occupied != null) occupied.add(finalMobPoint);
                createMobHologram(foundRoot, MobType.SPONGE);
                Bukkit.getScheduler().runTaskLater(BrainrotBases.this, () -> {
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                "execute in " + dim +
                                " as @e[type=item_display,tag=aj.luckyblock.root,tag=" + uniq + ",limit=1] at @s " +
                                "run function animated_java:luckyblock/animations/wings_move/play");
                    } catch (Exception e) {
                        getLogger().warning("[LUCKY BLOCK] Ошибка анимации: " + e.getMessage());
                    }
                }, 5L);
                if (doEffects) {
                    world.spawnParticle(Particle.END_ROD, spawnLoc, 50, 0.5, 0.5, 0.5, 0.1);
                    world.playSound(spawnLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
                }
                animatingPoints.remove(finalMobPoint);
                debugLog("[LUCKY BLOCK] ✅ база=" + finalBase +
                               " точка=" + finalMobPoint + " тег=" + uniq +
                               " yaw=" + finalYaw);
                if (callback != null) callback.accept(foundRoot);
                cancel();
            }
        }.runTaskTimer(this, 1L, 1L);
    }
    private void spawnLuckyBlockAnimated(Location loc, String base, String mobPoint, Consumer<Entity> callback, boolean playEffects) {
        spawnLuckyBlockAnimated(loc, base, mobPoint, callback, playEffects, null, null);
    }
    private void spawnRotWalkerAnimated(Location loc, String base, String mobPoint,
            Consumer<Entity> callback, boolean playEffects) {
        if (loc == null || base == null || mobPoint == null) {
            if (callback != null) callback.accept(null);
            return;
        }
        World world = loc.getWorld();
        if (world == null) {
            if (callback != null) callback.accept(null);
            return;
        }
        Player nearestPlayer = null;
        double nearestDist = Double.MAX_VALUE;
        for (Player p : world.getPlayers()) {
            double d = p.getLocation().distanceSquared(loc);
            if (d < nearestDist) { nearestDist = d; nearestPlayer = p; }
        }
        if (nearestPlayer == null) {
            if (callback != null) callback.accept(null);
            return;
        }
        List<String> allMobPoints = baseMobSpawnPoints.get(base);
        float yaw;
        if (allMobPoints != null) {
            int pointIndex = allMobPoints.indexOf(mobPoint);
            if (pointIndex == -1) pointIndex = 0;
            if (isReversedBase(base)) {
                yaw = (pointIndex >= 0 && pointIndex < 5) ? 90.0f : -90.0f;
            } else {
                yaw = (pointIndex >= 0 && pointIndex < 5) ? -90.0f : 90.0f;
            }
        } else {
            yaw = isReversedBase(base) ? 90.0f : -90.0f;
        }
        final float finalYaw = yaw;
        final String dim = world.getKey().toString();
        animatingPoints.add(mobPoint);
        String spawnMarker = "RW_EXISTING_" + System.currentTimeMillis();
        for (Entity e : world.getEntities()) {
            if (e.getType() == EntityType.ITEM_DISPLAY &&
                e.getScoreboardTags().contains(TAG_ROT_WALKER_ROOT)) {
                e.addScoreboardTag(spawnMarker);
            }
        }
        Set<UUID> existingAJEntities = new HashSet<>();
        for (Entity e : world.getEntities()) {
            if (e.getScoreboardTags().contains(TAG_ROT_WALKER_ENTITY))
                existingAJEntities.add(e.getUniqueId());
        }
        String summonCmd = String.format(Locale.US,
                "execute as %s positioned %.3f %.3f %.3f run function animated_java:rotwalker/summon {args:{}}",
                nearestPlayer.getName(), loc.getX(), loc.getY(), loc.getZ());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), summonCmd);
        final Location spawnLoc = loc.clone();
        final String finalBase = base;
        final String finalMobPoint = mobPoint;
        final String finalSpawnMarker = spawnMarker;
        final Set<UUID> finalExistingAJ = existingAJEntities;
        new BukkitRunnable() {
            int tries = 0;
            @Override
            public void run() {
                tries++;
                String currentOwner = bases.get(finalBase);
                if (currentOwner == null || currentOwner.equals("none")) {
                    cleanupSpawnMarker(world, finalSpawnMarker);
                    animatingPoints.remove(finalMobPoint);
                    Set<String> occ = occupiedMobPoints.get(finalBase);
                    if (occ != null) occ.remove(finalMobPoint);
                    if (callback != null) callback.accept(null);
                    cancel();
                    return;
                }
                Entity root = null;
                double bestDist = Double.MAX_VALUE;
                for (Entity e : world.getNearbyEntities(spawnLoc, 5, 5, 5)) {
                    if (e.getType() != EntityType.ITEM_DISPLAY) continue;
                    if (!e.getScoreboardTags().contains(TAG_ROT_WALKER_ROOT)) continue;
                    if (e.getScoreboardTags().contains(finalSpawnMarker)) continue;
                    if (rotWalkerTags.containsKey(e)) continue;
                    if (entityToPointMap.containsKey(e)) continue;
                    double d = e.getLocation().distanceSquared(spawnLoc);
                    if (d < bestDist) { bestDist = d; root = e; }
                }
                if (root == null) {
                    if (tries >= 60) {
                        cleanupSpawnMarker(world, finalSpawnMarker);
                        animatingPoints.remove(finalMobPoint);
                        Set<String> occ = occupiedMobPoints.get(finalBase);
                        if (occ != null) occ.remove(finalMobPoint);
                        if (callback != null) callback.accept(null);
                        cancel();
                    }
                    return;
                }
                final Entity foundRoot = root;
                cleanupSpawnMarker(world, finalSpawnMarker);
                if (foundRoot instanceof Display dr) dr.setTeleportDuration(0);
                for (Entity pass : foundRoot.getPassengers()) {
                    if (pass instanceof Display dp) dp.setTeleportDuration(0);
                }
                String uniq = "base_rw_" + foundRoot.getUniqueId().toString().replace("-", "");
                foundRoot.addScoreboardTag(uniq);
                for (Entity e : world.getNearbyEntities(spawnLoc, 2, 2, 2)) {
                    if (e.getScoreboardTags().contains(TAG_ROT_WALKER_ENTITY) &&
                        !finalExistingAJ.contains(e.getUniqueId())) {
                        e.addScoreboardTag(uniq);
                    }
                }
                for (Entity pass : foundRoot.getPassengers()) {
                    pass.addScoreboardTag(uniq);
                    for (Entity sub : pass.getPassengers()) sub.addScoreboardTag(uniq);
                }
                foundRoot.addScoreboardTag(TAG_ROT_WALKER_BASE);
                foundRoot.addScoreboardTag("ROT_WALKER_ANIMATED");
                foundRoot.addScoreboardTag("NO_DESPAWN");
                foundRoot.addScoreboardTag(BASE_MOB_TAG);
                foundRoot.addScoreboardTag("BASE_MOB_PERSISTENT");
                foundRoot.addScoreboardTag(MOB_TAG_PREFIX + "ROT_WALKER");
                foundRoot.addScoreboardTag("MOB_RARITY_MYTHICAL");
                foundRoot.addScoreboardTag("MYTHICAL_MOB");
                foundRoot.setPersistent(true);
                rotWalkerTags.put(foundRoot, uniq);
                final String rotateRootCmd = String.format(Locale.US,
                        "execute in %s run minecraft:tp @e[type=item_display,tag=%s,tag=%s,limit=1] %.3f %.3f %.3f %.1f 0",
                        dim, TAG_ROT_WALKER_ROOT, uniq,
                        spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), finalYaw);
                final String rotateAllCmd = String.format(Locale.US,
                        "execute in %s as @e[tag=%s] at @s run minecraft:tp @s ~ ~ ~ %.1f 0",
                        dim, uniq, finalYaw);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rotateRootCmd);
                for (long delay : new long[]{3L, 8L, 15L, 25L, 40L}) {
                    Bukkit.getScheduler().runTaskLater(BrainrotBases.this, () -> {
                        try {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rotateRootCmd);
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rotateAllCmd);
                        } catch (Exception ignored) {}
                    }, delay);
                }
                Interaction hitbox = (Interaction) world.spawnEntity(
                        spawnLoc.clone().add(0, 0.5, 0), EntityType.INTERACTION);
                hitbox.setInteractionWidth(1.2f);
                hitbox.setInteractionHeight(2.0f);
                hitbox.addScoreboardTag("ROT_WALKER_HITBOX");
                hitbox.addScoreboardTag("NO_DESPAWN");
                hitbox.addScoreboardTag(BASE_MOB_TAG);
                hitbox.addScoreboardTag(uniq);
                hitbox.setPersistent(true);
                rotWalkerHitboxMap.put(foundRoot, hitbox);
                entityToPointMap.put(foundRoot, finalMobPoint);
                mobSpawnTime.put(foundRoot, System.currentTimeMillis());
                Set<String> occupied = occupiedMobPoints.get(finalBase);
                if (occupied != null) occupied.add(finalMobPoint);
                createMobHologram(foundRoot, MobType.ROT_WALKER);
                Bukkit.getScheduler().runTaskLater(BrainrotBases.this, () -> {
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                "execute in " + dim +
                                " as @e[type=item_display,tag=" + TAG_ROT_WALKER_ROOT + ",tag=" + uniq + ",limit=1] at @s " +
                                "run function animated_java:rotwalker/animations/animation_rotwalker_idle/play");
                    } catch (Exception e) {
                        getLogger().warning("[ROT WALKER] Ошибка анимации: " + e.getMessage());
                    }
                }, 5L);
                if (playEffects) {
                    world.spawnParticle(Particle.HAPPY_VILLAGER, spawnLoc, 40, 0.5, 0.8, 0.5, 0.05);
                    world.playSound(spawnLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.5f);
                }
                animatingPoints.remove(finalMobPoint);
                debugLog("[ROT WALKER] ✅ база=" + finalBase + " точка=" + finalMobPoint + " тег=" + uniq);
                if (callback != null) callback.accept(foundRoot);
                cancel();
            }
        }.runTaskTimer(this, 1L, 1L);
    }
    private void cleanupSpawnMarker(World world, String marker) {
        if (world == null || marker == null) return;
        for (Entity e : world.getEntities()) {
            e.removeScoreboardTag(marker);
        }
    }
    private void removeLuckyBlockFromBase(Entity rootEntity) {
        if (rootEntity == null) return;
        String uniqTag = luckyBlockTags.remove(rootEntity);
        Entity hitbox = luckyBlockHitboxMap.remove(rootEntity);
        if (hitbox != null && hitbox.isValid()) {
            hitbox.remove();
        }
        BukkitRunnable anim = luckyBlockAnimations.remove(rootEntity);
        if (anim != null) {
            try { anim.cancel(); } catch (Exception ignored) {}
        }
        removeMobHologram(rootEntity);
        if (uniqTag != null) {
            String dim = rootEntity.getWorld().getKey().toString();
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "execute in " + dim + " as @e[type=item_display,tag=" + uniqTag + ",limit=1] " +
                    "run function animated_java:luckyblock/remove");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "execute in " + dim + " run minecraft:kill @e[tag=" + uniqTag + "]");
        }
        String mobPoint = entityToPointMap.remove(rootEntity);
        mobSpawnTime.remove(rootEntity);
        if (mobPoint != null) {
            String collectorId = entityToCollectorMap.get(mobPoint);
            if (collectorId != null) {
                removeCollectorHologram(collectorId);
            }
            String baseName = findBaseByMobPoint(mobPoint);
            if (baseName != null) {
                Set<String> occupied = occupiedMobPoints.get(baseName);
                if (occupied != null) {
                    occupied.remove(mobPoint);
                }
            }
        }
        getLogger().info("Lucky Block удалён с базы");
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onResetItemDrop(PlayerDropItemEvent event) {
        if (isResetItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color("&c❌ Этот предмет нельзя выбросить!"));
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onResetItemMove(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();
        if (isResetItem(currentItem)) {
            if (event.getClickedInventory() != player.getInventory()) {
                event.setCancelled(true);
                player.sendMessage(color("&c❌ Этот предмет нельзя переместить сюда!"));
                return;
            }
            if (event.isShiftClick() && event.getView().getTopInventory() != player.getInventory()) {
                event.setCancelled(true);
                player.sendMessage(color("&c❌ Этот предмет нельзя переместить!"));
                return;
            }
        }
        if (isResetItem(cursorItem)) {
            if (event.getClickedInventory() != player.getInventory()) {
                event.setCancelled(true);
                return;
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onResetItemDrag(InventoryDragEvent event) {
        for (ItemStack item : event.getNewItems().values()) {
            if (isResetItem(item)) {
                if (event.getInventory() != event.getWhoClicked().getInventory()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onResetItemFrame(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof ItemFrame) {
            ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
            if (isResetItem(item)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(color("&c❌ Этот предмет нельзя поместить в рамку!"));
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeathDrops(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isResetItem);
    }
    private void createMobHologram(Entity mob, MobType type) {
        if (mob == null || type == null || hologramManager == null) return;
        String hologramName = "mob_" + mob.getUniqueId();
        for (Hologram h : new ArrayList<>(hologramManager.getHolograms())) {
            if (h.getData().getName().equals(hologramName)) {
                hologramManager.removeHologram(h);
            }
        }
        double heightOffset;
        if (type.isLuckyBlock()) {
            heightOffset = 1.8;
        } else if (type.isRotWalker()) {
            heightOffset = 2.3;
        } else {
            double baseOffset = 0.3;
            double mobHeight = mob.getBoundingBox().getHeight();
            heightOffset = (mobHeight * 1.0) + baseOffset;
            if (heightOffset < 0.5) heightOffset = 0.5;
            if (heightOffset > 3.0) heightOffset = 3.0;
        }
        float scale = 0.7f;
        if (type.isMythical()) {
            scale = 0.9f;
        } else if (type.isLegendary()) {
            scale *= 1.1f;
        } else if (type.isEpic()) {
            scale *= 1.05f;
        }
        Location loc = mob.getLocation().clone().add(0, heightOffset, 0);
        TextHologramData data = new TextHologramData(hologramName, loc);
        Mutation mutation = baseMobMutations.getOrDefault(mob, Mutation.NONE);
        boolean snowy = baseMobSnowy.getOrDefault(mob, false);
        double mutMult = getMobMutationMultiplier(mob);
        boolean hasMutation = (mutation != Mutation.NONE) || snowy;
        double actualIncome = type.baseIncome * mutMult;
        List<String> lines = new ArrayList<>();
        if (type.isLuckyBlock()) {
            lines.add(color("§e⏳ --:--"));
            lines.add(color("§e§l⭐ §fЛаки-Блок §e§l⭐"));
            lines.add(color(type.getRarityDisplay()));
            lines.add(color("§6" + formatNumber(type.sellPrice) + "$"));
        } else if (type.isRotWalker()) {
            if (hasMutation) {
                String mutLine = getMutationDisplayLine(mutation, snowy);
                if (!mutLine.isEmpty()) lines.add(color(mutLine));
            }
            lines.add(color("§2☣ §fГнилоход §2☣"));
            lines.add(color("§2§lИвентовый"));
            String incomeText = "§a+" + formatNumber(actualIncome) + "§2$§a/сек";
            if (hasMutation) {
                incomeText += " §7(";
                if (mutation != Mutation.NONE)
                    incomeText += mutation.format + "×" + String.format("%.1f", mutation.incomeMultiplier);
                if (snowy) {
                    if (mutation != Mutation.NONE) incomeText += "§7+";
                    incomeText += Mutation.SNOWY.format + "×" + String.format("%.0f", Mutation.SNOWY.incomeMultiplier);
                }
                incomeText += "§7)";
            }
            lines.add(color(incomeText));
            lines.add(color("§2" + formatNumber(type.sellPrice) + "$"));
        } else {
            if (hasMutation) {
                String mutLine = getMutationDisplayLine(mutation, snowy);
                if (!mutLine.isEmpty()) {
                    lines.add(color(mutLine));
                }
            }
            if (type.isMythical()) {
                lines.add(color("§d✦ §f" + type.name + " §d✦"));
            } else {
                lines.add(color("§f" + type.name));
            }
            lines.add(color(type.getRarityDisplay()));
            String incomeText = "§a+" + formatNumber(actualIncome) + "§2$§a/сек";
            if (hasMutation) {
                incomeText += " §7(";
                if (mutation != Mutation.NONE) {
                    incomeText += mutation.format + "×" + String.format("%.1f", mutation.incomeMultiplier);
                }
                if (snowy) {
                    if (mutation != Mutation.NONE) incomeText += "§7+";
                    incomeText += Mutation.SNOWY.format + "×" + String.format("%.0f", Mutation.SNOWY.incomeMultiplier);
                }
                incomeText += "§7)";
            }
            lines.add(color(incomeText));
            lines.add(color("§6" + formatNumber(type.sellPrice) + "$"));
        }
        if (isMobAuctionListed(mob)) {
            lines.add(0, color("§c§lВЫСТАВЛЕН НА АУКЦИОН"));
        }
        data.setText(lines);
        data.setScale(new Vector3f(scale, scale, scale));
        data.setBackground(Hologram.TRANSPARENT);
        data.setSeeThrough(true);
        data.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        try {
            Hologram holo = hologramManager.create(data);
            hologramManager.addHologram(holo);
        } catch (Exception e) {
            getLogger().warning("Ошибка создания голограммы: " + e.getMessage());
        }
        if (mutation == Mutation.RAINBOW) {
            startBaseRainbowAnimation(mob, type, snowy);
        }
    }
    private void startBaseRainbowAnimation(Entity mob, MobType type, boolean snowy) {
        BukkitRunnable oldAnim = baseRainbowAnimations.remove(mob);
        if (oldAnim != null) {
            try { oldAnim.cancel(); } catch (Exception ignored) {}
        }
        BukkitRunnable anim = new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (mob == null || mob.isDead() || !mob.isValid() || !entityToPointMap.containsKey(mob)) {
                    cancel();
                    baseRainbowAnimations.remove(mob);
                    return;
                }
                String hologramName = "mob_" + mob.getUniqueId();
                Optional<Hologram> holoOpt = hologramManager.getHologram(hologramName);
                if (holoOpt.isEmpty()) {
                    cancel();
                    baseRainbowAnimations.remove(mob);
                    return;
                }
                Hologram holo = holoOpt.get();
                if (!(holo.getData() instanceof TextHologramData textData)) return;
                List<String> lines = new ArrayList<>(textData.getText());
                int offset = tick % 7;
                int auctionOffset = (!lines.isEmpty() && ChatColor.stripColor(lines.get(0)).contains("АУКЦИОН")) ? 1 : 0;
                int rainbowLineIndex = auctionOffset + (snowy ? 1 : 0);
                if (rainbowLineIndex < lines.size()) {
                    lines.set(rainbowLineIndex, color(getRainbowText("Радужный", offset)));
                }
                double mutMult = Mutation.RAINBOW.incomeMultiplier * (snowy ? Mutation.SNOWY.incomeMultiplier : 1.0);
                double actualIncome = type.baseIncome * mutMult;
                for (int i = 0; i < lines.size(); i++) {
                    if (ChatColor.stripColor(lines.get(i)).contains("/сек")) {
                        String incomeText = "§a+" + formatNumber(actualIncome) + "§2$§a/сек §7("
                                + getRainbowText("×10", offset) + "§7)";
                        if (snowy) {
                            incomeText += " §7+ §b×5";
                        }
                        lines.set(i, color(incomeText));
                        break;
                    }
                }
                textData.setText(lines);
                holo.refreshForViewers();
                tick++;
            }
        };
        anim.runTaskTimer(this, 0L, 2L);
        baseRainbowAnimations.put(mob, anim);
    }
    private void tickMutationParticlesBase(Entity mob, Mutation mutation, long tick) {
        if (mob == null || mutation == null || mutation == Mutation.NONE) return;
        Location p = mob.getLocation().add(0, 0.8, 0);
        World world = mob.getWorld();
        switch (mutation) {
            case GOLD -> {
                if (tick % 6 == 0) {
                    world.spawnParticle(Particle.DUST, p, 2, 0.25, 0.25, 0.25, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 210, 40), 1.0f));
                }
            }
            case DIAMOND -> {
                if (tick % 6 == 0) {
                    world.spawnParticle(Particle.DUST, p, 2, 0.25, 0.25, 0.25, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(80, 255, 255), 1.0f));
                }
            }
            case SNOWY -> {
                if (tick % 4 == 0) {
                    world.spawnParticle(Particle.SNOWFLAKE, p, 2, 0.25, 0.25, 0.25, 0.0);
                }
            }
            case RAINBOW -> {
                if (tick % 2 == 0) {
                    org.bukkit.Color[] colors = {
                        org.bukkit.Color.RED, org.bukkit.Color.ORANGE, org.bukkit.Color.YELLOW,
                        org.bukkit.Color.GREEN, org.bukkit.Color.AQUA, org.bukkit.Color.BLUE,
                        org.bukkit.Color.PURPLE
                    };
                    org.bukkit.Color c = colors[(int) ((System.currentTimeMillis() / 120) % colors.length)];
                    world.spawnParticle(Particle.DUST, p, 2, 0.25, 0.25, 0.25, 0,
                            new Particle.DustOptions(c, 1.0f));
                }
            }
            default -> {}
        }
    }
    private String getRainbowText(String text, int offset) {
        String[] colors = {"§c", "§6", "§e", "§a", "§b", "§9", "§d"};
        StringBuilder result = new StringBuilder();
        int colorIndex = offset % colors.length;
        for (char c : text.toCharArray()) {
            if (c == ' ') {
                result.append(' ');
            } else {
                result.append(colors[colorIndex]).append("§l").append(c);
                colorIndex = (colorIndex + 1) % colors.length;
            }
        }
        return result.toString();
    }
    private void startMutationParticleTimer() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long tick = System.currentTimeMillis() / 50;
            for (Map.Entry<Entity, String> entry : new HashMap<>(entityToPointMap).entrySet()) {
                Entity mob = entry.getKey();
                if (mob == null || mob.isDead() || !mob.isValid()) continue;
                Mutation mutation = baseMobMutations.getOrDefault(mob, Mutation.NONE);
                boolean snowy = baseMobSnowy.getOrDefault(mob, false);
                if (mutation != Mutation.NONE) {
                    tickMutationParticlesBase(mob, mutation, tick);
                }
                if (snowy) {
                    tickMutationParticlesBase(mob, Mutation.SNOWY, tick);
                }
            }
        }, 0L, 1L);
    }
    private void tickMutationParticles(Entity mob, Mutation mutation, long tick) {
        if (mob == null || mutation == null || mutation == Mutation.NONE) return;
        Location p = mob.getLocation().add(0, 0.8, 0);
        World world = mob.getWorld();
        switch (mutation) {
            case GOLD -> {
                if (tick % 6 == 0) {
                    world.spawnParticle(Particle.DUST, p, 2, 0.25, 0.25, 0.25, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 210, 40), 1.0f));
                }
            }
            case DIAMOND -> {
                if (tick % 6 == 0) {
                    world.spawnParticle(Particle.DUST, p, 2, 0.25, 0.25, 0.25, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(80, 255, 255), 1.0f));
                }
            }
            case SNOWY -> {
                if (tick % 4 == 0) {
                    world.spawnParticle(Particle.SNOWFLAKE, p, 2, 0.25, 0.25, 0.25, 0.0);
                }
            }
            case RAINBOW -> {
                if (tick % 2 == 0) {
                    org.bukkit.Color[] colors = {
                        org.bukkit.Color.RED, org.bukkit.Color.ORANGE, org.bukkit.Color.YELLOW,
                        org.bukkit.Color.GREEN, org.bukkit.Color.AQUA, org.bukkit.Color.BLUE, org.bukkit.Color.PURPLE
                    };
                    org.bukkit.Color c = colors[(int) ((System.currentTimeMillis() / 120) % colors.length)];
                    world.spawnParticle(Particle.DUST, p, 2, 0.25, 0.25, 0.25, 0,
                            new Particle.DustOptions(c, 1.0f));
                }
            }
        }
    }
    private void removeMobHologram(Entity mob) {
        if (mob == null || hologramManager == null) return;
        String hologramName = "mob_" + mob.getUniqueId();
        Optional<Hologram> mobHologram = hologramManager.getHologram(hologramName);
        if (mobHologram.isPresent()) {
            hologramManager.removeHologram(mobHologram.get());
        }
    }
    private void cleanupOrphanedMobHolograms() {
        if (hologramManager == null) return;
        int cleaned = 0;
        Set<String> validMobUUIDs = new HashSet<>();
        for (Entity mob : entityToPointMap.keySet()) {
            if (mob != null && !mob.isDead()) {
                validMobUUIDs.add(mob.getUniqueId().toString());
            }
        }
        for (Hologram holo : new ArrayList<>(hologramManager.getHolograms())) {
            String name = holo.getData().getName();
            if (name.startsWith("mob_")) {
                String uuidPart = name.substring("mob_".length());
                if (!validMobUUIDs.contains(uuidPart)) {
                    hologramManager.removeHologram(holo);
                    cleaned++;
                    getLogger().info("Очищена осирот��вшая голограмма моба: " + name);
                }
            }
        }
        if (cleaned > 0) {
            getLogger().info("Очищено " + cleaned + " осиротевших голограмм мобов");
        }
    }
    private boolean isReversedBase(String base) {
        if (base == null) return false;
        String baseLower = base.toLowerCase();
        if (baseLower.contains("base4") ||
            baseLower.contains("base5") ||
            baseLower.contains("base6")) {
            return true;
        }
        FileConfiguration cfg = getConfig();
        if (cfg.contains("bases." + base + ".reversed")) {
            return cfg.getBoolean("bases." + base + ".reversed", false);
        }
        return false;
    }
    private String findBaseByEntity(Entity mob) {
        if (mob == null) return null;
        for (Map.Entry<String, List<String>> entry : baseMobSpawnPoints.entrySet()) {
            String base = entry.getKey();
            List<String> points = entry.getValue();
            String mobPoint = entityToPointMap.get(mob);
            if (mobPoint != null && points.contains(mobPoint)) {
                return base;
            }
        }
        return null;
    }
    private void handleMobCheck(Player player, String base) {
        String owner = bases.get(base);
        if (owner == null || !owner.equals(player.getName())) {
            return;
        }
        List<String> mobPoints = baseMobSpawnPoints.get(base);
        Set<String> occupied = occupiedMobPoints.get(base);
        if (mobPoints == null || occupied == null) {
            sendCooldownMessage(player, "§c❌ Ошибка базы!", lastCollectMessage);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            return;
        }
        if (occupied.size() >= mobPoints.size()) {
            sendCooldownMessage(player, "§c❌ На вашей базе нет свободных мест для мобов!", lastCollectMessage);
            sendCooldownMessage(player, "§eℹ Продайте одного из мобов, чтобы освободить место.", lastCollectMessage);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            return;
        }
        String freeMobPoint = null;
        for (String mobPoint : mobPoints) {
            if (!occupied.contains(mobPoint)) {
                freeMobPoint = mobPoint;
                break;
            }
        }
        if (freeMobPoint == null) {
            sendCooldownMessage(player, "§c❌ На базе нет свободных мест для мобов!", lastCollectMessage);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            return;
        }
        List<String> collectorPoints = baseCollectorPoints.get(base);
        String freeCollector = null;
        if (collectorPoints != null) {
            freeCollector = findFreeCollectorForBase(base, collectorPoints);
        }
        if (freeCollector == null && collectorPoints != null && !collectorPoints.isEmpty()) {
            freeCollector = collectorPoints.get(0);
        }
        boolean foundMob = false;
        List<Entity> passengers = player.getPassengers();
        for (Entity passenger : passengers) {
            if (!passenger.getScoreboardTags().contains(BASE_MOB_TAG)) {
                MobType type = determineMobType(passenger);
                if (type == null || type == MobType.CHICKEN) {
                    type = getMobTypeByEntityType(passenger.getType());
                    if (type == null) {
                        getLogger().warning("Не удалось определить тип моба: " + passenger.getType() +
                                           " у игрока " + player.getName());
                        continue;
                    }
                }
                passenger.remove();
                spawnMobAtPoint(base, freeMobPoint, freeCollector, type);
                String message = type.getRarityDisplay() + " ✔ " + type.name + " размещен(а) на базе!";
                sendCooldownMessage(player, message, lastCollectMessage);
                if (type.isEpic() || type.isLegendary()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1f);
                    player.getWorld().spawnParticle(Particle.DRAGON_BREATH, player.getLocation(), 30, 0.5, 1, 0.5, 0.1);
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
                }
                if (foundMob) {
                    String collectorId = entityToCollectorMap.get(freeMobPoint);
                    if (collectorId == null) {
                        getLogger().warning("Коллектор не создан для точки " + freeMobPoint + "! Создаем...");
                        List<String> collectorPointsList = baseCollectorPoints.get(base);
                        if (collectorPoints != null && !collectorPoints.isEmpty()) {
                            String collectorPoint = findFreeCollectorForBase(base, collectorPoints);
                            if (collectorPoint == null) {
                                collectorPoint = collectorPoints.get(0);
                            }
                            createCollectorHologramForMob(base, freeMobPoint, collectorPoint);
                        }
                    }
                }
                if (type.isMythical()) {
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 50, 0.7, 0.7, 0.7, 0.1);
                    player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.2);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendMessage("§d§l✦ " + player.getName() + " §fразместил §d§l" + type.name + "§f на своей базе! §d§l✦");
                    }
                    if (type.isLuckyBlock()) {
                        player.sendMessage("§e⚡ Лаки-Блок не приносит постоянного дохода, но даёт огромный бонус при продаже!");
                    }
                    if (type.isRotWalker()) {
                        player.sendMessage("§2☣ Гнилоход — ивентовый моб с уникальной моделью!");
                    }
                }
                foundMob = true;
                break;
            }
        }
        if (!foundMob) {
            for (Entity entity : player.getNearbyEntities(3.0, 3.0, 3.0)) {
                if (!entity.getScoreboardTags().contains(BASE_MOB_TAG)) {
                    double distance = entity.getLocation().distance(player.getLocation());
                    if (distance < 2.0) {
                        MobType type = determineMobType(entity);
                        if (type == null || type == MobType.CHICKEN) {
                            type = getMobTypeByEntityType(entity.getType());
                            if (type == null) {
                                getLogger().warning("Не удалось определить тип моба рядом: " + entity.getType());
                                continue;
                            }
                        }
                        entity.remove();
                        spawnMobAtPoint(base, freeMobPoint, freeCollector, type);
                        String message = type.getRarityDisplay() + " ✔ " + type.name + " размещен(а) на базе!";
                        sendCooldownMessage(player, message, lastCollectMessage);
                        if (type.isEpic() || type.isLegendary()) {
                            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1f);
                            player.getWorld().spawnParticle(Particle.DRAGON_BREATH, player.getLocation(), 30, 0.5, 1, 0.5, 0.1);
                        } else {
                            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
                        }
                        foundMob = true;
                        break;
                    }
                }
            }
        }
        if (!foundMob) {
            boolean hasRealMobPassenger = false;
            for (Entity passenger : passengers) {
                if (passenger == null || passenger.isDead() || !passenger.isValid()) continue;
                if (passenger instanceof ArmorStand) continue;
                if (!(passenger instanceof LivingEntity)) continue;
                if (passenger.getScoreboardTags().contains(BASE_MOB_TAG)) continue;
                if (passenger.getScoreboardTags().contains("LUCKY_BLOCK_CARRY_DISPLAY")) continue;
                hasRealMobPassenger = true;
                break;
            }
            if (hasRealMobPassenger) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                sendCooldownMessage(player, "§c❌ Не удалось определить тип моба!", lastCollectMessage);
                for (Entity passenger : passengers) {
                    getLogger().info("Пассажир: " + passenger.getType() +
                                   ", теги: " + passenger.getScoreboardTags() +
                                   ", имя: " + passenger.getCustomName());
                }
            }
        }
    }
    private MobType determineMobType(Entity entity) {
        if (entity == null) return MobType.CHICKEN;
        for (MobType mt : MobType.values()) {
            if (entity.getScoreboardTags().contains("MOB_" + mt.name())) {
                return mt;
            }
        }
        if (entity.getScoreboardTags().contains("LUCKY_BLOCK") ||
            entity.getScoreboardTags().contains("aj.luckyblock.root") ||
            entity.getScoreboardTags().contains("MOB_SPONGE")) {
            return MobType.SPONGE;
        }
        boolean isMythical = entity.getScoreboardTags().contains("MOB_RARITY_MYTHICAL") ||
                            entity.getScoreboardTags().contains("MYTHICAL_MOB");
        boolean isLegendary = entity.getScoreboardTags().contains("MOB_RARITY_LEGENDARY") ||
                             entity.getScoreboardTags().contains("LEGENDARY_MOB");
        for (MobType mt : MobType.values()) {
            if (entity.getType() == mt.type) {
                if (mt == MobType.CAT_KUZYA && entity instanceof Cat cat) {
                    if (cat.getCatType() == Cat.Type.RED) {
                        return mt;
                    }
                    continue;
                }
                if (mt == MobType.FOX && entity instanceof Fox fox) {
                    if (fox.getFoxType() == Fox.Type.RED) {
                        return mt;
                    }
                    continue;
                }
                if (mt == MobType.BROWN_PANDA && entity instanceof Panda panda) {
                    if (panda.getMainGene() == Panda.Gene.BROWN) {
                        return mt;
                    }
                    continue;
                }
                if (mt == MobType.PANDA && entity instanceof Panda panda) {
                    if (panda.getMainGene() != Panda.Gene.BROWN) {
                        return mt;
                    }
                    continue;
                }
                if (mt == MobType.MOOSHROOM && entity instanceof MushroomCow) {
                    return mt;
                }
                if (entity.getType() == EntityType.SKELETON_HORSE) {
                    if (isMythical || entity.getScoreboardTags().contains("MOB_MYTHIC_SKELETON_HORSE")) {
                        return MobType.MYTHIC_SKELETON_HORSE;
                    }
                    if (mt == MobType.SKELETON_HORSE) {
                        return mt;
                    }
                    continue;
                }
                if (entity.getType() == EntityType.HUSK) return MobType.HUSK;
                if (entity.getType() == EntityType.STRAY) return MobType.STRAY;
                if (entity.getType() == EntityType.DROWNED) return MobType.DROWNED;
                if (entity.getType() == EntityType.WITHER_SKELETON) return MobType.WITHER_SKELETON;
                if (entity.getType() == EntityType.PIGLIN_BRUTE) return MobType.PIGLIN_BRUTE;
                if (entity.getType() == EntityType.ZOMBIFIED_PIGLIN) return MobType.ZOMBIFIED_PIGLIN;
                if (entity.getType() == EntityType.CAVE_SPIDER) return MobType.CAVE_SPIDER;
                if (entity.getType() == EntityType.ELDER_GUARDIAN) return MobType.ELDER_GUARDIAN;
                if (mt == MobType.WARDEN && entity.getType() == EntityType.WARDEN) {
                    return mt;
                }
                if (mt == MobType.AXOLOTL && entity instanceof Axolotl axolotl) {
                    if (axolotl.getVariant() == Axolotl.Variant.BLUE || isMythical) {
                        return mt;
                    }
                    continue;
                }
                if (mt == MobType.GOAT && entity.getType() == EntityType.GOAT) {
                    if (isMythical || entity.getScoreboardTags().contains("MOB_GOAT")) {
                        return mt;
                    }
                    continue;
                }
                if (mt == MobType.FROG && entity.getType() == EntityType.FROG) {
                    if (isMythical || entity.getScoreboardTags().contains("MOB_FROG")) {
                        return mt;
                    }
                    continue;
                }
                if (mt == MobType.GLOW_SQUID && entity.getType() == EntityType.GLOW_SQUID) {
                    if (isMythical || entity.getScoreboardTags().contains("MOB_GLOW_SQUID")) {
                        return mt;
                    }
                    continue;
                }
                if (mt == MobType.CREEPER && entity instanceof Creeper creeper) {
                    if (creeper.isPowered() || isMythical) {
                        return mt;
                    }
                    continue;
                }
                if (entity.getType() == EntityType.PIGLIN) {
                    if (isMythical || entity.getScoreboardTags().contains("MOB_PIGLIN")) {
                        return MobType.PIGLIN;
                    }
                    continue;
                }
                if (entity.getType() == EntityType.PIGLIN_BRUTE) {
                    return MobType.PIGLIN_BRUTE;
                }
                if (mt == MobType.ZOMBIFIED_PIGLIN && entity.getType() == EntityType.ZOMBIFIED_PIGLIN) {
                    return mt;
                }
                if (mt == MobType.ALLAY && entity.getType() == EntityType.ALLAY) {
                    return mt;
                }
                if (mt == MobType.SNIFFER && entity.getType() == EntityType.SNIFFER) {
                    return mt;
                }
                if (mt == MobType.ZOGLIN && entity.getType() == EntityType.ZOGLIN) {
                    return mt;
                }
                if (mt == MobType.CAMEL && entity.getType() == EntityType.CAMEL) {
                    return mt;
                }
                if (mt == MobType.SNOW_GOLEM && entity.getType() == EntityType.SNOW_GOLEM) {
                    return mt;
                }
                if (mt == MobType.WANDERING_TRADER && entity.getType() == EntityType.WANDERING_TRADER) {
                    return mt;
                }
                if (mt == MobType.DROWNED && entity.getType() == EntityType.DROWNED) {
                    return mt;
                }
                return mt;
            }
        }
        return MobType.CHICKEN;
    }
    private MobType getMobTypeByEntityType(EntityType entityType) {
        if (entityType == null) return null;
        Set<EntityType> skipTypes = Set.of(
            EntityType.CAT,
            EntityType.PANDA,
            EntityType.SKELETON_HORSE,
            EntityType.PIGLIN,
            EntityType.ITEM_DISPLAY
        );
        if (skipTypes.contains(entityType)) {
            return null;
        }
        for (MobType mt : MobType.values()) {
            if (mt.type == entityType) {
                if (mt == MobType.CAT_KUZYA) continue;
                if (mt == MobType.BROWN_PANDA) continue;
                if (mt == MobType.MYTHIC_SKELETON_HORSE) continue;
                if (mt == MobType.SPONGE) continue;
                return mt;
            }
        }
        return null;
    }
    private void handleLockPoint(Player player, String base) {
        String owner = bases.get(base);
        if (owner == null || !owner.equals(player.getName())) {
            return;
        }
        lockBase(base, player);
    }
    private void handleMoneyCollection(Player player, String base, String collectorPoint) {
        String owner = bases.get(base);
        if (owner == null || !owner.equals(player.getName())) {
            return;
        }
        boolean isLocked = baseLocked.getOrDefault(base, false);
        if (isLocked && !player.getName().equals(owner)) {
            sendCooldownMessage(player, "§c⛔ База заблокирована! Собирать деньги может только владелец.", lastCollectMessage);
            return;
        }
        String collectorId = base + "_" + collectorPoint;
        if (!collectorToEntityMap.containsKey(collectorId)) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        Map<String, Long> baseCooldowns = collectorCooldowns.computeIfAbsent(base, k -> new ConcurrentHashMap<>());
        Long lastCollectTime = baseCooldowns.get(collectorPoint);
        if (lastCollectTime != null && currentTime - lastCollectTime < COLLECT_COOLDOWN) {
            long timeLeft = (COLLECT_COOLDOWN - (currentTime - lastCollectTime)) / 1000;
            if (timeLeft > 0) {
                sendCooldownMessage(player, "§e⏳ Подождите " + timeLeft + " секунд перед сбором с этого сборщика!", lastCollectMessage);
                return;
            }
        }
        double moneyToCollect;
        synchronized (moneyLock) {
            double currentMoney = collectorMoney.getOrDefault(collectorId, 0.0);
            int moneyInt = (int) Math.floor(currentMoney);
            if (moneyInt < MIN_COLLECT_AMOUNT) {
                sendCooldownMessage(player, "§eℹ Недостаточно денег для сбора! Нужно минимум §a$" + MIN_COLLECT_AMOUNT, lastCollectMessage);
                return;
            }
            moneyToCollect = moneyInt;
            double remaining = currentMoney - moneyToCollect;
            if (remaining < 0) remaining = 0.0;
            collectorMoney.put(collectorId, remaining);
        }
        baseCooldowns.put(collectorPoint, currentTime);
        double multiplier = getPlayerEarnMultiplier(player);
        if (economy != null) {
            double actualMoney = moneyToCollect * multiplier;
            economy.depositPlayer(player, actualMoney);
        } else {
            sendCooldownMessage(player, "§a✔ Собрано: §e$" + moneyToCollect + " (система экономики не найдена)", lastCollectMessage);
        }
        updateCollectorHologram(collectorId);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
    }
    private void loadEconomyConfig() {
        FileConfiguration cfg = getConfig();
        cfg.addDefault("economy.initial-balance", INITIAL_BALANCE);
        cfg.addDefault("economy.min-collect-amount", MIN_COLLECT_AMOUNT);
        cfg.addDefault("economy.steal-time-seconds", STEAL_TIME);
        cfg.addDefault("economy.collect-cooldown-ms", COLLECT_COOLDOWN);
        cfg.addDefault("economy.lucky-block-timer-ms", LUCKY_BLOCK_TIMER);
        cfg.addDefault("economy.base-lock-duration-seconds", LOCK_DURATION);
        if (!cfg.isSet("economy.rebirth.earn-multipliers")) {
            java.util.List<Double> em = new java.util.ArrayList<>();
            for (double d : REBIRTH_EARN_MULTIPLIERS) em.add(d);
            cfg.addDefault("economy.rebirth.earn-multipliers", em);
        }
        if (!cfg.isSet("economy.rebirth.starting-money")) {
            java.util.List<Double> sm = new java.util.ArrayList<>();
            sm.add(REBIRTH_STARTING_MONEY_1); sm.add(REBIRTH_STARTING_MONEY_2);
            sm.add(REBIRTH_STARTING_MONEY_3); sm.add(REBIRTH_STARTING_MONEY_4);
            sm.add(REBIRTH_STARTING_MONEY_5); sm.add(REBIRTH_STARTING_MONEY_6);
            sm.add(REBIRTH_STARTING_MONEY_7); sm.add(REBIRTH_STARTING_MONEY_8);
            cfg.addDefault("economy.rebirth.starting-money", sm);
        }
        if (!cfg.isSet("economy.rebirth.lock-durations")) {
            java.util.List<Integer> ld = new java.util.ArrayList<>();
            ld.add(REBIRTH_LOCK_DURATION_1); ld.add(REBIRTH_LOCK_DURATION_2);
            ld.add(REBIRTH_LOCK_DURATION_3); ld.add(REBIRTH_LOCK_DURATION_4);
            ld.add(REBIRTH_LOCK_DURATION_5); ld.add(REBIRTH_LOCK_DURATION_6);
            ld.add(REBIRTH_LOCK_DURATION_7); ld.add(REBIRTH_LOCK_DURATION_8);
            cfg.addDefault("economy.rebirth.lock-durations", ld);
        }
        for (Mutation m : Mutation.values()) {
            if (m == Mutation.NONE) continue;
            cfg.addDefault("economy.mutations." + m.name() + ".income-multiplier", m.incomeMultiplier);
        }
        for (MobType mt : MobType.values()) {
            cfg.addDefault("economy.mobs." + mt.name() + ".base-income", mt.baseIncome);
            cfg.addDefault("economy.mobs." + mt.name() + ".sell-price", mt.sellPrice);
        }
        cfg.options().copyDefaults(true);
        saveConfig();
        INITIAL_BALANCE = cfg.getDouble("economy.initial-balance", INITIAL_BALANCE);
        MIN_COLLECT_AMOUNT = cfg.getInt("economy.min-collect-amount", MIN_COLLECT_AMOUNT);
        STEAL_TIME = cfg.getInt("economy.steal-time-seconds", STEAL_TIME);
        COLLECT_COOLDOWN = cfg.getLong("economy.collect-cooldown-ms", COLLECT_COOLDOWN);
        LUCKY_BLOCK_TIMER = cfg.getLong("economy.lucky-block-timer-ms", LUCKY_BLOCK_TIMER);
        LOCK_DURATION = cfg.getInt("economy.base-lock-duration-seconds", LOCK_DURATION);
        java.util.List<Double> em = cfg.getDoubleList("economy.rebirth.earn-multipliers");
        if (em != null && !em.isEmpty()) {
            double[] arr = new double[em.size()];
            for (int i = 0; i < em.size(); i++) arr[i] = em.get(i);
            REBIRTH_EARN_MULTIPLIERS = arr;
        }
        java.util.List<Double> sm = cfg.getDoubleList("economy.rebirth.starting-money");
        if (sm != null && sm.size() >= 8) {
            REBIRTH_STARTING_MONEY_1 = sm.get(0); REBIRTH_STARTING_MONEY_2 = sm.get(1);
            REBIRTH_STARTING_MONEY_3 = sm.get(2); REBIRTH_STARTING_MONEY_4 = sm.get(3);
            REBIRTH_STARTING_MONEY_5 = sm.get(4); REBIRTH_STARTING_MONEY_6 = sm.get(5);
            REBIRTH_STARTING_MONEY_7 = sm.get(6); REBIRTH_STARTING_MONEY_8 = sm.get(7);
        }
        java.util.List<Integer> ld = cfg.getIntegerList("economy.rebirth.lock-durations");
        if (ld != null && ld.size() >= 8) {
            REBIRTH_LOCK_DURATION_1 = ld.get(0); REBIRTH_LOCK_DURATION_2 = ld.get(1);
            REBIRTH_LOCK_DURATION_3 = ld.get(2); REBIRTH_LOCK_DURATION_4 = ld.get(3);
            REBIRTH_LOCK_DURATION_5 = ld.get(4); REBIRTH_LOCK_DURATION_6 = ld.get(5);
            REBIRTH_LOCK_DURATION_7 = ld.get(6); REBIRTH_LOCK_DURATION_8 = ld.get(7);
        }
        for (Mutation m : Mutation.values()) {
            if (m == Mutation.NONE) continue;
            m.incomeMultiplier = cfg.getDouble("economy.mutations." + m.name() + ".income-multiplier", m.incomeMultiplier);
        }
        for (MobType mt : MobType.values()) {
            mt.baseIncome = cfg.getDouble("economy.mobs." + mt.name() + ".base-income", mt.baseIncome);
            mt.sellPrice = cfg.getInt("economy.mobs." + mt.name() + ".sell-price", mt.sellPrice);
        }
        getLogger().info("[Economy] Конфиг экономики загружен: мобов=" + MobType.values().length + ", мутаций=" + (Mutation.values().length - 1) + ".");
    }

    private double getPlayerEarnMultiplier(Player player) {
        int rebirthCount = getRebirthCount(player);
        if (rebirthCount >= 1) {
            int idx = Math.min(rebirthCount, REBIRTH_EARN_MULTIPLIERS.length - 1);
            return REBIRTH_EARN_MULTIPLIERS[idx];
        }
        return playerEarnMultipliers.getOrDefault(player.getUniqueId(), 1.0);
    }
    private void setPlayerEarnMultiplier(Player player) {
        int rebirthCount = getRebirthCount(player);
        double multiplier;
        if (rebirthCount >= 1) {
            int idx = Math.min(rebirthCount, REBIRTH_EARN_MULTIPLIERS.length - 1);
            multiplier = REBIRTH_EARN_MULTIPLIERS[idx];
        } else {
            multiplier = 1.0;
        }
        playerEarnMultipliers.put(player.getUniqueId(), multiplier);
    }
    private void registerRebirthCommand() {
        PluginCommand command = getCommand("rebirth");
        if (command != null) {
            command.setExecutor(new CommandExecutor() {
                @Override
                public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("§cЭта команда только для игроков!");
                        return true;
                    }
                    Player player = (Player) sender;
                    openRebirthMenu(player);
                    return true;
                }
            });
        }
    }
    private void openRebirthMenu(Player player) {
        int rebirthCount = getRebirthCount(player);
        if (rebirthCount >= 8) {
            showMaxRebirthMessage(player);
            return;
        }
        int nextRebirthLevel = rebirthCount + 1;
        Inventory menu = Bukkit.createInventory(null, 27, "§6§lПерерождение §7[" + nextRebirthLevel + "]");
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 27; i++) { menu.setItem(i, glass); }
        ItemStack rebirthItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta rebirthMeta = rebirthItem.getItemMeta();
        rebirthMeta.setDisplayName("§a§lПерерождение §7[" + nextRebirthLevel + "]");
        List<String> lore = new ArrayList<>();
        lore.add("§7Требования для перерождения:");
        if (rebirthCount == 4) {
            lore.add(" §8• §fБаланс: §6$100М");
            lore.add(" §8• §dВерблюд: §a1 шт.");
            lore.add(" §8• §dКоричневая панда: §a1 шт.");
        } else if (rebirthCount == 5) {
            lore.add(" §8• §fБаланс: §6$350М");
            lore.add(" §8• §dКрипер: §a1 шт.");
        } else if (rebirthCount == 6) {
            lore.add(" §8• §fБаланс: §6$1 МЛРД");
            lore.add(" §8• §dУтопленник: §a1 шт.");
        } else if (rebirthCount == 7) {
            lore.add(" §8• §fБаланс: §6$5 МЛРД");
            lore.add(" §8• §dЛягушка: §a1 шт.");
        } else {
            if (rebirthCount == 0) { lore.add(" §8• §fБаланс: §6$500К"); lore.add(" §8• §fЧерепаха: §a1 шт."); lore.add(" §8• §fСвинья: §a1 шт."); }
            else if (rebirthCount == 1) { lore.add(" §8• §fБаланс: §6$1.5М"); lore.add(" §8• §fКот Кузя: §a1 шт."); lore.add(" §8• §fБульбульдог: §a1 шт."); }
            else if (rebirthCount == 2) { lore.add(" §8• §fБаланс: §6$7.5М"); lore.add(" §8• §5Страж: §a1 шт."); lore.add(" §8• §5Древний страж: §a1 шт."); }
            else if (rebirthCount == 3) { lore.add(" §8• §fБаланс: §6$25М"); lore.add(" §8• §6Магмовый куб: §a1 шт."); lore.add(" §8• §6Страйдер: §a1 шт."); }
        }
        lore.add("");
        lore.add("§7После перерождения вы получите:");
        if (rebirthCount == 4) {
            lore.add(" §8• §fНачальные деньги: §6$100,000");
            lore.add(" §8• §fМножитель заработка: §b2.0x");
            lore.add(" §8• §eПри закрытии базы: 110 секунд");
        } else if (rebirthCount == 5) {
            lore.add(" §8• §fНачальные деньги: §6$250,000");
            lore.add(" §8• §fМножитель заработка: §b3.0x");
            lore.add(" §8• §eПри закрытии базы: 120 секунд");
        } else if (rebirthCount == 6) {
            lore.add(" §8• §fНачальные деньги: §6$500,000");
            lore.add(" §8• §fМножитель заработка: §b4.0x");
            lore.add(" §8• §eПри закрытии базы: 130 секунд");
        } else if (rebirthCount == 7) {
            lore.add(" §8• §fНачальные деньги: §6$1,000,000");
            lore.add(" §8• §fМножитель заработка: §b5.0x");
            lore.add(" §8• §eПри закрытии базы: 140 секунд");
        } else {
            if (rebirthCount == 0) { lore.add(" §8• §fНачальные деньги: §6$5,000"); lore.add(" §8• §fМножитель: §bx1.2"); lore.add(" §8• §eПри закрытии: 70 секунд"); }
            else if (rebirthCount == 1) { lore.add(" §8• §fНачальные деньги: §6$5,000"); lore.add(" §8• §fМножитель: §bx1.4"); lore.add(" §8• §eПри закрытии: 80 секунд"); }
            else if (rebirthCount == 2) { lore.add(" §8• §fНачальные деньги: §6$25,000"); lore.add(" §8• §fМножитель: §bx1.6"); lore.add(" §8• §eПри закрытии: 90 секунд"); }
            else if (rebirthCount == 3) { lore.add(" §8• §fНачальные деньги: §6$50,000"); lore.add(" §8• §fМножитель: §bx1.8"); lore.add(" §8• §eПри закрытии: 100 секунд"); }
        }
        lore.add(" §8• §cВсе мобы на базе будут удалены!");
        lore.add("");
        double balance = getPlayerBalance(player);
        boolean hasMoney = false;
        boolean hasFirstMob = false;
        boolean hasSecondMob = false;
        if (rebirthCount == 4) {
            hasMoney = balance >= 100000000;
            int camelCount = countPlayerMobsByType(player, MobType.CAMEL);
            int pandaCount = countPlayerMobsByType(player, MobType.BROWN_PANDA);
            hasFirstMob = camelCount >= 1;
            hasSecondMob = pandaCount >= 1;
            lore.add("§7Ваш прогресс:");
            lore.add(" §8• §fБаланс: " + (hasMoney ? "§a" : "§c") + formatMoney(balance) + "/$100М");
            lore.add(" §8• §dВерблюд: " + (hasFirstMob ? "§aЕсть" : "§cНет"));
            lore.add(" §8• §dКор. панда: " + (hasSecondMob ? "§aЕсть" : "§cНет"));
        } else if (rebirthCount == 5) {
            hasMoney = balance >= 350000000;
            int creeperCount = countPlayerMobsByType(player, MobType.CREEPER);
            hasFirstMob = creeperCount >= 1;
            hasSecondMob = true;
            lore.add("§7Ваш прогресс:");
            lore.add(" §8• §fБаланс: " + (hasMoney ? "§a" : "§c") + formatMoney(balance) + "/$350М");
            lore.add(" §8• §dКрипер: " + (hasFirstMob ? "§aЕсть" : "§cНет"));
        } else if (rebirthCount == 6) {
            hasMoney = balance >= 1000000000;
            int drownedCount = countPlayerMobsByType(player, MobType.DROWNED);
            hasFirstMob = drownedCount >= 1;
            hasSecondMob = true;
            lore.add("§7Ваш прогресс:");
            lore.add(" §8• §fБаланс: " + (hasMoney ? "§a" : "§c") + formatMoney(balance) + "/$1 МЛРД");
            lore.add(" §8• §dУтопленник: " + (hasFirstMob ? "§aЕсть" : "§cНет"));
        } else if (rebirthCount == 7) {
            hasMoney = balance >= 5000000000L;
            int frogCount = countPlayerMobsByType(player, MobType.FROG);
            hasFirstMob = frogCount >= 1;
            hasSecondMob = true;
            lore.add("§7Ваш прогресс:");
            lore.add(" §8• §fБаланс: " + (hasMoney ? "§a" : "§c") + formatMoney(balance) + "/$5 МЛРД");
            lore.add(" §8• §dЛягушка: " + (hasFirstMob ? "§aЕсть" : "§cНет"));
        } else {
            if (rebirthCount == 0) {
                hasMoney = balance >= 500000;
                int turtleCount = countPlayerMobs(player, EntityType.TURTLE);
                int pigCount = countPlayerMobs(player, EntityType.PIG);
                hasFirstMob = turtleCount >= 1;
                hasSecondMob = pigCount >= 1;
                lore.add("§7Ваш прогресс:");
                lore.add(" §8• §fБаланс: " + (hasMoney ? "§a" : "§c") + formatMoney(balance) + "/$500,000");
                lore.add(" §8• §fЧерепаха: " + (hasFirstMob ? "§aЕсть" : "§cНет"));
                lore.add(" §8• §fСвинья: " + (hasSecondMob ? "§aЕсть" : "§cНет"));
            } else if (rebirthCount == 1) {
                hasMoney = balance >= 1500000;
                int catCount = countPlayerMobsByType(player, MobType.CAT_KUZYA);
                int wolfCount = countPlayerMobs(player, EntityType.WOLF);
                hasFirstMob = catCount >= 1;
                hasSecondMob = wolfCount >= 1;
                lore.add("§7Ваш прогресс:");
                lore.add(" §8• §fБаланс: " + (hasMoney ? "§a" : "§c") + formatMoney(balance) + "/$1,500,000");
                lore.add(" §8• §fКот Кузя: " + (hasFirstMob ? "§aЕсть" : "§cНет"));
                lore.add(" §8• §fБульбульдог: " + (hasSecondMob ? "§aЕсть" : "§cНет"));
            } else if (rebirthCount == 2) {
                hasMoney = balance >= 7500000;
                int guardianCount = countPlayerMobs(player, EntityType.GUARDIAN);
                int elderGuardianCount = countPlayerMobs(player, EntityType.ELDER_GUARDIAN);
                hasFirstMob = guardianCount >= 1;
                hasSecondMob = elderGuardianCount >= 1;
                lore.add("§7Ваш прогресс:");
                lore.add(" §8• §fБаланс: " + (hasMoney ? "§a" : "§c") + formatMoney(balance) + "/$7,500,000");
                lore.add(" §8• §5Страж: " + (hasFirstMob ? "§aЕсть" : "§cНет"));
                lore.add(" §8• §5Древний страж: " + (hasSecondMob ? "§aЕсть" : "§cНет"));
            } else if (rebirthCount == 3) {
                hasMoney = balance >= 25000000;
                int magmaCubeCount = countPlayerMobs(player, EntityType.MAGMA_CUBE);
                int striderCount = countPlayerMobs(player, EntityType.STRIDER);
                hasFirstMob = magmaCubeCount >= 1;
                hasSecondMob = striderCount >= 1;
                lore.add("§7Ваш прогресс:");
                lore.add(" §8• §fБаланс: " + (hasMoney ? "§a" : "§c") + formatMoney(balance) + "/$25,000,000");
                lore.add(" §8• §6Магмовый куб: " + (hasFirstMob ? "§aЕсть" : "§cНет"));
                lore.add(" §8• §6Страйдер: " + (hasSecondMob ? "§aЕсть" : "§cНет"));
            }
        }
        int totalMobsOnBase = countAllPlayerMobs(player);
        lore.add(" §8• §fВсего мобов на базе: §e" + totalMobsOnBase + " шт.");
        lore.add("");
        if (hasMoney && hasFirstMob && hasSecondMob) {
            lore.add("§a§l✓ Вы готовы к перерождению!");
            lore.add("§7Нажмите, чтобы открыть подтверждение");
            rebirthMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
        } else {
            lore.add("§c§l✗ Требования не выполнены!");
        }
        rebirthMeta.setLore(lore);
        rebirthItem.setItemMeta(rebirthMeta);
        menu.setItem(13, rebirthItem);
        player.openInventory(menu);
        registerRebirthMenuHandler(player, menu, totalMobsOnBase, rebirthCount);
    }
    private void showMaxRebirthMessage(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, "§6§lМаксимальное перерождение");
        ItemStack glass = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 27; i++) {
            menu.setItem(i, glass);
        }
        ItemStack maxItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta maxMeta = maxItem.getItemMeta();
        maxMeta.setDisplayName("§6§l★ МАКСИМАЛЬНЫЙ УРОВЕНЬ ★");
        maxMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§aПоздравляем! Вы достигли");
        lore.add("§aмаксимального уровня перерождения!");
        lore.add("");
        lore.add("§7Ваши бонусы:");
        lore.add(" §8• §fМножитель заработка: §b5.0x");
        lore.add(" §8• §fВремя блокировки базы: §e140 секунд");
        lore.add("");
        lore.add("§eСпасибо за игру в ранней версии");
        lore.add("§6§lSteal a Mob!");
        lore.add("");
        lore.add("§7К сожалению, это все перерождения");
        lore.add("§7на данный момент.");
        lore.add("");
        lore.add("§aСледите за новостями в ТГК:");
        lore.add("§b§nt.me/stealamob");
        lore.add("");
        lore.add("§8Нажмите для закрытия");
        maxMeta.setLore(lore);
        maxItem.setItemMeta(maxMeta);
        menu.setItem(13, maxItem);
        ItemStack star = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta starMeta = star.getItemMeta();
        starMeta.setDisplayName("§6★");
        star.setItemMeta(starMeta);
        menu.setItem(11, star);
        menu.setItem(15, star);
        menu.setItem(4, star);
        menu.setItem(22, star);
        player.openInventory(menu);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        player.sendMessage("");
        player.sendMessage("§6§l══════════════════════════════════");
        player.sendMessage("§e§l     ★ МАКСИМАЛЬНОЕ ПЕРЕРОЖДЕНИЕ ★");
        player.sendMessage("");
        player.sendMessage("§aСпасибо за игру в ранней версии §6§lSteal a Mob§a!");
        player.sendMessage("");
        player.sendMessage("§7К сожалению, это все перерождения");
        player.sendMessage("§7на данный момент.");
        player.sendMessage("");
        player.sendMessage("§eСледите за новостями в нашем ТГК:");
        player.sendMessage("§b§n https://t.me/stealamob");
        player.sendMessage("§6§l══════════════════════════════════");
        player.sendMessage("");
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                Bukkit.getPluginManager().registerEvents(new Listener() {
                    @EventHandler
                    public void onInventoryClick(InventoryClickEvent event) {
                        if (event.getInventory().equals(menu) && event.getWhoClicked().equals(player)) {
                            event.setCancelled(true);
                            player.closeInventory();
                        }
                    }
                    @EventHandler
                    public void onInventoryClose(InventoryCloseEvent event) {
                        if (event.getInventory().equals(menu) && event.getPlayer().equals(player)) {
                            HandlerList.unregisterAll(this);
                        }
                    }
                }, BrainrotBases.this);
            }
        }, 1L);
    }
    private int countPlayerMobsByType(Player player, MobType mobType) {
        int count = 0;
        String playerBase = findPlayerBase(player);
        if (playerBase == null) {
            return 0;
        }
        List<String> mobPoints = baseMobSpawnPoints.get(playerBase);
        if (mobPoints == null) {
            return 0;
        }
        for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
            Entity mob = entry.getKey();
            String mobPoint = entry.getValue();
            if (mobPoints.contains(mobPoint)) {
                MobType type = MobType.fromEntity(mob);
                if (type != null && type == mobType) {
                    count++;
                }
            }
        }
        return count;
    }
    private void registerRebirthMenuHandler(Player player, Inventory menu, int totalMobs, int rebirthCount) {
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                Bukkit.getPluginManager().registerEvents(new Listener() {
                    @EventHandler
                    public void onInventoryClick(InventoryClickEvent event) {
                        if (event.getInventory().equals(menu) && event.getWhoClicked().equals(player)) {
                            event.setCancelled(true);
                            if (event.getSlot() == 13) {
                                player.closeInventory();
                                openRebirthConfirmationMenu(player, totalMobs, rebirthCount);
                            }
                        }
                    }
                    @EventHandler
                    public void onInventoryClose(InventoryCloseEvent event) {
                        if (event.getInventory().equals(menu) && event.getPlayer().equals(player)) {
                            HandlerList.unregisterAll(this);
                        }
                    }
                }, BrainrotBases.this);
            }
        }, 1L);
    }
    private void openRebirthConfirmationMenu(Player player, int totalMobs, int rebirthCount) {
        Inventory menu = Bukkit.createInventory(null, 27, "§6§lПодтверждение перерождения");
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 27; i++) { menu.setItem(i, glass); }
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§6§lИнформация о перерождении");
        List<String> lore = new ArrayList<>();
        lore.add("§7На вашей базе сейчас:");
        lore.add(" §8• §fВсего мобов: §c" + totalMobs + " шт.");
        if (rebirthCount == 4) {
            int camelCount = countPlayerMobsByType(player, MobType.CAMEL);
            int pandaCount = countPlayerMobsByType(player, MobType.BROWN_PANDA);
            lore.add(" §8• §dВерблюдов: §a" + camelCount + " шт.");
            lore.add(" §8• §dКоричневых панд: §a" + pandaCount + " шт.");
        } else if (rebirthCount == 5) {
            int creeperCount = countPlayerMobsByType(player, MobType.CREEPER);
            lore.add(" §8• §dКриперов: §a" + creeperCount + " шт.");
        } else {
            if (rebirthCount == 0) {
                int turtleCount = countPlayerMobs(player, EntityType.TURTLE);
                int pigCount = countPlayerMobs(player, EntityType.PIG);
                lore.add(" §8• §fЧерепах: §a" + turtleCount + " шт.");
                lore.add(" §8• §fСвиней: §a" + pigCount + " шт.");
            } else if (rebirthCount == 1) {
                int catCount = countPlayerMobsByType(player, MobType.CAT_KUZYA);
                int wolfCount = countPlayerMobs(player, EntityType.WOLF);
                lore.add(" §8• §fКотов Кузя: §a" + catCount + " шт.");
                lore.add(" §8• §fБульдогов: §a" + wolfCount + " шт.");
            } else if (rebirthCount == 2) {
                int guardianCount = countPlayerMobs(player, EntityType.GUARDIAN);
                int elderGuardianCount = countPlayerMobs(player, EntityType.ELDER_GUARDIAN);
                lore.add(" §8• §5Стражей: §a" + guardianCount + " шт.");
                lore.add(" §8• §5Древних стражей: §a" + elderGuardianCount + " шт.");
            } else if (rebirthCount == 3) {
                int magmaCubeCount = countPlayerMobs(player, EntityType.MAGMA_CUBE);
                int striderCount = countPlayerMobs(player, EntityType.STRIDER);
                lore.add(" §8• §6Магмовых кубов: §a" + magmaCubeCount + " шт.");
                lore.add(" §8• §6Страйдеров: §a" + striderCount + " шт.");
            }
        }
        lore.add("");
        lore.add("§c⚠ После перерождения:");
        lore.add(" §8• §cВсе мобы будут удалены!");
        int newRebirthCount = rebirthCount + 1;
        if (newRebirthCount == 5) {
            lore.add(" §8• §fБаланс: §6$100,000");
            lore.add(" §8• §fМножитель: §b2.0x (супер!)");
            lore.add(" §8• §eПри закрытии базы: 110 секунд");
        } else if (newRebirthCount == 6) {
            lore.add(" §8• §fБаланс: §6$250,000");
            lore.add(" §8• §fМножитель: §b3.0x (МАКС!)");
            lore.add(" §8• §eПри закрытии базы: 120 секунд");
        } else {
            if (newRebirthCount == 1) {
                lore.add(" §8• §fБаланс: §6$5,000");
                lore.add(" §8• §fМножитель: §bx1.2 (новый!)");
                lore.add(" §8• §eПри закрытии базы: 70 секунд");
            } else if (newRebirthCount == 2) {
                lore.add(" §8• §fБаланс: §6$5,000");
                lore.add(" §8• §fМножитель: §bx1.4 (улучшенный!)");
                lore.add(" §8• §eПри закрытии базы: 80 секунд");
            } else if (newRebirthCount == 3) {
                lore.add(" §8• §fБаланс: §6$25,000");
                lore.add(" §8• §fМножитель: §bx1.6 (улучшенный!)");
                lore.add(" §8• §eПри закрытии базы: 90 секунд");
            } else if (newRebirthCount == 4) {
                lore.add(" §8• §fБаланс: §6$50,000");
                lore.add(" §8• §fМножитель: §bx1.8 (высокий!)");
                lore.add(" §8• §eПри закрытии базы: 100 секунд");
            }
        }
        lore.add("");
        lore.add("§eВы уверены, что хотите продолжить?");
        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        menu.setItem(13, info);
        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName("§a§lДА, ПЕРЕРОДИТЬСЯ");
        confirm.setItemMeta(confirmMeta);
        menu.setItem(11, confirm);
        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName("§c§lОТМЕНИТЬ");
        cancel.setItemMeta(cancelMeta);
        menu.setItem(15, cancel);
        player.openInventory(menu);
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                Bukkit.getPluginManager().registerEvents(new Listener() {
                    @EventHandler
                    public void onInventoryClick(InventoryClickEvent event) {
                        if (event.getInventory().equals(menu) && event.getWhoClicked().equals(player)) {
                            event.setCancelled(true);
                            if (event.getSlot() == 11) {
                                player.closeInventory();
                                attemptRebirth(player);
                            } else if (event.getSlot() == 15) {
                                player.closeInventory();
                                player.sendMessage("§e❌ Перерождение отменено.");
                            }
                        }
                    }
                    @EventHandler
                    public void onInventoryClose(InventoryCloseEvent event) {
                        if (event.getInventory().equals(menu) && event.getPlayer().equals(player)) {
                            HandlerList.unregisterAll(this);
                        }
                    }
                }, BrainrotBases.this);
            }
        }, 1L);
    }
    private void attemptRebirth(Player player) {
        int rebirthCount = getRebirthCount(player);
        double balance = getPlayerBalance(player);
        if (rebirthCount == 0) {
            if (balance < 500000) { player.sendMessage("§cНедостаточно $! Нужно $500,000."); return; }
            int turtleCount = countPlayerMobs(player, EntityType.TURTLE);
            int pigCount = countPlayerMobs(player, EntityType.PIG);
            if (turtleCount < 1 || pigCount < 1) { player.sendMessage("§cНужны Черепаха и Свинья!"); return; }
            executeRebirth(player, turtleCount, pigCount, rebirthCount);
        } else if (rebirthCount == 1) {
            if (balance < 1500000) { player.sendMessage("§cНедостаточно $! Нужно $1.5М."); return; }
            int catCount = countPlayerMobsByType(player, MobType.CAT_KUZYA);
            int wolfCount = countPlayerMobs(player, EntityType.WOLF);
            if (catCount < 1 || wolfCount < 1) { player.sendMessage("§cНужны Кот Кузя и Бульбульдог!"); return; }
            executeRebirth(player, catCount, wolfCount, rebirthCount);
        } else if (rebirthCount == 2) {
            if (balance < 7500000) { player.sendMessage("§cНедостаточно $! Нужно $7.5М."); return; }
            int guardianCount = countPlayerMobs(player, EntityType.GUARDIAN);
            int elderCount = countPlayerMobs(player, EntityType.ELDER_GUARDIAN);
            if (guardianCount < 1 || elderCount < 1) { player.sendMessage("§cНужны Страж и Древний страж!"); return; }
            executeRebirth(player, guardianCount, elderCount, rebirthCount);
        } else if (rebirthCount == 3) {
            if (balance < 25000000) { player.sendMessage("§cНедостаточно $! Нужно $25М."); return; }
            int magmaCount = countPlayerMobs(player, EntityType.MAGMA_CUBE);
            int striderCount = countPlayerMobs(player, EntityType.STRIDER);
            if (magmaCount < 1 || striderCount < 1) { player.sendMessage("§cНужны Магмовый куб и Страйдер!"); return; }
            executeRebirth(player, magmaCount, striderCount, rebirthCount);
        } else if (rebirthCount == 4) {
            if (balance < 100000000) { player.sendMessage("§cНедостаточно $! Нужно $100М."); return; }
            int camelCount = countPlayerMobsByType(player, MobType.CAMEL);
            int pandaCount = countPlayerMobsByType(player, MobType.BROWN_PANDA);
            if (camelCount < 1) { player.sendMessage("§cНужен хотя бы один §dВерблюд§c!"); return; }
            if (pandaCount < 1) { player.sendMessage("§cНужна хотя бы одна §dКоричневая панда§c!"); return; }
            executeRebirth(player, camelCount, pandaCount, rebirthCount);
        } else if (rebirthCount == 5) {
            if (balance < 350000000) { player.sendMessage("§cНедостаточно $! Нужно $350М."); return; }
            int creeperCount = countPlayerMobsByType(player, MobType.CREEPER);
            if (creeperCount < 1) { player.sendMessage("§cНужен хотя бы один §dКрипер§c!"); return; }
            executeRebirth(player, creeperCount, 0, rebirthCount);
        } else if (rebirthCount == 6) {
            if (balance < 1000000000.0) {
                player.sendMessage("§cНедостаточно $! Нужно $1 Миллиард.");
                return;
            }
            int drownedCount = countPlayerMobsByType(player, MobType.DROWNED);
            player.sendMessage("§8[Debug] Найдено утопленников: " + drownedCount);
            if (drownedCount < 1) {
                player.sendMessage("§cНужен хотя бы один §dУтопленник§c!");
                return;
            }
            executeRebirth(player, drownedCount, 0, rebirthCount);
        } else if (rebirthCount == 7) {
            if (balance < 5000000000.0) {
                player.sendMessage("§cНедостаточно $! Нужно $5 Миллиардов.");
                return;
            }
            int frogCount = countPlayerMobsByType(player, MobType.FROG);
            if (frogCount < 1) {
                player.sendMessage("§cНужна хотя бы одна §dЛягушка§c!");
                return;
            }
            executeRebirth(player, frogCount, 0, rebirthCount);
        } else {
            player.sendMessage("§cОшибка: Неизвестный уровень перерождения (" + rebirthCount + ")");
        }
    }
    private void executeRebirth(Player player, int firstMobCount, int secondMobCount, int currentRebirthCount) {
        try {
            org.bukkit.plugin.Plugin extrasPlugin = Bukkit.getPluginManager().getPlugin("BrainrotExtras");
            if (extrasPlugin != null) {
                extrasPlugin.getClass().getMethod("cancelPlayerBets", Player.class).invoke(extrasPlugin, player);
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Не удалось очистить ставки казино для " + player.getName() + " (возможно плагин Extras не загружен)");
        }
        double startingMoney;
        if (currentRebirthCount >= 7) {
            startingMoney = REBIRTH_STARTING_MONEY_8;
        } else if (currentRebirthCount >= 6) {
            startingMoney = REBIRTH_STARTING_MONEY_7;
        } else if (currentRebirthCount >= 5) {
            startingMoney = REBIRTH_STARTING_MONEY_6;
        } else if (currentRebirthCount >= 4) {
            startingMoney = REBIRTH_STARTING_MONEY_5;
        } else if (currentRebirthCount >= 3) {
            startingMoney = REBIRTH_STARTING_MONEY_4;
        } else if (currentRebirthCount >= 2) {
            startingMoney = REBIRTH_STARTING_MONEY_3;
        } else if (currentRebirthCount >= 1) {
            // __fix__ was missing: rebirth #2 silently fell through to MONEY_1 (worked only because both == 5000)
            startingMoney = REBIRTH_STARTING_MONEY_2;
        } else {
            startingMoney = REBIRTH_STARTING_MONEY_1;
        }
        if (economy != null) {
            double currentBalance = economy.getBalance(player);
            if (currentBalance < startingMoney) {
                economy.depositPlayer(player, startingMoney - currentBalance);
            } else if (currentBalance > startingMoney) {
                economy.withdrawPlayer(player, currentBalance - startingMoney);
            }
            player.sendMessage("§e§l! §fВаши деньги установлены на: §6$" + formatNumber((int)startingMoney));
        }
        String playerBase = findPlayerBase(player);
        int removedCount = 0;
        if (playerBase != null) {
            removedCount = removeAllPlayerMobs(player);
            Set<String> occupied = occupiedMobPoints.get(playerBase);
            if (occupied != null) occupied.clear();
            cleanupCollectorLinksForBase(playerBase);
        }
        savedPlayerMobs.remove(player.getName());
        if (mobsConfig != null) {
            mobsConfig.set("mobs." + player.getName(), null);
            try { saveConfigAsync(mobsConfig, mobsFile); } catch (IOException e) {}
        }
        saveRebirthData(player);
        setPlayerEarnMultiplier(player);
        int newRebirthCount = getRebirthCount(player);
        double newMultiplier = getPlayerEarnMultiplier(player);
        player.sendMessage("§6§l══════════════════════════════════");
        player.sendMessage("§a§l          ПЕРЕРОЖДЕНИЕ #" + newRebirthCount + " УСПЕШНО!");
        player.sendMessage("");
        player.sendMessage("§f• §7Удалено мобов с базы: §c" + removedCount + " шт.");
        if (currentRebirthCount == 4) {
            player.sendMessage("§f• §dВерблюдов удалено: §c" + firstMobCount + " шт.");
            player.sendMessage("§f• §dКор. панд удалено: §c" + secondMobCount + " шт.");
        } else if (currentRebirthCount == 5) {
            player.sendMessage("§f• §dКриперов удалено: §c" + firstMobCount + " шт.");
        } else if (currentRebirthCount == 6) {
            player.sendMessage("§f• §dУтопленников удалено: §c" + firstMobCount + " шт.");
        } else if (currentRebirthCount == 7) {
            player.sendMessage("§f• §dЛягушек удалено: §c" + firstMobCount + " шт.");
        } else {
            if (currentRebirthCount == 0) {
                player.sendMessage("§f• §7Черепах удалено: §c" + firstMobCount + " шт.");
                player.sendMessage("§f• §7Свиней удалено: §c" + secondMobCount + " шт.");
            } else if (currentRebirthCount == 1) {
                player.sendMessage("§f• §7Котов Кузя удалено: §c" + firstMobCount + " шт.");
                player.sendMessage("§f• §7Бульдогов удалено: §c" + secondMobCount + " шт.");
            } else if (currentRebirthCount == 2) {
                player.sendMessage("§f• §5Стражей удалено: §c" + firstMobCount + " шт.");
                player.sendMessage("§f• §5Древних стражей удалено: §c" + secondMobCount + " шт.");
            } else if (currentRebirthCount == 3) {
                player.sendMessage("§f• §6Магмовых кубов удалено: §c" + firstMobCount + " шт.");
                player.sendMessage("§f• §6Страйдеров удалено: §c" + secondMobCount + " шт.");
            }
        }
        player.sendMessage("§f• §7База: §aОТКРЫТА");
        player.sendMessage("§f• §7Баланс: §6$" + formatNumber((int)startingMoney));
        player.sendMessage("§f• §7Множитель: §b" + newMultiplier + "x");
        if (newRebirthCount >= 8) {
            player.sendMessage("§f• §7При закрытии базы: §e140 секунд");
        } else if (newRebirthCount >= 7) {
            player.sendMessage("§f• §7При закрытии базы: §e130 секунд");
        } else if (newRebirthCount >= 6) {
            player.sendMessage("§f• §7При закрытии базы: §e120 секунд");
        } else if (newRebirthCount >= 5) {
            player.sendMessage("§f• §7При закрытии базы: §e110 секунд");
        } else if (newRebirthCount >= 4) {
            player.sendMessage("§f• §7При закрытии базы: §e100 секунд");
        } else {
            player.sendMessage("§f• §7При закрытии базы: §e" + (newRebirthCount == 3 ? "90" : newRebirthCount == 2 ? "80" : "70") + " секунд");
        }
        player.sendMessage("§6§l══════════════════════════════════");
        savePlayerMobsToFile(player.getName());
    }
    private double getPlayerBalance(Player player) {
        if (economy != null) {
            return economy.getBalance(player);
        }
        return 0;
    }
    private int countPlayerMobs(Player player, EntityType type) {
        int count = 0;
        String playerBase = findPlayerBase(player);
        if (playerBase == null) {
            return 0;
        }
        List<String> mobPoints = baseMobSpawnPoints.get(playerBase);
        if (mobPoints == null) {
            return 0;
        }
        for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
            Entity mob = entry.getKey();
            String mobPoint = entry.getValue();
            if (mobPoints.contains(mobPoint) && mob.getType() == type) {
                count++;
            }
        }
        return count;
    }
    private int countAllPlayerMobs(Player player) {
        int count = 0;
        String playerBase = findPlayerBase(player);
        if (playerBase == null) {
            return 0;
        }
        List<String> mobPoints = baseMobSpawnPoints.get(playerBase);
        if (mobPoints == null) {
            return 0;
        }
        for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
            Entity mob = entry.getKey();
            String mobPoint = entry.getValue();
            if (mobPoints.contains(mobPoint)) {
                count++;
            }
        }
        return count;
    }
    public Location getNearestSubmitLocation(Player player, String baseName) {
        if (baseName == null || player == null) {
            getLogger().warning("getNearestSubmitLocation: player или baseName = null");
            return null;
        }
        List<String> points = baseSubmitPoints.get(baseName);
        if (points == null || points.isEmpty()) {
            points = baseCollectorPoints.get(baseName);
        }
        if (points == null || points.isEmpty()) {
            points = baseMobSpawnPoints.get(baseName);
        }
        if (points == null || points.isEmpty()) {
            getLogger().warning("База " + baseName + " не имеет никаких точек!");
            return null;
        }
        Location nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        Location playerLoc = player.getLocation();
        for (String point : points) {
            String[] parts = point.split("_");
            if (parts.length < 4) continue;
            try {
                World world = Bukkit.getWorld(parts[0]);
                if (world == null || !world.equals(playerLoc.getWorld())) continue;
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[3]);
                int y;
                if (parts[2].equalsIgnoreCase("ANY")) {
                    y = playerLoc.getBlockY();
                } else {
                    y = Integer.parseInt(parts[2]);
                }
                Location pointLoc = new Location(world, x + 0.5, y, z + 0.5);
                double distance = playerLoc.distance(pointLoc);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = pointLoc;
                }
            } catch (NumberFormatException e) {
                continue;
            }
        }
        if (nearest != null) {
            debugLog("Найдена точка сдачи для " + player.getName() +
                            " на расстоянии " + String.format("%.1f", nearestDistance));
        }
        return nearest;
    }
    private int removeAllPlayerMobs(Player player) {
        String playerBase = findPlayerBase(player);
        if (playerBase == null) {
            return 0;
        }
        List<String> mobPoints = baseMobSpawnPoints.get(playerBase);
        if (mobPoints == null) {
            return 0;
        }
        List<Entity> toRemove = new ArrayList<>();
        for (Map.Entry<Entity, String> entry : new HashMap<>(entityToPointMap).entrySet()) {
            Entity mob = entry.getKey();
            String mobPoint = entry.getValue();
            if (mobPoints.contains(mobPoint)) {
                toRemove.add(mob);
            }
        }
        int removedCount = 0;
        for (Entity mob : toRemove) {
            String mobPoint = entityToPointMap.get(mob);
            removeMobHologram(mob);
            if (mobPoint != null) {
                String collectorId = entityToCollectorMap.get(mobPoint);
                if (collectorId != null) {
                    removeCollectorHologram(collectorId);
                    getLogger().info("Удалена голограмма коллектора: " + collectorId);
                    collectorToEntityMap.remove(collectorId);
                    entityToCollectorMap.remove(mobPoint);
                    synchronized (moneyLock) {
                        collectorMoney.remove(collectorId);
                        collectorLastUpdate.remove(collectorId);
                    }
                }
            }
            entityToPointMap.remove(mob);
            mobSpawnTime.remove(mob);
            if (mob != null) {
                mob.removeScoreboardTag(BASE_MOB_TAG);
                mob.removeScoreboardTag("BASE_MOB_PERSISTENT");
                mob.removeScoreboardTag("NO_DESPAWN");
                for (MobType type : MobType.values()) {
                    mob.removeScoreboardTag(MOB_TAG_PREFIX + type.name());
                }
                if (!mob.isDead()) {
                    mob.remove();
                    removedCount++;
                    getLogger().info("Моб " + mob.getType() + " успешно удален");
                }
            }
        }
        synchronized (moneyLock) {
            for (String collectorId : new ArrayList<>(collectorToEntityMap.keySet())) {
                if (collectorId.startsWith(playerBase + "_")) {
                    collectorMoney.remove(collectorId);
                    collectorLastUpdate.remove(collectorId);
                }
            }
        }
        for (Map.Entry<String, String> entry : new ArrayList<>(collectorToEntityMap.entrySet())) {
            if (entry.getKey().startsWith(playerBase + "_")) {
                collectorToEntityMap.remove(entry.getKey());
            }
        }
        for (Map.Entry<String, String> entry : new ArrayList<>(entityToCollectorMap.entrySet())) {
            if (findBaseByMobPoint(entry.getKey()) != null && findBaseByMobPoint(entry.getKey()).equals(playerBase)) {
                entityToCollectorMap.remove(entry.getKey());
            }
        }
        if (mobsConfig != null) {
            mobsConfig.set("mobs." + player.getName(), null);
            try {
                saveConfigAsync(mobsConfig, mobsFile);
            } catch (IOException e) {
                getLogger().warning("Ошибка сохранения: " + e.getMessage());
            }
        }
        return removedCount;
    }
    private String findPlayerBase(Player player) {
        for (Map.Entry<String, String> entry : bases.entrySet()) {
            if (entry.getValue().equals(player.getName())) {
                return entry.getKey();
            }
        }
        return null;
    }
    private void saveRebirthData(Player player) {
        if (player == null) return;
        if (rebirthsConfig == null) initRebirthsFile();
        String key = "rebirths." + player.getUniqueId().toString();
        int current = rebirthsConfig.getInt(key + ".count", 0);
        int updated = current + 1;
        rebirthsConfig.set(key + ".name", player.getName());
        rebirthsConfig.set(key + ".count", updated);
        rebirthsConfig.set(key + ".last", System.currentTimeMillis());
        saveRebirthsFile();
    }
    public int getRebirthCount(Player player) {
        if (player == null) return 0;
        if (rebirthsConfig == null) initRebirthsFile();
        return rebirthsConfig.getInt("rebirths." + player.getUniqueId().toString() + ".count", 0);
    }
    public long getLastRebirthTime(Player player) {
        if (player == null) return 0L;
        if (rebirthsConfig == null) initRebirthsFile();
        return rebirthsConfig.getLong("rebirths." + player.getUniqueId().toString() + ".last", 0L);
    }
    public String getTimeSinceLastRebirth(Player player) {
        long lastRebirth = getLastRebirthTime(player);
        if (lastRebirth == 0) {
            return "никогда";
        }
        long currentTime = System.currentTimeMillis();
        long diff = currentTime - lastRebirth;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        if (days > 0) {
            return days + "д " + (hours % 24) + "ч";
        } else if (hours > 0) {
            return hours + "ч " + (minutes % 60) + "м";
        } else if (minutes > 0) {
            return minutes + "м " + (seconds % 60) + "с";
        } else {
            return seconds + "с";
        }
    }
 public List<String> getSubmitPoints(String baseName) {
     return baseSubmitPoints.get(baseName);
 }
public List<String> getCollectorPoints(String baseName) {
    return baseCollectorPoints.get(baseName);
}
public List<String> getMobPoints(String baseName) {
    return baseMobSpawnPoints.get(baseName);
}
 public static BrainrotBases getInstance() {
     return JavaPlugin.getPlugin(BrainrotBases.class);
 }
    public double getPlayerMultiplier(Player player) {
        return getPlayerEarnMultiplier(player);
    }
    public int getRebirthLevel(Player player) {
        int rebirthCount = getRebirthCount(player);
        return rebirthCount + 1;
    }
    public String getNextRebirthRequirements(Player player) {
        int rebirthCount = getRebirthCount(player);
        if (rebirthCount == 0) return "$500К, 1 черепаха, 1 свинья";
        if (rebirthCount == 1) return "$1.5М, 1 Кот Кузя, 1 Бульбульдог";
        if (rebirthCount == 2) return "$7.5М, 1 Страж, 1 Древний страж";
        if (rebirthCount == 3) return "$25М, 1 Магмовый куб, 1 Страйдер";
        if (rebirthCount == 4) return "$100М, 1 Верблюд, 1 Кор. панда";
        if (rebirthCount == 5) return "$350М, 1 Крипер";
        if (rebirthCount == 6) return "$1МЛРД, 1 Утопленник";
        if (rebirthCount == 7) return "$5МЛРД, 1 Лягушка";
        return "§6★ Максимум достигнут! ★";
    }
    public String getRebirthBonus(Player player) {
        int rebirthCount = getRebirthCount(player);
        if (rebirthCount == 0) return "нет";
        if (rebirthCount == 1) return "x1.2, 70 сек";
        if (rebirthCount == 2) return "x1.4, 80 сек";
        if (rebirthCount == 3) return "x1.6, 90 сек";
        if (rebirthCount == 4) return "x1.8, 100 сек";
        if (rebirthCount == 5) return "x2.0, 110 сек";
        if (rebirthCount == 6) return "x3.0, 120 сек";
        if (rebirthCount == 7) return "x4.0, 130 сек";
        return "x5.0, 140 сек (МАКС)";
    }
    public class BrainrotPlaceholder extends PlaceholderExpansion {
        private final BrainrotBases plugin;
        public BrainrotPlaceholder(BrainrotBases plugin) {
            this.plugin = plugin;
        }
        @Override
        public boolean persist() {
            return true;
        }
        @Override
        public boolean canRegister() {
            return true;
        }
        @Override
        public String getAuthor() {
            return plugin.getDescription().getAuthors().toString();
        }
        @Override
        public String getIdentifier() {
            return "brainrotbases";
        }
        @Override
        public String getVersion() {
            return plugin.getDescription().getVersion();
        }
        @Override
        public String onPlaceholderRequest(Player player, String identifier) {
            if (player == null) {
                return "";
            }
            switch (identifier.toLowerCase()) {
                case "rebirth_count":
                    return String.valueOf(plugin.getRebirthCount(player));
                case "rebirth_level":
                    return String.valueOf(plugin.getRebirthLevel(player));
                case "last_rebirth_time":
                    long lastTime = plugin.getLastRebirthTime(player);
                    if (lastTime == 0) return "никогда";
                    Date date = new Date(lastTime);
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm");
                    return sdf.format(date);
                case "time_since_last_rebirth":
                    return plugin.getTimeSinceLastRebirth(player);
                case "player_multiplier":
                    return String.format("%.1f", plugin.getPlayerMultiplier(player));
                case "next_rebirth_requirements":
                    return plugin.getNextRebirthRequirements(player);
                case "rebirth_bonus":
                    return plugin.getRebirthBonus(player);
                case "has_base":
                    return plugin.findPlayerBase(player) != null ? "да" : "нет";
                case "base_name":
                    String base = plugin.findPlayerBase(player);
                    return base != null ? base : "нет";
                case "base_mobs_count":
                    return String.valueOf(plugin.countAllPlayerMobs(player));
                case "base_free_slots":
                    String playerBase = plugin.findPlayerBase(player);
                    if (playerBase == null) return "0";
                    List<String> mobPoints = baseMobSpawnPoints.get(playerBase);
                    Set<String> occupied = occupiedMobPoints.get(playerBase);
                    if (mobPoints == null || occupied == null) return "0";
                    return String.valueOf(mobPoints.size() - occupied.size());
                case "base_total_slots":
                    playerBase = plugin.findPlayerBase(player);
                    if (playerBase == null) return "0";
                    mobPoints = baseMobSpawnPoints.get(playerBase);
                    return mobPoints != null ? String.valueOf(mobPoints.size()) : "0";
                case "base_occupied_slots":
                    playerBase = plugin.findPlayerBase(player);
                    if (playerBase == null) return "0";
                    occupied = occupiedMobPoints.get(playerBase);
                    return occupied != null ? String.valueOf(occupied.size()) : "0";
                case "base_locked":
                    playerBase = plugin.findPlayerBase(player);
                    if (playerBase == null) return "нет";
                    return baseLocked.getOrDefault(playerBase, false) ? "да" : "нет";
                case "base_lock_time":
                    playerBase = plugin.findPlayerBase(player);
                    if (playerBase == null) return "0";
                    return String.valueOf(baseLockTime.getOrDefault(playerBase, 0));
                case "mobs_turtle_count":
                    return String.valueOf(countPlayerMobs(player, EntityType.TURTLE));
                case "mobs_pig_count":
                    return String.valueOf(countPlayerMobs(player, EntityType.PIG));
                case "mobs_cat_kuzya_count":
                    return String.valueOf(countPlayerMobsByType(player, MobType.CAT_KUZYA));
                case "mobs_wolf_count":
                    return String.valueOf(countPlayerMobs(player, EntityType.WOLF));
                case "mobs_chicken_count":
                    return String.valueOf(countPlayerMobs(player, EntityType.CHICKEN));
                case "mobs_cow_count":
                    return String.valueOf(countPlayerMobs(player, EntityType.COW));
                case "mobs_sheep_count":
                    return String.valueOf(countPlayerMobs(player, EntityType.SHEEP));
                case "mobs_epic_count":
                    return String.valueOf(countEpicMobs(player));
                case "mobs_legendary_count":
                    return String.valueOf(countLegendaryMobs(player));
                case "mobs_rare_count":
                    return String.valueOf(countRareMobs(player));
                case "total_income_per_second":
                    return String.format("%.1f", calculateTotalIncome(player));
                case "collectors_total_money":
                    return formatMoney(calculateTotalCollectorMoney(player));
                case "collectors_count":
                    return String.valueOf(countCollectors(player));
            }
            return null;
        }
    }
    private int countEpicMobs(Player player) {
        String playerBase = findPlayerBase(player);
        if (playerBase == null) return 0;
        List<String> mobPoints = baseMobSpawnPoints.get(playerBase);
        if (mobPoints == null) return 0;
        int count = 0;
        for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
            Entity mob = entry.getKey();
            String mobPoint = entry.getValue();
            if (mobPoints.contains(mobPoint)) {
                MobType type = MobType.fromEntity(mob);
                if (type != null && type.isEpic()) {
                    count++;
                }
            }
        }
        return count;
    }
    private int countLegendaryMobs(Player player) {
        String playerBase = findPlayerBase(player);
        if (playerBase == null) return 0;
        List<String> mobPoints = baseMobSpawnPoints.get(playerBase);
        if (mobPoints == null) return 0;
        int count = 0;
        for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
            Entity mob = entry.getKey();
            String mobPoint = entry.getValue();
            if (mobPoints.contains(mobPoint)) {
                MobType type = MobType.fromEntity(mob);
                if (type != null && type.isLegendary()) {
                    count++;
                }
            }
        }
        return count;
    }
    private int countRareMobs(Player player) {
        String playerBase = findPlayerBase(player);
        if (playerBase == null) return 0;
        List<String> mobPoints = baseMobSpawnPoints.get(playerBase);
        if (mobPoints == null) return 0;
        int count = 0;
        for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
            Entity mob = entry.getKey();
            String mobPoint = entry.getValue();
            if (mobPoints.contains(mobPoint)) {
                MobType type = MobType.fromEntity(mob);
                if (type != null && type.rarity == Rarity.RARE) {
                    count++;
                }
            }
        }
        return count;
    }
    private double calculateTotalIncome(Player player) {
        String playerBase = findPlayerBase(player);
        if (playerBase == null) return 0.0;
        List<String> mobPoints = baseMobSpawnPoints.get(playerBase);
        if (mobPoints == null) return 0.0;
        double totalIncome = 0.0;
        double multiplier = getPlayerEarnMultiplier(player);
        for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
            Entity mob = entry.getKey();
            String mobPoint = entry.getValue();
            if (mobPoints.contains(mobPoint)) {
                MobType type = MobType.fromEntity(mob);
                if (type != null) {
                    totalIncome += type.baseIncome * multiplier;
                }
            }
        }
        return totalIncome;
    }
    private double calculateTotalCollectorMoney(Player player) {
        String playerBase = findPlayerBase(player);
        if (playerBase == null) return 0.0;
        double totalMoney = 0.0;
        synchronized (moneyLock) {
            for (String collectorId : collectorMoney.keySet()) {
                if (collectorId.startsWith(playerBase + "_")) {
                    totalMoney += collectorMoney.getOrDefault(collectorId, 0.0);
                }
            }
        }
        return totalMoney;
    }
    private int countCollectors(Player player) {
        String playerBase = findPlayerBase(player);
        if (playerBase == null) return 0;
        int count = 0;
        for (String collectorId : collectorToEntityMap.keySet()) {
            if (collectorId.startsWith(playerBase + "_")) {
                count++;
            }
        }
        return count;
    }
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String playerName = p.getName();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            debugLog("========== [JOIN DEBUG] Вход " + playerName + " ==========");
            List<SavedMobData> savedMobs = savedPlayerMobs.get(playerName);
            boolean hasSavedMobs = savedMobs != null && !savedMobs.isEmpty();
            if (hasSavedMobs) {
                debugLog("[JOIN DEBUG] savedPlayerMobs содержит " + savedMobs.size() + " мобов");
                for (SavedMobData sm : savedMobs) {
                    if (sm.mobType.isLuckyBlock()) {
                        debugLog("[JOIN DEBUG] LB в памяти: timer=" + sm.luckyBlockRemainingMs + " ready=" + sm.luckyBlockReady);
                    }
                }
            }
            if (mobsConfig != null) {
                ConfigurationSection sec = mobsConfig.getConfigurationSection("mobs." + playerName);
                if (sec != null) {
                    for (String key : sec.getKeys(false)) {
                        String mobType = mobsConfig.getString("mobs." + playerName + "." + key + ".mobType");
                        if ("SPONGE".equals(mobType)) {
                            long timer = mobsConfig.getLong("mobs." + playerName + "." + key + ".luckyBlockTimer", -999);
                            boolean rdy = mobsConfig.getBoolean("mobs." + playerName + "." + key + ".luckyBlockReady", false);
                            debugLog("[JOIN DEBUG] mobs.yml LB: timer=" + timer + " ready=" + rdy);
                        }
                    }
                } else {
                    debugLog("[JOIN DEBUG] mobs.yml секция для " + playerName + " = null!");
                }
            }
            String playerBase = null;
            for (Map.Entry<String, String> entry : bases.entrySet()) {
                if (entry.getValue().equals(playerName)) {
                    playerBase = entry.getKey();
                    break;
                }
            }
            if (playerBase != null) {
                debugLog("[JOIN DEBUG] База игрока: " + playerBase);
                removeAllMobsFromBase(playerBase);
                Set<String> occupied = occupiedMobPoints.get(playerBase);
                if (occupied != null) occupied.clear();
                if (hasSavedMobs) {
                    debugLog("[JOIN DEBUG] Восстанавливаем " + savedMobs.size() + " мобов...");
                    restorePlayerMobs(playerName);
                }
                sendCooldownMessage(p, "§a✅ Добро пожаловать на базу!", lastCollectMessage);
            } else {
                String selectedBase = getRandomFreeBase();
                if (selectedBase == null) {
                    sendCooldownMessage(p, "§c❌ Нет свободных баз!", lastCollectMessage);
                    return;
                }
                debugLog("[JOIN DEBUG] Назначена НОВАЯ база: " + selectedBase);
                removeAllMobsFromBase(selectedBase);
                Set<String> occupied = occupiedMobPoints.get(selectedBase);
                if (occupied != null) occupied.clear();
                bases.put(selectedBase, playerName);
                teleportToBaseSpawn(p, selectedBase);
                updateHologram(selectedBase);
                if (hasSavedMobs) {
                    moveSavedMobsToNewBase(playerName, selectedBase);
                    sendCooldownMessage(p, "§a✅ Мобы перенесены на базу: §e" + selectedBase, lastCollectMessage);
                }
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (p.isOnline()) {
                        giveResetItem(p);
                    }
                }, 60L);
                sendCooldownMessage(p, "§a✔ Ваша база: §e" + selectedBase, lastCollectMessage);
            }
            if (!playersWithInitialBalance.contains(playerName)) {
                if (economy != null) {
                    economy.depositPlayer(p, INITIAL_BALANCE);
                    sendCooldownMessage(p, "§a✔ Начальный баланс: §6$" + (int)INITIAL_BALANCE, lastCollectMessage);
                }
                playersWithInitialBalance.add(playerName);
                saveInitialBalancePlayers();
            }
            int rebirthCount = getRebirthCount(p);
            if (rebirthCount > 0) {
                sendCooldownMessage(p, "§6Перерождений: §e" + rebirthCount + " §6| Множитель: §e" + getPlayerMultiplier(p) + "x", lastCollectMessage);
            }
            String st2JoinBase = findPlayerBase(p);
            if (st2JoinBase != null && stage2Configs.containsKey(st2JoinBase)) {
                Stage2Config st2sc = stage2Configs.get(st2JoinBase);
                if (getRebirthCount(p) >= st2sc.requiredRebirths) {
                    loadStage2(st2JoinBase);
                }
            }
            debugLog("========== [JOIN DEBUG] Завершено ==========");
        }, 20L);
    }
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        String playerName = p.getName();
        debugLog("========== [QUIT DEBUG] Выход " + playerName + " ==========");
        if (p.getScoreboardTags().contains("CARRYING_LUCKY_BLOCK")) {
            removeLuckyBlockCarryDisplay(p);
        }
        cancelStealing(p, false);
        String uuidPrefix = "lb_anim_" + p.getUniqueId().toString().substring(0, 8);
        for (Hologram holo : new ArrayList<>(hologramManager.getHolograms())) {
            if (holo.getData().getName().startsWith(uuidPrefix)) {
                hologramManager.removeHologram(holo);
            }
        }
        String playerBase = findPlayerBase(p);
        if (playerBase != null) {
            List<String> basePoints = baseMobSpawnPoints.get(playerBase);
            if (basePoints != null) {
                for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
                    Entity mob = entry.getKey();
                    String mobPoint = entry.getValue();
                    if (!basePoints.contains(mobPoint)) continue;
                    MobType type = MobType.fromEntity(mob);
                    if (type != null && type.isLuckyBlock()) {
                        debugLog("[QUIT DEBUG] Lucky Block найден:");
                        debugLog("[QUIT DEBUG]   UUID: " + mob.getUniqueId());
                        debugLog("[QUIT DEBUG]   isDead: " + mob.isDead());
                        debugLog("[QUIT DEBUG]   isValid: " + mob.isValid());
                        debugLog("[QUIT DEBUG]   mobPoint: " + mobPoint);
                        debugLog("[QUIT DEBUG]   luckyBlockOpenTime contains: " + luckyBlockOpenTime.containsKey(mob));
                        debugLog("[QUIT DEBUG]   luckyBlockReady contains: " + luckyBlockReady.containsKey(mob));
                        Long openTime = luckyBlockOpenTime.get(mob);
                        Boolean ready = luckyBlockReady.get(mob);
                        debugLog("[QUIT DEBUG]   openTime: " + openTime);
                        debugLog("[QUIT DEBUG]   ready: " + ready);
                        if (openTime != null && openTime > 0) {
                            long remaining = openTime - System.currentTimeMillis();
                            debugLog("[QUIT DEBUG]   remaining: " + remaining + "мс (" + (remaining / 1000) + " сек)");
                        }
                    }
                }
            }
            debugLog("[QUIT DEBUG] Вызываем savePlayerMobsInstantly...");
            savePlayerMobsInstantly(playerName);
            debugLog("[QUIT DEBUG] Проверяем savedPlayerMobs...");
            List<SavedMobData> saved = savedPlayerMobs.get(playerName);
            if (saved != null) {
                for (SavedMobData s : saved) {
                    if (s.mobType.isLuckyBlock()) {
                        debugLog("[QUIT DEBUG] Saved LB: timer=" + s.luckyBlockRemainingMs + " ready=" + s.luckyBlockReady);
                    }
                }
            }
            debugLog("[QUIT DEBUG] Проверяем mobs.yml...");
            if (mobsConfig != null) {
                ConfigurationSection sec = mobsConfig.getConfigurationSection("mobs." + playerName);
                if (sec != null) {
                    for (String key : sec.getKeys(false)) {
                        String mobType = mobsConfig.getString("mobs." + playerName + "." + key + ".mobType");
                        if ("SPONGE".equals(mobType)) {
                            long timer = mobsConfig.getLong("mobs." + playerName + "." + key + ".luckyBlockTimer", -999);
                            boolean rdy = mobsConfig.getBoolean("mobs." + playerName + "." + key + ".luckyBlockReady", false);
                            debugLog("[QUIT DEBUG] mobs.yml LB: timer=" + timer + " ready=" + rdy);
                        }
                    }
                }
            }
            removeAllMobsFromBase(playerBase);
            List<String> bPoints = baseMobSpawnPoints.get(playerBase);
            if (bPoints != null) {
                animatingPoints.removeAll(bPoints);
            }
            bases.put(playerBase, "none");
            updateHologram(playerBase);
            if (baseLocked.getOrDefault(playerBase, false)) {
                unlockBase(playerBase);
            }
            if (stage2Active.getOrDefault(playerBase, false)) {
                unloadStage2(playerBase);
            }
            collectorCooldowns.remove(playerBase);
        }
        resetCooldowns.remove(p.getUniqueId());
        pendingResets.remove(p.getUniqueId());
        lastLockMessage.remove(p);
        lastCollectMessage.remove(p);
        lastStealMessage.remove(p);
        sellMenuEntity.remove(p);
        stealingPlayers.remove(p);
        debugLog("========== [QUIT DEBUG] Завершено ==========");
    }
    private void savePlayerMobsInstantly(String playerName) {
        if (playerName == null || playerName.isEmpty()) return;
        if (mobsFile == null) {
            mobsFile = new File(getDataFolder(), "mobs.yml");
        }
        if (mobsConfig == null) {
            mobsConfig = YamlConfiguration.loadConfiguration(mobsFile);
        }
        String playerBase = null;
        for (Map.Entry<String, String> entry : bases.entrySet()) {
            if (playerName.equals(entry.getValue())) {
                playerBase = entry.getKey();
                break;
            }
        }
        if (playerBase == null) {
            mobsConfig.set("mobs." + playerName, null);
            savedPlayerMobs.remove(playerName);
            try {
                saveConfigAsync(mobsConfig, mobsFile);
            } catch (IOException e) {
                getLogger().severe("ОШИБКА записи mobs.yml (no base): " + e.getMessage());
            }
            return;
        }
        List<String> basePoints = baseMobSpawnPoints.get(playerBase);
        if (basePoints == null) basePoints = Collections.emptyList();
        mobsConfig.set("mobs." + playerName, null);
        List<SavedMobData> inMemory = new ArrayList<>();
        Map<Entity, String> snapshot = new HashMap<>(entityToPointMap);
        int savedCount = 0;
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<Entity, String> entry : snapshot.entrySet()) {
            Entity mob = entry.getKey();
            String mobPoint = entry.getValue();
            if (mob == null || mob.isDead() || !mob.isValid()) continue;
            if (mobPoint == null) continue;
            if (!basePoints.contains(mobPoint)) continue;
            MobType type = MobType.fromEntity(mob);
            if (type == null) continue;
            String collectorPoint = null;
            String collectorId = entityToCollectorMap.get(mobPoint);
            if (collectorId != null) {
                String[] parts = collectorId.split("_", 2);
                if (parts.length == 2 && parts[0].equals(playerBase)) {
                    collectorPoint = parts[1];
                }
            }
            String path = "mobs." + playerName + "." + savedCount;
            mobsConfig.set(path + ".base", playerBase);
            mobsConfig.set(path + ".mobPoint", mobPoint);
            mobsConfig.set(path + ".mobType", type.name());
            mobsConfig.set(path + ".collectorPoint", collectorPoint);
            Mutation mutation = baseMobMutations.getOrDefault(mob, Mutation.NONE);
            boolean snowy = baseMobSnowy.getOrDefault(mob, false);
            mobsConfig.set(path + ".mutation", mutation.name());
            mobsConfig.set(path + ".snowy", snowy);
            long lbRemainingMs = -1L;
            boolean lbReady = false;
            if (type.isLuckyBlock()) {
                boolean isReady = luckyBlockReady.getOrDefault(mob, false);
                long openTime = luckyBlockOpenTime.getOrDefault(mob, 0L);
                if (isReady) {
                    lbReady = true;
                    lbRemainingMs = 0L;
                    mobsConfig.set(path + ".luckyBlockReady", true);
                    mobsConfig.set(path + ".luckyBlockTimer", 0L);
                } else if (openTime > 0) {
                    long remaining = openTime - currentTime;
                    if (remaining > 0) {
                        lbReady = false;
                        lbRemainingMs = remaining;
                        mobsConfig.set(path + ".luckyBlockReady", false);
                        mobsConfig.set(path + ".luckyBlockTimer", remaining);
                    } else {
                        lbReady = true;
                        lbRemainingMs = 0L;
                        mobsConfig.set(path + ".luckyBlockReady", true);
                        mobsConfig.set(path + ".luckyBlockTimer", 0L);
                    }
                } else {
                    lbReady = false;
                    lbRemainingMs = LUCKY_BLOCK_TIMER;
                    mobsConfig.set(path + ".luckyBlockReady", false);
                    mobsConfig.set(path + ".luckyBlockTimer", LUCKY_BLOCK_TIMER);
                }
            }
            if (type.isLuckyBlock()) {
                inMemory.add(new SavedMobData(playerBase, mobPoint, collectorPoint, type, lbRemainingMs, lbReady, mutation.name(), snowy));
            } else {
                inMemory.add(new SavedMobData(playerBase, mobPoint, collectorPoint, type, -1L, false, mutation.name(), snowy));
            }
            savedCount++;
        }
        if (inMemory.isEmpty()) {
            savedPlayerMobs.remove(playerName);
        } else {
            savedPlayerMobs.put(playerName, inMemory);
        }
        try {
            saveConfigAsync(mobsConfig, mobsFile);
            debugLog("Сохранено " + savedCount + " мобов для игрока " + playerName);
        } catch (IOException e) {
            getLogger().severe("ОШИБКА записи mobs.yml: " + e.getMessage());
        }
    }
    private boolean hasClearInteractionPath(Player player, Entity target, double maxDistance) {
        if (player == null || target == null) return false;
        if (!player.getWorld().equals(target.getWorld())) return false;
        Location eye = player.getEyeLocation();
        double targetHeight = target.getHeight();
        Location targetLoc = target.getLocation().clone().add(0, Math.max(0.5, targetHeight * 0.7), 0);
        Vector direction = targetLoc.toVector().subtract(eye.toVector());
        double distance = direction.length();
        if (distance > maxDistance) return false;
        RayTraceResult hit = player.getWorld().rayTraceBlocks(
                eye,
                direction.normalize(),
                distance,
                FluidCollisionMode.NEVER,
                true
        );
        if (hit != null && hit.getHitBlock() != null) {
            Block block = hit.getHitBlock();
            Material type = block.getType();
            return false;
        }
        return true;
    }
    private void removeAllMobsFromBase(String base) {
        if (base == null) return;
        List<String> mobPoints = baseMobSpawnPoints.get(base);
        if (mobPoints == null) return;
        List<Entity> mobsToRemove = new ArrayList<>();
        for (Map.Entry<Entity, String> entry : new HashMap<>(entityToPointMap).entrySet()) {
            Entity mob = entry.getKey();
            String mobPoint = entry.getValue();
            if (mobPoints.contains(mobPoint)) {
                mobsToRemove.add(mob);
            }
        }
        for (Entity mob : mobsToRemove) {
            if (mob.getScoreboardTags().contains("LUCKY_BLOCK") ||
                luckyBlockTags.containsKey(mob)) {
                removeLuckyBlockFromBase(mob);
            } else {
                removeMobFromSystem(mob);
            }
        }
        Set<String> occupied = occupiedMobPoints.get(base);
        if (occupied != null) {
            occupied.clear();
        }
        getLogger().info("Удалено " + mobsToRemove.size() + " мобов с базы " + base);
    }
    private void registerCleanCommand() {
        PluginCommand command = getCommand("clearbasemobs");
        if (command != null) {
            command.setExecutor(new CommandExecutor() {
                @Override
                public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                    if (!sender.hasPermission("brainrotbases.admin")) {
                        sender.sendMessage("§cНет прав!");
                        return true;
                    }
                    sender.sendMessage("§eОчистка всех мобов...");
                    for (Entity entity : new ArrayList<>(entityToPointMap.keySet())) {
                        if (entity != null && !entity.isDead()) {
                            if (entity.getScoreboardTags().contains("LUCKY_BLOCK") ||
                                entity.getScoreboardTags().contains("aj.luckyblock.root") ||
                                luckyBlockTags.containsKey(entity)) {
                                removeLuckyBlockFromBase(entity);
                            } else {
                                entity.remove();
                            }
                        }
                    }
                    entityToPointMap.clear();
                    for (Hologram holo : new ArrayList<>(collectorHolograms.values())) {
                        hologramManager.removeHologram(holo);
                    }
                    collectorHolograms.clear();
                    for (Set<String> occupied : occupiedMobPoints.values()) {
                        occupied.clear();
                    }
                    cleanupOrphanedLuckyBlocks();
                    sender.sendMessage("§aВсе мобы и осиротевшие модели очищены!");
                    return true;
                }
            });
        }
    }
    @EventHandler
    public void onChickenLayEgg(org.bukkit.event.entity.EntityDropItemEvent event) {
        if (event.getEntity().getType() == EntityType.CHICKEN) {
            if (event.getItemDrop().getItemStack().getType() == Material.EGG) {
                event.setCancelled(true);
            }
        }
    }
    private void ensureWorldsLoaded() {
        FileConfiguration cfg = getConfig();
        if (!cfg.contains("bases")) return;
        Set<String> worldsToLoad = new HashSet<>();
        for (String baseName : cfg.getConfigurationSection("bases").getKeys(false)) {
            String worldName = cfg.getString("bases." + baseName + ".world", "world");
            worldsToLoad.add(worldName);
        }
        getLogger().info("========== ЗАГРУЗКА МИРОВ ==========");
        for (String worldName : worldsToLoad) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                getLogger().info("✓ Мир " + worldName + " уже загружен");
                try { world.setGameRule(org.bukkit.GameRule.SEND_COMMAND_FEEDBACK, false); } catch (Throwable __t) {} // __fix__ silence cmd spam
                continue;
            }
            File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
            if (!worldFolder.exists() || !worldFolder.isDirectory()) {
                getLogger().severe("✗ Папка мира " + worldName + " не найдена!");
                getLogger().severe("  Путь: " + worldFolder.getAbsolutePath());
                continue;
            }
            File levelDat = new File(worldFolder, "level.dat");
            if (!levelDat.exists()) {
                getLogger().severe("✗ Файл level.dat не найден в мире " + worldName);
                continue;
            }
            getLogger().info("Загружаю мир " + worldName + "...");
            try {
                WorldCreator creator = new WorldCreator(worldName);
                World loadedWorld = creator.createWorld();
                if (loadedWorld != null) {
                    getLogger().info("✓ Мир " + worldName + " успешно загружен!");
                    try { loadedWorld.setGameRule(org.bukkit.GameRule.SEND_COMMAND_FEEDBACK, false); } catch (Throwable __t) {} // __fix__ silence cmd spam
                } else {
                    getLogger().severe("✗ Не удалось загрузить мир " + worldName);
                }
            } catch (Exception e) {
                getLogger().severe("✗ Ошибка загрузки мира " + worldName + ": " + e.getMessage());
                org.bukkit.Bukkit.getLogger().warning("Brainrot: " + e.getMessage());
            }
        }
        debugLog("=====================================");
    }
    private void registerDebugCommand() {
        PluginCommand command = getCommand("mobdebug");
        if (command != null) {
            command.setExecutor(new CommandExecutor() {
                @Override
                public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                    if (!(sender instanceof Player)) return true;
                    Player player = (Player) sender;
                    String base = findPlayerBase(player);
                    if (base == null) {
                        player.sendMessage("§cУ вас нет базы!");
                        return true;
                    }
                    List<String> mobPoints = baseMobSpawnPoints.get(base);
                    Set<String> occupied = occupiedMobPoints.get(base);
                    player.sendMessage("§6=== ДИАГНОСТИКА БАЗЫ " + base + " ===");
                    player.sendMessage("§7Всего точек: §f" + (mobPoints != null ? mobPoints.size() : 0));
                    player.sendMessage("§7Занято точек: §f" + (occupied != null ? occupied.size() : 0));
                    if (mobPoints != null) {
                        for (String mobPoint : mobPoints) {
                            boolean isOccupied = occupied != null && occupied.contains(mobPoint);
                            boolean hasMob = false;
                            Entity mobAtPoint = null;
                            for (Map.Entry<Entity, String> entry : entityToPointMap.entrySet()) {
                                if (mobPoint.equals(entry.getValue())) {
                                    hasMob = true;
                                    mobAtPoint = entry.getKey();
                                    break;
                                }
                            }
                            String status = isOccupied ? (hasMob ? "§a✓ ЗАНЯТА (моб жив)" : "§c⚠ ЗАНЯТА (НО МОБА НЕТ!)") : "§7СВОБОДНА";
                            player.sendMessage("§8- §f" + mobPoint + ": " + status);
                            if (isOccupied && !hasMob) {
                                player.sendMessage("§c  ВОССТАНОВЛЕНИЕ...");
                                restoreMobFromSavedData(base, mobPoint);
                            }
                        }
                    }
                    return true;
                }
            });
        }
    }
    private void registerSaveCommand() {
        PluginCommand command = getCommand("savemobs");
        if (command != null) {
            command.setExecutor(new CommandExecutor() {
                @Override
                public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                    if (!sender.hasPermission("brainrotbases.admin")) {
                        sender.sendMessage("§cНет прав!");
                        return true;
                    }
                    saveAllMobsToFile();
                    sender.sendMessage("§aВсе мобы сохранены в mobs.yml!");
                    return true;
                }
            });
        }
    }
    private void teleportToBaseSpawn(Player p, String base) {
        FileConfiguration cfg = getConfig();
        World world = Bukkit.getWorld(cfg.getString("bases." + base + ".world"));
        if (world == null) return;
        Location spawn = new Location(world,
                cfg.getDouble("bases." + base + ".spawn.x"),
                cfg.getDouble("bases." + base + ".spawn.y"),
                cfg.getDouble("bases." + base + ".spawn.z")
        );
        spawn.setYaw((float) cfg.getDouble("bases." + base + ".spawn.yaw", 0f));
        spawn.setPitch((float) cfg.getDouble("bases." + base + ".spawn.pitch", 0f));
        p.teleport(spawn);
    }
    private String formatMoney(double amount) {
        DecimalFormat df = new DecimalFormat("#,##0");
        return df.format(amount);
    }
    // ==================== AUCTION BRIDGE (brainrot-auction integration) ====================
    private final Set<String> auctionListedPoints = ConcurrentHashMap.newKeySet();
    private final Map<String, Hologram> auctionHolograms = new ConcurrentHashMap<>();
    private AuctionHook auctionHook;

    private static String auctionKey(String base, String mobPoint) {
        return base + "\u0000" + mobPoint;
    }

    public void setAuctionHook(AuctionHook hook) { this.auctionHook = hook; }

    public boolean isAuctionListed(String base, String mobPoint) {
        return base != null && mobPoint != null && auctionListedPoints.contains(auctionKey(base, mobPoint));
    }

    private String auctionFindBaseForMobPoint(String mobPoint) {
        if (mobPoint == null) return null;
        for (Map.Entry<String, Set<String>> e : occupiedMobPoints.entrySet()) {
            if (e.getValue() != null && e.getValue().contains(mobPoint)) return e.getKey();
        }
        for (Map.Entry<String, List<String>> e : baseMobSpawnPoints.entrySet()) {
            if (e.getValue() != null && e.getValue().contains(mobPoint)) return e.getKey();
        }
        return null;
    }

    private Entity auctionFindMobEntity(String mobPoint) {
        if (mobPoint == null) return null;
        for (Map.Entry<Entity, String> e : entityToPointMap.entrySet()) {
            if (mobPoint.equals(e.getValue())) {
                Entity en = e.getKey();
                if (en != null && !en.isDead()) return en;
            }
        }
        return null;
    }

    private void refreshMobHologramForPoint(String base, String mobPoint) {
        if (hologramManager == null) return;
        Entity mob = auctionFindMobEntity(mobPoint);
        if (mob == null) return;
        MobType type = MobType.fromEntity(mob);
        if (type == null) return;
        createMobHologram(mob, type);
    }

    private boolean isMobAuctionListed(Entity mob) {
        if (mob == null) return false;
        String mobPoint = entityToPointMap.get(mob);
        if (mobPoint == null) return false;
        String base = auctionFindBaseForMobPoint(mobPoint);
        if (base == null) return false;
        return isAuctionListed(base, mobPoint);
    }

    public String getPlayerBase(String ownerName) {
        if (ownerName == null) return null;
        for (Map.Entry<String, String> e : bases.entrySet()) {
            String owner = e.getValue();
            if (owner != null && owner.equalsIgnoreCase(ownerName)) return e.getKey();
        }
        return null;
    }

    public boolean hasFreeSlot(String ownerName) {
        String base = getPlayerBase(ownerName);
        if (base == null) return false;
        return findFreeMobPoint(base) != null;
    }

    public AuctionMobInfo getListableMobInfo(Player player, Entity mob) {
        if (player == null || mob == null) return null;
        String mobPoint = entityToPointMap.get(mob);
        if (mobPoint == null) return null;
        String base = auctionFindBaseForMobPoint(mobPoint);
        if (base == null) return null;
        String owner = bases.get(base);
        if (owner == null || !owner.equalsIgnoreCase(player.getName())) return null;
        MobType type = MobType.fromEntity(mob);
        if (type == null || type.isLuckyBlock() || type.isRotWalker()) return null;
        if (isAuctionListed(base, mobPoint)) return null;
        Mutation mut = baseMobMutations.getOrDefault(mob, Mutation.NONE);
        boolean snowy = baseMobSnowy.getOrDefault(mob, false);
        double income = type.baseIncome * getMobMutationMultiplier(mob);
        AuctionMobInfo info = new AuctionMobInfo();
        info.base = base;
        info.mobPoint = mobPoint;
        info.mobType = type.name();
        info.displayName = type.name;
        info.mutationName = mut.name();
        info.snowy = snowy;
        info.baseIncomePerSec = income;
        info.iconMaterial = type.getCorrectIcon().name();
        info.rarityOrder = type.rarity == null ? 0 : type.rarity.ordinal();
        info.sellerName = owner;
        return info;
    }

    public boolean listMobForAuction(String base, String mobPoint) {
        if (base == null || mobPoint == null) return false;
        auctionListedPoints.add(auctionKey(base, mobPoint));
        refreshMobHologramForPoint(base, mobPoint);
        return true;
    }

    public void unlistMobFromAuction(String base, String mobPoint) {
        if (base == null || mobPoint == null) return;
        auctionListedPoints.remove(auctionKey(base, mobPoint));
        refreshMobHologramForPoint(base, mobPoint);
    }

    private void createAuctionHologram(String base, String mobPoint) {
        if (hologramManager == null) return;
        try {
            Entity mob = auctionFindMobEntity(mobPoint);
            if (mob == null) return;
            removeAuctionHologram(base, mobPoint);
            Location loc = mob.getLocation().clone().add(0, 2.4, 0);
            String name = "auction_" + base + "_" + mobPoint;
            TextHologramData data = new TextHologramData(name, loc);
            List<String> lines = new ArrayList<>();
            lines.add(color("§c§lВЫСТАВЛЕН НА АУКЦИОН"));
            data.setText(lines);
            data.setBackground(Hologram.TRANSPARENT);
            data.setSeeThrough(true);
            data.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            Hologram holo = hologramManager.create(data);
            hologramManager.addHologram(holo);
            auctionHolograms.put(auctionKey(base, mobPoint), holo);
        } catch (Exception e) {
            getLogger().warning("Auction hologram error: " + e.getMessage());
        }
    }

    private void removeAuctionHologram(String base, String mobPoint) {
        Hologram holo = auctionHolograms.remove(auctionKey(base, mobPoint));
        if (holo != null && hologramManager != null) {
            try { hologramManager.removeHologram(holo); } catch (Exception ignored) {}
        }
    }

    private void auctionNotifyRemoved(String base, String mobPoint) {
        if (!isAuctionListed(base, mobPoint)) return;
        unlistMobFromAuction(base, mobPoint);
        if (auctionHook != null) {
            try { auctionHook.onListedMobRemoved(base, mobPoint); } catch (Exception ignored) {}
        }
    }

    public boolean removeListedMobForSale(String base, String mobPoint) {
        if (base == null || mobPoint == null) return false;
        unlistMobFromAuction(base, mobPoint);
        removeMobAtPoint(mobPoint);
        String owner = bases.get(base);
        if (owner != null && !owner.equals("none")) {
            savePlayerMobsInstantly(owner);
        }
        return true;
    }

    public boolean giveAuctionMob(String ownerName, String mobTypeName, String mutationName, boolean snowy) {
        final String base = getPlayerBase(ownerName);
        if (base == null) return false;
        final String freePoint = findFreeMobPoint(base);
        if (freePoint == null) return false;
        MobType type = MobType.fromName(mobTypeName);
        if (type == null) return false;
        List<String> colPoints = baseCollectorPoints.get(base);
        String freeCol = findFreeCollectorForBase(base, colPoints);
        spawnMobAtPoint(base, freePoint, freeCol, type);
        final String fMut = mutationName;
        final boolean fSnowy = snowy;
        if ((mutationName != null && !mutationName.isEmpty() && !mutationName.equalsIgnoreCase("NONE")) || snowy) {
            Bukkit.getScheduler().runTaskLater(this, () -> applyMutationToPoint(base, freePoint, fMut, fSnowy), 5L);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> savePlayerMobsInstantly(ownerName), 10L);
        return true;
    }

    public double auctionGetBalance(OfflinePlayer p) {
        if (economy == null || p == null) return 0.0;
        try { return economy.getBalance(p); } catch (Exception e) { return 0.0; }
    }

    public boolean auctionWithdraw(OfflinePlayer p, double amount) {
        if (economy == null || p == null) return false;
        try {
            if (economy.getBalance(p) < amount) return false;
            return economy.withdrawPlayer(p, amount).transactionSuccess();
        } catch (Exception e) { return false; }
    }

    public boolean auctionDeposit(OfflinePlayer p, double amount) {
        if (economy == null || p == null) return false;
        try { return economy.depositPlayer(p, amount).transactionSuccess(); } catch (Exception e) { return false; }
    }

    public double auctionGetEarnMultiplier(Player player) {
        try { return getPlayerEarnMultiplier(player); } catch (Exception e) { return 1.0; }
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}