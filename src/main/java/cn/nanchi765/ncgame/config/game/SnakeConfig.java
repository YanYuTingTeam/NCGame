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

public class SnakeConfig implements GameConfig {

    private final File file;
    private FileConfiguration config;

    public SnakeConfig(File file) {
        this.file = file;
        reload();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public String getWorldName() {
        return config.getString("game.world", "minigame_world");
    }

    public World getWorld() {
        return Bukkit.getWorld(getWorldName());
    }

    public Location getSpawnLocation() {
        World world = getWorld();
        if (world == null) return null;
        double x = config.getDouble("game.spawn.x", 0.5);
        double y = config.getDouble("game.spawn.y", 64.0);
        double z = config.getDouble("game.spawn.z", 0.5);
        float yaw = (float) config.getDouble("game.spawn.yaw", 0.0);
        float pitch = (float) config.getDouble("game.spawn.pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
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

    public int getVanishInterval() {
        return config.getInt("game.vanish.interval", 10);
    }

    public List<String> getVanishBlocks() {
        return config.getStringList("game.vanish.blocks");
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
// 在 SnakeConfig 类中添加以下方法

    public boolean isBarrierEnabled() {
        return config.getBoolean("game.barrier.enabled", false);
    }

    public int getBarrierY() {
        return config.getInt("game.barrier.y", 62);
    }

    public Location getBarrierPos1() {
        World world = getWorld();
        if (world == null) return null;
        double x = config.getDouble("game.barrier.pos1.x", 0.0);
        double z = config.getDouble("game.barrier.pos1.z", 0.0);
        return new Location(world, x, getBarrierY(), z);
    }

    public double getTimer() {
        return Math.min(5.0, Math.max(0.5, config.getDouble("game.timer", 2.0)));
    }

    public double getJumpPower() {
        return config.getDouble("game.jump-power", 0.42);
    }

    public Location getBarrierPos2() {
        World world = getWorld();
        if (world == null) return null;
        double x = config.getDouble("game.barrier.pos2.x", 0.0);
        double z = config.getDouble("game.barrier.pos2.z", 0.0);
        return new Location(world, x, getBarrierY(), z);
    }

    public double getSpectatorRegionRadius() {
        return config.getDouble("game.spectator.region.radius", 50.0);
    }

    public boolean isCountdownSoundEnabled() {
        return config.getBoolean("game.countdown-sound", true);
    }

    public String getScoreboardTitle() {
        return ChatColor.translateAlternateColorCodes('&',
                config.getString("game.scoreboard.title", "&6贪吃蛇"));
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
        return "脚下的方块即将消失，保持移动！";
    }
}