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

import java.util.*;

public class FaithBlockGame implements Listener, IMinigame {

    private final JavaPlugin plugin;
    private final FaithBlockConfig config;
    private final List<Player> players;
    private final List<Player> alivePlayers;
    private final List<Player> eliminatedPlayers;
    private final List<Player> lateSpectators = new ArrayList<>();
    private final Map<Player, BukkitTask> spectatorWatchTasks = new HashMap<>();
    private GameState state;
    private int prepareTimer;
    private int gameTimer;
    private BukkitTask gameTask;
    private BukkitTask changeTask;
    private final Random random = new Random();

    // 颜色映射：数据值 -> 阶段标识（用于概率判断）
    private static final byte WHITE = 0;
    private static final byte YELLOW = 4;
    private static final byte ORANGE = 1;
    private static final byte RED = 14;

    private enum GameState {
        WAITING, PREPARE, PLAYING, ENDED
    }

    public FaithBlockGame(JavaPlugin plugin, FaithBlockConfig config, List<Player> players) {
        this.plugin = plugin;
        this.config = config;
        this.players = new ArrayList<>(players);
        this.alivePlayers = new ArrayList<>(players);
        this.eliminatedPlayers = new ArrayList<>();
        this.state = GameState.WAITING;
    }

    @Override
    public void start() {
        if (plugin instanceof NCGame) ((NCGame) plugin).stopLobbyScoreboard();

        Location spawn = config.getSpawnLocation();
        if (spawn == null) {
            plugin.getLogger().severe("信仰方块出生点未配置，游戏取消");
            endGameDirectly();
            return;
        }

        // 初始化区域：全部白色羊毛
        fillRegionWithWool(WHITE);

        for (Player p : players) {
            p.teleport(spawn);
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

    private void fillRegionWithWool(byte color) {
        Location pos1 = config.getRegionPos1();
        Location pos2 = config.getRegionPos2();
        if (pos1 == null || pos2 == null) return;
        World world = pos1.getWorld();
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.AIR) {
                        block.setType(Material.WOOL);
                        block.setData(color);
                    }
                }
            }
        }
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
                    String subtitle = ChatColor.DARK_GREEN + "地面会变色并消失！" + ChatColor.WHITE + "白->" + ChatColor.YELLOW + "黄->" + ChatColor.GOLD + "橙->" + ChatColor.RED + "红->" + ChatColor.WHITE + "消失";
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

        // 启动定时变色任务（固定间隔）
        int interval = config.getChangeInterval(); // tick
        changeTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.PLAYING) return;
                doRandomColorChange();
            }
        }.runTaskTimer(plugin, 0L, interval); // 立即开始，然后每隔 interval tick 执行

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

    private void doRandomColorChange() {
        Location pos1 = config.getRegionPos1();
        Location pos2 = config.getRegionPos2();
        if (pos1 == null || pos2 == null) return;
        World world = pos1.getWorld();
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        // 本次变色的目标数量
        int target = config.getChangeAmountMin() + random.nextInt(config.getChangeAmountMax() - config.getChangeAmountMin() + 1);
        int changed = 0;
        int attempts = 0;
        int maxAttempts = target * 10; // 增加尝试次数，因为很多随机点可能不是羊毛

        while (changed < target && attempts < maxAttempts) {
            attempts++;
            int x = minX + random.nextInt(maxX - minX + 1);
            int y = minY + random.nextInt(maxY - minY + 1);
            int z = minZ + random.nextInt(maxZ - minZ + 1);

            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;

            Block block = world.getBlockAt(x, y, z);
            if (block.getType() == Material.WOOL) {
                byte currentData = block.getData();
                byte nextData = tryUpgradeColor(currentData);
                if (nextData != currentData) {
                    if (nextData == -1) {
                        block.setType(Material.AIR);
                    } else {
                        block.setData(nextData);
                    }
                    changed++;
                }
            }
        }
    }

    /**
     * 根据当前颜色和配置的概率决定是否升级，返回新颜色（或 -1 表示消失），若未升级则返回原颜色。
     */
    private byte tryUpgradeColor(byte current) {
        int prob = random.nextInt(100) + 1; // 1-100
        if (current == WHITE) {
            if (prob <= config.getProbWhiteToYellow()) return YELLOW;
        } else if (current == YELLOW) {
            if (prob <= config.getProbYellowToOrange()) return ORANGE;
        } else if (current == ORANGE) {
            if (prob <= config.getProbOrangeToRed()) return RED;
        } else if (current == RED) {
            if (prob <= config.getProbRedToAir()) return -1; // 消失
        }
        return current; // 不变
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
            Objective obj = board.registerNewObjective("faith", "dummy");
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
            Objective obj = board.registerNewObjective("faith", "dummy");
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
        if (changeTask != null) changeTask.cancel();

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
            for (Map.Entry<Player, Integer> entry : scoreAdd.entrySet()) {
                ncGame.addScore(entry.getKey(), entry.getValue());
            }
        }

        for (Player p : players) {
            int myRank = playerRank.getOrDefault(p, players.size());
            int myAdd = scoreAdd.getOrDefault(p, 0);
            p.sendMessage(ChatColor.GREEN + "你在本回合小游戏获得第 " + myRank + " 名！" + (myAdd > 0 ? " (+" + myAdd + "积分)" : ""));
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "===== 信仰方块 排行榜 =====");
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
        if (changeTask != null) changeTask.cancel();

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

    // ========== 事件处理 ==========
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!players.contains(player) && !lateSpectators.contains(player)) return;

        if (lateSpectators.contains(player) && player.getGameMode() != GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SPECTATOR);
        }

        // 死亡高度
        if (alivePlayers.contains(player) && (state == GameState.PLAYING || state == GameState.PREPARE)) {
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