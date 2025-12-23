/*
 * ManageGuildsContainer - Container for managing guild memberships
 * Part of Medieval Sim Mod guild management system.
 * Per docs: handles per-guild leave, buy banner, and guild selection.
 */
package medievalsim.guilds.ui;

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
 * Container for the Manage Guilds UI.
 * Shows player's guild memberships with per-guild actions.
 */
public class ManageGuildsContainer extends Container {
    
    public static int CONTAINER_ID;
    
    // Guild membership data for display
    private final List<GuildMembershipInfo> memberships = new ArrayList<>();
    
    /**
     * Info about a player's guild membership.
     */
    public static class GuildMembershipInfo {
        public final int guildID;
        public final String guildName;
        public final int rankLevel;
        public final long treasury;
        public final int memberCount;
        public final int bannerCount;
        
        public GuildMembershipInfo(int guildID, String guildName, int rankLevel, 
                                   long treasury, int memberCount, int bannerCount) {
            this.guildID = guildID;
            this.guildName = guildName;
            this.rankLevel = rankLevel;
            this.treasury = treasury;
            this.memberCount = memberCount;
            this.bannerCount = bannerCount;
        }
    }
    
    public ManageGuildsContainer(NetworkClient client, int uniqueSeed, Packet content) {
        super(client, uniqueSeed);
        
        if (content != null) {
            PacketReader reader = new PacketReader(content);
            int count = reader.getNextInt();
            
            for (int i = 0; i < count; i++) {
                int guildID = reader.getNextInt();
                String guildName = reader.getNextString();
                int rankLevel = reader.getNextInt();
                long treasury = reader.getNextLong();
                int memberCount = reader.getNextInt();
                int bannerCount = reader.getNextInt();
                
                memberships.add(new GuildMembershipInfo(
                    guildID, guildName, rankLevel, treasury, memberCount, bannerCount));
            }
        }
        
        ModLogger.debug("ManageGuildsContainer created with %d membership(s)", memberships.size());
    }
    
    public List<GuildMembershipInfo> getMemberships() {
        return memberships;
    }
    
    // === Registration ===
    
    public static void registerContainer() {
        CONTAINER_ID = ContainerRegistry.registerContainer(
            // Client handler - creates the UI form
            (client, uniqueSeed, content) -> {
                ManageGuildsContainer container = new ManageGuildsContainer(
                    client.getClient(), uniqueSeed, content);
                return new ManageGuildsForm(client, container);
            },
            // Server handler - creates the server-side container
            (client, uniqueSeed, content, serverObject) -> new ManageGuildsContainer(
                (NetworkClient) client, uniqueSeed, content)
        );
        
        ModLogger.info("Registered ManageGuildsContainer: ID=%d", CONTAINER_ID);
    }
    
    /**
     * Open the manage guilds UI for a player.
     */
    public static void openManageGuildsUI(ServerClient serverClient, Packet content) {
        PacketOpenContainer openPacket = new PacketOpenContainer(CONTAINER_ID, content);
        ContainerRegistry.openAndSendContainer(serverClient, openPacket);
        ModLogger.debug("Opened manage guilds UI for player");
    }
}
