package Polfg.Polfg;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * BrainrotEvents — фоновая музыка + два погодных ивента.
 *
 * Ивенты:
 *  • Плохая Погода — босс-бар на время ивента, дождь/гроза, молнии бьют мобов
 *    на конвейере и навешивают мутацию «Электрический». Урон от молний отключён.
 *  • Метеоритный Дождь — босс-бар, по региону спавна падают фаерболы без урона
 *    и без разрушений; попадание по мобу даёт мутацию «Метеоритный».
 *
 * Сами мутации живут в BrainrotSpawner (стакаются с базовой), сюда они не копируются:
 * плагин дергает публичный API спавнера через рефлексию, поэтому жёсткой зависимости нет.
 */
public class BrainrotEvents extends JavaPlugin implements Listener {

    // ===== Музыка (читается из config.yml) =====
    private boolean musicEnabled = true;
    private String soundKey = "minecraft:day";
    private SoundCategory soundCategory = SoundCategory.MUSIC;
    private float soundVolume = 1.0f;
    private float soundPitch = 1.0f;
    private int trackLengthSeconds = 190;     // длина трека -> интервал повтора
    private boolean restartOnJoin = true;

    // Пер-игровой цикл: у КАЖДОГО игрока свой таймер от момента его старта.
    private final Map<UUID, BukkitTask> playerLoops = new HashMap<>();

    // ===== Ивенты =====
    private enum EventType {
        BAD_WEATHER("badweather", "Плохая Погода", "§9§l", BarColor.BLUE),
        METEOR_SHOWER("meteor", "Метеоритный Дождь", "§c§l", BarColor.RED);

        final String key;
        final String title;
        final String format;
        final BarColor color;

        EventType(String key, String title, String format, BarColor color) {
            this.key = key; this.title = title; this.format = format; this.color = color;
        }

        static EventType byKey(String s) {
            if (s == null) return null;
            String k = s.toLowerCase(Locale.ROOT);
            for (EventType t : values()) if (t.key.equals(k) || t.name().toLowerCase(Locale.ROOT).equals(k)) return t;
            if (k.startsWith("плох") || k.startsWith("weather") || k.startsWith("storm")) return BAD_WEATHER;
            if (k.startsWith("метео") || k.startsWith("meteor")) return METEOR_SHOWER;
            return null;
        }
    }
    private static final String METEOR_TAG = "BRAINROT_METEOR";
    private final Random random = new Random();

    private EventType activeEvent;
    private BossBar activeBar;
    private World activeWorld;
    private long eventEndMillis;
    private int eventTotalSeconds;
    private BukkitTask barTask;
    private BukkitTask actionTask;
    private BukkitTask meteorTickTask;
    private BukkitTask autoTask;
    private final Set<Entity> activeMeteors = new HashSet<>();
    private boolean weatherRestore = false;

    // Конфиг ивентов
    private String eventsWorldName = "";
    private boolean autoEnabled = false;
    private int autoIntervalMinutes = 20;
    private boolean weatherEnabled = true;
    private int weatherDuration = 180;
    private int weatherStrikeInterval = 40;
    private double weatherStrikeChance = 0.35;
    private int weatherStrikesPerWave = 1;
    private boolean meteorEnabled = true;
    private int meteorDuration = 180;
    private int meteorWaveInterval = 25;
    private int meteorPerWave = 2;
    private double meteorSpawnHeight = 28;
    private double meteorHitRadius = 2.5;
    private String regionName = "spawn";
    private double regionPadding = 8;
    private double manualMinX = 0, manualMinZ = 0, manualMaxX = 0, manualMaxZ = 0;

    // Кэш границ области ивента: minX, minZ, maxX, maxZ
    private double[] regionBounds;

    // =========================================================
    // LIFECYCLE
    // =========================================================
    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadMusicConfig();
        loadEventsConfig();

        if (getCommand("brainrotmusic") != null) {
            MusicCommand cmd = new MusicCommand();
            getCommand("brainrotmusic").setExecutor(cmd);
            getCommand("brainrotmusic").setTabCompleter(cmd);
        }
        if (getCommand("brainrotevent") != null) {
            EventCommand cmd = new EventCommand();
            getCommand("brainrotevent").setExecutor(cmd);
            getCommand("brainrotevent").setTabCompleter(cmd);
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        if (musicEnabled) {
            for (Player p : Bukkit.getOnlinePlayers()) startPlayerLoop(p);
        }
        startAutoScheduler();
        getLogger().info("BrainrotEvents включён. Трек: " + soundKey + ", ивенты: Плохая Погода / Метеоритный Дождь");
    }

