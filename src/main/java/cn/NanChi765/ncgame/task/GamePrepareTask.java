package cn.nanchi765.ncgame.task;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.GameConfig;
import cn.nanchi765.ncgame.game.IMinigame;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.manager.Status;
import cn.nanchi765.ncgame.utils.Utils;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

// 用于游戏 准备倒计时
public class GamePrepareTask implements Runnable {

    private final BukkitTask task;
    private final GameManager gameManager;
    private final IMinigame minigame;
    private final GameConfig gameConfig;
    private final int reqPlayers;
    @Getter
    @Setter
    private int countdown;

    public GamePrepareTask(GameManager gameManager, IMinigame minigame) {
        this.gameManager = gameManager;
        this.minigame = minigame;
        this.gameConfig = minigame.getConfig();
        this.countdown = gameConfig.getPrepareTime();
        this.reqPlayers = NCGame.getInstance().getLobbyConfig().getRequiredPlayers();
        this.task = Bukkit.getScheduler().runTaskTimer(NCGame.getInstance(), this, 0, 20L);
        gameManager.setCurrentStatus(Status.PREPARE);

        for (Player player : gameManager.getPlayers()) {
            Utils.resetPlayer(player);
        }
    }

    public int getTask() {
        return task.getTaskId();
    }

    public BukkitTask getBukkitTask() {
        return task;
    }

    @Override
    public void run() {
        if (gameManager.getPlayers().size() < reqPlayers) {
            this.cancel();
            gameManager.onMinigameEnd();
            return;
        }
        if (countdown-- > 0) {

            if (countdown <= 10) {
                String title = ChatColor.YELLOW + String.valueOf(countdown);
                String subtitle = ChatColor.DARK_GREEN + gameConfig.subtitle();
                boolean playSound = gameConfig.isCountdownSoundEnabled();
                for (Player p : gameManager.getPlayers()) {
                    p.sendTitle(title, subtitle, 0, 25, 5);
                    if (playSound) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_PLING, 1.0f, 1.0f);
                    }
                }
            }

            return;
        }

        task.cancel();
        gameManager.setGamePrepareTask(null);
        minigame.start();
        gameManager.setGamePlayingTask(new GamePlayingTask(gameManager, minigame));
    }

    public void cancel() {
        task.cancel();
    }
}
