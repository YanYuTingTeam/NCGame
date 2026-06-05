package cn.nanchi765.ncgame;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;

public class SpeedrunGame implements Listener, IMinigame {

    private final JavaPlugin plugin;
    private final SpeedrunConfig config;
    private final List<Player> players;
    private final List<Player> alivePlayers;
    private final List<Player> finishedPlayers;
    private final List<Player> eliminatedPlayers;
    private final List<Player> lateSpectators = new ArrayList<>();
    private final Map<Player, BukkitTask> spectatorWatchTasks = new HashMap<>();
    private final Map<Player, Location> spawnPoints = new HashMap<>();
    private GameState state;
    private int prepareTimer;
    private int gameTimer;
    private BukkitTask gameTask;

    private enum GameState {
        WAITING, PREPARE, PLAYING, ENDED
    }

    public SpeedrunGame(JavaPlugin plugin, SpeedrunConfig config, List<Player> players) {
        this.plugin = plugin;
        this.config = config;
        this.players = new ArrayList<>(players);
        this.alivePlayers = new ArrayList<>(players);
        this.finishedPlayers = new ArrayList<>();
        this.eliminatedPlayers = new ArrayList<>();
        this.state = GameState.WAITING;
    }

    @Override
    public void start() {
        if (plugin instanceof NCGame) ((NCGame) plugin).stopLobbyScoreboard();

        List<Location> spawns = new ArrayList<>(config.getSpawnLocations());
        Collections.shuffle(spawns);
        List<Player> shuffledPlayers = new ArrayList<>(players);
        Collections.shuffle(shuffledPlayers);

        for (int i = 0; i < shuffledPlayers.size(); i++) {
            Player p = shuffledPlayers.get(i);
            Location loc = (i < spawns.size()) ? spawns.get(i) : spawns.get(0);
            p.teleport(loc);
            spawnPoints.put(p, loc.clone());
            p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().clear();
            p.setFoodLevel(20);
            p.setSaturation(10);
            p.setHealth(20);
            p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
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
                if (state != GameState.PREPARE) { cancel(); return; }
                if (prepareTimer <= 0) { startPlayingPhase(); cancel(); return; }
                if (prepareTimer <= 5) { // 最后5秒中央倒计时
                    String title = ChatColor.YELLOW + String.valueOf(prepareTimer);
                    String subtitle = ChatColor.DARK_GREEN + "全速奔跑，第一个到达终点！";
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

    private void startPlayingPhase() {
        state = GameState.PLAYING;
        for (Player p : players) p.setGameMode(GameMode.ADVENTURE);
        gameTimer = config.getGameTime();
        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.PLAYING) { cancel(); return; }
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
            Objective obj = board.registerNewObjective("speedrun", "dummy");
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
            Objective obj = board.registerNewObjective("speedrun", "dummy");
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

        Map<Player, Integer> playerRank = new LinkedHashMap<>();
        int rank = 1;
        for (Player p : finishedPlayers) {
            playerRank.put(p, rank++);
        }

        Map<Player, Integer> scoreAdd = new HashMap<>();
        for (Map.Entry<Player, Integer> entry : playerRank.entrySet()) {
            int r = entry.getValue();
            int add = 0;
            if (r == 1) add = 10;
            else if (r == 2) add = 7;
            else if (r == 3) add = 5;
            else if (r == 4) add = 1;
            scoreAdd.put(entry.getKey(), add);
        }

        if (plugin instanceof NCGame) {
            NCGame ncGame = (NCGame) plugin;
            for (Map.Entry<Player, Integer> entry : scoreAdd.entrySet())
                ncGame.addScore(entry.getKey(), entry.getValue());
        }

        for (Player p : players) {
            if (finishedPlayers.contains(p)) {
                int myRank = playerRank.get(p);
                int myAdd = scoreAdd.getOrDefault(p, 0);
                p.sendMessage(ChatColor.GREEN + "你在本回合小游戏获得第 " + myRank + " 名！" + (myAdd > 0 ? " (+" + myAdd + "积分)" : ""));
            } else {
                p.sendMessage(ChatColor.RED + "你没有完成本轮游戏，无排名。");
            }
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "===== 急速酷跑 排行榜 =====");
        if (finishedPlayers.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.RED + "无人完成本轮游戏！");
        } else {
            int count = 0;
            for (Map.Entry<Player, Integer> entry : playerRank.entrySet()) {
                if (count >= 4) break;
                Player p = entry.getKey();
                int r = entry.getValue();
                int add = scoreAdd.getOrDefault(p, 0);
                Bukkit.broadcastMessage(ChatColor.YELLOW + "第" + r + "名：" + ChatColor.WHITE + p.getName() + " (+" + add + "积分)");
                count++;
            }
        }

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
                    if (player.getGameMode() != GameMode.SPECTATOR) player.setGameMode(GameMode.SPECTATOR);
                }
            }.runTaskTimer(plugin, 0L, 20L);
            spectatorWatchTasks.put(player, task);
        }
    }

    // ========== 事件处理 ==========
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!players.contains(player) && !lateSpectators.contains(player)) return;

        // 准备阶段锁定
        if (state == GameState.PREPARE && players.contains(player)) {
            Location spawn = spawnPoints.get(player);
            if (spawn != null && player.getLocation().distanceSquared(spawn) > 0.01) {
                player.teleport(spawn);
            }
            return;
        }

        if (lateSpectators.contains(player) && player.getGameMode() != GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SPECTATOR);
        }

        // 意外掉落传送回出生点
        if (players.contains(player) && state == GameState.PLAYING && alivePlayers.contains(player)) {
            if (player.getLocation().getY() < 0.0) {
                Location spawn = spawnPoints.get(player);
                if (spawn != null) {
                    player.teleport(spawn);
                    player.sendMessage(ChatColor.RED + "你掉下去了，已传送回出生点！");
                }
                return;
            }
        }

        // 胜利区域检测
        if (players.contains(player) && state == GameState.PLAYING && alivePlayers.contains(player)) {
            if (!finishedPlayers.contains(player) && config.isInWinRegion(player.getLocation())) {
                finishedPlayers.add(player);
                player.setGameMode(GameMode.SPECTATOR);
                player.teleport(config.getSpectatorSpawn());
                String msg = ChatColor.AQUA + player.getName() + " 到达终点！（第 " + finishedPlayers.size() + " 名）";
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle("", msg, 10, 70, 10);
                }
                if (finishedPlayers.size() == alivePlayers.size()) {
                    endGame();
                }
                return;
            }
        }

        // 旁观者区域限制
        if (lateSpectators.contains(player) || finishedPlayers.contains(player) || !alivePlayers.contains(player)) {
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
}