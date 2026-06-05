package cn.nanchi765.ncgame.manager;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.game.*;
import cn.nanchi765.ncgame.game.*;
import cn.nanchi765.ncgame.task.GamePlayingTask;
import cn.nanchi765.ncgame.task.GamePrepareTask;
import cn.nanchi765.ncgame.task.GameStartingTask;
import cn.nanchi765.ncgame.utils.LobbyUtils;
import cn.nanchi765.ncgame.utils.Utils;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

public class GameManager {

    @Getter
    public final Map<UUID, Integer> playerScores = new HashMap<>();
    private final NCGame plugin;
    // 剩余游戏
    @Getter
    private final List<String> pendingGames = new ArrayList<>();
    // 玩家
    @Getter
    private final List<Player> players = new ArrayList<>();
    // 存活玩家
    @Getter
    private final List<Player> alivePlayers = new ArrayList<>();
    @Getter
    // 热加入玩家匹配
    private final List<Player> lateJoinQueue = new ArrayList<>();
    private final File dataFolder;
    @Getter
    private IMinigame currentGame;
    @Getter
    @Setter
    private Status currentStatus;
    // 任务
    @Getter
    @Setter
    private GameStartingTask gameStartingTask = null;
    @Getter
    @Setter
    private GamePrepareTask gamePrepareTask = null;
    @Getter
    @Setter
    private GamePlayingTask gamePlayingTask = null;

    public GameManager(NCGame plugin) {
        this.plugin = plugin;
        dataFolder = plugin.getDataFolder();
        currentStatus = Status.WAITING;
    }

    public void addPlayer(Player player) {
        Utils.resetPlayer(player);
        players.add(player);
        alivePlayers.add(player);
    }

    public void removePlayer(Player player) {
        Utils.resetPlayer(player);
        players.remove(player);
        alivePlayers.remove(player);
    }

    public void addLatePlayer(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(currentGame.getConfig().getSpectatorSpawn());
        lateJoinQueue.add(player);
    }

    public void startNextGame() {
        if (pendingGames.isEmpty()) return;
        String nextGame = pendingGames.remove(0);

        players.addAll(lateJoinQueue);
        alivePlayers.addAll(players);
        lateJoinQueue.clear();

        if (players.size() < plugin.getLobbyConfig().getRequiredPlayers()) {
            Bukkit.broadcastMessage(ChatColor.RED + "人数不足，本轮游戏取消，返回大厅...");
            pendingGames.clear();
            onMinigameEnd();
            for (Player player : players) {
                LobbyUtils.teleportToLobby(player);
            }
            return;
        }

        switch (nextGame) {
            case "掘一死战":
                SnowSpleefConfig spleefConfig = new SnowSpleefConfig(new File(dataFolder, "snowspleef.yml"));
                currentGame = new SnowSpleefGame(plugin, spleefConfig);
                break;
            case "眼疾手快":
                QuickHandsConfig qhConfig = new QuickHandsConfig(new File(dataFolder, "quickhands.yml"));
                currentGame = new QuickHandsGame(plugin, qhConfig);
                break;
            case "极限雷区":
                ExtremeMinefieldConfig emConfig = new ExtremeMinefieldConfig(new File(dataFolder, "extrememinefield.yml"));
                currentGame = new ExtremeMinefieldGame(plugin, emConfig);
                break;
            case "大扫除":
                CleanupConfig cConfig = new CleanupConfig(new File(dataFolder, "cleanup.yml"));
                currentGame = new CleanupGame(plugin, cConfig);
                break;
            case "急速酷跑":
                SpeedrunConfig spConfig = new SpeedrunConfig(new File(dataFolder, "speedrun.yml"));
                currentGame = new SpeedrunGame(plugin, spConfig);
                break;
            case "一箭必杀":
                OneHitKillConfig ohkConfig = new OneHitKillConfig(new File(dataFolder, "onehitkill.yml"));
                currentGame = new OneHitKillGame(plugin, ohkConfig);
                break;
            case "厨神":
                ChefMConfig chefConfig = new ChefMConfig(new File(dataFolder, "chefm.yml"));
                currentGame = new ChefMGame(plugin, chefConfig);
                break;
            case "炸弹羊":
                BombSheepConfig bsConfig = new BombSheepConfig(new File(dataFolder, "bombsheep.yml"));
                currentGame = new BombSheepGame(plugin, bsConfig);
                break;
            case "击退战":
                KnockbackBattleConfig kbConfig = new KnockbackBattleConfig(new File(dataFolder, "knockbackbattle.yml"));
                currentGame = new KnockbackBattleGame(plugin, kbConfig);
                break;
            case "贪吃蛇":
                SnakeConfig snakeConfig = new SnakeConfig(new File(dataFolder, "snake.yml"));
                currentGame = new SnakeGame(plugin, snakeConfig);
                break;
            case "信仰方块":
                FaithBlockConfig fbConfig = new FaithBlockConfig(new File(dataFolder, "faithblock.yml"));
                currentGame = new FaithBlockGame(plugin, fbConfig);
                break;
            default:
                Bukkit.broadcastMessage(ChatColor.RED + "未知游戏: " + nextGame);
                startNextGame();
                return;
        }
        this.setGamePrepareTask(new GamePrepareTask(this, currentGame));
    }

