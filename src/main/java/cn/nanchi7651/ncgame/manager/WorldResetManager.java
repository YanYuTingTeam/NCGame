package cn.nanchi7651.ncgame.manager;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

public class WorldResetManager {

    private final JavaPlugin plugin;
    private final File backupFolder;

    public WorldResetManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.backupFolder = new File(plugin.getDataFolder(), "world_backups");
        if (!backupFolder.exists()) backupFolder.mkdirs();
    }

    /**
     * 备份一个世界到备份文件夹
     */
    public void backupWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("无法备份世界 " + worldName + "：世界未加载");
            return;
        }

        File worldFolder = world.getWorldFolder();
        File dest = new File(backupFolder, worldName);

        try {
            // 先删除旧备份
            if (dest.exists()) deleteDirectory(dest);
            copyDirectory(worldFolder, dest);
            plugin.getLogger().info("世界 " + worldName + " 备份成功");
        } catch (IOException e) {
            plugin.getLogger().severe("备份世界 " + worldName + " 失败: " + e.getMessage());
        }
    }

    /**
     * 重置世界：卸载 → 删除 → 从备份复制 → 重新加载
     */
    public void resetWorld(String worldName) {
        World oldWorld = Bukkit.getWorld(worldName);
        File worldFolder = (oldWorld != null) ? oldWorld.getWorldFolder() : new File(Bukkit.getWorldContainer(), worldName);

        // 如果世界已加载，先卸载
        if (oldWorld != null) {
            // 将玩家踢出（应由调用者保证此时无玩家）
            oldWorld.getPlayers().forEach(p -> p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation()));
            Bukkit.unloadWorld(oldWorld, false);
        }

        // 删除当前世界文件夹
        if (worldFolder.exists()) {
            try {
                deleteDirectory(worldFolder);
            } catch (IOException e) {
                plugin.getLogger().severe("删除世界 " + worldName + " 失败: " + e.getMessage());
                return;
            }
        }

        // 从备份恢复
        File backup = new File(backupFolder, worldName);
        if (!backup.exists()) {
            plugin.getLogger().severe("世界 " + worldName + " 的备份不存在，无法重置");
            return;
        }

        try {
            copyDirectory(backup, worldFolder);
        } catch (IOException e) {
            plugin.getLogger().severe("恢复世界 " + worldName + " 失败: " + e.getMessage());
            return;
        }

        // 重新加载世界
        WorldCreator creator = new WorldCreator(worldName);
        World loaded = Bukkit.createWorld(creator);
        if (loaded != null) {
            loaded.setAutoSave(true);
        }
        plugin.getLogger().info("世界 " + worldName + " 重置完成");
    }

    private void copyDirectory(File source, File target) throws IOException {
        Path src = source.toPath();
        Path dest = target.toPath();
        Files.walk(src).forEach(s -> {
            Path d = dest.resolve(src.relativize(s));
            try {
                if (Files.isDirectory(s)) {
                    if (!Files.exists(d)) Files.createDirectories(d);
                } else {
                    Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void deleteDirectory(File directory) throws IOException {
        if (directory.exists()) {
            Files.walk(directory.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}