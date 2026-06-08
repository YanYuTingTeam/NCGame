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

public class FaithBlockConfig implements GameConfig {

    private final File file;
    private FileConfiguration config;

    public FaithBlockConfig(File file) {
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

    public Location getRegionPos1() {
        World world = getWorld();
        if (world == null) return null;
        double x = config.getDouble("game.region.pos1.x", 0.0);
        double y = config.getDouble("game.region.pos1.y", 64.0);
        double z = config.getDouble("game.region.pos1.z", 0.0);
        return new Location(world, x, y, z);
    }

    public Location getRegionPos2() {
        World world = getWorld();
        if (world == null) return null;
        double x = config.getDouble("game.region.pos2.x", 0.0);
        double y = config.getDouble("game.region.pos2.y", 64.0);
        double z = config.getDouble("game.region.pos2.z", 0.0);
        return new Location(world, x, y, z);
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

    public int getPhaseDurationMin() {
        return config.getInt("game.phase-duration-min", 5);
    }

    public int getPhaseDurationMax() {
        return config.getInt("game.phase-duration-max", 24);
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

    public boolean isCountdownSoundEnabled() {
        return config.getBoolean("game.countdown-sound", true);
    }

    public int getChangeInterval() {
        return config.getInt("game.change-interval", 4);
    }

    public int getProbWhiteToYellow() {
        return config.getInt("game.probabilities.white-to-yellow", 70);
    }

    public int getProbYellowToOrange() {
        return config.getInt("game.probabilities.yellow-to-orange", 50);
    }

    public int getProbOrangeToRed() {
        return config.getInt("game.probabilities.orange-to-red", 30);
    }

    public int getProbRedToAir() {
        return config.getInt("game.probabilities.red-to-air", 10);
    }

    // 在 FaithBlockConfig 类中添加以下两个方法
    public int getChangeAmountMin() {
        return config.getInt("game.change-amount-min", 5);
    }

    public int getChangeAmountMax() {
        return config.getInt("game.change-amount-max", 10);
    }

    public String getScoreboardTitle() {
        return ChatColor.translateAlternateColorCodes('&',
                config.getString("game.scoreboard.title", "&6信仰方块"));
    }

    public double getPhaseChangePercent() {
        return config.getDouble("game.phase-change-percent", 0.3);
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
        return ChatColor.DARK_GREEN + "地面会变色并消失！" + ChatColor.WHITE + "白->" + ChatColor.YELLOW + "黄->" + ChatColor.GOLD + "橙->" + ChatColor.RED + "红->" + ChatColor.WHITE + "消失";
    }

    @Override
    public boolean isCanBuild() {
        return false;
    }

    @Override
    public boolean isCanBreak() {
        return false;
    }
}