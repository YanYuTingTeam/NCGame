package cn.nanchi765.ncgame;

import org.bukkit.*;
import org.bukkit.block.Block;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import org.bukkit.util.Vector;

import java.util.*;

public class SnakeGame implements Listener, IMinigame {

    private final JavaPlugin plugin;
    private final SnakeConfig config;
    private final List<Player> players;
    private final List<Player> alivePlayers;
    private final List<Player> eliminatedPlayers;
    private final List<Player> lateSpectators = new ArrayList<>();
    private final Map<Player, BukkitTask> spectatorWatchTasks = new HashMap<>();
    private final List<String> vanishBlocks;
    private GameState state;
    private int prepareTimer;
    private int gameTimer;
    private BukkitTask gameTask;
    private BukkitTask vanishTask;
    private BukkitTask speedTask;   // 新增：持续保持速度的任务
    private final Set<UUID> wasOnGround = new HashSet<>();

    private enum GameState {
        WAITING, PREPARE, PLAYING, ENDED
    }

    public SnakeGame(JavaPlugin plugin, SnakeConfig config, List<Player> players) {
        this.plugin = plugin;
        this.config = config;
        this.players = new ArrayList<>(players);
        this.alivePlayers = new ArrayList<>(players);
        this.eliminatedPlayers = new ArrayList<>();
        this.vanishBlocks = config.getVanishBlocks();
        this.state = GameState.WAITING;
    }

    @Override
    public void start() {
        if (plugin instanceof NCGame) ((NCGame) plugin).stopLobbyScoreboard();

        Location spawn = config.getSpawnLocation();
        if (spawn == null) {
            plugin.getLogger().severe("贪吃蛇出生点未配置，游戏取消");
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
            p.setWalkSpeed(0.2f);
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
                if (prepareTimer <= 10) {
                    String title = ChatColor.YELLOW + String.valueOf(prepareTimer) + " 秒后";
                    String subtitle = ChatColor.DARK_GREEN + "脚下的方块即将消失，保持移动！";
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
        for (Player p : players) {
            p.setGameMode(GameMode.ADVENTURE);
        }
        gameTimer = config.getGameTime();

        // 放置屏障
        placeBarriers();

        // 设置 Timer 速度
        double timerValue = config.getTimer();
        final float speed = (float) Math.min(1.0f, 0.2 * timerValue);
        for (Player p : players) {
            p.setWalkSpeed(speed);
        }

        // 持续保持速度的任务（解决跳跃后丢失问题）
        speedTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.PLAYING) return;
                for (Player p : alivePlayers) {
                    if (p.getWalkSpeed() != speed) {
                        p.setWalkSpeed(speed);
                    }
                }
            }
        }.runTaskTimer(plugin, 5L, 5L); // 每5 tick（0.25秒）检查并修正速度

