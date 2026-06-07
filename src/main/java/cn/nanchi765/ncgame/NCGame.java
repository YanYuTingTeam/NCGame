package cn.nanchi765.ncgame;

import cn.nanchi765.ncgame.config.EnableGameConfig;
import cn.nanchi765.ncgame.config.LobbyConfig;
import cn.nanchi765.ncgame.config.game.*;
import cn.nanchi765.ncgame.listener.InGameListener;
import cn.nanchi765.ncgame.listener.LobbyListener;
import cn.nanchi765.ncgame.listener.MOTDListener;
import cn.nanchi765.ncgame.listener.WaitingListener;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.manager.PlayerDataManager;
import cn.nanchi765.ncgame.manager.ScoreBoardManager;
import cn.nanchi765.ncgame.manager.WorldResetManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class NCGame extends JavaPlugin {

    @Getter
    private static NCGame instance;
    @Getter
    private LobbyConfig lobbyConfig;
    @Getter
    private EnableGameConfig enableGameConfig;
    @Getter
    private ScoreBoardManager scoreBoardManager;
    @Getter
    private GameManager gameManager;
    @Getter
    private WorldResetManager worldResetManager;
    private PlayerDataManager playerDataManager;

    @Override
    public void onEnable() {
        instance = this;
        // 保存默认配置文件
        saveResource("lobby.yml", false);
        saveResource("enablegame.yml", false);

        // 游戏配置 → games/ 目录下
        File gamesDir = new File(getDataFolder(), "games");
        if (!gamesDir.exists()) {
            gamesDir.mkdirs();
        }

        for (String game : List.of(
                "snowspleef", "quickhands", "extrememinefield", "cleanup",
                "speedrun", "onehitkill", "chefm", "bombsheep",
                "knockbackbattle", "snake", "faithblock")) {
            saveResource("games/" + game + ".yml", false);
        }
        saveDefaultConfig();

        lobbyConfig = new LobbyConfig(new File(getDataFolder(), "lobby.yml"));
        enableGameConfig = new EnableGameConfig(new File(getDataFolder(), "enablegame.yml"));

        gameManager = new GameManager(this);
        scoreBoardManager = new ScoreBoardManager(this, gameManager, lobbyConfig);

        getServer().getPluginManager().registerEvents(new InGameListener(), this);
        getServer().getPluginManager().registerEvents(new LobbyListener(), this);
        getServer().getPluginManager().registerEvents(new MOTDListener(), this);
        getServer().getPluginManager().registerEvents(new WaitingListener(), this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        for (World world : Bukkit.getWorlds()) {
            world.setGameRuleValue("announceAdvancements", "false");
            world.setGameRuleValue("doDaylightCycle", "false");
            world.setGameRuleValue("doWeatherCycle", "false");
            world.setGameRuleValue("doFireTick", "false");
            world.setTime(6000);
        }

        worldResetManager = new WorldResetManager(this);
        // 开服时重置所有游戏世界（如果已有备份则恢复，否则保持原样）
        resetAllGameWorlds();
        // 将当前干净状态备份，供后续重置使用
        backupAllGameWorlds();

        // 初始化数据管理器，恢复持久化积分
        playerDataManager = new PlayerDataManager(this);
        Map<UUID, Integer> savedScores = playerDataManager.loadAllScores();
        playerDataManager.getPlayerScores().putAll(savedScores);

        World lobbyWorld = getServer().getWorld(lobbyConfig.getWorldName());
        if (lobbyWorld != null) lobbyWorld.setPVP(false);

        getCommand("ncgame").setExecutor(new Command(this));

        getLogger().info("NCGame enabled.");
    }

    private void backupAllGameWorlds() {
        Set<String> worlds = new HashSet<>();
        worlds.add(new SnowSpleefConfig(new File(getDataFolder(), "snowspleef.yml")).getWorldName());
        worlds.add(new QuickHandsConfig(new File(getDataFolder(), "quickhands.yml")).getWorldName());
        worlds.add(new ExtremeMinefieldConfig(new File(getDataFolder(), "extrememinefield.yml")).getWorldName());
        worlds.add(new CleanupConfig(new File(getDataFolder(), "cleanup.yml")).getWorldName());
        worlds.add(new SpeedrunConfig(new File(getDataFolder(), "speedrun.yml")).getWorldName());
        worlds.add(new OneHitKillConfig(new File(getDataFolder(), "onehitkill.yml")).getWorldName());
        worlds.add(new ChefMConfig(new File(getDataFolder(), "chefm.yml")).getWorldName());
        worlds.add(new BombSheepConfig(new File(getDataFolder(), "bombsheep.yml")).getWorldName());
        worlds.add(new KnockbackBattleConfig(new File(getDataFolder(), "knockbackbattle.yml")).getWorldName());
        worlds.add(new SnakeConfig(new File(getDataFolder(), "snake.yml")).getWorldName());
        worlds.add(new FaithBlockConfig(new File(getDataFolder(), "faithblock.yml")).getWorldName());
        for (String world : worlds) worldResetManager.backupWorld(world);
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) playerDataManager.close();   // 新增
        getLogger().info("NCGame disabled.");
    }

    public void addScore(Player player, int amount) {
        gameManager.getPlayerScores().merge(player.getUniqueId(), amount, Integer::sum);
        int newScore = playerDataManager.getPlayerScores().merge(player.getUniqueId(), amount, Integer::sum);
        playerDataManager.savePlayerScore(player, newScore);
    }

    private void resetAllGameWorlds() {
        Set<String> worlds = new HashSet<>();
        worlds.add(new SnowSpleefConfig(new File(getDataFolder(), "snowspleef.yml")).getWorldName());
        worlds.add(new QuickHandsConfig(new File(getDataFolder(), "quickhands.yml")).getWorldName());
        worlds.add(new ExtremeMinefieldConfig(new File(getDataFolder(), "extrememinefield.yml")).getWorldName());
        worlds.add(new CleanupConfig(new File(getDataFolder(), "cleanup.yml")).getWorldName());
        worlds.add(new SpeedrunConfig(new File(getDataFolder(), "speedrun.yml")).getWorldName());
        worlds.add(new OneHitKillConfig(new File(getDataFolder(), "onehitkill.yml")).getWorldName());
        worlds.add(new ChefMConfig(new File(getDataFolder(), "chefm.yml")).getWorldName());
        worlds.add(new BombSheepConfig(new File(getDataFolder(), "bombsheep.yml")).getWorldName());
        worlds.add(new KnockbackBattleConfig(new File(getDataFolder(), "knockbackbattle.yml")).getWorldName());
        worlds.add(new SnakeConfig(new File(getDataFolder(), "snake.yml")).getWorldName());
        worlds.add(new FaithBlockConfig(new File(getDataFolder(), "faithblock.yml")).getWorldName());
        for (String world : worlds) worldResetManager.resetWorld(world);
        Bukkit.getScheduler().runTaskLater(this, this::backupAllGameWorlds, 100L);
    }
}