package cn.nanchi765.ncgame.game;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.game.OneHitKillConfig;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.utils.GameUtils;
import cn.nanchi765.ncgame.utils.RankUtils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class OneHitKillGame implements Listener, IMinigame {

    private static final String GAME_NAME = "一箭必杀";

    private final JavaPlugin plugin;
    @Getter
    private final OneHitKillConfig config;
    private final GameManager gameManager;
    private final List<Player> eliminationOrder = new ArrayList<>();

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
        for (Player p : gameManager.getAlivePlayers()) {
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    @Override
    public void end() {
        Map<Player, Integer> rankings = buildRankings();
        Map<Player, Integer> scoreMap = calculateScores(rankings);

        applyScores(scoreMap);
        broadcastLeaderboard(rankings, scoreMap);

        HandlerList.unregisterAll(this);
    }

    // ======================== 准备阶段辅助 ========================

    private ItemStack createBow() {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        if (meta == null) return bow;
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
        return RankUtils.buildSurvivalRanking(gameManager.getAlivePlayers(), eliminationOrder);
    }

    private Map<Player, Integer> calculateScores(Map<Player, Integer> rankings) {
        return RankUtils.calculateScores(rankings);
    }

    private void applyScores(Map<Player, Integer> scoreMap) {
        if (!(plugin instanceof NCGame)) return;
        NCGame ncGame = (NCGame) plugin;
        scoreMap.forEach(ncGame::addScore);
    }

    private void broadcastLeaderboard(Map<Player, Integer> rankings, Map<Player, Integer> scoreMap) {
        RankUtils.broadcastLeaderboard(GAME_NAME, rankings, scoreMap);
    }

    // ======================== 淘汰逻辑 ========================

    @Override
    public void eliminatePlayer(Player player) {
        eliminationOrder.add(player);
        String title = ChatColor.AQUA + player.getName() + "被终结";
        for (Player p : gameManager.getPlayers()) {
            p.sendTitle("", title, 10, 30, 10);
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
}
