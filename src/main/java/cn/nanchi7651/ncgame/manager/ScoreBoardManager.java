package cn.nanchi7651.ncgame.manager;

import cn.nanchi7651.ncgame.NCGame;
import cn.nanchi7651.ncgame.config.LobbyConfig;
import fr.mrmicky.fastboard.FastBoard;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class ScoreBoardManager {

    private final HashMap<UUID, FastBoard> boards = new HashMap<>();
    private final GameManager gameManager;
    private final LobbyConfig lobbyConfig;

    public ScoreBoardManager(NCGame ncGame, GameManager gameManager, LobbyConfig lobbyConfig) {
        this.gameManager = gameManager;
        this.lobbyConfig = lobbyConfig;

        Bukkit.getServer().getScheduler().runTaskTimerAsynchronously(NCGame.getInstance(), () -> {
            for (Player player : gameManager.getPlayers()) {
                giveScoreBoard(player);
            }
//            for (FastBoard board : this.boards.values()) {
//                scoreBoardManager.updateBoard(board, board.getPlayer());
//            }
        }, 0, 20);
    }

    public void giveScoreBoard(Player player) {
        FastBoard board;
        if (boards.getOrDefault(player.getUniqueId(), null) == null) {
            board = new FastBoard(player);
            boards.put(player.getUniqueId(), board);
        } else {
            board = boards.getOrDefault(player.getUniqueId(), null);
        }

        if (gameManager.getCurrentStatus() == Status.WAITING) {
            if (!lobbyConfig.isScoreboardEnabled()) return;
            String lobbyTitle = lobbyConfig.getScoreboardTitle();
            List<String> lines = replaceLobbyVariables(lobbyConfig.getScoreboardLines(), player);
            setBoard(board, player, lobbyTitle, lines);
            return;
        }

        String gameSbTitle = gameManager.getCurrentGame().getConfig().getScoreboardTitle();
        List<String> lines = gameManager.getCurrentGame().getConfig().getScoreboardLines();
        setBoard(board, player, gameSbTitle, lines);
    }

    private void setBoard(FastBoard board, Player player, String title, List<String> lines) {
        if (board == null) return;
        if (player == null || !player.isOnline()) {
            board.delete();
            return;
        }
        board.updateTitle(title.replace("&", "§"));
        board.updateLines(lines);
    }

    private List<String> replaceLobbyVariables(List<String> lines, Player player) {
        List<String> result = new ArrayList<>();
        int countdown = NCGame.getInstance().getGameManager().getGameStartingTask().getCountdown();
        for (String line : lines) {
            result.add(ChatColor.translateAlternateColorCodes('&',
                    line.replace("{current_players}", String.valueOf(gameManager.getPlayers().size()))
                            .replace("{min_players}", String.valueOf(lobbyConfig.getMinPlayers()))
                            .replace("{required_players}", String.valueOf(lobbyConfig.getRequiredPlayers()))
                            .replace("{countdown}", String.valueOf(countdown))
                            .replace("{player_score}", String.valueOf(
                                    NCGame.getInstance().getPlayerScore(player)))));
        }
        return result;
    }

}
