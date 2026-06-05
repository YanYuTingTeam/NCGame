package cn.nanchi7651.ncgame.utils;

import cn.nanchi7651.ncgame.NCGame;
import cn.nanchi7651.ncgame.game.IMinigame;
import cn.nanchi7651.ncgame.manager.GameManager;
import cn.nanchi7651.ncgame.manager.Status;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public class Utils {

    public static void resetPlayer(Player player) {
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.updateInventory();

        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(20);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setFoodLevel(20);
        player.setSaturation(10);
        player.setWalkSpeed(0.2f);

        player.setFlying(false);
        player.setAllowFlight(false);
    }

    public static boolean isGameActive(IMinigame minigame) {
        GameManager gameManager = NCGame.getInstance().getGameManager();
        return gameManager.getCurrentStatus() == Status.PLAYING && gameManager.getCurrentGame() == minigame;
    }

    /**
     * 判断玩家是否存活
     */
    public static boolean isAlive(Player player) {
        GameManager gameManager = NCGame.getInstance().getGameManager();
        return gameManager.getAlivePlayers().contains(player);
    }
}
