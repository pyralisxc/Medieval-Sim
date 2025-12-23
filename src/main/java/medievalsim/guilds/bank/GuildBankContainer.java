/*
 * Guild Bank Container for Medieval Sim Mod
 * Server-side container for the guild bank.
 */
package medievalsim.guilds.bank;

import medievalsim.guilds.GuildData;
import medievalsim.guilds.GuildManager;
import medievalsim.guilds.PermissionType;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkClient;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketOpenContainer;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ContainerRegistry;
import necesse.engine.world.World;
import necesse.inventory.Inventory;
import necesse.inventory.container.Container;
import necesse.inventory.container.slots.ContainerSlot;

/**
 * Server-side container for guild bank interactions.
 * Handles inventory slots for the currently selected bank tab.
 */
public class GuildBankContainer extends Container {
    
    public static int CONTAINER_ID;
    
    private final int guildID;
    private int currentTab = 0;
    
    // Slot indices
    public int BANK_SLOTS_START = -1;
    public int BANK_SLOTS_END = -1;
    
    public GuildBankContainer(NetworkClient client, int uniqueSeed, Packet content) {
        super(client, uniqueSeed);
        
        PacketReader reader = new PacketReader(content);
        this.guildID = reader.getNextInt();
        this.currentTab = reader.getNextInt();
        
        // Setup bank tab slots on server side
        if (client.isServer()) {
            setupBankSlots();
        }
        
        ModLogger.debug("GuildBankContainer created: guildID=%d, tab=%d", guildID, currentTab);
    }
    
    private void setupBankSlots() {
        ServerClient serverClient = client.getServerClient();
        if (serverClient == null) return;
        
        World world = serverClient.getServer().world;
        GuildManager manager = GuildManager.get(world);
        if (manager != null) {
            GuildData guild = manager.getGuild(guildID);
            if (guild != null) {
                GuildBank bank = guild.getBank();
                GuildBankTab tab = bank.getTab(currentTab);
                if (tab != null) {
                    Inventory tabInventory = tab.getInventory();
                    for (int i = 0; i < tabInventory.getSize(); i++) {
                        int index = addSlot(new ContainerSlot(tabInventory, i));
                        if (BANK_SLOTS_START == -1) {
                            BANK_SLOTS_START = index;
                        }
                        BANK_SLOTS_END = index;
                    }
                }
            }
        }
    }
    
    public int getGuildID() {
        return guildID;
    }
    
    public int getCurrentTab() {
        return currentTab;
    }
    
    /**
     * Register this container with the game.
     */
    public static void registerContainer() {
        CONTAINER_ID = ContainerRegistry.registerContainer(
            // Client handler - creates the UI form
            (client, uniqueSeed, content) -> {
                GuildBankContainer container = new GuildBankContainer(client.getClient(), uniqueSeed, content);
                return new GuildBankContainerForm(client, container);
            },
            // Server handler - creates the server-side container
            (client, uniqueSeed, content, serverObject) -> new GuildBankContainer(
                (NetworkClient) client,
                uniqueSeed,
                content
            )
        );
        
        ModLogger.info("Registered GuildBankContainer: ID=%d", CONTAINER_ID);
    }
    
    /**
     * Open the guild bank UI for a player.
     */
    public static void openGuildBank(ServerClient serverClient, int guildID, int tabIndex) {
        // Check if player has permission to access the bank
        World world = serverClient.getServer().world;
        GuildManager manager = GuildManager.get(world);
        if (manager == null) {
            ModLogger.warn("Cannot open guild bank - GuildManager not found");
            return;
        }
        
        GuildData guild = manager.getGuild(guildID);
        if (guild == null) {
            ModLogger.warn("Cannot open guild bank - Guild %d not found", guildID);
            return;
        }
        
        // Check player is in the guild
        long playerAuth = serverClient.authentication;
        if (!guild.isMember(playerAuth)) {
            ModLogger.warn("Player %d tried to access guild %d bank but is not a member", 
                playerAuth, guildID);
            return;
        }
        
        // Check bank access permission
        if (!guild.hasPermission(playerAuth, PermissionType.BANK_DEPOSIT) && 
            !guild.hasPermission(playerAuth, PermissionType.BANK_WITHDRAW)) {
            ModLogger.warn("Player %d lacks permission to access guild %d bank", 
                playerAuth, guildID);
            return;
        }
        
        // Create packet with guild and tab info
        Packet content = new Packet();
        PacketWriter writer = new PacketWriter(content);
        writer.putNextInt(guildID);
        writer.putNextInt(tabIndex);
        
        // Open the container
        PacketOpenContainer openPacket = new PacketOpenContainer(CONTAINER_ID, content);
        ContainerRegistry.openAndSendContainer(serverClient, openPacket);
        
        ModLogger.debug("Opened guild bank for player %d, guild %d, tab %d", 
            playerAuth, guildID, tabIndex);
    }
}
