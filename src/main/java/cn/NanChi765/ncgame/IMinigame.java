package cn.nanchi765.ncgame;

import org.bukkit.entity.Player;

public interface IMinigame {
    void start();

    boolean isRunning();

    void addLatePlayer(Player player);
}