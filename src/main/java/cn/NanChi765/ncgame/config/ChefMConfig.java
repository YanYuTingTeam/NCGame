package cn.nanchi765.ncgame.config;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

public class ChefMConfig {

    private final File file;
    private FileConfiguration config;

    public ChefMConfig(File file) {
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

    // 村民坐标
    public List<Location> getVillagerLocations() {
        List<Location> locs = new ArrayList<>();
        World world = getWorld();
        if (world == null) return locs;
        List<Map<?, ?>> villagers = config.getMapList("game.villagers");
        for (Map<?, ?> map : villagers) {
            if (map.containsKey("x") && map.containsKey("y") && map.containsKey("z")) {
                double x = ((Number) map.get("x")).doubleValue();
                double y = ((Number) map.get("y")).doubleValue();
                double z = ((Number) map.get("z")).doubleValue();
                locs.add(new Location(world, x, y, z));
            }
        }
        return locs;
    }

    // 动物区域
    public Location getAnimalRegionPos1() {
        return getLocation("game.animal-region.pos1");
    }

    public Location getAnimalRegionPos2() {
        return getLocation("game.animal-region.pos2");
    }

    private Location getLocation(String path) {
        World world = getWorld();
        if (world == null) return null;
        if (config.contains(path)) {
            double x = config.getDouble(path + ".x", 0);
            double y = config.getDouble(path + ".y", 64);
            double z = config.getDouble(path + ".z", 0);
            return new Location(world, x, y, z);
        }
        return null;
    }

    // 动物种类及数量
    public Map<String, Integer> getAnimals() {
        Map<String, Integer> animals = new LinkedHashMap<>();
        if (config.isConfigurationSection("game.animals")) {
            for (String key : config.getConfigurationSection("game.animals").getKeys(false)) {
                animals.put(key.toUpperCase(), config.getInt("game.animals." + key));
            }
        }
        return animals;
    }

    // 独立箱子配置
    public List<ChestConfig> getChestConfigs() {
        List<ChestConfig> list = new ArrayList<>();
        if (!config.isConfigurationSection("game.chests")) return list;

        for (String key : config.getConfigurationSection("game.chests").getKeys(false)) {
            String path = "game.chests." + key;
            Location loc = getLocation(path + ".location");
            if (loc == null) continue;

            // 读取朝向
            BlockFace facing = BlockFace.NORTH;
            String faceStr = config.getString(path + ".facing", "NORTH").toUpperCase();
            try {
                facing = BlockFace.valueOf(faceStr);
            } catch (IllegalArgumentException ignored) {}

            // 读取物品列表
            List<ItemStack> items = new ArrayList<>();
            List<String> rawItems = config.getStringList(path + ".items");
            for (String entry : rawItems) {
                String[] parts = entry.split(":");
                Material material = Material.matchMaterial(parts[0]);
                if (material == null) continue;
                short data = 0;
                if (parts.length > 1) {
                    try {
                        data = Short.parseShort(parts[1]);
                    } catch (NumberFormatException ignored) {}
                }
                items.add(new ItemStack(material, 1, data));
            }

            int min = config.getInt(path + ".amount-min", 1);
            int max = config.getInt(path + ".amount-max", 3);
            long delay = config.getLong(path + ".refill-delay", 20);

            list.add(new ChestConfig(loc, facing, items, min, max, delay));
        }
        return list;
    }

    // 任务
    public List<Task> getTasks() {
        List<Task> tasks = new ArrayList<>();
        if (config.isConfigurationSection("game.tasks")) {
            Set<String> keys = config.getConfigurationSection("game.tasks").getKeys(false);
            List<Integer> sortedKeys = new ArrayList<>();
            for (String key : keys) {
                try { sortedKeys.add(Integer.parseInt(key)); } catch (NumberFormatException ignored) {}
            }
            Collections.sort(sortedKeys);
            for (int key : sortedKeys) {
                String path = "game.tasks." + key;
                String matName = config.getString(path + ".material", "STONE");
                Material material = Material.matchMaterial(matName);
                int amount = config.getInt(path + ".amount", 1);
                String desc = ChatColor.translateAlternateColorCodes('&',
                        config.getString(path + ".description", "收集物品"));
                // 优先读取配置的中文名，否则用材料名
                String displayName = config.getString(path + ".display-name", null);
                if (displayName != null) {
                    displayName = ChatColor.translateAlternateColorCodes('&', displayName);
                }
                tasks.add(new Task(material, amount, desc, displayName));
            }
        }
        return tasks;
    }

    // 旁观者
    public Location getSpectatorSpawn() {
        return getLocationFrom("game.spectator.spawn");
    }

    public Location getSpectatorRegionCenter() {
        return getLocationFrom("game.spectator.region.center");
    }

    private Location getLocationFrom(String path) {
        World world = getWorld();
        if (world == null) return null;
        double x = config.getDouble(path + ".x", 0.0);
        double y = config.getDouble(path + ".y", 64.0);
        double z = config.getDouble(path + ".z", 0.0);
        float yaw = (float) config.getDouble(path + ".yaw", 0.0);
        float pitch = (float) config.getDouble(path + ".pitch", 0.0);
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
                config.getString("game.scoreboard.title", "&6厨神"));
    }

    public List<String> getScoreboardLines() {
        List<String> raw = config.getStringList("game.scoreboard.lines");
        List<String> colored = new ArrayList<>();
        for (String line : raw) {
            colored.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        return colored;
    }

    // 内部类：独立箱子配置
    public static class ChestConfig {
        public final Location location;
        public final BlockFace facing;
        public final List<ItemStack> items;
        public final int amountMin;
        public final int amountMax;
        public final long refillDelay;

        public ChestConfig(Location location, BlockFace facing, List<ItemStack> items, int amountMin, int amountMax, long refillDelay) {
            this.location = location;
            this.facing = facing;
            this.items = items;
            this.amountMin = amountMin;
            this.amountMax = amountMax;
            this.refillDelay = refillDelay;
        }
    }

    // 内部类：任务
    public static class Task {
        public final Material material;
        public final int amount;
        public final String description;
        public final String displayName;  // 可能为 null

        public Task(Material material, int amount, String description, String displayName) {
            this.material = material;
            this.amount = amount;
            this.description = description;
            this.displayName = displayName;
        }

        /**
         * 获取用于显示的中文名称
         */
        public String getDisplayName() {
            if (displayName != null && !displayName.isEmpty()) return displayName;
            // 默认映射
            return getChineseName(material);
        }

        private static String getChineseName(Material mat) {
            switch (mat) {
                case COOKED_RABBIT: return "烤兔肉";
                case COOKED_CHICKEN: return "烤鸡肉";
                case COOKED_FISH: return "烤鱼";
                case BREAD: return "面包";
                case CAKE: return "蛋糕";
                case GRILLED_PORK: return "烤猪排";
                case COOKED_BEEF: return "烤牛排";
                case PUMPKIN_PIE: return "南瓜派";
                default: return mat.toString().toLowerCase().replace('_', ' ');
            }
        }
    }
}