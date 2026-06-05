package cn.nanchi765.ncgame.game;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.game.CleanupConfig;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.utils.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class CleanupGame implements Listener, IMinigame {

    private final JavaPlugin plugin;
    private final GameManager gameManager;
    @Getter
    private final CleanupConfig config;
    private final Map<Player, Integer> blockCounts = new HashMap<>();

    public CleanupGame(JavaPlugin plugin, CleanupConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.gameManager = NCGame.getInstance().getGameManager();
        for (Player p : gameManager.getPlayers()) {
            blockCounts.put(p, 0);
        }
    }

    @Override
    public void prepare() {

        Location spawn = config.getSpawnLocation();
        if (spawn == null) {
            plugin.getLogger().severe("大扫除出生点未配置，游戏取消");
            return;
        }

        // 发放工具
        ItemStack shears = createItem(config.getShearsMaterial(), config.getShearsName());
        ItemStack axe = createItem(config.getAxeMaterial(), config.getAxeName());
        ItemStack shovel = createItem(config.getShovelMaterial(), config.getShovelName());

        for (Player p : gameManager.getPlayers()) {
            p.getInventory().setItem(0, shears);
            p.getInventory().setItem(1, axe);
            p.getInventory().setItem(2, shovel);
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void start() {

        for (Player player : gameManager.getPlayers()) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    @Override
    public void end() {
        // 按破坏数量排序：多的在前
        List<Map.Entry<Player, Integer>> sorted = new ArrayList<>(blockCounts.entrySet());
        sorted.sort(Map.Entry.<Player, Integer>comparingByValue().reversed());

        Map<Player, Integer> playerRank = new LinkedHashMap<>();
        int rank = 1;
        for (Map.Entry<Player, Integer> entry : sorted) {
            playerRank.put(entry.getKey(), rank++);
        }

        Map<Player, Integer> scoreAdd = new HashMap<>();
        for (Map.Entry<Player, Integer> entry : playerRank.entrySet()) {
            Player p = entry.getKey();
            int r = entry.getValue();
            int add = 0;
            if (r == 1) add = 10;
            else if (r == 2) add = 7;
            else if (r == 3) add = 5;
            else if (r == 4) add = 1;
            scoreAdd.put(p, add);
        }

        if (plugin instanceof NCGame) {
            NCGame ncGame = (NCGame) plugin;
            for (Map.Entry<Player, Integer> entry : scoreAdd.entrySet()) {
                ncGame.addScore(entry.getKey(), entry.getValue());
            }
        }

        for (Player p : gameManager.getPlayers()) {
            int myRank = playerRank.getOrDefault(p, gameManager.getPlayers().size());
            int myAdd = scoreAdd.getOrDefault(p, 0);
            int blocks = blockCounts.getOrDefault(p, 0);
            p.sendMessage(ChatColor.GREEN + "你破坏了 " + blocks + " 个方块，获得第 " + myRank + " 名！" + (myAdd > 0 ? " (+" + myAdd + "积分)" : ""));
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "===== 大扫除 排行榜 =====");
        int count = 0;
        for (Map.Entry<Player, Integer> entry : playerRank.entrySet()) {
            if (count >= 4) break;
            Player p = entry.getKey();
            int r = entry.getValue();
            int add = scoreAdd.getOrDefault(p, 0);
            int blocks = blockCounts.getOrDefault(p, 0);
            Bukkit.broadcastMessage(ChatColor.YELLOW + "第" + r + "名：" + ChatColor.WHITE + p.getName() + " (破坏 " + blocks + " 块) (+" + add + "积分)");
            count++;
        }

        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!Utils.isGameActive(this)) {
            event.setCancelled(true);
            return;
        }
        Player player = event.getPlayer();
        if (!Utils.isAlive(player)) {
            event.setCancelled(true);
            return;
        }

        // 高度限制：低于或等于配置的 Y 轴禁止破坏
        int limitY = config.getBreakLimitY();
        if (limitY >= 0 && event.getBlock().getY() <= limitY) {
            event.setCancelled(true);
            if (config.isDebugEnabled()) {
                player.sendMessage("§c此高度禁止破坏 (Y ≤ " + limitY + ")");
            }
            return;
        }

        Material type = event.getBlock().getType();
        if (!config.getBreakableBlocks().contains(type.name())) {
            if (config.isDebugEnabled()) {
                player.sendMessage("§c无法破坏: " + type.name());
            }
            event.setCancelled(true);
        } else {
            event.setDropItems(false);
            blockCounts.merge(player, 1, Integer::sum);
            player.sendTitle("", ChatColor.GREEN + "你获得了 " + ChatColor.YELLOW + "1" + ChatColor.GREEN + " 分", 5, 20, 5);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (Utils.isGameActive(this)) {
            event.setCancelled(true);
        }
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
    }
}