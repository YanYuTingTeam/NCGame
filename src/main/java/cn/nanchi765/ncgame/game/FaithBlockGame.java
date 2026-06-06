package cn.nanchi765.ncgame.game;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.game.FaithBlockConfig;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.utils.GameUtils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class FaithBlockGame implements Listener, IMinigame {

    // ========== 常量 ==========
    private static final byte COLOR_WHITE = 0;
    private static final byte COLOR_YELLOW = 4;
    private static final byte COLOR_ORANGE = 1;
    private static final byte COLOR_RED = 14;
    private static final byte COLOR_AIR = -1;

    // 积分表：排名 -> 积分
    private static final int[] SCORE_TABLE = {0, 10, 7, 5, 1};

    // ========== 依赖 & 状态 ==========
    private final JavaPlugin plugin;
    @Getter
    private final FaithBlockConfig config;
    @Getter
    private final GameManager gameManager;
    private final Random random = new Random();

    private BukkitTask changeTask;

    // 缓存区域边界，避免每次事件/定时任务重复计算
    private World regionWorld;
    private int minX, maxX, minY, maxY, minZ, maxZ;

    public FaithBlockGame(JavaPlugin plugin, FaithBlockConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.gameManager = NCGame.getInstance().getGameManager();
    }

    // ========== 生命周期 ==========

    @Override
    public void prepare() {
        Location spawn = config.getSpawnLocation();
        if (spawn == null) {
            plugin.getLogger().severe("信仰方块出生点未配置，游戏取消");
            return;
        }
        for (Player p : gameManager.getAlivePlayers()) {
            p.teleport(spawn);
            p.setGameMode(GameMode.SURVIVAL);
        }

        cacheRegionBounds();
        fillRegionWithWool(COLOR_WHITE);

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void start() {
        int interval = config.getChangeInterval();
        changeTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (GameUtils.isGameActive(FaithBlockGame.this)) {
                    doRandomColorChange();
                }
            }
        }.runTaskTimer(plugin, 0L, interval);
    }

    @Override
    public void end() {
        cancelChangeTask();
        broadcastRanking();
        HandlerList.unregisterAll(this);
    }

    // ========== 区域操作 ==========

    /**
     * 缓存区域边界坐标，仅在 prepare 时计算一次
     */
    private void cacheRegionBounds() {
        Location pos1 = config.getRegionPos1();
        Location pos2 = config.getRegionPos2();
        if (pos1 == null || pos2 == null) return;

        this.regionWorld = pos1.getWorld();
        this.minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        this.maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        this.minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        this.maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        this.minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        this.maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
    }

    /**
     * 将区域内所有非空气方块填充为指定颜色的羊毛
     */
    private void fillRegionWithWool(byte color) {
        if (regionWorld == null) return;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = regionWorld.getBlockAt(x, y, z);
                    if (block.getType() != Material.AIR) {
                        block.setType(Material.WOOL);
                        block.setData(color);
                    }
                }
            }
        }
    }

    /**
     * 随机选取方块并尝试升级颜色
     */
    private void doRandomColorChange() {
        if (regionWorld == null) return;

        int rangeX = maxX - minX + 1;
        int rangeY = maxY - minY + 1;
        int rangeZ = maxZ - minZ + 1;

        int target = config.getChangeAmountMin()
                + random.nextInt(config.getChangeAmountMax() - config.getChangeAmountMin() + 1);

        int changed = 0;
        int maxAttempts = target * 10;

        for (int attempt = 0; changed < target && attempt < maxAttempts; attempt++) {
            int x = minX + random.nextInt(rangeX);
            int y = minY + random.nextInt(rangeY);
            int z = minZ + random.nextInt(rangeZ);

            if (!regionWorld.isChunkLoaded(x >> 4, z >> 4)) continue;

            Block block = regionWorld.getBlockAt(x, y, z);
            if (block.getType() != Material.WOOL) continue;

            byte newData = tryUpgradeColor(block.getData());
            if (newData == block.getData()) continue;

            if (newData == COLOR_AIR) {
                block.setType(Material.AIR);
            } else {
                block.setData(newData);
            }
            changed++;
        }
    }

    /**
     * 根据当前颜色和配置概率决定是否升级。
     * 返回新颜色，未升级则返回原颜色。
     */
    private byte tryUpgradeColor(byte current) {
        int prob = random.nextInt(100) + 1;

        if (current == COLOR_WHITE && prob <= config.getProbWhiteToYellow()) return COLOR_YELLOW;
        if (current == COLOR_YELLOW && prob <= config.getProbYellowToOrange()) return COLOR_ORANGE;
        if (current == COLOR_ORANGE && prob <= config.getProbOrangeToRed()) return COLOR_RED;
        if (current == COLOR_RED && prob <= config.getProbRedToAir()) return COLOR_AIR;

        return current;
    }

    // ========== 排名 & 积分 ==========

    /**
     * 计算排名、发放积分、广播排行榜
     */
    private void broadcastRanking() {
        List<Player> alivePlayers = new ArrayList<>(gameManager.getAlivePlayers());
        List<Player> allPlayers = gameManager.getPlayers();

        // 收集被淘汰的玩家
        Set<Player> aliveSet = new HashSet<>(alivePlayers);
        List<Player> eliminated = new ArrayList<>();
        for (Player p : allPlayers) {
            if (!aliveSet.contains(p)) {
                eliminated.add(p);
            }
        }

        // 构建排名：存活玩家并列第1，被淘汰玩家按淘汰顺序倒序排列
        Map<Player, Integer> playerRank = new LinkedHashMap<>();
        int rank = 1;

        for (Player p : alivePlayers) {
            playerRank.put(p, rank);
        }
        if (!alivePlayers.isEmpty()) {
            rank = 2;
        }

        // 最后被淘汰的排名靠前，最早被淘汰的排名靠后
        Collections.reverse(eliminated);
        for (Player p : eliminated) {
            playerRank.put(p, rank++);
        }

        // 计算积分并发放
        Map<Player, Integer> scoreAdd = new HashMap<>();
        playerRank.forEach((p, r) -> scoreAdd.put(p, getScoreByRank(r)));

        if (plugin instanceof NCGame) {
            NCGame ncGame = (NCGame) plugin;
            scoreAdd.forEach(ncGame::addScore);
        }

        // 个人消息
        for (Player p : allPlayers) {
            int myRank = playerRank.getOrDefault(p, allPlayers.size());
            int myScore = scoreAdd.getOrDefault(p, 0);
            String suffix = myScore > 0 ? " (+" + myScore + "积分)" : "";
            p.sendMessage(ChatColor.GREEN + "你在本回合小游戏获得第 " + myRank + " 名！" + suffix);
        }

        // 广播前4名
        Bukkit.broadcastMessage(ChatColor.GOLD + "===== 信仰方块 排行榜 =====");
        int displayed = 0;
        for (Map.Entry<Player, Integer> entry : playerRank.entrySet()) {
            if (displayed >= 4) break;
            Player p = entry.getKey();
            int r = entry.getValue();
            int add = scoreAdd.getOrDefault(p, 0);
            Bukkit.broadcastMessage(
                    ChatColor.YELLOW + "第" + r + "名："
                            + ChatColor.WHITE + p.getName() + " (+" + add + "积分)");
            displayed++;
        }
    }

    private int getScoreByRank(int rank) {
        if (rank >= 1 && rank < SCORE_TABLE.length) {
            return SCORE_TABLE[rank];
        }
        return 0;
    }

    // ========== 事件监听 ==========

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!GameUtils.isGameActive(this)) return;

        Player player = event.getPlayer();
        if (player.getLocation().getY() < config.getDeathY()) {
            eliminatePlayer(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!GameUtils.isGameActive(this)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!GameUtils.isGameActive(this)) return;
        event.setCancelled(true);
    }

    // ========== 淘汰逻辑 ==========

    public void eliminatePlayer(Player player) {
        for (Player p : gameManager.getPlayers()) {
            p.sendTitle("", ChatColor.AQUA + player.getName() + " 被淘汰了", 0, 40, 10);
        }
        gameManager.eliminatePlayer(player);
    }

    // ========== 工具方法 ==========

    private void cancelChangeTask() {
        if (changeTask != null) {
            changeTask.cancel();
            changeTask = null;
        }
    }
}
