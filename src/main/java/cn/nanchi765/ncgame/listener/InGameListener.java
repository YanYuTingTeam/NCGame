package cn.nanchi765.ncgame.listener;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.manager.Status;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class InGameListener implements Listener {

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        GameManager gameManager = NCGame.getInstance().getGameManager();
        if (gameManager.getCurrentStatus() != Status.PLAYING) return;
        if (gameManager.getCurrentGame() == null) return;

        Player player = event.getPlayer();
        gameManager.getCurrentGame().eliminatePlayer(player);
        NCGame.getInstance().getScoreBoardManager().removeScoreBoard(player);
    }

    //    @EventHandler
    //    public void onVoidDagame(EntityDamageEvent event) {
    //        if (!(event.getEntity() instanceof Player)) {
    //            return;
    //        }
    //        Player player = (Player) event.getEntity();
    //        GameManager gameManager = NCGame.getInstance().getGameManager();
    //        if (gameManager.getCurrentStatus() != Status.PLAYING) return;
    //        if (gameManager.getCurrentGame() == null) return;
    //        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
    ////            player.teleport(gameManager.getCurrentGame().getConfig().getSpectatorSpawn());
    //        }
    //    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (event.getEntity() == null) {
            return;
        }
        Player player = event.getEntity();
        GameManager gameManager = NCGame.getInstance().getGameManager();
        if (gameManager.getCurrentStatus() != Status.PLAYING) return;
        if (gameManager.getCurrentGame() == null) return;
        gameManager.getCurrentGame().eliminatePlayer(player);
    }
}
