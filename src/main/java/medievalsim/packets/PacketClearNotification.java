/*
 * PacketClearNotification - Client requests to clear a single notification
 * Part of Medieval Sim Mod guild management system.
 */
package medievalsim.packets;

import medievalsim.guilds.GuildManager;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Client->Server packet to clear (dismiss) a single notification.
 */
public class PacketClearNotification extends Packet {

    private long notificationID;

    public PacketClearNotification(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.notificationID = reader.getNextLong();
    }

    public PacketClearNotification(long notificationID) {
        this.notificationID = notificationID;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(notificationID);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            GuildManager gm = GuildManager.get(server.world);
            if (gm == null) {
                ModLogger.warn("GuildManager null when clearing notification");
                return;
            }

            boolean cleared = gm.clearPlayerNotification(client.authentication, notificationID);
            if (cleared) {
                ModLogger.debug("Cleared notification %d for player %d", notificationID, client.authentication);
            } else {
                ModLogger.debug("Failed to clear notification %d for player %d (not found)", 
                    notificationID, client.authentication);
            }
        } catch (Exception e) {
            ModLogger.error("Error clearing notification: %s", e.getMessage());
        }
    }
}
