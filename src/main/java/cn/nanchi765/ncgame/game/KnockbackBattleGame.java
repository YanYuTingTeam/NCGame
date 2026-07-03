package cn.nanchi765.ncgame.game;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.game.KnockbackBattleConfig;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.manager.Status;
import cn.nanchi765.ncgame.utils.GameUtils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.*;

public class KnockbackBattleGame implements Listener, IMinigame {

    // ========== 积分表 ==========
    private static final int[] SCORE_TABLE = {0, 10, 7, 5, 1};

    // ========== 依赖 & 状态 ==========
    private final JavaPlugin plugin;
    @Getter
    private final KnockbackBattleConfig config;
    private final GameManager gameManager;

    // 二段跳状态
    private final Map<UUID, Long> lastDoubleJumpTimes = new HashMap<>();
    private final Map<UUID, Boolean> hasDoubleJumped = new HashMap<>();

    // 出生点分配记录
    private final Map<UUID, Location> spawnAssignments = new HashMap<>();

    private final Set<UUID> initialPlayers = new HashSet<>();

    public KnockbackBattleGame(JavaPlugin plugin, KnockbackBattleConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.gameManager = NCGame.getInstance().getGameManager();
    }

    // ========== 生命周期 ==========

    @Override
    public void prepare() {
        List<Location> spawns = new ArrayList<>(config.getSpawnLocations());
        Collections.shuffle(spawns);

        List<Player> shuffledPlayers = new ArrayList<>(gameManager.getAlivePlayers());
        Collections.shuffle(shuffledPlayers);

        // 分配出生点并记录
        for (int i = 0; i < shuffledPlayers.size(); i++) {
            Location loc = spawns.get(i % spawns.size());
            Player p = shuffledPlayers.get(i);
            p.teleport(loc);
            spawnAssignments.put(p.getUniqueId(), loc);
        }

        // 发放击退棒
        ItemStack stick = createKnockbackStick();
        for (Player p : gameManager.getAlivePlayers()) {
            p.getInventory().clear();
            p.getInventory().setItem(0, stick);
            p.setAllowFlight(true);
            p.setFlying(false);
            hasDoubleJumped.put(p.getUniqueId(), false);
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void start() {
        for (Player p:gameManager.getAlivePlayers()){initialPlayers.add(p.getUniqueId());}
    }

    @Override
    public void end() {
        for (Player p : gameManager.getPlayers()) {
            GameUtils.resetPlayer(p);
        }
        broadcastRanking();
        lastDoubleJumpTimes.clear();
        hasDoubleJumped.clear();
        spawnAssignments.clear();
        initialPlayers.clear();
        HandlerList.unregisterAll(this);
    }

    // ========== 物品创建 ==========

    private ItemStack createKnockbackStick() {
        ItemStack stick = new ItemStack(Material.STICK, 1);
        ItemMeta meta = stick.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "击退棒");
        stick.setItemMeta(meta);
        stick.addUnsafeEnchantment(Enchantment.KNOCKBACK, 10);
        return stick;
    }

    // ========== 排名 & 积分 ==========

    private void broadcastRanking() {
        List<Player> allPlayers = gameManager.getPlayers();
        Set<Player> aliveSet = new HashSet<>(gameManager.getAlivePlayers());

        List<Player> gamePlayers = new ArrayList<>();
        for (Player p : allPlayers) {
            if (initialPlayers.contains(p.getUniqueId())) {
                gamePlayers.add(p);
            }
        }

        List<Player> alivePlayers = new ArrayList<>();
        List<Player> eliminated = new ArrayList<>();
        for (Player p : gamePlayers) {
            if (aliveSet.contains(p)) {
                alivePlayers.add(p);
            } else {
                eliminated.add(p);
            }
        }

        // 构建排名
        Map<Player, Integer> playerRank = new LinkedHashMap<>();
        int rank = 1;

        for (Player p : alivePlayers) {
            playerRank.put(p, rank);
        }
        if (!alivePlayers.isEmpty()) {
            rank = 2;
        }

        // 后淘汰的排名更靠前
        Collections.reverse(eliminated);
        for (Player p : eliminated) {
            playerRank.put(p, rank++);
        }

        // 计算积分并发放
        Map<Player, Integer> scoreAdd = new HashMap<>();
        playerRank.forEach((p, r) -> scoreAdd.put(p, getScoreByRank(r)));

        if (plugin instanceof NCGame) {
            NCGame ncGame = (NCGame) plugin;
            scoreAdd.forEach(ncGame::addScore);
        }

        // 个人消息
        for (Player p : gamePlayers) {
            int myRank = playerRank.getOrDefault(p, gamePlayers.size());
            int myScore = scoreAdd.getOrDefault(p, 0);
            String suffix = myScore > 0 ? " (+" + myScore + "积分)" : "";
            p.sendMessage(ChatColor.GREEN + "你在本回合小游戏获得第 " + myRank + " 名！" + suffix);
        }

        // 广播前4名
        Bukkit.broadcastMessage(ChatColor.GOLD + "===== 击退战 排行榜 =====");
        int displayed = 0;
        for (Map.Entry<Player, Integer> entry : playerRank.entrySet()) {
            if (displayed >= 4) break;
            Player p = entry.getKey();
            int r = entry.getValue();
            int add = scoreAdd.getOrDefault(p, 0);
            Bukkit.broadcastMessage(
                    ChatColor.YELLOW + "第" + r + "名："
                            + ChatColor.WHITE + p.getName() + " (+" + add + "积分)");
            displayed++;
        }
    }

    private int getScoreByRank(int rank) {
        if (rank >= 1 && rank < SCORE_TABLE.length) {
            return SCORE_TABLE[rank];
        }
        return 0;
    }

    // ========== 事件监听 ==========

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!GameUtils.isAlive(player)) return;

