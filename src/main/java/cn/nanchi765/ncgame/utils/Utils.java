package cn.nanchi765.ncgame.utils;

import org.bukkit.ChatColor;

public class Utils {
    public static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
