package cn.nanchi765.ncgame.listener;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.LobbyConfig;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.manager.Status;
import cn.nanchi765.ncgame.task.GameStartingTask;
import cn.nanchi765.ncgame.utils.LobbyUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import static cn.nanchi765.ncgame.utils.LobbyUtils.teleportToLobby;

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
        NCGame.getInstance().getScoreBoardManager().updateScoreBoard(player);
        // 热加入玩家
        GameManager gameManager = NCGame.getInstance().getGameManager();
        if (gameManager.getCurrentStatus() != Status.WAITING) {
            gameManager.addLatePlayer(player);
            return;
        }
        gameManager.addPlayer(player);
        // 传送大厅
        teleportToLobby(player);

        // 达到人数 开始倒计时
        if (gameManager.getGameStartingTask() == null && gameManager.getPlayers().size() >= lobbyConfig.getMinPlayers()) {
            gameManager.setGameStartingTask(new GameStartingTask(gameManager));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        gameManager.removePlayer(player);
        NCGame.getInstance().getScoreBoardManager().removeScoreBoard(player);
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
        if (!event.getAction().toString().contains("RIGHT")) return;
        ItemStack item = event.getItem();
        if (item != null && item.hasItemMeta() && lobbyConfig.getLobbyItem().hasItemMeta()
                && item.getItemMeta().hasDisplayName() && lobbyConfig.getLobbyItem().getItemMeta().hasDisplayName()
                && item.getItemMeta().getDisplayName().equals(lobbyConfig.getLobbyItem().getItemMeta().getDisplayName())) {
            event.setCancelled(true);
            LobbyUtils.sendBungeeServer(player, lobbyConfig.getBungeeServer());
        }
    }

    @EventHandler
    public void onPlayerInventoryClick(InventoryClickEvent event) {
        if (gameManager.getCurrentStatus() != Status.WAITING) return;
        event.setCancelled(true);
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
