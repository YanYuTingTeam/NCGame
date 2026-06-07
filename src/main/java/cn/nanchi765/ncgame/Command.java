package cn.nanchi765.ncgame;

import cn.nanchi765.ncgame.config.EnableGameConfig;
import cn.nanchi765.ncgame.manager.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class Command implements CommandExecutor {

    private final NCGame plugin;

    public Command(NCGame plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        if (!sender.hasPermission("ncgame.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此操作。");
            return true;
        }
        GameManager gameManager = plugin.getGameManager();
        switch (args[0].toLowerCase()) {
            case "setcountdown":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "用法: /ncgame setcountdown <秒数>");
                    return true;
                }
                int seconds;
                try {
                    seconds = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "无效数字: " + args[1]);
                    return true;
                }

                if (gameManager.getGameStartingTask() != null) {
                    gameManager.getGameStartingTask().setCountdown(seconds);
                } else if (gameManager.getGamePrepareTask() != null) {
                    gameManager.getGamePrepareTask().setCountdown(seconds);
                } else if (gameManager.getGamePlayingTask() != null) {
                    gameManager.getGamePlayingTask().setCountdown(seconds);
                } else {
                    sender.sendMessage(ChatColor.RED + "当前没有正在运行的任务。");
                }
                break;
            case "reload":
                break;
            case "enable":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "用法: /ncgame enable <游戏名称>");
                    return true;
                }
                if (!sender.hasPermission("ncgame.admin")) {
                    sender.sendMessage(ChatColor.RED + "你没有权限执行此操作。");
                    return true;
                }
                setGameStatus(sender, args[1], true);
                break;
            case "disable":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "用法: /ncgame disable <游戏名称>");
                    return true;
                }
                if (!sender.hasPermission("ncgame.admin")) {
                    sender.sendMessage(ChatColor.RED + "你没有权限执行此操作。");
                    return true;
                }
                setGameStatus(sender, args[1], false);
                break;
            case "list":
                listGames(sender);
                break;
            default:
                showHelp(sender);
                break;
        }
        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========== NCGame 小游戏派对 ==========");
        sender.sendMessage(ChatColor.WHITE + "作者: " + ChatColor.AQUA + "NanChi765");
        sender.sendMessage(ChatColor.WHITE + "版本: " + ChatColor.AQUA + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.WHITE + "Refactor By " + ChatColor.AQUA + "YukiEnd");
        if (!sender.hasPermission("ncgame.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "指令帮助:");
            sender.sendMessage(ChatColor.GREEN + "/ncgame reload " + ChatColor.WHITE + "- 重启插件");
            sender.sendMessage(ChatColor.GREEN + "/ncgame enable <游戏> " + ChatColor.WHITE + "- 启用游戏");
            sender.sendMessage(ChatColor.GREEN + "/ncgame disable <游戏> " + ChatColor.WHITE + "- 禁用游戏");
            sender.sendMessage(ChatColor.GREEN + "/ncgame list " + ChatColor.WHITE + "- 列出所有游戏");
        }
    }

    private void setGameStatus(CommandSender sender, String gameName, boolean enable) {
        EnableGameConfig enableGameConfig = NCGame.getInstance().getEnableGameConfig();
        Map<String, Boolean> allGames = enableGameConfig.getAllGames();
        if (!allGames.containsKey(gameName)) {
            sender.sendMessage(ChatColor.RED + "游戏 '" + gameName + "' 不存在！");
            return;
        }
        enableGameConfig.setGameEnabled(gameName, enable);
        String status = enable ? ChatColor.GREEN + "启用" : ChatColor.RED + "禁用";
        sender.sendMessage(ChatColor.YELLOW + "游戏 " + ChatColor.WHITE + gameName + " 已" + status);
    }

    private void listGames(CommandSender sender) {
        EnableGameConfig enableGameConfig = NCGame.getInstance().getEnableGameConfig();
        Map<String, Boolean> games = enableGameConfig.getAllGames();
        if (games.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "未找到任何游戏配置，请联系管理员检查 enablegame.yml 文件。");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "===== 游戏列表 =====");
        for (Map.Entry<String, Boolean> entry : games.entrySet()) {
            String name = entry.getKey();
            boolean enabled = entry.getValue();
            String status = enabled ? ChatColor.GREEN + "✔ 开启" : ChatColor.RED + "✘ 关闭";
            sender.sendMessage(ChatColor.WHITE + name + " : " + status);
        }
    }
}
