package cn.nanchi765.ncgame.game;

import cn.nanchi765.ncgame.config.GameConfig;
import org.bukkit.entity.Player;

public interface IMinigame {

    void prepare();

    void start();

    void end();

    GameConfig getConfig();

    void eliminatePlayer(Player player);

}