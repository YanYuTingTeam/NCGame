package cn.nanchi765.ncgame.game;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.game.ExtremeMinefieldConfig;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.manager.Status;
import cn.nanchi765.ncgame.utils.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class ExtremeMinefieldGame implements Listener, IMinigame {

    private final JavaPlugin plugin;
    @Getter
    private final ExtremeMinefieldConfig config;
    @Getter
    private final GameManager gameManager;
    private final List<Player> finishedPlayers;
    private final Map<Player, Location> spawnPoints = new HashMap<>();

    public ExtremeMinefieldGame(JavaPlugin plugin, ExtremeMinefieldConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.finishedPlayers = new ArrayList<>();
        this.gameManager = NCGame.getInstance().getGameManager();
    }

    @Override
    public void prepare() {

        List<Location> spawns = new ArrayList<>(config.getSpawnLocations());
        Collections.shuffle(spawns);
        List<Player> shuffledPlayers = new ArrayList<>(gameManager.getAlivePlayers());
        Collections.shuffle(shuffledPlayers);

        for (int i = 0; i < shuffledPlayers.size(); i++) {
            Player p = shuffledPlayers.get(i);
            Location loc = (i < spawns.size()) ? spawns.get(i) : spawns.get(0);
            p.teleport(loc);
            spawnPoints.put(p, loc.clone());
            p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().clear();
            p.setFoodLevel(20);
            p.setSaturation(10);
            p.setHealth(20);
            p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void start() {

    }

    @Override
    public void end() {
        // 只对完成的玩家排名
        Map<Player, Integer> playerRank = new LinkedHashMap<>();
        int rank = 1;
        for (Player p : finishedPlayers) {
            playerRank.put(p, rank++);
        }

        Map<Player, Integer> scoreAdd = new HashMap<>();
        for (Map.Entry<Player, Integer> entry : playerRank.entrySet()) {
            int r = entry.getValue();
            int add = 0;
            if (r == 1) add = 10;
            else if (r == 2) add = 7;
            else if (r == 3) add = 5;
            else if (r == 4) add = 1;
            scoreAdd.put(entry.getKey(), add);
        }

        if (plugin instanceof NCGame) {
            NCGame ncGame = (NCGame) plugin;
            for (Map.Entry<Player, Integer> entry : scoreAdd.entrySet())
                ncGame.addScore(entry.getKey(), entry.getValue());
        }

        for (Player p : gameManager.getAlivePlayers()) {
            if (finishedPlayers.contains(p)) {
                int myRank = playerRank.get(p);
                int myAdd = scoreAdd.getOrDefault(p, 0);
                p.sendMessage(ChatColor.GREEN + "你在本回合小游戏获得第 " + myRank + " 名！" + (myAdd > 0 ? " (+" + myAdd + "积分)" : ""));
            } else {
                p.sendMessage(ChatColor.RED + "你没有完成本轮游戏，无排名。");
            }
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "===== 极限雷区 排行榜 =====");
        if (finishedPlayers.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.RED + "无人完成本轮游戏！");
        } else {
            int count = 0;
            for (Map.Entry<Player, Integer> entry : playerRank.entrySet()) {
                if (count >= 4) break;
                Player p = entry.getKey();
                int r = entry.getValue();
                int add = scoreAdd.getOrDefault(p, 0);
                Bukkit.broadcastMessage(ChatColor.YELLOW + "第" + r + "名：" + ChatColor.WHITE + p.getName() + " (+" + add + "积分)");
                count++;
            }
        }

        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!Utils.isAlive(player)) return;

        // 准备阶段锁定
        if (gameManager.getCurrentStatus() == Status.PREPARE) {
            Location spawn = spawnPoints.get(player);
            if (spawn != null && player.getLocation().distanceSquared(spawn) > 0.01) {
                player.teleport(spawn);
            }
            return;
        }

        List<Player> alivePlayers = gameManager.getAlivePlayers();
        // 意外掉落传送回出生点
        if (gameManager.getCurrentStatus() == Status.PLAYING && alivePlayers.contains(player)) {
            if (player.getLocation().getY() < 0.0) {
                Location spawn = spawnPoints.get(player);
                if (spawn != null) {
                    player.teleport(spawn);
                    player.sendMessage(ChatColor.RED + "你掉下去了，已传送回出生点！");
                }
                return;
            }
            if (!finishedPlayers.contains(player) && config.isInWinRegion(player.getLocation())) {
                finishedPlayers.add(player);
                player.setGameMode(GameMode.SPECTATOR);
                player.teleport(config.getSpectatorSpawn());
                String msg = ChatColor.AQUA + player.getName() + " 到达终点！（第 " + finishedPlayers.size() + " 名）";
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle("", msg, 10, 70, 10);
                }
                if (finishedPlayers.size() == alivePlayers.size()) {
                    end();
                }
            }
        }
    }

    // ========== 事件处理 ==========

    @EventHandler
    public void onPressurePlate(PlayerInteractEvent event) {
        if (!Utils.isGameActive(this)) return;
        if (event.getAction() != Action.PHYSICAL) return;
        Player player = event.getPlayer();
        if (!Utils.isAlive(player)) return;
        if (finishedPlayers.contains(player)) return;

        Material type = event.getClickedBlock().getType();
        if (type == Material.STONE_PLATE || type == Material.WOOD_PLATE ||
                type == Material.GOLD_PLATE || type == Material.IRON_PLATE) {
            event.setCancelled(true);
            Vector vel = new Vector(0, config.getLaunchPower(), 0);
            player.setVelocity(vel);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

            // 播放特效
            if (config.isEffectEnabled()) {
                String particleName = config.getParticleName();
                Particle particle = Particle.valueOf(particleName.toUpperCase());
                player.getWorld().spawnParticle(particle, player.getLocation().add(0, 0.5, 0), config.getParticleCount(), 0.3, 0.3, 0.3, 0);
            }

            // 延迟传送回出生点
            if (config.isTeleportBack()) {
                int delay = config.getTeleportDelay();
                Location spawn = spawnPoints.get(player);
                if (spawn != null) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.teleport(spawn);
                        }
                    }.runTaskLater(plugin, delay);
                }
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (Utils.isGameActive(this)) {
            event.setCancelled(true);
        }
    }

    @Override
    public void eliminatePlayer(Player player) {
        List<Player> alivePlayers = gameManager.getAlivePlayers();
        if (!alivePlayers.remove(player)) return;
        Utils.resetPlayer(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(config.getSpectatorSpawn());
    }
}