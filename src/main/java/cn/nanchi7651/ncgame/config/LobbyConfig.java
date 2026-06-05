package cn.nanchi7651.ncgame.config;

import lombok.Getter;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LobbyConfig {

    private final File file;
    private FileConfiguration config;

    @Getter
    private String worldName;
    private Location lobbyLocation;

    @Getter
    private ItemStack lobbyItem;

    public LobbyConfig(File file) {
        this.file = file;
        reload();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);

        this.worldName = config.getString("world", "world");
        double x = config.getDouble("x", 0.0);
        double y = config.getDouble("y", 100.0);
        double z = config.getDouble("z", 0.0);
        float yaw = (float) config.getDouble("yaw", 0.0);
        float pitch = (float) config.getDouble("pitch", 0.0);

        World world = Bukkit.getWorld(worldName);
        this.lobbyLocation = new Location(world, x, y, z, yaw, pitch);
        world.setPVP(false);

        lobbyItem = createReturnItem();
    }

    /**
     * 获取大厅 Location，自动补全 World 引用。
     * 世界不存在时返回 null。
     */
    public Location getLocation() {
        if (lobbyLocation.getWorld() == null) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;
            lobbyLocation.setWorld(world);
        }
        lobbyLocation.getWorld().setPVP(false);
        return lobbyLocation.clone();
    }

    /**
     * 用外部指定的 World 构建 Location（忽略配置中的 world）。
     */
    public Location getLocation(World world) {
        if (world == null) return null;
        world.setPVP(false);
        return new Location(world,
                lobbyLocation.getX(), lobbyLocation.getY(), lobbyLocation.getZ(),
                lobbyLocation.getYaw(), lobbyLocation.getPitch());
    }

    // ========== 返回物品 ==========

    public ItemStack createReturnItem() {
        Material m = Material.getMaterial(config.getString("return-item.material", "REDSTONE_BLOCK"));
        return new ItemStack(m, 1);
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
        return config.getString("scoreboard.title", "&6大厅");
    }

    public List<String> getScoreboardLines() {
        List<String> raw = config.getStringList("scoreboard.lines");
        return new ArrayList<>(raw);
    }
}
