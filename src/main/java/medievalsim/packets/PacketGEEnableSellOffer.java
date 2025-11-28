package medievalsim.packets;

import medievalsim.grandexchange.domain.GrandExchangeLevelData;
import medievalsim.packets.core.AbstractPayloadPacket;
import medievalsim.util.ModLogger;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;

/**
 * Client-to-server packet to enable/disable a sell offer (checkbox toggle).
 */
public class PacketGEEnableSellOffer extends AbstractPayloadPacket {
    public final int slotIndex;
    public final boolean enable;  // true = enable (DRAFT → ACTIVE), false = disable (ACTIVE → DRAFT)
    
    /**
     * Receiving constructor (server-side).
     */
    public PacketGEEnableSellOffer(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.slotIndex = reader.getNextByteUnsigned();
        this.enable = reader.getNextBoolean();
    }
    
    /**
     * Sending constructor (client-side).
     */
    public PacketGEEnableSellOffer(int slotIndex, boolean enable) {
        this.slotIndex = slotIndex;
        this.enable = enable;
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextByteUnsigned(slotIndex);
        writer.putNextBoolean(enable);
    }
    
    @Override
    public void processClient(necesse.engine.network.NetworkPacket packet, Client client) {
        ModLogger.warn("PacketGEEnableSellOffer.processClient called - client-to-server only!");
    }
    
    @Override
    public void processServer(necesse.engine.network.NetworkPacket packet, Server server, ServerClient client) {
        if (client == null || client.playerMob == null) {
            ModLogger.warn("Received PacketGEEnableSellOffer from invalid client");
            return;
        }
        
        Level level = client.playerMob.getLevel();
        GrandExchangeLevelData geData = GrandExchangeLevelData.getGrandExchangeData(level);
        if (geData == null) {
            ModLogger.error("GrandExchangeLevelData not available for enable/disable offer");
            return;
        }
        
        long playerAuth = client.authentication;
        boolean success = false;
        
        if (enable) {
            // Enable offer (DRAFT → ACTIVE, add to market)
            success = geData.enableSellOffer(level, playerAuth, slotIndex);
            ModLogger.info("Player auth=%d enabled sell offer in slot %d: %s", 
                playerAuth, slotIndex, success ? "SUCCESS" : "FAILED");
        } else {
            // Disable offer (ACTIVE → DRAFT, remove from market)
            success = geData.disableSellOffer(level, playerAuth, slotIndex);
            ModLogger.info("Player auth=%d disabled sell offer in slot %d: %s", 
                playerAuth, slotIndex, success ? "SUCCESS" : "FAILED");
        }
        
        // Send sync packet back to client to update UI
        geData.sendSellInventorySyncToClient(client, playerAuth);
        
        // If failed, optionally send error notification (future enhancement)
        if (!success) {
            // TODO: Send error packet to show notification on client
            ModLogger.debug("Failed to %s offer in slot %d, sync sent to show current state",
                enable ? "enable" : "disable", slotIndex);
        }
    }
}
