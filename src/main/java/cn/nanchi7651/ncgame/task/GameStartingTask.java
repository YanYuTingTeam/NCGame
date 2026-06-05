package cn.nanchi7651.ncgame.task;

import cn.nanchi7651.ncgame.NCGame;
import cn.nanchi7651.ncgame.manager.GameManager;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

// 用于大厅 开始倒计时
public class GameStartingTask implements Runnable {

    private final BukkitTask task;
    private final GameManager gameManager;
    @Getter
    @Setter
    private int countdown;

    public GameStartingTask(GameManager gameManager) {
        this.gameManager = gameManager;
        this.countdown = NCGame.getInstance().getLobbyConfig().getCountdownSeconds();
        this.task = Bukkit.getScheduler().runTaskTimer(NCGame.getInstance(), this, 0, 20L);
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

            if (countdown <= 5 && countdown > 0) {
                String title = ChatColor.YELLOW + String.valueOf(countdown) + "秒后";
                String subtitle = ChatColor.DARK_GREEN + "小游戏即将启动";
                for (Player p : gameManager.getPlayers()) {
                    p.sendTitle(title, subtitle, 0, 25, 10);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_PLING, 1.0f, 1.0f);
                }
            }

            return;
        }

        task.cancel();

        ArrayList<String> pending = NCGame.getInstance().getEnableGameConfig().getAllGames().entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayList::new));

        if (pending.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.RED + "没有启用的游戏，请联系管理员！");
            return;
        }

        // 游戏开始 排列游戏
        Collections.shuffle(pending);
        gameManager.getPendingGames().clear();
        gameManager.getPendingGames().addAll(pending);

        gameManager.startNextGame();
        gameManager.setGameStartingTask(null);
    }

    public void cancel() {
        for (Player p : gameManager.getPlayers()) {
            p.sendMessage(ChatColor.RED + "人数不足，倒计时取消");
        }
        task.cancel();
    }
}
