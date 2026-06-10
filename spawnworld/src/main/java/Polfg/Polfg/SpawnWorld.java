package Polfg.Polfg;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class SpawnWorld extends JavaPlugin implements Listener, CommandExecutor {

    private String sourceWorldName;
    private String targetWorldName;
    private boolean worldReady = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        sourceWorldName = getConfig().getString("source-world", "world");
        targetWorldName = getConfig().getString("target-world", "spawn_world");
        
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("spawnworld").setExecutor(this);
        
        // Загружаем мир при старте если он существует
        Bukkit.getScheduler().runTaskLater(this, () -> {
            loadExistingWorld();
        }, 20L);
        
        getLogger().info(ChatColor.GREEN + "SpawnWorld загружен!");
        getLogger().info(ChatColor.GREEN + "Исходный мир: " + sourceWorldName);
        getLogger().info(ChatColor.GREEN + "Целевой мир: " + targetWorldName);
    }

    @Override
    public void onDisable() {
        // __leakfix__
        try { org.bukkit.Bukkit.getScheduler().cancelTasks(this); } catch (Throwable __t) {}
        try { org.bukkit.event.HandlerList.unregisterAll(this); } catch (Throwable __t) {}
        // Сохраняем мир перед выключением
        World targetWorld = Bukkit.getWorld(targetWorldName);
        if (targetWorld != null) {
            // Телепортируем всех игроков из этого мира
            World defaultWorld = Bukkit.getWorld(sourceWorldName);
            if (defaultWorld == null) {
                defaultWorld = Bukkit.getWorlds().get(0);
            }
            
            for (Player player : targetWorld.getPlayers()) {
                player.teleport(defaultWorld.getSpawnLocation());
            }
            
            // Сохраняем мир
            targetWorld.save();
            getLogger().info("Мир " + targetWorldName + " сохранён!");
        }
    }

    /**
     * Загружает существующий мир если папка уже есть
     */
    private void loadExistingWorld() {
        File worldFolder = new File(Bukkit.getWorldContainer(), targetWorldName);
        
        if (worldFolder.exists() && worldFolder.isDirectory()) {
            // Проверяем есть ли level.dat
            File levelDat = new File(worldFolder, "level.dat");
            if (levelDat.exists()) {
                getLogger().info("Найден существующий мир " + targetWorldName + ", загружаю...");
                
                World world = loadWorld(targetWorldName);
                if (world != null) {
                    worldReady = true;
                    getLogger().info(ChatColor.GREEN + "Мир " + targetWorldName + " успешно загружен!");
                } else {
                    getLogger().warning("Не удалось загрузить мир " + targetWorldName);
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("spawnworld")) {
            return false;
        }
        
        if (!sender.hasPermission("spawnworld.admin")) {
            sender.sendMessage(ChatColor.RED + "Нет прав!");
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "create":
                createWorld(sender);
                break;
            case "delete":
                deleteWorld(sender);
                break;
            case "tp":
            case "teleport":
                teleportToWorld(sender);
                break;
            case "reload":
                reloadWorld(sender);
                break;
            case "save":
                saveWorld(sender);
                break;
            case "info":
                showInfo(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }
        
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== SpawnWorld Команды ===");
        sender.sendMessage(ChatColor.YELLOW + "/spawnworld create " + ChatColor.GRAY + "- Создать/скопировать мир");
        sender.sendMessage(ChatColor.YELLOW + "/spawnworld delete " + ChatColor.GRAY + "- Удалить мир");
        sender.sendMessage(ChatColor.YELLOW + "/spawnworld tp " + ChatColor.GRAY + "- Телепорт в мир");
        sender.sendMessage(ChatColor.YELLOW + "/spawnworld save " + ChatColor.GRAY + "- Сохранить мир");
        sender.sendMessage(ChatColor.YELLOW + "/spawnworld reload " + ChatColor.GRAY + "- Перезагрузить мир");
        sender.sendMessage(ChatColor.YELLOW + "/spawnworld info " + ChatColor.GRAY + "- Информация");
    }

    /**
     * Создаёт копию мира
     */
    private void createWorld(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Начинаю копирование мира...");
        
        // Проверяем исходный мир
        World sourceWorld = Bukkit.getWorld(sourceWorldName);
        if (sourceWorld == null) {
            sender.sendMessage(ChatColor.RED + "Исходный мир '" + sourceWorldName + "' не найден!");
            return;
        }
        
        // Сохраняем исходный мир перед копированием
        sourceWorld.save();
        
        // Проверяем, существует ли уже целевой мир
        World existingWorld = Bukkit.getWorld(targetWorldName);
        if (existingWorld != null) {
            sender.sendMessage(ChatColor.RED + "Мир '" + targetWorldName + "' уже существует!");
            sender.sendMessage(ChatColor.GRAY + "Используйте /spawnworld delete чтобы удалить его.");
            return;
        }
        
        // Выполняем копирование асинхронно
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    File sourceFolder = sourceWorld.getWorldFolder();
                    File targetFolder = new File(Bukkit.getWorldContainer(), targetWorldName);
                    
                    // Удаляем старую папку если есть
                    if (targetFolder.exists()) {
                        deleteFolder(targetFolder);
                    }
                    
                    // Копируем папку мира
                    copyFolder(sourceFolder.toPath(), targetFolder.toPath());
                    
                    // Удаляем uid.dat чтобы мир получил новый ID
                    File uidFile = new File(targetFolder, "uid.dat");
                    if (uidFile.exists()) {
                        uidFile.delete();
                    }
                    
                    // Удаляем session.lock
                    File sessionLock = new File(targetFolder, "session.lock");
                    if (sessionLock.exists()) {
                        sessionLock.delete();
                    }
                    
                    // Загружаем мир в главном потоке
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            World newWorld = loadWorld(targetWorldName);
                            
                            if (newWorld != null) {
                                worldReady = true;
                                sender.sendMessage(ChatColor.GREEN + "Мир '" + targetWorldName + "' успешно создан!");
                                sender.sendMessage(ChatColor.GRAY + "Используйте /spawnworld tp для телепорта.");
                                
                                // Настраиваем мир
                                setupWorld(newWorld);
                            } else {
                                sender.sendMessage(ChatColor.RED + "Ошибка при загрузке мира!");
                            }
                        }
                    }.runTask(SpawnWorld.this);
                    
                } catch (Exception e) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            sender.sendMessage(ChatColor.RED + "Ошибка при копировании: " + e.getMessage());
                            org.bukkit.Bukkit.getLogger().warning("Brainrot: " + e.getMessage());
                        }
                    }.runTask(SpawnWorld.this);
                }
            }
        }.runTaskAsynchronously(this);
    }

    /**
     * Загружает мир
     */
    private World loadWorld(String worldName) {
        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.NORMAL);
        
        try {
            return creator.createWorld();
        } catch (Exception e) {
            getLogger().severe("Ошибка загрузки мира: " + e.getMessage());
            org.bukkit.Bukkit.getLogger().warning("Brainrot: " + e.getMessage());
            return null;
        }
    }

    /**
     * Настраивает мир после создания/загрузки
     */
    private void setupWorld(World world) {
        // Базовые настройки
        world.setAutoSave(true);
        world.setKeepSpawnInMemory(true);
        
        // Можно добавить дополнительные настройки из конфига
        if (getConfig().getBoolean("world-settings.disable-monsters", false)) {
            world.setSpawnFlags(false, true);
        }
        
        if (getConfig().getBoolean("world-settings.always-day", false)) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setTime(6000);
        }
        
        if (getConfig().getBoolean("world-settings.no-weather", false)) {
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setStorm(false);
            world.setThundering(false);
        }
    }

    /**
     * Удаляет мир
     */
    private void deleteWorld(CommandSender sender) {
        World world = Bukkit.getWorld(targetWorldName);
        
        if (world != null) {
            // Телепортируем игроков
            World defaultWorld = Bukkit.getWorld(sourceWorldName);
            if (defaultWorld == null) {
                defaultWorld = Bukkit.getWorlds().get(0);
            }
            
            for (Player player : world.getPlayers()) {
                player.teleport(defaultWorld.getSpawnLocation());
                player.sendMessage(ChatColor.YELLOW + "Мир удаляется, вы были телепортированы.");
            }
            
            // Выгружаем мир
            Bukkit.unloadWorld(world, false);
        }
        
        worldReady = false;
        
        // Удаляем папку асинхронно
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    File worldFolder = new File(Bukkit.getWorldContainer(), targetWorldName);
                    if (worldFolder.exists()) {
                        deleteFolder(worldFolder);
                    }
                    
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            sender.sendMessage(ChatColor.GREEN + "Мир '" + targetWorldName + "' удалён!");
                        }
                    }.runTask(SpawnWorld.this);
                    
                } catch (Exception e) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            sender.sendMessage(ChatColor.RED + "Ошибка при удалении: " + e.getMessage());
                        }
                    }.runTask(SpawnWorld.this);
                }
            }
        }.runTaskAsynchronously(this);
    }

    /**
     * Телепортирует в мир
     */
    private void teleportToWorld(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Только для игроков!");
            return;
        }
        
        Player player = (Player) sender;
        World world = Bukkit.getWorld(targetWorldName);
        
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "Мир не загружен! Используйте /spawnworld create");
            return;
        }
        
        player.teleport(world.getSpawnLocation());
        player.sendMessage(ChatColor.GREEN + "Телепортирован в " + targetWorldName);
    }

    /**
     * Сохраняет мир
     */
    private void saveWorld(CommandSender sender) {
        World world = Bukkit.getWorld(targetWorldName);
        
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "Мир не загружен!");
            return;
        }
        
        world.save();
        sender.sendMessage(ChatColor.GREEN + "Мир " + targetWorldName + " сохранён!");
    }

    /**
     * Перезагружает мир
     */
    private void reloadWorld(CommandSender sender) {
        World world = Bukkit.getWorld(targetWorldName);
        
        if (world != null) {
            // Телепортируем игроков
            World defaultWorld = Bukkit.getWorld(sourceWorldName);
            if (defaultWorld == null) {
                defaultWorld = Bukkit.getWorlds().get(0);
            }
            
            for (Player player : world.getPlayers()) {
                player.teleport(defaultWorld.getSpawnLocation());
            }
            
            // Сохраняем и выгружаем
            world.save();
            Bukkit.unloadWorld(world, true);
        }
        
        // Загружаем заново
        World reloaded = loadWorld(targetWorldName);
        if (reloaded != null) {
            setupWorld(reloaded);
            worldReady = true;
            sender.sendMessage(ChatColor.GREEN + "Мир перезагружен!");
        } else {
            sender.sendMessage(ChatColor.RED + "Ошибка перезагрузки мира!");
        }
    }

    /**
     * Показывает информацию
     */
    private void showInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== SpawnWorld Info ===");
        sender.sendMessage(ChatColor.YELLOW + "Исходный мир: " + ChatColor.WHITE + sourceWorldName);
        sender.sendMessage(ChatColor.YELLOW + "Целевой мир: " + ChatColor.WHITE + targetWorldName);
        
        World world = Bukkit.getWorld(targetWorldName);
        if (world != null) {
            sender.sendMessage(ChatColor.YELLOW + "Статус: " + ChatColor.GREEN + "Загружен");
            sender.sendMessage(ChatColor.YELLOW + "Игроков: " + ChatColor.WHITE + world.getPlayers().size());
            sender.sendMessage(ChatColor.YELLOW + "Спавн: " + ChatColor.WHITE + 
                world.getSpawnLocation().getBlockX() + ", " +
                world.getSpawnLocation().getBlockY() + ", " +
                world.getSpawnLocation().getBlockZ());
        } else {
            File worldFolder = new File(Bukkit.getWorldContainer(), targetWorldName);
            if (worldFolder.exists()) {
                sender.sendMessage(ChatColor.YELLOW + "Статус: " + ChatColor.GRAY + "Не загружен (папка существует)");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Статус: " + ChatColor.RED + "Не создан");
            }
        }
    }

    // =============== СОБЫТИЯ ===============

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Опционально: телепортировать игроков в spawn_world при входе
        if (getConfig().getBoolean("teleport-on-join", false) && worldReady) {
            World world = Bukkit.getWorld(targetWorldName);
            if (world != null) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    event.getPlayer().teleport(world.getSpawnLocation());
                }, 5L);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // Опционально: респавн в spawn_world
        if (getConfig().getBoolean("respawn-in-spawn-world", false) && worldReady) {
            World world = Bukkit.getWorld(targetWorldName);
            if (world != null) {
                event.setRespawnLocation(world.getSpawnLocation());
            }
        }
    }

    // =============== УТИЛИТЫ ===============

    /**
     * Копирует папку рекурсивно
     */
    private void copyFolder(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();
                
                // Пропускаем файлы которые не нужно копировать
                if (fileName.equals("session.lock") || fileName.equals("uid.dat")) {
                    return FileVisitResult.CONTINUE;
                }
                
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Удаляет папку рекурсивно
     */
    private void deleteFolder(File folder) {
        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteFolder(file);
                }
            }
        }
        folder.delete();
    }
}