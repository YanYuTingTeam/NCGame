package cn.nanchi7651.ncgame.config;

import org.bukkit.Location;

import java.util.List;

public interface GameConfig {

    int getPrepareTime();

    int getGameTime();

    Location getSpectatorSpawn();

    double getSpectatorRegionRadius();

    boolean isCountdownSoundEnabled();

    String getScoreboardTitle();

    List<String> getScoreboardLines();

    String subtitle();
}
