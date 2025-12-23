/*
 * PacketClearAllGuildNotifications - Client requests to clear all notifications
 * Part of Medieval Sim Mod guild management system.
 */
package medievalsim.packets;

import medievalsim.guilds.GuildManager;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Client->Server packet to clear all notifications for the player.
 */
public class PacketClearAllGuildNotifications extends Packet {

    public PacketClearAllGuildNotifications(byte[] data) {
        super(data);
    }

    public PacketClearAllGuildNotifications() {
        // No parameters needed
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            GuildManager gm = GuildManager.get(server.world);
            if (gm == null) {
                ModLogger.warn("GuildManager null when clearing all notifications");
                return;
            }

            int cleared = gm.clearAllPlayerNotifications(client.authentication);
            ModLogger.debug("Cleared %d notification(s) for player %d", cleared, client.authentication);
        } catch (Exception e) {
            ModLogger.error("Error clearing all notifications: %s", e.getMessage());
        }
    }
}
