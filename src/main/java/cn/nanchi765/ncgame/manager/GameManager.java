package cn.nanchi765.ncgame.manager;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.game.*;
import cn.nanchi765.ncgame.game.*;
import cn.nanchi765.ncgame.task.GamePlayingTask;
import cn.nanchi765.ncgame.task.GamePrepareTask;
import cn.nanchi765.ncgame.task.GameStartingTask;
import cn.nanchi765.ncgame.utils.GameUtils;
import cn.nanchi765.ncgame.utils.LobbyUtils;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;
import java.util.function.BiFunction;

public class GameManager {

    // ==================== 游戏注册表 ====================
    private static final Map<String, BiFunction<NCGame, File, IMinigame>> GAME_FACTORIES = new LinkedHashMap<>();
    static {
        GAME_FACTORIES.put("掘一死战",   (p, d) -> new SnowSpleefGame(p, new SnowSpleefConfig(new File(d, "snowspleef.yml"))));
        GAME_FACTORIES.put("眼疾手快",   (p, d) -> new QuickHandsGame(p, new QuickHandsConfig(new File(d, "quickhands.yml"))));
        GAME_FACTORIES.put("极限雷区",   (p, d) -> new ExtremeMinefieldGame(p, new ExtremeMinefieldConfig(new File(d, "extrememinefield.yml"))));
        GAME_FACTORIES.put("大扫除",     (p, d) -> new CleanupGame(p, new CleanupConfig(new File(d, "cleanup.yml"))));
        GAME_FACTORIES.put("急速酷跑",   (p, d) -> new SpeedrunGame(p, new SpeedrunConfig(new File(d, "speedrun.yml"))));
        GAME_FACTORIES.put("一箭必杀",   (p, d) -> new OneHitKillGame(p, new OneHitKillConfig(new File(d, "onehitkill.yml"))));
        GAME_FACTORIES.put("厨神",       (p, d) -> new ChefMGame(p, new ChefMConfig(new File(d, "chefm.yml"))));
        GAME_FACTORIES.put("炸弹羊",     (p, d) -> new BombSheepGame(p, new BombSheepConfig(new File(d, "bombsheep.yml"))));
        GAME_FACTORIES.put("击退战",     (p, d) -> new KnockbackBattleGame(p, new KnockbackBattleConfig(new File(d, "knockbackbattle.yml"))));
        GAME_FACTORIES.put("贪吃蛇",     (p, d) -> new SnakeGame(p, new SnakeConfig(new File(d, "snake.yml"))));
        GAME_FACTORIES.put("信仰方块",   (p, d) -> new FaithBlockGame(p, new FaithBlockConfig(new File(d, "faithblock.yml"))));
    }

    // ==================== 字段 ====================
    @Getter
    private final Map<UUID, Integer> playerScores = new HashMap<>();
    private final NCGame plugin;
    private final File dataFolder;
    private final Set<String> gameWorldNames;

    @Getter private final List<String> pendingGames = new ArrayList<>();
    @Getter private final List<Player> players = new ArrayList<>();
    @Getter private final List<Player> alivePlayers = new ArrayList<>();

    @Getter private IMinigame currentGame;
    @Getter @Setter private Status currentStatus;
    @Getter @Setter private GameStartingTask gameStartingTask = null;
    @Getter @Setter private GamePrepareTask gamePrepareTask = null;
    @Getter @Setter private GamePlayingTask gamePlayingTask = null;

    // ==================== 构造 ====================
    public GameManager(NCGame plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder();
        this.currentStatus = Status.WAITING;

        this.gameWorldNames = new HashSet<>();
        gameWorldNames.add(new SnowSpleefConfig(new File(dataFolder, "snowspleef.yml")).getWorldName());
        gameWorldNames.add(new QuickHandsConfig(new File(dataFolder, "quickhands.yml")).getWorldName());
        gameWorldNames.add(new ExtremeMinefieldConfig(new File(dataFolder, "extrememinefield.yml")).getWorldName());
        gameWorldNames.add(new CleanupConfig(new File(dataFolder, "cleanup.yml")).getWorldName());
        gameWorldNames.add(new SpeedrunConfig(new File(dataFolder, "speedrun.yml")).getWorldName());
        gameWorldNames.add(new OneHitKillConfig(new File(dataFolder, "onehitkill.yml")).getWorldName());
        gameWorldNames.add(new ChefMConfig(new File(dataFolder, "chefm.yml")).getWorldName());
        gameWorldNames.add(new BombSheepConfig(new File(dataFolder, "bombsheep.yml")).getWorldName());
        gameWorldNames.add(new KnockbackBattleConfig(new File(dataFolder, "knockbackbattle.yml")).getWorldName());
        gameWorldNames.add(new SnakeConfig(new File(dataFolder, "snake.yml")).getWorldName());
        gameWorldNames.add(new FaithBlockConfig(new File(dataFolder, "faithblock.yml")).getWorldName());
    }

