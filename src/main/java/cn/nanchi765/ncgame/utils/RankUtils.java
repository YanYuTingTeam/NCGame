package cn.nanchi765.ncgame.utils;

import org.bukkit.entity.Player;
import java.util.*;

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
}