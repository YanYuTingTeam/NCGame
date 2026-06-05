package cn.nanchi7651.ncgame.game;

import cn.nanchi7651.ncgame.NCGame;
import cn.nanchi7651.ncgame.config.game.BombSheepConfig;
import cn.nanchi7651.ncgame.manager.GameManager;
import cn.nanchi7651.ncgame.utils.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

import static cn.nanchi7651.ncgame.utils.Utils.isAlive;
import static cn.nanchi7651.ncgame.utils.Utils.isGameActive;

public class BombSheepGame implements Listener, IMinigame {

    private final JavaPlugin plugin;
    private final GameManager gameManager;
    @Getter
    private final BombSheepConfig config;
    private final Map<Player, Integer> woolScores = new HashMap<>();
    private final List<Sheep> activeSheep = new ArrayList<>();
    private final Random random = new Random();
    private BukkitTask sheepSpawnTask;

    public BombSheepGame(JavaPlugin plugin, BombSheepConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.gameManager = NCGame.getInstance().getGameManager();
        for (Player p : gameManager.getAlivePlayers()) {
            woolScores.put(p, 0);
        }
    }

    @Override
    public void prepare() {

        List<Player> players = gameManager.getAlivePlayers();
        List<Location> spawns = new ArrayList<>(config.getSpawnLocations());
        Collections.shuffle(spawns);

        List<Player> shuffledPlayers = new ArrayList<>(players);
        Collections.shuffle(shuffledPlayers);

        for (int i = 0; i < shuffledPlayers.size(); i++) {
            Player p = shuffledPlayers.get(i);
            Location loc = (i < spawns.size()) ? spawns.get(i) : spawns.get(0);
            p.teleport(loc);
        }

        ItemStack shears = createShears();
        for (Player p : players) {
            p.getInventory().setItem(0, shears);
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void start() {


        sheepSpawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isGameActive(BombSheepGame.this)) {
                    cancel();
                    return;
                }
                activeSheep.removeIf(s -> s == null || !s.isValid());
                int max = config.getMaxSheep();
                while (activeSheep.size() < max) {
                    Location loc = findValidSpawnLocation();
                    if (loc == null) break;
                    spawnBombSheep(loc);
                }
            }
        }.runTaskTimer(plugin, 0L, config.getSpawnInterval());
    }

    @Override
    public void end() {
        cleanupSheep();
        cancelSheepTask();

        List<Player> players = gameManager.getPlayers();
        List<Player> alivePlayers = gameManager.getAlivePlayers();

        // 仅统计存活玩家的羊毛数进行排名
        Map<Player, Integer> aliveWoolScores = new LinkedHashMap<>();
        for (Player p : alivePlayers) {
            aliveWoolScores.put(p, woolScores.getOrDefault(p, 0));
        }

        // 按羊毛数降序排序
        List<Map.Entry<Player, Integer>> sorted = new ArrayList<>(aliveWoolScores.entrySet());
        sorted.sort(Map.Entry.<Player, Integer>comparingByValue().reversed());

        // 构建排名和积分
        Map<Player, Integer> playerRank = new LinkedHashMap<>();
        Map<Player, Integer> scoreAdd = new HashMap<>();
        int rank = 1;
        for (Map.Entry<Player, Integer> entry : sorted) {
            playerRank.put(entry.getKey(), rank);
            scoreAdd.put(entry.getKey(), rankToScore(rank));
            rank++;
        }

        // 发放积分
        addScores(scoreAdd);

        // 向玩家发送个人结果
        sendPlayerResults(players, alivePlayers, playerRank, scoreAdd);

        // 广播排行榜
        broadcastLeaderboard(playerRank, scoreAdd);

        // 清理
        HandlerList.unregisterAll(this);
    }

    // ==================== 事件处理 ====================

    @EventHandler
    public void onShear(PlayerShearEntityEvent event) {
        if (!isGameActive(this)) {
            event.setCancelled(true);
            return;
        }
        Player player = event.getPlayer();
        if (!isAlive(player)) return;
        if (!(event.getEntity() instanceof Sheep)) return;

        Sheep sheep = (Sheep) event.getEntity();
        if (!activeSheep.contains(sheep)) return;

        // 炸弹判定
        if (random.nextDouble() < config.getExplodeChance()) {
            triggerExplosion(player, sheep);
            event.setCancelled(true);
            return;
        }

        // 正常剪毛
        player.playSound(player.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1.0f, 1.0f);
        activeSheep.remove(sheep);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (sheep.isValid()) sheep.remove();
        });
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        if (!isGameActive(this)) return;
        Player player = event.getPlayer();
        if (!isAlive(player)) return;

        ItemStack item = event.getItem().getItemStack();
        if (item.getType() == Material.WOOL && item.getDurability() == 0) {
            woolScores.merge(player, item.getAmount(), Integer::sum);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!isGameActive(this)) return;
        Player player = event.getPlayer();
        List<Player> alivePlayers = gameManager.getAlivePlayers();

        if (alivePlayers.contains(player)) {
            if (player.getLocation().getY() < config.getDeathY()) {
                eliminatePlayer(player);
            }
        }
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!isGameActive(this)) return;
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!isGameActive(this)) return;
        if (event.getEntity() instanceof Sheep && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isGameActive(this)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isGameActive(this)) return;
        event.setCancelled(true);
    }

    /**
     * 创建剪毛剪刀物品
     */
    private ItemStack createShears() {
        ItemStack shears = new ItemStack(Material.SHEARS, 1);
        ItemMeta meta = shears.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "剪毛剪刀");
        shears.setItemMeta(meta);
        return shears;
    }

    /**
     * 在指定位置生成炸弹羊
     */
    private void spawnBombSheep(Location loc) {
        Sheep sheep = loc.getWorld().spawn(loc, Sheep.class);
        sheep.setColor(DyeColor.WHITE);
        sheep.setCustomName("jeb_");
        sheep.setCustomNameVisible(false);
        activeSheep.add(sheep);
    }

    /**
     * 触发炸弹羊爆炸
     */
    private void triggerExplosion(Player player, Sheep sheep) {
        player.getWorld().createExplosion(sheep.getLocation(), (float) config.getExplosionPower(), true);
        player.setHealth(Math.max(0, player.getHealth() - config.getExplodeDamage()));
        player.sendMessage(ChatColor.RED + "炸弹羊爆炸了！你受到了伤害。");
        sheep.remove();
        activeSheep.remove(sheep);
    }

    /**
     * 清除所有存活炸弹羊
     */
    private void cleanupSheep() {
        for (Sheep s : activeSheep) {
            if (s != null && s.isValid()) s.remove();
        }
        activeSheep.clear();
    }

    /**
     * 取消刷羊任务
     */
    private void cancelSheepTask() {
        if (sheepSpawnTask != null) {
            sheepSpawnTask.cancel();
            sheepSpawnTask = null;
        }
    }

    /**
     * 名次转积分
     */
    private int rankToScore(int rank) {
        switch (rank) {
            case 1:
                return 10;
            case 2:
                return 7;
            case 3:
                return 5;
            case 4:
                return 1;
            default:
                return 0;
        }
    }

    /**
     * 批量发放积分
     */
    private void addScores(Map<Player, Integer> scoreAdd) {
        if (!(plugin instanceof NCGame)) return;
        NCGame ncGame = (NCGame) plugin;
        for (Map.Entry<Player, Integer> entry : scoreAdd.entrySet()) {
            ncGame.addScore(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 向所有玩家发送个人结果
     */
    private void sendPlayerResults(List<Player> players, List<Player> alivePlayers,
                                   Map<Player, Integer> playerRank, Map<Player, Integer> scoreAdd) {
        for (Player p : players) {
            if (alivePlayers.contains(p)) {
                int myWool = woolScores.getOrDefault(p, 0);
                int myRank = playerRank.getOrDefault(p, 0);
                int myAdd = scoreAdd.getOrDefault(p, 0);
                String bonus = myAdd > 0 ? " (+" + myAdd + "积分)" : "";
                p.sendMessage(ChatColor.GREEN + "你收集了 " + myWool + " 个羊毛，第 " + myRank + " 名！" + bonus);
            } else {
                p.sendMessage(ChatColor.RED + "你已被淘汰，不参与排名。");
            }
        }
    }

    /**
     * 广播排行榜
     */
    private void broadcastLeaderboard(Map<Player, Integer> playerRank, Map<Player, Integer> scoreAdd) {
        Bukkit.broadcastMessage(ChatColor.GOLD + "===== 炸弹羊 排行榜 =====");
        if (playerRank.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.RED + "无存活玩家！");
            return;
        }
        int count = 0;
        for (Map.Entry<Player, Integer> entry : playerRank.entrySet()) {
            if (count >= 4) break;
            Player p = entry.getKey();
            int r = entry.getValue();
            int add = scoreAdd.getOrDefault(p, 0);
            int wool = woolScores.getOrDefault(p, 0);
            Bukkit.broadcastMessage(ChatColor.YELLOW + "第" + r + "名：" + ChatColor.WHITE
                    + p.getName() + " (羊毛 " + wool + ") (+" + add + "积分)");
            count++;
        }
    }

    /**
     * 寻找有效刷怪位置
     */
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
                Block block = world.getBlockAt((int) Math.floor(x), y, (int) Math.floor(z));
                if (block.getType().isSolid()) {
                    return block.getLocation().add(0.5, 1, 0.5);
                }
            }
        }
        return null;
    }

    /**
     * 淘汰玩家
     */
    @Override
    public void eliminatePlayer(Player player) {
        List<Player> alivePlayers = gameManager.getAlivePlayers();
        if (!alivePlayers.remove(player)) return;
        Utils.resetPlayer(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(config.getSpectatorSpawn());

        String title = ChatColor.AQUA + player.getName() + " 被淘汰了";
        for (Player p : gameManager.getPlayers()) {
            p.sendTitle("", title, 0, 40, 10);
        }
    }
}
