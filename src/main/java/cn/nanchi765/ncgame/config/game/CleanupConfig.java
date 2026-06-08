package cn.nanchi765.ncgame.config.game;

import cn.nanchi765.ncgame.config.GameConfig;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CleanupConfig implements GameConfig {

    private final File file;
    private FileConfiguration config;

    public CleanupConfig(File file) {
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

    // 唯一出生点
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

    public List<String> getBreakableBlocks() {
        return config.getStringList("game.breakable-blocks");
    }

    // 工具
    public Material getShearsMaterial() {
        String mat = config.getString("game.tools.shears.material", "SHEARS");
        Material material = Material.matchMaterial(mat);
        return material != null ? material : Material.SHEARS;
    }

    public String getShearsName() {
        return ChatColor.translateAlternateColorCodes('&',
                config.getString("game.tools.shears.name", "&a剪刀"));
    }

    public Material getAxeMaterial() {
        String mat = config.getString("game.tools.axe.material", "IRON_AXE");
        Material material = Material.matchMaterial(mat);
        return material != null ? material : Material.IRON_AXE;
    }

    public String getAxeName() {
        return ChatColor.translateAlternateColorCodes('&',
                config.getString("game.tools.axe.name", "&a铁斧"));
    }

    public Material getShovelMaterial() {
        String mat = config.getString("game.tools.shovel.material", "IRON_SPADE");
        Material material = Material.matchMaterial(mat);
        return material != null ? material : Material.IRON_SPADE;
    }

    public String getShovelName() {
        return ChatColor.translateAlternateColorCodes('&',
                config.getString("game.tools.shovel.name", "&a铁铲"));
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
                config.getString("game.scoreboard.title", "&6大扫除"));
    }

    public int getBreakLimitY() {
        return config.getInt("game.break-limit-y", -1);
    }

    // debug
    public boolean isDebugEnabled() {
        return config.getBoolean("game.debug", false);
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
        return "破坏方块，收集最多积分！";
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