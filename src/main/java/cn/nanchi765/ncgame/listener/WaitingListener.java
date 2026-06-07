package cn.nanchi765.ncgame.listener;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.manager.Status;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

public class WaitingListener implements Listener {

    private final GameManager gameManager;

    public WaitingListener() {
        NCGame plugin = NCGame.getInstance();
        gameManager = plugin.getGameManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (gameManager.getCurrentStatus() != Status.PLAYING) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(EntityDamageEvent event) {
        if (gameManager.getCurrentStatus() != Status.PLAYING) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (gameManager.getCurrentStatus() != Status.PLAYING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction().toString().contains("RIGHT")) return;
        if (gameManager.getCurrentStatus() != Status.PLAYING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerShearEntityEvent event) {
        if (gameManager.getCurrentStatus() != Status.PLAYING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        if (gameManager.getCurrentStatus() != Status.PLAYING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjLaunch(ProjectileLaunchEvent event) {
        if (gameManager.getCurrentStatus() != Status.PLAYING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInventoryClick(InventoryClickEvent event) {
        if (gameManager.getCurrentStatus() != Status.PLAYING) {
            event.setCancelled(true);
        }
    }

}
