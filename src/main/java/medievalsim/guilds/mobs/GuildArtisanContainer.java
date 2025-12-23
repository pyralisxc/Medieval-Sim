/*
 * Guild Artisan Container for Medieval Sim Mod
 * Container for interacting with the guild artisan NPC.
 */
package medievalsim.guilds.mobs;

import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkClient;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketOpenContainer;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ContainerRegistry;
import necesse.entity.mobs.Mob;
import necesse.inventory.container.Container;

/**
 * Container for guild artisan interactions.
 * Handles guild creation, crest editing, and other services.
 */
public class GuildArtisanContainer extends Container {

    public static int CONTAINER_ID;

    // The player's current guild ID (or -1 if not in a guild)
    private int playerGuildID = -1;
    // Additional client hints
    private int playerRankLevel = 0;
    private boolean plotFlagsEnabled = false;
    private boolean hasUnlockedGuilds = true; // Default to true for backwards compat

    public int getPlayerRankLevel() { return playerRankLevel; }
    public boolean isPlotFlagsEnabled() { return plotFlagsEnabled; }
    public boolean hasUnlockedGuilds() { return hasUnlockedGuilds; }
    
    public GuildArtisanContainer(NetworkClient client, int uniqueSeed, Packet content) {
        super(client, uniqueSeed);
        
        if (content != null) {
            PacketReader reader = new PacketReader(content);
            this.playerGuildID = reader.getNextInt();
            // Additional data: player's rank level and plot flags enabled
            this.playerRankLevel = reader.getNextInt();
            this.plotFlagsEnabled = reader.getNextBoolean();
            // Unlock status (if present in packet)
            try {
                this.hasUnlockedGuilds = reader.getNextBoolean();
            } catch (Exception e) {
                this.hasUnlockedGuilds = true; // Default if not in packet
            }
        }
        
        ModLogger.debug("GuildArtisanContainer created: playerGuildID=%d, rank=%d, plotsEnabled=%b, unlocked=%b", 
            playerGuildID, playerRankLevel, plotFlagsEnabled, hasUnlockedGuilds);
    }

    public int getPlayerGuildID() {
        return playerGuildID;
    }
    
    public boolean isPlayerInGuild() {
        return playerGuildID >= 0;
    }

    // === Registration ===

    public static void registerContainer() {
        CONTAINER_ID = ContainerRegistry.registerContainer(
            // Client handler - creates the UI form
            (client, uniqueSeed, content) -> {
                GuildArtisanContainer container = new GuildArtisanContainer(
                    client.getClient(), uniqueSeed, content);
                return new GuildArtisanContainerForm(client, container);
            },
            // Server handler - creates the server-side container  
            (client, uniqueSeed, content, serverObject) -> new GuildArtisanContainer(
                (NetworkClient) client, uniqueSeed, content)
        );
        
        ModLogger.info("Registered GuildArtisanContainer: ID=%d", CONTAINER_ID);
    }
    
    /**
     * Open the guild artisan UI for a player interacting with the NPC.
     */
    public static void openArtisanUI(ServerClient serverClient, int playerGuildID) {
        // Create packet with player's guild info
        Packet content = new Packet();
        PacketWriter writer = new PacketWriter(content);
        writer.putNextInt(playerGuildID);
        
        // Open the container
        PacketOpenContainer openPacket = new PacketOpenContainer(CONTAINER_ID, content);
        ContainerRegistry.openAndSendContainer(serverClient, openPacket);
        
        ModLogger.debug("Opened artisan UI for player, guildID=%d", playerGuildID);
    }
}
