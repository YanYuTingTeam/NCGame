package cn.nanchi765.ncgame.game;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.game.SnakeConfig;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.utils.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class SnakeGame implements Listener, IMinigame {

    private static final String GAME_NAME = "贪吃蛇";
    private static final int[] SCORE_TABLE = {10, 7, 5, 1};
    private static final int LEADERBOARD_MAX_DISPLAY = 4;

    private final JavaPlugin plugin;
    @Getter
    private final SnakeConfig config;
    private final GameManager gameManager;

    private final Set<String> vanishBlockSet;
    private BukkitTask vanishTask;

    /**
     * 掉落淘汰的 Y 坐标阈值
     */
    private final double deathY;

    public SnakeGame(JavaPlugin plugin, SnakeConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.gameManager = NCGame.getInstance().getGameManager();
        this.vanishBlockSet = new HashSet<>(config.getVanishBlocks());
        this.deathY = config.getDeathY() != 0 ? config.getDeathY() : 0;
    }

    // ======================== 生命周期 ========================

    @Override
    public void prepare() {
        Location spawn = config.getSpawnLocation();
        if (spawn == null || spawn.getWorld() == null) {
            throw new IllegalStateException(GAME_NAME + " 出生点未配置或世界不存在");
        }

        List<Player> players = new ArrayList<>(gameManager.getAlivePlayers());

        for (Player p : players) {
            p.teleport(spawn);
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void start() {
        placeBarriers();

        float speed = (float) Math.min(1.0, 0.2 * config.getTimer());
        for (Player p : gameManager.getPlayers()) {
            p.setGameMode(GameMode.SURVIVAL);
            p.setWalkSpeed(speed);
        }

        int interval = config.getVanishInterval();
        vanishTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!Utils.isGameActive(SnakeGame.this)) {
                    cancel();
                    return;
                }
                for (Player p : gameManager.getAlivePlayers()) {
                    Block below = p.getLocation().getBlock().getRelative(0, -1, 0);
                    if (vanishBlockSet.contains(below.getType().name())) {
                        below.setType(Material.AIR);
                    }
                }
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    @Override
    public void end() {
        if (vanishTask != null) {
            vanishTask.cancel();
            vanishTask = null;
        }

        for (Player p : gameManager.getPlayers()) {
            p.setWalkSpeed(0.2f);
        }

        Map<Player, Integer> rankings = buildRankings();
        Map<Player, Integer> scoreMap = calculateScores(rankings);

        applyScores(scoreMap);
        notifyResults(rankings, scoreMap);
        broadcastLeaderboard(rankings, scoreMap);

        HandlerList.unregisterAll(this);
    }

    @Override
    public void eliminatePlayer(Player player) {
        List<Player> alivePlayers = gameManager.getAlivePlayers();
        if (!alivePlayers.remove(player)) return;

        Utils.resetPlayer(player);
        player.setGameMode(GameMode.SPECTATOR);

        Location spectatorSpawn = config.getSpectatorSpawn();
        if (spectatorSpawn != null) {
            player.teleport(spectatorSpawn);
        }

        String title = ChatColor.AQUA + player.getName() + " 掉下去了";
        for (Player p : gameManager.getPlayers()) {
            p.sendTitle("", title, 0, 40, 10);
        }

        if (alivePlayers.isEmpty()) {
            end();
        }
    }

    // ======================== 屏障放置 ========================

    private void placeBarriers() {
        if (!config.isBarrierEnabled()) return;

        Location pos1 = config.getBarrierPos1();
        Location pos2 = config.getBarrierPos2();
        if (pos1 == null || pos2 == null || pos1.getWorld() == null) {
            plugin.getLogger().warning(GAME_NAME + " 屏障坐标未配置或世界不存在");
            return;
        }

        World world = pos1.getWorld();
        int y = config.getBarrierY();
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block block = world.getBlockAt(x, y, z);
                if (block.getType() == Material.AIR) {
                    block.setType(Material.BARRIER);
                    count++;
                }
            }
        }
        plugin.getLogger().info(GAME_NAME + " 屏障已放置 " + count + " 个");
    }

    // ======================== 结算辅助 ========================

    private Map<Player, Integer> buildRankings() {
        Map<Player, Integer> rankings = new LinkedHashMap<>();
        List<Player> alivePlayers = gameManager.getAlivePlayers();

        int nextRank = alivePlayers.isEmpty() ? 1 : 2;
        for (Player p : alivePlayers) {
            rankings.put(p, 1);
        }

        List<Player> eliminated = getEliminatedPlayers();
        Collections.reverse(eliminated);
        for (Player p : eliminated) {
            rankings.put(p, nextRank++);
        }

        return rankings;
    }

    private List<Player> getEliminatedPlayers() {
        Set<Player> aliveSet = new HashSet<>(gameManager.getAlivePlayers());
        List<Player> eliminated = new ArrayList<>();
        for (Player p : gameManager.getPlayers()) {
            if (!aliveSet.contains(p)) {
                eliminated.add(p);
            }
        }
        return eliminated;
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
            int rank = rankings.getOrDefault(p, gameManager.getPlayers().size());
            int add = scoreMap.getOrDefault(p, 0);
            String suffix = add > 0 ? " (+" + add + "积分)" : "";
            p.sendMessage(ChatColor.GREEN + "你在本回合小游戏获得第 " + rank + " 名！" + suffix);
        }
    }

    private void broadcastLeaderboard(Map<Player, Integer> rankings, Map<Player, Integer> scoreMap) {
        Bukkit.broadcastMessage(ChatColor.GOLD + "===== " + GAME_NAME + " 排行榜 =====");

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

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!Utils.isGameActive(this)) return;

        Player player = event.getPlayer();
        if (!Utils.isAlive(player)) return;

        if (player.getLocation().getY() < deathY) {
            eliminatePlayer(player);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!Utils.isGameActive(this)) return;
        if (!(event.getEntity() instanceof Player)) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            Player player = (Player) event.getEntity();
            if (Utils.isAlive(player)) {
                eliminatePlayer(player);
            }
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (Utils.isGameActive(this)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (Utils.isGameActive(this)) {
            event.setCancelled(true);
        }
    }
}
