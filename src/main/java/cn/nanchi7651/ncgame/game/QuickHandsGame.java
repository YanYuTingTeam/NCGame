package cn.nanchi7651.ncgame.game;

import cn.nanchi7651.ncgame.NCGame;
import cn.nanchi7651.ncgame.config.game.QuickHandsConfig;
import cn.nanchi7651.ncgame.manager.GameManager;
import cn.nanchi7651.ncgame.utils.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class QuickHandsGame implements Listener, IMinigame {

    private static final String GAME_NAME = "眼疾手快";
    /**
     * 按名次排列的积分奖励
     */
    private static final int[] SCORE_TABLE = {10, 7, 5, 1};
    private static final int LEADERBOARD_MAX_DISPLAY = 4;

    private final JavaPlugin plugin;
    @Getter
    private final QuickHandsConfig config;
    private final GameManager gameManager;

    /**
     * 用 Set 加速 contains 判定
     */
    private final Set<Player> finishedSet = new LinkedHashSet<>();
    /**
     * 按到达顺序记录
     */
    private final List<Player> finishedList = new ArrayList<>();

    public QuickHandsGame(JavaPlugin plugin, QuickHandsConfig config) {
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

        List<Player> players = new ArrayList<>(gameManager.getAlivePlayers());
        Collections.shuffle(players);

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            p.teleport(spawns.get(i % spawns.size()));
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        distributeTools();
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
        notifyResults(rankings, scoreMap);
        broadcastLeaderboard(rankings, scoreMap);

        HandlerList.unregisterAll(this);
    }

    @Override
    public void eliminatePlayer(Player player) {
        List<Player> alivePlayers = gameManager.getAlivePlayers();
        if (!alivePlayers.remove(player)) return;

        Utils.resetPlayer(player);
        player.setGameMode(GameMode.SPECTATOR);

        Location spectatorSpawn = config.getSpectatorSpawn();
        if (spectatorSpawn != null) {
            player.teleport(spectatorSpawn);
        }
    }

    // ======================== 工具发放 ========================

    private void distributeTools() {
        ItemStack axe = createTool(
                Material.matchMaterial(config.getAxeMaterial()),
                config.getAxeEnchantment(),
                config.getAxeLevel(),
                ChatColor.GREEN + "铁斧"
        );
        ItemStack pick = createTool(
                Material.matchMaterial(config.getPickaxeMaterial()),
                config.getPickaxeEnchantment(),
                config.getPickaxeLevel(),
                ChatColor.GREEN + "铁镐"
        );
        ItemStack shovel = createTool(
                Material.matchMaterial(config.getShovelMaterial()),
                config.getShovelEnchantment(),
                config.getShovelLevel(),
                ChatColor.GREEN + "铁铲"
        );

        for (Player p : gameManager.getAlivePlayers()) {
            p.getInventory().setItem(0, axe.clone());
            p.getInventory().setItem(1, pick.clone());
            p.getInventory().setItem(2, shovel.clone());
        }
    }

    private ItemStack createTool(Material material, Enchantment ench, int level, String name) {
        ItemStack tool = new ItemStack(material);
        ItemMeta meta = tool.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            tool.setItemMeta(meta);
        }
        Enchantment enchantment = ench != null ? ench : Enchantment.DIG_SPEED;
        tool.addUnsafeEnchantment(enchantment, level > 0 ? level : 5);
        return tool;
    }

    // ======================== 结算辅助 ========================

    private Map<Player, Integer> buildRankings() {
        Map<Player, Integer> rankings = new LinkedHashMap<>();
        int rank = 1;
        for (Player p : finishedSet) {
            rankings.put(p, rank++);
        }
        return rankings;
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
        for (Player p : gameManager.getPlayers()) {
            if (rankings.containsKey(p)) {
                int rank = rankings.get(p);
                int add = scoreMap.getOrDefault(p, 0);
                String suffix = add > 0 ? " (+" + add + "积分)" : "";
                p.sendMessage(ChatColor.GREEN + "你在本回合小游戏获得第 " + rank + " 名！" + suffix);
            } else {
                p.sendMessage(ChatColor.RED + "你没有完成本轮游戏，无排名。");
            }
        }
    }

    private void broadcastLeaderboard(Map<Player, Integer> rankings, Map<Player, Integer> scoreMap) {
        Bukkit.broadcastMessage(ChatColor.GOLD + "===== " + GAME_NAME + " 排行榜 =====");

        if (rankings.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.RED + "无人完成本轮游戏！");
            return;
        }

        int count = 0;
        for (Map.Entry<Player, Integer> entry : rankings.entrySet()) {
            if (count >= LEADERBOARD_MAX_DISPLAY) break;
            int add = scoreMap.getOrDefault(entry.getKey(), 0);
            Bukkit.broadcastMessage(
                    ChatColor.YELLOW + "第" + entry.getValue() + "名："
                            + ChatColor.WHITE + entry.getKey().getName() + " (+" + add + "积分)");
            count++;
        }
    }

    // ======================== 到达判定 ========================

    private void checkFinish(Player player) {
        if (finishedSet.contains(player)) return;
        if (player.getLocation().getY() > config.getWinY()) return;

        finishedSet.add(player);
        finishedList.add(player);
        player.setGameMode(GameMode.SPECTATOR);

        Location spectatorSpawn = config.getSpectatorSpawn();
        if (spectatorSpawn != null) {
            player.teleport(spectatorSpawn);
        }

        String title = ChatColor.AQUA + player.getName() + " 挖到了宝藏";
        for (Player p : gameManager.getPlayers()) {
            p.sendTitle("", title, 10, 70, 10);
        }

        if (finishedSet.size() == gameManager.getAlivePlayers().size()) {
            end();
        }
    }

    // ======================== 事件监听 ========================

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!Utils.isGameActive(this)) return;
        if (!Utils.isAlive(player)) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return; // 同一方块内微小移动，跳过
        }

        checkFinish(player);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // 游戏未活跃或玩家非存活状态：一律取消
        if (!Utils.isGameActive(this) || !Utils.isAlive(player)) {
            event.setCancelled(true);
            return;
        }

        Material type = event.getBlock().getType();
        if (!config.getBreakableBlocks().contains(type.name())) {
            event.setCancelled(true);
        } else {
            event.setDropItems(false);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (Utils.isGameActive(this) && event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }
}