    public void onMinigameEnd() {
        currentGame = null;
        currentStatus = Status.ENDED;
        if (pendingGames.isEmpty()) {
            broadcastRanking();
            resetAllGameWorlds();
            for (Player player : Bukkit.getOnlinePlayers()) {
                LobbyUtils.teleportToLobby(player);
            }
            currentStatus = Status.WAITING;
        } else {
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
        int count = 0;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            if (count >= 5) break;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                Bukkit.broadcastMessage(ChatColor.YELLOW + "第" + rank + "名：" + ChatColor.WHITE + player.getName() + " - " + entry.getValue() + "分");
            }
            rank++;
            count++;
        }
    }

    private void backupAllGameWorlds() {
        Set<String> worlds = new HashSet<>();
        worlds.add(new SnowSpleefConfig(new File(dataFolder, "snowspleef.yml")).getWorldName());
        worlds.add(new QuickHandsConfig(new File(dataFolder, "quickhands.yml")).getWorldName());
        worlds.add(new ExtremeMinefieldConfig(new File(dataFolder, "extrememinefield.yml")).getWorldName());
        worlds.add(new CleanupConfig(new File(dataFolder, "cleanup.yml")).getWorldName());
        worlds.add(new SpeedrunConfig(new File(dataFolder, "speedrun.yml")).getWorldName());
        worlds.add(new OneHitKillConfig(new File(dataFolder, "onehitkill.yml")).getWorldName());
        worlds.add(new ChefMConfig(new File(dataFolder, "chefm.yml")).getWorldName());
        worlds.add(new BombSheepConfig(new File(dataFolder, "bombsheep.yml")).getWorldName());
        worlds.add(new KnockbackBattleConfig(new File(dataFolder, "knockbackbattle.yml")).getWorldName());
        worlds.add(new SnakeConfig(new File(dataFolder, "snake.yml")).getWorldName());
        worlds.add(new FaithBlockConfig(new File(dataFolder, "faithblock.yml")).getWorldName());
        for (String world : worlds) NCGame.getInstance().getWorldResetManager().backupWorld(world);
    }

    private void resetAllGameWorlds() {
        Set<String> worlds = new HashSet<>();
        worlds.add(new SnowSpleefConfig(new File(dataFolder, "snowspleef.yml")).getWorldName());
        worlds.add(new QuickHandsConfig(new File(dataFolder, "quickhands.yml")).getWorldName());
        worlds.add(new ExtremeMinefieldConfig(new File(dataFolder, "extrememinefield.yml")).getWorldName());
        worlds.add(new CleanupConfig(new File(dataFolder, "cleanup.yml")).getWorldName());
        worlds.add(new SpeedrunConfig(new File(dataFolder, "speedrun.yml")).getWorldName());
        worlds.add(new OneHitKillConfig(new File(dataFolder, "onehitkill.yml")).getWorldName());
        worlds.add(new ChefMConfig(new File(dataFolder, "chefm.yml")).getWorldName());
        worlds.add(new BombSheepConfig(new File(dataFolder, "bombsheep.yml")).getWorldName());
        worlds.add(new KnockbackBattleConfig(new File(dataFolder, "knockbackbattle.yml")).getWorldName());
        worlds.add(new SnakeConfig(new File(dataFolder, "snake.yml")).getWorldName());
        worlds.add(new FaithBlockConfig(new File(dataFolder, "faithblock.yml")).getWorldName());
        for (String world : worlds) NCGame.getInstance().getWorldResetManager().resetWorld(world);
        Bukkit.getScheduler().runTaskLater(NCGame.getInstance(), this::backupAllGameWorlds, 100L);
    }
}
