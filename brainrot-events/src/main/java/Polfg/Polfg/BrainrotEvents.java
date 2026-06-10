package Polfg.Polfg;

import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

public class BrainrotEvents extends JavaPlugin implements Listener {

    // ====== состояние события ======
    private String activeEvent = null;
    private boolean eventRunning = false;
    private boolean busy = false;
    private boolean pendingStop = false;
    private Location activePasteMinCorner = null;

    // ===== BossBar =====
    private BossBar bossBar;
    private BukkitRunnable timerTask;
    private int totalSeconds;
    private int secondsLeft;

    // ===== FAWE =====
    private File schematicsFolder;

    // ===== Музыка =====
    private static final SoundCategory SOUND_CATEGORY = SoundCategory.AMBIENT;
    private static final float SOUND_VOLUME = 0.4f;
    private static final float SOUND_PITCH = 1.0f;
    private static final String SOUND_DAY = "minecraft:day";
    private static final String SOUND_WINTER = "minecraft:winter";
    private String currentSoundKey = SOUND_DAY;
    private BukkitRunnable soundLoopTask;

    // ===== СНЕГОПАД =====
    private BukkitRunnable snowRainSpawnTask;
    private BukkitRunnable snowRainMoveTask;
    private final Map<UUID, SnowDrop> snowDrops = new HashMap<>();
    private NamespacedKey snowDisplayKey;

    private static class SnowDrop {
        final UUID entityId;
        final Vector vel;
        int ticksLeft;
        SnowDrop(UUID entityId, Vector vel, int ticksLeft) {
            this.entityId = entityId;
            this.vel = vel;
            this.ticksLeft = ticksLeft;
        }
    }

    // ===== NPC снеговики =====
    private NamespacedKey npcIdKey;
    private final List<Snowman> npcs = new ArrayList<>();
    private final Map<UUID, Long> npcCooldowns = new HashMap<>();

    private static final Map<Integer, List<String>> SNOWMAN_FACTS = new HashMap<>();
    static {
        SNOWMAN_FACTS.put(1, Arrays.asList(
            "§bСнеговик #1: §fУбери факел! Убери! Я сейчас лужей стану!",
            "§bСнеговик #1: §fЯ видел солнце... Оно смотрело на меня. Жутко.",
            "§bСнеговик #1: §fМоя мечта — жить в холодильнике.",
            "§bСнеговик #1: §fТы ведь не принес зажигалку? Скажи, что нет!"
        ));
        SNOWMAN_FACTS.put(2, Arrays.asList(
            "§bСнеговик #2: §fДобро пожаловать на Steal a Mob! Главное — веселись!",
            "§bСнеговик #2: §fЭх, были времена... Мы ходили за границу карты пешком!",
            "§bСнеговик #2: §fЯ однажды выпал из мира. Там темно и страшно.",
            "§bСнеговик #2: §fНе пытайся сбежать с ивента за карту, я слежу!"
        ));
        SNOWMAN_FACTS.put(3, Arrays.asList(
            "§bСнеговик #3: §fНашего разраба зовут Егор (Devesuch).",
            "§bСнеговик #3: §fЕгор такой ленивый, что даже этот текст писал лёжа.",
            "§bСнеговик #3: §fЕсли что-то сломалось — это не баг, это Егор забыл дописать код.",
            "§bСнеговик #3: §fТссс! Не шуми, а то разбудишь разработчика, он опять спит."
        ));
        SNOWMAN_FACTS.put(4, Arrays.asList(
            "§bСнеговик #4: §fНадеюсь когда нибудь я тоже мутирую)",
            "§bСнеговик #4: §fЭй! Залутал моба?",
            "§bСнеговик #4: §fЭтот ивент просто имба, честно.",
            "§bСнеговик #4: §fЯ сделал сальтуху и сломал позвоночник. Омагад, нихуя."
        ));
        SNOWMAN_FACTS.put(5, Arrays.asList(
            "§bСнеговик #5: §fИщи мобов со Снежной мутацией! Они самые ценные.",
            "§bСнеговик #5: §fСнег, мобы, деньги... Что еще нужно для счастья?",
            "§bСнеговик #5: §fНе стой столбом, лутай мутантов!",
            "§bСнеговик #5: §fЗдесь холодно, зато фарм горячий!"
        ));
        SNOWMAN_FACTS.put(6, Arrays.asList(
            "§bСнеговик #6: §fМы крадем мобов... или мобы крадут наше время?",
            "§bСнеговик #6: §fЕсли Егор ленивый, то кто написал мой ИИ? Пустота...",
            "§bСнеговик #6: §fМир за картой всё ещё существует в наших сердцах.",
            "§bСнеговик #6: §fСнег — это просто замерзшие слезы разработчика."
        ));
    }

    // ===== config =====
    private final Map<String, EventConfig> events = new HashMap<>();

