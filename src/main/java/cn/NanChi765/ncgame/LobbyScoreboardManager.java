package cn.NanChi765.ncgame;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.List;

public class LobbyScoreboardManager {

    private final JavaPlugin plugin;
    private final LobbyConfig lobbyConfig;
    private final GameCountdownManager countdownManager;
    private BukkitRunnable updateTask;

    public LobbyScoreboardManager(JavaPlugin plugin, LobbyConfig lobbyConfig,
                                  GameCountdownManager countdownManager) {
        this.plugin = plugin;
        this.lobbyConfig = lobbyConfig;
        this.countdownManager = countdownManager;
        startUpdating();
    }

    private void startUpdating() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateScoreboards();
            }
        };
        updateTask.runTaskTimer(plugin, 0L, 20L);
    }

    public void stopUpdating() {
        if (updateTask != null) updateTask.cancel();
    }

    private void updateScoreboards() {
        if (!lobbyConfig.isScoreboardEnabled()) return;

        int currentPlayers = Bukkit.getOnlinePlayers().size();
        int requiredPlayers = lobbyConfig.getRequiredPlayers();
        int countdown = countdownManager.isCounting()
                ? countdownManager.getCurrentCountdown()
                : lobbyConfig.getCountdownSeconds();

        List<String> templateLines = lobbyConfig.getScoreboardLines();
        if (templateLines.isEmpty()) return;

        ScoreboardManager sm = Bukkit.getScoreboardManager();

        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard board = sm.getNewScoreboard();
            Objective obj = board.registerNewObjective("lobby", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.setDisplayName(lobbyConfig.getScoreboardTitle());

            int score = templateLines.size();

            for (int i = 0; i < templateLines.size(); i++) {
                String line = templateLines.get(i);
                // 获取玩家个人积分
                int playerScore = 0;
                if (plugin instanceof NCGame) {
                    playerScore = ((NCGame) plugin).getPlayerScore(player);
                }
                // 替换变量，包括 {player_score}
                String replaced = replaceVariables(line, currentPlayers, requiredPlayers, countdown, playerScore);
                obj.getScore(replaced).setScore(score);
                score--;
            }
            player.setScoreboard(board);
        }
    }

    private String replaceVariables(String line, int current, int required, int countdown, int playerScore) {
        return ChatColor.translateAlternateColorCodes('&',
                line.replace("{current_players}", String.valueOf(current))
                        .replace("{min_players}", String.valueOf(lobbyConfig.getMinPlayers()))
                        .replace("{required_players}", String.valueOf(required))
                        .replace("{countdown}", String.valueOf(countdown))
                        .replace("{player_score}", String.valueOf(playerScore)));
    }
}