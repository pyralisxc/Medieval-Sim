/*
 * Guild Teleport Container for Medieval Sim Mod
 * Server-side container that lists all guild teleport banners as destinations.
 */
package medievalsim.guilds.teleport;

import java.util.ArrayList;
import java.util.List;

import medievalsim.guilds.GuildData;
import medievalsim.guilds.GuildManager;
import medievalsim.guilds.objects.GuildTeleportBannerObjectEntity;
import medievalsim.util.ModLogger;
import necesse.engine.localization.Localization;
import necesse.engine.network.NetworkClient;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketOpenContainer;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ContainerRegistry;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.util.LevelIdentifier;
import necesse.engine.util.TeleportResult;
import necesse.gfx.GameColor;
import necesse.entity.levelEvent.TeleportEvent;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.inventory.container.Container;
import necesse.inventory.container.customAction.IntCustomAction;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;

/**
 * Server container for guild teleport system.
 * Lists all banners in the player's guild as teleport destinations.
 */
public class GuildTeleportContainer extends Container {

    public static int CONTAINER_ID;
    
    // The guild ID for this teleport session
    public final int guildID;
    
    // Source banner location (where player is teleporting from)
    public final int sourceTileX;
    public final int sourceTileY;
    public final LevelIdentifier sourceLevelIdentifier;
    
    // Teleport destinations (sent to client)
    public List<TeleportDestination> destinations = new ArrayList<>();
    
    // Actions
    public IntCustomAction teleportToSlotAction;
    
    // Configuration
    public static final int TELEPORT_DELAY = 3000; // 3 second delay
    public static final float TELEPORT_SICKNESS_SECONDS = 30f;

    /**
     * Client-side constructor - reads data from packet.
     */
    public GuildTeleportContainer(NetworkClient client, int uniqueSeed, Packet content) {
        super(client, uniqueSeed);
        
        PacketReader reader = new PacketReader(content);
        this.guildID = reader.getNextInt();
        this.sourceTileX = reader.getNextInt();
        this.sourceTileY = reader.getNextInt();
        this.sourceLevelIdentifier = new LevelIdentifier(reader);
        
        // Read destinations
        int destCount = reader.getNextInt();
        for (int i = 0; i < destCount; i++) {
            destinations.add(TeleportDestination.readFromPacket(reader));
        }
        
        setupActions();
    }

    private void setupActions() {
        // Client requests teleport to a specific slot
        this.teleportToSlotAction = registerAction(new IntCustomAction() {
            @Override
            protected void run(int slotIndex) {
                if (!client.isServer()) return;
                
                ServerClient serverClient = client.getServerClient();
                if (serverClient == null) return;
                
                if (slotIndex < 0 || slotIndex >= destinations.size()) {
                    serverClient.sendChatMessage(GameColor.RED.getColorCode() + "Invalid teleport destination.");
                    return;
                }
                
                TeleportDestination dest = destinations.get(slotIndex);
                performTeleport(serverClient, dest);
            }
        });
    }
    
    /**
     * Perform the teleport to the given destination.
     */
    private void performTeleport(ServerClient serverClient, TeleportDestination dest) {
        if (serverClient == null || dest == null) return;
        
        // Create teleport event on the client's current level
        Level currentLevel = serverClient.playerMob.getLevel();
        if (currentLevel == null) return;
        
        TeleportEvent event = new TeleportEvent(
            serverClient, 
            TELEPORT_DELAY, 
            dest.levelIdentifier,
            TELEPORT_SICKNESS_SECONDS,
            null,  // No custom level generator
            newLevel -> {
                // Return teleport result - where to place player
                return new TeleportResult(true, dest.levelIdentifier, dest.x, dest.y);
            }
        );
        
        // Add the event to the level
        currentLevel.entityManager.addLevelEvent(event);
        
        // Close container
        serverClient.closeContainer(true);
        
        ModLogger.debug("Teleporting %s to %s at %d,%d", serverClient.getName(), dest.levelIdentifier.stringID, dest.x, dest.y);
    }
    
