package cn.NanChi765.ncgame;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.DirectionalContainer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;

public class ChefMGame implements Listener, IMinigame {

    private final JavaPlugin plugin;
    private final ChefMConfig config;
    private final List<Player> players;
    private final List<Player> alivePlayers;
    private final List<Player> eliminatedPlayers;
    private final List<Player> lateSpectators = new ArrayList<>();
    private final Map<Player, BukkitTask> spectatorWatchTasks = new HashMap<>();
    private final Map<Player, Integer> scores = new HashMap<>();
    private final Map<Player, Integer> taskIndex = new HashMap<>();
    private final List<ChefMConfig.Task> tasks;
    private final List<ChefMConfig.ChestConfig> chestConfigs;
    private final Map<Location, BukkitTask> chestTasks = new HashMap<>();
    private final Map<EntityType, Integer> animalTypeCounts = new HashMap<>();
    private GameState state;
    private int prepareTimer;
    private int gameTimer;
    private BukkitTask gameTask;
    private final Random random = new Random();
    private boolean firstTaskDisplay = true;

    private enum GameState {
        WAITING, PREPARE, PLAYING, ENDED
    }

    public ChefMGame(JavaPlugin plugin, ChefMConfig config, List<Player> players) {
        this.plugin = plugin;
        this.config = config;
        this.players = new ArrayList<>(players);
        this.alivePlayers = new ArrayList<>(players);
        this.eliminatedPlayers = new ArrayList<>();
        this.tasks = config.getTasks();
        this.chestConfigs = config.getChestConfigs();
        this.state = GameState.WAITING;
    }

