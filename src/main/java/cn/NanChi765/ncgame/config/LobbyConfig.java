package cn.nanchi765.ncgame.config;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LobbyConfig {

    private final File file;
    private FileConfiguration config;

    public LobbyConfig(File file) {
        this.file = file;
        reload();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    // ========== 传送相关 ==========
    public String getWorldName() {
        return config.getString("world", "world");
    }

    public double getX() {
        return config.getDouble("x", 0.0);
    }

    public double getY() {
        return config.getDouble("y", 100.0);
    }

    public double getZ() {
        return config.getDouble("z", 0.0);
    }

    public float getYaw() {
        return (float) config.getDouble("yaw", 0.0);
    }

    public float getPitch() {
        return (float) config.getDouble("pitch", 0.0);
    }

    // ========== 返回物品 ==========
    public Material getReturnMaterial() {
        Material m = Material.getMaterial(config.getString("return-item.material", "REDSTONE_BLOCK"));
        return m != null ? m : Material.REDSTONE_BLOCK;
    }

    public String getReturnItemName() {
        return ChatColor.translateAlternateColorCodes('&', config.getString("return-item.name", "&c返回大厅"));
    }

    public int getReturnSlot() {
        return config.getInt("return-item.slot", 8);
    }

    // ========== BungeeCord ==========
    public String getBungeeServer() {
        return config.getString("bungee-server", "lobby");
    }

    // ========== 游戏设置 ==========
    public int getMinPlayers() {
        return config.getInt("game-settings.min-players", 2);
    }

    public int getRequiredPlayers() {
        return config.getInt("game-settings.required-players", 2);
    }

    public int getCountdownSeconds() {
        return config.getInt("game-settings.countdown-seconds", 30);
    }

    // ========== 计分板 ==========
    public boolean isScoreboardEnabled() {
        return config.getBoolean("scoreboard.enabled", false);
    }

    public String getScoreboardTitle() {
        return ChatColor.translateAlternateColorCodes('&', config.getString("scoreboard.title", "&6大厅"));
    }

    /**
     * 返回颜色转译后的模板行列表（不替换变量）
     */
    public List<String> getScoreboardLines() {
        List<String> raw = config.getStringList("scoreboard.lines");
        List<String> colored = new ArrayList<>();
        for (String line : raw) {
            colored.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        return colored;
    }
}