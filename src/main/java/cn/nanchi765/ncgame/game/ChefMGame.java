package cn.nanchi765.ncgame.game;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.game.ChefMConfig;
import cn.nanchi765.ncgame.manager.GameManager;
import cn.nanchi765.ncgame.manager.Status;
import cn.nanchi765.ncgame.utils.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

import static cn.nanchi765.ncgame.utils.Utils.isGameActive;

public class ChefMGame implements Listener, IMinigame {

    // ==================== 常量 ====================

    private static final String ANIMAL_NAME_PREFIX = "chef_";
    private static final int TASK_DISPLAY_SLOT = 8;
    /**
     * 名次 -> 积分 (index 1-based)
     */
    private static final int[] RANK_SCORES = {0, 10, 7, 5, 1};

    // ==================== 字段 ====================

    private final JavaPlugin plugin;
    private final GameManager gameManager;
    @Getter
    private final ChefMConfig config;
    private final List<ChefMConfig.Task> tasks;
    private final List<ChefMConfig.ChestConfig> chestConfigs;
    private final Map<Player, Integer> scores = new HashMap<>();
    private final Map<Player, Integer> taskIndex = new HashMap<>();
    private final Map<Location, BukkitTask> chestTasks = new HashMap<>();
    private final Random random = new Random();


    public ChefMGame(JavaPlugin plugin, ChefMConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.gameManager = NCGame.getInstance().getGameManager();
        this.tasks = config.getTasks();
        this.chestConfigs = config.getChestConfigs();
    }