    @Override
    public void start() {
        if (plugin instanceof NCGame) ((NCGame) plugin).stopLobbyScoreboard();

        Location spawn = config.getSpawnLocation();
        if (spawn == null) {
            plugin.getLogger().severe("厨神出生点未配置，游戏取消");
            endGameDirectly();
            return;
        }

        // 生成村民（无敌且不会消失）
        for (Location loc : config.getVillagerLocations()) {
            if (loc != null) {
                Villager villager = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
                villager.setRemoveWhenFarAway(false);
                villager.setAdult();
            }
        }

        // 生成动物，并赋予自定义名称，记录期望数量
        spawnAnimals();

        // 初始化箱子
        setupAllChests();

        for (Player p : players) {
            p.teleport(spawn);
            p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().clear();
            p.setFoodLevel(20);
            p.setSaturation(10);
            p.setHealth(20);
            p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
            scores.put(p, 0);
            taskIndex.put(p, 0);
            // 发放铁剑
            ItemStack sword = new ItemStack(Material.IRON_SWORD, 1);
            ItemMeta swordMeta = sword.getItemMeta();
            swordMeta.setDisplayName(ChatColor.GREEN + "厨刀");
            sword.setItemMeta(swordMeta);
            p.getInventory().setItem(0, sword);
            updateTaskItem(p);
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startPreparePhase();
    }

    private void spawnAnimals() {
        Location pos1 = config.getAnimalRegionPos1();
        Location pos2 = config.getAnimalRegionPos2();
        if (pos1 == null || pos2 == null) return;
        World world = pos1.getWorld();
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        Map<String, Integer> animalConfig = config.getAnimals();
        for (Map.Entry<String, Integer> entry : animalConfig.entrySet()) {
            String typeName = entry.getKey();
            int count = entry.getValue();
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(typeName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("未知动物类型: " + typeName);
                continue;
            }
            for (int i = 0; i < count; i++) {
                Location loc = new Location(world,
                        minX + random.nextDouble() * (maxX - minX),
                        minY + random.nextDouble() * (maxY - minY),
                        minZ + random.nextDouble() * (maxZ - minZ));
                Entity entity = world.spawnEntity(loc, entityType);
                entity.setCustomName("chef_" + typeName.toLowerCase());
                entity.setCustomNameVisible(false);
            }
            animalTypeCounts.put(entityType, count);
        }
    }

    private void setupAllChests() {
        for (ChefMConfig.ChestConfig cc : chestConfigs) {
            Location loc = cc.location;
            Block block = loc.getBlock();
            block.setType(Material.CHEST);
            // 设置朝向
            if (block.getState() instanceof Chest) {
                Chest chest = (Chest) block.getState();
                org.bukkit.material.Chest chestData = (org.bukkit.material.Chest) chest.getData();
                chestData.setFacingDirection(cc.facing);
                chest.setData(chestData);
                chest.update();
                Inventory inv = chest.getBlockInventory();
                inv.clear();
                refillChest(cc, inv);
                BukkitTask task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (state != GameState.PLAYING) return;
                        Block b = loc.getBlock();
                        if (b.getType() == Material.CHEST && b.getState() instanceof Chest) {
                            Chest c = (Chest) b.getState();
                            if (isInventoryEmpty(c.getBlockInventory())) {
                                refillChest(cc, c.getBlockInventory());
                            }
                        }
                    }
                }.runTaskTimer(plugin, cc.refillDelay, cc.refillDelay);
                chestTasks.put(loc, task);
            }
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

    // 随机分布填充箱子，每个物品单独占一个格子
    private void refillChest(ChefMConfig.ChestConfig cc, Inventory inv) {
        List<ItemStack> possibleItems = cc.items;
        if (possibleItems.isEmpty()) return;
        int amount = cc.amountMin + random.nextInt(cc.amountMax - cc.amountMin + 1);
        inv.clear();
        // 获取所有可能的空位索引
        List<Integer> emptySlots = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                emptySlots.add(i);
            }
        }
        // 打乱空位顺序
        Collections.shuffle(emptySlots);
        // 放置物品，最多 amount 个，如果空位不够就只放空位数个
        int toPlace = Math.min(amount, emptySlots.size());
        for (int i = 0; i < toPlace; i++) {
            ItemStack item = possibleItems.get(random.nextInt(possibleItems.size())).clone();
            inv.setItem(emptySlots.get(i), item);
        }
    }

    private void startPreparePhase() {
        state = GameState.PREPARE;
        prepareTimer = config.getPrepareTime();

        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.PREPARE) { cancel(); return; }
                if (prepareTimer <= 0) { startPlayingPhase(); cancel(); return; }
                if (prepareTimer <= 10) {
                    String title = ChatColor.YELLOW + String.valueOf(prepareTimer) + " 秒后";
                    String subtitle = ChatColor.DARK_GREEN + "收集材料，提交任务！";
                    for (Player p : players) {
                        p.sendTitle(title, subtitle, 0, 25, 5);
                        if (config.isCountdownSoundEnabled())
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_PLING, 1.0f, 1.0f);
                    }
                }
                updateScoreboard(prepareTimer, config.getPrepareTime());
                prepareTimer--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startPlayingPhase() {
        state = GameState.PLAYING;
        for (Player p : players) p.setGameMode(GameMode.ADVENTURE);
        gameTimer = config.getGameTime();
        showCurrentTaskToAll();

        firstTaskDisplay = true;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.PLAYING) return;
                for (Player p : players) {
                    sendStartTaskTitle(p);
                }
                firstTaskDisplay = false;
            }
        }.runTaskLater(plugin, 20L);

        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.PLAYING) { cancel(); return; }
                if (gameTimer > 0) gameTimer--;
                if (alivePlayers.size() < getRequiredPlayers() || (config.getGameTime() > 0 && gameTimer <= 0)) {
                    endGame();
                    cancel();
                    return;
                }
                updateScoreboard(gameTimer, config.getGameTime());
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void sendStartTaskTitle(Player p) {
        int idx = taskIndex.getOrDefault(p, 0);
        if (idx < tasks.size()) {
            ChefMConfig.Task task = tasks.get(idx);
            p.sendTitle(ChatColor.YELLOW + "开始！",
                    ChatColor.AQUA + "下一个烹饪目标 " + ChatColor.RED + task.getDisplayName() + ChatColor.AQUA + "！",
                    10, 70, 10);
        }
    }

    private int getRequiredPlayers() {
        if (plugin instanceof NCGame) return ((NCGame) plugin).getLobbyConfig().getRequiredPlayers();
        return 2;
    }

    private void showCurrentTaskToAll() {
        for (Player p : players) {
            int idx = taskIndex.getOrDefault(p, 0);
            if (idx < tasks.size()) {
                ChefMConfig.Task task = tasks.get(idx);
                p.sendTitle("", task.description, 0, 40, 10);
            } else {
                p.sendTitle("", ChatColor.GOLD + "所有任务完成！", 0, 40, 10);
            }
            updateTaskItem(p);
        }
    }

    private void updateTaskItem(Player p) {
        int idx = taskIndex.getOrDefault(p, 0);
        if (idx >= tasks.size()) {
            p.getInventory().setItem(8, null);
            return;
        }
        ChefMConfig.Task task = tasks.get(idx);
        ItemStack displayItem = new ItemStack(task.material, 1);
        ItemMeta meta = displayItem.getItemMeta();
        meta.setDisplayName(ChatColor.RESET + task.getDisplayName()); // 显示中文名
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        displayItem.setItemMeta(meta);
        displayItem.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
        p.getInventory().setItem(8, displayItem);
    }

    private void updateScoreboard(int timeLeft, int totalTime) {
        List<String> lines = config.getScoreboardLines();
        if (lines.isEmpty()) return;
        ScoreboardManager sm = Bukkit.getScoreboardManager();

        for (Player p : players) {
            Scoreboard board = sm.getNewScoreboard();
            Objective obj = board.registerNewObjective("chefm", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.setDisplayName(config.getScoreboardTitle());
            int score = lines.size();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int playerScore = 0;
                if (plugin instanceof NCGame) playerScore = ((NCGame) plugin).getPlayerScore(p);
                String replaced = replaceVariables(line, timeLeft, totalTime, playerScore);
                obj.getScore(makeUnique(replaced, i)).setScore(score);
                score--;
            }
            p.setScoreboard(board);
        }

        for (Player p : lateSpectators) {
            Scoreboard board = sm.getNewScoreboard();
            Objective obj = board.registerNewObjective("chefm", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.setDisplayName(config.getScoreboardTitle());
            int score = lines.size();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int playerScore = 0;
                if (plugin instanceof NCGame) playerScore = ((NCGame) plugin).getPlayerScore(p);
                String replaced = replaceVariables(line, timeLeft, totalTime, playerScore);
                obj.getScore(makeUnique(replaced, i)).setScore(score);
                score--;
            }
            p.setScoreboard(board);
        }
    }

    private String replaceVariables(String line, int timeLeft, int totalTime, int playerScore) {
        return line.replace("{time_left}", String.valueOf(timeLeft))
                .replace("{total_time}", String.valueOf(totalTime))
                .replace("{score}", String.valueOf(playerScore));
    }

    private String makeUnique(String text, int index) {
        if (text.trim().isEmpty()) return ChatColor.BLACK.toString();
        ChatColor[] colors = ChatColor.values();
        return text + colors[index % colors.length];
    }

    private void endGame() {
        state = GameState.ENDED;
        for (BukkitTask task : chestTasks.values()) task.cancel();
        chestTasks.clear();

        List<Map.Entry<Player, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Map.Entry.<Player, Integer>comparingByValue().reversed());

        Map<Player, Integer> playerRank = new LinkedHashMap<>();
        int rank = 1;
        for (Map.Entry<Player, Integer> entry : sorted) {
            playerRank.put(entry.getKey(), rank++);
        }

        Map<Player, Integer> scoreAdd = new HashMap<>();
        for (Map.Entry<Player, Integer> entry : playerRank.entrySet()) {
            int r = entry.getValue();
            int add = r == 1 ? 10 : r == 2 ? 7 : r == 3 ? 5 : r == 4 ? 1 : 0;
            scoreAdd.put(entry.getKey(), add);
        }

        if (plugin instanceof NCGame) {
            NCGame ncGame = (NCGame) plugin;
            for (Map.Entry<Player, Integer> entry : scoreAdd.entrySet()) {
                ncGame.addScore(entry.getKey(), entry.getValue());
            }
        }

        for (Player p : players) {
            int myScore = scores.getOrDefault(p, 0);
            int myRank = playerRank.getOrDefault(p, players.size());
            int myAdd = scoreAdd.getOrDefault(p, 0);
            p.sendMessage(ChatColor.GREEN + "你获得了 " + myScore + " 分，第 " + myRank + " 名！" + (myAdd > 0 ? " (+" + myAdd + "积分)" : ""));
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "===== 厨神 排行榜 =====");
        int count = 0;
        for (Map.Entry<Player, Integer> entry : playerRank.entrySet()) {
            if (count >= 4) break;
            Player p = entry.getKey();
            int r = entry.getValue();
            int add = scoreAdd.getOrDefault(p, 0);
            int s = scores.getOrDefault(p, 0);
            Bukkit.broadcastMessage(ChatColor.YELLOW + "第" + r + "名：" + ChatColor.WHITE + p.getName() + " (厨神分 " + s + ") (+" + add + "积分)");
            count++;
        }

        cleanupAndReturnLobby();
    }

    private void endGameDirectly() {
        state = GameState.ENDED;
        cleanupAndReturnLobby();
    }

    private void cleanupAndReturnLobby() {
        HandlerList.unregisterAll(this);
        if (gameTask != null) gameTask.cancel();
        for (BukkitTask task : chestTasks.values()) task.cancel();
        chestTasks.clear();

        for (BukkitTask task : spectatorWatchTasks.values()) task.cancel();
        spectatorWatchTasks.clear();

        for (Player p : players) {
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            p.updateInventory();
        }
        for (Player p : lateSpectators) {
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            p.updateInventory();
        }
        for (Player p : players) p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        for (Player p : lateSpectators) p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());

        if (plugin instanceof NCGame) {
            ((NCGame) plugin).onMinigameEnd();
        }
    }

    @Override
    public boolean isRunning() {
        return state != GameState.ENDED && state != GameState.WAITING;
    }

    @Override
    public void addLatePlayer(Player player) {
        if (!lateSpectators.contains(player)) {
            lateSpectators.add(player);
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(config.getSpectatorSpawn());
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || !lateSpectators.contains(player)) {
                        cancel();
                        spectatorWatchTasks.remove(player);
                        return;
                    }
                    if (player.getGameMode() != GameMode.SPECTATOR) {
                        player.setGameMode(GameMode.SPECTATOR);
                    }
                }
            }.runTaskTimer(plugin, 0L, 20L);
            spectatorWatchTasks.put(player, task);
        }
    }

    // ========== 事件处理 ==========
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // 村民无敌
        if (event.getEntity() instanceof Villager) {
            event.setCancelled(true);
            return;
        }

        if (state != GameState.PLAYING) {
            if (event.getEntity() instanceof Player) event.setCancelled(true);
            return;
        }

        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            event.setCancelled(true);
            return;
        }

        if (event.getDamager() instanceof Player && event.getEntity() instanceof LivingEntity) {
            Player player = (Player) event.getDamager();
            if (!players.contains(player) || !alivePlayers.contains(player)) return;
            event.setCancelled(false);
            return;
        }

        event.setCancelled(true);
    }

    // 动物死亡补充（通过自定义名称识别）
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (state != GameState.PLAYING) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof Animals)) return;
        String customName = entity.getCustomName();
        if (customName == null || !customName.startsWith("chef_")) return;

        EntityType type = entity.getType();
        Integer targetCount = config.getAnimals().get(type.toString());
        if (targetCount == null) return;

        // 延迟检查存活数量
        new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.PLAYING) return;
                Location pos1 = config.getAnimalRegionPos1();
                Location pos2 = config.getAnimalRegionPos2();
                if (pos1 == null || pos2 == null) return;
                World world = pos1.getWorld();
                int current = 0;
                for (Entity e : world.getEntities()) {
                    if (e.getType() == type && e.isValid() && !e.isDead() &&
                            e.getCustomName() != null && e.getCustomName().startsWith("chef_")) {
                        current++;
                    }
                }
                int need = targetCount - current;
                for (int i = 0; i < need; i++) {
                    Location loc = randomLocationInRegion(pos1, pos2);
                    if (loc != null) {
                        Entity newEntity = world.spawnEntity(loc, type);
                        newEntity.setCustomName("chef_" + type.toString().toLowerCase());
                        newEntity.setCustomNameVisible(false);
                    }
                }
            }
        }.runTask(plugin);
    }

    private boolean isInRegion(Location loc, Location pos1, Location pos2) {
        if (!loc.getWorld().equals(pos1.getWorld())) return false;
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        return loc.getX() >= minX && loc.getX() <= maxX
                && loc.getY() >= minY && loc.getY() <= maxY
                && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    private Location randomLocationInRegion(Location pos1, Location pos2) {
        World world = pos1.getWorld();
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        return new Location(world,
                minX + random.nextDouble() * (maxX - minX),
                minY + random.nextDouble() * (maxY - minY),
                minZ + random.nextDouble() * (maxZ - minZ));
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (state != GameState.PLAYING) return;
        Player player = event.getPlayer();
        if (!players.contains(player) || !alivePlayers.contains(player)) return;
        if (event.getRightClicked() instanceof Villager) {
            int idx = taskIndex.getOrDefault(player, 0);
            if (idx >= tasks.size()) {
                player.sendMessage(ChatColor.RED + "你已经完成了所有任务！");
                return;
            }
            ChefMConfig.Task task = tasks.get(idx);
            // 统计背包中该材料数量，但排除最后一栏（索引8）的任务展示物品
            int totalCount = 0;
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                if (i == 8) continue; // 跳过任务展示栏
                ItemStack stack = player.getInventory().getItem(i);
                if (stack != null && stack.getType() == task.material) {
                    totalCount += stack.getAmount();
                }
            }
            if (totalCount >= task.amount) {
                // 消耗所需数量
                int toRemove = task.amount;
                for (int i = 0; i < player.getInventory().getSize(); i++) {
                    if (i == 8) continue;
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack != null && stack.getType() == task.material) {
                        int remove = Math.min(stack.getAmount(), toRemove);
                        stack.setAmount(stack.getAmount() - remove);
                        toRemove -= remove;
                        if (toRemove <= 0) break;
                    }
                }
                player.updateInventory();
                int currentScore = scores.getOrDefault(player, 0) + task.amount;
                scores.put(player, currentScore);
                taskIndex.put(player, idx + 1);
                player.sendMessage(ChatColor.GREEN + "提交成功！当前分数: " + currentScore);
                // 全服完成提示
                String completeMsg = ChatColor.GREEN + player.getName() + " " +
                        ChatColor.AQUA + "完成了 " + ChatColor.RED + task.getDisplayName() + ChatColor.AQUA + "！";
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle("", completeMsg, 10, 70, 10);
                }
                showCurrentTaskToAll();
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            } else {
                player.sendMessage(ChatColor.RED + "你需要 " + task.amount + " 个 " + task.getDisplayName() + " 来完成此任务");
            }
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!players.contains(player) && !lateSpectators.contains(player)) return;

        if (lateSpectators.contains(player) && player.getGameMode() != GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SPECTATOR);
        }

        if (!alivePlayers.contains(player) || lateSpectators.contains(player)) {
            Location loc = player.getLocation();
            Location center = config.getSpectatorRegionCenter();
            if (center != null && loc.getWorld().equals(center.getWorld())) {
                double radius = config.getSpectatorRegionRadius();
                if (loc.distanceSquared(center) > radius * radius) {
                    player.teleport(config.getSpectatorSpawn());
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (state == GameState.PREPARE || state == GameState.PLAYING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (state == GameState.PREPARE || state == GameState.PLAYING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (players.contains(player) && (state == GameState.PREPARE || state == GameState.PLAYING)) {
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (players.contains(player)) {
                event.setCancelled(true);
                player.setFoodLevel(20);
                player.setSaturation(10f);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (players.contains(player)) {
            eliminatePlayer(player);
        }
        if (lateSpectators.contains(player)) {
            lateSpectators.remove(player);
            BukkitTask task = spectatorWatchTasks.remove(player);
            if (task != null) task.cancel();
        }
    }

    private void eliminatePlayer(Player player) {
        if (!alivePlayers.remove(player)) return;
        eliminatedPlayers.add(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(config.getSpectatorSpawn());

        for (Player p : players) {
            p.sendTitle("", ChatColor.AQUA + player.getName() + " 被淘汰了", 0, 40, 10);
        }

        if (alivePlayers.size() < getRequiredPlayers()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (state == GameState.PLAYING) endGame();
            }, 20L);
        }
    }
}