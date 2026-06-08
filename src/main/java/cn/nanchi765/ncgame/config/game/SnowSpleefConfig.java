package cn.nanchi765.ncgame.config.game;

import cn.nanchi765.ncgame.config.GameConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SnowSpleefConfig implements GameConfig {

    private final File file;
    private FileConfiguration config;

    public SnowSpleefConfig(File file) {
        this.file = file;
        reload();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    // ===== 基础设置 =====
    public String getWorldName() {
        return config.getString("game.world", "minigame_world");
    }

    public World getWorld() {
        return Bukkit.getWorld(getWorldName());
    }

    public List<Location> getSpawnLocations() {
        List<Location> locs = new ArrayList<>();
        World world = getWorld();
        if (world == null) return locs;
        if (config.isConfigurationSection("game.spawns")) {
            for (String key : config.getConfigurationSection("game.spawns").getKeys(false)) {
                double x = config.getDouble("game.spawns." + key + ".x", 0.0);
                double y = config.getDouble("game.spawns." + key + ".y", 64.0);
                double z = config.getDouble("game.spawns." + key + ".z", 0.0);
                float yaw = (float) config.getDouble("game.spawns." + key + ".yaw", 0.0);
                float pitch = (float) config.getDouble("game.spawns." + key + ".pitch", 0.0);
                locs.add(new Location(world, x, y, z, yaw, pitch));
            }
        }
        return locs;
    }

    public int getPrepareTime() {
        return config.getInt("game.prepare-time", 10);
    }

    public int getGameTime() {
        return config.getInt("game.game-time", 300);
    }

    public double getDeathY() {
        return config.getDouble("game.death-y", 0.0);
    }

    public Enchantment getShovelEnchantmentType() {
        String type = config.getString("game.shovel-enchantment.type", "DIG_SPEED");
        return Enchantment.getByName(type);
    }

    public int getShovelEnchantmentLevel() {
        return config.getInt("game.shovel-enchantment.level", 5);
    }

    public List<String> getBreakableBlocks() {
        return config.getStringList("game.breakable-blocks");
    }

    // ===== 旁观者 =====
    public Location getSpectatorSpawn() {
        World world = Bukkit.getWorld(config.getString("game.spectator.spawn.world", getWorldName()));
        if (world == null) world = getWorld();
        double x = config.getDouble("game.spectator.spawn.x", 0.0);
        double y = config.getDouble("game.spectator.spawn.y", 64.0);
        double z = config.getDouble("game.spectator.spawn.z", 0.0);
        float yaw = (float) config.getDouble("game.spectator.spawn.yaw", 0.0);
        float pitch = (float) config.getDouble("game.spectator.spawn.pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    public double getSpectatorRegionRadius() {
        return config.getDouble("game.spectator.region.radius", 50.0);
    }

    // ===== 音效开关 =====
    public boolean isCountdownSoundEnabled() {
        return config.getBoolean("game.countdown-sound", true);
    }

    // ===== 计分板配置 =====
    public String getScoreboardTitle() {
        return ChatColor.translateAlternateColorCodes('&',
                config.getString("game.scoreboard.title", "&6掘一死战"));
    }

    public double getWinY() {
        return config.getDouble("game.win-y", 100.0);
    }

    public List<String> getScoreboardLines() {
        List<String> raw = config.getStringList("game.scoreboard.lines");
        List<String> colored = new ArrayList<>();
        for (String line : raw) {
            colored.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        return colored;
    }

    @Override
    public String subtitle() {
        return "挖掉或使用雪球打掉雪块，将其他人砸下去！";
    }

    @Override
    public boolean isCanBuild() {
        return false;
    }

    @Override
    public boolean isCanBreak() {
        return true;
    }
}