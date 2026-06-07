package cn.nanchi765.ncgame.listener;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.GameConfig;
import cn.nanchi765.ncgame.game.IMinigame;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.manager.Status;
import cn.nanchi765.ncgame.utils.GameUtils;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayingListener implements Listener {

    private final GameManager gameManager;

    public PlayingListener() {
        NCGame plugin = NCGame.getInstance();
        gameManager = plugin.getGameManager();
    }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(10f);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (gameManager.getCurrentStatus() != Status.PLAYING) return;
        if (gameManager.getCurrentGame() == null) return;

        Player player = event.getPlayer();
        gameManager.removePlayer(player);
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

//        @EventHandler
//        public void onVoidDagame(EntityDamageEvent event) {
//            if (!(event.getEntity() instanceof Player)) {
//                return;
//            }
//            Player player = (Player) event.getEntity();
//            GameManager gameManager = NCGame.getInstance().getGameManager();
//            if (gameManager.getCurrentStatus() != Status.PLAYING) return;
//            if (gameManager.getCurrentGame() == null) return;
//            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
//    //            player.teleport(gameManager.getCurrentGame().getConfig().getSpectatorSpawn());
//            }
//        }

    // 防止神秘死亡
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDeath(final PlayerDeathEvent e) {
        final Player player = e.getEntity();
        player.spigot().respawn();
        IMinigame currentGame = gameManager.getCurrentGame();
        if (currentGame == null) return;
        if (GameUtils.isAlive(player)) {
            gameManager.getCurrentGame().eliminatePlayer(player);
        }
    }

    // 重生后旁观者模式
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (gameManager.getCurrentStatus() != Status.PLAYING) return;
        IMinigame currentGame = gameManager.getCurrentGame();
        if (currentGame == null) return;
        if (!GameUtils.isAlive(player)) {
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(currentGame.getConfig().getSpectatorSpawn());
        }
    }

    // 旁观者移动
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (gameManager.getCurrentStatus() != Status.PLAYING) return;
        if (gameManager.getCurrentGame() == null) return;

        Player player = event.getPlayer();
        GameConfig config = gameManager.getCurrentGame().getConfig();
        boolean alive = gameManager.getAlivePlayers().contains(player);

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
