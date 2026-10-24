package cn.nanchi765.ncgame.game;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.game.BombSheepConfig;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.manager.Status;
import cn.nanchi765.ncgame.utils.GameUtils;
import cn.nanchi765.ncgame.utils.RankUtils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import java.util.*;

import static cn.nanchi765.ncgame.utils.GameUtils.isAlive;
import static cn.nanchi765.ncgame.utils.GameUtils.isGameActive;

public class BombSheepGame implements Listener, IMinigame {

    private final JavaPlugin plugin;
    private final GameManager gameManager;
    @Getter
    private final BombSheepConfig config;
    private final Map<Player, Integer> woolScores = new HashMap<>();
    private final List<Sheep> activeSheep = new ArrayList<>();
    private final Random random = new Random();
    private BukkitTask sheepSpawnTask;

    // 出生点分配记录
    private final Map<UUID, Location> spawnAssignments = new HashMap<>();

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

        // 分配出生点并记录
        for (int i = 0; i < shuffledPlayers.size(); i++) {
            Player p = shuffledPlayers.get(i);
            Location loc = (i < spawns.size()) ? spawns.get(i) : spawns.get(0);
            p.teleport(loc);
            spawnAssignments.put(p.getUniqueId(), loc);
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

        // 按羊毛数排名：多的在前，数量相同并列
        Map<Player, Integer> playerRank = RankUtils.buildCompetitionRanking(
                new ArrayList<>(woolScores.keySet()),
                Comparator.comparing((Player p) -> woolScores.getOrDefault(p, 0)).reversed()
        );

        Map<Player, Integer> scoreAdd = RankUtils.calculateScores(playerRank);

        // 发放积分
        addScores(scoreAdd);

        // 广播排行榜
        broadcastLeaderboard(playerRank, scoreAdd);

        // 清理
        spawnAssignments.clear();
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
        event.setCancelled(true);
        player.playSound(player.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1.0f, 1.0f);
        dropWool(sheep.getLocation());
        activeSheep.remove(sheep);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (sheep.isValid()) sheep.remove();
        });
    }

    /**
     * 剪羊毛掉落：1~3个羊毛, 5%概率为异色羊毛
     */
    private void dropWool(Location loc) {
        int amount = 1 + random.nextInt(3);
        short color = 0;
        if (random.nextDouble() < 0.05) {
            short[] colors = {1, 15, 8, 14};
            color = colors[random.nextInt(colors.length)];
        }
        loc.getWorld().dropItem(loc, new ItemStack(Material.WOOL, amount, color));
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        if (!isGameActive(this)) return;
        Player player = event.getPlayer();
        if (!isAlive(player)) return;

        ItemStack item = event.getItem().getItemStack();
        if (item.getType() != Material.WOOL) return;
        if (item.getDurability() == 0) {
            woolScores.merge(player, item.getAmount(), Integer::sum);
        } else {
            woolScores.merge(player, item.getAmount() * 3, Integer::sum);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!GameUtils.isAlive(player)) return;

        if (player.getLocation().getY() < config.getDeathY()) {
            if (gameManager.getCurrentStatus() == Status.PREPARE) {
                Location spawn = spawnAssignments.get(player.getUniqueId());
                if (spawn != null) {
                    player.teleport(spawn);
                }
                return;
            }
            eliminatePlayer(player);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
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
     * 广播排行榜
     */
    private void broadcastLeaderboard(Map<Player, Integer> playerRank, Map<Player, Integer> scoreAdd) {
        RankUtils.broadcastLeaderboard("炸弹羊", playerRank, scoreAdd);
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
        String title = ChatColor.AQUA + player.getName() + "被炸弹羊送走了";
        for (Player p : gameManager.getPlayers()) {
            p.sendTitle("", title, 10, 30, 10);
        }
        gameManager.eliminatePlayer(player);
    }
}
