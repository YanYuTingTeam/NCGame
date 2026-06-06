package cn.nanchi765.ncgame.game;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.game.OneHitKillConfig;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.utils.GameUtils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class OneHitKillGame implements Listener, IMinigame {

    private static final String GAME_NAME = "一箭必杀";
    /**
     * 按名次排列的积分奖励：第1名→10, 第2名→7, 第3名→5, 第4名→1, 其余→0
     */
    private static final int[] SCORE_TABLE = {10, 7, 5, 1};

    private final JavaPlugin plugin;
    @Getter
    private final OneHitKillConfig config;
    private final GameManager gameManager;

    public OneHitKillGame(JavaPlugin plugin, OneHitKillConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.gameManager = NCGame.getInstance().getGameManager();
    }

    // ======================== 生命周期 ========================

    @Override
    public void prepare() {
        List<Location> spawns = new ArrayList<>(config.getSpawnLocations());
        if (spawns.isEmpty()) {
            throw new IllegalStateException(GAME_NAME + " 未配置出生点");
        }
        Collections.shuffle(spawns);

        List<Player> shuffledPlayers = new ArrayList<>(gameManager.getAlivePlayers());
        Collections.shuffle(shuffledPlayers);

        ItemStack bow = createBow();

        for (int i = 0; i < shuffledPlayers.size(); i++) {
            initPlayer(shuffledPlayers.get(i), spawns.get(i % spawns.size()), bow);
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void start() {

        for (Player p : gameManager.getPlayers()) {
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    @Override
    public void end() {
        Map<Player, Integer> rankings = buildRankings();
        Map<Player, Integer> scoreMap = calculateScores(rankings);

        applyScores(scoreMap);
        notifyResults(rankings, scoreMap);
        broadcastLeaderboard(rankings, scoreMap);

        HandlerList.unregisterAll(this);

        
    }

    // ======================== 准备阶段辅助 ========================

    private ItemStack createBow() {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        if (meta == null) return bow;

        meta.setDisplayName(ChatColor.RED + GAME_NAME);
        meta.addEnchant(Enchantment.ARROW_DAMAGE, 5, true);
        meta.addEnchant(Enchantment.ARROW_INFINITE, 1, true);
        bow.setItemMeta(meta);
        return bow;
    }

    private void initPlayer(Player player, Location spawn, ItemStack bow) {
        player.teleport(spawn);
        player.getInventory().setItem(0, bow.clone());
        player.getInventory().setItem(9, new ItemStack(Material.ARROW));
    }

    // ======================== 结算阶段辅助 ========================

    /**
     * 构建排名：存活玩家并列第1，淘汰玩家按淘汰时间倒序排列
     */
    private Map<Player, Integer> buildRankings() {
        Map<Player, Integer> rankings = new LinkedHashMap<>();
        List<Player> alivePlayers = gameManager.getAlivePlayers();

        int nextRank = alivePlayers.isEmpty() ? 1 : 2;
        for (Player p : alivePlayers) {
            rankings.put(p, 1);
        }

        List<Player> eliminated = getEliminatedPlayers();
        Collections.reverse(eliminated);
        for (Player p : eliminated) {
            rankings.put(p, nextRank++);
        }

        return rankings;
    }

    private List<Player> getEliminatedPlayers() {
        Set<Player> aliveSet = new HashSet<>(gameManager.getAlivePlayers());
        List<Player> eliminated = new ArrayList<>();
        for (Player p : gameManager.getPlayers()) {
            if (!aliveSet.contains(p)) {
                eliminated.add(p);
            }
        }
        return eliminated;
    }

    private Map<Player, Integer> calculateScores(Map<Player, Integer> rankings) {
        Map<Player, Integer> scores = new HashMap<>();
        rankings.forEach((player, rank) -> {
            int index = rank - 1;
            scores.put(player, index < SCORE_TABLE.length ? SCORE_TABLE[index] : 0);
        });
        return scores;
    }

    private void applyScores(Map<Player, Integer> scoreMap) {
        if (!(plugin instanceof NCGame)) return;
        NCGame ncGame = (NCGame) plugin;
        scoreMap.forEach(ncGame::addScore);
    }

    private void notifyResults(Map<Player, Integer> rankings, Map<Player, Integer> scoreMap) {
        rankings.forEach((player, rank) -> {
            int add = scoreMap.getOrDefault(player, 0);
            String suffix = add > 0 ? " (+" + add + "积分)" : "";
            player.sendMessage(ChatColor.GREEN + "你在本回合小游戏获得第 " + rank + " 名！" + suffix);
        });
    }

    private void broadcastLeaderboard(Map<Player, Integer> rankings, Map<Player, Integer> scoreMap) {
        Bukkit.broadcastMessage(ChatColor.GOLD + "===== " + GAME_NAME + " 排行榜 =====");

        int count = 0;
        for (Map.Entry<Player, Integer> entry : rankings.entrySet()) {
            if (count >= 4) break;
            int add = scoreMap.getOrDefault(entry.getKey(), 0);
            Bukkit.broadcastMessage(
                    ChatColor.YELLOW + "第" + entry.getValue() + "名："
                            + ChatColor.WHITE + entry.getKey().getName() + " (+" + add + "积分)");
            count++;
        }
    }

    // ======================== 淘汰逻辑 ========================

    @Override
    public void eliminatePlayer(Player player) {
        String title = ChatColor.AQUA + player.getName() + " 被一箭射杀";
        for (Player p : gameManager.getPlayers()) {
            p.sendTitle("", title, 0, 40, 10);
        }
        gameManager.eliminatePlayer(player);
    }

    // ======================== 事件监听 ========================

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();

        if (!GameUtils.isGameActive(this)) {
            event.setCancelled(true);
            return;
        }

        if (!GameUtils.isAlive(victim)) {
            event.setCancelled(true);
            return;
        }

        if (event.getDamager() instanceof Arrow) {
            Arrow arrow = (Arrow) event.getDamager();
            if (arrow.getShooter() instanceof Player) {
                Player shooter = (Player) arrow.getShooter();
                if (!shooter.equals(victim) && GameUtils.isAlive(shooter)) {
                    eliminatePlayer(victim);
                }
            }
        }

        // 淘汰由 eliminatePlayer 手动处理，取消所有伤害事件
        event.setCancelled(true);
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (GameUtils.isGameActive(this) && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (GameUtils.isGameActive(this)) {
            event.setCancelled(true);
        }
    }
}
