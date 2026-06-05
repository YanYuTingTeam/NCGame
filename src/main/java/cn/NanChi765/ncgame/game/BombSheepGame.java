package cn.nanchi765.ncgame.game;

import cn.nanchi765.ncgame.IMinigame;
import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.BombSheepConfig;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;

public class BombSheepGame implements Listener, IMinigame {

    private final JavaPlugin plugin;
    private final BombSheepConfig config;
    private final List<Player> players;
    private final Map<Player, Integer> woolScores = new HashMap<>();
    private final List<Player> alivePlayers;
    private final List<Player> eliminatedPlayers;
    private final List<Player> lateSpectators = new ArrayList<>();
    private final Map<Player, BukkitTask> spectatorWatchTasks = new HashMap<>();
    private final List<Sheep> activeSheep = new ArrayList<>();
    private GameState state;
    private int prepareTimer;
    private int gameTimer;
    private BukkitTask gameTask;
    private BukkitTask sheepSpawnTask;
    private Random random = new Random();

    private enum GameState {
        WAITING, PREPARE, PLAYING, ENDED
    }

    public BombSheepGame(JavaPlugin plugin, BombSheepConfig config, List<Player> players) {
        this.plugin = plugin;
        this.config = config;
        this.players = new ArrayList<>(players);
        this.alivePlayers = new ArrayList<>(players);
        this.eliminatedPlayers = new ArrayList<>();
        for (Player p : players) {
            woolScores.put(p, 0);
        }
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
            p.setGameMode(GameMode.ADVENTURE);   // 全程冒险模式
            p.getInventory().clear();
            p.setFoodLevel(20);
            p.setSaturation(10);
            p.setHealth(20);
            p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        }

