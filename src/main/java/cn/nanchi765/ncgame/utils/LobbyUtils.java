package cn.nanchi765.ncgame.utils;

import cn.nanchi765.ncgame.NCGame;
import cn.nanchi765.ncgame.config.LobbyConfig;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

public class LobbyUtils {

    public static void sendBungeeServer(Player player, String server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        player.sendPluginMessage(NCGame.getInstance(), "BungeeCord", out.toByteArray());
    }

    public static void teleportToLobby(Player player) {
        LobbyConfig lobbyConfig = NCGame.getInstance().getLobbyConfig();
        Location loc = lobbyConfig.getLocation();
        player.teleport(loc);
        Utils.resetPlayer(player);
        player.getInventory().setItem(7, createGuideBook());
        player.getInventory().setItem(lobbyConfig.getReturnSlot(), lobbyConfig.createReturnItem());
    }

    private static ItemStack createGuideBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        meta.setTitle(ChatColor.GREEN + "小游戏指南" + ChatColor.GRAY + "(右键阅读)");
        meta.setAuthor(ChatColor.GRAY + "花雨庭");
        List<String> lore = new ArrayList<>();
        meta.setLore(lore);

        // 第一页
        String page1 = ChatColor.BOLD + "     ● 小游戏指南\n" + ChatColor.RESET +
                "\n" +
                ChatColor.BOLD + "信仰方块：\n" + ChatColor.RESET +
                "地面会逐渐变色至消失;最后生还者胜。\n\n" +
                ChatColor.BOLD + "疯狂的羊：\n" + ChatColor.RESET +
                "用剪刀右键羊剪羊毛：注意有些羊会爆炸;剪毛最多者胜。\n\n" +
                ChatColor.BOLD + "颜色配对：\n" + ChatColor.RESET +
                "跳到与背包内羊毛颜色相同的方块上;最后生还者胜。";

        // 第二页
        String page2 = ChatColor.BOLD + "掘一死战：\n" + ChatColor.RESET +
                "挖掉对手脚下的雪块，最后生还者获胜。\n\n" +
                ChatColor.BOLD + "击退战：\n" + ChatColor.RESET +
                "利用击退棒将其他玩家击飞，双击空格使用二段跳:最后生还者获胜。\n\n" +
                ChatColor.BOLD + "一击必杀：\n" + ChatColor.RESET +
                "利用多箭射杀其它对手;最后生还者获胜。";

        // 第三页
        String page3 = ChatColor.BOLD + "地雷战：\n" + ChatColor.RESET +
                "躲避沿途的地雷，用最快的速度达到终点。\n\n" +
                ChatColor.BOLD + "跑跑跳跳：\n" + ChatColor.RESET +
                "跳过障碍，向终点前进！\n\n" +
                ChatColor.BOLD + "方块配对：\n" + ChatColor.RESET +
                "挖掉前面的障碍，以最快速度到送终点。\n\n" +
                ChatColor.BOLD + "死胡同：\n" + ChatColor.RESET +
                "脚下的方块不断消失，向前奔跑吧!";

        // 第四页
        String page4 = ChatColor.BOLD + "      ● 更新日志\n" + ChatColor.RESET +
                ChatColor.BOLD + "❒ 版本：1.4.0\n" +
                ChatColor.BOLD + "❑ 日期：2026/6/6\n\n" + ChatColor.RESET +
                " 1一正式上线\n" +
                " ■包含多达11款小型游戏\n" +
                " ■节奏快捷、玩法类别多";

        // 第五页
        String page5 = ChatColor.BOLD + "      ● 关于我们\n" + ChatColor.RESET +
                "《烟雨庭》是由一群热爱我的世界的小伙伴们仿造的国风小游戏服务器《花雨庭》；上线伊始，我们与其他服达成合作,相信在大家的支持下，我们会越来越好!《烟雨庭》只为你的快乐而生。\n" + ChatColor.BOLD +
                "➡官网：www.mcyyt.cn\n" +
                "➡客服：即将上线\n" +
                "➡QQ群：892702519";

        meta.addPage(page1, page2, page3, page4, page5);
        book.setItemMeta(meta);
        return book;
    }

}
