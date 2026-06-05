package cn.nanchi765.ncgame.listener;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.manager.Status;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class InGameQuitListener implements Listener {

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        GameManager gameManager = NCGame.getInstance().getGameManager();
        if (gameManager.getCurrentStatus() != Status.PLAYING) return;
        if (gameManager.getCurrentGame() == null) return;

        Player player = event.getPlayer();
        gameManager.getCurrentGame().eliminatePlayer(player);
        NCGame.getInstance().getScoreBoardManager().removeScoreBoard(player);
    }
}
