package cn.NanChi765.ncgame;

import org.bukkit.entity.Player;

public interface IMinigame {
    void start();
    boolean isRunning();
    void addLatePlayer(Player player);
}