    // ===== АВТО-ИВЕНТ =====
    private BukkitRunnable autoEventTask;
    private boolean autoEventEnabled = false;
    private int autoIntervalMinutes = 90;          // Каждые N минут — гарантированный запуск
    private int autoMinDurationMinutes = 20;       // Мин. длительность ивента
    private int autoMaxDurationMinutes = 40;       // Макс. длительность ивента
    private String autoEventId = "winter";         // Какой ивент запускать
    private int autoMinPlayers = 1;                // Мин. игроков онлайн
    private final Random autoRandom = new Random();

    private static class EventConfig {
        String id;
        String displayName;
        String worldName;
        Location pos1;
        Location pos2;
        String schematic;
        String originalSchematic;
        int durationMinutes;
        BarColor barColor;
        int snowballsPerSecond = 6;
        int snowballHeight = 20;
        List<Location> npcBlocks = new ArrayList<>();

        EventConfig(String id) {
            this.id = id;
            this.displayName = id;
            this.worldName = "world";
            this.schematic = "";
            this.originalSchematic = "";
            this.durationMinutes = 30;
            this.barColor = BarColor.BLUE;
        }
    }

    @FunctionalInterface
    private interface PasteCallback { void done(boolean ok); }

    // =========================================================
    // LIFECYCLE
    // =========================================================
    @Override
    public void onEnable() {
        npcIdKey = new NamespacedKey(this, "npc_id");
        snowDisplayKey = new NamespacedKey(this, "event_snow_display");

        if (Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            getLogger().severe("§c✖ FastAsyncWorldEdit не найден! BrainrotEvents отключён.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        schematicsFolder = new File("plugins/FastAsyncWorldEdit/schematics");
        if (!schematicsFolder.exists()) schematicsFolder.mkdirs();

        saveDefaultConfig();
        loadEvents();
        loadAutoEventConfig();

        if (getCommand("brainrotevent") != null) {
            BrainrotEventCommand cmd = new BrainrotEventCommand();
            getCommand("brainrotevent").setExecutor(cmd);
            getCommand("brainrotevent").setTabCompleter(cmd);
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        switchSoundTo(SOUND_DAY);

        if (autoEventEnabled) {
            startAutoEventTimer();
            getLogger().info("§a✓ Авто-ивент включён: каждые " + autoIntervalMinutes + 
                " мин, длительность " + autoMinDurationMinutes + "-" + autoMaxDurationMinutes + " мин");
        }
    }

    @Override
    public void onDisable() {
        // __leakfix__
        try { org.bukkit.Bukkit.getScheduler().cancelTasks(this); } catch (Throwable __t) {}
        try { org.bukkit.event.HandlerList.unregisterAll(this); } catch (Throwable __t) {}
        stopAutoEventTimer();

        if (eventRunning && activeEvent != null) {
            getLogger().info("§e⚠ Сервер выключается, принудительно завершаем ивент...");
            EventConfig e = events.get(activeEvent);
            if (e != null) stopEventInternalSync(e);
        }

        stopTimer();
        removeBossBar();
        stopSnowRain();
        despawnNpcs();
        stopSoundLoop();
        stopAllPlayersSound();
    }

    private void stopEventInternalSync(EventConfig e) {
        stopTimer();
        removeBossBar();
        if (activeEvent != null && activeEvent.equalsIgnoreCase("winter")) {
            stopSnowRain();
            despawnNpcs();
        }
        setDay(e);
        activeEvent = null;
        eventRunning = false;
        activePasteMinCorner = null;
        secondsLeft = 0;
        totalSeconds = 0;
    }

    // =========================================================
    // АВТО-ИВЕНТ
    // =========================================================
    private void loadAutoEventConfig() {
        FileConfiguration cfg = getConfig();

        if (!cfg.contains("auto-event.enabled")) {
            cfg.set("auto-event.enabled", false);
            cfg.set("auto-event.event-id", "winter");
            cfg.set("auto-event.interval-minutes", 90);
            cfg.set("auto-event.min-duration-minutes", 20);
            cfg.set("auto-event.max-duration-minutes", 40);
            cfg.set("auto-event.min-players", 1);
            saveConfig();
        }

        autoEventEnabled = cfg.getBoolean("auto-event.enabled", false);
        autoEventId = cfg.getString("auto-event.event-id", "winter").toLowerCase(Locale.ROOT);
        autoIntervalMinutes = cfg.getInt("auto-event.interval-minutes", 90);
        autoMinDurationMinutes = cfg.getInt("auto-event.min-duration-minutes", 20);
        autoMaxDurationMinutes = cfg.getInt("auto-event.max-duration-minutes", 40);
        autoMinPlayers = cfg.getInt("auto-event.min-players", 1);

        // Валидация
        if (autoIntervalMinutes < 1) autoIntervalMinutes = 1;
        if (autoMinDurationMinutes < 1) autoMinDurationMinutes = 1;
        if (autoMaxDurationMinutes < autoMinDurationMinutes) autoMaxDurationMinutes = autoMinDurationMinutes;
        if (autoMinPlayers < 0) autoMinPlayers = 0;
    }

    private void startAutoEventTimer() {
        stopAutoEventTimer();

        long intervalTicks = autoIntervalMinutes * 60L * 20L;

        autoEventTask = new BukkitRunnable() {
            @Override
            public void run() {
                autoStartEvent();
            }
        };
        autoEventTask.runTaskTimer(this, intervalTicks, intervalTicks);

        getLogger().info("[AUTO-EVENT] Таймер запущен: ивент каждые " + autoIntervalMinutes + " мин");
    }

    private void stopAutoEventTimer() {
        if (autoEventTask != null) {
            try { autoEventTask.cancel(); } catch (Exception ignored) {}
            autoEventTask = null;
        }
    }

    private void autoStartEvent() {
        // Уже идёт ивент — пропускаем, запустится через следующий интервал
        if (eventRunning || busy) {
            getLogger().info("[AUTO-EVENT] Пропуск: ивент уже идёт, следующая попытка через " + autoIntervalMinutes + " мин");
            return;
        }

        // Мало игроков — пропускаем
        int online = Bukkit.getOnlinePlayers().size();
        if (online < autoMinPlayers) {
            getLogger().info("[AUTO-EVENT] Пропуск: игроков " + online + "/" + autoMinPlayers);
            return;
        }

        // Проверяем что ивент существует
        if (!events.containsKey(autoEventId)) {
            getLogger().warning("[AUTO-EVENT] Ивент '" + autoEventId + "' не найден в конфиге!");
            return;
        }

        // Рандомная длительность
        int duration;
        if (autoMaxDurationMinutes > autoMinDurationMinutes) {
            duration = autoMinDurationMinutes + autoRandom.nextInt(autoMaxDurationMinutes - autoMinDurationMinutes + 1);
        } else {
            duration = autoMinDurationMinutes;
        }

        getLogger().info("[AUTO-EVENT] §a✓ Запуск ивента '" + autoEventId + "' на " + duration + " минут!");

        // Анонс
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage("");
            p.sendMessage("§b§l❄ ═══════════════════════════════ ❄");
            p.sendMessage("");
            p.sendMessage("§e§l         ⚡ ВНИМАНИЕ! ⚡");
            p.sendMessage("");
            p.sendMessage("§f    Начинается §b§lЗимний Ивент§f!");
            p.sendMessage("§f    Длительность: §e" + duration + " минут");
            p.sendMessage("");
            p.sendMessage("§7  Ищите мобов со §b❄ Снежной мутацией§7!");
            p.sendMessage("");
            p.sendMessage("§b§l❄ ═══════════════════════════════ ❄");
            p.sendMessage("");
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        }

        // Задержка 5 секунд перед стартом
        final int finalDuration = duration;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            startEvent(autoEventId, Bukkit.getConsoleSender(), finalDuration);
        }, 100L);
    }

