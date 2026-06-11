package Polfg.Polfg.auction;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Loads/saves auction listings to listings.yml in the plugin data folder. */
public class AuctionStorage {

    private final JavaPlugin plugin;
    private final File file;

    public AuctionStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "listings.yml");
    }

    public List<AuctionListing> loadAll() {
        List<AuctionListing> result = new ArrayList<>();
        if (!file.exists()) return result;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("listings");
        if (root == null) return result;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            AuctionListing l = AuctionListing.load(id, s);
            if (l != null) result.add(l);
        }
        return result;
    }

    public void saveAll(Collection<AuctionListing> listings) {
        YamlConfiguration cfg = new YamlConfiguration();
        for (AuctionListing l : listings) {
            ConfigurationSection s = cfg.createSection("listings." + l.id);
            l.save(s);
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0441\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c \u0430\u0443\u043a\u0446\u0438\u043e\u043d: " + e.getMessage());
        }
    }
}
