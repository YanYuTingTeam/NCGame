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

public class KnockbackBattleConfig implements GameConfig {

    private final File file;
    private FileConfiguration config;

    public KnockbackBattleConfig(File file) {
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

    public boolean isDoubleJumpEnabled() {
        return config.getBoolean("game.double-jump.enabled", true);
    }

    public double getDoubleJumpPower() {
        return config.getDouble("game.double-jump.power", 1.5);
    }

    public double getDoubleJumpUpVelocity() {
        return config.getDouble("game.double-jump.up-velocity", 1.5);
    }

    public double getDoubleJumpForwardVelocity() {
        return config.getDouble("game.double-jump.forward-velocity", 0.8);
    }

    public double getDoubleJumpSprintingUpVelocity() {
        return config.getDouble("game.double-jump.sprinting-up-velocity", 1.8);
    }

    public double getDoubleJumpSprintingForwardVelocity() {
        return config.getDouble("game.double-jump.sprinting-forward-velocity", 1.2);
    }

    public long getDoubleJumpCooldown() {
        return config.getLong("game.double-jump.cooldown", 1000L);
    }

    public boolean isKeepDoubleJump() {
        return config.getBoolean("game.double-jump.keep-doublejump", false);
    }

    public int getFallTest() {
        return config.getInt("game.double-jump.fall-test", 3);
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

    public String getScoreboardTitle() {
        return ChatColor.translateAlternateColorCodes('&',
                config.getString("game.scoreboard.title", "&6击退战"));
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
        return "利用击退棍把对手打下平台！";
    }
}