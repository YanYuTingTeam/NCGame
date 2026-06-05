package cn.nanchi765.ncgame.manager;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.LobbyConfig;
import fr.mrmicky.fastboard.FastBoard;
import lombok.var;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static cn.nanchi765.ncgame.utils.Utils.colorize;

public class ScoreBoardManager {

    private final Map<UUID, FastBoard> boards = new ConcurrentHashMap<>();
    private final NCGame plugin;
    private final GameManager gameManager;
    private final LobbyConfig lobbyConfig;

    public ScoreBoardManager(NCGame plugin, GameManager gameManager, LobbyConfig lobbyConfig) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.lobbyConfig = lobbyConfig;

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Player player : gameManager.getPlayers()) {
                updateScoreBoard(player);
            }
        }, 0L, 20L);
    }

    // ======================== 公开接口 ========================

    public void removeScoreBoard(Player player) {
        FastBoard board = boards.remove(player.getUniqueId());
        if (board != null) {
            board.delete();
        }
    }

    // ======================== 核心逻辑 ========================

    public void updateScoreBoard(Player player) {
        if (!player.isOnline()) {
            removeScoreBoard(player);
            return;
        }

        FastBoard board = boards.computeIfAbsent(player.getUniqueId(), id -> new FastBoard(player));

        if (gameManager.getCurrentStatus() == Status.WAITING) {
            if (!lobbyConfig.isScoreboardEnabled()) return;
            applyBoard(board,
                    lobbyConfig.getScoreboardTitle(),
                    lobbyConfig.getScoreboardLines(),
                    player, true);
        } else {
            var config = gameManager.getCurrentGame().getConfig();
            applyBoard(board,
                    config.getScoreboardTitle(),
                    config.getScoreboardLines(),
                    player, false);
        }
    }

    private void applyBoard(FastBoard board, String title, List<String> template,
                            Player player, boolean isLobby) {
        board.updateTitle(colorize(title));
        board.updateLines(buildLines(template, player, isLobby));
    }

    // ======================== 变量替换 ========================

    private List<String> buildLines(List<String> template, Player player, boolean isLobby) {
        int countdown    = resolveCountdown(isLobby);
        int gameTime = isLobby
                ? 0
                : gameManager.getCurrentGame().getConfig().getGameTime();
        int playerScore  = plugin.getPlayerScore(player);
        int currentSize  = gameManager.getPlayers().size();
        int minPlayers   = lobbyConfig.getMinPlayers();
        int required     = lobbyConfig.getRequiredPlayers();

        List<String> result = new ArrayList<>(template.size());
        for (String line : template) {
            String replaced = line
                    .replace("{current_players}", String.valueOf(currentSize))
                    .replace("{min_players}",      String.valueOf(minPlayers))
                    .replace("{required_players}",  String.valueOf(required))
                    .replace("{countdown}",         String.valueOf(countdown))
                    .replace("{time_left}",         String.valueOf(countdown))
                    .replace("{total_time}",        String.valueOf(gameTime))
                    .replace("{player_score}",      String.valueOf(playerScore));
            result.add(colorize(replaced));
        }
        return result;
    }


    private int resolveCountdown(boolean isLobby) {
        var startingTask = gameManager.getGameStartingTask();
        if (startingTask != null) {
            return startingTask.getCountdown();
        }
        return isLobby
                ? lobbyConfig.getCountdownSeconds()
                : gameManager.getCurrentGame().getConfig().getGameTime();
    }

    // ======================== 工具 ========================

}
