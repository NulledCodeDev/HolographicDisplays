package com.gmail.filoghost.holographicdisplays.bridge.protocollib;

import com.gmail.filoghost.holographicdisplays.nms.interfaces.NMSManager;
import com.gmail.filoghost.holographicdisplays.object.CraftHologram;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public interface ProtocolLibHook {

    public boolean hook(Plugin plugin, NMSManager nmsManager);

    public void sendDestroyEntitiesPacket(Player player, CraftHologram hologram);

    public void sendCreateEntitiesPacket(Player player, CraftHologram hologram);

}
