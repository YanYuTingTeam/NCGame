package cn.nanchi765.ncgame.config;

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

public class QuickHandsConfig {

    private final File file;
    private FileConfiguration config;

    public QuickHandsConfig(File file) {
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

    public List<String> getBreakableBlocks() {
        return config.getStringList("game.breakable-blocks");
    }

    // ===== 工具 =====
    public String getAxeMaterial() {
        return config.getString("game.tools.axe.material", "IRON_AXE");
    }

    public Enchantment getAxeEnchantment() {
        String name = config.getString("game.tools.axe.enchantment", "DIG_SPEED");
        return Enchantment.getByName(name);
    }

    public int getAxeLevel() {
        return config.getInt("game.tools.axe.level", 5);
    }

    public String getPickaxeMaterial() {
        return config.getString("game.tools.pickaxe.material", "IRON_PICKAXE");
    }

    public Enchantment getPickaxeEnchantment() {
        String name = config.getString("game.tools.pickaxe.enchantment", "DIG_SPEED");
        return Enchantment.getByName(name);
    }

    public int getPickaxeLevel() {
        return config.getInt("game.tools.pickaxe.level", 5);
    }

    public String getShovelMaterial() {
        return config.getString("game.tools.shovel.material", "IRON_SPADE");
    }

    public Enchantment getShovelEnchantment() {
        String name = config.getString("game.tools.shovel.enchantment", "DIG_SPEED");
        return Enchantment.getByName(name);
    }

    public int getShovelLevel() {
        return config.getInt("game.tools.shovel.level", 5);
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

    public Location getSpectatorRegionCenter() {
        World world = Bukkit.getWorld(config.getString("game.spectator.region.center.world", getWorldName()));
        if (world == null) world = getWorld();
        double x = config.getDouble("game.spectator.region.center.x", 0.0);
        double y = config.getDouble("game.spectator.region.center.y", 64.0);
        double z = config.getDouble("game.spectator.region.center.z", 0.0);
        return new Location(world, x, y, z);
    }

    public double getSpectatorRegionRadius() {
        return config.getDouble("game.spectator.region.radius", 50.0);
    }

    // ===== 音效 =====
    public boolean isCountdownSoundEnabled() {
        return config.getBoolean("game.countdown-sound", true);
    }

    // ===== 计分板 =====
    public String getScoreboardTitle() {
        return ChatColor.translateAlternateColorCodes('&',
                config.getString("game.scoreboard.title", "&6眼疾手快"));
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
}