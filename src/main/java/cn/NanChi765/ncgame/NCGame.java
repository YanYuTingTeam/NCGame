package cn.nanchi765.ncgame;

import cn.nanchi765.ncgame.config.*;
import cn.nanchi765.ncgame.game.*;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class NCGame extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, Integer> playerScores = new HashMap<>();
    private final List<Player> lateJoinQueue = new ArrayList<>();
    private LobbyConfig lobbyConfig;
    private EnableGameConfig enableGameConfig;
    private GameCountdownManager countdownManager;
    private LobbyScoreboardManager scoreboardManager;
    private IMinigame currentGame;
    private WorldResetManager worldResetManager;
    private final List<String> pendingGames = new ArrayList<>();
    private PlayerDataManager playerDataManager;
    private BukkitTask lobbyMonitorTask; // 大厅实时监控

    @Override
    public void onEnable() {
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
        saveDefaultConfig(); // 生成 config.yml（数据库配置）

        lobbyConfig = new LobbyConfig(new File(getDataFolder(), "lobby.yml"));
        enableGameConfig = new EnableGameConfig(new File(getDataFolder(), "enablegame.yml"));

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        countdownManager = new GameCountdownManager(this, lobbyConfig);
        scoreboardManager = new LobbyScoreboardManager(this, lobbyConfig, countdownManager);

        worldResetManager = new WorldResetManager(this);
        // 开服时重置所有游戏世界（如果已有备份则恢复，否则保持原样）
        resetAllGameWorlds();
        // 将当前干净状态备份，供后续重置使用
        backupAllGameWorlds();

        // 初始化数据管理器，恢复持久化积分
        playerDataManager = new PlayerDataManager(this);
        Map<UUID, Integer> savedScores = playerDataManager.loadAllScores();
        playerScores.putAll(savedScores);

        World lobbyWorld = getServer().getWorld(lobbyConfig.getWorldName());
        if (lobbyWorld != null) lobbyWorld.setPVP(false);

        if (getCommand("ncgame") != null) getCommand("ncgame").setExecutor(this);

        // 启动大厅持续监控（强制冒险模式）
        startLobbyMonitor();

        // 倒计时结束回调：启动新轮次
        countdownManager.setGameStartCallback(() -> {
            // 清空上轮积分（新一轮开始）
            playerScores.clear();
            stopLobbyScoreboard();
            pendingGames.clear();
            pendingGames.addAll(enableGameConfig.getAllGames().entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList()));
            if (pendingGames.isEmpty()) {
                Bukkit.broadcastMessage(ChatColor.RED + "没有启用的游戏！");
                startLobbyScoreboard();
                return;
            }
            Collections.shuffle(pendingGames);
            startNextGame();
        });

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
        if (scoreboardManager != null) scoreboardManager.stopUpdating();
        if (lobbyMonitorTask != null) lobbyMonitorTask.cancel();
        if (playerDataManager != null) playerDataManager.close();   // 新增
        getLogger().info("NCGame disabled.");
    }

    // ========== 公共方法 ==========
    public LobbyConfig getLobbyConfig() {
        return lobbyConfig;
    }

    public EnableGameConfig getEnableGameConfig() {
        return enableGameConfig;
    }

    public void stopLobbyScoreboard() {
        if (scoreboardManager != null) scoreboardManager.stopUpdating();
        if (countdownManager != null) countdownManager.setPaused(true);
    }

    public void startLobbyScoreboard() {
        if (scoreboardManager != null)
            scoreboardManager = new LobbyScoreboardManager(this, lobbyConfig, countdownManager);
        if (countdownManager != null) countdownManager.setPaused(false);
    }

    public void teleportToLobby(Player player) {
        String worldName = lobbyConfig.getWorldName();
        World world = getServer().getWorld(worldName);
        if (world == null) {
            getLogger().warning("大厅世界 '" + worldName + "' 未找到！");
            return;
        }
        world.setPVP(false);
        Location loc = new Location(world,
                lobbyConfig.getX(), lobbyConfig.getY(), lobbyConfig.getZ(),
                lobbyConfig.getYaw(), lobbyConfig.getPitch());

        // 强制清空背包和装备，防止带出游戏物品
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.updateInventory();

        player.teleport(loc);
        player.setGameMode(GameMode.ADVENTURE);
        player.setFoodLevel(20);
        player.setSaturation(10);
        giveReturnItem(player);
    }

    public void addScore(Player player, int amount) {
        int newScore = playerScores.merge(player.getUniqueId(), amount, Integer::sum);
        playerDataManager.savePlayerScore(player, newScore);  // 传入 Player 以保存名称
    }

    public int getPlayerScore(Player player) {
        return playerScores.getOrDefault(player.getUniqueId(), 0);
    }

    private void startNextGame() {
        if (pendingGames.isEmpty()) return;
        String nextGame = pendingGames.remove(0);

        Set<Player> playerSet = new HashSet<>(Bukkit.getOnlinePlayers());
        playerSet.addAll(lateJoinQueue);
        List<Player> players = new ArrayList<>(playerSet);
        lateJoinQueue.clear();

        if (players.size() < lobbyConfig.getRequiredPlayers()) {
            Bukkit.broadcastMessage(ChatColor.RED + "人数不足，本轮游戏取消，返回大厅...");
            pendingGames.clear();
            returnAllToLobby();
            startLobbyScoreboard();
            return;
        }

        switch (nextGame) {
            case "掘一死战":
                SnowSpleefConfig spleefConfig = new SnowSpleefConfig(new File(getDataFolder(), "snowspleef.yml"));
                currentGame = new SnowSpleefGame(this, spleefConfig, players);
                break;
            case "眼疾手快":
                QuickHandsConfig qhConfig = new QuickHandsConfig(new File(getDataFolder(), "quickhands.yml"));
                currentGame = new QuickHandsGame(this, qhConfig, players);
                break;
            case "极限雷区":
                ExtremeMinefieldConfig emConfig = new ExtremeMinefieldConfig(new File(getDataFolder(), "extrememinefield.yml"));
                currentGame = new ExtremeMinefieldGame(this, emConfig, players);
                break;
            case "大扫除":
                CleanupConfig cConfig = new CleanupConfig(new File(getDataFolder(), "cleanup.yml"));
                currentGame = new CleanupGame(this, cConfig, players);
                break;
            case "急速酷跑":
                SpeedrunConfig spConfig = new SpeedrunConfig(new File(getDataFolder(), "speedrun.yml"));
                currentGame = new SpeedrunGame(this, spConfig, players);
                break;
            case "一箭必杀":
                OneHitKillConfig ohkConfig = new OneHitKillConfig(new File(getDataFolder(), "onehitkill.yml"));
                currentGame = new OneHitKillGame(this, ohkConfig, players);
                break;
            case "厨神":
                ChefMConfig chefConfig = new ChefMConfig(new File(getDataFolder(), "chefm.yml"));
                currentGame = new ChefMGame(this, chefConfig, players);
                break;
            case "炸弹羊":
                BombSheepConfig bsConfig = new BombSheepConfig(new File(getDataFolder(), "bombsheep.yml"));
                currentGame = new BombSheepGame(this, bsConfig, players);
                break;
            case "击退战":
                KnockbackBattleConfig kbConfig = new KnockbackBattleConfig(new File(getDataFolder(), "knockbackbattle.yml"));
                currentGame = new KnockbackBattleGame(this, kbConfig, players);
                break;
            case "贪吃蛇":
                SnakeConfig snakeConfig = new SnakeConfig(new File(getDataFolder(), "snake.yml"));
                currentGame = new SnakeGame(this, snakeConfig, players);
                break;
            case "信仰方块":
                FaithBlockConfig fbConfig = new FaithBlockConfig(new File(getDataFolder(), "faithblock.yml"));
                currentGame = new FaithBlockGame(this, fbConfig, players);
                break;
            default:
                Bukkit.broadcastMessage(ChatColor.RED + "未知游戏: " + nextGame);
                startNextGame();
                return;
        }
        currentGame.start();
    }

    private void returnAllToLobby() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            teleportToLobby(p);
        }
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

    public void onMinigameEnd() {
        currentGame = null;
        if (pendingGames.isEmpty()) {
            broadcastRanking();
            resetAllGameWorlds();
            returnAllToLobby();
            Bukkit.getScheduler().runTaskLater(this, this::startLobbyScoreboard, 100L);
        } else {
            startNextGame();
        }
    }

    private void broadcastRanking() {
        if (playerScores.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "本轮无积分记录。");
            return;
        }
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(playerScores.entrySet());
        sorted.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());

        Bukkit.broadcastMessage(ChatColor.GOLD + "========== 总积分排行榜 ==========");
        int rank = 1;
        int count = 0;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            if (count >= 5) break;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                Bukkit.broadcastMessage(ChatColor.YELLOW + "第" + rank + "名：" + ChatColor.WHITE + player.getName() + " - " + entry.getValue() + "分");
            }
            rank++;
            count++;
        }
    }

    // ========== 大厅实时监控 ==========
    private void startLobbyMonitor() {
        lobbyMonitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().getName().equalsIgnoreCase(lobbyConfig.getWorldName())) {
                        if (p.getGameMode() != GameMode.ADVENTURE) {
                            p.setGameMode(GameMode.ADVENTURE);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    // ========== 事件处理 ==========
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (currentGame != null) {
            currentGame.addLatePlayer(player);
            lateJoinQueue.add(player);
            return;
        }
        teleportToLobby(player);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
                event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (isReturnItem(item)) {
                event.setCancelled(true);
                sendToBungeeLobby(player);
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (player.getWorld().getName().equalsIgnoreCase(lobbyConfig.getWorldName())) {
                event.setCancelled(true);
                player.setFoodLevel(20);
                player.setSaturation(10f);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (player.getWorld().getName().equalsIgnoreCase(lobbyConfig.getWorldName())) {
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    event.setCancelled(true);
                }
            }
        }
    }

    // 大厅保护（忽略大小写）
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().getWorld().getName().equalsIgnoreCase(lobbyConfig.getWorldName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getPlayer().getWorld().getName().equalsIgnoreCase(lobbyConfig.getWorldName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        if (event.getPlayer().getWorld().getName().equalsIgnoreCase(lobbyConfig.getWorldName())) {
            event.setCancelled(true);
        }
    }

    private boolean isReturnItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (item.getType() != lobbyConfig.getReturnMaterial()) return false;
        ItemMeta meta = item.getItemMeta();
        String displayName = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "";
        String expectedName = lobbyConfig.getReturnItemName();
        return ChatColor.stripColor(displayName).equals(ChatColor.stripColor(expectedName));
    }

    private void giveReturnItem(Player player) {
        // 返回大厅物品
        ItemStack returnItem = new ItemStack(lobbyConfig.getReturnMaterial(), 1);
        ItemMeta meta = returnItem.getItemMeta();
        meta.setDisplayName(lobbyConfig.getReturnItemName());
        returnItem.setItemMeta(meta);
        int slot = lobbyConfig.getReturnSlot();
        if (slot < 0 || slot > 8) slot = 8;
        player.getInventory().setItem(slot, returnItem);

        // 添加指南书到倒数第二个格子（索引7）
        player.getInventory().setItem(7, createGuideBook());
    }

    private ItemStack createGuideBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        meta.setTitle(ChatColor.GREEN + "小游戏指南" + ChatColor.GRAY + "(右键阅读)");
        meta.setAuthor(ChatColor.GRAY + "花雨庭");
        List<String> lore = new ArrayList<>();
//        lore.add(ChatColor.GRAY + "by花雨庭");
//        lore.add(ChatColor.GRAY + "Orignal");
        meta.setLore(lore);

        // 第一页
        String page1 = ChatColor.BOLD + "     ● 小游戏指南\n" + ChatColor.RESET +
                "\n" +
                ChatColor.BOLD + "信仰方块：\n" + ChatColor.RESET +
                "地面会逐渐变色至消失;最后生还者胜。\n\n" +
                ChatColor.BOLD + "疯狂的羊：\n" + ChatColor.RESET +
                "用剪刀右键羊剪羊毛：注意有些羊会爆炸;剪毛最多者胜。\n\n" +
                ChatColor.BOLD + "颜色配对：\n" + ChatColor.RESET +
                "跳到与背包内羊毛颜色相同的方块上;最后生还者胜。";

        // 第二页
        String page2 = ChatColor.BOLD + "掘一死战：\n" + ChatColor.RESET +
                "挖掉对手脚下的雪块，最后生还者获胜。\n\n" +
                ChatColor.BOLD + "击退战：\n" + ChatColor.RESET +
                "利用击退棒将其他玩家击飞，双击空格使用二段跳:最后生还者获胜。\n\n" +
                ChatColor.BOLD + "一击必杀：\n" + ChatColor.RESET +
                "利用多箭射杀其它对手;最后生还者获胜。";

        // 第三页
        String page3 = ChatColor.BOLD + "地雷战：\n" + ChatColor.RESET +
                "躲避沿途的地雷，用最快的速度达到终点。\n\n" +
                ChatColor.BOLD + "跑跑跳跳：\n" + ChatColor.RESET +
                "跳过障碍，向终点前进！\n\n" +
                ChatColor.BOLD + "方块配对：\n" + ChatColor.RESET +
                "挖掉前面的障碍，以最快速度到送终点。\n\n" +
                ChatColor.BOLD + "死胡同：\n" + ChatColor.RESET +
                "脚下的方块不断消失，向前奔跑吧!";

        // 第四页
        String page4 = ChatColor.BOLD + "      ● 更新日志\n" + ChatColor.RESET +
                ChatColor.BOLD + "❒ 版本：1.4.0\n" +
                ChatColor.BOLD + "❑ 日期：2026/6/6\n\n" + ChatColor.RESET +
                " 1一正式上线\n" +
                " ■包含多达11款小型游戏\n" +
                " ■节奏快捷、玩法类别多";

        // 第五页
        String page5 = ChatColor.BOLD + "      ● 关于我们\n" + ChatColor.RESET +
                "《烟雨庭》是由一群热爱我的世界的小伙伴们仿造的国风小游戏服务器《花雨庭》；上线伊始，我们与其他服达成合作,相信在大家的支持下，我们会越来越好!《烟雨庭》只为你的快乐而生。\n" + ChatColor.BOLD +
                "➡官网：www.mcyyt.cn\n" +
                "➡客服：即将上线\n" +
                "➡QQ群：892702519";

        meta.addPage(page1, page2, page3, page4, page5);
        book.setItemMeta(meta);
        return book;
    }

    private void sendToBungeeLobby(Player player) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(lobbyConfig.getBungeeServer());
        player.sendPluginMessage(this, "BungeeCord", out.toByteArray());
    }

    // ========== 命令处理 ==========
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("ncgame.admin")) {
                    sender.sendMessage(ChatColor.RED + "你没有权限执行此操作。");
                    return true;
                }
                reloadPlugin(sender);
                break;
            case "enable":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "用法: /ncgame enable <游戏名称>");
                    return true;
                }
                if (!sender.hasPermission("ncgame.admin")) {
                    sender.sendMessage(ChatColor.RED + "你没有权限执行此操作。");
                    return true;
                }
                setGameStatus(sender, args[1], true);
                break;
            case "disable":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "用法: /ncgame disable <游戏名称>");
                    return true;
                }
                if (!sender.hasPermission("ncgame.admin")) {
                    sender.sendMessage(ChatColor.RED + "你没有权限执行此操作。");
                    return true;
                }
                setGameStatus(sender, args[1], false);
                break;
            case "list":
                listGames(sender);
                break;
            case "sb":
                if (args.length > 1 && args[1].equalsIgnoreCase("reload")) {
                    if (!sender.hasPermission("ncgame.admin")) {
                        sender.sendMessage(ChatColor.RED + "你没有权限执行此操作。");
                        return true;
                    }
                    reloadScoreboard(sender);
                } else {
                    sender.sendMessage(ChatColor.RED + "用法: /ncgame sb reload");
                }
                break;
            default:
                showHelp(sender);
                break;
        }
        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========== NCGame 小游戏派对 ==========");
        sender.sendMessage(ChatColor.WHITE + "作者: " + ChatColor.AQUA + "nanchi765");
        sender.sendMessage(ChatColor.WHITE + "版本: " + ChatColor.AQUA + getDescription().getVersion());
        sender.sendMessage(ChatColor.WHITE + "介绍: BungeeCord 大厅子服小游戏插件");
        sender.sendMessage(ChatColor.YELLOW + "指令帮助:");
        sender.sendMessage(ChatColor.GREEN + "/ncgame reload " + ChatColor.WHITE + "- 重启插件");
        sender.sendMessage(ChatColor.GREEN + "/ncgame enable <游戏> " + ChatColor.WHITE + "- 启用游戏");
        sender.sendMessage(ChatColor.GREEN + "/ncgame disable <游戏> " + ChatColor.WHITE + "- 禁用游戏");
        sender.sendMessage(ChatColor.GREEN + "/ncgame list " + ChatColor.WHITE + "- 列出所有游戏");
        sender.sendMessage(ChatColor.GREEN + "/ncgame sb reload " + ChatColor.WHITE + "- 重载计分板配置");
    }

    private void reloadPlugin(CommandSender sender) {
        if (scoreboardManager != null) scoreboardManager.stopUpdating();
        if (countdownManager != null) countdownManager.cancelCountdown(null);
        lobbyConfig = new LobbyConfig(new File(getDataFolder(), "lobby.yml"));
        enableGameConfig = new EnableGameConfig(new File(getDataFolder(), "enablegame.yml"));
        countdownManager = new GameCountdownManager(this, lobbyConfig);
        scoreboardManager = new LobbyScoreboardManager(this, lobbyConfig, countdownManager);
        pendingGames.clear();
        lateJoinQueue.clear();
        playerScores.clear();
        sender.sendMessage(ChatColor.GREEN + "NCGame 已重新加载！");
        getLogger().info("NCGame 被 " + sender.getName() + " 重载。");
    }

    private void setGameStatus(CommandSender sender, String gameName, boolean enable) {
        Map<String, Boolean> allGames = enableGameConfig.getAllGames();
        if (!allGames.containsKey(gameName)) {
            sender.sendMessage(ChatColor.RED + "游戏 '" + gameName + "' 不存在！");
            return;
        }
        enableGameConfig.setGameEnabled(gameName, enable);
        String status = enable ? ChatColor.GREEN + "启用" : ChatColor.RED + "禁用";
        sender.sendMessage(ChatColor.YELLOW + "游戏 " + ChatColor.WHITE + gameName + " 已" + status);
    }

    private void listGames(CommandSender sender) {
        Map<String, Boolean> games = enableGameConfig.getAllGames();
        if (games.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "未找到任何游戏配置，请联系管理员检查 enablegame.yml 文件。");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "===== 游戏列表 =====");
        for (Map.Entry<String, Boolean> entry : games.entrySet()) {
            String name = entry.getKey();
            boolean enabled = entry.getValue();
            String status = enabled ? ChatColor.GREEN + "✔ 开启" : ChatColor.RED + "✘ 关闭";
            sender.sendMessage(ChatColor.WHITE + name + " : " + status);
        }
    }

    private void reloadScoreboard(CommandSender sender) {
        lobbyConfig.reload();
        if (scoreboardManager != null) scoreboardManager.stopUpdating();
        scoreboardManager = new LobbyScoreboardManager(this, lobbyConfig, countdownManager);
        sender.sendMessage(ChatColor.GREEN + "计分板配置已重新加载！");
    }
}