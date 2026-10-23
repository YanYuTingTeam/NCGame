package cn.nanchi765.ncgame.manager;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDataManager {

    private final JavaPlugin plugin;
    private final String storageType;
    private File dataFile;
    private FileConfiguration dataConfig;
    private HikariDataSource hikari;
    @Getter
    private Map<UUID, Integer> playerScores = new HashMap<>();
    private boolean yamlDirty = false;
    private boolean flushTaskQueued = false;

    public PlayerDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration mainConfig = plugin.getConfig();
        this.storageType = mainConfig.getString("database.type", "yaml").toLowerCase();

        if ("yaml".equals(storageType)) {
            initYaml();
        } else if ("mysql".equals(storageType)) {
            initMysql(mainConfig);
            createTableIfNotExists();
        } else {
            plugin.getLogger().warning("未知的数据库类型: " + storageType + "，将使用 YAML 后备");
            initYaml();
        }
    }

    // ==================== YAML 初始化 ====================
    private void initYaml() {
        File databaseFolder = new File(plugin.getDataFolder(), "database");
        if (!databaseFolder.exists()) {
            databaseFolder.mkdirs();
        }
        dataFile = new File(databaseFolder, "playerdata.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("无法创建 playerdata.yml");
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    // ==================== MySQL 初始化 ====================
    private void initMysql(FileConfiguration mainConfig) {
        String host = mainConfig.getString("database.mysql.host", "localhost");
        int port = mainConfig.getInt("database.mysql.port", 3306);
        String database = mainConfig.getString("database.mysql.database", "ncgame");
        String username = mainConfig.getString("database.mysql.username", "root");
        String password = mainConfig.getString("database.mysql.password", "password");
        int maxPool = mainConfig.getInt("database.mysql.pool.maximum-pool-size", 10);
        int minIdle = mainConfig.getInt("database.mysql.pool.minimum-idle", 2);
        int connTimeout = mainConfig.getInt("database.mysql.pool.connection-timeout", 5000);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&characterEncoding=utf8");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPool);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connTimeout);
        config.setDriverClassName("com.mysql.jdbc.Driver");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        hikari = new HikariDataSource(config);
        plugin.getLogger().info("MySQL 连接池初始化成功");
    }

    private void createTableIfNotExists() {
        String table = getTableName();
        String sql = "CREATE TABLE IF NOT EXISTS " + table + " (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "name VARCHAR(36) NOT NULL, " +
                "score INT NOT NULL DEFAULT 0, " +
                "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
            plugin.getLogger().info("MySQL 数据表 " + table + " 已确认存在");
        } catch (SQLException e) {
            plugin.getLogger().severe("创建 MySQL 表失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getTableName() {
        return plugin.getConfig().getString("database.mysql.table", "player_scores");
    }

    // ==================== 公共存储接口 ====================
    public void savePlayerScore(Player player, int score) {
        savePlayerScore(player.getUniqueId(), player.getName(), score);
    }

    // 保留旧接口（兼容无 Player 对象时）
    public void savePlayerScore(UUID uuid, int score) {
        savePlayerScore(uuid, getPlayerName(uuid), score);
    }

    private void savePlayerScore(UUID uuid, String name, int score) {
        if ("yaml".equals(storageType)) {
            synchronized (dataConfig) {
                dataConfig.set("players." + uuid.toString() + ".name", name);
                dataConfig.set("players." + uuid + ".score", score);
            }
            yamlDirty = true;
            scheduleFlush();
        } else if ("mysql".equals(storageType)) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> saveToMysql(uuid, name, score));
        }
    }

    // ==================== YAML 合并落盘 ====================
    private void scheduleFlush() {
        if (flushTaskQueued) return;
        flushTaskQueued = true;
        plugin.getServer().getScheduler().runTaskLater(plugin, this::flushYaml, 600L);
    }

    private void flushYaml() {
        flushTaskQueued = false;
        if (!yamlDirty) return;
        yamlDirty = false;
        String serialized;
        synchronized (dataConfig) {
            serialized = dataConfig.saveToString();
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> writeYamlFile(serialized));
    }

    private void writeYamlFile(String data) {
        try {
            java.nio.file.Files.write(dataFile.toPath(), data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存玩家数据到 YAML");
        }
    }

    public int getPlayerScore(UUID uuid) {
        if ("yaml".equals(storageType)) {
            return dataConfig.getInt("players." + uuid.toString() + ".score", 0);
        } else if ("mysql".equals(storageType)) {
            return loadScoreFromMysql(uuid);
        }
        return 0;
    }

    public String getPlayerName(UUID uuid) {
        if ("yaml".equals(storageType)) {
            return dataConfig.getString("players." + uuid.toString() + ".name", "Unknown");
        }
        // MySQL 暂不单独实现名称查询，可从内存或离线获取
        return "Unknown";
    }

    public Map<UUID, Integer> loadAllScores() {
        Map<UUID, Integer> scores = new HashMap<>();
        if ("yaml".equals(storageType)) {
            if (dataConfig.contains("players")) {
                for (String key : dataConfig.getConfigurationSection("players").getKeys(false)) {
                    UUID uuid = UUID.fromString(key);
                    int score = dataConfig.getInt("players." + key + ".score", 0);
                    scores.put(uuid, score);
                }
            }
        } else if ("mysql".equals(storageType)) {
            loadAllFromMysql(scores);
        }
        return scores;
    }

    // ==================== MySQL 操作 ====================
    private void saveToMysql(UUID uuid, String name, int score) {
        String table = getTableName();
        String sql = "INSERT INTO " + table + " (uuid, name, score) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE name = VALUES(name), score = VALUES(score);";
        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setInt(3, score);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("保存玩家积分失败 (" + uuid + "): " + e.getMessage());
        }
    }

    private int loadScoreFromMysql(UUID uuid) {
        String table = getTableName();
        String sql = "SELECT score FROM " + table + " WHERE uuid = ?;";
        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("score");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("加载玩家积分失败 (" + uuid + "): " + e.getMessage());
        }
        return 0;
    }

    private void loadAllFromMysql(Map<UUID, Integer> scores) {
        String table = getTableName();
        String sql = "SELECT uuid, score FROM " + table + ";";
        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                int score = rs.getInt("score");
                scores.put(uuid, score);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("批量加载玩家积分失败: " + e.getMessage());
        }
    }

    // 关闭连接池
    public void close() {
        if ("yaml".equals(storageType) && yamlDirty) {
            yamlDirty = false;
            String serialized;
            synchronized (dataConfig) {
                serialized = dataConfig.saveToString();
            }
            writeYamlFile(serialized);
        }
        if (hikari != null && !hikari.isClosed()) {
            hikari.close();
            plugin.getLogger().info("MySQL 连接池已关闭");
        }
    }
}