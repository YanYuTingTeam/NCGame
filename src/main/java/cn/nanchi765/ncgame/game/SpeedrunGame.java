package cn.nanchi765.ncgame.game;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.game.SpeedrunConfig;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.utils.GameUtils;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class SpeedrunGame implements Listener, IMinigame {

    private static final String GAME_NAME = "急速酷跑";
    private static final int[] SCORE_TABLE = {10, 7, 5, 1};
    private static final int LEADERBOARD_MAX_DISPLAY = 4;

    private final JavaPlugin plugin;
    @Getter
    private final SpeedrunConfig config;
    private final GameManager gameManager;

    /**
     * 用 Set 加速 contains 判定
     */
    private final Set<Player> finishedSet = new LinkedHashSet<>();
    /**
     * 每位玩家的出生点，用于掉落回溯和准备阶段锁定
     */
    private final Map<Player, Location> spawnPoints = new HashMap<>();

    public SpeedrunGame(JavaPlugin plugin, SpeedrunConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.gameManager = NCGame.getInstance().getGameManager();
    }

    // ======================== 生命周期 ========================

    /**
     * 判断是否发生了方块级位移（忽略同方块内的头部转动）
     */
    private static boolean hasMovedBlock(PlayerMoveEvent event) {
        return event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ();
    }

    @Override
    public void prepare() {
        List<Location> spawns = new ArrayList<>(config.getSpawnLocations());
        if (spawns.isEmpty()) {
            throw new IllegalStateException(GAME_NAME + " 未配置出生点");
        }
        Collections.shuffle(spawns);

        List<Player> players = new ArrayList<>(gameManager.getAlivePlayers());
        Collections.shuffle(players);

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            Location loc = spawns.get(i % spawns.size());
            spawnPoints.put(p, loc.clone());
            p.teleport(loc);
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void start() {
        for (Player p : gameManager.getAlivePlayers()) {
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    @Override
    public void end() {
        Map<Player, Integer> rankings = buildRankings();
        Map<Player, Integer> scoreMap = calculateScores(rankings);

        applyScores(scoreMap);
        notifyResults(rankings, scoreMap);
        broadcastLeaderboard(rankings, scoreMap);

        HandlerList.unregisterAll(this);

        
    }

    // ======================== 结算辅助 ========================

    @Override
    public void eliminatePlayer(Player player) {
        gameManager.eliminatePlayer(player);
    }

    private Map<Player, Integer> buildRankings() {
        Map<Player, Integer> rankings = new LinkedHashMap<>();
        int rank = 1;
        for (Player p : finishedSet) {
            rankings.put(p, rank++);
        }
        return rankings;
    }

    private Map<Player, Integer> calculateScores(Map<Player, Integer> rankings) {
        Map<Player, Integer> scores = new HashMap<>();
        rankings.forEach((player, rank) -> {
            int index = rank - 1;
            scores.put(player, index < SCORE_TABLE.length ? SCORE_TABLE[index] : 0);
        });
        return scores;
    }

    private void applyScores(Map<Player, Integer> scoreMap) {
        if (!(plugin instanceof NCGame)) return;
        NCGame ncGame = (NCGame) plugin;
        scoreMap.forEach(ncGame::addScore);
    }

    private void notifyResults(Map<Player, Integer> rankings, Map<Player, Integer> scoreMap) {
        for (Player p : gameManager.getPlayers()) {
            if (rankings.containsKey(p)) {
                int rank = rankings.get(p);
                int add = scoreMap.getOrDefault(p, 0);
                String suffix = add > 0 ? " (+" + add + "积分)" : "";
                p.sendMessage(ChatColor.GREEN + "你在本回合小游戏获得第 " + rank + " 名！" + suffix);
            } else {
                p.sendMessage(ChatColor.RED + "你没有完成本轮游戏，无排名。");
            }
        }
    }

    // ======================== 到达判定 ========================

    private void broadcastLeaderboard(Map<Player, Integer> rankings, Map<Player, Integer> scoreMap) {
        Bukkit.broadcastMessage(ChatColor.GOLD + "===== " + GAME_NAME + " 排行榜 =====");

        if (rankings.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.RED + "无人完成本轮游戏！");
            return;
        }

        int count = 0;
        for (Map.Entry<Player, Integer> entry : rankings.entrySet()) {
            if (count >= LEADERBOARD_MAX_DISPLAY) break;
            int add = scoreMap.getOrDefault(entry.getKey(), 0);
            Bukkit.broadcastMessage(
                    ChatColor.YELLOW + "第" + entry.getValue() + "名："
                            + ChatColor.WHITE + entry.getKey().getName() + " (+" + add + "积分)");
            count++;
        }
    }

    // ======================== 事件监听 ========================

    private void checkFinish(Player player) {
        if (finishedSet.contains(player)) return;
        if (!config.isInWinRegion(player.getLocation())) return;

        finishedSet.add(player);
        player.setGameMode(GameMode.SPECTATOR);

        Location spectatorSpawn = config.getSpectatorSpawn();
        if (spectatorSpawn != null) {
            player.teleport(spectatorSpawn);
        }

        String msg = ChatColor.AQUA + player.getName() + " 到达终点！（第 " + finishedSet.size() + " 名）";
        for (Player p : gameManager.getPlayers()) {
            p.sendTitle("", msg, 10, 70, 10);
        }

        if (finishedSet.size() == gameManager.getAlivePlayers().size()) {
            gameManager.getGamePlayingTask().setCountdown(0);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // 准备阶段：锁定玩家在出生点（仅检查方块级位移，忽略头部转动）
        if (!GameUtils.isGameActive(this)) {
            Location spawn = spawnPoints.get(player);
            if (spawn != null && hasMovedBlock(event)) {
                player.teleport(spawn);
            }
            return;
        }

        if (!GameUtils.isAlive(player)) return;

        // 掉落回溯
        if (player.getLocation().getY() < 0) {
            Location spawn = spawnPoints.get(player);
            if (spawn != null) {
                player.teleport(spawn);
                player.sendMessage(ChatColor.RED + "你掉下去了，已传送回出生点！");
            }
            return;
        }

        // 胜利区域检测
        checkFinish(player);
    }

    @EventHandler
    public void onBlock(BlockBreakEvent event) {
        if (!GameUtils.isGameActive(this)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!GameUtils.isGameActive(this)) return;
        if (!(event.getEntity() instanceof Player)) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            Player player = (Player) event.getEntity();
            if (GameUtils.isAlive(player)) {
                Location spawn = spawnPoints.get(player);
                if (spawn != null) {
                    player.teleport(spawn);
                    player.sendMessage(ChatColor.RED + "你掉下去了，已传送回出生点！");
                }
            }
        }
        event.setCancelled(true);
    }
}
