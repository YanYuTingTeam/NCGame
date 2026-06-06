package cn.nanchi765.ncgame.manager;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.game.*;
import cn.nanchi765.ncgame.game.*;
import cn.nanchi765.ncgame.task.GamePlayingTask;
import cn.nanchi765.ncgame.task.GamePrepareTask;
import cn.nanchi765.ncgame.task.GameStartingTask;
import cn.nanchi765.ncgame.utils.LobbyUtils;
import cn.nanchi765.ncgame.utils.GameUtils;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.function.BiFunction;

public class GameManager {

    // ==================== 游戏注册表（消除 switch-case）====================
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
    public final Map<UUID, Integer> playerScores = new HashMap<>();
    private final NCGame plugin;
    private final File dataFolder;
    private final Set<String> gameWorldNames;

    @Getter private final List<String> pendingGames = new ArrayList<>();
    @Getter private final List<Player> players = new ArrayList<>();
    @Getter private final List<Player> alivePlayers = new ArrayList<>();
    @Getter private final List<Player> lateJoinQueue = new ArrayList<>();

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

        // 一次性收集所有游戏地图名，backup/reset 共用
        this.gameWorldNames = Set.of(
                new SnowSpleefConfig(new File(dataFolder, "snowspleef.yml")).getWorldName(),
                new QuickHandsConfig(new File(dataFolder, "quickhands.yml")).getWorldName(),
                new ExtremeMinefieldConfig(new File(dataFolder, "extrememinefield.yml")).getWorldName(),
                new CleanupConfig(new File(dataFolder, "cleanup.yml")).getWorldName(),
                new SpeedrunConfig(new File(dataFolder, "speedrun.yml")).getWorldName(),
                new OneHitKillConfig(new File(dataFolder, "onehitkill.yml")).getWorldName(),
                new ChefMConfig(new File(dataFolder, "chefm.yml")).getWorldName(),
                new BombSheepConfig(new File(dataFolder, "bombsheep.yml")).getWorldName(),
                new KnockbackBattleConfig(new File(dataFolder, "knockbackbattle.yml")).getWorldName(),
                new SnakeConfig(new File(dataFolder, "snake.yml")).getWorldName(),
                new FaithBlockConfig(new File(dataFolder, "faithblock.yml")).getWorldName()
        );
    }

    // ==================== 玩家管理 ====================
    public void addPlayer(Player player) {
        GameUtils.resetPlayer(player);
        players.add(player);
        alivePlayers.add(player);
    }

    public void removePlayer(Player player) {
        GameUtils.resetPlayer(player);
        players.remove(player);
        alivePlayers.remove(player);
    }

    public boolean eliminatePlayer(Player player) {
        if (!alivePlayers.remove(player)) return false;

        GameUtils.resetPlayer(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(currentGame.getConfig().getSpectatorSpawn());

        if (alivePlayers.size() <= 1) {
            gamePlayingTask.setCountdown(0);
        }
        return true;
    }

    public void addLatePlayer(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(currentGame.getConfig().getSpectatorSpawn());
        lateJoinQueue.add(player);
    }

    // ==================== 游戏流程 ====================
    public void startNextGame() {
        if (pendingGames.isEmpty()) return;
        String nextGame = pendingGames.remove(0);

        // 先合并迟到玩家，再统一重建存活列表
        players.addAll(lateJoinQueue);
        lateJoinQueue.clear();
        alivePlayers.clear();
        alivePlayers.addAll(players);

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
            broadcastRanking();
            resetAllGameWorlds();
            for (Player player : Bukkit.getOnlinePlayers()) {
                LobbyUtils.teleportToLobby(player);
            }
            currentStatus = Status.WAITING;
            if (gameStartingTask == null
                    && players.size() >= NCGame.getInstance().getLobbyConfig().getMinPlayers()) {
                gameStartingTask = new GameStartingTask(this);
            }
        } else if (gamePrepareTask == null) {
            startNextGame();
        }
    }

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
