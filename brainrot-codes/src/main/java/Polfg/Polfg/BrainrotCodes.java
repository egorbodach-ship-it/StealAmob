package Polfg.Polfg;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class BrainrotCodes extends JavaPlugin implements CommandExecutor {
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


    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();

        // Регистрируем команды
        getCommand("code").setExecutor(this);
        
        getLogger().info("§aBrainrotCodes успешно запущен!");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    // ================= КОМАНДЫ =================
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cТолько для игроков!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage("§cИспользование: /code <промокод>");
            return true;
        }

        String codeInput = args[0];
        handleCode(player, codeInput);
        return true;
    }

    // ================= ЛОГИКА ПРОМОКОДОВ =================
    private void handleCode(Player player, String code) {
        // Ищем код в конфиге (игнорируем регистр при поиске ключа)
        String realCodeKey = null;
        ConfigurationSection codesSection = getConfig().getConfigurationSection("codes");
        
        if (codesSection != null) {
            for (String key : codesSection.getKeys(false)) {
                if (key.equalsIgnoreCase(code)) {
                    realCodeKey = key;
                    break;
                }
            }
        }

        if (realCodeKey == null) {
            player.sendMessage("§c✖ Такого промокода не существует!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        String path = "codes." + realCodeKey + ".";
        
        // 1. Проверка: использовал ли игрок уже этот код
        boolean oneTime = getConfig().getBoolean(path + "one-time", true);
        if (oneTime) {
            List<String> usedPlayers = dataConfig.getStringList("used." + realCodeKey);
            if (usedPlayers.contains(player.getName())) {
                player.sendMessage("§c✖ Вы уже активировали этот промокод!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
        }

        // 2. Проверка: глобальный лимит использований
        int maxUses = getConfig().getInt(path + "max-uses", -1);
        if (maxUses > 0) {
            int currentUses = dataConfig.getInt("global-count." + realCodeKey, 0);
            if (currentUses >= maxUses) {
                player.sendMessage("§c✖ Этот промокод закончился!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
        }

        // 3. Выдача награды
        String type = getConfig().getString(path + "type", "COMMAND").toUpperCase();
        boolean success = false;

        if (type.equals("LUCKY_BLOCK_BASE")) {
            success = giveLuckyBlockOnBase(player);
        } else if (type.equals("COMMAND")) {
            List<String> commands = getConfig().getStringList(path + "commands");
            for (String cmd : commands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
            }
            success = true;
        } else {
            getLogger().warning("Неизвестный тип награды: " + type);
        }

        // 4. Если награда выдана успешно — сохраняем
        if (success) {
            // Сохраняем использование игроком
            if (oneTime) {
                List<String> usedPlayers = dataConfig.getStringList("used." + realCodeKey);
                usedPlayers.add(player.getName());
                dataConfig.set("used." + realCodeKey, usedPlayers);
            }

            // Обновляем глобальный счетчик
            int currentUses = dataConfig.getInt("global-count." + realCodeKey, 0);
            dataConfig.set("global-count." + realCodeKey, currentUses + 1);
            
            saveData();

            player.sendMessage("§a✔ Промокод §e" + realCodeKey + " §aуспешно активирован!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        }
    }

    // ================= ИНТЕГРАЦИЯ С BRAINROTBASES =================
    private boolean giveLuckyBlockOnBase(Player player) {
        Plugin basesPlugin = Bukkit.getPluginManager().getPlugin("BrainrotBases");
        if (basesPlugin == null) {
            player.sendMessage("§c✖ Ошибка: Система баз недоступна!");
            return false;
        }

        try {
            // 1. Получаем имя базы игрока
            // Метод: private String findPlayerBase(Player player)
            Method findBaseMethod = basesPlugin.getClass().getDeclaredMethod("findPlayerBase", Player.class);
            findBaseMethod.setAccessible(true);
            String baseName = (String) findBaseMethod.invoke(basesPlugin, player);

            if (baseName == null) {
                player.sendMessage("§c✖ У вас нет базы, чтобы получить награду!");
                return false;
            }

            // 2. Ищем свободное место для моба
            // Метод: public String findFreeMobPoint(String base)
            Method findPointMethod = basesPlugin.getClass().getDeclaredMethod("findFreeMobPoint", String.class);
            findPointMethod.setAccessible(true);
            String freePoint = (String) findPointMethod.invoke(basesPlugin, baseName);

            if (freePoint == null) {
                player.sendMessage("§c✖ На вашей базе нет свободных мест для Лаки-Блока!");
                return false;
            }

            // 3. Получаем список коллекторов
            // Метод: public List<String> getCollectorPoints(String baseName)
            Method getCollectorsMethod = basesPlugin.getClass().getDeclaredMethod("getCollectorPoints", String.class);
            // Либо через поле, если геттера нет, но в BrainrotBases мы добавляли геттеры в прошлом запросе.
            // Если нет, используем рефлексию к полю map.
            // Предположим, что public метод есть (из прошлого запроса). Если нет - используем приватный.
            List<String> collectorPoints = null;
            try {
                collectorPoints = (List<String>) getCollectorsMethod.invoke(basesPlugin, baseName);
            } catch (Exception e) {
                // Если public метода нет, пробуем достать из приватного поля baseCollectorPoints
                java.lang.reflect.Field field = basesPlugin.getClass().getDeclaredField("baseCollectorPoints");
                field.setAccessible(true);
                java.util.Map<String, List<String>> map = (java.util.Map<String, List<String>>) field.get(basesPlugin);
                collectorPoints = map.get(baseName);
            }

            // 4. Ищем свободный коллектор
            // Метод: private String findFreeCollectorForBase(String base, List<String> collectorPoints)
            Method findColMethod = basesPlugin.getClass().getDeclaredMethod("findFreeCollectorForBase", String.class, List.class);
            findColMethod.setAccessible(true);
            String freeCollector = (String) findColMethod.invoke(basesPlugin, baseName, collectorPoints);

            // Если коллектора нет, берем первый попавшийся (как в логике баз)
            if (freeCollector == null && collectorPoints != null && !collectorPoints.isEmpty()) {
                freeCollector = collectorPoints.get(0);
            }

            // 5. Получаем Enum MobType.SPONGE
            // Вложенный класс BrainrotBases$MobType
            Class<?> mobTypeEnum = null;
            for (Class<?> clazz : basesPlugin.getClass().getDeclaredClasses()) {
                if (clazz.getSimpleName().equals("MobType")) {
                    mobTypeEnum = clazz;
                    break;
                }
            }
            
            if (mobTypeEnum == null) {
                getLogger().severe("Не найден Enum MobType в BrainrotBases!");
                return false;
            }

            Object spongeType = Enum.valueOf((Class<Enum>) mobTypeEnum, "SPONGE");

            // 6. Спавним моба
            // Метод: private void spawnMobAtPoint(String base, String mobPoint, String collectorPoint, MobType type)
            Method spawnMethod = basesPlugin.getClass().getDeclaredMethod("spawnMobAtPoint", String.class, String.class, String.class, mobTypeEnum);
            spawnMethod.setAccessible(true);
            spawnMethod.invoke(basesPlugin, baseName, freePoint, freeCollector, spongeType);

            // 7. Сохраняем мобов игрока
            // Метод: private void savePlayerMobsInstantly(String playerName)
            Method saveMethod = basesPlugin.getClass().getDeclaredMethod("savePlayerMobsInstantly", String.class);
            saveMethod.setAccessible(true);
            saveMethod.invoke(basesPlugin, player.getName());

            player.sendMessage("§d§l✦ ВАМ ВЫДАН ЛАКИ-БЛОК НА БАЗУ! ✦");
            return true;

        } catch (Exception e) {
            getLogger().severe("Ошибка при интеграции с BrainrotBases: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage("§c✖ Произошла внутренняя ошибка при выдаче награды.");
            return false;
        }
    }

    // ================= ФАЙЛ ДАННЫХ =================
    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveData() {
        try {
            saveConfigAsync(dataConfig, dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}