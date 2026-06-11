package Polfg.Polfg;

import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * BrainrotEvents — упрощён до чистого проигрывателя фоновой музыки.
 * Все прочие ивенты (зима, снегопад, NPC-снеговики, авто-ивент, вставка
 * схематик, босс-бар) вырезаны по запросу. Тег MUTATION_SNOWY здесь больше
 * не ставится — он независимо выдаётся в BrainrotBases/BrainrotSpawner.
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
    // Это убирает рассинхрон/наложение, которое было с одним общим таймером.
    private final Map<UUID, BukkitTask> playerLoops = new HashMap<>();

    // =========================================================
    // LIFECYCLE
    // =========================================================
    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadMusicConfig();

        if (getCommand("brainrotmusic") != null) {
            MusicCommand cmd = new MusicCommand();
            getCommand("brainrotmusic").setExecutor(cmd);
            getCommand("brainrotmusic").setTabCompleter(cmd);
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        // Запускаем музыку тем, кто уже онлайн (например, после /reload).
        if (musicEnabled) {
            for (Player p : Bukkit.getOnlinePlayers()) startPlayerLoop(p);
        }
        getLogger().info("BrainrotEvents (только музыка) включён. Трек: " + soundKey
                + ", категория " + soundCategory + ", длина " + trackLengthSeconds + "с");
    }

    @Override
    public void onDisable() {
        // __leakfix__
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

        // Валидация
        if (trackLengthSeconds < 1) trackLengthSeconds = 1;
        if (soundVolume < 0f) soundVolume = 0f;
        if (soundPitch < 0.5f) soundPitch = 0.5f;
        if (soundPitch > 2.0f) soundPitch = 2.0f;
    }

    // =========================================================
    // ВОСПРОИЗВЕДЕНИЕ (пер-игровой цикл)
    // =========================================================
    private void playOnce(Player p) {
        // Непозиционно центрируем на самом игроке -> полная громкость без паннинга
        // и затухания при движении. Категория MUSIC, чтобы регулировался
        // музыкальным ползунком, а не "Окружением".
        try { p.playSound(p.getLocation(), soundKey, soundCategory, soundVolume, soundPitch); }
        catch (Throwable t) {
            try { p.playSound(p.getLocation(), soundKey, soundVolume, soundPitch); } catch (Throwable ignored) {}
        }
    }

    private void stopSoundFor(Player p) {
        try { p.stopSound(soundKey, soundCategory); }
        catch (Throwable t) { try { p.stopSound(soundKey); } catch (Throwable ignored) {} }
    }

    /** Персональный цикл музыки игрока: играет сразу и повторяет ровно по длине трека. */
    private void startPlayerLoop(Player p) {
        stopPlayerLoop(p);     // без наложений
        if (!musicEnabled) return;
        final UUID id = p.getUniqueId();

        stopSoundFor(p);       // глушим возможные хвосты прошлого трека
        playOnce(p);           // старт трека с нуля для этого игрока

        long periodTicks = trackLengthSeconds * 20L;
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() {
                Player pl = Bukkit.getPlayer(id);
                if (pl == null || !pl.isOnline()) { stopPlayerLoopById(id); return; }
                playOnce(pl);  // ровно когда трек закончился — следующий повтор
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

    // =========================================================
    // join/quit
    // =========================================================
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!musicEnabled) return;
        final Player p = e.getPlayer();
        final UUID id = p.getUniqueId();
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
    }

    // =========================================================
    // COMMAND /brainrotmusic
    // =========================================================
    private class MusicCommand implements CommandExecutor, TabCompleter {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("brainrotevents.admin")) {
                sender.sendMessage("§cУ вас нет прав!"); return true;
            }
            String sub = (args.length >= 1) ? args[0].toLowerCase(Locale.ROOT) : "help";
            switch (sub) {
                case "on" -> {
                    musicEnabled = true;
                    getConfig().set("music.enabled", true); saveConfig();
                    restartAllLoops();
                    sender.sendMessage("§a✓ Музыка включена для всех игроков.");
                }
                case "off" -> {
                    musicEnabled = false;
                    getConfig().set("music.enabled", false); saveConfig();
                    stopAllLoops();
                    sender.sendMessage("§c✗ Музыка выключена.");
                }
                case "restart" -> {
                    restartAllLoops();
                    sender.sendMessage("§a✓ Музыка перезапущена для всех.");
                }
                case "reload" -> {
                    reloadConfig(); loadMusicConfig();
                    restartAllLoops();
                    sender.sendMessage("§a✓ Конфиг музыки перезагружен, треки перезапущены.");
                    showStatus(sender);
                }
                case "status" -> showStatus(sender);
                default -> {
                    sender.sendMessage("§6=== BrainrotMusic ===");
                    sender.sendMessage("§e/brainrotmusic on §7- включить музыку");
                    sender.sendMessage("§e/brainrotmusic off §7- выключить");
                    sender.sendMessage("§e/brainrotmusic restart §7- перезапустить всем");
                    sender.sendMessage("§e/brainrotmusic reload §7- перечитать config.yml");
                    sender.sendMessage("§e/brainrotmusic status §7- статус");
                }
            }
            return true;
        }

        private void showStatus(CommandSender sender) {
            sender.sendMessage("§6=== Музыка ===");
            sender.sendMessage("§7Статус: " + (musicEnabled ? "§a✓ Включена" : "§c✗ Выключена"));
            sender.sendMessage("§7Трек: §f" + soundKey);
            sender.sendMessage("§7Категория: §f" + soundCategory);
            sender.sendMessage("§7Громкость: §f" + soundVolume + " §7| Питч: §f" + soundPitch);
            sender.sendMessage("§7Длина трека: §f" + trackLengthSeconds + "с");
            sender.sendMessage("§7Перезапуск при входе: §f" + restartOnJoin);
            sender.sendMessage("§7Активных циклов: §f" + playerLoops.size());
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) return Arrays.asList("on", "off", "restart", "reload", "status");
            return Collections.emptyList();
        }
    }
}
