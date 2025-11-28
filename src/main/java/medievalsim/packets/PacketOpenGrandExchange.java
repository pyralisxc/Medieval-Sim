package medievalsim.packets;

import medievalsim.config.ModConfig;
import medievalsim.grandexchange.domain.GrandExchangeLevelData;
import medievalsim.registries.MedievalSimContainers;
import medievalsim.util.ModLogger;
import necesse.engine.Settings;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.packet.PacketOpenContainer;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ContainerRegistry;

/**
 * Packet to open the Grand Exchange UI.
 * Sent from client to server when player wants to access the GE.
 */
public class PacketOpenGrandExchange extends Packet {
    
    /**
     * Receiving constructor (server-side).
     */
    public PacketOpenGrandExchange(byte[] data) {
        super(data);
    }
    
    /**
     * Sending constructor (client-side).
     */
    public PacketOpenGrandExchange() {
        // No data needed
    }
    
    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        // Check if GE is enabled
        if (!ModConfig.GrandExchange.enabled) {
            ModLogger.warn("Player %s tried to open GE but it's disabled", client.getName());
            return;
        }
        
        // Get GE data for the level
        GrandExchangeLevelData geData = GrandExchangeLevelData.getGrandExchangeData(client.getLevel());
        if (geData == null) {
            ModLogger.error("Failed to get GE data for level");
            return;
        }
        
        // Create packet content with player auth, balance, and privilege flag
        Packet content = new Packet();
        PacketWriter writer = new PacketWriter(content);
        writer.putNextLong(client.authentication);
        
        // Add player's bank coin balance to opening packet
        long coinBalance = 0;
        try {
            medievalsim.banking.domain.BankingLevelData bankingData = medievalsim.banking.domain.BankingLevelData.getBankingData(client.getLevel());
            if (bankingData != null) {
                medievalsim.banking.domain.PlayerBank bank = bankingData.getBank(client.authentication);
                if (bank != null) {
                    coinBalance = bank.getCoins();
                    ModLogger.info("[BANK SYNC] Fetched bank balance for GE open: auth=%d, balance=%d", 
                        client.authentication, coinBalance);
                } else {
                    ModLogger.warn("[BANK SYNC] No bank found for player auth=%d", client.authentication);
                }
            } else {
                ModLogger.warn("[BANK SYNC] No banking data found in level");
            }
        } catch (Exception e) {
            ModLogger.error("[BANK SYNC] Failed to get bank balance for GE: %s", e.getMessage());
        }
        writer.putNextLong(coinBalance);

        boolean isServerOwner = Settings.serverOwnerAuth != -1L
            && client.authentication == Settings.serverOwnerAuth;
        writer.putNextBoolean(isServerOwner);
        writer.putNextInt(ModConfig.GrandExchange.geInventorySlots);
        writer.putNextInt(ModConfig.GrandExchange.buyOrderSlots);

        ModLogger.info("[BANK SYNC] Writing coin balance %d (owner=%s) to GE open packet", 
            coinBalance, isServerOwner);

        // Open GE container for the client
        PacketOpenContainer openPacket = new PacketOpenContainer(
            MedievalSimContainers.GRAND_EXCHANGE_CONTAINER,
            content
        );
        ContainerRegistry.openAndSendContainer(client, openPacket);

        ModLogger.debug("Opened Grand Exchange for player %s (auth=%d)",
            client.getName(), client.authentication);
    }
    
    @Override
    public void processClient(NetworkPacket packet, Client client) {
        // Not used - GE is opened server-side
    }
}

