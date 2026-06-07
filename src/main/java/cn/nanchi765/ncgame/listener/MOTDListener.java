package cn.nanchi765.ncgame.listener;

import cn.nanchi765.ncgame.NCGame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

public class MOTDListener implements Listener {

    private final int maxPlayer = 16;

    @EventHandler
    public void onPinged(ServerListPingEvent event) {
        event.setMaxPlayers(maxPlayer);
        if (NCGame.getInstance().getGameManager().getPlayers().size() > maxPlayer) {
            event.setMotd("Full!");
            return;
        }
        event.setMotd("Waiting...");
    }
}
