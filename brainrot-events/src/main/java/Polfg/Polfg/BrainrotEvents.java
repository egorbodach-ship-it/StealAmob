package Polfg.Polfg;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.CreeperPowerEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
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
 * BrainrotEvents — фоновая музыка + четыре ивента.
 *
 * Ивенты:
 *  • Плохая Погода — босс-бар на время ивента, дождь/гроза, молнии бьют мобов
 *    на конвейере и навешивают мутацию «Электрический». Урон от молний отключён.
 *  • Метеоритный Дождь — босс-бар, по региону спавна падают фаерболы без урона
 *    и без разрушений; попадание по мобу даёт мутацию «Метеоритный».
 *  • Крипер Пати — босс-бар, вдоль двух линий выставляются неуязвимые криперы,
 *    они синхронно прыгают, а иногда один бежит к мобу на конвейере и «взрывается»
 *    (косметика, без урона и грифинга) — моб получает мутацию «Взрывной».
 *    Реже вместо моба крипер идёт к ближайшему игроку и отбрасывает его без урона.
 *  • 3 Тропы — босс-бар, стена спавна меняется на схему с тремя проходами, под
 *    двумя новыми тропами кладётся красный бетон, и на время ивента поднимаются
 *    две дополнительные дорожки конвейера. По окончании возвращается исходная
 *    схема и дёрн, а временные дорожки снимаются. Запускается только вручную,
 *    но может идти одновременно с остальными ивентами.
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
        METEOR_SHOWER("meteor", "Метеоритный Дождь", "§c§l", BarColor.RED),
        CREEPER_PARTY("creeper", "Крипер Пати", "§a§l", BarColor.GREEN),
        THREE_ROADS("3road", "3 Тропы", "§6§l", BarColor.YELLOW),
        LUCKY_2X("2x", "2x Удача", "§d§l", BarColor.PURPLE),
        GUM_MACHINE("gum", "Бабл Гам Машина", "§d§l", BarColor.PINK),
        UFO("ufo", "НЛО", "§a§l", BarColor.GREEN);

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
            if (k.startsWith("крип") || k.startsWith("creep") || k.startsWith("party") || k.startsWith("пати")) return CREEPER_PARTY;
            if (k.startsWith("нло") || k.startsWith("ufo") || k.startsWith("тарел")
                    || k.startsWith("приш") || k.startsWith("alien") || k.startsWith("saucer")) return UFO;
            // Машину проверяем до «2x»: «бабл гам» с цифрами не пересекается, зато
            // слово «gum» короткое и его легко перехватить чужим префиксом.
            if (k.startsWith("бабл") || k.startsWith("гам") || k.startsWith("жвач")
                    || k.startsWith("gum") || k.startsWith("bubble")) return GUM_MACHINE;
            // «2x удача» проверяется раньше троп: иначе «2x» съел бы префикс-матч по цифре.
            if (k.startsWith("удач") || k.startsWith("luck") || k.startsWith("2") || k.equals("x2")) return LUCKY_2X;
            if (k.startsWith("троп") || k.startsWith("3") || k.startsWith("три") || k.startsWith("road")
                    || k.startsWith("tropy") || k.startsWith("path")) return THREE_ROADS;
            return null;
        }
    }
    private static final String METEOR_TAG = "BRAINROT_METEOR";
    private static final String PARTY_CREEPER_TAG = "BRAINROT_PARTY_CREEPER";
    private final Random random = new Random();

    /** Один запущенный ивент: свой босс-бар, свой мир, свой таймер и свои задачи. */
    private static final class EventSession {
        final EventType type;
        final World world;
        final BossBar bar;
        final int totalSeconds;
        final long endMillis;
        final List<BukkitTask> tasks = new ArrayList<>();

        EventSession(EventType type, World world, BossBar bar, int totalSeconds) {
            this.type = type;
            this.world = world;
            this.bar = bar;
            this.totalSeconds = totalSeconds;
            this.endMillis = System.currentTimeMillis() + totalSeconds * 1000L;
        }
    }

    /** Ивенты стакаются: одновременно может идти любой набор, но не по два одинаковых. */
    private final Map<EventType, EventSession> sessions = new EnumMap<>(EventType.class);
    private BukkitTask barTask;
    private BukkitTask autoTask;
    private final Set<Entity> activeMeteors = new HashSet<>();
    private boolean weatherRestore = false;

    // Крипер Пати
    private final List<Entity> partyCreepers = new ArrayList<>();
    private final List<Location> partyCreeperSpots = new ArrayList<>();
    private final Map<Entity, BukkitTask> creeperCharges = new HashMap<>();
    private final Set<UUID> knockbackGrace = new HashSet<>();
    private BukkitTask creeperJumpTask;
    private BukkitTask creeperRefillTask;

    // 3 Тропы: id временных дорожек, состояние стройки и «поколение» запуска.
    // Поколение нужно, чтобы отложенные шаги стройки (схема -> бетон -> дорожки)
    // не догоняли ивент, который уже успели остановить.
    private static final String ROADS_LANE1_ID = "event3road1";
    private static final String ROADS_LANE2_ID = "event3road2";
    private final List<String> roadsLaneIds = new ArrayList<>();
    private int roadsGeneration = 0;
    private boolean roadsBusy = false;
    private World roadsPendingTeardown;
    private BukkitTask roadsFloorTask;

    // Конфиг ивентов
    private String eventsWorldName = "";
    private boolean autoEnabled = false;
    private int autoIntervalMinutes = 20;
    private int autoMaxActive = 2;
    private boolean weatherEnabled = true;
    private int weatherDuration = 180;
    private int weatherStrikeInterval = 60;
    private double weatherStrikeChance = 0.18;
    private int weatherStrikesPerWave = 1;
    private boolean meteorEnabled = true;
    private int meteorDuration = 180;
    private int meteorWaveInterval = 25;
    private int meteorPerWave = 2;
    private double meteorSpawnHeight = 28;
    private double meteorHitRadius = 2.5;
    private boolean creeperEnabled = true;
    private int creeperDuration = 180;
    private int creeperLineY = 46;
    private int creeperMinX = -58;
    private int creeperMaxX = 21;
    private int creeperLine1Z = 71;
    private int creeperLine2Z = 59;
    private int creeperStep = 3;
    private int creeperJumpInterval = 40;
    private double creeperJumpPower = 0.42;
    private int creeperChargeInterval = 50;
    private double creeperChargeChance = 0.45;
    private double creeperPlayerChance = 0.15;
    private int creeperMaxCharges = 3;
    private double creeperWalkSpeed = 0.26;
    private int creeperChargeTimeout = 160;
    private double creeperHitRadius = 2.0;
    private double creeperPlayerKnockback = 1.35;
    private int creeperRespawnDelay = 60;
    // Ивент «3 Тропы»
    private boolean roadsEnabled = true;
    private int roadsDuration = 180;
    private boolean roadsIncludeInAuto = false;
    private String roadsSchemOpen = "3road";
    private String roadsSchemClose = "roadorig";
    private String roadsWallPos1 = "-60 51 76";
    private String roadsWallPos2 = "-60 47 54";
    private int roadsFloorY = 46;
    private int roadsFloorX1 = -59;
    private int roadsFloorX2 = 22;
    private int roadsStrip1Z1 = 55;
    private int roadsStrip1Z2 = 59;
    private int roadsStrip2Z1 = 71;
    private int roadsStrip2Z2 = 75;
    private String roadsFloorMaterial = "RED_CONCRETE";
    private String roadsRestoreMaterial = "GRASS_BLOCK";
    private int roadsBlocksPerTick = 400;
    private String roadsLane1Spawn = "-60 47 73";
    private String roadsLane1End = "23 47 73";
    private String roadsLane2Spawn = "-60 47 57";
    private String roadsLane2End = "23 47 57";
    private double roadsLaneSpeed = 0;
    private long roadsLaneCooldown = 0;

    // «2x Удача»
    private boolean luckEnabled = true;
    private int luckDuration = 300;
    private boolean luckIncludeInAuto = false;
    private double luckMultiplier = 2.0;

    // «Бабл Гам Машина»
    private boolean gumEnabled = true;
    private int gumDuration = 300;
    private boolean gumIncludeInAuto = false;
    private String gumSchematic = "gumbubble";
    private String gumPos1 = "-44 54 68";
    private String gumPos2 = "-40 47 62";
    private String gumTrigger = "-42 46 65";
    private double gumTopY = 53;
    private double gumChance = 10;
    private double gumHoldMinSeconds = 4.0;
    private double gumHoldMaxSeconds = 5.0;
    private double gumCooldownSeconds = 8.0;
    private boolean gumBusy = false;
    private static final String GUM_SNAPSHOT_FILE = "gum-snapshot.yml";

    // «НЛО»: пять тарелок кружат над спавном и лучом забирают мобов.
    private boolean ufoEnabled = true;
    private int ufoDuration = 300;
    private boolean ufoIncludeInAuto = false;
    private String ufoBlueprint = "ufo_abductor";
    private List<String> ufoSpawnPoints = new ArrayList<>();
    /**
     * На сколько блоков тарелка висит выше точки из spawn-points. Без этого запаса
     * тарелка оказывалась ровно на уровне конвейера: моб никогда не был «под лучом»,
     * поэтому захват не срабатывал вообще, а модель торчала в полу.
     */
    private double ufoHoverHeight = 3.0;
    private double ufoRoamRadius = 12.0;
    private double ufoSpeed = 0.09;
    private double ufoBeamRange = 5.0;
    private double ufoChance = 12.0;
    private int ufoTryInterval = 60;
    private double ufoRiseBlocks = 4.5;
    private double ufoHoldMinSeconds = 3.0;
    private double ufoHoldMaxSeconds = 5.0;
    private double ufoCooldownSeconds = 15.0;
    private int ufoMaxBeams = 2;
    private static final String UFO_TAG = "BRAINROT_UFO";
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
        // Если сервер упал с открытыми «3 Тропами» — вернуть схему и дёрн.
        restoreRoadsAfterCrash();
        // …и если он упал с поставленной бабл-гам машиной — откатить регион по снимку.
        restoreGumAfterCrash();
        // Тарелки — обычные сущности: если сервер упал с идущим ивентом, они остались
        // висеть в мире. Убираем по тегу.
        cleanupStrayUfos();
        getLogger().info("BrainrotEvents включён. Трек: " + soundKey
                + ", ивенты: Плохая Погода / Метеоритный Дождь / Крипер Пати / 3 Тропы / 2x Удача / Бабл Гам Машина / НЛО");
    }

    @Override
    public void onDisable() {
        try { stopAllEvents(true); } catch (Throwable ignored) {}
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
        // Сколько ивентов авто-планировщик может держать одновременно (ивенты стакаются).
        changed |= def("events.auto.max-active", 2);
        changed |= def("events.bad-weather.enabled", true);
        changed |= def("events.bad-weather.duration-seconds", 180);
        changed |= def("events.bad-weather.strike-interval-ticks", 60);
        changed |= def("events.bad-weather.strike-chance", 0.18);
        changed |= def("events.bad-weather.strikes-per-wave", 1);
        changed |= def("events.meteor-shower.enabled", true);
        changed |= def("events.meteor-shower.duration-seconds", 180);
        changed |= def("events.meteor-shower.wave-interval-ticks", 25);
        changed |= def("events.meteor-shower.fireballs-per-wave", 2);
        changed |= def("events.meteor-shower.spawn-height", 28);
        changed |= def("events.meteor-shower.hit-radius", 2.5);
        changed |= def("events.creeper-party.enabled", true);
        changed |= def("events.creeper-party.duration-seconds", 180);
        changed |= def("events.creeper-party.line-y", 46);
        changed |= def("events.creeper-party.min-x", -58);
        changed |= def("events.creeper-party.max-x", 21);
        changed |= def("events.creeper-party.line1-z", 71);
        changed |= def("events.creeper-party.line2-z", 59);
        changed |= def("events.creeper-party.step", 3);
        changed |= def("events.creeper-party.jump-interval-ticks", 40);
        changed |= def("events.creeper-party.jump-power", 0.42);
        changed |= def("events.creeper-party.charge-interval-ticks", 70);
        changed |= def("events.creeper-party.charge-chance", 0.22);
        changed |= def("events.creeper-party.player-target-chance", 0.15);
        changed |= def("events.creeper-party.max-active-charges", 2);
        changed |= def("events.creeper-party.walk-speed", 0.26);
        changed |= def("events.creeper-party.charge-timeout-ticks", 160);
        changed |= def("events.creeper-party.hit-radius", 2.0);
        changed |= def("events.creeper-party.player-knockback", 1.35);
        changed |= def("events.creeper-party.respawn-delay-ticks", 60);
        // «3 Тропы»: стена спавна меняется на схему с проходами, под тропами кладётся бетон.
        changed |= def("events.three-roads.enabled", true);
        changed |= def("events.three-roads.duration-seconds", 180);
        // Ивент стакается с остальными, но в авто-ротацию не идёт: запускаем руками.
        changed |= def("events.three-roads.include-in-auto", false);
        changed |= def("events.three-roads.schematic-open", "3road");
        changed |= def("events.three-roads.schematic-close", "roadorig");
        changed |= def("events.three-roads.wall.pos1", "-60 51 76");
        changed |= def("events.three-roads.wall.pos2", "-60 47 54");
        changed |= def("events.three-roads.floor.y", 46);
        changed |= def("events.three-roads.floor.x1", -59);
        changed |= def("events.three-roads.floor.x2", 22);
        changed |= def("events.three-roads.floor.strip1-z1", 55);
        changed |= def("events.three-roads.floor.strip1-z2", 59);
        changed |= def("events.three-roads.floor.strip2-z1", 71);
        changed |= def("events.three-roads.floor.strip2-z2", 75);
        changed |= def("events.three-roads.floor.material", "RED_CONCRETE");
        changed |= def("events.three-roads.floor.restore-material", "GRASS_BLOCK");
        changed |= def("events.three-roads.floor.blocks-per-tick", 400);
        changed |= def("events.three-roads.lane1.spawn", "-60 47 73");
        changed |= def("events.three-roads.lane1.end", "23 47 73");
        changed |= def("events.three-roads.lane2.spawn", "-60 47 57");
        changed |= def("events.three-roads.lane2.end", "23 47 57");
        // 0 = взять скорость и кулдаун у постоянной дорожки спавнера.
        changed |= def("events.three-roads.lane-speed", 0.0);
        changed |= def("events.three-roads.lane-cooldown-ticks", 0);
        // Ставится на время стройки: если сервер упадёт, при старте вернём дёрн и схему.
        changed |= def("events.three-roads.state.active", false);
        changed |= def("events.three-roads.state.world", "");
        // «2x Удача»: множитель шансов в спавнере на Легендарный и выше.
        changed |= def("events.lucky-2x.enabled", true);
        changed |= def("events.lucky-2x.duration-seconds", 300);
        changed |= def("events.lucky-2x.include-in-auto", false);
        changed |= def("events.lucky-2x.multiplier", 2.0);
        // «Бабл Гам Машина»: схема встаёт над конвейером, моб с шансом залипает в пузыре.
        changed |= def("events.gum-machine.enabled", true);
        changed |= def("events.gum-machine.duration-seconds", 300);
        changed |= def("events.gum-machine.include-in-auto", false);
        changed |= def("events.gum-machine.schematic", "gumbubble");
        changed |= def("events.gum-machine.region.pos1", "-44 54 68");
        changed |= def("events.gum-machine.region.pos2", "-40 47 62");
        changed |= def("events.gum-machine.trigger", "-42 46 65");
        changed |= def("events.gum-machine.top-y", 53);
        changed |= def("events.gum-machine.chance-percent", 10);
        changed |= def("events.gum-machine.hold-seconds-min", 4.0);
        changed |= def("events.gum-machine.hold-seconds-max", 5.0);
        // После посадки моба машина столько отдыхает — второй пузырь не начнётся.
        changed |= def("events.gum-machine.cooldown-seconds", 8.0);
        // Ставится, пока схема стоит в мире: снимок блоков лежит в gum-snapshot.yml,
        // и при падении сервера мы откатим регион по нему на следующем старте.
        changed |= def("events.gum-machine.state.active", false);
        changed |= def("events.gum-machine.state.world", "");
        // «НЛО»: пять тарелок висят над спавном, летают по кругу и лучом забирают мобов.
        changed |= def("events.ufo.enabled", true);
        changed |= def("events.ufo.duration-seconds", 300);
        changed |= def("events.ufo.include-in-auto", false);
        // Блюпринт ModelEngine (plugins/ModelEngine/blueprints/<id>.bbmodel).
        changed |= def("events.ufo.blueprint", "ufo_abductor");
        // Точки появления. Первые две — заданные вручную, остальные рядом.
        if (!cfg.isList("events.ufo.spawn-points")) {
            cfg.set("events.ufo.spawn-points", new ArrayList<>(List.of(
                    "-17 52 69", "-17 52 61", "-31 52 69", "-31 52 61", "-24 52 65")));
            changed = true;
        }
        // Высота, на которой тарелка висит над точкой из spawn-points.
        changed |= def("events.ufo.hover-height", 3.0);
        // Насколько далеко тарелка может уйти от своей точки. Наружу спавна не улетит.
        changed |= def("events.ufo.roam-radius", 12.0);
        changed |= def("events.ufo.speed", 0.09);
        // На какой горизонтальной дистанции тарелка «видит» моба под собой.
        changed |= def("events.ufo.beam-range", 5.0);
        changed |= def("events.ufo.chance-percent", 12.0);
        changed |= def("events.ufo.try-interval-ticks", 60);
        changed |= def("events.ufo.rise-blocks", 4.5);
        changed |= def("events.ufo.hold-seconds-min", 3.0);
        changed |= def("events.ufo.hold-seconds-max", 5.0);
        changed |= def("events.ufo.cooldown-seconds", 15.0);
        // Сколько мобов может висеть в лучах одновременно.
        changed |= def("events.ufo.max-beams", 2);
        // Одноразовая правка уже записанного конфига: в первой версии тарелки висели на
        // 6 блоках и «смотрели» на 5 — слишком высоко и узко, луч почти не находил мобов.
        // Трогаем только значения, которые никто не менял вручную.
        if (cfg.getInt("events.ufo.config-version", 1) < 2) {
            cfg.set("events.ufo.config-version", 2);
            if (Math.abs(cfg.getDouble("events.ufo.hover-height", 3.0) - 6.0) < 1e-6) {
                cfg.set("events.ufo.hover-height", 3.0);
            }
            if (Math.abs(cfg.getDouble("events.ufo.beam-range", 7.0) - 5.0) < 1e-6) {
                cfg.set("events.ufo.beam-range", 7.0);
            }
            changed = true;
        }
        changed |= def("events.region.worldguard-region", "spawn");
        changed |= def("events.region.padding", 8);
        changed |= def("events.region.min-x", 0);
        changed |= def("events.region.min-z", 0);
        changed |= def("events.region.max-x", 0);
        changed |= def("events.region.max-z", 0);

        // Миграция: у кого уже лежит конфиг с прежними (слишком частыми) молниями — переводим на новые числа.
        if (cfg.getInt("events.config-version", 1) < 2) {
            if (Math.abs(cfg.getDouble("events.bad-weather.strike-chance", 0.18) - 0.35) < 1.0E-6) {
                cfg.set("events.bad-weather.strike-chance", 0.18);
            }
            if (cfg.getInt("events.bad-weather.strike-interval-ticks", 60) == 40) {
                cfg.set("events.bad-weather.strike-interval-ticks", 60);
            }
            cfg.set("events.config-version", 2);
            changed = true;
        }
        // Миграция 3: криперы взрывали мобов слишком часто — реже кубик, ниже шанс, меньше одновременных забегов.
        if (cfg.getInt("events.config-version", 1) < 3) {
            if (Math.abs(cfg.getDouble("events.creeper-party.charge-chance", 0.22) - 0.45) < 1.0E-6) {
                cfg.set("events.creeper-party.charge-chance", 0.22);
            }
            if (cfg.getInt("events.creeper-party.charge-interval-ticks", 70) == 50) {
                cfg.set("events.creeper-party.charge-interval-ticks", 70);
            }
            if (cfg.getInt("events.creeper-party.max-active-charges", 2) == 3) {
                cfg.set("events.creeper-party.max-active-charges", 2);
            }
            cfg.set("events.config-version", 3);
            changed = true;
        }
        if (changed) saveConfig();

        eventsWorldName      = cfg.getString("events.world", "");
        autoEnabled          = cfg.getBoolean("events.auto.enabled", false);
        autoIntervalMinutes  = Math.max(1, cfg.getInt("events.auto.interval-minutes", 20));
        autoMaxActive        = Math.max(1, cfg.getInt("events.auto.max-active", 2));
        weatherEnabled       = cfg.getBoolean("events.bad-weather.enabled", true);
        weatherDuration      = Math.max(5, cfg.getInt("events.bad-weather.duration-seconds", 180));
        weatherStrikeInterval= Math.max(5, cfg.getInt("events.bad-weather.strike-interval-ticks", 60));
        weatherStrikeChance  = Math.min(1.0, Math.max(0.0, cfg.getDouble("events.bad-weather.strike-chance", 0.18)));
        weatherStrikesPerWave= Math.max(1, cfg.getInt("events.bad-weather.strikes-per-wave", 1));
        meteorEnabled        = cfg.getBoolean("events.meteor-shower.enabled", true);
        meteorDuration       = Math.max(5, cfg.getInt("events.meteor-shower.duration-seconds", 180));
        meteorWaveInterval   = Math.max(5, cfg.getInt("events.meteor-shower.wave-interval-ticks", 25));
        meteorPerWave        = Math.max(1, cfg.getInt("events.meteor-shower.fireballs-per-wave", 2));
        meteorSpawnHeight    = Math.max(5, cfg.getDouble("events.meteor-shower.spawn-height", 28));
        meteorHitRadius      = Math.max(0.5, cfg.getDouble("events.meteor-shower.hit-radius", 2.5));
        creeperEnabled       = cfg.getBoolean("events.creeper-party.enabled", true);
        creeperDuration      = Math.max(5, cfg.getInt("events.creeper-party.duration-seconds", 180));
        creeperLineY         = cfg.getInt("events.creeper-party.line-y", 46);
        creeperMinX          = cfg.getInt("events.creeper-party.min-x", -58);
        creeperMaxX          = cfg.getInt("events.creeper-party.max-x", 21);
        creeperLine1Z        = cfg.getInt("events.creeper-party.line1-z", 71);
        creeperLine2Z        = cfg.getInt("events.creeper-party.line2-z", 59);
        creeperStep          = Math.max(1, cfg.getInt("events.creeper-party.step", 3));
        creeperJumpInterval  = Math.max(5, cfg.getInt("events.creeper-party.jump-interval-ticks", 40));
        creeperJumpPower     = Math.max(0.05, cfg.getDouble("events.creeper-party.jump-power", 0.42));
        creeperChargeInterval= Math.max(5, cfg.getInt("events.creeper-party.charge-interval-ticks", 70));
        creeperChargeChance  = Math.min(1.0, Math.max(0.0, cfg.getDouble("events.creeper-party.charge-chance", 0.22)));
        creeperPlayerChance  = Math.min(1.0, Math.max(0.0, cfg.getDouble("events.creeper-party.player-target-chance", 0.15)));
        creeperMaxCharges    = Math.max(1, cfg.getInt("events.creeper-party.max-active-charges", 2));
        creeperWalkSpeed     = Math.max(0.05, cfg.getDouble("events.creeper-party.walk-speed", 0.26));
        creeperChargeTimeout = Math.max(20, cfg.getInt("events.creeper-party.charge-timeout-ticks", 160));
        creeperHitRadius     = Math.max(0.5, cfg.getDouble("events.creeper-party.hit-radius", 2.0));
        creeperPlayerKnockback = Math.max(0.1, cfg.getDouble("events.creeper-party.player-knockback", 1.35));
        creeperRespawnDelay  = Math.max(10, cfg.getInt("events.creeper-party.respawn-delay-ticks", 60));
        roadsEnabled         = cfg.getBoolean("events.three-roads.enabled", true);
        roadsDuration        = Math.max(5, cfg.getInt("events.three-roads.duration-seconds", 180));
        roadsIncludeInAuto   = cfg.getBoolean("events.three-roads.include-in-auto", false);
        roadsSchemOpen       = cfg.getString("events.three-roads.schematic-open", "3road");
        roadsSchemClose      = cfg.getString("events.three-roads.schematic-close", "roadorig");
        roadsWallPos1        = cfg.getString("events.three-roads.wall.pos1", "-60 51 76");
        roadsWallPos2        = cfg.getString("events.three-roads.wall.pos2", "-60 47 54");
        roadsFloorY          = cfg.getInt("events.three-roads.floor.y", 46);
        roadsFloorX1         = cfg.getInt("events.three-roads.floor.x1", -59);
        roadsFloorX2         = cfg.getInt("events.three-roads.floor.x2", 22);
        roadsStrip1Z1        = cfg.getInt("events.three-roads.floor.strip1-z1", 55);
        roadsStrip1Z2        = cfg.getInt("events.three-roads.floor.strip1-z2", 59);
        roadsStrip2Z1        = cfg.getInt("events.three-roads.floor.strip2-z1", 71);
        roadsStrip2Z2        = cfg.getInt("events.three-roads.floor.strip2-z2", 75);
        roadsFloorMaterial   = cfg.getString("events.three-roads.floor.material", "RED_CONCRETE");
        roadsRestoreMaterial = cfg.getString("events.three-roads.floor.restore-material", "GRASS_BLOCK");
        roadsBlocksPerTick   = Math.max(16, cfg.getInt("events.three-roads.floor.blocks-per-tick", 400));
        roadsLane1Spawn      = cfg.getString("events.three-roads.lane1.spawn", "-60 47 73");
        roadsLane1End        = cfg.getString("events.three-roads.lane1.end", "23 47 73");
        roadsLane2Spawn      = cfg.getString("events.three-roads.lane2.spawn", "-60 47 57");
        roadsLane2End        = cfg.getString("events.three-roads.lane2.end", "23 47 57");
        roadsLaneSpeed       = Math.max(0.0, cfg.getDouble("events.three-roads.lane-speed", 0.0));
        roadsLaneCooldown    = Math.max(0L, cfg.getLong("events.three-roads.lane-cooldown-ticks", 0L));
        luckEnabled          = cfg.getBoolean("events.lucky-2x.enabled", true);
        luckDuration         = Math.max(5, cfg.getInt("events.lucky-2x.duration-seconds", 300));
        luckIncludeInAuto    = cfg.getBoolean("events.lucky-2x.include-in-auto", false);
        luckMultiplier       = Math.max(1.0, Math.min(10.0, cfg.getDouble("events.lucky-2x.multiplier", 2.0)));
        gumEnabled           = cfg.getBoolean("events.gum-machine.enabled", true);
        gumDuration          = Math.max(5, cfg.getInt("events.gum-machine.duration-seconds", 300));
        gumIncludeInAuto     = cfg.getBoolean("events.gum-machine.include-in-auto", false);
        gumSchematic         = cfg.getString("events.gum-machine.schematic", "gumbubble");
        gumPos1              = cfg.getString("events.gum-machine.region.pos1", "-44 54 68");
        gumPos2              = cfg.getString("events.gum-machine.region.pos2", "-40 47 62");
        gumTrigger           = cfg.getString("events.gum-machine.trigger", "-42 46 65");
        gumTopY              = cfg.getDouble("events.gum-machine.top-y", 53);
        gumChance            = Math.max(0.0, Math.min(100.0, cfg.getDouble("events.gum-machine.chance-percent", 10)));
        gumHoldMinSeconds    = Math.max(0.5, cfg.getDouble("events.gum-machine.hold-seconds-min", 4.0));
        gumHoldMaxSeconds    = Math.max(gumHoldMinSeconds, cfg.getDouble("events.gum-machine.hold-seconds-max", 5.0));
        gumCooldownSeconds   = Math.max(0.0, cfg.getDouble("events.gum-machine.cooldown-seconds", 8.0));
        ufoEnabled           = cfg.getBoolean("events.ufo.enabled", true);
        ufoDuration          = Math.max(5, cfg.getInt("events.ufo.duration-seconds", 300));
        ufoIncludeInAuto     = cfg.getBoolean("events.ufo.include-in-auto", false);
        ufoBlueprint         = cfg.getString("events.ufo.blueprint", "ufo_abductor");
        ufoSpawnPoints       = new ArrayList<>(cfg.getStringList("events.ufo.spawn-points"));
        ufoHoverHeight       = Math.max(0.0, Math.min(24.0, cfg.getDouble("events.ufo.hover-height", 3.0)));
        if (ufoSpawnPoints.isEmpty()) ufoSpawnPoints.add("-17 52 69");
        ufoRoamRadius        = Math.max(2.0, cfg.getDouble("events.ufo.roam-radius", 12.0));
        ufoSpeed             = Math.max(0.02, Math.min(0.6, cfg.getDouble("events.ufo.speed", 0.09)));
        ufoBeamRange         = Math.max(1.0, cfg.getDouble("events.ufo.beam-range", 7.0));
        ufoChance            = Math.max(0.0, Math.min(100.0, cfg.getDouble("events.ufo.chance-percent", 12.0)));
        ufoTryInterval       = Math.max(10, cfg.getInt("events.ufo.try-interval-ticks", 60));
        ufoRiseBlocks        = Math.max(1.0, cfg.getDouble("events.ufo.rise-blocks", 4.5));
        ufoHoldMinSeconds    = Math.max(0.5, cfg.getDouble("events.ufo.hold-seconds-min", 3.0));
        ufoHoldMaxSeconds    = Math.max(ufoHoldMinSeconds, cfg.getDouble("events.ufo.hold-seconds-max", 5.0));
        ufoCooldownSeconds   = Math.max(0.0, cfg.getDouble("events.ufo.cooldown-seconds", 15.0));
        ufoMaxBeams          = Math.max(1, cfg.getInt("events.ufo.max-beams", 2));
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
        for (EventSession s : sessions.values()) {
            if (s.world != null && p.getWorld().equals(s.world)) s.bar.addPlayer(p);
        }
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
        for (EventSession s : sessions.values()) s.bar.removePlayer(e.getPlayer());
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
        if (type == EventType.CREEPER_PARTY && !creeperEnabled) {
            if (feedback != null) feedback.sendMessage("§cИвент «Крипер Пати» отключён в конфиге.");
            return false;
        }
        if (type == EventType.THREE_ROADS) {
            if (!roadsEnabled) {
                if (feedback != null) feedback.sendMessage("§cИвент «3 Тропы» отключён в конфиге.");
                return false;
            }
            // Схемы вставляются асинхронно, пол — порциями по тикам. Пока стройка или
            // разбор не закончились, второй запуск затоптал бы сам себя.
            if (roadsBusy) {
                if (feedback != null) feedback.sendMessage("§eКарта «3 Троп» ещё перестраивается, подожди пару секунд.");
                return false;
            }
        }
        if (type == EventType.LUCKY_2X && !luckEnabled) {
            if (feedback != null) feedback.sendMessage("§cИвент «2x Удача» отключён в конфиге.");
            return false;
        }
        if (type == EventType.GUM_MACHINE) {
            if (!gumEnabled) {
                if (feedback != null) feedback.sendMessage("§cИвент «Бабл Гам Машина» отключён в конфиге.");
                return false;
            }
            // Схема вставляется асинхронно, а откат идёт по снимку блоков. Второй
            // запуск в этот момент снял бы снимок уже с самой машины.
            if (gumBusy) {
                if (feedback != null) feedback.sendMessage("§eМашина ещё ставится или разбирается, подожди пару секунд.");
                return false;
            }
        }
        if (type == EventType.UFO && !ufoEnabled) {
            if (feedback != null) feedback.sendMessage("§cИвент «НЛО» отключён в конфиге.");
            return false;
        }
        if (isActive(type)) {
            if (type == EventType.THREE_ROADS) {
                // Карта уже перестроена. Разбирать её и строить заново — это две лишние
                // вставки схемы подряд, поэтому просто заменяем сессию: тропы и бетон
                // остаются на месте, у ивента обновляется только таймер и босс-бар.
                dropSessionKeepingMap(type);
            } else if (type == EventType.GUM_MACHINE) {
                // Машина уже стоит и снимок блоков снят — второй разбор/вставка только
                // испортили бы снимок. Продлеваем ивент, мир не трогаем.
                dropSessionKeepingMap(type);
            } else {
                // Тот же ивент запускают повторно — перезапускаем именно его, остальные не трогаем.
                stopEvent(type, true);
            }
        }

        World world = resolveWorld(feedback);
        if (world == null) {
            if (feedback != null) feedback.sendMessage("§cНе найден мир для ивента (events.world в конфиге).");
            return false;
        }
        if (seconds <= 0) {
            seconds = switch (type) {
                case BAD_WEATHER -> weatherDuration;
                case METEOR_SHOWER -> meteorDuration;
                case CREEPER_PARTY -> creeperDuration;
                case THREE_ROADS -> roadsDuration;
                case LUCKY_2X -> luckDuration;
                case GUM_MACHINE -> gumDuration;
                case UFO -> ufoDuration;
            };
        }

        BossBar bar = Bukkit.createBossBar(type.format + type.title, type.color, BarStyle.SEGMENTED_10);
        bar.setProgress(1.0);
        bar.setVisible(true);
        EventSession session = new EventSession(type, world, bar, seconds);
        sessions.put(type, session);
        syncBarPlayers(session);

        for (Player p : world.getPlayers()) {
            p.sendMessage(type.format + "✦ Начался ивент: " + type.title + "!");
            try { p.sendTitle(type.format + type.title, "§7" + seconds + " секунд", 10, 50, 20); }
            catch (Throwable ignored) {}
            p.playSound(p.getLocation(), switch (type) {
                case BAD_WEATHER -> Sound.ENTITY_LIGHTNING_BOLT_THUNDER;
                case METEOR_SHOWER -> Sound.ENTITY_GENERIC_EXPLODE;
                case CREEPER_PARTY -> Sound.ENTITY_CREEPER_PRIMED;
                case THREE_ROADS -> Sound.ENTITY_PLAYER_LEVELUP;
                case LUCKY_2X -> Sound.ENTITY_PLAYER_LEVELUP;
                case GUM_MACHINE -> Sound.ENTITY_SLIME_SQUISH;
                case UFO -> Sound.BLOCK_BEACON_ACTIVATE;
            }, 0.7f, 0.8f);
        }

        ensureBarTask();

        switch (type) {
            case BAD_WEATHER -> startBadWeather(session, seconds);
            case METEOR_SHOWER -> startMeteorShower(session);
            case CREEPER_PARTY -> startCreeperParty(session);
            case THREE_ROADS -> {
                // Дорожки уже стоят — значит карта открыта и ивент просто продлили.
                if (roadsLaneIds.isEmpty()) startThreeRoads(session);
                else getLogger().info("3 Тропы: карта уже открыта, продлеваю ивент до " + seconds + "с.");
            }
            case LUCKY_2X -> startLucky2x(session);
            case GUM_MACHINE -> {
                // Машина уже в мире (ивент просто продлили) — второй раз не ставим.
                if (!isGumMachineUp()) startGumMachine(session);
                else getLogger().info("Бабл Гам Машина: уже стоит, продлеваю ивент до " + seconds + "с.");
            }
            case UFO -> startUfoEvent(session);
        }

        getLogger().info("Ивент " + type.title + " запущен на " + seconds + "с в мире " + world.getName()
                + " (всего активных: " + sessions.size() + ")");
        return true;
    }

    private boolean isActive(EventType type) {
        return sessions.containsKey(type);
    }

    /**
     * Снимает сессию ивента, но НЕ разбирает то, что он построил в мире.
     * Нужно только «3 Тропам» при повторном запуске: карта уже открыта.
     */
    private void dropSessionKeepingMap(EventType type) {
        EventSession s = sessions.remove(type);
        if (s == null) return;
        for (BukkitTask t : s.tasks) {
            try { if (t != null) t.cancel(); } catch (Throwable ignored) {}
        }
        s.tasks.clear();
        try { s.bar.removeAll(); s.bar.setVisible(false); } catch (Throwable ignored) {}
    }

    /** Мир любого идущего ивента — для статуса и подсказок. */
    private World anyEventWorld() {
        for (EventSession s : sessions.values()) return s.world;
        return null;
    }

    /** Строка вида «Плохая Погода, Крипер Пати» для статуса. */
    private String activeEventsLine() {
        StringBuilder sb = new StringBuilder();
        for (EventSession s : sessions.values()) {
            if (sb.length() > 0) sb.append("§7, §f");
            sb.append(s.type.title);
        }
        return sb.length() == 0 ? "нет" : sb.toString();
    }

    private void ensureBarTask() {
        if (barTask != null) return;
        barTask = new BukkitRunnable() {
            @Override public void run() { tickBars(); }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void tickBars() {
        if (sessions.isEmpty()) {
            if (barTask != null) { try { barTask.cancel(); } catch (Throwable ignored) {} barTask = null; }
            return;
        }
        List<EventType> expired = new ArrayList<>();
        for (EventSession s : new ArrayList<>(sessions.values())) {
            long left = s.endMillis - System.currentTimeMillis();
            if (left <= 0) { expired.add(s.type); continue; }
            int secondsLeft = (int) Math.ceil(left / 1000.0);
            double progress = Math.max(0.0, Math.min(1.0, (double) secondsLeft / (double) s.totalSeconds));
            s.bar.setProgress(progress);
            s.bar.setTitle(s.type.format + s.type.title + " §7— §f" + formatTime(secondsLeft));
            syncBarPlayers(s);
        }
        for (EventType t : expired) stopEvent(t, false);
    }

    private void syncBarPlayers(EventSession s) {
        if (s == null || s.bar == null || s.world == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(s.world)) s.bar.addPlayer(p);
            else s.bar.removePlayer(p);
        }
    }

    private static String formatTime(int seconds) {
        int m = seconds / 60, s = seconds % 60;
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    /** Остановить все идущие ивенты. */
    private void stopAllEvents(boolean silent) {
        for (EventType t : new ArrayList<>(sessions.keySet())) stopEvent(t, silent);
    }

    private void stopEvent(EventType type, boolean silent) {
        EventSession s = sessions.remove(type);
        if (s == null) return;
        for (BukkitTask t : s.tasks) {
            try { if (t != null) t.cancel(); } catch (Throwable ignored) {}
        }
        s.tasks.clear();
        try { s.bar.removeAll(); s.bar.setVisible(false); } catch (Throwable ignored) {}

        switch (type) {
            case BAD_WEATHER -> {
                if (weatherRestore && s.world != null) {
                    try {
                        s.world.setThundering(false);
                        s.world.setStorm(false);
                        s.world.setWeatherDuration(0);
                        s.world.setClearWeatherDuration(20 * 60 * 10);
                    } catch (Throwable ignored) {}
                }
                weatherRestore = false;
            }
            case METEOR_SHOWER -> {
                for (Entity meteor : new ArrayList<>(activeMeteors)) {
                    try { if (meteor.isValid()) meteor.remove(); } catch (Throwable ignored) {}
                }
                activeMeteors.clear();
            }
            case CREEPER_PARTY -> clearCreeperParty();
            case THREE_ROADS -> teardownThreeRoads(s.world);
            case LUCKY_2X -> stopLucky2x();
            case GUM_MACHINE -> teardownGumMachine(s.world);
            case UFO -> teardownUfoEvent();
        }

        if (sessions.isEmpty() && barTask != null) {
            try { barTask.cancel(); } catch (Throwable ignored) {}
            barTask = null;
        }
        if (!silent && s.world != null) {
            for (Player p : s.world.getPlayers()) {
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
                if (sessions.size() >= autoMaxActive) return;
                World w = resolveWorld();
                if (w == null || w.getPlayers().isEmpty()) return;
                List<EventType> pool = new ArrayList<>();
                if (weatherEnabled && !isActive(EventType.BAD_WEATHER)) pool.add(EventType.BAD_WEATHER);
                if (meteorEnabled && !isActive(EventType.METEOR_SHOWER)) pool.add(EventType.METEOR_SHOWER);
                if (creeperEnabled && !isActive(EventType.CREEPER_PARTY)) pool.add(EventType.CREEPER_PARTY);
                // «3 Тропы» по умолчанию вне ротации: ивент перестраивает карту,
                // поэтому запускается руками. Включается events.three-roads.include-in-auto.
                if (roadsEnabled && roadsIncludeInAuto && !roadsBusy && !isActive(EventType.THREE_ROADS)) {
                    pool.add(EventType.THREE_ROADS);
                }
                // «2x Удача» тоже вне ротации по умолчанию — это подарок, а не погода.
                // Включается events.lucky-2x.include-in-auto.
                if (luckEnabled && luckIncludeInAuto && !isActive(EventType.LUCKY_2X)) {
                    pool.add(EventType.LUCKY_2X);
                }
                // Машина тоже вне ротации: она меняет карту, как и тропы.
                // Включается events.gum-machine.include-in-auto.
                if (gumEnabled && gumIncludeInAuto && !gumBusy && !isActive(EventType.GUM_MACHINE)) {
                    pool.add(EventType.GUM_MACHINE);
                }
                // НЛО тоже руками: тарелки видны только с ModelEngine, и лучше
                // включать их осознанно. events.ufo.include-in-auto.
                if (ufoEnabled && ufoIncludeInAuto && !isActive(EventType.UFO)) {
                    pool.add(EventType.UFO);
                }
                if (pool.isEmpty()) return;
                startEvent(pool.get(random.nextInt(pool.size())), 0, null);
            }
        }.runTaskTimer(this, period, period);
    }
    // =========================================================
    // ИВЕНТ 1: ПЛОХАЯ ПОГОДА (молния -> мутация Электрический)
    // =========================================================
    private void startBadWeather(EventSession session, int seconds) {
        final World world = session.world;
        try {
            world.setStorm(true);
            world.setThundering(true);
            world.setWeatherDuration(seconds * 20 + 200);
            world.setThunderDuration(seconds * 20 + 200);
            weatherRestore = true;
        } catch (Throwable ignored) {}

        session.tasks.add(new BukkitRunnable() {
            @Override public void run() {
                if (!isActive(EventType.BAD_WEATHER)) { cancel(); return; }
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
        }.runTaskTimer(this, weatherStrikeInterval, weatherStrikeInterval));
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
    private void startMeteorShower(EventSession session) {
        final World world = session.world;
        session.tasks.add(new BukkitRunnable() {
            @Override public void run() {
                if (!isActive(EventType.METEOR_SHOWER)) { cancel(); return; }
                double[] box = getRegionBounds(world);
                if (box == null) return;
                for (int i = 0; i < meteorPerWave; i++) spawnMeteor(world, box);
            }
        }.runTaskTimer(this, 20L, meteorWaveInterval));

        session.tasks.add(new BukkitRunnable() {
            @Override public void run() {
                if (!isActive(EventType.METEOR_SHOWER)) { cancel(); return; }
                tickMeteors();
            }
        }.runTaskTimer(this, 1L, 1L));
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
    // ИВЕНТ 3: КРИПЕР ПАТИ (крипер добегает и взрывает -> мутация Взрывной)
    // =========================================================
    private void startCreeperParty(EventSession session) {
        final World world = session.world;
        partyCreeperSpots.clear();
        int minX = Math.min(creeperMinX, creeperMaxX);
        int maxX = Math.max(creeperMinX, creeperMaxX);
        for (int x = minX; x <= maxX; x += creeperStep) {
            partyCreeperSpots.add(new Location(world, x + 0.5, creeperLineY, creeperLine1Z + 0.5));
            partyCreeperSpots.add(new Location(world, x + 0.5, creeperLineY, creeperLine2Z + 0.5));
        }
        for (Location spot : partyCreeperSpots) spawnPartyCreeper(spot);
        getLogger().info("Крипер Пати: выставлено криперов — " + partyCreepers.size()
                + " (шаг " + creeperStep + ")");

        // Прыжки: все криперы синхронно подпрыгивают.
        creeperJumpTask = new BukkitRunnable() {
            @Override public void run() {
                if (!isActive(EventType.CREEPER_PARTY)) { cancel(); return; }
                for (Entity c : new ArrayList<>(partyCreepers)) {
                    if (c == null || !c.isValid()) continue;
                    if (creeperCharges.containsKey(c)) continue; // бегущий не прыгает
                    try {
                        c.setVelocity(new Vector(0, creeperJumpPower, 0));
                        c.getWorld().playSound(c.getLocation(), Sound.ENTITY_CREEPER_HURT, 0.25f, 1.8f);
                        c.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, c.getLocation().add(0, 0.6, 0),
                                2, 0.2, 0.2, 0.2, 0.0);
                    } catch (Throwable ignored) {}
                }
            }
        }.runTaskTimer(this, creeperJumpInterval, creeperJumpInterval);
        session.tasks.add(creeperJumpTask);

        // Выбор жертвы.
        session.tasks.add(new BukkitRunnable() {
            @Override public void run() {
                if (!isActive(EventType.CREEPER_PARTY)) { cancel(); return; }
                if (creeperCharges.size() >= creeperMaxCharges) return;
                if (random.nextDouble() > creeperChargeChance) return;
                launchCreeperCharge(world);
            }
        }.runTaskTimer(this, 40L, creeperChargeInterval));

        // Добор пропавших криперов.
        creeperRefillTask = new BukkitRunnable() {
            @Override public void run() {
                if (!isActive(EventType.CREEPER_PARTY)) { cancel(); return; }
                partyCreepers.removeIf(c -> c == null || !c.isValid());
                if (partyCreepers.size() >= partyCreeperSpots.size()) return;
                for (Location spot : partyCreeperSpots) {
                    if (partyCreepers.size() >= partyCreeperSpots.size()) break;
                    boolean taken = false;
                    for (Entity c : partyCreepers) {
                        if (c.isValid() && c.getLocation().distanceSquared(spot) <= 4.0) { taken = true; break; }
                    }
                    if (!taken) spawnPartyCreeper(spot);
                }
            }
        }.runTaskTimer(this, creeperRespawnDelay, creeperRespawnDelay);
        session.tasks.add(creeperRefillTask);
    }

    private void spawnPartyCreeper(Location spot) {
        if (spot == null || spot.getWorld() == null) return;
        try {
            Creeper c = spot.getWorld().spawn(spot, Creeper.class, cr -> {
                cr.addScoreboardTag(PARTY_CREEPER_TAG);
                // ИИ оставляем включённым: с NoAI сервер не считает физику, и прыжки бы не работали.
                // Вместо этого вешаем бесконечную слабость к скорости, чтобы криперы стояли в линии.
                cr.setAI(true);
                try {
                    cr.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                            PotionEffect.INFINITE_DURATION, 250, false, false, false));
                } catch (Throwable ignored) {}
                cr.setInvulnerable(true);
                cr.setSilent(true);
                cr.setCollidable(false);
                cr.setRemoveWhenFarAway(false);
                cr.setPersistent(true);
                cr.setCustomName("§a§lКрипер Пати");
                cr.setCustomNameVisible(false);
                try { cr.setTarget(null); } catch (Throwable ignored) {}
                try { cr.setExplosionRadius(0); } catch (Throwable ignored) {}
                try { cr.setMaxFuseTicks(200); } catch (Throwable ignored) {}
            });
            partyCreepers.add(c);
        } catch (Throwable t) {
            getLogger().warning("Не удалось поставить крипера: " + t.getMessage());
        }
    }

    /** Выбирает свободного крипера и цель: чаще моб на конвейере, реже — ближайший игрок. */
    private void launchCreeperCharge(World world) {
        List<Entity> idle = new ArrayList<>();
        for (Entity c : partyCreepers) {
            if (c != null && c.isValid() && !creeperCharges.containsKey(c)) idle.add(c);
        }
        if (idle.isEmpty()) return;

        boolean goPlayer = random.nextDouble() < creeperPlayerChance;
        Entity target = null;
        Entity creeper = null;

        if (!goPlayer) {
            List<Entity> mobs = new ArrayList<>();
            for (Entity m : getSpawnerMobs()) {
                if (m != null && m.isValid() && m.getWorld().equals(world)) mobs.add(m);
            }
            if (!mobs.isEmpty()) target = mobs.get(random.nextInt(mobs.size()));
        }
        if (target == null) {
            // Либо выпал дебафф, либо мобов на конвейере нет — идём к игроку.
            goPlayer = true;
            Player best = null;
            double bestDist = Double.MAX_VALUE;
            for (Player p : world.getPlayers()) {
                if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                for (Entity c : idle) {
                    double d = c.getLocation().distanceSquared(p.getLocation());
                    if (d < bestDist) { bestDist = d; best = p; creeper = c; }
                }
            }
            if (best == null) return;
            target = best;
        }
        if (creeper == null) {
            // Ближайший к цели свободный крипер.
            double bestDist = Double.MAX_VALUE;
            for (Entity c : idle) {
                double d = c.getLocation().distanceSquared(target.getLocation());
                if (d < bestDist) { bestDist = d; creeper = c; }
            }
        }
        if (creeper == null) return;
        startCreeperCharge(creeper, target, goPlayer);
    }

    private void startCreeperCharge(Entity creeper, Entity target, boolean playerTarget) {
        try {
            creeper.setVelocity(new Vector(0, 0, 0));
            if (creeper instanceof Creeper cr) cr.setIgnited(false);
            creeper.getWorld().playSound(creeper.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 0.8f, 1.2f);
        } catch (Throwable ignored) {}

        final Entity finalTarget = target;
        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!isActive(EventType.CREEPER_PARTY) || !creeper.isValid()) { finish(); return; }
                if (finalTarget == null || !finalTarget.isValid() || ++ticks > creeperChargeTimeout) {
                    fizzleCreeper(creeper);
                    finish();
                    return;
                }
                Location cur = creeper.getLocation();
                Location tl = finalTarget.getLocation();
                if (!cur.getWorld().equals(tl.getWorld())) { fizzleCreeper(creeper); finish(); return; }
                if (cur.distanceSquared(tl) <= creeperHitRadius * creeperHitRadius) {
                    detonateCreeper(creeper, finalTarget, playerTarget);
                    finish();
                    return;
                }
                Vector dir = tl.toVector().subtract(cur.toVector());
                dir.setY(0);
                if (dir.lengthSquared() < 1.0E-4) dir = new Vector(0.1, 0, 0);
                dir.normalize().multiply(creeperWalkSpeed);
                Location next = cur.clone().add(dir);
                next.setY(groundYFor(next, cur.getY(), tl.getY()));
                next.setDirection(tl.toVector().subtract(next.toVector()));
                try {
                    creeper.teleport(next);
                    if (ticks % 4 == 0) {
                        creeper.getWorld().spawnParticle(Particle.CRIT, next.clone().add(0, 0.3, 0), 1, 0.1, 0.1, 0.1, 0.0);
                    }
                    if (ticks % 10 == 0) {
                        creeper.getWorld().playSound(next, Sound.ENTITY_CREEPER_PRIMED, 0.35f, 1.6f);
                    }
                } catch (Throwable ignored) {}
            }

            private void finish() {
                creeperCharges.remove(creeper);
                try { cancel(); } catch (Throwable ignored) {}
            }
        }.runTaskTimer(this, 1L, 1L);
        creeperCharges.put(creeper, task);
    }

    /** Держим крипера на земле: без AI гравитация при телепортах не помогает. */
    private double groundYFor(Location next, double currentY, double targetY) {
        World w = next.getWorld();
        int bx = next.getBlockX(), bz = next.getBlockZ();
        int from = (int) Math.floor(Math.max(currentY, targetY)) + 2;
        int to = (int) Math.floor(Math.min(currentY, targetY)) - 3;
        for (int y = from; y >= to; y--) {
            try {
                if (!w.getBlockAt(bx, y, bz).isPassable()
                        && w.getBlockAt(bx, y + 1, bz).isPassable()
                        && w.getBlockAt(bx, y + 2, bz).isPassable()) {
                    return y + 1;
                }
            } catch (Throwable ignored) {}
        }
        return currentY;
    }

    private void fizzleCreeper(Entity creeper) {
        try {
            creeper.getWorld().spawnParticle(Particle.SMOKE, creeper.getLocation().add(0, 0.5, 0), 10, 0.3, 0.3, 0.3, 0.02);
        } catch (Throwable ignored) {}
        returnCreeperToLine(creeper);
    }

    /** Вернуть крипера на свободное место в линии, чтобы шеренга не редела. */
    private void returnCreeperToLine(Entity creeper) {
        Location home = null;
        for (Location spot : partyCreeperSpots) {
            boolean taken = false;
            for (Entity c : partyCreepers) {
                if (c != creeper && c.isValid() && c.getLocation().distanceSquared(spot) <= 4.0) { taken = true; break; }
            }
            if (!taken) { home = spot; break; }
        }
        if (home == null) return;
        try { creeper.teleport(home); } catch (Throwable ignored) {}
    }

    private void detonateCreeper(Entity creeper, Entity target, boolean playerTarget) {
        Location loc = creeper.getLocation();
        try {
            loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0.0);
            loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 0.5, 0), 25, 0.6, 0.5, 0.6, 0.05);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.1f);
        } catch (Throwable ignored) {}
        try { creeper.remove(); } catch (Throwable ignored) {}
        partyCreepers.remove(creeper);

        if (playerTarget && target instanceof Player p) {
            Vector push = p.getLocation().toVector().subtract(loc.toVector());
            push.setY(0);
            if (push.lengthSquared() < 1.0E-4) push = new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
            push.normalize().multiply(creeperPlayerKnockback).setY(0.85);
            knockbackGrace.add(p.getUniqueId());
            final UUID id = p.getUniqueId();
            Bukkit.getScheduler().runTaskLater(this, () -> knockbackGrace.remove(id), 200L);
            try {
                p.setVelocity(push);
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.3f);
                p.sendMessage("§a☠ Крипер подкрался к тебе и рванул — держись!");
            } catch (Throwable ignored) {}
        } else if (target != null && applyStackableMutation(target, "EXPLOSIVE")) {
            for (Player p : loc.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(loc) <= 60 * 60) {
                    p.sendMessage("§a✦ Крипер взорвал моба — мутация §aВзрывной§a!");
                }
            }
        }
    }

    private void clearCreeperParty() {
        if (creeperJumpTask != null) { try { creeperJumpTask.cancel(); } catch (Throwable ignored) {} creeperJumpTask = null; }
        if (creeperRefillTask != null) { try { creeperRefillTask.cancel(); } catch (Throwable ignored) {} creeperRefillTask = null; }
        for (BukkitTask t : new ArrayList<>(creeperCharges.values())) {
            try { t.cancel(); } catch (Throwable ignored) {}
        }
        creeperCharges.clear();
        for (Entity c : new ArrayList<>(partyCreepers)) {
            try { if (c != null && c.isValid()) c.remove(); } catch (Throwable ignored) {}
        }
        partyCreepers.clear();
        partyCreeperSpots.clear();
        knockbackGrace.clear();
    }

    private boolean isPartyCreeper(Entity e) {
        return e != null && e.getScoreboardTags().contains(PARTY_CREEPER_TAG);
    }

    // =========================================================
    // ИВЕНТ 4: 3 ТРОПЫ (схема с проходами + бетон + две временные дорожки)
    // =========================================================
    /**
     * Порядок стройки: сначала стена спавна меняется на схему с тремя проходами,
     * потом под двумя новыми тропами кладётся бетон, и только после этого
     * поднимаются дорожки конвейера — иначе моб успел бы выехать в закрытую стену.
     * Разбор идёт в обратном порядке: дорожки снимаются мгновенно, затем
     * возвращается исходная схема и дёрн.
     */
    private void startThreeRoads(EventSession session) {
        final World world = session.world;
        final int gen = ++roadsGeneration;
        roadsBusy = true;
        markRoadsState(true, world.getName());
        getLogger().info("3 Тропы: открываю проходы (схема " + roadsSchemOpen + ").");
        pasteRoadsSchematic(world, roadsSchemOpen, () -> {
            if (roadsStale(gen)) { roadsBusy = false; runPendingRoadsTeardown(); return; }
            fillRoadsFloor(world, roadsFloorMaterial, () -> {
                if (roadsStale(gen)) { roadsBusy = false; runPendingRoadsTeardown(); return; }
                int lanes = openRoadsLanes(world);
                roadsBusy = false;
                if (roadsPendingTeardown != null) { runPendingRoadsTeardown(); return; }
                for (Player p : world.getPlayers()) {
                    p.sendMessage("§6§l✦ Открылись 3 тропы! §7Мобы теперь едут по трём дорожкам.");
                    try { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f); }
                    catch (Throwable ignored) {}
                }
                getLogger().info("3 Тропы: стройка закончена, временных дорожек — " + lanes + ".");
            });
        });
    }

    /** Стройку обогнали: ивент уже сняли или перезапустили. */
    private boolean roadsStale(int gen) {
        return gen != roadsGeneration || roadsPendingTeardown != null || !isActive(EventType.THREE_ROADS);
    }

    private void teardownThreeRoads(World world) {
        closeRoadsLanes();
        if (roadsBusy) {
            // Стройка ещё в воздухе. Отменить асинхронную вставку схемы нельзя,
            // поэтому разбор запомним и выполним сразу, как она закончится.
            roadsPendingTeardown = (world != null ? world : anyEventWorld());
            getLogger().info("3 Тропы: разбор отложен до конца текущей перестройки.");
            return;
        }
        restoreRoadsMap(world);
    }

    private void runPendingRoadsTeardown() {
        if (roadsPendingTeardown == null || roadsBusy) return;
        World w = roadsPendingTeardown;
        roadsPendingTeardown = null;
        restoreRoadsMap(w);
    }

    /** Возвращает исходную схему стены и дёрн под тропами. */
    private void restoreRoadsMap(World world) {
        if (world == null) {
            getLogger().warning("3 Тропы: мир не найден, карту вернуть не могу. Флаг снимаю, проверь спавн вручную.");
            markRoadsState(false, "");
            return;
        }
        final int gen = ++roadsGeneration;
        roadsBusy = true;
        getLogger().info("3 Тропы: возвращаю схему " + roadsSchemClose + " и " + roadsRestoreMaterial + ".");
        pasteRoadsSchematic(world, roadsSchemClose, () -> fillRoadsFloor(world, roadsRestoreMaterial, () -> {
            roadsBusy = false;
            if (gen == roadsGeneration) markRoadsState(false, "");
            getLogger().info("3 Тропы: карта возвращена в исходное состояние.");
            runPendingRoadsTeardown();
        }));
    }

    /**
     * Флаг «карта сейчас перестроена». Пишется на диск сразу: если сервер упадёт
     * с открытыми тропами, при следующем старте мы увидим флаг и вернём всё назад,
     * иначе бетон и дырявая стена остались бы навсегда.
     */
    private void markRoadsState(boolean active, String worldName) {
        try {
            getConfig().set("events.three-roads.state.active", active);
            getConfig().set("events.three-roads.state.world", worldName == null ? "" : worldName);
            saveConfig();
        } catch (Throwable t) {
            getLogger().warning("3 Тропы: не удалось сохранить состояние: " + t.getMessage());
        }
    }

    /** Вызывается на onEnable: если прошлый запуск не закрылся — чиним карту. */
    private void restoreRoadsAfterCrash() {
        if (!getConfig().getBoolean("events.three-roads.state.active", false)) return;
        String wn = getConfig().getString("events.three-roads.state.world", "");
        getLogger().warning("3 Тропы: прошлый ивент не был закрыт (перезапуск сервера?) — возвращаю карту.");
        // Ждём, пока прогрузятся мир и WorldEdit.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            World w = (wn == null || wn.isEmpty()) ? null : Bukkit.getWorld(wn);
            if (w == null) w = resolveWorld();
            restoreRoadsMap(w);
        }, 100L);
    }

    // ---------- геометрия и блоки ----------

    /** Точка вида «-60 47 73» или «-60,47,73». */
    private Location parseRoadsPoint(World world, String raw) {
        if (world == null || raw == null) return null;
        String[] parts = raw.trim().replace(',', ' ').split("\\s+");
        if (parts.length < 3) return null;
        try {
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            // Центр блока: мобы должны ехать по середине тропы, а не по её краю.
            return new Location(world, Math.floor(x) + 0.5, y, Math.floor(z) + 0.5);
        } catch (NumberFormatException ex) {
            getLogger().warning("3 Тропы: не разобрал координату '" + raw + "'.");
            return null;
        }
    }

    private Material roadsMaterial(String name, Material fallback) {
        if (name != null && !name.trim().isEmpty()) {
            try {
                Material m = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
                if (m != null && m.isBlock()) return m;
            } catch (Throwable ignored) {}
            getLogger().warning("3 Тропы: блок '" + name + "' не найден, беру " + fallback + ".");
        }
        return fallback;
    }

    /** Обе полосы пола одним списком координат X/Z (высота одна — roadsFloorY). */
    private List<int[]> roadsFloorBlocks() {
        List<int[]> out = new ArrayList<>();
        int x1 = Math.min(roadsFloorX1, roadsFloorX2);
        int x2 = Math.max(roadsFloorX1, roadsFloorX2);
        addFloorStrip(out, x1, x2, roadsStrip1Z1, roadsStrip1Z2);
        addFloorStrip(out, x1, x2, roadsStrip2Z1, roadsStrip2Z2);
        return out;
    }

    private void addFloorStrip(List<int[]> out, int x1, int x2, int za, int zb) {
        int z1 = Math.min(za, zb);
        int z2 = Math.max(za, zb);
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) out.add(new int[]{x, z});
        }
    }

    /** Кладёт пол порциями по тикам, чтобы не собрать лаг-спайк на ~800 блоков. */
    private void fillRoadsFloor(World world, String materialName, Runnable afterMain) {
        if (roadsFloorTask != null) {
            try { roadsFloorTask.cancel(); } catch (Throwable ignored) {}
            roadsFloorTask = null;
        }
        if (world == null) {
            if (afterMain != null) afterMain.run();
            return;
        }
        final Material mat = roadsMaterial(materialName, Material.GRASS_BLOCK);
        final List<int[]> blocks = roadsFloorBlocks();
        final int[] idx = {0};
        try {
            roadsFloorTask = new BukkitRunnable() {
                @Override public void run() {
                    int done = 0;
                    while (idx[0] < blocks.size() && done < roadsBlocksPerTick) {
                        int[] b = blocks.get(idx[0]++);
                        done++;
                        try {
                            Block block = world.getBlockAt(b[0], roadsFloorY, b[1]);
                            if (block.getType() != mat) block.setType(mat, false);
                        } catch (Throwable ignored) {}
                    }
                    if (idx[0] >= blocks.size()) {
                        try { cancel(); } catch (Throwable ignored) {}
                        roadsFloorTask = null;
                        getLogger().info("3 Тропы: пол выложен (" + blocks.size() + " блоков " + mat + ").");
                        if (afterMain != null) afterMain.run();
                    }
                }
            }.runTaskTimer(this, 1L, 1L);
        } catch (Throwable t) {
            getLogger().warning("3 Тропы: не смог запустить укладку пола: " + t.getMessage());
            roadsFloorTask = null;
        }
    }

    /**
     * Вставка схемы стены. Повторяет проверенный путь из BrainrotBases:
     * origin сбрасывается в минимальный угол, чтобы вставка легла ровно по pos1/pos2,
     * а ignoreAirBlocks(false) нужен, чтобы проходы в схеме действительно
     * вырезали блоки, а не «просвечивали» старую стену.
     */
    private void pasteRoadsSchematic(World world, String schemName, Runnable afterMain) {
        if (world == null || schemName == null || schemName.trim().isEmpty()) {
            if (afterMain != null) afterMain.run();
            return;
        }
        Location l1 = parseRoadsPoint(world, roadsWallPos1);
        Location l2 = parseRoadsPoint(world, roadsWallPos2);
        if (l1 == null || l2 == null) {
            getLogger().warning("3 Тропы: не заданы координаты стены (events.three-roads.wall.pos1/pos2).");
            if (afterMain != null) afterMain.run();
            return;
        }
        final int minX = Math.min(l1.getBlockX(), l2.getBlockX());
        final int minY = Math.min(l1.getBlockY(), l2.getBlockY());
        final int minZ = Math.min(l1.getBlockZ(), l2.getBlockZ());
        final File file = resolveSchematicFile(schemName);
        if (file == null || !file.exists()) {
            getLogger().severe("3 Тропы: схема '" + schemName + "' не найдена. Положи её в plugins/BrainrotEvents/schematics/"
                    + " или в папку схем WorldEdit/FAWE.");
            if (afterMain != null) afterMain.run();
            return;
        }
        final Runnable done = afterMain;
        try {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    ClipboardFormat format = ClipboardFormats.findByFile(file);
                    if (format == null) {
                        getLogger().warning("3 Тропы: неизвестный формат схемы " + file.getName());
                    } else {
                        Clipboard clipboard;
                        try (java.io.FileInputStream fis = new java.io.FileInputStream(file);
                             ClipboardReader reader = format.getReader(fis)) {
                            clipboard = reader.read();
                        }
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
                        getLogger().info("3 Тропы: схема '" + schemName + "' вставлена в "
                                + minX + "," + minY + "," + minZ);
                    }
                } catch (Throwable t) {
                    getLogger().severe("3 Тропы: ошибка вставки схемы '" + schemName + "': " + t);
                }
                if (done != null) {
                    try { Bukkit.getScheduler().runTask(this, done); }
                    catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            getLogger().warning("3 Тропы: не смог запустить вставку схемы: " + t.getMessage());
            if (done != null) done.run();
        }
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

    // ---------- временные дорожки конвейера ----------

    /**
     * Поднимает две дорожки на время ивента. Скорость и кулдаун по умолчанию
     * наследуются у постоянной дорожки спавнера, чтобы новые тропы шли в том же
     * темпе. Гарантированных легендарного и мифика у временных дорожек нет —
     * это сделано на стороне спавнера.
     */
    private int openRoadsLanes(World world) {
        closeRoadsLanes();
        double speed = roadsLaneSpeed;
        long cooldown = roadsLaneCooldown;
        if (speed <= 0 || cooldown <= 0) {
            for (String id : getPermanentSpawnerIds()) {
                if (speed <= 0) {
                    double s = getSpawnerSpeed(id);
                    if (s > 0) speed = s;
                }
                if (cooldown <= 0) {
                    long c = getSpawnerCooldownTicks(id);
                    if (c > 0) cooldown = c;
                }
                if (speed > 0 && cooldown > 0) break;
            }
        }
        if (speed <= 0) speed = 0.2;
        if (cooldown <= 0) cooldown = 600L;

        int opened = 0;
        if (addRoadsLane(ROADS_LANE1_ID, world, roadsLane1Spawn, roadsLane1End, speed, cooldown)) opened++;
        if (addRoadsLane(ROADS_LANE2_ID, world, roadsLane2Spawn, roadsLane2End, speed, cooldown)) opened++;
        if (opened == 0) {
            getLogger().warning("3 Тропы: ни одна временная дорожка не поднялась — спавнер не отвечает?");
        }
        // Область метеоритов считается по точкам конвейеров и кэшируется — сбрасываем.
        regionBounds = null;
        return opened;
    }

    private boolean addRoadsLane(String id, World world, String spawnRaw, String endRaw, double speed, long cooldown) {
        Location spawn = parseRoadsPoint(world, spawnRaw);
        Location end = parseRoadsPoint(world, endRaw);
        if (spawn == null || end == null) {
            getLogger().warning("3 Тропы: у дорожки " + id + " битые координаты, пропускаю.");
            return false;
        }
        if (!addEventConveyor(id, spawn, end, speed, "", cooldown)) return false;
        roadsLaneIds.add(id);
        return true;
    }

    private void closeRoadsLanes() {
        if (roadsLaneIds.isEmpty()) return;
        int removed = 0;
        for (String id : new ArrayList<>(roadsLaneIds)) {
            int cleaned = removeEventConveyor(id);
            if (cleaned >= 0) removed += cleaned;
        }
        roadsLaneIds.clear();
        regionBounds = null;
        getLogger().info("3 Тропы: временные дорожки сняты, убрано некупленных мобов — " + removed + ".");
    }

    // =========================================================
    // ИВЕНТ 5: 2x УДАЧА
    // =========================================================
    /**
     * Просит спавнер поднять шансы на Легендарный и выше. Всё удвоение считает
     * сам спавнер: он единственный знает свою сетку редкостей и умеет забрать
     * разницу у Обычного, чтобы сумма осталась ровно 100.
     *
     * Множитель живёт только в памяти спавнера, поэтому падение сервера с
     * активным ивентом не оставит шансы перекошенными — после старта они обычные.
     */
    private void startLucky2x(EventSession session) {
        if (!setSpawnerLuck(luckMultiplier)) {
            getLogger().warning("2x Удача: спавнер не отозвался, шансы не изменились "
                    + "(нет BrainrotSpawner или он старой версии).");
            return;
        }
        String mult = trimNumber(luckMultiplier);
        for (Player p : session.world.getPlayers()) {
            p.sendMessage("§d§l✦ ×" + mult + " удача! §7Шанс на легендарных и выше поднят.");
        }
        getLogger().info("2x Удача: множитель ×" + mult + " включён.");
    }

    private void stopLucky2x() {
        clearSpawnerLuck();
        getLogger().info("2x Удача: множитель снят.");
    }

    /** «2» вместо «2.0» в сообщениях игрокам. */
    private String trimNumber(double v) {
        return (v == Math.rint(v)) ? String.valueOf((long) v) : String.format(Locale.US, "%.2f", v);
    }

    private boolean setSpawnerLuck(double multiplier) {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return false;
        try {
            Object res = spawner.getClass().getMethod("setLuckMultiplier", double.class)
                    .invoke(spawner, multiplier);
            return !(res instanceof Boolean b) || b;
        } catch (NoSuchMethodException ex) {
            getLogger().warning("2x Удача: в установленном BrainrotSpawner нет setLuckMultiplier — обнови спавнер.");
            return false;
        } catch (Throwable t) {
            getLogger().warning("2x Удача: не удалось включить множитель: " + t);
            return false;
        }
    }

    private void clearSpawnerLuck() {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return;
        try {
            spawner.getClass().getMethod("clearLuckMultiplier").invoke(spawner);
        } catch (Throwable t) {
            getLogger().warning("2x Удача: не удалось снять множитель: " + t);
        }
    }

    /** Текущий множитель по данным спавнера — для /brevent status. */
    private double getSpawnerLuck() {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return 1.0;
        try {
            Object res = spawner.getClass().getMethod("getLuckMultiplier").invoke(spawner);
            if (res instanceof Number n) return n.doubleValue();
        } catch (Throwable ignored) {}
        return 1.0;
    }

    // =========================================================
    // ИВЕНТ 6: БАБЛ ГАМ МАШИНА
    // =========================================================
    /*
     * Машина — это схема над конвейером плюс переключатель в спавнере. Сам лифт
     * живёт в спавнере: он телепортирует мобов каждый тик, поэтому поднять моба
     * снаружи невозможно. Здесь только: снимок блоков -> вставка схемы -> включить
     * спавнер; на выходе — выключить спавнер и вернуть блоки по снимку.
     *
     * Откат делаем не второй схемой, а точным снимком: регион маленький (пара сотен
     * блоков), зато возвращается ровно то, что было, включая случайную застройку.
     */
    private World gumPendingTeardown;

    private boolean isGumMachineUp() {
        return gumSnapshotFile().exists() || getConfig().getBoolean("events.gum-machine.state.active", false);
    }

    private File gumSnapshotFile() {
        return new File(getDataFolder(), GUM_SNAPSHOT_FILE);
    }

    private void startGumMachine(EventSession session) {
        final World world = session.world;
        if (world == null) return;
        gumBusy = true;
        if (!saveGumSnapshot(world)) {
            // Без снимка вставлять нельзя: схему потом нечем будет убрать.
            gumBusy = false;
            getLogger().severe("Бабл Гам Машина: снимок региона не снят, ивент отменяю.");
            stopEvent(EventType.GUM_MACHINE, true);
            return;
        }
        markGumState(true, world.getName());
        getLogger().info("Бабл Гам Машина: ставлю схему " + gumSchematic + ".");
        pasteGumSchematic(world, () -> {
            gumBusy = false;
            if (gumPendingTeardown != null) { runPendingGumTeardown(); return; }
            if (!isActive(EventType.GUM_MACHINE)) { restoreGumRegion(world); return; }
            if (!setSpawnerGumMachine(world)) {
                getLogger().warning("Бабл Гам Машина: спавнер не отозвался — пузырь работать не будет "
                        + "(нет BrainrotSpawner или он старой версии).");
            }
            for (Player p : world.getPlayers()) {
                p.sendMessage("§d§l✦ Бабл Гам Машина заработала! §7Мобы залипают в пузыре и выходят «Баблгамовыми».");
                try { p.playSound(p.getLocation(), Sound.BLOCK_HONEY_BLOCK_PLACE, 0.9f, 1.3f); }
                catch (Throwable ignored) {}
            }
        });
    }

    private void teardownGumMachine(World world) {
        clearSpawnerGumMachine();
        if (gumBusy) {
            // Вставку схемы отменить нельзя — разберём сразу, как она закончится.
            gumPendingTeardown = (world != null ? world : anyEventWorld());
            getLogger().info("Бабл Гам Машина: разбор отложен до конца вставки схемы.");
            return;
        }
        restoreGumRegion(world);
    }

    private void runPendingGumTeardown() {
        if (gumPendingTeardown == null || gumBusy) return;
        World w = gumPendingTeardown;
        gumPendingTeardown = null;
        restoreGumRegion(w);
    }

    /** Границы региона машины в блоках: minX,minY,minZ,maxX,maxY,maxZ. */
    private int[] gumRegionBox(World world) {
        Location l1 = parseRoadsPoint(world, gumPos1);
        Location l2 = parseRoadsPoint(world, gumPos2);
        if (l1 == null || l2 == null) {
            getLogger().warning("Бабл Гам Машина: не заданы координаты региона (events.gum-machine.region.pos1/pos2).");
            return null;
        }
        return new int[]{
                Math.min(l1.getBlockX(), l2.getBlockX()), Math.min(l1.getBlockY(), l2.getBlockY()),
                Math.min(l1.getBlockZ(), l2.getBlockZ()), Math.max(l1.getBlockX(), l2.getBlockX()),
                Math.max(l1.getBlockY(), l2.getBlockY()), Math.max(l1.getBlockZ(), l2.getBlockZ())
        };
    }

    /** Снимок региона в gum-snapshot.yml. Пишется до вставки схемы. */
    private boolean saveGumSnapshot(World world) {
        int[] box = gumRegionBox(world);
        if (box == null) return false;
        List<String> rows = new ArrayList<>();
        for (int x = box[0]; x <= box[3]; x++) {
            for (int y = box[1]; y <= box[4]; y++) {
                for (int z = box[2]; z <= box[5]; z++) {
                    try {
                        Block b = world.getBlockAt(x, y, z);
                        rows.add(x + ";" + y + ";" + z + ";" + b.getBlockData().getAsString());
                    } catch (Throwable ignored) {}
                }
            }
        }
        YamlConfiguration snap = new YamlConfiguration();
        snap.set("world", world.getName());
        snap.set("blocks", rows);
        try {
            File dir = getDataFolder();
            if (!dir.exists() && !dir.mkdirs()) getLogger().warning("Бабл Гам Машина: не создал папку плагина.");
            snap.save(gumSnapshotFile());
        } catch (Throwable t) {
            getLogger().severe("Бабл Гам Машина: не смог сохранить снимок региона: " + t);
            return false;
        }
        getLogger().info("Бабл Гам Машина: снимок региона снят (" + rows.size() + " блоков).");
        return true;
    }

    /** Точный откат по снимку. Снимок удаляется только после успешной раскладки. */
    private void restoreGumRegion(World world) {
        File file = gumSnapshotFile();
        if (!file.exists()) {
            getLogger().warning("Бабл Гам Машина: снимка нет, откатывать нечего.");
            markGumState(false, "");
            return;
        }
        YamlConfiguration snap = YamlConfiguration.loadConfiguration(file);
        World target = world;
        String wn = snap.getString("world", "");
        if (wn != null && !wn.isEmpty()) {
            World fromSnap = Bukkit.getWorld(wn);
            if (fromSnap != null) target = fromSnap;
        }
        if (target == null) {
            getLogger().warning("Бабл Гам Машина: мир снимка не найден, откат невозможен. Проверь регион руками.");
            return;
        }
        final World finalWorld = target;
        int restored = 0, failed = 0;
        for (String row : snap.getStringList("blocks")) {
            String[] parts = row.split(";", 4);
            if (parts.length < 4) { failed++; continue; }
            try {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                finalWorld.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(parts[3]), false);
                restored++;
            } catch (Throwable t) {
                failed++;
            }
        }
        if (failed > 0) getLogger().warning("Бабл Гам Машина: не вернул " + failed + " блоков из снимка.");
        getLogger().info("Бабл Гам Машина: регион возвращён (" + restored + " блоков).");
        try { if (!file.delete()) getLogger().warning("Бабл Гам Машина: снимок не удалился, удали " + GUM_SNAPSHOT_FILE + " руками."); }
        catch (Throwable ignored) {}
        markGumState(false, "");
        runPendingGumTeardown();
    }

    /** Флаг «схема сейчас в мире» — страховка на случай падения сервера. */
    private void markGumState(boolean active, String worldName) {
        try {
            getConfig().set("events.gum-machine.state.active", active);
            getConfig().set("events.gum-machine.state.world", worldName == null ? "" : worldName);
            saveConfig();
        } catch (Throwable t) {
            getLogger().warning("Бабл Гам Машина: не удалось сохранить состояние: " + t.getMessage());
        }
    }

    /** Вызывается на onEnable: снимок на диске означает незакрытый прошлый запуск. */
    private void restoreGumAfterCrash() {
        if (!isGumMachineUp()) return;
        getLogger().warning("Бабл Гам Машина: прошлый ивент не был закрыт (перезапуск сервера?) — откатываю регион.");
        String wn = getConfig().getString("events.gum-machine.state.world", "");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            World w = (wn == null || wn.isEmpty()) ? null : Bukkit.getWorld(wn);
            if (w == null) w = resolveWorld();
            restoreGumRegion(w);
        }, 100L);
    }

    /** Вставка схемы машины — тот же путь, что у «3 Троп», но по своему региону. */
    private void pasteGumSchematic(World world, Runnable afterMain) {
        final Runnable done = afterMain;
        int[] box = gumRegionBox(world);
        if (box == null) { if (done != null) done.run(); return; }
        final int minX = box[0], minY = box[1], minZ = box[2];
        final File file = resolveSchematicFile(gumSchematic);
        if (file == null || !file.exists()) {
            getLogger().severe("Бабл Гам Машина: схема '" + gumSchematic + "' не найдена. Положи её в "
                    + "plugins/BrainrotEvents/schematics/ или в папку схем WorldEdit/FAWE.");
            if (done != null) done.run();
            return;
        }
        try {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    ClipboardFormat format = ClipboardFormats.findByFile(file);
                    if (format == null) {
                        getLogger().warning("Бабл Гам Машина: неизвестный формат схемы " + file.getName());
                    } else {
                        Clipboard clipboard;
                        try (java.io.FileInputStream fis = new java.io.FileInputStream(file);
                             ClipboardReader reader = format.getReader(fis)) {
                            clipboard = reader.read();
                        }
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
                        getLogger().info("Бабл Гам Машина: схема вставлена в " + minX + "," + minY + "," + minZ);
                    }
                } catch (Throwable t) {
                    getLogger().severe("Бабл Гам Машина: ошибка вставки схемы: " + t);
                }
                if (done != null) {
                    try { Bukkit.getScheduler().runTask(this, done); } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            getLogger().warning("Бабл Гам Машина: не смог запустить вставку схемы: " + t.getMessage());
            if (done != null) done.run();
        }
    }

    /** Включает лифт в спавнере. Точка триггера, верх пузыря, шанс и время висения — из конфига. */
    private boolean setSpawnerGumMachine(World world) {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return false;
        Location trigger = parseRoadsPoint(world, gumTrigger);
        if (trigger == null) {
            getLogger().warning("Бабл Гам Машина: не разобрал events.gum-machine.trigger.");
            return false;
        }
        int holdMin = (int) Math.round(gumHoldMinSeconds * 20.0);
        int holdMax = (int) Math.round(gumHoldMaxSeconds * 20.0);
        try {
            // Сначала версия с кулдауном, потом старая на 8 аргументов —
            // чтобы плагин работал и со спавнером, собранным до кулдауна.
            try {
                Object res = spawner.getClass().getMethod("setGumMachine", String.class, double.class, double.class,
                                double.class, double.class, double.class, int.class, int.class, double.class)
                        .invoke(spawner, world.getName(), trigger.getX(), trigger.getY(), trigger.getZ(),
                                gumTopY, gumChance, holdMin, holdMax, gumCooldownSeconds);
                return !(res instanceof Boolean b) || b;
            } catch (NoSuchMethodException ignored) {
                getLogger().warning("Бабл Гам Машина: у спавнера нет кулдауна (старая сборка) — включаю без него.");
            }
            Object res = spawner.getClass().getMethod("setGumMachine", String.class, double.class, double.class,
                            double.class, double.class, double.class, int.class, int.class)
                    .invoke(spawner, world.getName(), trigger.getX(), trigger.getY(), trigger.getZ(),
                            gumTopY, gumChance, holdMin, holdMax);
            return !(res instanceof Boolean b) || b;
        } catch (NoSuchMethodException ex) {
            getLogger().warning("Бабл Гам Машина: в установленном BrainrotSpawner нет setGumMachine — обнови спавнер.");
            return false;
        } catch (Throwable t) {
            getLogger().warning("Бабл Гам Машина: не удалось включить лифт: " + t);
            return false;
        }
    }

    private void clearSpawnerGumMachine() {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return;
        try {
            spawner.getClass().getMethod("clearGumMachine").invoke(spawner);
        } catch (Throwable t) {
            getLogger().warning("Бабл Гам Машина: не удалось выключить лифт: " + t);
        }
    }

    /** Что об этом думает сам спавнер — для /brevent status. */
    private boolean isSpawnerGumActive() {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return false;
        try {
            Object res = spawner.getClass().getMethod("isGumMachineActive").invoke(spawner);
            return res instanceof Boolean b && b;
        } catch (Throwable ignored) {}
        return false;
    }

    // =========================================================
    // ИВЕНТ 7: НЛО (тарелки летают, луч забирает моба -> мутация «Инопланетный»)
    // =========================================================
    /**
     * Тарелки — это невидимые ArmorStand'ы с моделью ModelEngine, которые мы сами
     * телепортируем каждый тик. Своего ИИ у них нет специально: ванильный ИИ полез бы
     * в стены и мог бы уйти со спавна, а нам нужно ровно наоборот — держаться своей
     * точки, не подниматься по Y и не проходить сквозь блоки.
     *
     * Подъём моба живёт в спавнере (startAbduction): мобов на конвейере каждый тик
     * телепортирует их собственная задача движения, снаружи их не поднять.
     */
    private static final class UfoUnit {
        final Entity carrier;
        final Location home;
        double x, y, z;
        double dirX, dirZ;
        int turnIn;              // через сколько тиков сменить курс
        long readyAtMillis;      // кулдаун луча
        Entity target;           // кого держим лучом
        int beamTicks;           // сколько тиков ещё идёт захват
        int beamTotal;

        UfoUnit(Entity carrier, Location home) {
            this.carrier = carrier;
            this.home = home;
            this.x = home.getX();
            this.y = home.getY();
            this.z = home.getZ();
        }
    }

    private final List<UfoUnit> ufos = new ArrayList<>();
    private BukkitTask ufoTask;

    private void startUfoEvent(EventSession session) {
        final World world = session.world;
        if (world == null) return;
        clearUfos();

        boolean modelOk = isSpawnerModelEngineAvailable();
        int spawned = 0;
        for (String raw : ufoSpawnPoints) {
            Location parsed = parseRoadsPoint(world, raw);
            if (parsed == null) {
                getLogger().warning("НЛО: не разобрал точку '" + raw + "' в events.ufo.spawn-points.");
                continue;
            }
            // Точки в конфиге даны на уровне спавна, а тарелка должна висеть НАД мобами:
            // иначе луч некому забирать (моб обязан быть ниже тарелки) и модель сидит в полу.
            Location home = parsed.clone().add(0.0, ufoHoverHeight, 0.0);
            Entity carrier = spawnUfoCarrier(home);
            if (carrier == null) continue;
            if (modelOk && !attachSpawnerModel(carrier, ufoBlueprint)) {
                getLogger().warning("НЛО: модель '" + ufoBlueprint + "' не встала на тарелку.");
            }
            playSpawnerModel(carrier, "idle", true);
            ufos.add(new UfoUnit(carrier, home));
            spawned++;
        }
        if (spawned == 0) {
            getLogger().severe("НЛО: не появилось ни одной тарелки, ивент отменяю.");
            stopEvent(EventType.UFO, true);
            return;
        }
        if (!modelOk) {
            getLogger().warning("НЛО: ModelEngine недоступен — тарелки будут невидимыми, "
                    + "но луч и мутация работают. Поставь блюпринт " + ufoBlueprint + ".");
        }

        ufoTask = new BukkitRunnable() {
            long tick = 0;
            @Override public void run() {
                if (!isActive(EventType.UFO)) { cancel(); return; }
                tick++;
                tickUfos(tick);
            }
        }.runTaskTimer(this, 5L, 1L);
        session.tasks.add(ufoTask);

        for (Player p : world.getPlayers()) {
            p.sendMessage("§a§l✦ НЛО над спавном! §7Тарелки забирают мобов и возвращают «Инопланетными».");
            try { p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 0.7f); }
            catch (Throwable ignored) {}
        }
        getLogger().info("НЛО: поднял " + spawned + " тарелок в мире " + world.getName() + ".");
    }

    /** Носитель модели: невидимый, без гравитации, неуязвимый, без хитбокса-помехи. */
    private Entity spawnUfoCarrier(Location home) {
        try {
            ArmorStand as = home.getWorld().spawn(home, ArmorStand.class, e -> {
                e.setVisible(false);
                e.setGravity(false);
                e.setInvulnerable(true);
                e.setSilent(true);
                e.setMarker(true);
                e.setPersistent(false);
                e.setCustomNameVisible(false);
                e.addScoreboardTag(UFO_TAG);
            });
            return as;
        } catch (Throwable t) {
            getLogger().warning("НЛО: не смог создать носителя модели: " + t);
            return null;
        }
    }

    private void tickUfos(long tick) {
        for (UfoUnit u : new ArrayList<>(ufos)) {
            if (u.carrier == null || !u.carrier.isValid() || u.carrier.isDead()) {
                ufos.remove(u);
                continue;
            }
            if (u.beamTicks > 0) tickUfoBeam(u);
            else {
                moveUfo(u);
                // idle запускаем не только на спавне: в момент attach риг ME ещё не готов,
                // и самый первый playAnimation уходит в пустоту — тарелка стоит без анимаций.
                if (tick == 10 || tick % 200 == 0) playSpawnerModel(u.carrier, "idle", true);
            }
            if (u.beamTicks <= 0 && tick % ufoTryInterval == 0) tryUfoAbduction(u);
        }
    }

    /**
     * Полёт: только по горизонтали. Y фиксирован точкой появления — «вверх не будут
     * вобще подниматься». Курс меняется сам и отражается от стен и от границы радиуса.
     */
    private void moveUfo(UfoUnit u) {
        if (u.dirX == 0 && u.dirZ == 0) pickUfoCourse(u, null);
        if (--u.turnIn <= 0) pickUfoCourse(u, null);

        // Пока кулдаун прошёл — тарелка не гуляет случайно, а правит курс на ближайшего
        // моба. Со случайным курсом она могла ни разу не оказаться над конвейером за весь
        // ивент: именно поэтому луч не включался и мутация никому не выдавалась.
        Location prey = (System.currentTimeMillis() >= u.readyAtMillis && countUfoBeams() < ufoMaxBeams)
                ? nearestUfoPrey(u) : null;
        if (prey != null) {
            double ax = prey.getX() - u.x, az = prey.getZ() - u.z;
            double len = Math.hypot(ax, az);
            if (len > 0.4) {
                u.dirX = ax / len;
                u.dirZ = az / len;
                u.turnIn = Math.max(u.turnIn, 20);
            }
        }

        double nx = u.x + u.dirX * ufoSpeed;
        double nz = u.z + u.dirZ * ufoSpeed;
        boolean blocked = false;
        // Дальше своего радиуса — разворот. Так тарелка не улетит за спавн.
        double dx = nx - u.home.getX(), dz = nz - u.home.getZ();
        if (dx * dx + dz * dz > ufoRoamRadius * ufoRoamRadius) blocked = true;
        if (!blocked && !isUfoSpaceFree(u.carrier.getWorld(), nx, u.y, nz)) blocked = true;
        if (blocked) {
            // Отражаем курс и сразу пробуем новый: ждать тик — значит подрагивать в стене.
            pickUfoCourse(u, u.home);
            return;
        }
        u.x = nx;
        u.z = nz;
        applyUfoPosition(u, Math.toDegrees(Math.atan2(-u.dirX, u.dirZ)));
    }

    /**
     * Ближайший подходящий моб в пределах своего радиуса — цель для облёта.
     * Условия по высоте те же, что и у луча: иначе тарелка полетит к мобу, которого
     * всё равно не сможет забрать.
     */
    private Location nearestUfoPrey(UfoUnit u) {
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity mob : getSpawnerMobs()) {
            if (mob == null || !mob.isValid() || mob.isDead()) continue;
            if (!mob.getWorld().equals(u.carrier.getWorld())) continue;
            Location l = mob.getLocation();
            double drop = u.y - l.getY();
            if (drop < 1.0 || drop > ufoHoverHeight + 8.0) continue;
            // Гоняться только за тем, что внутри своего радиуса — иначе тарелки сойдутся
            // в одну точку и разбредутся по всему спавну.
            double hx = l.getX() - u.home.getX(), hz = l.getZ() - u.home.getZ();
            if (hx * hx + hz * hz > ufoRoamRadius * ufoRoamRadius) continue;
            if (isSpawnerAbducted(mob) || !isSpawnerConveyorMob(mob)) continue;
            double d = Math.hypot(l.getX() - u.x, l.getZ() - u.z);
            if (d >= bestDist) continue;
            best = l;
            bestDist = d;
        }
        return best;
    }

    private void pickUfoCourse(UfoUnit u, Location pullTo) {
        double angle;
        if (pullTo != null) {
            // Разворот к дому с разбросом — иначе тарелки залипают вдоль стены.
            angle = Math.atan2(pullTo.getZ() - u.z, pullTo.getX() - u.x)
                    + (random.nextDouble() - 0.5) * 1.2;
        } else {
            angle = random.nextDouble() * Math.PI * 2;
        }
        u.dirX = Math.cos(angle);
        u.dirZ = Math.sin(angle);
        u.turnIn = 60 + random.nextInt(80);
    }

    /** Свободно ли место под тарелку: сквозь стены она не летает. */
    private boolean isUfoSpaceFree(World world, double x, double y, double z) {
        try {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block b = world.getBlockAt((int) Math.floor(x) + dx, (int) Math.floor(y) + dy,
                                (int) Math.floor(z) + dz);
                        if (!b.isPassable()) return false;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return true;
    }

    private void applyUfoPosition(UfoUnit u, double yaw) {
        try {
            Location loc = new Location(u.carrier.getWorld(), u.x, u.y, u.z, (float) yaw, 0f);
            u.carrier.teleport(loc);
        } catch (Throwable ignored) {}
    }

    /** Кубик на захват: моб должен быть рядом и НИЖЕ тарелки. */
    private void tryUfoAbduction(UfoUnit u) {
        if (System.currentTimeMillis() < u.readyAtMillis) return;
        if (countUfoBeams() >= ufoMaxBeams) return;
        if (random.nextDouble() * 100.0 >= ufoChance) return;
        pickUfoTarget(u, true);
    }

    /**
     * Ищет моба под тарелкой и запускает луч.
     *
     * @param quiet true для обычного тика (молча), false для /brainrotevent ufobeam —
     *              там нужно объяснить, почему цели нет, иначе ивент выглядит «сломанным».
     * @return null при успехе либо причина отказа
     */
    private String pickUfoTarget(UfoUnit u, boolean quiet) {
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        int seen = 0, tooHigh = 0, tooFar = 0, busy = 0, offBelt = 0;
        for (Entity mob : getSpawnerMobs()) {
            if (mob == null || !mob.isValid() || mob.isDead()) continue;
            if (!mob.getWorld().equals(u.carrier.getWorld())) continue;
            seen++;
            Location l = mob.getLocation();
            double drop = u.y - l.getY();
            // Моб обязан быть под тарелкой, но не на два этажа ниже.
            if (drop < 1.0) { tooHigh++; continue; }
            if (drop > ufoHoverHeight + 8.0) { tooFar++; continue; }
            double d = Math.hypot(l.getX() - u.x, l.getZ() - u.z);
            if (d > ufoBeamRange) { tooFar++; continue; }
            if (d >= bestDist) continue;
            if (isSpawnerAbducted(mob)) { busy++; continue; }
            // Не с конвейера — спавнер всё равно откажет, лучше поискать другого.
            if (!isSpawnerConveyorMob(mob)) { offBelt++; continue; }
            best = mob;
            bestDist = d;
        }
        if (best == null) {
            return "мобов спавнера рядом: " + seen + " §7(выше тарелки: " + tooHigh
                    + ", далеко: " + tooFar + ", уже в луче: " + busy
                    + ", не на конвейере: " + offBelt + ")";
        }

        int hold = (int) Math.round((ufoHoldMinSeconds
                + random.nextDouble() * (ufoHoldMaxSeconds - ufoHoldMinSeconds)) * 20.0);
        // Столько поднимаем, чтобы моб не влез в саму тарелку.
        double rise = Math.min(ufoRiseBlocks, Math.max(1.0, u.y - best.getLocation().getY() - 1.0));
        if (!startSpawnerAbduction(best, rise, hold, "ALIEN")) {
            return "спавнер отказался поднимать моба (startAbduction вернул false — "
                    + "либо моб уже в лифте, либо мутация ALIEN на нём уже есть, либо спавнер старой сборки)";
        }

        u.target = best;
        // 50 тиков подъём + висение + 40 тиков возврат — так считает лифт в спавнере.
        u.beamTotal = 50 + hold + 40;
        u.beamTicks = u.beamTotal;
        u.readyAtMillis = System.currentTimeMillis() + (long) (ufoCooldownSeconds * 1000.0)
                + u.beamTotal * 50L;
        playSpawnerModel(u.carrier, "beam_on", false);
        if (!quiet) getLogger().info("НЛО: луч включён вручную, держим " + (hold / 20.0) + "с.");
        return null;
    }

    /**
     * Пока луч работает, тарелка висит над мобом: иначе моб уезжает по конвейеру
     * из-под луча и картинка рассыпается. Двигаемся только по горизонтали.
     */
    private void tickUfoBeam(UfoUnit u) {
        u.beamTicks--;
        Entity mob = u.target;
        if (mob != null && mob.isValid() && !mob.isDead()) {
            Location l = mob.getLocation();
            // Догоняем моба плавно — резкий телепорт выглядел бы как рывок модели.
            double followX = u.x + (l.getX() - u.x) * 0.25;
            double followZ = u.z + (l.getZ() - u.z) * 0.25;
            double dx = followX - u.home.getX(), dz = followZ - u.home.getZ();
            // За радиус не выходим даже за мобом: иначе тарелка уедет со спавна.
            if (dx * dx + dz * dz <= (ufoRoamRadius + ufoBeamRange) * (ufoRoamRadius + ufoBeamRange)
                    && isUfoSpaceFree(u.carrier.getWorld(), followX, u.y, followZ)) {
                u.x = followX;
                u.z = followZ;
            }
            applyUfoPosition(u, Math.toDegrees(Math.atan2(-(l.getX() - u.x), l.getZ() - u.z)));
        }
        int passed = u.beamTotal - u.beamTicks;
        if (passed == 50) playSpawnerModel(u.carrier, "abduct", false);
        if (u.beamTicks == 40) playSpawnerModel(u.carrier, "beam_off", false);
        if (u.beamTicks <= 0) {
            u.target = null;
            u.beamTotal = 0;
            playSpawnerModel(u.carrier, "idle", true);
        }
    }

    private int countUfoBeams() {
        int n = 0;
        for (UfoUnit u : ufos) if (u.beamTicks > 0) n++;
        return n;
    }

    private void teardownUfoEvent() {
        if (ufoTask != null) { try { ufoTask.cancel(); } catch (Throwable ignored) {} ufoTask = null; }
        clearSpawnerAbductions();
        clearUfos();
    }

    private void clearUfos() {
        for (UfoUnit u : new ArrayList<>(ufos)) {
            try {
                detachSpawnerModel(u.carrier);
                if (u.carrier != null && u.carrier.isValid()) u.carrier.remove();
            } catch (Throwable ignored) {}
        }
        ufos.clear();
    }

    /** Тарелки после падения сервера: ищем по тегу во всех мирах. */
    private void cleanupStrayUfos() {
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                try {
                    if (!e.getScoreboardTags().contains(UFO_TAG)) continue;
                    detachSpawnerModel(e);
                    e.remove();
                    removed++;
                } catch (Throwable ignored) {}
            }
        }
        if (removed > 0) getLogger().warning("НЛО: убрал " + removed + " тарелок с прошлого запуска.");
    }

    // ----- мост к спавнеру для НЛО -----

    private boolean startSpawnerAbduction(Entity mob, double rise, int holdTicks, String mutation) {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return false;
        try {
            Object res = spawner.getClass()
                    .getMethod("startAbduction", Entity.class, double.class, int.class, String.class)
                    .invoke(spawner, mob, rise, holdTicks, mutation);
            return !(res instanceof Boolean b) || b;
        } catch (NoSuchMethodException ex) {
            getLogger().warning("НЛО: в установленном BrainrotSpawner нет startAbduction — обнови спавнер.");
            return false;
        } catch (Throwable t) {
            getLogger().warning("НЛО: не удалось поднять моба лучом: " + t);
            return false;
        }
    }

    private boolean isSpawnerAbducted(Entity mob) {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return false;
        try {
            Object res = spawner.getClass().getMethod("isAbducted", Entity.class).invoke(spawner, mob);
            return res instanceof Boolean b && b;
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Едет ли моб по конвейеру. Луч поднимает только таких — мобов, купленных и стоящих
     * на базе, спавнер поднимать откажется. На старом спавнере метода нет: тогда не
     * фильтруем вообще, пусть решает сам startAbduction.
     */
    private boolean isSpawnerConveyorMob(Entity mob) {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return false;
        try {
            Object res = spawner.getClass().getMethod("isConveyorMob", Entity.class).invoke(spawner, mob);
            return !(res instanceof Boolean b) || b;
        } catch (NoSuchMethodException ex) {
            return true;
        } catch (Throwable ignored) {}
        return true;
    }

    private void clearSpawnerAbductions() {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return;
        try { spawner.getClass().getMethod("clearAbductions").invoke(spawner); }
        catch (Throwable ignored) {}
    }

    private boolean isSpawnerModelEngineAvailable() {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return false;
        try {
            Object res = spawner.getClass().getMethod("isModelEngineAvailable").invoke(spawner);
            return res instanceof Boolean b && b;
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean attachSpawnerModel(Entity entity, String blueprint) {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return false;
        try {
            Object res = spawner.getClass().getMethod("attachEventModel", Entity.class, String.class)
                    .invoke(spawner, entity, blueprint);
            return res instanceof Boolean b && b;
        } catch (Throwable t) {
            getLogger().warning("НЛО: не смог повесить модель: " + t);
            return false;
        }
    }

    private boolean playSpawnerModel(Entity entity, String animation, boolean loop) {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return false;
        try {
            Object res = spawner.getClass().getMethod("playEventModel", Entity.class, String.class, boolean.class)
                    .invoke(spawner, entity, animation, loop);
            return !(res instanceof Boolean b) || b;
        } catch (Throwable ignored) {}
        return false;
    }

    private void detachSpawnerModel(Entity entity) {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return;
        try { spawner.getClass().getMethod("detachEventModel", Entity.class).invoke(spawner, entity); }
        catch (Throwable ignored) {}
    }

    /** Есть ли в установленной сборке спавнера нужный метод: сборки расходятся во времени. */
    private boolean spawnerHasMethod(String name, int argc) {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return false;
        for (java.lang.reflect.Method m : spawner.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == argc) return true;
        }
        return false;
    }

    /** Отчёт ModelEngine по тарелке: главное — список анимаций, которые видит ME. */
    @SuppressWarnings("unchecked")
    private List<String> describeSpawnerModel(Entity entity) {
        Object spawner = getSpawnerPlugin();
        if (spawner == null || entity == null) return List.of("§cспавнер не подключён");
        try {
            Object res = spawner.getClass().getMethod("describeEventModel", Entity.class)
                    .invoke(spawner, entity);
            if (res instanceof List) return (List<String>) res;
        } catch (NoSuchMethodException ex) {
            return List.of("§7describeEventModel в спавнере нет — обнови BrainrotSpawner, "
                    + "чтобы видеть список анимаций блюпринта");
        } catch (Throwable t) {
            return List.of("§cне удалось получить отчёт ME: " + t);
        }
        return List.of();
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
        World running = anyEventWorld();
        if (running != null) return running;
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

    /** Постоянные дорожки спавнера — у них ивент занимает скорость и кулдаун. */
    @SuppressWarnings("unchecked")
    private List<String> getPermanentSpawnerIds() {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return Collections.emptyList();
        try {
            Object res = spawner.getClass().getMethod("getPermanentSpawnerIds").invoke(spawner);
            if (res instanceof List) return (List<String>) res;
        } catch (Throwable ignored) {}
        return Collections.emptyList();
    }

    private double getSpawnerSpeed(String id) {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return 0.0;
        try {
            Object res = spawner.getClass().getMethod("getSpawnerSpeed", String.class).invoke(spawner, id);
            if (res instanceof Number n) return n.doubleValue();
        } catch (Throwable ignored) {}
        return 0.0;
    }

    private long getSpawnerCooldownTicks(String id) {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return 0L;
        try {
            Object res = spawner.getClass().getMethod("getSpawnerCooldownTicks", String.class).invoke(spawner, id);
            if (res instanceof Number n) return n.longValue();
        } catch (Throwable ignored) {}
        return 0L;
    }

    /** Заводит временную дорожку в спавнере. Пустое направление — спавнер посчитает сам. */
    private boolean addEventConveyor(String id, Location spawn, Location despawn,
                                     double speed, String direction, long cooldownTicks) {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) {
            getLogger().warning("3 Тропы: BrainrotSpawner не подключён, дорожку " + id + " не поднять.");
            return false;
        }
        try {
            Object res = spawner.getClass().getMethod("addEventConveyor",
                            String.class, Location.class, Location.class, double.class, String.class, long.class)
                    .invoke(spawner, id, spawn, despawn, speed, direction, cooldownTicks);
            return res instanceof Boolean && (Boolean) res;
        } catch (NoSuchMethodException ex) {
            getLogger().warning("3 Тропы: в установленном BrainrotSpawner нет addEventConveyor — обнови спавнер.");
            return false;
        } catch (Throwable t) {
            getLogger().warning("3 Тропы: дорожка " + id + " не поднялась: " + t);
            return false;
        }
    }

    /** Снимает временную дорожку. Возвращает число убранных мобов или -1. */
    private int removeEventConveyor(String id) {
        Object spawner = getSpawnerPlugin();
        if (spawner == null) return -1;
        try {
            Object res = spawner.getClass().getMethod("removeEventConveyor", String.class).invoke(spawner, id);
            if (res instanceof Number n) return n.intValue();
        } catch (Throwable t) {
            getLogger().warning("3 Тропы: дорожку " + id + " снять не вышло: " + t);
        }
        return -1;
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
        if (isPartyCreeper(event.getEntity())) { event.setCancelled(true); return; }
        if (cause == EntityDamageEvent.DamageCause.LIGHTNING) { event.setCancelled(true); return; }
        // Отброшенный крипером игрок не должен умереть от падения.
        if ((cause == EntityDamageEvent.DamageCause.FALL || cause == EntityDamageEvent.DamageCause.FLY_INTO_WALL)
                && event.getEntity() instanceof Player p && knockbackGrace.contains(p.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (isActive(EventType.BAD_WEATHER)
                && (cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof LightningStrike || isMeteor(damager) || isPartyCreeper(damager)) {
            event.setDamage(0.0);
            event.setCancelled(true);
        }
    }

    /** Криперы пати не должны заряжаться от молний во время «Плохой Погоды». */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreeperPower(CreeperPowerEvent event) {
        if (isPartyCreeper(event.getEntity())) event.setCancelled(true);
    }

    /** Цели крипер выбирает не сам — движением рулит ивент. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreeperTarget(EntityTargetEvent event) {
        if (isPartyCreeper(event.getEntity())) event.setCancelled(true);
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
        else if (isActive(EventType.METEOR_SHOWER) && cause == BlockIgniteEvent.IgniteCause.FIREBALL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (!isMeteor(event.getEntity()) && !isPartyCreeper(event.getEntity())) return;
        event.setRadius(0f);
        event.setFire(false);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplode(EntityExplodeEvent event) {
        if (!isMeteor(event.getEntity()) && !isPartyCreeper(event.getEntity())) return;
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
        if (!isActive(EventType.BAD_WEATHER)) return;
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
                    sender.sendMessage("§eИвенты: " + (sessions.isEmpty() ? "§7нет" : "§f" + activeEventsLine()));
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
                sender.sendMessage("§6/" + label + " start <badweather|meteor|creeper|3road|2x|gum> [секунды] §7— можно запускать несколько сразу");
                sender.sendMessage("§6/" + label + " stop [badweather|meteor|creeper|3road|2x|gum] §7— остановить один или все");
                sender.sendMessage("§6/" + label + " status §7— что сейчас идёт");
                sender.sendMessage("§6/" + label + " ufodebug §7— почему НЛО не забирает мобов");
                sender.sendMessage("§6/" + label + " ufobeam §7— включить луч прямо сейчас (проверка мутации)");
                sender.sendMessage("§6/" + label + " ufoanim <имя> [loop] §7— проверить анимацию тарелки");
                sender.sendMessage("§6/" + label + " reload §7— перечитать конфиг");
                return true;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "start" -> {
                    if (args.length < 2) { sender.sendMessage("§cУкажи ивент: badweather, meteor, creeper, 3road, 2x или gum."); return true; }
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
                    if (sessions.isEmpty()) { sender.sendMessage("§7Сейчас нет активных ивентов."); return true; }
                    if (args.length >= 2) {
                        EventType type = EventType.byKey(args[1]);
                        if (type == null) { sender.sendMessage("§cНеизвестный ивент: " + args[1]); return true; }
                        if (!isActive(type)) { sender.sendMessage("§7Ивент «" + type.title + "» не идёт."); return true; }
                        stopEvent(type, false);
                        sender.sendMessage("§aИвент «" + type.title + "» остановлен.");
                        return true;
                    }
                    String all = activeEventsLine();
                    stopAllEvents(false);
                    sender.sendMessage("§aОстановлено: §f" + all);
                }
                case "status" -> {
                    World w = anyEventWorld() != null ? anyEventWorld() : resolveWorld(sender);
                    sender.sendMessage("§7Мир ивентов: §f" + (w != null ? w.getName() : "?")
                            + " §7(events.world = §f" + (eventsWorldName.isEmpty() ? "авто" : eventsWorldName) + "§7)");
                    sender.sendMessage("§7Спавнер подключён: " + (getSpawnerPlugin() != null ? "§aда" : "§cнет")
                            + " §7| мобов на конвейере: §f" + getSpawnerMobs().size());
                    double lm = getSpawnerLuck();
                    sender.sendMessage("§7Множитель удачи в спавнере: §f×" + trimNumber(lm)
                            + (lm > 1.0 ? " §d(ивент идёт)" : " §7(обычные шансы)"));
                    if (w != null) {
                        double[] box = getRegionBounds(w);
                        sender.sendMessage("§7Область метеоритов: §f" + (box == null ? "не определена"
                                : String.format(Locale.US, "%.0f,%.0f → %.0f,%.0f", box[0], box[1], box[2], box[3])));
                    }
                    if (sessions.isEmpty()) { sender.sendMessage("§7Активных ивентов нет."); return true; }
                    sender.sendMessage("§eИдёт ивентов: §f" + sessions.size());
                    for (EventSession s : new ArrayList<>(sessions.values())) {
                        int left = (int) Math.max(0, Math.ceil((s.endMillis - System.currentTimeMillis()) / 1000.0));
                        sender.sendMessage(s.type.format + "• " + s.type.title + " §7(осталось " + formatTime(left) + ")");
                        if (s.type == EventType.METEOR_SHOWER) {
                            sender.sendMessage("§7  метеоритов в воздухе: §f" + activeMeteors.size());
                        }
                        if (s.type == EventType.CREEPER_PARTY) {
                            sender.sendMessage("§7  криперов в линии: §f" + partyCreepers.size()
                                    + " §7| бегут к цели: §f" + creeperCharges.size()
                                    + " §7| кубик раз в §f" + creeperChargeInterval + " §7тиков с шансом §f"
                                    + (int) Math.round(creeperChargeChance * 100) + "%"
                                    + " §7| шанс на игрока: §f" + (int) Math.round(creeperPlayerChance * 100) + "%");
                        }
                        if (s.type == EventType.THREE_ROADS) {
                            sender.sendMessage("§7  временных дорожек: §f" + roadsLaneIds.size()
                                    + " §7| перестройка: " + (roadsBusy ? "§eидёт" : "§aзакончена")
                                    + " §7| схемы: §f" + roadsSchemOpen + " §7/ §f" + roadsSchemClose);
                        }
                        if (s.type == EventType.UFO) {
                            sender.sendMessage("§7  тарелок: §f" + ufos.size()
                                    + " §7| лучей: §f" + countUfoBeams() + "§7/§f" + ufoMaxBeams
                                    + " §7| высота: §f+" + trimNumber(ufoHoverHeight)
                                    + " §7| шанс: §f" + trimNumber(ufoChance) + "%"
                                    + " §7| подробности: §f/" + label + " ufodebug");
                        }
                        if (s.type == EventType.GUM_MACHINE) {
                            sender.sendMessage("§7  схема: §f" + gumSchematic
                                    + " §7| шанс: §f" + trimNumber(gumChance) + "%"
                                    + " §7| висит: §f" + trimNumber(gumHoldMinSeconds) + "–" + trimNumber(gumHoldMaxSeconds) + "с"
                                    + " §7| кд: §f" + trimNumber(gumCooldownSeconds) + "с"
                                    + " §7| верх Y: §f" + trimNumber(gumTopY));
                            sender.sendMessage("§7  снимок региона: " + (gumSnapshotFile().exists() ? "§aесть" : "§cнет")
                                    + " §7| стройка: " + (gumBusy ? "§eидёт" : "§aзакончена")
                                    + " §7| лифт в спавнере: " + (isSpawnerGumActive() ? "§aвкл" : "§cвыкл"));
                        }
                    }
                }
                case "ufodebug" -> {
                    sender.sendMessage("§7Спавнер: " + (getSpawnerPlugin() != null ? "§aда" : "§cнет")
                            + " §7| startAbduction: " + (spawnerHasMethod("startAbduction", 4) ? "§aесть" : "§cНЕТ (обнови BrainrotSpawner)")
                            + " §7| ME: " + (isSpawnerModelEngineAvailable() ? "§aда" : "§cнет"));
                    sender.sendMessage("§7Тарелок: §f" + ufos.size() + " §7| лучей сейчас: §f" + countUfoBeams()
                            + "§7/§f" + ufoMaxBeams + " §7| мобов спавнера: §f" + getSpawnerMobs().size());
                    sender.sendMessage("§7hover-height §f" + trimNumber(ufoHoverHeight)
                            + " §7| beam-range §f" + trimNumber(ufoBeamRange)
                            + " §7| шанс §f" + trimNumber(ufoChance) + "% §7раз в §f" + ufoTryInterval + " §7тиков"
                            + " §7| подъём §f" + trimNumber(ufoRiseBlocks));
                    for (UfoUnit u : new ArrayList<>(ufos)) {
                        sender.sendMessage(String.format(Locale.US,
                                "§7  тарелка §fY=%.1f §7(%.1f, %.1f) §7луч: %s §7кд: %.1fс",
                                u.y, u.x, u.z, u.beamTicks > 0 ? "§aидёт" : "§7нет",
                                Math.max(0, u.readyAtMillis - System.currentTimeMillis()) / 1000.0));
                        int seen = 0;
                        for (Entity mob : getSpawnerMobs()) {
                            if (mob == null || !mob.isValid()) continue;
                            if (!mob.getWorld().equals(u.carrier.getWorld())) continue;
                            seen++;
                            Location l = mob.getLocation();
                            double d = Math.hypot(l.getX() - u.x, l.getZ() - u.z);
                            if (d <= ufoBeamRange + 4) {
                                sender.sendMessage(String.format(Locale.US,
                                        "§7    моб рядом: гориз. §f%.1f §7(лимит %.1f), ниже на §f%.1f§7, конвейер: %s",
                                        d, ufoBeamRange, u.y - l.getY(),
                                        isSpawnerConveyorMob(mob) ? "§aда" : "§cнет"));
                            }
                        }
                        if (seen == 0) sender.sendMessage("§c    мобов спавнера в этом мире нет вообще");
                    }
                    if (!ufos.isEmpty()) {
                        for (String line : describeSpawnerModel(ufos.get(0).carrier)) {
                            sender.sendMessage("§7  " + line);
                        }
                    }
                }
                case "ufobeam" -> {
                    if (ufos.isEmpty()) { sender.sendMessage("§cИвент НЛО не идёт — сначала /" + label + " start ufo."); return true; }
                    boolean done = false;
                    for (UfoUnit u : new ArrayList<>(ufos)) {
                        if (u.beamTicks > 0) continue;
                        // Кулдаун при ручной проверке сбрасываем: иначе «ничего не происходит».
                        u.readyAtMillis = 0L;
                        String why = pickUfoTarget(u, false);
                        if (why == null) { done = true; break; }
                        sender.sendMessage("§7Тарелка отказала: §f" + why);
                    }
                    sender.sendMessage(done ? "§aЛуч включён — через ~2.5с моб поднимется, потом вернётся «Инопланетным»."
                            : "§cНи одна тарелка не нашла моба. Смотри /" + label + " ufodebug.");
                }
                case "ufoanim" -> {
                    if (ufos.isEmpty()) { sender.sendMessage("§cИвент НЛО не идёт — сначала /" + label + " start ufo."); return true; }
                    String anim = args.length > 1 ? args[1] : "idle";
                    boolean loop = args.length > 2 && (args[2].equalsIgnoreCase("loop") || args[2].equalsIgnoreCase("true"));
                    // Ручная проверка имён: ME мог разобрать блюпринт с префиксом animation.*,
                    // и тогда плоское имя из кода не играется. playSpawnerModel сам пробует оба.
                    int ok = 0;
                    for (UfoUnit u : new ArrayList<>(ufos)) if (playSpawnerModel(u.carrier, anim, loop)) ok++;
                    sender.sendMessage("§7Анимация §f" + anim + " §7(loop " + loop + "): сыграла на §f"
                            + ok + "§7/§f" + ufos.size() + " §7тарелках."
                            + (ok == 0 ? " §cСмотри список имён в /" + label + " ufodebug." : ""));
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
                return filter(List.of("start", "stop", "status", "reload", "ufodebug", "ufobeam", "ufoanim"), args[0]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("ufoanim")) {
                return filter(List.of("idle", "beam_on", "abduct", "beam_off"), args[1]);
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("stop"))) {
                return filter(List.of("badweather", "meteor", "creeper", "3road", "2x", "gum", "ufo"), args[1]);
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