        ItemStack shears = new ItemStack(Material.SHEARS, 1);
        ItemMeta meta = shears.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "剪毛剪刀");
        shears.setItemMeta(meta);
        for (Player p : players) {
            p.getInventory().setItem(0, shears);
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
                    String subtitle = ChatColor.DARK_GREEN + "剪羊毛，但要小心炸弹羊！";
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
        for (Player p : players) p.setGameMode(GameMode.ADVENTURE); // 冒险模式
        gameTimer = config.getGameTime();

        sheepSpawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.PLAYING) {
                    cancel();
                    return;
                }
                activeSheep.removeIf(sheep -> sheep == null || !sheep.isValid());
                int max = config.getMaxSheep();
                while (activeSheep.size() < max) {
                    Location loc = findValidSpawnLocation();
                    if (loc == null) break;
                    Sheep sheep = loc.getWorld().spawn(loc, Sheep.class);
                    sheep.setColor(DyeColor.WHITE);
                    sheep.setCustomName("jeb_");
                    sheep.setCustomNameVisible(false);
                    activeSheep.add(sheep);
                }
            }
        }.runTaskTimer(plugin, 0L, config.getSpawnInterval());

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

    private Location findValidSpawnLocation() {
        Location pos1 = config.getSheepRegionPos1();
        Location pos2 = config.getSheepRegionPos2();
        if (pos1 == null || pos2 == null) return null;
        World world = pos1.getWorld();
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());

        for (int attempt = 0; attempt < 10; attempt++) {
            double x = minX + random.nextDouble() * (maxX - minX);
            double z = minZ + random.nextDouble() * (maxZ - minZ);
            for (int y = maxY; y >= minY; y--) {
                Block block = world.getBlockAt((int)Math.floor(x), y, (int)Math.floor(z));
                if (block.getType().isSolid()) {
                    return block.getLocation().add(0.5, 1, 0.5);
                }
            }
        }
        return null;
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
            Objective obj = board.registerNewObjective("bombsheep", "dummy");
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
            Objective obj = board.registerNewObjective("bombsheep", "dummy");
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
        for (Sheep s : activeSheep) { if (s != null && s.isValid()) s.remove(); }
        activeSheep.clear();
        if (sheepSpawnTask != null) sheepSpawnTask.cancel();

        // 仅统计存活玩家的羊毛数进行排名（淘汰玩家不参与排名）
        Map<Player, Integer> aliveWoolScores = new HashMap<>();
        for (Player p : alivePlayers) {
            aliveWoolScores.put(p, woolScores.getOrDefault(p, 0));
        }

        List<Map.Entry<Player, Integer>> sorted = new ArrayList<>(aliveWoolScores.entrySet());
        sorted.sort(Map.Entry.<Player, Integer>comparingByValue().reversed());

        Map<Player, Integer> playerRank = new LinkedHashMap<>();
        int rank = 1;
        for (Map.Entry<Player, Integer> entry : sorted) {
            playerRank.put(entry.getKey(), rank++);
        }

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

        // 向玩家发送结果
        for (Player p : players) {
            if (alivePlayers.contains(p)) {
                int myWool = woolScores.getOrDefault(p, 0);
                int myRank = playerRank.get(p);
                int myAdd = scoreAdd.getOrDefault(p, 0);
                p.sendMessage(ChatColor.GREEN + "你收集了 " + myWool + " 个羊毛，第 " + myRank + " 名！" + (myAdd > 0 ? " (+" + myAdd + "积分)" : ""));
            } else {
                p.sendMessage(ChatColor.RED + "你已被淘汰，不参与排名。");
            }
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "===== 炸弹羊 排行榜 =====");
        if (aliveWoolScores.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.RED + "无存活玩家！");
        } else {
            int count = 0;
            for (Map.Entry<Player, Integer> entry : playerRank.entrySet()) {
                if (count >= 4) break;
                Player p = entry.getKey();
                int r = entry.getValue();
                int add = scoreAdd.getOrDefault(p, 0);
                int wool = woolScores.getOrDefault(p, 0);
                Bukkit.broadcastMessage(ChatColor.YELLOW + "第" + r + "名：" + ChatColor.WHITE + p.getName() + " (羊毛 " + wool + ") (+" + add + "积分)");
                count++;
            }
        }

        cleanupAndReturnLobby();
    }

    private void cleanupAndReturnLobby() {
        HandlerList.unregisterAll(this);
        if (gameTask != null) gameTask.cancel();
        if (sheepSpawnTask != null) sheepSpawnTask.cancel();

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

        if (plugin instanceof NCGame) ((NCGame) plugin).onMinigameEnd();
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
    public void onShear(PlayerShearEntityEvent event) {
        if (state != GameState.PLAYING) {
            event.setCancelled(true);
            return;
        }
        Player player = event.getPlayer();
        if (!players.contains(player) || !alivePlayers.contains(player)) return; // 只有存活玩家可以剪

        if (event.getEntity() instanceof Sheep) {
            Sheep sheep = (Sheep) event.getEntity();
            if (!activeSheep.contains(sheep)) return;

            if (random.nextDouble() < config.getExplodeChance()) {
                player.getWorld().createExplosion(sheep.getLocation(), (float) config.getExplosionPower(), true);
                player.setHealth(Math.max(0, player.getHealth() - config.getExplodeDamage()));
                player.sendMessage(ChatColor.RED + "炸弹羊爆炸了！你受到了伤害。");
                sheep.remove();
                activeSheep.remove(sheep);
                event.setCancelled(true);
                return;
            }

            // 正常剪毛，掉落羊毛
            player.playSound(player.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1.0f, 1.0f);
            activeSheep.remove(sheep);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sheep.isValid()) sheep.remove();
            });
        }
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        if (state != GameState.PLAYING) return;
        Player player = event.getPlayer();
        if (!players.contains(player) || !alivePlayers.contains(player)) return; // 只有存活玩家捡羊毛计分
        ItemStack item = event.getItem().getItemStack();
        if (item.getType() == Material.WOOL && item.getDurability() == 0) {
            woolScores.merge(player, item.getAmount(), Integer::sum);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!players.contains(player) && !lateSpectators.contains(player)) return;

        if (lateSpectators.contains(player) && player.getGameMode() != GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SPECTATOR);
        }

        if (alivePlayers.contains(player) && state == GameState.PLAYING) {
            if (player.getLocation().getY() < config.getDeathY()) {
                eliminatePlayer(player);
                return;
            }
        }

        if (!alivePlayers.contains(player) || lateSpectators.contains(player)) {
            Location loc = player.getLocation();
            Location center = config.getSpectatorRegionCenter();
            if (center != null && loc.getWorld().equals(center.getWorld())) {
                if (loc.distanceSquared(center) > config.getSpectatorRegionRadius() * config.getSpectatorRegionRadius()) {
                    player.teleport(config.getSpectatorSpawn());
                }
            }
        }
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        // 禁止玩家间伤害
        if (state == GameState.PREPARE || state == GameState.PLAYING) {
            if (event.getEntity() instanceof Player) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // 羊免疫摔落伤害
        if (event.getEntity() instanceof Sheep) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                event.setCancelled(true);
            }
        }
        // 玩家禁止所有伤害（爆炸伤害已在 onShear 中手动扣除）
        if (event.getEntity() instanceof Player) {
            if (state == GameState.PREPARE || state == GameState.PLAYING) {
                event.setCancelled(true);
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
    public void onBlockPlace(BlockPlaceEvent event) {
        if (state == GameState.PREPARE || state == GameState.PLAYING) {
            event.setCancelled(true);
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
        if (players.contains(player)) eliminatePlayer(player);
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