    public void addPlayer(Player player) {
        GameUtils.resetPlayer(player);
        players.add(player);
        alivePlayers.add(player);
        playerScores.put(player.getUniqueId(), 0);
    }

    /**
     * 玩家断开连接
     */
    public void removePlayer(Player player) {
        players.remove(player);
        alivePlayers.remove(player);
        playerScores.remove(player.getUniqueId());

        // 游戏进行中且剩余玩家不足，提前结束
        if (currentGame != null && alivePlayers.size() <= 1 && gamePlayingTask != null) {
            gamePlayingTask.setCountdown(0);
        }

        if (currentGame != null && GameUtils.isAlive(player)) {
            eliminatePlayer(player);
        }
    }

    /**
     * 玩家在游戏中被淘汰（仍在服务器上）。
     * 负责状态重置 + 观战切换 + 游戏结束检查。
     */
    public boolean eliminatePlayer(Player player) {
        if (!alivePlayers.remove(player)) return false;

        GameUtils.resetPlayer(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(currentGame.getConfig().getSpectatorSpawn());

        if (alivePlayers.size() <= 1 && gamePlayingTask != null) {
            gamePlayingTask.setCountdown(0);
        }
        return true;
    }

    public void addLatePlayer(Player player) {
        addPlayer(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(currentGame.getConfig().getSpectatorSpawn());
        new BukkitRunnable() {
            @Override
            public void run() {
                player.setGameMode(GameMode.SPECTATOR);
                player.teleport(currentGame.getConfig().getSpectatorSpawn());
            }
        }.runTaskLater(plugin, 1L);
    }

    // ==================== 游戏流程 ====================
    public void startNextGame() {
        if (pendingGames.isEmpty()) return;
        String nextGame = pendingGames.remove(0);

        // 重建存活列表
        alivePlayers.clear();
        for (Player player : players) {
            GameUtils.resetPlayer(player);
            alivePlayers.add(player);
        }

        if (GameUtils.cancelIfNotEnoughPlayers()) {
            return;
        }

        BiFunction<NCGame, File, IMinigame> factory = GAME_FACTORIES.get(nextGame);
        if (factory == null) {
            Bukkit.broadcastMessage(ChatColor.RED + "未知游戏: " + nextGame);
            startNextGame();
            return;
        }
        currentGame = factory.apply(plugin, dataFolder);

        if (gamePrepareTask == null) {
            this.setGamePrepareTask(new GamePrepareTask(this, currentGame));
        }
    }

    public void onMinigameEnd() {
        Bukkit.getLogger().info(currentGame + " 结束");

        if (gamePlayingTask != null) {
            gamePlayingTask.cancel();
            gamePlayingTask = null;
            return;
        }

        currentGame = null;
        currentStatus = Status.ENDED;

        if (pendingGames.isEmpty()) {
            // 整轮结束 — 先播报，再清空
            broadcastRanking();
            playerScores.clear();
            resetAllGameWorlds();

            for (Player player : Bukkit.getOnlinePlayers()) {
                LobbyUtils.teleportToLobby(player);
            }
            currentStatus = Status.WAITING;
            if (gameStartingTask == null
                    && players.size() >= NCGame.getInstance().getLobbyConfig().getMinPlayers()) {
                gameStartingTask = new GameStartingTask(this);
            }
        } else {
            startNextGame();
        }
    }

    // ==================== 积分播报 ====================
    private void broadcastRanking() {
        if (playerScores.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "本轮无积分记录。");
            return;
        }

        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(playerScores.entrySet());
        sorted.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());

        Bukkit.broadcastMessage(ChatColor.GOLD + "========== 总积分排行榜 ==========");
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            if (rank > 5) break;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                Bukkit.broadcastMessage(
                        ChatColor.YELLOW + "第" + rank + "名："
                                + ChatColor.WHITE + player.getName()
                                + " - " + entry.getValue() + "分"
                );
            }
            rank++;
        }
    }

    // ==================== 地图重置 ====================
    private void backupAllGameWorlds() {
        for (String world : gameWorldNames) {
            NCGame.getInstance().getWorldResetManager().backupWorld(world);
        }
    }

    private void resetAllGameWorlds() {
        for (String world : gameWorldNames) {
            NCGame.getInstance().getWorldResetManager().resetWorld(world);
        }
        Bukkit.getScheduler().runTaskLater(NCGame.getInstance(), this::backupAllGameWorlds, 100L);
    }
}