        // 方块消失定时器
        int interval = config.getVanishInterval();
        vanishTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.PLAYING) {
                    cancel();
                    return;
                }
                for (Player p : alivePlayers) {
                    Location loc = p.getLocation().getBlock().getRelative(0, -1, 0).getLocation();
                    Material type = loc.getBlock().getType();
                    if (vanishBlocks.contains(type.name())) {
                        loc.getBlock().setType(Material.AIR);
                    }
                }
            }
        }.runTaskTimer(plugin, interval, interval);

        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.PLAYING) { cancel(); return; }
                if (gameTimer > 0) gameTimer--;
                if (alivePlayers.size() < getRequiredPlayers() || (config.getGameTime() > 0 && gameTimer <= 0)) {
                    endGame();
                    cancel();
                    return;
                }
                updateScoreboard(gameTimer, config.getGameTime());
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void placeBarriers() {
        if (!config.isBarrierEnabled()) {
            plugin.getLogger().info("贪吃蛇屏障未启用，跳过放置");
            return;
        }
        Location pos1 = config.getBarrierPos1();
        Location pos2 = config.getBarrierPos2();
        if (pos1 == null || pos2 == null) {
            plugin.getLogger().warning("屏障坐标未配置或世界不存在");
            return;
        }
        World world = pos1.getWorld();
        int y = config.getBarrierY();
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block block = world.getBlockAt(x, y, z);
                if (block.getType() == Material.AIR) {
                    block.setType(Material.BARRIER);
                    count++;
                }
            }
        }
        plugin.getLogger().info("贪吃蛇屏障已放置 " + count + " 个");
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
            Objective obj = board.registerNewObjective("snake", "dummy");
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
            Objective obj = board.registerNewObjective("snake", "dummy");
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
        if (vanishTask != null) vanishTask.cancel();
        if (speedTask != null) speedTask.cancel();

        for (Player p : players) {
            p.setWalkSpeed(0.2f);
        }

        List<Player> ranking = new ArrayList<>(eliminatedPlayers);
        ranking.addAll(alivePlayers);
        Map<Player, Integer> playerRank = new LinkedHashMap<>();
        int rank = 1;
        if (!alivePlayers.isEmpty()) {
            for (Player p : alivePlayers) playerRank.put(p, 1);
            rank = 2;
        }
        List<Player> revElim = new ArrayList<>(eliminatedPlayers);
        Collections.reverse(revElim);
        for (Player p : revElim) playerRank.put(p, rank++);

        Map<Player, Integer> scoreAdd = new HashMap<>();
        for (Map.Entry<Player, Integer> entry : playerRank.entrySet()) {
            int r = entry.getValue();
            int add = r == 1 ? 10 : r == 2 ? 7 : r == 3 ? 5 : r == 4 ? 1 : 0;
            scoreAdd.put(entry.getKey(), add);
        }

        if (plugin instanceof NCGame) {
            NCGame ncGame = (NCGame) plugin;
            for (Map.Entry<Player, Integer> entry : scoreAdd.entrySet())
                ncGame.addScore(entry.getKey(), entry.getValue());
        }

        for (Player p : players) {
            int myRank = playerRank.getOrDefault(p, players.size());
            int myAdd = scoreAdd.getOrDefault(p, 0);
            p.sendMessage(ChatColor.GREEN + "你在本回合小游戏获得第 " + myRank + " 名！" + (myAdd > 0 ? " (+" + myAdd + "积分)" : ""));
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "===== 贪吃蛇 排行榜 =====");
        int count = 0;
        for (Map.Entry<Player, Integer> entry : playerRank.entrySet()) {
            if (count >= 4) break;
            Player p = entry.getKey();
            int r = entry.getValue();
            int add = scoreAdd.getOrDefault(p, 0);
            Bukkit.broadcastMessage(ChatColor.YELLOW + "第" + r + "名：" + ChatColor.WHITE + p.getName() + " (+" + add + "积分)");
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
        if (vanishTask != null) vanishTask.cancel();
        if (speedTask != null) speedTask.cancel();

        for (BukkitTask task : spectatorWatchTasks.values()) task.cancel();
        spectatorWatchTasks.clear();

        for (Player p : players) {
            p.setWalkSpeed(0.2f);
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

    // ========== 事件处理 ==========
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!players.contains(player) && !lateSpectators.contains(player)) return;

        // 旁观者模式保护
        if (lateSpectators.contains(player) && player.getGameMode() != GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SPECTATOR);
        }

        // 游戏进行中且玩家存活时：每次移动都强制更新行走速度，防止跳跃后丢失
        if (state == GameState.PLAYING && alivePlayers.contains(player)) {
            float targetSpeed = (float) Math.min(1.0f, 0.2 * config.getTimer());
            if (player.getWalkSpeed() != targetSpeed) {
                player.setWalkSpeed(targetSpeed);
            }

            // 可控跳跃高度（保持原有逻辑）
            boolean onGroundNow = player.isOnGround() || isNearGround(player);
            UUID uuid = player.getUniqueId();
            if (wasOnGround.contains(uuid) && !onGroundNow) {
                Vector vel = player.getVelocity();
                vel.setY(config.getJumpPower());
                player.setVelocity(vel);
            }
            if (onGroundNow) {
                wasOnGround.add(uuid);
            } else {
                wasOnGround.remove(uuid);
            }
        }

        // 存活玩家掉落死亡
        if (alivePlayers.contains(player) && state == GameState.PLAYING) {
            if (player.getLocation().getY() < config.getDeathY()) {
                eliminatePlayer(player);
                return;
            }
        }

        // 旁观者区域限制
        if (!alivePlayers.contains(player) || lateSpectators.contains(player)) {
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

    private boolean isNearGround(Player player) {
        Location loc = player.getLocation().clone().subtract(0, 1, 0);
        for (int i = 0; i < 3; i++) {
            if (loc.getBlock().getType() != Material.AIR) {
                return true;
            }
            loc.subtract(0, 1, 0);
        }
        return false;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (state == GameState.PREPARE || state == GameState.PLAYING) {
            event.setCancelled(true);
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
        player.setWalkSpeed(0.2f);

        for (Player p : players) {
            p.sendTitle("", ChatColor.AQUA + player.getName() + " 掉下去了", 0, 40, 10);
        }

        if (alivePlayers.size() < getRequiredPlayers()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (state == GameState.PLAYING) endGame();
            }, 20L);
        }
    }
}