        // 掉落淘汰
        if (player.getLocation().getY() < config.getDeathY()) {
            if (gameManager.getCurrentStatus() == Status.PREPARE) {
                Location spawn = spawnAssignments.get(player.getUniqueId());
                if (spawn != null) {
                    player.teleport(spawn);
                }
                return;
            }
            eliminatePlayer(player);
            return;
        }

        // 二段跳落地重置
        UUID uuid = player.getUniqueId();
        if (hasDoubleJumped.getOrDefault(uuid, false) || !player.getAllowFlight()) {
            if (isOnGround(player, config.getFallTest())) {
                hasDoubleJumped.put(uuid, false);
                player.setAllowFlight(true);
            }
        }
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!GameUtils.isAlive(player)) return;

        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return;

        event.setCancelled(true);
        player.setAllowFlight(false);
        player.setFlying(false);

        // 冷却检查
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastDoubleJumpTimes.getOrDefault(uuid, 0L);
        long cooldown = config.getDoubleJumpCooldown();
        if (cooldown > 0 && (now - last) < cooldown) {
            return;
        }

        // 根据疾跑状态选择力度
        double forward;
        double up;
        if (player.isSprinting()) {
            forward = config.getDoubleJumpSprintingForwardVelocity();
            up = config.getDoubleJumpSprintingUpVelocity();
        } else {
            forward = config.getDoubleJumpForwardVelocity();
            up = config.getDoubleJumpUpVelocity();
        }

        Vector direction = player.getLocation().getDirection().multiply(forward).setY(up);
        player.setVelocity(direction);
        lastDoubleJumpTimes.put(uuid, now);

        if (!config.isKeepDoubleJump()) {
            hasDoubleJumped.put(uuid, true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) return;

        if (!GameUtils.isGameActive(this)) return;

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();
        if (GameUtils.isAlive(victim) && GameUtils.isAlive(attacker)) {
            event.setDamage(0); // 存活玩家之间只保留击退，取消伤害
        } else {
            event.setCancelled(true);
        }
    }

    // ========== 落地检测 ==========

    private boolean isOnGround(Player player, int fallTest) {
        if (player.isOnGround()) return true;

        Location loc = player.getLocation().clone().subtract(0, 1, 0);
        for (int i = 0; i < fallTest; i++) {
            if (loc.getBlock().getType() != Material.AIR) {
                return true;
            }
            loc.subtract(0, 1, 0);
        }
        return false;
    }

    // ========== 淘汰逻辑 ==========

    @Override
    public void eliminatePlayer(Player player) {
        for (Player p : gameManager.getPlayers()) {
            p.sendTitle("", ChatColor.AQUA + player.getName() + " 被击落了", 0, 40, 10);
        }
        gameManager.eliminatePlayer(player);
    }
}