    @Override
    public void onDisable() {
        try { stopEvent(true); } catch (Throwable ignored) {}
        try { Bukkit.getScheduler().cancelTasks(this); } catch (Throwable __t) {}
        try { org.bukkit.event.HandlerList.unregisterAll((org.bukkit.plugin.Plugin) this); } catch (Throwable __t) {}
        stopAllLoops();
    }
    // =========================================================
    // CONFIG
    // =========================================================
    private void loadMusicConfig() {
        FileConfiguration cfg = getConfig();
        boolean changed = false;
        if (!cfg.contains("music.enabled"))        { cfg.set("music.enabled", true);            changed = true; }
        if (!cfg.contains("music.sound"))          { cfg.set("music.sound", "minecraft:day");   changed = true; }
        if (!cfg.contains("music.category"))       { cfg.set("music.category", "MUSIC");         changed = true; }
        if (!cfg.contains("music.volume"))         { cfg.set("music.volume", 1.0);              changed = true; }
        if (!cfg.contains("music.pitch"))          { cfg.set("music.pitch", 1.0);               changed = true; }
        if (!cfg.contains("music.length-seconds")) { cfg.set("music.length-seconds", 190);       changed = true; }
        if (!cfg.contains("music.restart-on-join")){ cfg.set("music.restart-on-join", true);     changed = true; }
        if (changed) saveConfig();

        musicEnabled       = cfg.getBoolean("music.enabled", true);
        soundKey           = cfg.getString("music.sound", "minecraft:day");
        soundVolume        = (float) cfg.getDouble("music.volume", 1.0);
        soundPitch         = (float) cfg.getDouble("music.pitch", 1.0);
        trackLengthSeconds = cfg.getInt("music.length-seconds", 190);
        restartOnJoin      = cfg.getBoolean("music.restart-on-join", true);

        String cat = cfg.getString("music.category", "MUSIC");
        try { soundCategory = SoundCategory.valueOf(cat.toUpperCase(Locale.ROOT)); }
        catch (Exception ex) {
            getLogger().warning("Неизвестная категория звука '" + cat + "', использую MUSIC");
            soundCategory = SoundCategory.MUSIC;
        }

        if (trackLengthSeconds < 1) trackLengthSeconds = 1;
        if (soundVolume < 0f) soundVolume = 0f;
        if (soundPitch < 0.5f) soundPitch = 0.5f;
        if (soundPitch > 2.0f) soundPitch = 2.0f;
    }

    private void loadEventsConfig() {
        FileConfiguration cfg = getConfig();
        boolean changed = false;
        changed |= def("events.world", "");
        changed |= def("events.auto.enabled", false);
        changed |= def("events.auto.interval-minutes", 20);
        changed |= def("events.bad-weather.enabled", true);
        changed |= def("events.bad-weather.duration-seconds", 180);
        changed |= def("events.bad-weather.strike-interval-ticks", 40);
        changed |= def("events.bad-weather.strike-chance", 0.35);
        changed |= def("events.bad-weather.strikes-per-wave", 1);
        changed |= def("events.meteor-shower.enabled", true);
        changed |= def("events.meteor-shower.duration-seconds", 180);
        changed |= def("events.meteor-shower.wave-interval-ticks", 25);
        changed |= def("events.meteor-shower.fireballs-per-wave", 2);
        changed |= def("events.meteor-shower.spawn-height", 28);
        changed |= def("events.meteor-shower.hit-radius", 2.5);
        changed |= def("events.region.worldguard-region", "spawn");
        changed |= def("events.region.padding", 8);
        changed |= def("events.region.min-x", 0);
        changed |= def("events.region.min-z", 0);
        changed |= def("events.region.max-x", 0);
        changed |= def("events.region.max-z", 0);
        if (changed) saveConfig();

        eventsWorldName      = cfg.getString("events.world", "");
        autoEnabled          = cfg.getBoolean("events.auto.enabled", false);
        autoIntervalMinutes  = Math.max(1, cfg.getInt("events.auto.interval-minutes", 20));
        weatherEnabled       = cfg.getBoolean("events.bad-weather.enabled", true);
        weatherDuration      = Math.max(5, cfg.getInt("events.bad-weather.duration-seconds", 180));
        weatherStrikeInterval= Math.max(5, cfg.getInt("events.bad-weather.strike-interval-ticks", 40));
        weatherStrikeChance  = Math.min(1.0, Math.max(0.0, cfg.getDouble("events.bad-weather.strike-chance", 0.35)));
        weatherStrikesPerWave= Math.max(1, cfg.getInt("events.bad-weather.strikes-per-wave", 1));
        meteorEnabled        = cfg.getBoolean("events.meteor-shower.enabled", true);
        meteorDuration       = Math.max(5, cfg.getInt("events.meteor-shower.duration-seconds", 180));
        meteorWaveInterval   = Math.max(5, cfg.getInt("events.meteor-shower.wave-interval-ticks", 25));
        meteorPerWave        = Math.max(1, cfg.getInt("events.meteor-shower.fireballs-per-wave", 2));
        meteorSpawnHeight    = Math.max(5, cfg.getDouble("events.meteor-shower.spawn-height", 28));
        meteorHitRadius      = Math.max(0.5, cfg.getDouble("events.meteor-shower.hit-radius", 2.5));
        regionName           = cfg.getString("events.region.worldguard-region", "spawn");
        regionPadding        = Math.max(0, cfg.getDouble("events.region.padding", 8));
        manualMinX           = cfg.getDouble("events.region.min-x", 0);
        manualMinZ           = cfg.getDouble("events.region.min-z", 0);
        manualMaxX           = cfg.getDouble("events.region.max-x", 0);
        manualMaxZ           = cfg.getDouble("events.region.max-z", 0);
        regionBounds = null;
    }