    @Override
    public void prepare() {
        Location spawn = config.getSpawnLocation();
        if (spawn == null) {
            plugin.getLogger().severe("厨神出生点未配置，游戏取消");
            return;
        }

        spawnVillagers();
        spawnAnimals();
        setupAllChests();

        for (Player p : gameManager.getAlivePlayers()) {
            p.teleport(spawn);
            scores.put(p, 0);
            taskIndex.put(p, 0);
            giveChefSword(p);
            updateTaskItem(p);
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void start() {
        for (Player player : gameManager.getAlivePlayers()) {
            int idx = taskIndex.getOrDefault(player, 0);
            if (idx < tasks.size()) {
                ChefMConfig.Task task = tasks.get(idx);
                player.sendTitle("§e§l开始！",
                        "§b下一个烹饪目标 §c§l" + task.getDisplayName() + " §b!",
                        10, 80, 10);
            }
        }
    }

    @Override
    public void end() {
        chestTasks.values().forEach(BukkitTask::cancel);
        chestTasks.clear();
        announceResults();
        HandlerList.unregisterAll(this);
        
    }

    private void spawnVillagers() {
        for (Location loc : config.getVillagerLocations()) {
            if (loc == null) continue;
            Villager villager = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
            villager.setRemoveWhenFarAway(false);
            villager.setAdult();
        }
    }

    private void spawnAnimals() {
        RegionBounds bounds = RegionBounds.from(config.getAnimalRegionPos1(), config.getAnimalRegionPos2());
        if (bounds == null) return;

        for (Map.Entry<String, Integer> entry : config.getAnimals().entrySet()) {
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(entry.getKey());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("未知动物类型: " + entry.getKey());
                continue;
            }
            for (int i = 0; i < entry.getValue(); i++) {
                Location loc = bounds.randomLocation(random);
                Entity entity = bounds.world.spawnEntity(loc, entityType);
                entity.setCustomName(ANIMAL_NAME_PREFIX + entityType.name().toLowerCase());
                entity.setCustomNameVisible(false);
            }
        }
    }

    private void setupAllChests() {
        for (ChefMConfig.ChestConfig cc : chestConfigs) {
            Location loc = cc.location;
            Block block = loc.getBlock();
            block.setType(Material.CHEST);

            if (!(block.getState() instanceof Chest)) continue;
            Chest chest = (Chest) block.getState();

            org.bukkit.material.Chest chestData = (org.bukkit.material.Chest) chest.getData();
            chestData.setFacingDirection(cc.facing);
            chest.setData(chestData);
            chest.update();

            refillChest(cc, chest.getBlockInventory());
            startChestRefillTask(cc, loc);
        }
    }

    private void startChestRefillTask(ChefMConfig.ChestConfig cc, Location loc) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (gameManager.getCurrentStatus() != Status.PLAYING) {
                    this.cancel();
                    return;
                }
                Block b = loc.getBlock();
                if (b.getType() == Material.CHEST && b.getState() instanceof Chest) {
                    Chest c = (Chest) b.getState();
                    if (isInventoryEmpty(c.getBlockInventory())) {
                        refillChest(cc, c.getBlockInventory());
                    }
                }
            }
        }.runTaskTimer(plugin, 0, cc.refillDelay);
        chestTasks.put(loc, task);
    }

    private void giveChefSword(Player p) {
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "厨刀");
        sword.setItemMeta(meta);
        p.getInventory().setItem(0, sword);
    }

    // ==================== 箱子逻辑 ====================

    private void refillChest(ChefMConfig.ChestConfig cc, Inventory inv) {
        if (cc.items.isEmpty()) return;
        int amount = cc.amountMin + random.nextInt(cc.amountMax - cc.amountMin + 1);
        inv.clear();

        List<Integer> emptySlots = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                emptySlots.add(i);
            }
        }
        Collections.shuffle(emptySlots);

        int toPlace = Math.min(amount, emptySlots.size());
        for (int i = 0; i < toPlace; i++) {
            ItemStack item = cc.items.get(random.nextInt(cc.items.size())).clone();
            inv.setItem(emptySlots.get(i), item);
        }
    }

    private boolean isInventoryEmpty(Inventory inv) {
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }
        return true;
    }

    private void updateTaskItem(Player p) {
        int idx = taskIndex.getOrDefault(p, 0);
        if (idx >= tasks.size()) {
            p.getInventory().setItem(TASK_DISPLAY_SLOT, null);
            return;
        }
        ChefMConfig.Task task = tasks.get(idx);
        ItemStack displayItem = new ItemStack(task.material);
        ItemMeta meta = displayItem.getItemMeta();
        meta.setDisplayName(ChatColor.RESET + task.getDisplayName());
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        displayItem.setItemMeta(meta);
        displayItem.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
        p.getInventory().setItem(TASK_DISPLAY_SLOT, displayItem);
    }

    // ==================== 结算 ====================

    private void announceResults() {
        List<Map.Entry<Player, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Map.Entry.<Player, Integer>comparingByValue().reversed());

        Map<Player, Integer> playerRank = new LinkedHashMap<>();
        Map<Player, Integer> scoreAdd = new HashMap<>();
        int rank = 1;
        for (Map.Entry<Player, Integer> entry : sorted) {
            playerRank.put(entry.getKey(), rank);
            scoreAdd.put(entry.getKey(), getRankScore(rank));
            rank++;
        }

        if (plugin instanceof NCGame) {
            NCGame ncGame = (NCGame) plugin;
            scoreAdd.forEach(ncGame::addScore);
        }

        for (Player p : gameManager.getAlivePlayers()) {
            int myScore = scores.getOrDefault(p, 0);
            int myRank = playerRank.getOrDefault(p, 0);
            int myAdd = scoreAdd.getOrDefault(p, 0);
            String addMsg = myAdd > 0 ? " (+" + myAdd + "积分)" : "";
            p.sendMessage(ChatColor.GREEN + "你获得了 " + myScore + " 分，第 " + myRank + " 名！" + addMsg);
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "===== 厨神 排行榜 =====");
        int displayed = 0;
        for (Map.Entry<Player, Integer> entry : playerRank.entrySet()) {
            if (displayed >= 4) break;
            Player p = entry.getKey();
            int r = entry.getValue();
            Bukkit.broadcastMessage(
                    ChatColor.YELLOW + "第" + r + "名：" + ChatColor.WHITE + p.getName()
                            + " (厨神分 " + scores.getOrDefault(p, 0)
                            + ") (+" + scoreAdd.getOrDefault(p, 0) + "积分)");
            displayed++;
        }
    }

    private int getRankScore(int rank) {
        if (rank >= 1 && rank < RANK_SCORES.length) return RANK_SCORES[rank];
        return 0;
    }

    // ==================== 事件处理 ====================

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // 村民永远无敌
        if (event.getEntity() instanceof Villager) {
            event.setCancelled(true);
            return;
        }

        if (!isGameActive(this)) {
            event.setCancelled(true);
            return;
        }

        // 游戏中：玩家不可被任何东西伤害
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
            return;
        }

        // 游戏中：仅存活玩家可造成伤害（允许攻击动物）
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            event.setCancelled(!Utils.isAlive(player));
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!isGameActive(this)) return;
        if (event instanceof EntityDamageByEntityEvent) return; // 交给专门处理器
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (event.getClickedInventory() == player.getInventory() && event.getSlot() == 8) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!isGameActive(this)) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof Animals)) return;
        String customName = entity.getCustomName();
        if (customName == null || !customName.startsWith(ANIMAL_NAME_PREFIX)) return;

        EntityType type = entity.getType();
        Integer targetCount = config.getAnimals().get(type.name());
        if (targetCount == null) return;

        // 延迟 1 tick 补充动物
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isGameActive(ChefMGame.this)) return;

                RegionBounds bounds = RegionBounds.from(
                        config.getAnimalRegionPos1(), config.getAnimalRegionPos2());
                if (bounds == null) return;

                // 使用 getNearbyEntities 代替遍历全服实体，性能显著提升
                long current = bounds.getNearbyEntities().stream()
                        .filter(e -> e.getType() == type
                                && e.isValid() && !e.isDead()
                                && e.getCustomName() != null
                                && e.getCustomName().startsWith(ANIMAL_NAME_PREFIX))
                        .count();

                int need = targetCount - (int) current;
                for (int i = 0; i < need; i++) {
                    Location loc = bounds.randomLocation(random);
                    Entity newEntity = bounds.world.spawnEntity(loc, type);
                    newEntity.setCustomName(ANIMAL_NAME_PREFIX + type.name().toLowerCase());
                    newEntity.setCustomNameVisible(false);
                }
            }
        }.runTask(plugin);
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!isGameActive(this)) return;

        Player player = event.getPlayer();
        if (!Utils.isAlive(player)) return;
        if (!(event.getRightClicked() instanceof Villager)) return;

        event.setCancelled(true);

        int idx = taskIndex.getOrDefault(player, 0);
        if (idx >= tasks.size()) {
            player.sendMessage(ChatColor.RED + "你已经完成了所有任务！");
            return;
        }

        ChefMConfig.Task task = tasks.get(idx);
        int totalCount = countMaterialInInventory(player, task.material);

        if (totalCount < task.amount) {
            player.sendMessage(ChatColor.RED + "你需要 " + task.amount
                    + " 个 " + task.getDisplayName() + " 来完成此任务");
            return;
        }

        // 消耗材料 -> 加分 -> 推进任务
        removeFromInventory(player, task.material, task.amount);
        player.updateInventory();

        int currentScore = scores.getOrDefault(player, 0) + task.amount;
        scores.put(player, currentScore);
        taskIndex.put(player, idx + 1);

        updateTaskItem(player);

        player.sendMessage(ChatColor.GREEN + "提交成功！当前分数: " + currentScore);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        String completeMsg = ChatColor.GREEN + player.getName() + " "
                + ChatColor.AQUA + "完成了 " + ChatColor.RED + task.getDisplayName()
                + ChatColor.AQUA + "！";
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("", completeMsg, 10, 70, 10);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isGameActive(this)) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isGameActive(this)) event.setCancelled(true);
    }

    @Override
    public void eliminatePlayer(Player player) {
        List<Player> alivePlayers = gameManager.getAlivePlayers();
        if (!alivePlayers.remove(player)) return;
        Utils.resetPlayer(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(config.getSpectatorSpawn());
    }

    private int countMaterialInInventory(Player player, Material material) {
        int total = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (i == TASK_DISPLAY_SLOT) continue;
            ItemStack stack = inv.getItem(i);
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void removeFromInventory(Player player, Material material, int amount) {
        int remaining = amount;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            if (i == TASK_DISPLAY_SLOT) continue;
            ItemStack stack = inv.getItem(i);
            if (stack != null && stack.getType() == material) {
                int remove = Math.min(stack.getAmount(), remaining);
                stack.setAmount(stack.getAmount() - remove);
                remaining -= remove;
            }
        }
    }

    // ==================== 区域辅助类 ====================

    /**
     * 封装区域边界计算与实体查询，消除 4 处重复的 min/max 计算代码。
     * 利用 getNearbyEntities 替代全服实体遍历，性能更优。
     */
    private static class RegionBounds {
        final World world;
        final double minX, maxX, minY, maxY, minZ, maxZ;

        private RegionBounds(Location pos1, Location pos2) {
            this.world = pos1.getWorld();
            this.minX = Math.min(pos1.getX(), pos2.getX());
            this.maxX = Math.max(pos1.getX(), pos2.getX());
            this.minY = Math.min(pos1.getY(), pos2.getY());
            this.maxY = Math.max(pos1.getY(), pos2.getY());
            this.minZ = Math.min(pos1.getZ(), pos2.getZ());
            this.maxZ = Math.max(pos1.getZ(), pos2.getZ());
        }

        static RegionBounds from(Location pos1, Location pos2) {
            if (pos1 == null || pos2 == null || pos1.getWorld() == null) return null;
            return new RegionBounds(pos1, pos2);
        }

        Location randomLocation(Random random) {
            return new Location(world,
                    minX + random.nextDouble() * (maxX - minX),
                    minY + random.nextDouble() * (maxY - minY),
                    minZ + random.nextDouble() * (maxZ - minZ));
        }

        /**
         * 利用 Bukkit 的空间索引获取区域附近的实体，比 world.getEntities() 全量遍历快一个数量级
         */
        Collection<Entity> getNearbyEntities() {
            Location center = new Location(world,
                    (minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
            double dx = (maxX - minX) / 2 + 1;
            double dy = (maxY - minY) / 2 + 1;
            double dz = (maxZ - minZ) / 2 + 1;
            return world.getNearbyEntities(center, dx, dy, dz);
        }
    }
}
