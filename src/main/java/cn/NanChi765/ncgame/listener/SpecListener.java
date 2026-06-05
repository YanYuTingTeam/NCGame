package cn.nanchi765.ncgame.listener;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.GameConfig;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.manager.Status;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class SpecListener implements Listener {

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        GameManager gameManager = NCGame.getInstance().getGameManager();
        if (gameManager.getCurrentStatus() != Status.PLAYING) return;
        if (gameManager.getCurrentGame() == null) return;

        Player player = event.getPlayer();
        GameConfig config = gameManager.getCurrentGame().getConfig();
        boolean alive = gameManager.getAlivePlayers().contains(player) || (!player.getGameMode().equals(GameMode.SPECTATOR));

        if (alive) return; // 存活玩家不受限制

        // 非存活玩家强制旁观
        if (player.getGameMode() != GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SPECTATOR);
            return;
        }

        // 旁观者边界限制
        Location center = config.getSpectatorSpawn();
        if (center == null || !player.getWorld().equals(center.getWorld())) return;
        if (player.getLocation().distanceSquared(center) > config.getSpectatorRegionRadius() * config.getSpectatorRegionRadius()) {
            player.teleport(center);
        }
    }
}
