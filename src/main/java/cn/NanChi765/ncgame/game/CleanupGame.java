package cn.nanchi765.ncgame.game;

import cn.nanchi765.ncgame.IMinigame;
import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.CleanupConfig;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.*;

public class CleanupGame implements Listener, IMinigame {

    private final JavaPlugin plugin;
    private final CleanupConfig config;
    private final List<Player> players;
    private final List<Player> alivePlayers;
    private final List<Player> eliminatedPlayers;
    private final Map<Player, Integer> blockCounts = new HashMap<>();
    private final List<Player> lateSpectators = new ArrayList<>();
    private final Map<Player, BukkitTask> spectatorWatchTasks = new HashMap<>();
    private GameState state;
    private int prepareTimer;
    private int gameTimer;
    private BukkitTask gameTask;

    public CleanupGame(JavaPlugin plugin, CleanupConfig config, List<Player> players) {
        this.plugin = plugin;
        this.config = config;
        this.players = new ArrayList<>(players);
        this.alivePlayers = new ArrayList<>(players);
        this.eliminatedPlayers = new ArrayList<>();
        for (Player p : players) {
            blockCounts.put(p, 0);
        }
        this.state = GameState.WAITING;
    }

    @Override
    public void start() {
        if (plugin instanceof NCGame) ((NCGame) plugin).stopLobbyScoreboard();

        Location spawn = config.getSpawnLocation();
        if (spawn == null) {
            plugin.getLogger().severe("大扫除出生点未配置，游戏取消");
            endGameDirectly();
            return;
        }

        for (Player p : players) {
            p.teleport(spawn);
            p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().clear();
            p.setFoodLevel(20);
            p.setSaturation(10);
            p.setHealth(20);
            p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        }

        // 发放工具
        ItemStack shears = createItem(config.getShearsMaterial(), config.getShearsName());
        ItemStack axe = createItem(config.getAxeMaterial(), config.getAxeName());
        ItemStack shovel = createItem(config.getShovelMaterial(), config.getShovelName());

        for (Player p : players) {
            p.getInventory().setItem(0, shears);
            p.getInventory().setItem(1, axe);
            p.getInventory().setItem(2, shovel);
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startPreparePhase();
    }

    private void startPreparePhase() {
        state = GameState.PREPARE;
        prepareTimer = config.getPrepareTime();

        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.PREPARE) {
                    cancel();
                    return;
                }
                if (prepareTimer <= 0) {
                    startPlayingPhase();
                    cancel();
                    return;
                }
                if (prepareTimer <= 10) {
                    String title = ChatColor.YELLOW + String.valueOf(prepareTimer) + " 秒后";
                    String subtitle = ChatColor.DARK_GREEN + "破坏方块，收集最多积分！";
                    for (Player p : players) {
                        p.sendTitle(title, subtitle, 0, 25, 5);
                        if (config.isCountdownSoundEnabled())
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_PLING, 1.0f, 1.0f);
                    }
                }
                updateScoreboard(prepareTimer, config.getPrepareTime());
                prepareTimer--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private void startPlayingPhase() {
        state = GameState.PLAYING;
        for (Player p : players) {
            p.setGameMode(GameMode.SURVIVAL);
        }
        gameTimer = config.getGameTime();
        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.PLAYING) {
                    cancel();
                    return;
                }
                if (gameTimer > 0) gameTimer--;
                if (gameTimer <= 5 && gameTimer > 0) {
                    String title = ChatColor.YELLOW + String.valueOf(gameTimer) + " 秒后";
                    String subtitle = ChatColor.DARK_GREEN + "游戏即将结束";
                    for (Player p : players) {
                        p.sendTitle(title, subtitle, 0, 20, 10);
                        if (config.isCountdownSoundEnabled())
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_PLING, 1.0f, 1.0f);
                    }
                }
                if (alivePlayers.size() < getRequiredPlayers() || (config.getGameTime() > 0 && gameTimer <= 0)) {
                    endGame();
                    cancel();
                    return;
                }
                updateScoreboard(gameTimer, config.getGameTime());
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private int getRequiredPlayers() {
        if (plugin instanceof NCGame) return ((NCGame) plugin).getLobbyConfig().getRequiredPlayers();
        return 2;
    }

    private void updateScoreboard(int timeLeft, int totalTime) {
        List<String> lines = config.getScoreboardLines();
        if (lines.isEmpty()) return;
        ScoreboardManager sm = Bukkit.getScoreboardManager();

        for (Player p : players) {
            Scoreboard board = sm.getNewScoreboard();
            Objective obj = board.registerNewObjective("cleanup", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.setDisplayName(config.getScoreboardTitle());
            int score = lines.size();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int playerScore = 0;
                if (plugin instanceof NCGame) playerScore = ((NCGame) plugin).getPlayerScore(p);
                String replaced = replaceVariables(line, timeLeft, totalTime, playerScore);
                obj.getScore(makeUnique(replaced, i)).setScore(score);
                score--;
            }
            p.setScoreboard(board);
        }

        for (Player p : lateSpectators) {
            Scoreboard board = sm.getNewScoreboard();
            Objective obj = board.registerNewObjective("cleanup", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.setDisplayName(config.getScoreboardTitle());
            int score = lines.size();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int playerScore = 0;
                if (plugin instanceof NCGame) playerScore = ((NCGame) plugin).getPlayerScore(p);
                String replaced = replaceVariables(line, timeLeft, totalTime, playerScore);
                obj.getScore(makeUnique(replaced, i)).setScore(score);
                score--;
            }
            p.setScoreboard(board);
        }
    }

    private String replaceVariables(String line, int timeLeft, int totalTime, int playerScore) {
        return line.replace("{time_left}", String.valueOf(timeLeft))
                .replace("{total_time}", String.valueOf(totalTime))
                .replace("{score}", String.valueOf(playerScore));
    }

    private String makeUnique(String text, int index) {
        if (text.trim().isEmpty()) return ChatColor.BLACK.toString();
        ChatColor[] colors = ChatColor.values();
        return text + colors[index % colors.length];
    }

    private void endGame() {
        state = GameState.ENDED;

        // 按破坏数量排序：多的在前
        List<Map.Entry<Player, Integer>> sorted = new ArrayList<>(blockCounts.entrySet());
        sorted.sort(Map.Entry.<Player, Integer>comparingByValue().reversed());

        Map<Player, Integer> playerRank = new LinkedHashMap<>();
        int rank = 1;
        for (Map.Entry<Player, Integer> entry : sorted) {
            playerRank.put(entry.getKey(), rank++);
        }

        Map<Player, Integer> scoreAdd = new HashMap<>();
        for (Map.Entry<Player, Integer> entry : playerRank.entrySet()) {
            Player p = entry.getKey();
            int r = entry.getValue();
            int add = 0;
            if (r == 1) add = 10;
            else if (r == 2) add = 7;
            else if (r == 3) add = 5;
            else if (r == 4) add = 1;
            scoreAdd.put(p, add);
        }

        if (plugin instanceof NCGame) {
            NCGame ncGame = (NCGame) plugin;
            for (Map.Entry<Player, Integer> entry : scoreAdd.entrySet()) {
                ncGame.addScore(entry.getKey(), entry.getValue());
            }
        }

        for (Player p : players) {
            int myRank = playerRank.getOrDefault(p, players.size());
            int myAdd = scoreAdd.getOrDefault(p, 0);
            int blocks = blockCounts.getOrDefault(p, 0);
            p.sendMessage(ChatColor.GREEN + "你破坏了 " + blocks + " 个方块，获得第 " + myRank + " 名！" + (myAdd > 0 ? " (+" + myAdd + "积分)" : ""));
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "===== 大扫除 排行榜 =====");
        int count = 0;
        for (Map.Entry<Player, Integer> entry : playerRank.entrySet()) {
            if (count >= 4) break;
            Player p = entry.getKey();
            int r = entry.getValue();
            int add = scoreAdd.getOrDefault(p, 0);
            int blocks = blockCounts.getOrDefault(p, 0);
            Bukkit.broadcastMessage(ChatColor.YELLOW + "第" + r + "名：" + ChatColor.WHITE + p.getName() + " (破坏 " + blocks + " 块) (+" + add + "积分)");
            count++;
        }

        cleanupAndReturnLobby();
    }

    private void endGameDirectly() {
        state = GameState.ENDED;
        cleanupAndReturnLobby();
    }

    private void cleanupAndReturnLobby() {
        HandlerList.unregisterAll(this);
        if (gameTask != null) gameTask.cancel();

        for (BukkitTask task : spectatorWatchTasks.values()) task.cancel();
        spectatorWatchTasks.clear();

        for (Player p : players) {
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            p.updateInventory();
        }
        for (Player p : lateSpectators) {
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            p.updateInventory();
        }
        for (Player p : players) p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        for (Player p : lateSpectators) p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());

        if (plugin instanceof NCGame) {
            ((NCGame) plugin).onMinigameEnd();
        }
    }

    @Override
    public boolean isRunning() {
        return state != GameState.ENDED && state != GameState.WAITING;
    }

    @Override
    public void addLatePlayer(Player player) {
        if (!lateSpectators.contains(player)) {
            lateSpectators.add(player);
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(config.getSpectatorSpawn());
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || !lateSpectators.contains(player)) {
                        cancel();
                        spectatorWatchTasks.remove(player);
                        return;
                    }
                    if (player.getGameMode() != GameMode.SPECTATOR) {
                        player.setGameMode(GameMode.SPECTATOR);
                    }
                }
            }.runTaskTimer(plugin, 0L, 20L);
            spectatorWatchTasks.put(player, task);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (state != GameState.PLAYING) {
            event.setCancelled(true);
            return;
        }
        Player player = event.getPlayer();
        if (!players.contains(player) || !alivePlayers.contains(player)) return;

        // 高度限制：低于或等于配置的 Y 轴禁止破坏
        int limitY = config.getBreakLimitY();
        if (limitY >= 0 && event.getBlock().getY() <= limitY) {
            event.setCancelled(true);
            if (config.isDebugEnabled()) {
                player.sendMessage("§c此高度禁止破坏 (Y ≤ " + limitY + ")");
            }
            return;
        }

        Material type = event.getBlock().getType();
        if (!config.getBreakableBlocks().contains(type.name())) {
            if (config.isDebugEnabled()) {
                player.sendMessage("§c无法破坏: " + type.name());
            }
            event.setCancelled(true);
        } else {
            event.setDropItems(false);
            blockCounts.merge(player, 1, Integer::sum);
            player.sendTitle("", ChatColor.GREEN + "你获得了 " + ChatColor.YELLOW + "1" + ChatColor.GREEN + " 分", 5, 20, 5);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!players.contains(player) && !lateSpectators.contains(player)) return;

        // 旁观者模式保护
        if (lateSpectators.contains(player) && player.getGameMode() != GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SPECTATOR);
        }

        // 旁观者区域限制
        if (lateSpectators.contains(player)) {
            Location loc = player.getLocation();
            Location center = config.getSpectatorRegionCenter();
            if (center != null && loc.getWorld().equals(center.getWorld())) {
                double radius = config.getSpectatorRegionRadius();
                if (loc.distanceSquared(center) > radius * radius) {
                    player.teleport(config.getSpectatorSpawn());
                }
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (state == GameState.PREPARE || state == GameState.PLAYING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (players.contains(player) && (state == GameState.PREPARE || state == GameState.PLAYING)) {
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (players.contains(player)) {
                event.setCancelled(true);
                player.setFoodLevel(20);
                player.setSaturation(10f);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (players.contains(player)) {
            eliminatePlayer(player);
        }
        if (lateSpectators.contains(player)) {
            lateSpectators.remove(player);
            BukkitTask task = spectatorWatchTasks.remove(player);
            if (task != null) task.cancel();
        }
    }

    private void eliminatePlayer(Player player) {
        if (!alivePlayers.remove(player)) return;
        eliminatedPlayers.add(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(config.getSpectatorSpawn());

        for (Player p : players) {
            p.sendTitle("", ChatColor.AQUA + player.getName() + " 被淘汰了", 0, 40, 10);
        }

        if (alivePlayers.size() < getRequiredPlayers()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (state == GameState.PLAYING) endGame();
            }, 20L);
        }
    }

    private enum GameState {
        WAITING, PREPARE, PLAYING, ENDED
    }
}