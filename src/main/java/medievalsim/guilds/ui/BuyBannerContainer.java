/*
 * BuyBannerContainer - Container for buying guild banners
 * Part of Medieval Sim Mod guild management system.
 * Per docs: shows cost and placement options for guild banners.
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

/**
 * Container for the Buy Banner modal/UI.
 * Shows banner cost and purchase options.
 */
public class BuyBannerContainer extends Container {
    
    public static int CONTAINER_ID;
    
    private final int guildID;
    private final String guildName;
    private final long bannerCost;
    private final int currentBannerCount;
    private final int maxBanners;
    
    public BuyBannerContainer(NetworkClient client, int uniqueSeed, Packet content) {
        super(client, uniqueSeed);
        
        if (content != null) {
            PacketReader reader = new PacketReader(content);
            this.guildID = reader.getNextInt();
            this.guildName = reader.getNextString();
            this.bannerCost = reader.getNextLong();
            this.currentBannerCount = reader.getNextInt();
            this.maxBanners = reader.getNextInt();
        } else {
            this.guildID = -1;
            this.guildName = "Unknown";
            this.bannerCost = 500;
            this.currentBannerCount = 0;
            this.maxBanners = 1;
        }
        
        ModLogger.debug("BuyBannerContainer created: guildID=%d, cost=%d, current=%d, max=%d", 
            guildID, bannerCost, currentBannerCount, maxBanners);
    }
    
    public int getGuildID() { return guildID; }
    public String getGuildName() { return guildName; }
    public long getBannerCost() { return bannerCost; }
    public int getCurrentBannerCount() { return currentBannerCount; }
    public int getMaxBanners() { return maxBanners; }
    public boolean canBuyMore() { return currentBannerCount < maxBanners; }
    
    // === Registration ===
    
    public static void registerContainer() {
        CONTAINER_ID = ContainerRegistry.registerContainer(
            // Client handler - creates the UI form
            (client, uniqueSeed, content) -> {
                BuyBannerContainer container = new BuyBannerContainer(
                    client.getClient(), uniqueSeed, content);
                return new BuyBannerForm(client, container);
            },
            // Server handler - creates the server-side container
            (client, uniqueSeed, content, serverObject) -> new BuyBannerContainer(
                (NetworkClient) client, uniqueSeed, content)
        );
        
        ModLogger.info("Registered BuyBannerContainer: ID=%d", CONTAINER_ID);
    }
    
    /**
     * Open the buy banner UI for a player.
     */
    public static void openBuyBannerUI(ServerClient serverClient, Packet content) {
        PacketOpenContainer openPacket = new PacketOpenContainer(CONTAINER_ID, content);
        ContainerRegistry.openAndSendContainer(serverClient, openPacket);
        ModLogger.debug("Opened buy banner UI for player");
    }
}
