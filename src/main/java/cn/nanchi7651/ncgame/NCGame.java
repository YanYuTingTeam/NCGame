package cn.nanchi7651.ncgame;

import cn.nanchi7651.ncgame.config.EnableGameConfig;
import cn.nanchi7651.ncgame.config.LobbyConfig;
import cn.nanchi765.ncgame.config.game.*;
import cn.nanchi7651.ncgame.config.game.*;
import cn.nanchi7651.ncgame.manager.GameManager;
import cn.nanchi7651.ncgame.manager.PlayerDataManager;
import cn.nanchi7651.ncgame.manager.ScoreBoardManager;
import cn.nanchi7651.ncgame.manager.WorldResetManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NCGame extends JavaPlugin implements Listener {

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
        saveResource("snowspleef.yml", false);
        saveResource("quickhands.yml", false);
        saveResource("extrememinefield.yml", false);
        saveResource("cleanup.yml", false);
        saveResource("speedrun.yml", false);
        saveResource("onehitkill.yml", false);
        saveResource("chefm.yml", false);
        saveResource("bombsheep.yml", false);
        saveResource("knockbackbattle.yml", false);
        saveResource("snake.yml", false);
        saveResource("faithblock.yml", false);
        saveDefaultConfig();

        lobbyConfig = new LobbyConfig(new File(getDataFolder(), "lobby.yml"));
        enableGameConfig = new EnableGameConfig(new File(getDataFolder(), "enablegame.yml"));

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        gameManager = new GameManager(this);
        scoreBoardManager = new ScoreBoardManager(this, gameManager, lobbyConfig);

        worldResetManager = new WorldResetManager(this);
        // 开服时重置所有游戏世界（如果已有备份则恢复，否则保持原样）
        resetAllGameWorlds();
        // 将当前干净状态备份，供后续重置使用
        backupAllGameWorlds();

        // 初始化数据管理器，恢复持久化积分
        playerDataManager = new PlayerDataManager(this);
        Map<UUID, Integer> savedScores = playerDataManager.loadAllScores();
        gameManager.getPlayerScores().putAll(savedScores);

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
        int newScore = gameManager.getPlayerScores().merge(player.getUniqueId(), amount, Integer::sum);
        playerDataManager.savePlayerScore(player, newScore);  // 传入 Player 以保存名称
    }

    public int getPlayerScore(Player player) {
        return gameManager.getPlayerScores().getOrDefault(player.getUniqueId(), 0);
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