    /**
     * Gather destinations from a server.
     */
    public static List<TeleportDestination> gatherDestinations(Server server, int guildID, 
            LevelIdentifier sourceLevelId, int sourceTileX, int sourceTileY) {
        List<TeleportDestination> destinations = new ArrayList<>();
        
        if (server == null) return destinations;
        
        GuildManager gm = GuildManager.get(server.world);
        if (gm == null) return destinations;
        
        GuildData guild = gm.getGuild(guildID);
        if (guild == null) return destinations;
        
        // Find all guild teleport banners belonging to this guild
        int bannerObjectID = ObjectRegistry.getObjectID("guildteleportbanner");
        
        // Iterate over loaded levels to find banners
        for (Level level : server.world.levelManager.getLoadedLevels()) {
            if (level == null) continue;
            
            LevelIdentifier levelId = level.getIdentifier();
            
            // Find all our banner entities in this level
            for (ObjectEntity oe : level.entityManager.objectEntities) {
                if (!(oe instanceof GuildTeleportBannerObjectEntity)) continue;
                
                GuildTeleportBannerObjectEntity bannerEntity = (GuildTeleportBannerObjectEntity) oe;
                if (!bannerEntity.belongsToGuild(guildID)) continue;
                
                int bx = bannerEntity.tileX;
                int by = bannerEntity.tileY;
                
                // Check if object at location is still a banner
                GameObject obj = level.getObject(bx, by);
                if (obj == null || obj.getID() != bannerObjectID) continue;
                
                // Skip the source banner
                if (levelId.equals(sourceLevelId) && bx == sourceTileX && by == sourceTileY) {
                    continue;
                }
                
                // Calculate distance (if same level)
                float distance = -1;
                if (levelId.equals(sourceLevelId)) {
                    int dx = bx * 32 - sourceTileX * 32;
                    int dy = by * 32 - sourceTileY * 32;
                    distance = (float) Math.sqrt(dx * dx + dy * dy) / 32f;
                }
                
                String displayName = bannerEntity.getFullDisplayName(gm);
                
                destinations.add(new TeleportDestination(
                    displayName,
                    levelId,
                    bx * 32 + 16, // Center of tile
                    by * 32 + 16,
                    distance
                ));
            }
        }
        
        ModLogger.debug("Found %d teleport destinations for guild %d", destinations.size(), guildID);
        return destinations;
    }
    
    /**
     * Static method to open the teleport UI for a client.
     */
    public static void openTeleportUI(ServerClient serverClient, int guildID, int tileX, int tileY, Level level) {
        LevelIdentifier levelId = level.getIdentifier();
        List<TeleportDestination> destinations = gatherDestinations(
            serverClient.getServer(), guildID, levelId, tileX, tileY);
        
        Packet content = new Packet();
        PacketWriter writer = new PacketWriter(content);
        
        writer.putNextInt(guildID);
        writer.putNextInt(tileX);
        writer.putNextInt(tileY);
        levelId.writePacket(writer);
        
        // Write destinations
        writer.putNextInt(destinations.size());
        for (TeleportDestination dest : destinations) {
            dest.writeToPacket(writer);
        }
        
        PacketOpenContainer openPacket = new PacketOpenContainer(CONTAINER_ID, content);
        ContainerRegistry.openAndSendContainer(serverClient, openPacket);
        
        ModLogger.debug("Opened teleport UI for player, guildID=%d, %d destinations", guildID, destinations.size());
    }
    
    /**
     * Register this container type.
     */
    public static void registerContainer() {
        CONTAINER_ID = ContainerRegistry.registerContainer(
            // Client handler - creates the UI form
            (client, uniqueSeed, content) -> {
                GuildTeleportContainer container = new GuildTeleportContainer(
                    client.getClient(), uniqueSeed, content);
                return new GuildTeleportContainerForm(client, container);
            },
            // Server handler - creates the server-side container
            (client, uniqueSeed, content, serverObject) -> new GuildTeleportContainer(
                (NetworkClient) client, uniqueSeed, content)
        );
        
        ModLogger.info("Registered guild teleport container: ID=%d", CONTAINER_ID);
    }
    
    /**
     * Represents a teleport destination (a guild banner).
     */
    public static class TeleportDestination {
        public final String displayName;
        public final LevelIdentifier levelIdentifier;
        public final int x;
        public final int y;
        public final float distance; // -1 if different level
        
        public TeleportDestination(String displayName, LevelIdentifier levelIdentifier, int x, int y, float distance) {
            this.displayName = displayName;
            this.levelIdentifier = levelIdentifier;
            this.x = x;
            this.y = y;
            this.distance = distance;
        }
        
        public void writeToPacket(PacketWriter writer) {
            writer.putNextString(displayName);
            levelIdentifier.writePacket(writer);
            writer.putNextInt(x);
            writer.putNextInt(y);
            writer.putNextFloat(distance);
        }
        
        public static TeleportDestination readFromPacket(PacketReader reader) {
            String name = reader.getNextString();
            LevelIdentifier levelId = new LevelIdentifier(reader);
            int x = reader.getNextInt();
            int y = reader.getNextInt();
            float distance = reader.getNextFloat();
            return new TeleportDestination(name, levelId, x, y, distance);
        }
        
        public String getDistanceString() {
            if (distance < 0) {
                return Localization.translate("ui", "differentisland");
            }
            return String.format("%.0f tiles", distance);
        }
    }
}
