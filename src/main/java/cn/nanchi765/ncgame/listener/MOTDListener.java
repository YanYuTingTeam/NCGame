package cn.nanchi765.ncgame.listener;

import cn.nanchi765.ncgame.NCGame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

public class MOTDListener implements Listener {

    @EventHandler
    public void onPinged(ServerListPingEvent event) {
        if (NCGame.getInstance().getGameManager().getPlayers().size() > 16) {
            event.setMotd("Full!");
            return;
        }


        event.setMotd("Waiting...");
    }
}
