package cn.nanchi765.ncgame.task;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.GameConfig;
import cn.nanchi765.ncgame.game.IMinigame;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.manager.Status;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

// 用于游戏 游戏中倒计时
public class GamePlayingTask implements Runnable {

    private final BukkitTask task;
    private final GameManager gameManager;
    private final IMinigame minigame;
    private final GameConfig gameConfig;
    @Getter
    @Setter
    private int countdown;

    public GamePlayingTask(GameManager gameManager, IMinigame minigame) {
        this.gameManager = gameManager;
        this.minigame = minigame;
        this.gameConfig = minigame.getConfig();
        this.countdown = gameManager.getCurrentGame().getConfig().getGameTime();
        this.task = Bukkit.getScheduler().runTaskTimer(NCGame.getInstance(), this, 0, 20L);
        gameManager.setCurrentStatus(Status.PLAYING);
        minigame.start();
    }

    public int getTask() {
        return task.getTaskId();
    }

    public BukkitTask getBukkitTask() {
        return task;
    }

    @Override
    public void run() {
        if (countdown-- > 0) {
            if (countdown < 5 && countdown > 0) {
                String title = ChatColor.YELLOW + String.valueOf(countdown) + "秒后";
                String subtitle = ChatColor.DARK_GREEN + "游戏即将结束";
                for (Player p : gameManager.getPlayers()) {
                    p.sendTitle(title, subtitle, countdown==4?10:0, 25, 10);
                    if (gameConfig.isCountdownSoundEnabled())
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_PLING, 1.0f, 1.0f);
                }
            }

            return;
        }

        this.cancel();
        minigame.end();
        gameManager.onMinigameEnd();
    }

    public void cancel() {
        gameManager.setGamePlayingTask(null);
        task.cancel();
    }
}
