package cn.nanchi765.ncgame.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.function.Function;

public class RankUtils {
    public static Map<Player, Integer> buildSurvivalRanking(List<Player> alivePlayers, List<Player> eliminatedInOrder) {
        Map<Player, Integer> rankings=new LinkedHashMap<>();
        int nextRank = alivePlayers.isEmpty()? 1 :2;
        for (Player p : alivePlayers) {rankings.put(p,1);}
        List<Player> reversed=new ArrayList<>(eliminatedInOrder);
        Collections.reverse(reversed);
        for (Player p:reversed) {rankings.put(p,nextRank++);}
        return rankings;
    }

    public static Map<Integer, List<Player>> groupByRank(Map<Player, Integer> rankings) {
        Map<Integer, List<Player>> grouped=new LinkedHashMap<>();
        for (Map.Entry<Player, Integer> entry :rankings.entrySet()) {grouped.computeIfAbsent(entry.getValue(),
                k ->new ArrayList<>()).add(entry.getKey());}
        return grouped;
    }

    public static void broadcastLeaderboard(String gameName, Map<Player, Integer> rankings, Map<Player, Integer> scoreMap) {
        //Bukkit.broadcastMessage("§6===== "+gameName+" 排行榜 =====");
        Bukkit.broadcastMessage("§6 "); //滚木消息
        if (rankings.isEmpty()) {
            Bukkit.broadcastMessage("§c无人完成本轮游戏！");
            return;
        }
        int count = 0;
        for (Map.Entry<Integer, List<Player>> entry :groupByRank(rankings).entrySet()) {
            if (count>=4) break;
            int rank=entry.getKey();
            List<Player> players =entry.getValue();
            StringBuilder names =new StringBuilder();
            for (int i=0; i<players.size(); i++) {
                if (i>0) names.append(",");
                names.append(players.get(i).getName());
            }
            Player first =players.get(0);
            int add =scoreMap.getOrDefault(first, 0);
            Bukkit.broadcastMessage("§f第"+rank+"名："+ names+" (+"+add+"分)");
            count++;
        }
    }
}