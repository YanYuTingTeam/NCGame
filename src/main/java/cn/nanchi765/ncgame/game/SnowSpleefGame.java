package cn.nanchi765.ncgame.game;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.game.SnowSpleefConfig;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.utils.GameUtils;
import cn.nanchi765.ncgame.utils.RankUtils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

import static cn.nanchi765.ncgame.utils.GameUtils.isGameActive;

public class SnowSpleefGame implements Listener, IMinigame {

    private static final String GAME_NAME = "掘一死战";
    private static final int[] SCORE_TABLE = {10, 7, 5, 1};
    private static final int LEADERBOARD_MAX_DISPLAY = 4;

    private final JavaPlugin plugin;
    @Getter
    private final SnowSpleefConfig config;
    private final GameManager gameManager;

    /**
     * 可破坏方块集合，O(1) 查找
     */
    private final Set<String> breakableBlockSet;
    private final List<Player> t = new ArrayList<>();

    public SnowSpleefGame(JavaPlugin plugin, SnowSpleefConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.gameManager = NCGame.getInstance().getGameManager();
        this.breakableBlockSet = new HashSet<>(config.getBreakableBlocks());
    }

    // ======================== 生命周期 ========================

    @Override
    public void prepare() {
        List<Location> spawns = new ArrayList<>(config.getSpawnLocations());
        if (spawns.isEmpty()) {
            throw new IllegalStateException(GAME_NAME + " 未配置出生点");
        }
        Collections.shuffle(spawns);

        List<Player> players = new ArrayList<>(gameManager.getAlivePlayers());
        Collections.shuffle(players);

        ItemStack shovel = createShovel();

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            p.teleport(spawns.get(i % spawns.size()));
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().setItem(0, shovel.clone());
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void start() {
    }

    @Override
    public void end() {
        Map<Player, Integer> rankings = buildRankings();
        Map<Player, Integer> scoreMap = calculateScores(rankings);

        applyScores(scoreMap);
        broadcastLeaderboard(rankings, scoreMap);

        HandlerList.unregisterAll(this);
    }

    @Override
    public void eliminatePlayer(Player player) {
        t.add(player);
        String title = ChatColor.AQUA + player.getName() + "在岩浆里游泳";
        for (Player p : gameManager.getPlayers()) {
            p.sendTitle("", title, 10, 30, 10);
        }
        gameManager.eliminatePlayer(player);
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!isGameActive(this)) return;
        event.setCancelled(true);
    }

    // ======================== 工具 ========================

    private ItemStack createShovel() {
        ItemStack shovel = new ItemStack(Material.DIAMOND_SPADE);
        ItemMeta meta = shovel.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "雪铲");
            shovel.setItemMeta(meta);
        }

        Enchantment ench = config.getShovelEnchantmentType();
        shovel.addUnsafeEnchantment(ench != null ? ench : Enchantment.DIG_SPEED,
                ench != null ? config.getShovelEnchantmentLevel() : 5);
        return shovel;
    }

    // ======================== 结算辅助 ========================

    private Map<Player, Integer> buildRankings() {
        return RankUtils.buildSurvivalRanking(gameManager.getAlivePlayers(), t);
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

    private void broadcastLeaderboard(Map<Player, Integer> rankings, Map<Player, Integer> scoreMap) {
        RankUtils.broadcastLeaderboard(GAME_NAME, rankings, scoreMap);
    }

    // ======================== 事件监听 ========================

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (!GameUtils.isGameActive(this) || !GameUtils.isAlive(player)) {
            event.setCancelled(true);
            return;
        }

        Material type = event.getBlock().getType();
        if (!breakableBlockSet.contains(type.name())) {
            event.setCancelled(true);
            return;
        }

        event.setDropItems(false);
        int amount = (type == Material.SNOW_BLOCK) ? 4 : 1;
        player.getInventory().addItem(new ItemStack(Material.SNOW_BALL, amount));
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!GameUtils.isGameActive(this)) return;
        if (!(event.getEntity() instanceof Snowball)) return;

        Snowball snowball = (Snowball) event.getEntity();
        if (!(snowball.getShooter() instanceof Player)) return;

        Player shooter = (Player) snowball.getShooter();
        if (!GameUtils.isAlive(shooter)) return;

        if (event.getHitBlock() != null) {
            Material type = event.getHitBlock().getType();
            if (breakableBlockSet.contains(type.name())) {
                event.getHitBlock().setType(Material.AIR);
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!GameUtils.isGameActive(this)) return;
        Player player = event.getPlayer();

        if (GameUtils.isAlive(player) && player.getLocation().getY() < config.getDeathY()) {
            eliminatePlayer(player);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!GameUtils.isGameActive(this)) return;
        if (!(event.getEntity() instanceof Player)) return;
        event.setCancelled(true);
    }
}
