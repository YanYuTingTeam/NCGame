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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

public class WaitingListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        GameManager gameManager = NCGame.getInstance().getGameManager();
        if (gameManager.getCurrentStatus() != Status.PLAYING) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(EntityDamageEvent event) {
        GameManager gameManager = NCGame.getInstance().getGameManager();
        if (gameManager.getCurrentStatus() != Status.PLAYING) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        GameManager gameManager = NCGame.getInstance().getGameManager();
        if (gameManager.getCurrentStatus() != Status.PLAYING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        GameManager gameManager = NCGame.getInstance().getGameManager();
        if (event.getAction().toString().contains("RIGHT")) return;
        if (gameManager.getCurrentStatus() != Status.PLAYING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerShearEntityEvent event) {
        GameManager gameManager = NCGame.getInstance().getGameManager();
        if (gameManager.getCurrentStatus() != Status.PLAYING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        GameManager gameManager = NCGame.getInstance().getGameManager();
        if (gameManager.getCurrentStatus() != Status.PLAYING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjLaunch(ProjectileLaunchEvent event) {
        GameManager gameManager = NCGame.getInstance().getGameManager();
        if (gameManager.getCurrentStatus() != Status.PLAYING) {
            event.setCancelled(true);
        }
    }


}