    // =========================================================
    // CONFIG
    // =========================================================
    private void loadEvents() {
        events.clear();
        FileConfiguration cfg = getConfig();

        if (!cfg.contains("events")) {
            cfg.set("events.winter.display-name", "§b❄ Зимний Ивент ❄");
            cfg.set("events.winter.world", "world");
            cfg.set("events.winter.schematic", "winter");
            cfg.set("events.winter.original-schematic", "original");
            cfg.set("events.winter.duration-minutes", 30);
            cfg.set("events.winter.bar-color", "BLUE");
            cfg.set("events.winter.pos1.x", -60);
            cfg.set("events.winter.pos1.y", 58);
            cfg.set("events.winter.pos1.z", 95);
            cfg.set("events.winter.pos2.x", 20);
            cfg.set("events.winter.pos2.y", 46);
            cfg.set("events.winter.pos2.z", 35);
            cfg.set("events.winter.snowballs-per-second", 6);
            cfg.set("events.winter.snowball-height", 20);

            List<Map<String, Object>> npcsList = new ArrayList<>();
            npcsList.add(mapNpc(-55, 46, 40, 180f));
            npcsList.add(mapNpc(-50, 46, 45, 180f));
            npcsList.add(mapNpc(-45, 46, 50, 180f));
            npcsList.add(mapNpc(-40, 46, 55, 180f));
            npcsList.add(mapNpc(-35, 46, 60, 180f));
            npcsList.add(mapNpc(-30, 46, 65, 180f));
            cfg.set("events.winter.npcs", npcsList);
            saveConfig();
        }

        ConfigurationSection sec = cfg.getConfigurationSection("events");
        if (sec == null) return;

        for (String rawId : sec.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            String p = "events." + rawId + ".";

            EventConfig e = new EventConfig(id);
            e.displayName = cfg.getString(p + "display-name", rawId);
            e.worldName = cfg.getString(p + "world", "world");
            e.schematic = cfg.getString(p + "schematic", "");
            e.originalSchematic = cfg.getString(p + "original-schematic", "");
            e.durationMinutes = cfg.getInt(p + "duration-minutes", 30);

            String col = cfg.getString(p + "bar-color", "BLUE");
            try { e.barColor = BarColor.valueOf(col.toUpperCase(Locale.ROOT)); }
            catch (Exception ex) { e.barColor = BarColor.BLUE; }

            World w = Bukkit.getWorld(e.worldName);
            if (w != null && cfg.contains(p + "pos1.x") && cfg.contains(p + "pos2.x")) {
                e.pos1 = new Location(w, cfg.getDouble(p + "pos1.x"), cfg.getDouble(p + "pos1.y"), cfg.getDouble(p + "pos1.z"));
                e.pos2 = new Location(w, cfg.getDouble(p + "pos2.x"), cfg.getDouble(p + "pos2.y"), cfg.getDouble(p + "pos2.z"));
            }

            e.snowballsPerSecond = cfg.getInt(p + "snowballs-per-second", 6);
            e.snowballHeight = cfg.getInt(p + "snowball-height", 20);

            e.npcBlocks.clear();
            List<Map<?, ?>> npcList = cfg.getMapList(p + "npcs");
            if (w != null && npcList != null) {
                for (Map<?, ?> m : npcList) {
                    Object ox = m.get("x"), oy = m.get("y"), oz = m.get("z"), oyaw = m.get("yaw");
                    if (ox == null || oy == null || oz == null) continue;
                    double x = Double.parseDouble(String.valueOf(ox));
                    double y = Double.parseDouble(String.valueOf(oy));
                    double z = Double.parseDouble(String.valueOf(oz));
                    float yaw2 = (oyaw != null) ? Float.parseFloat(String.valueOf(oyaw)) : 0.0f;
                    Location loc = new Location(w, x, y, z);
                    loc.setYaw(yaw2);
                    e.npcBlocks.add(loc);
                }
            }
            events.put(id, e);
        }
    }

