package cn.nanchi765.ncgame.config.game;

import cn.nanchi765.ncgame.config.GameConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SpeedrunConfig implements GameConfig {

    private final File file;
    private FileConfiguration config;

    public SpeedrunConfig(File file) {
        this.file = file;
        reload();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    // 基础设置
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

    // 胜利区域判断
    public boolean isInWinRegion(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;
        String winWorld = config.getString("game.win-region.world", getWorldName());
        if (!world.getName().equals(winWorld)) return false;

        double x1 = config.getDouble("game.win-region.pos1.x", 0);
        double y1 = config.getDouble("game.win-region.pos1.y", 0);
        double z1 = config.getDouble("game.win-region.pos1.z", 0);
        double x2 = config.getDouble("game.win-region.pos2.x", 0);
        double y2 = config.getDouble("game.win-region.pos2.y", 0);
        double z2 = config.getDouble("game.win-region.pos2.z", 0);

        double minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        double minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);

        return loc.getX() >= minX && loc.getX() <= maxX
                && loc.getY() >= minY && loc.getY() <= maxY
                && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    // 旁观者
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

    // 音效
    public boolean isCountdownSoundEnabled() {
        return config.getBoolean("game.countdown-sound", true);
    }

    // 计分板
    public String getScoreboardTitle() {
        return ChatColor.translateAlternateColorCodes('&',
                config.getString("game.scoreboard.title", "&6急速酷跑"));
    }

    public double getDeathY() {
        return config.getDouble("game.death-y", 0.0);
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
        return "全速奔跑，第一个到达终点！";
    }
}