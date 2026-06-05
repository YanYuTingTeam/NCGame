package cn.nanchi765.ncgame;

import cn.nanchi765.ncgame.config.LobbyConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class GameCountdownManager {

    private final JavaPlugin plugin;
    private final LobbyConfig lobbyConfig;
    private int countdownTaskId = -1;
    private int currentCountdown;
    private boolean counting = false;
    private boolean paused = false;              // 新增：暂停状态
    private GameStartCallback callback;

    /**
     * 游戏开始回调接口
     */
    public interface GameStartCallback {
        void onGameStart();
    }

    public void setGameStartCallback(GameStartCallback callback) {
        this.callback = callback;
    }

    public GameCountdownManager(JavaPlugin plugin, LobbyConfig lobbyConfig) {
        this.plugin = plugin;
        this.lobbyConfig = lobbyConfig;
        startMonitor();
    }

    /**
     * 设置暂停状态（用于游戏进行中冻结倒计时）
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
        if (paused) {
            cancelCountdown(null);   // 立即取消正在进行的倒计时
        }
    }

    // 每 20 ticks（1秒）检查一次玩家数
    private void startMonitor() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (paused) return;            // 新增：暂停时直接跳过

                int online = Bukkit.getOnlinePlayers().size();
                int required = lobbyConfig.getRequiredPlayers();

                if (!counting && online >= required) {
                    startCountdown();
                } else if (counting && online < required) {
                    cancelCountdown(ChatColor.RED + "人数不足，倒计时取消");
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startCountdown() {
        counting = true;
        currentCountdown = lobbyConfig.getCountdownSeconds();

        countdownTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (currentCountdown <= 0) {
                    broadcastTitle("", "");
                    Bukkit.broadcastMessage(ChatColor.GREEN + "游戏开始！");
                    cancel();
                    counting = false;
                    if (callback != null) {
                        callback.onGameStart();
                    }
                    return;
                }

                if (currentCountdown <= 10) {
                    String number = ChatColor.YELLOW + String.valueOf(currentCountdown) + " 秒后";
                    String subtitle = ChatColor.GREEN + "小游戏即将启动";
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle(number, subtitle, 0, 25, 5);
                    }
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_PLING, 1.0f, 1.0f);
                    }
                }

                currentCountdown--;
            }
        }.runTaskTimer(plugin, 0L, 20L).getTaskId();
    }

    public void cancelCountdown(String reason) {
        if (countdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(countdownTaskId);
            countdownTaskId = -1;
        }
        counting = false;
        if (reason != null) {
            Bukkit.broadcastMessage(reason);
        }
        broadcastTitle("", "");
    }

    private void broadcastTitle(String title, String subtitle) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(title, subtitle, 0, 10, 5);
        }
    }

    public boolean isCounting() {
        return counting;
    }

    public int getCurrentCountdown() {
        return counting ? currentCountdown : 0;
    }
}