package cn.NanChi765.ncgame;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class EnableGameConfig {

    private final File file;
    private FileConfiguration config;

    public EnableGameConfig(File file) {
        this.file = file;
        reload();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<String, Boolean> getAllGames() {
        Map<String, Boolean> result = new HashMap<>();
        if (config.isConfigurationSection("games")) {
            for (String key : config.getConfigurationSection("games").getKeys(false)) {
                result.put(key, config.getBoolean("games." + key, true));
            }
        }
        return result;
    }

    public boolean isGameEnabled(String gameName) {
        return config.getBoolean("games." + gameName, false);
    }

    public void setGameEnabled(String gameName, boolean enabled) {
        config.set("games." + gameName, enabled);
        save();
    }
}