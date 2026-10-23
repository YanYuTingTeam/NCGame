package cn.nanchi765.ncgame.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.function.Function;

public class RankUtils {

    public static final int[] RANK_SCORES = {10, 7, 5, 3, 1};

    public static Map<Player, Integer> buildCompetitionRanking(List<Player> players, Comparator<Player> comparator) {
        List<Player> sorted=new ArrayList<>(players);
        sorted.sort(comparator);
        Map<Player, Integer> rankings=new LinkedHashMap<>();
        int rank = 1;
        for (int i=0; i<sorted.size(); i++) {
            if (i>0 && comparator.compare(sorted.get(i-1), sorted.get(i))!=0) {rank = i+1;}
            rankings.put(sorted.get(i), rank);
        }
        return rankings;
    }

    public static Map<Player, Integer> buildRaceRanking(List<Player> players, List<Player> finishOrder, Function<Player, Integer> distanceFunc) {
        List<Player> unfinished=new ArrayList<>(players);
        unfinished.removeAll(new HashSet<>(finishOrder));
        List<Player> reversed=new ArrayList<>(finishOrder);
        Collections.reverse(reversed);
        Map<Player, Long> keys=new HashMap<>();
        for (Player p : unfinished) {keys.put(p, distanceFunc.apply(p).longValue());}
        for (int i=0; i<reversed.size(); i++) {keys.put(reversed.get(i), -10000000000L - i);}
        List<Player> sorted=new ArrayList<>(unfinished);
        sorted.addAll(reversed);
        return buildCompetitionRanking(sorted, Comparator.comparingLong(keys::get));
    }

    public static int getScoreByRank(int rank) {
        return rank >= 1 && rank <= RANK_SCORES.length ? RANK_SCORES[rank-1] : 0;
    }

    public static Map<Player, Integer> calculateScores(Map<Player, Integer> rankings) {
        Map<Player, Integer> scores=new HashMap<>();
        rankings.forEach((player, rank) -> scores.put(player, getScoreByRank(rank)));
        return scores;
    }

    public static Map<Player, Integer> buildSurvivalRanking(List<Player> alivePlayers, List<Player> eliminatedInOrder) {
        List<Player> reversed=new ArrayList<>(eliminatedInOrder);
        Collections.reverse(reversed);
        Map<Player, Long> keys=new HashMap<>();
        for (Player p : alivePlayers) {keys.put(p, 0L);}
        for (int i=0; i<reversed.size(); i++) {keys.put(reversed.get(i), (long)(i+1));}
        List<Player> sorted=new ArrayList<>(alivePlayers);
        sorted.addAll(reversed);
        return buildCompetitionRanking(sorted, Comparator.comparingLong(keys::get));
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