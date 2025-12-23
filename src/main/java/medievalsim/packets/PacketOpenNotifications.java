/*
 * PacketOpenNotifications - Client requests to open the Notifications inbox
 * Part of Medieval Sim Mod guild management system.
 * Per docs: shows invites, guild notices, admin messages with clear/clear-all actions.
 */
package medievalsim.packets;

import medievalsim.guilds.GuildManager;
import medievalsim.guilds.notifications.GuildNotification;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

import java.util.List;

/**
 * Client->Server packet requesting to open the Notifications UI.
 * Server responds by opening the NotificationsContainer with player's notifications.
 */
public class PacketOpenNotifications extends Packet {

    public PacketOpenNotifications(byte[] data) {
        super(data);
    }

    public PacketOpenNotifications() {
        // No parameters needed
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            GuildManager gm = GuildManager.get(server.world);
            if (gm == null) {
                ModLogger.warn("GuildManager null when processing notifications request");
                return;
            }

            // Get player's notifications
            List<GuildNotification> notifications = gm.getPlayerNotifications(client.authentication);
            
            Packet content = new Packet();
            PacketWriter writer = new PacketWriter(content);
            
            // Write notification count
            int count = notifications != null ? notifications.size() : 0;
            writer.putNextInt(count);
            
            // Write each notification
            if (notifications != null) {
                for (GuildNotification notif : notifications) {
                    writer.putNextLong(notif.getNotificationID());
                    writer.putNextInt(notif.getType().ordinal());
                    writer.putNextString(notif.getTitle());
                    writer.putNextString(notif.getMessage());
                    writer.putNextLong(notif.getTimestamp());
                    writer.putNextInt(notif.getGuildID());
                    writer.putNextBoolean(notif.isRead());
                    // Optional action data (e.g., invite guildID for accept/decline)
                    writer.putNextString(notif.getActionData() != null ? notif.getActionData() : "");
                }
            }
            
            // Open the container
            medievalsim.guilds.ui.NotificationsContainer.openNotificationsUI(client, content);
            ModLogger.debug("Opened notifications UI for player %d with %d notification(s)", 
                client.authentication, count);
                
        } catch (Exception e) {
            ModLogger.error("Error processing notifications request: %s", e.getMessage());
        }
    }
}