    private boolean def(String path, Object value) {
        if (getConfig().contains(path)) return false;
        getConfig().set(path, value);
        return true;
    }
    // =========================================================
    // МУЗЫКА (пер-игровой цикл)
    // =========================================================
    private void playOnce(Player p) {
        try { p.playSound(p.getLocation(), soundKey, soundCategory, soundVolume, soundPitch); }
        catch (Throwable t) {
            try { p.playSound(p.getLocation(), soundKey, soundVolume, soundPitch); } catch (Throwable ignored) {}
        }
    }

    private void stopSoundFor(Player p) {
        try { p.stopSound(soundKey, soundCategory); }
        catch (Throwable t) { try { p.stopSound(soundKey); } catch (Throwable ignored) {} }
    }

    private void startPlayerLoop(Player p) {
        stopPlayerLoop(p);
        if (!musicEnabled) return;
        final UUID id = p.getUniqueId();

        stopSoundFor(p);
        playOnce(p);

        long periodTicks = trackLengthSeconds * 20L;
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() {
                Player pl = Bukkit.getPlayer(id);
                if (pl == null || !pl.isOnline()) { stopPlayerLoopById(id); return; }
                playOnce(pl);
            }
        }.runTaskTimer(this, periodTicks, periodTicks);

        playerLoops.put(id, task);
    }

    private void stopPlayerLoop(Player p) { stopPlayerLoopById(p.getUniqueId()); }

    private void stopPlayerLoopById(UUID id) {
        BukkitTask t = playerLoops.remove(id);
        if (t != null) { try { t.cancel(); } catch (Throwable ignored) {} }
    }

    private void stopAllLoops() {
        for (BukkitTask t : new ArrayList<>(playerLoops.values())) {
            try { t.cancel(); } catch (Throwable ignored) {}
        }
        playerLoops.clear();
        for (Player p : Bukkit.getOnlinePlayers()) stopSoundFor(p);
    }

    private void restartAllLoops() {
        stopAllLoops();
        if (musicEnabled) for (Player p : Bukkit.getOnlinePlayers()) startPlayerLoop(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        final UUID id = p.getUniqueId();
        if (activeBar != null && activeWorld != null && p.getWorld().equals(activeWorld)) activeBar.addPlayer(p);
        if (!musicEnabled) return;
        // Небольшая задержка, чтобы клиент успел подгрузить ресурспак.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player pl = Bukkit.getPlayer(id);
            if (pl == null || !pl.isOnline()) return;
            if (restartOnJoin || !playerLoops.containsKey(id)) startPlayerLoop(pl);
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stopPlayerLoop(e.getPlayer());
        if (activeBar != null) activeBar.removePlayer(e.getPlayer());
    }
    // =========================================================
    // ИВЕНТЫ: запуск / остановка / босс-бар
    // =========================================================
    private boolean startEvent(EventType type, int seconds, CommandSender feedback) {
        if (type == null) return false;
        if (type == EventType.BAD_WEATHER && !weatherEnabled) {
            if (feedback != null) feedback.sendMessage("§cИвент «Плохая Погода» отключён в конфиге.");
            return false;
        }
        if (type == EventType.METEOR_SHOWER && !meteorEnabled) {
            if (feedback != null) feedback.sendMessage("§cИвент «Метеоритный Дождь» отключён в конфиге.");
            return false;
        }
        if (activeEvent != null) stopEvent(true);

        World world = resolveWorld(feedback);
        if (world == null) {
            if (feedback != null) feedback.sendMessage("§cНе найден мир для ивента (events.world в конфиге).");
            return false;
        }
        if (seconds <= 0) seconds = (type == EventType.BAD_WEATHER) ? weatherDuration : meteorDuration;

        activeEvent = type;
        activeWorld = world;
        eventTotalSeconds = seconds;
        eventEndMillis = System.currentTimeMillis() + seconds * 1000L;

        activeBar = Bukkit.createBossBar(type.format + type.title, type.color, BarStyle.SEGMENTED_10);
        activeBar.setProgress(1.0);
        activeBar.setVisible(true);
        syncBarPlayers();

        for (Player p : world.getPlayers()) {
            p.sendMessage(type.format + "✦ Начался ивент: " + type.title + "!");
            try { p.sendTitle(type.format + type.title, "§7" + eventTotalSeconds + " секунд", 10, 50, 20); }
            catch (Throwable ignored) {}
            p.playSound(p.getLocation(), type == EventType.BAD_WEATHER ? Sound.ENTITY_LIGHTNING_BOLT_THUNDER
                    : Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.8f);
        }

        barTask = new BukkitRunnable() {
            @Override public void run() { tickBar(); }
        }.runTaskTimer(this, 20L, 20L);

        if (type == EventType.BAD_WEATHER) startBadWeather(world, seconds);
        else startMeteorShower(world);

        getLogger().info("Ивент " + type.title + " запущен на " + seconds + "с в мире " + world.getName());
        return true;
    }

    private void tickBar() {
        if (activeEvent == null || activeBar == null) return;
        long left = eventEndMillis - System.currentTimeMillis();
        if (left <= 0) { stopEvent(false); return; }
        int secondsLeft = (int) Math.ceil(left / 1000.0);
        double progress = Math.max(0.0, Math.min(1.0, (double) secondsLeft / (double) eventTotalSeconds));
        activeBar.setProgress(progress);
        activeBar.setTitle(activeEvent.format + activeEvent.title + " §7— §f" + formatTime(secondsLeft));
        syncBarPlayers();
    }

    private void syncBarPlayers() {
        if (activeBar == null || activeWorld == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(activeWorld)) activeBar.addPlayer(p);
            else activeBar.removePlayer(p);
        }
    }

    private static String formatTime(int seconds) {
        int m = seconds / 60, s = seconds % 60;
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    private void stopEvent(boolean silent) {
        EventType type = activeEvent;
        World world = activeWorld;
        if (barTask != null) { try { barTask.cancel(); } catch (Throwable ignored) {} barTask = null; }
        if (actionTask != null) { try { actionTask.cancel(); } catch (Throwable ignored) {} actionTask = null; }
        if (meteorTickTask != null) { try { meteorTickTask.cancel(); } catch (Throwable ignored) {} meteorTickTask = null; }
        if (activeBar != null) { try { activeBar.removeAll(); activeBar.setVisible(false); } catch (Throwable ignored) {} activeBar = null; }
        for (Entity meteor : new ArrayList<>(activeMeteors)) {
            try { if (meteor.isValid()) meteor.remove(); } catch (Throwable ignored) {}
        }
        activeMeteors.clear();
        if (weatherRestore && world != null) {
            try {
                world.setThundering(false);
                world.setStorm(false);
                world.setWeatherDuration(0);
                world.setClearWeatherDuration(20 * 60 * 10);
            } catch (Throwable ignored) {}
            weatherRestore = false;
        }
        activeEvent = null;
        activeWorld = null;
        if (!silent && type != null && world != null) {
            for (Player p : world.getPlayers()) {
                p.sendMessage("§7✦ Ивент «" + type.title + "» закончился.");
            }
            getLogger().info("Ивент " + type.title + " завершён.");
        }
    }

    private void startAutoScheduler() {
        if (autoTask != null) { try { autoTask.cancel(); } catch (Throwable ignored) {} autoTask = null; }
        if (!autoEnabled) return;
        long period = autoIntervalMinutes * 60L * 20L;
        autoTask = new BukkitRunnable() {
            @Override public void run() {
                if (activeEvent != null) return;
                World w = resolveWorld();
                if (w == null || w.getPlayers().isEmpty()) return;
                List<EventType> pool = new ArrayList<>();
                if (weatherEnabled) pool.add(EventType.BAD_WEATHER);
                if (meteorEnabled) pool.add(EventType.METEOR_SHOWER);
                if (pool.isEmpty()) return;
                startEvent(pool.get(random.nextInt(pool.size())), 0, null);
            }
        }.runTaskTimer(this, period, period);
    }
    // =========================================================
    // ИВЕНТ 1: ПЛОХАЯ ПОГОДА (молния -> мутация Электрический)
    // =========================================================
    private void startBadWeather(World world, int seconds) {
        try {
            world.setStorm(true);
            world.setThundering(true);
            world.setWeatherDuration(seconds * 20 + 200);
            world.setThunderDuration(seconds * 20 + 200);
            weatherRestore = true;
        } catch (Throwable ignored) {}

        actionTask = new BukkitRunnable() {
            @Override public void run() {
                if (activeEvent != EventType.BAD_WEATHER) { cancel(); return; }
                try { world.setStorm(true); world.setThundering(true); } catch (Throwable ignored) {}
                List<Entity> mobs = getSpawnerMobs();
                if (mobs.isEmpty()) return;
                for (int i = 0; i < weatherStrikesPerWave; i++) {
                    if (random.nextDouble() > weatherStrikeChance) continue;
                    Entity target = mobs.get(random.nextInt(mobs.size()));
                    if (target == null || !target.isValid()) continue;
                    if (!target.getWorld().equals(world)) continue;
                    strikeMob(target);
                }
            }
        }.runTaskTimer(this, weatherStrikeInterval, weatherStrikeInterval);
    }

    private void strikeMob(Entity mob) {
        Location loc = mob.getLocation();
        try { mob.getWorld().strikeLightningEffect(loc); } catch (Throwable ignored) {}
        try {
            mob.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.15);
            mob.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.2f, 1.0f);
        } catch (Throwable ignored) {}
        if (applyStackableMutation(mob, "ELECTRIC")) {
            for (Player p : mob.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(loc) <= 60 * 60) {
                    p.sendMessage("§e⚡ Молния ударила в моба — мутация §eЭлектрический§e!");
                }
            }
        }
    }

    // =========================================================
    // ИВЕНТ 2: МЕТЕОРИТНЫЙ ДОЖДЬ (фаербол -> мутация Метеоритный)
    // =========================================================
    private void startMeteorShower(World world) {
        actionTask = new BukkitRunnable() {
            @Override public void run() {
                if (activeEvent != EventType.METEOR_SHOWER) { cancel(); return; }
                double[] box = getRegionBounds(world);
                if (box == null) return;
                for (int i = 0; i < meteorPerWave; i++) spawnMeteor(world, box);
            }
        }.runTaskTimer(this, 20L, meteorWaveInterval);

        meteorTickTask = new BukkitRunnable() {
            @Override public void run() {
                if (activeEvent != EventType.METEOR_SHOWER) { cancel(); return; }
                tickMeteors();
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    private void spawnMeteor(World world, double[] box) {
        double x = box[0] + random.nextDouble() * Math.max(0.1, box[2] - box[0]);
        double z = box[1] + random.nextDouble() * Math.max(0.1, box[3] - box[1]);
        double baseY = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z));
        Location spawn = new Location(world, x, baseY + meteorSpawnHeight, z);
        try {
            LargeFireball fb = world.spawn(spawn, LargeFireball.class);
            fb.setYield(0f);
            fb.setIsIncendiary(false);
            fb.setDirection(new Vector((random.nextDouble() - 0.5) * 0.15, -1.0, (random.nextDouble() - 0.5) * 0.15));
            fb.setVelocity(fb.getDirection().normalize().multiply(0.75));
            fb.addScoreboardTag(METEOR_TAG);
            fb.setGravity(false);
            activeMeteors.add(fb);
            world.playSound(spawn, Sound.ENTITY_BLAZE_SHOOT, 1.6f, 0.6f);
        } catch (Throwable t) {
            getLogger().warning("Не удалось создать метеорит: " + t.getMessage());
        }
    }
    private void tickMeteors() {
        if (activeMeteors.isEmpty()) return;
        List<Entity> mobs = null;
        Iterator<Entity> it = activeMeteors.iterator();
        while (it.hasNext()) {
            Entity meteor = it.next();
            if (meteor == null || !meteor.isValid()) { it.remove(); continue; }
            Location loc = meteor.getLocation();
            try {
                meteor.getWorld().spawnParticle(Particle.FLAME, loc, 6, 0.15, 0.15, 0.15, 0.01);
                meteor.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 3, 0.2, 0.2, 0.2, 0.01);
                meteor.getWorld().spawnParticle(Particle.LAVA, loc, 1, 0.1, 0.1, 0.1, 0.0);
            } catch (Throwable ignored) {}
            if (mobs == null) mobs = getSpawnerMobs();
            for (Entity mob : mobs) {
                if (mob == null || !mob.isValid()) continue;
                if (!mob.getWorld().equals(meteor.getWorld())) continue;
                if (mob.getLocation().distanceSquared(loc) <= meteorHitRadius * meteorHitRadius) {
                    meteorImpact(meteor, mob);
                    it.remove();
                    break;
                }
            }
        }
    }

    private void meteorImpact(Entity meteor, Entity mob) {
        Location loc = meteor.getLocation();
        try { meteor.remove(); } catch (Throwable ignored) {}
        try {
            meteor.getWorld().spawnParticle(Particle.EXPLOSION, loc, 2, 0.3, 0.3, 0.3, 0.0);
            meteor.getWorld().spawnParticle(Particle.FLAME, loc, 40, 0.6, 0.6, 0.6, 0.08);
            meteor.getWorld().spawnParticle(Particle.LAVA, loc, 12, 0.4, 0.4, 0.4, 0.0);
            meteor.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.4f);
        } catch (Throwable ignored) {}
        if (mob != null && applyStackableMutation(mob, "METEOR")) {
            for (Player p : loc.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(loc) <= 60 * 60) {
                    p.sendMessage("§c☄ Метеорит попал в моба — мутация §cМетеоритный§c!");
                }
            }
        }
    }

    private void handleMeteorLanding(Entity meteor) {
        if (!activeMeteors.remove(meteor)) return;
        Location loc = meteor.getLocation();
        Entity closest = null;
        double best = meteorHitRadius * meteorHitRadius;
        for (Entity mob : getSpawnerMobs()) {
            if (mob == null || !mob.isValid() || !mob.getWorld().equals(meteor.getWorld())) continue;
            double d = mob.getLocation().distanceSquared(loc);
            if (d <= best) { best = d; closest = mob; }
        }
        meteorImpact(meteor, closest);
    }

    // =========================================================
    // РЕГИОН (WorldGuard -> конвейеры -> ручной бокс)
    // =========================================================
    private double[] getRegionBounds(World world) {
        if (regionBounds != null) return regionBounds;
        double[] box = boundsFromWorldGuard(world);
        if (box == null) box = boundsFromConveyors(world);
        if (box == null) box = boundsFromConfig();
        if (box != null) { regionBounds = box; return box; }
        // Последний шанс: сыпем вокруг игроков, чтобы ивент не был пустым (не кэшируем — игроки ходят).
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        int used = 0;
        for (Player p : world.getPlayers()) {
            Location l = p.getLocation();
            minX = Math.min(minX, l.getX() - 24); maxX = Math.max(maxX, l.getX() + 24);
            minZ = Math.min(minZ, l.getZ() - 24); maxZ = Math.max(maxZ, l.getZ() + 24);
            used++;
        }
        if (used == 0) {
            if (!regionWarned) {
                regionWarned = true;
                getLogger().warning("Не удалось определить область метеоритов: нет WorldGuard-региона '"
                        + regionName + "', нет конвейеров в мире " + world.getName()
                        + ", нет ручного бокса в events.region.min-x/max-x и нет игроков.");
            }
            return null;
        }
        return new double[]{minX, minZ, maxX, maxZ};
    }

    private boolean regionWarned = false;

    private double[] boundsFromWorldGuard(World world) {
        if (regionName == null || regionName.isEmpty()) return null;
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object wg = wgClass.getMethod("getInstance").invoke(null);
            Object platform = wgClass.getMethod("getPlatform").invoke(wg);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object weWorld = adapter.getMethod("adapt", World.class).invoke(null, world);
            Object manager = container.getClass()
                    .getMethod("get", Class.forName("com.sk89q.worldedit.world.World"))
                    .invoke(container, weWorld);
            if (manager == null) return null;
            Object region = manager.getClass().getMethod("getRegion", String.class).invoke(manager, regionName);
            if (region == null) return null;
            Object min = region.getClass().getMethod("getMinimumPoint").invoke(region);
            Object max = region.getClass().getMethod("getMaximumPoint").invoke(region);
            int minX = (int) min.getClass().getMethod("getBlockX").invoke(min);
            int minZ = (int) min.getClass().getMethod("getBlockZ").invoke(min);
            int maxX = (int) max.getClass().getMethod("getBlockX").invoke(max);
            int maxZ = (int) max.getClass().getMethod("getBlockZ").invoke(max);
            getLogger().info("Метеориты: регион '" + regionName + "' " + minX + "," + minZ + " -> " + maxX + "," + maxZ);
            return new double[]{Math.min(minX, maxX), Math.min(minZ, maxZ), Math.max(minX, maxX) + 1, Math.max(minZ, maxZ) + 1};
        } catch (Throwable t) {
            return null;
        }
    }

    private double[] boundsFromConveyors(World world) {
        List<Location> points = getConveyorPoints();
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        int used = 0;
        for (Location loc : points) {
            if (loc == null || loc.getWorld() == null || !loc.getWorld().equals(world)) continue;
            minX = Math.min(minX, loc.getX()); maxX = Math.max(maxX, loc.getX());
            minZ = Math.min(minZ, loc.getZ()); maxZ = Math.max(maxZ, loc.getZ());
            used++;
        }
        if (used == 0) return null;
        getLogger().info("Метеориты: регион взят по конвейерам (" + used + " точек).");
        return new double[]{minX - regionPadding, minZ - regionPadding, maxX + regionPadding, maxZ + regionPadding};
    }

    private double[] boundsFromConfig() {
        if (manualMinX == 0 && manualMinZ == 0 && manualMaxX == 0 && manualMaxZ == 0) return null;
        return new double[]{Math.min(manualMinX, manualMaxX), Math.min(manualMinZ, manualMaxZ),
                Math.max(manualMinX, manualMaxX), Math.max(manualMinZ, manualMaxZ)};
    }
    private World resolveWorld() {
        return resolveWorld(null);
    }

    /**
     * Мир ивента. Порядок: events.world из конфига -> мир того, кто запустил команду ->
     * мир конвейеров спавнера -> мир, где больше всего игроков -> первый загруженный.
     * Важно: раньше сразу брался первый мир, из-за чего ивент уезжал в другой мир,
     * и игрок не видел ни босс-бара, ни дождя, ни метеоритов.
     */
    private World resolveWorld(CommandSender starter) {
        if (activeWorld != null) return activeWorld;
        if (eventsWorldName != null && !eventsWorldName.isEmpty()) {
            World w = Bukkit.getWorld(eventsWorldName);
            if (w != null) return w;
            getLogger().warning("Мир '" + eventsWorldName + "' из events.world не найден.");
        }
        if (starter instanceof Player p) return p.getWorld();
        for (Location loc : getConveyorPoints()) {
            if (loc != null && loc.getWorld() != null) return loc.getWorld();
        }
        World best = null;
        int bestCount = -1;
        for (World w : Bukkit.getWorlds()) {
            int c = w.getPlayers().size();
            if (c > bestCount) { bestCount = c; best = w; }
        }
        if (best != null) return best;
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    // =========================================================
    // МОСТ К BrainrotSpawner (через рефлексию — плагины не связаны зависимостью)
    // =========================================================
    private Object getSpawnerPlugin() {
        try {
            org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("BrainrotSpawner");
            return (p != null && p.isEnabled()) ? p : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Entity> getSpawnerMobs() {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return Collections.emptyList();
        try {
            Object res = spawner.getClass().getMethod("getSpawnerMobs").invoke(spawner);
            if (res instanceof List) return (List<Entity>) res;
        } catch (Throwable ignored) {}
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<Location> getConveyorPoints() {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return Collections.emptyList();
        try {
            Object res = spawner.getClass().getMethod("getConveyorPoints").invoke(spawner);
            if (res instanceof List) return (List<Location>) res;
        } catch (Throwable ignored) {}
        return Collections.emptyList();
    }

    private boolean applyStackableMutation(Entity mob, String mutation) {
        Object spawner = getSpawnerPlugin();
        if (spawner == null || mob == null) return false;
        try {
            Object res = spawner.getClass()
                    .getMethod("applyStackableMutation", Entity.class, String.class)
                    .invoke(spawner, mob, mutation);
            return res instanceof Boolean && (Boolean) res;
        } catch (Throwable t) {
            return false;
        }
    }

    // =========================================================
    // ОТКЛЮЧЕНИЕ УРОНА (молнии и метеориты — только косметика)
    // =========================================================
    private boolean isMeteor(Entity e) {
        return e != null && e.getScoreboardTags().contains(METEOR_TAG);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnyDamage(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.LIGHTNING) { event.setCancelled(true); return; }
        if (activeEvent == EventType.BAD_WEATHER
                && (cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof LightningStrike || isMeteor(damager)) {
            event.setDamage(0.0);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombust(EntityCombustByEntityEvent event) {
        if (event.getCombuster() instanceof LightningStrike || isMeteor(event.getCombuster())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onIgnite(BlockIgniteEvent event) {
        BlockIgniteEvent.IgniteCause cause = event.getCause();
        if (cause == BlockIgniteEvent.IgniteCause.LIGHTNING) { event.setCancelled(true); return; }
        if (isMeteor(event.getIgnitingEntity())) event.setCancelled(true);
        else if (activeEvent == EventType.METEOR_SHOWER && cause == BlockIgniteEvent.IgniteCause.FIREBALL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (!isMeteor(event.getEntity())) return;
        event.setRadius(0f);
        event.setFire(false);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplode(EntityExplodeEvent event) {
        if (!isMeteor(event.getEntity())) return;
        event.blockList().clear();
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!isMeteor(event.getEntity())) return;
        handleMeteorLanding(event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNaturalLightning(LightningStrikeEvent event) {
        if (activeEvent != EventType.BAD_WEATHER) return;
        if (event.getCause() == LightningStrikeEvent.Cause.WEATHER) event.setCancelled(true);
    }
    // =========================================================
    // КОМАНДА /brainrotmusic
    // =========================================================
    private class MusicCommand implements CommandExecutor, TabCompleter {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("brainrotevents.admin")) {
                sender.sendMessage("§cНет прав.");
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage("§6/" + label + " on §7— включить музыку всем");
                sender.sendMessage("§6/" + label + " off §7— выключить музыку всем");
                sender.sendMessage("§6/" + label + " restart §7— перезапустить трек всем");
                sender.sendMessage("§6/" + label + " reload §7— перечитать конфиг");
                sender.sendMessage("§6/" + label + " status §7— текущее состояние");
                return true;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "on" -> {
                    musicEnabled = true;
                    getConfig().set("music.enabled", true);
                    saveConfig();
                    restartAllLoops();
                    sender.sendMessage("§aМузыка включена.");
                }
                case "off" -> {
                    musicEnabled = false;
                    getConfig().set("music.enabled", false);
                    saveConfig();
                    stopAllLoops();
                    for (Player p : Bukkit.getOnlinePlayers()) stopSoundFor(p);
                    sender.sendMessage("§aМузыка выключена.");
                }
                case "restart" -> {
                    restartAllLoops();
                    sender.sendMessage("§aТрек перезапущен для всех игроков.");
                }
                case "reload" -> {
                    reloadConfig();
                    loadMusicConfig();
                    loadEventsConfig();
                    regionBounds = null;
                    restartAllLoops();
                    startAutoScheduler();
                    sender.sendMessage("§aКонфиг перечитан. Трек: §f" + soundKey);
                }
                case "status" -> {
                    sender.sendMessage("§eМузыка: " + (musicEnabled ? "§aвкл" : "§cвыкл")
                            + " §7| трек §f" + soundKey + " §7| длина §f" + trackLengthSeconds + "с"
                            + " §7| активных циклов §f" + playerLoops.size());
                    sender.sendMessage("§eИвент: " + (activeEvent == null ? "§7нет" : "§f" + activeEvent.title));
                }
                default -> sender.sendMessage("§cНеизвестная подкоманда. Напиши /" + label + " без аргументов.");
            }
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length != 1) return Collections.emptyList();
            String p = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String o : List.of("on", "off", "restart", "reload", "status")) {
                if (o.startsWith(p)) out.add(o);
            }
            return out;
        }
    }

    // =========================================================
    // КОМАНДА /brainrotevent
    // =========================================================
    private class EventCommand implements CommandExecutor, TabCompleter {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("brainrotevents.admin")) {
                sender.sendMessage("§cНет прав.");
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage("§6/" + label + " start <badweather|meteor> [секунды]");
                sender.sendMessage("§6/" + label + " stop §7— остановить текущий ивент");
                sender.sendMessage("§6/" + label + " status §7— что сейчас идёт");
                sender.sendMessage("§6/" + label + " reload §7— перечитать конфиг");
                return true;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "start" -> {
                    if (args.length < 2) { sender.sendMessage("§cУкажи ивент: badweather или meteor."); return true; }
                    EventType type = EventType.byKey(args[1]);
                    if (type == null) { sender.sendMessage("§cНеизвестный ивент: " + args[1]); return true; }
                    int seconds = 0;
                    if (args.length >= 3) {
                        try { seconds = Math.max(1, Integer.parseInt(args[2])); }
                        catch (NumberFormatException ex) { sender.sendMessage("§cВремя должно быть числом."); return true; }
                    }
                    if (startEvent(type, seconds, sender)) {
                        sender.sendMessage("§aИвент «" + type.title + "» запущен.");
                    }
                }
                case "stop" -> {
                    if (activeEvent == null) { sender.sendMessage("§7Сейчас нет активного ивента."); return true; }
                    String name = activeEvent.title;
                    stopEvent(false);
                    sender.sendMessage("§aИвент «" + name + "» остановлен.");
                }
                case "status" -> {
                    World w = activeWorld != null ? activeWorld : resolveWorld(sender);
                    sender.sendMessage("§7Мир ивентов: §f" + (w != null ? w.getName() : "?")
                            + " §7(events.world = §f" + (eventsWorldName.isEmpty() ? "авто" : eventsWorldName) + "§7)");
                    sender.sendMessage("§7Спавнер подключён: " + (getSpawnerPlugin() != null ? "§aда" : "§cнет")
                            + " §7| мобов на конвейере: §f" + getSpawnerMobs().size());
                    if (w != null) {
                        double[] box = getRegionBounds(w);
                        sender.sendMessage("§7Область метеоритов: §f" + (box == null ? "не определена"
                                : String.format(Locale.US, "%.0f,%.0f → %.0f,%.0f", box[0], box[1], box[2], box[3])));
                    }
                    if (activeEvent == null) { sender.sendMessage("§7Активного ивента нет."); return true; }
                    int left = (int) Math.max(0, Math.ceil((eventEndMillis - System.currentTimeMillis()) / 1000.0));
                    sender.sendMessage("§eИдёт: " + activeEvent.title + " §7(осталось " + formatTime(left) + ")");
                    if (activeEvent == EventType.METEOR_SHOWER) {
                        sender.sendMessage("§7Метеоритов в воздухе: " + activeMeteors.size());
                    }
                }
                case "reload" -> {
                    reloadConfig();
                    loadMusicConfig();
                    loadEventsConfig();
                    regionBounds = null;
                    restartAllLoops();
                    startAutoScheduler();
                    sender.sendMessage("§aКонфиг перечитан.");
                }
                default -> sender.sendMessage("§cНеизвестная подкоманда. Напиши /" + label + " без аргументов.");
            }
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) {
                return filter(List.of("start", "stop", "status", "reload"), args[0]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
                return filter(List.of("badweather", "meteor"), args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("start")) {
                return filter(List.of("60", "120", "180", "300"), args[2]);
            }
            return Collections.emptyList();
        }

        private List<String> filter(List<String> options, String prefix) {
            String p = prefix.toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
            return out;
        }
    }
}
