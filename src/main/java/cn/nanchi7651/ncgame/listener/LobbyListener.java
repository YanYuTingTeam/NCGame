package cn.nanchi7651.ncgame.listener;

import cn.nanchi7651.ncgame.NCGame;
import cn.nanchi7651.ncgame.config.LobbyConfig;
import cn.nanchi7651.ncgame.manager.GameManager;
import cn.nanchi7651.ncgame.manager.Status;
import cn.nanchi7651.ncgame.task.GameStartingTask;
import cn.nanchi7651.ncgame.utils.LobbyUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import static cn.nanchi7651.ncgame.utils.LobbyUtils.teleportToLobby;

public class LobbyListener implements Listener {

    private final LobbyConfig lobbyConfig;
    private final GameManager gameManager;

    public LobbyListener() {
        NCGame plugin = NCGame.getInstance();
        lobbyConfig = plugin.getLobbyConfig();
        gameManager = plugin.getGameManager();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 热加入玩家
        GameManager gameManager = NCGame.getInstance().getGameManager();
        if (gameManager.getCurrentStatus() != Status.WAITING && gameManager.getCurrentGame() != null) {
            gameManager.addLatePlayer(player);
            return;
        }
        // 传送大厅
        teleportToLobby(player);

        // 达到人数 开始倒计时
        if (gameManager.getGameStartingTask() == null && gameManager.getPlayers().size() > lobbyConfig.getMinPlayers()) {
            gameManager.setGameStartingTask(new GameStartingTask(gameManager));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // 人数离开 没达到人数 取消倒计时
        if (gameManager.getGameStartingTask() != null && gameManager.getPlayers().size() < lobbyConfig.getMinPlayers()) {
            gameManager.getGameStartingTask().cancel();
            gameManager.setGameStartingTask(null);
        }
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (gameManager.getCurrentStatus() != Status.WAITING) return;
        Player player = event.getPlayer();
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
                event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();
            if (item != null && item == lobbyConfig.getLobbyItem()) {
                event.setCancelled(true);
                LobbyUtils.sendBungeeServer(player, lobbyConfig.getBungeeServer());
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player) || gameManager.getCurrentStatus() != Status.WAITING) return;
        Player player = (Player) event.getEntity();
        event.setCancelled(true);
        player.setFoodLevel(20);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player) || gameManager.getCurrentStatus() != Status.WAITING) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (gameManager.getCurrentStatus() != Status.WAITING) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (gameManager.getCurrentStatus() != Status.WAITING) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        if (gameManager.getCurrentStatus() != Status.WAITING) return;
        event.setCancelled(true);
    }

}