    private Map<String, Object> mapNpc(int x, int y, int z, float yaw) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", x); m.put("y", y); m.put("z", z); m.put("yaw", yaw);
        return m;
    }

    // =========================================================
    // SOUND
    // =========================================================
    private void stopAllPlayersSound() {
        for (Player p : Bukkit.getOnlinePlayers()) stopAllEventSounds(p);
    }
    private void stopAllEventSounds(Player p) {
        stopSound(p, SOUND_DAY); stopSound(p, SOUND_WINTER);
    }
    private void stopSound(Player p, String key) {
        try { p.stopSound(key, SOUND_CATEGORY); } catch (Throwable ignored) {
            try { p.stopSound(key); } catch (Throwable ignored2) {}
        }
    }
    private void playSound(Player p, String key) {
        try { p.playSound(p.getLocation(), key, SOUND_CATEGORY, SOUND_VOLUME, SOUND_PITCH); }
        catch (Throwable ignored) { p.playSound(p.getLocation(), key, SOUND_VOLUME, SOUND_PITCH); }
    }
    private void switchSoundTo(String key) {
        currentSoundKey = key;
        stopAllPlayersSound();
        for (Player p : Bukkit.getOnlinePlayers()) playSound(p, currentSoundKey);
        startSoundLoop();
    }
    private void startSoundLoop() {
        stopSoundLoop();
        long delayTicks = currentSoundKey.equals(SOUND_WINTER) ? 204 * 20L : 190 * 20L;
        soundLoopTask = new BukkitRunnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) playSound(p, currentSoundKey);
            }
        };
        soundLoopTask.runTaskTimer(this, delayTicks, delayTicks);
    }
    private void stopSoundLoop() {
        if (soundLoopTask != null) {
            try { soundLoopTask.cancel(); } catch (Exception ignored) {}
            soundLoopTask = null;
        }
    }

    // =========================================================
    // TIME
    // =========================================================
    private void setNight(EventConfig e) {
        World w = Bukkit.getWorld(e.worldName);
        if (w != null) w.setTime(18000L);
    }
    private void setDay(EventConfig e) {
        World w = Bukkit.getWorld(e.worldName);
        if (w != null) w.setTime(6000L);
    }

    // =========================================================
    // REGION HELPERS
    // =========================================================
    private Location minCorner(EventConfig e) {
        if (e.pos1 == null || e.pos2 == null) return null;
        return new Location(e.pos1.getWorld(),
                Math.min(e.pos1.getX(), e.pos2.getX()),
                Math.min(e.pos1.getY(), e.pos2.getY()),
                Math.min(e.pos1.getZ(), e.pos2.getZ()));
    }
    private Location maxCorner(EventConfig e) {
        if (e.pos1 == null || e.pos2 == null) return null;
        return new Location(e.pos1.getWorld(),
                Math.max(e.pos1.getX(), e.pos2.getX()),
                Math.max(e.pos1.getY(), e.pos2.getY()),
                Math.max(e.pos1.getZ(), e.pos2.getZ()));
    }
    private Location getPasteLocationFromSelectionOrConfig(EventConfig e, CommandSender sender) {
        if (sender instanceof Player p) {
            try {
                if (p.getWorld().getName().equalsIgnoreCase(e.worldName)) {
                    com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(p);
                    LocalSession session = WorldEdit.getInstance().getSessionManager().get(wePlayer);
                    com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(p.getWorld());
                    Region sel = session.getSelection(weWorld);
                    BlockVector3 min = sel.getMinimumPoint();
                    return new Location(p.getWorld(), min.getX(), min.getY(), min.getZ());
                }
            } catch (IncompleteRegionException ignored) {
            } catch (Exception ignored) {}
        }
        return minCorner(e);
    }

    // =========================================================
    // PASTE
    // =========================================================
    private void pasteSchematic(String schematicName, Location targetMinCorner, PasteCallback cb) {
        if (schematicName == null || schematicName.isEmpty() || targetMinCorner == null) {
            cb.done(false); return;
        }
        File file = findSchematicFile(schematicName);
        if (file == null || !file.exists()) {
            getLogger().severe("§c✖ Схема не найдена: " + schematicName);
            cb.done(false); return;
        }
        getLogger().info("§eВставка схемы: " + file.getName());

        final File fileFinal = file;
        final Location locFinal = targetMinCorner.clone();
        final String schemFinal = schematicName;

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            boolean ok = true;
            try {
                ClipboardFormat format = ClipboardFormats.findByFile(fileFinal);
                if (format == null) { ok = false; }
                else {
                    Clipboard clipboard;
                    try (ClipboardReader reader = format.getReader(new FileInputStream(fileFinal))) {
                        clipboard = reader.read();
                    }
                    com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(locFinal.getWorld());
                    try (EditSession editSession = WorldEdit.getInstance()
                            .newEditSessionBuilder().world(weWorld).maxBlocks(-1).build()) {
                        Region region = clipboard.getRegion();
                        BlockVector3 clipMin = region.getMinimumPoint();
                        BlockVector3 clipOrigin = clipboard.getOrigin();
                        BlockVector3 pastePos = BlockVector3.at(
                                locFinal.getBlockX(), locFinal.getBlockY(), locFinal.getBlockZ()
                        ).add(clipOrigin.subtract(clipMin));
                        Operation operation = new ClipboardHolder(clipboard)
                                .createPaste(editSession).to(pastePos).ignoreAirBlocks(false).build();
                        Operations.complete(operation);
                        getLogger().info("§a✓ Схема " + schemFinal + " вставлена!");
                    }
                }
            } catch (Exception ex) {
                ok = false;
                getLogger().severe("§c✖ Ошибка вставки: " + ex.getMessage());
            }
            final boolean finalOk = ok;
            Bukkit.getScheduler().runTask(this, () -> cb.done(finalOk));
        });
    }

    private File findSchematicFile(String name) {
        File schem = new File(schematicsFolder, name + ".schem");
        if (schem.exists()) return schem;
        File schematic = new File(schematicsFolder, name + ".schematic");
        if (schematic.exists()) return schematic;
        File direct = new File(schematicsFolder, name);
        if (direct.exists()) return direct;
        return null;
    }

    // =========================================================
    // SNOW RAIN
    // =========================================================
    private void startSnowRain(EventConfig e) {
        stopSnowRain();
        Location min = minCorner(e); Location max = maxCorner(e);
        if (min == null || max == null) return;
        final World w = min.getWorld(); if (w == null) return;

        final int minX = (int) Math.floor(min.getX()), maxX = (int) Math.floor(max.getX());
        final int minZ = (int) Math.floor(min.getZ()), maxZ = (int) Math.floor(max.getZ());
        final int minY = (int) Math.floor(min.getY()), maxY = (int) Math.floor(max.getY());
        final int perSecond = Math.max(1, e.snowballsPerSecond);
        final int spawnY = Math.min(maxY + Math.max(5, e.snowballHeight), w.getMaxHeight() - 2);

        snowRainSpawnTask = new BukkitRunnable() {
            final Random rnd = new Random();
            @Override public void run() {
                if (!eventRunning || activeEvent == null || !activeEvent.equalsIgnoreCase("winter")) return;
                if (snowDrops.size() > 400) return;
                for (int i = 0; i < perSecond; i++) {
                    int x = rnd.nextInt(Math.max(1, (maxX - minX + 1))) + minX;
                    int z = rnd.nextInt(Math.max(1, (maxZ - minZ + 1))) + minZ;
                    Location at = new Location(w, x + 0.5, spawnY, z + 0.5);
                    ItemDisplay d = w.spawn(at, ItemDisplay.class, disp -> {
                        disp.setItemStack(new ItemStack(Material.SNOWBALL));
                        disp.setBillboard(Display.Billboard.FIXED);
                        disp.setInterpolationDelay(0); disp.setInterpolationDuration(1);
                        disp.addScoreboardTag("BREVENT_SNOWDISPLAY");
                        disp.getPersistentDataContainer().set(snowDisplayKey, PersistentDataType.BYTE, (byte) 1);
                    });
                    Vector v = new Vector((rnd.nextDouble() - 0.5) * 0.03,
                            -0.35 - rnd.nextDouble() * 0.20, (rnd.nextDouble() - 0.5) * 0.03);
                    snowDrops.put(d.getUniqueId(), new SnowDrop(d.getUniqueId(), v, 6 * 20));
                }
            }
        };
        snowRainSpawnTask.runTaskTimer(this, 20L, 20L);

        snowRainMoveTask = new BukkitRunnable() {
            @Override public void run() {
                if (!eventRunning || activeEvent == null || !activeEvent.equalsIgnoreCase("winter")) return;
                Iterator<SnowDrop> it = snowDrops.values().iterator();
                while (it.hasNext()) {
                    SnowDrop sd = it.next();
                    Entity ent = Bukkit.getEntity(sd.entityId);
                    if (!(ent instanceof ItemDisplay disp) || !disp.isValid()) { it.remove(); continue; }
                    sd.ticksLeft--;
                    Location loc = disp.getLocation().add(sd.vel);
                    disp.teleport(loc);
                    if (sd.ticksLeft <= 0 || loc.getY() < (minY - 5)) { disp.remove(); it.remove(); continue; }
                    for (Entity near : w.getNearbyEntities(loc, 0.6, 0.9, 0.6)) {
                        if (!(near instanceof LivingEntity living)) continue;
                        if (living instanceof Player) continue;
                        if (living.getScoreboardTags().contains("BREVENT_NPC")) continue;
                        living.addScoreboardTag("MUTATION_SNOWY");
                        w.playSound(living.getLocation(), Sound.BLOCK_SNOW_PLACE, 0.7f, 1.2f);
                        disp.remove(); it.remove(); break;
                    }
                }
            }
        };
        snowRainMoveTask.runTaskTimer(this, 1L, 1L);
    }

    private void stopSnowRain() {
        if (snowRainSpawnTask != null) { try { snowRainSpawnTask.cancel(); } catch (Exception ignored) {} snowRainSpawnTask = null; }
        if (snowRainMoveTask != null) { try { snowRainMoveTask.cancel(); } catch (Exception ignored) {} snowRainMoveTask = null; }
        for (SnowDrop sd : snowDrops.values()) {
            Entity ent = Bukkit.getEntity(sd.entityId);
            if (ent != null && ent.isValid()) ent.remove();
        }
        snowDrops.clear();
    }

    // =========================================================
    // NPC
    // =========================================================
    private void spawnNpcs(EventConfig e) {
        despawnNpcs();
        if (e.npcBlocks == null || e.npcBlocks.isEmpty()) return;
        for (int i = 0; i < e.npcBlocks.size(); i++) {
            Location b = e.npcBlocks.get(i);
            if (b == null || b.getWorld() == null) continue;
            int npcId = i + 1;
            Location spawn = b.clone().add(0.5, 0, 0.5);
            spawn.setYaw(b.getYaw());
            Snowman s = b.getWorld().spawn(spawn, Snowman.class, sn -> {
                sn.setAI(false); sn.setInvulnerable(true); sn.setSilent(true);
                sn.setGravity(false); sn.setCollidable(false); sn.setPersistent(true);
                sn.setRemoveWhenFarAway(false); sn.setRotation(b.getYaw(), 0);
                sn.setCustomName("§bСнеговик #" + npcId); sn.setCustomNameVisible(true);
                sn.addScoreboardTag("BREVENT_NPC");
                sn.getPersistentDataContainer().set(npcIdKey, PersistentDataType.INTEGER, npcId);
            });
            s.teleport(spawn);
            npcs.add(s);
        }
    }

    private void despawnNpcs() {
        for (Snowman s : npcs) { if (s != null && s.isValid()) s.remove(); }
        npcs.clear(); npcCooldowns.clear();
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractAtEntityEvent e) {
        if (!eventRunning || activeEvent == null || !activeEvent.equalsIgnoreCase("winter")) return;
        if (!(e.getRightClicked() instanceof Snowman sn)) return;
        if (!sn.getScoreboardTags().contains("BREVENT_NPC")) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        if (now - npcCooldowns.getOrDefault(p.getUniqueId(), 0L) < 2000) return;
        npcCooldowns.put(p.getUniqueId(), now);
        Integer id = sn.getPersistentDataContainer().get(npcIdKey, PersistentDataType.INTEGER);
        if (id == null) id = 1;
        List<String> facts = SNOWMAN_FACTS.getOrDefault(id, SNOWMAN_FACTS.get(1));
        String fact = facts.get(new Random().nextInt(facts.size()));
        p.sendMessage(""); p.sendMessage(fact); p.sendMessage("");
        p.playSound(p.getLocation(), Sound.ENTITY_SNOW_GOLEM_AMBIENT, 1.0f, 1.0f);
        sn.getWorld().spawnParticle(Particle.NOTE, sn.getLocation().add(0, 2, 0), 3, 0.2, 0.2, 0.2, 0.5);
    }

    @EventHandler
    public void onNpcDamage(EntityDamageEvent e) {
        if (e.getEntity().getScoreboardTags().contains("BREVENT_NPC")) e.setCancelled(true);
    }

    // =========================================================
    // EVENT start/stop
    // =========================================================
    public boolean startEvent(String eventId, CommandSender sender, int durationOverrideMinutes) {
        final String id = eventId.toLowerCase(Locale.ROOT);
        final EventConfig e = events.get(id);
        if (e == null) { sender.sendMessage("§c✖ Событие '" + eventId + "' не найдено!"); return false; }
        if (eventRunning || busy) { sender.sendMessage("§c✖ Уже запущено: " + activeEvent); return false; }
        if (e.originalSchematic == null || e.originalSchematic.isEmpty()) {
            sender.sendMessage("§c✖ Нет original-schematic для " + id); return false;
        }

        busy = true;
        Location pasteMin = getPasteLocationFromSelectionOrConfig(e, sender);
        if (pasteMin == null) { sender.sendMessage("§e⚠ Позиции не заданы"); busy = false; return true; }

        activePasteMinCorner = pasteMin.clone();
        int minutes = durationOverrideMinutes > 0 ? durationOverrideMinutes : e.durationMinutes;
        totalSeconds = minutes * 60;
        secondsLeft = totalSeconds;

        pasteSchematic(e.schematic, pasteMin, ok -> {
            activeEvent = id; eventRunning = true;
            setNight(e); createBossBar(e); startTimer(e);
            switchSoundTo(SOUND_WINTER);
            if (id.equalsIgnoreCase("winter")) { startSnowRain(e); spawnNpcs(e); }
            sender.sendMessage("§a✓ Событие '" + e.displayName + "§a' запущено на " + minutes + " мин!");
            getLogger().info("Событие " + id + " запущено на " + minutes + " мин");
            busy = false;
            if (pendingStop) { pendingStop = false; stopEventInternal(e); }
        });
        return true;
    }

    public boolean stopEvent(String eventId, CommandSender sender) {
        String id = eventId.toLowerCase(Locale.ROOT);
        EventConfig e = events.get(id);
        if (e == null) { sender.sendMessage("§c✖ Событие '" + eventId + "' не найдено!"); return false; }
        if (!eventRunning || activeEvent == null || !id.equalsIgnoreCase(activeEvent)) {
            sender.sendMessage("§c✖ Событие '" + eventId + "' не запущено!"); return false;
        }
        stopEventInternal(e);
        sender.sendMessage("§a✓ Событие остановлено!");
        return true;
    }

    private void stopEventInternal(EventConfig e) {
        if (busy) { pendingStop = true; return; }
        busy = true;
        stopTimer(); removeBossBar();
        if (activeEvent != null && activeEvent.equalsIgnoreCase("winter")) { stopSnowRain(); despawnNpcs(); }
        setDay(e); switchSoundTo(SOUND_DAY);

        // Анонс окончания
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage("");
            p.sendMessage("§7§l═══════════════════════════════");
            p.sendMessage("§e         Ивент завершён!");
            p.sendMessage("§7    Спасибо за участие!");
            p.sendMessage("§7§l═══════════════════════════════");
            p.sendMessage("");
            p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.0f);
        }

        Location restoreAt = (activePasteMinCorner != null) ? activePasteMinCorner.clone() : minCorner(e);
        pasteSchematic(e.originalSchematic, restoreAt, ok -> {
            activeEvent = null; eventRunning = false;
            activePasteMinCorner = null; secondsLeft = 0; totalSeconds = 0;
            getLogger().info("Событие " + e.id + " завершено");
            busy = false;
        });
    }

    // =========================================================
    // BossBar + timer
    // =========================================================
    private void createBossBar(EventConfig e) {
        removeBossBar();
        bossBar = Bukkit.createBossBar(e.displayName + " §7| §e" + fmt(secondsLeft), e.barColor, BarStyle.SOLID);
        bossBar.setVisible(true); bossBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) bossBar.addPlayer(p);
    }
    private void removeBossBar() {
        if (bossBar != null) { bossBar.removeAll(); bossBar = null; }
    }
    private void startTimer(EventConfig e) {
        stopTimer();
        timerTask = new BukkitRunnable() {
            @Override public void run() {
                if (!eventRunning) { cancel(); return; }
                secondsLeft--;
                if (secondsLeft <= 0) {
                    cancel();
                    Bukkit.getScheduler().runTask(BrainrotEvents.this, () -> stopEventInternal(e));
                    return;
                }
                if (bossBar != null) {
                    bossBar.setTitle(e.displayName + " §7| §e" + fmt(secondsLeft));
                    bossBar.setProgress(Math.max(0.0, Math.min(1.0, secondsLeft / (double) totalSeconds)));
                    if (secondsLeft <= 60) bossBar.setColor(BarColor.RED);
                    else if (secondsLeft <= 300) bossBar.setColor(BarColor.YELLOW);
                    else bossBar.setColor(e.barColor);
                }
            }
        };
        timerTask.runTaskTimer(this, 20L, 20L);
    }
    private void stopTimer() {
        if (timerTask != null) { try { timerTask.cancel(); } catch (Exception ignored) {} timerTask = null; }
    }
    private String fmt(int sec) { return String.format("%02d:%02d", sec / 60, sec % 60); }

    // =========================================================
    // join/quit
    // =========================================================
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (eventRunning && bossBar != null) bossBar.addPlayer(p);
            stopAllEventSounds(p);
            playSound(p, currentSoundKey);
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (bossBar != null) bossBar.removePlayer(e.getPlayer());
        npcCooldowns.remove(e.getPlayer().getUniqueId());
    }

    // =========================================================
    // COMMANDS
    // =========================================================
    private class BrainrotEventCommand implements CommandExecutor, TabCompleter {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("brainrotevents.admin")) {
                sender.sendMessage("§cУ вас нет прав!"); return true;
            }
            if (args.length < 1) { showHelp(sender); return true; }

            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "start" -> {
                    if (args.length < 2) {
                        sender.sendMessage("§cИспользование: /brainrotevent start <событие> [минуты]");
                        sender.sendMessage("§eДоступные: §f" + String.join(", ", events.keySet()));
                        return true;
                    }
                    int minutes = -1;
                    if (args.length >= 3) {
                        try { minutes = Integer.parseInt(args[2]); }
                        catch (NumberFormatException ex) { sender.sendMessage("§cНеверное число!"); return true; }
                    }
                    startEvent(args[1], sender, minutes);
                }
                case "stop" -> {
                    if (!eventRunning || activeEvent == null) {
                        sender.sendMessage("§cНет активного события!"); return true;
                    }
                    stopEvent(activeEvent, sender);
                }
                case "list" -> {
                    sender.sendMessage("§6=== Доступные события ===");
                    for (EventConfig ev : events.values()) {
                        String status = (eventRunning && ev.id.equalsIgnoreCase(activeEvent))
                                ? "§a[АКТИВНО] §7" + fmt(secondsLeft) : "§7[неактивно]";
                        sender.sendMessage("§e• " + ev.id + " §f- " + ev.displayName + " " + status);
                    }
                }
                case "auto" -> {
                    if (args.length < 2) { showAutoStatus(sender); return true; }
                    String autoSub = args[1].toLowerCase(Locale.ROOT);
                    switch (autoSub) {
                        case "on" -> {
                            autoEventEnabled = true;
                            getConfig().set("auto-event.enabled", true); saveConfig();
                            startAutoEventTimer();
                            sender.sendMessage("§a✓ Авто-ивент включён!");
                            showAutoStatus(sender);
                        }
                        case "off" -> {
                            autoEventEnabled = false;
                            getConfig().set("auto-event.enabled", false); saveConfig();
                            stopAutoEventTimer();
                            sender.sendMessage("§c✗ Авто-ивент выключен!");
                        }
                        case "status" -> showAutoStatus(sender);
                        case "force" -> {
                            sender.sendMessage("§eПринудительный запуск авто-ивента...");
                            autoStartEvent();
                        }
                        case "reload" -> {
                            reloadConfig(); loadAutoEventConfig();
                            if (autoEventEnabled) startAutoEventTimer();
                            else stopAutoEventTimer();
                            sender.sendMessage("§a✓ Настройки перезагружены!");
                            showAutoStatus(sender);
                        }
                        default -> sender.sendMessage("§c/brainrotevent auto <on|off|status|force|reload>");
                    }
                }
                default -> showHelp(sender);
            }
            return true;
        }

        private void showHelp(CommandSender sender) {
            sender.sendMessage("§6=== BrainrotEvents ===");
            sender.sendMessage("§e/brainrotevent start <событие> [минуты]");
            sender.sendMessage("§e/brainrotevent stop");
            sender.sendMessage("§e/brainrotevent list");
            sender.sendMessage("§e/brainrotevent auto <on|off|status|force|reload>");
        }

        private void showAutoStatus(CommandSender sender) {
            sender.sendMessage("§6=== Авто-ивент ===");
            sender.sendMessage("§7Статус: " + (autoEventEnabled ? "§a✓ Включён" : "§c✗ Выключен"));
            sender.sendMessage("§7Ивент: §f" + autoEventId);
            sender.sendMessage("§7Интервал: §fкаждые " + autoIntervalMinutes + " мин");
            sender.sendMessage("§7Длительность: §f" + autoMinDurationMinutes + "-" + autoMaxDurationMinutes + " мин");
            sender.sendMessage("§7Мин. игроков: §f" + autoMinPlayers);
            sender.sendMessage("§7Сейчас: " + (eventRunning ? "§a" + activeEvent + " (" + fmt(secondsLeft) + ")" : "§7нет"));
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) return Arrays.asList("start", "stop", "list", "auto");
            if (args.length == 2) {
                if (args[0].equalsIgnoreCase("start")) return events.keySet().stream().sorted().toList();
                if (args[0].equalsIgnoreCase("auto")) return Arrays.asList("on", "off", "status", "force", "reload");
            }
            return Collections.emptyList();
        }
    }

    // =========================================================
    // Public API
    // =========================================================
    public boolean isEventRunning() { return eventRunning; }
    public String getActiveEvent() { return activeEvent; }
    public int getRemainingSeconds() { return secondsLeft; }
    public Set<String> getAvailableEvents() { return Collections.unmodifiableSet(events.keySet()); }
}