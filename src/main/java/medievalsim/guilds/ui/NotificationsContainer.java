/*
 * NotificationsContainer - Container for guild notifications inbox
 * Part of Medieval Sim Mod guild management system.
 * Per docs: shows invites, guild notices, admin messages with clear actions.
 */
package medievalsim.guilds.ui;

import medievalsim.guilds.notifications.GuildNotification;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkClient;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketOpenContainer;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ContainerRegistry;
import necesse.inventory.container.Container;

import java.util.ArrayList;
import java.util.List;

/**
 * Container for the Notifications inbox UI.
 * Shows player's guild notifications with actions.
 */
public class NotificationsContainer extends Container {
    
    public static int CONTAINER_ID;
    
    // Notification data for display
    private final List<NotificationInfo> notifications = new ArrayList<>();
    
    /**
     * Info about a notification for display.
     */
    public static class NotificationInfo {
        public final long notificationID;
        public final GuildNotification.NotificationType type;
        public final String title;
        public final String message;
        public final long timestamp;
        public final int guildID;
        public final boolean read;
        public final String actionData;
        
        public NotificationInfo(long notificationID, int typeOrdinal, String title, 
                               String message, long timestamp, int guildID, 
                               boolean read, String actionData) {
            this.notificationID = notificationID;
            this.type = GuildNotification.NotificationType.values()[typeOrdinal];
            this.title = title;
            this.message = message;
            this.timestamp = timestamp;
            this.guildID = guildID;
            this.read = read;
            this.actionData = actionData;
        }
    }
    
    public NotificationsContainer(NetworkClient client, int uniqueSeed, Packet content) {
        super(client, uniqueSeed);
        
        if (content != null) {
            PacketReader reader = new PacketReader(content);
            int count = reader.getNextInt();
            
            for (int i = 0; i < count; i++) {
                long notifID = reader.getNextLong();
                int typeOrdinal = reader.getNextInt();
                String title = reader.getNextString();
                String message = reader.getNextString();
                long timestamp = reader.getNextLong();
                int guildID = reader.getNextInt();
                boolean isRead = reader.getNextBoolean();
                String actionData = reader.getNextString();
                
                notifications.add(new NotificationInfo(
                    notifID, typeOrdinal, title, message, timestamp, guildID, isRead, actionData));
            }
        }
        
        ModLogger.debug("NotificationsContainer created with %d notification(s)", notifications.size());
    }
    
    public List<NotificationInfo> getNotifications() {
        return notifications;
    }
    
    // === Registration ===
    
    public static void registerContainer() {
        CONTAINER_ID = ContainerRegistry.registerContainer(
            // Client handler - creates the UI form
            (client, uniqueSeed, content) -> {
                NotificationsContainer container = new NotificationsContainer(
                    client.getClient(), uniqueSeed, content);
                return new NotificationsForm(client, container);
            },
            // Server handler - creates the server-side container
            (client, uniqueSeed, content, serverObject) -> new NotificationsContainer(
                (NetworkClient) client, uniqueSeed, content)
        );
        
        ModLogger.info("Registered NotificationsContainer: ID=%d", CONTAINER_ID);
    }
    
    /**
     * Open the notifications UI for a player.
     */
    public static void openNotificationsUI(ServerClient serverClient, Packet content) {
        PacketOpenContainer openPacket = new PacketOpenContainer(CONTAINER_ID, content);
        ContainerRegistry.openAndSendContainer(serverClient, openPacket);
        ModLogger.debug("Opened notifications UI for player");
    }
}
