package medievalsim.packets;

import medievalsim.grandexchange.application.GrandExchangeContext;
import medievalsim.grandexchange.application.GrandExchangeLedger;
import medievalsim.grandexchange.net.SellActionResultCode;
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
        GrandExchangeContext context = GrandExchangeContext.resolve(level);
        if (context == null) {
            ModLogger.error("GrandExchangeLevelData not available for enable/disable offer");
            return;
        }
        GrandExchangeLedger ledger = context.getLedger();
        
        long playerAuth = client.authentication;
        SellActionResultCode result = SellActionResultCode.UNKNOWN_FAILURE;
        
        if (enable) {
            // Enable offer (DRAFT → ACTIVE, add to market)
            result = ledger.enableSellOffer(level, playerAuth, slotIndex);
            ModLogger.info("Player auth=%d enabled sell offer in slot %d: %s", 
                playerAuth, slotIndex, result == SellActionResultCode.SUCCESS ? "SUCCESS" : result);
        } else {
            // Disable offer (ACTIVE → DRAFT, remove from market)
            result = ledger.disableSellOffer(level, playerAuth, slotIndex);
            ModLogger.info("Player auth=%d disabled sell offer in slot %d: %s", 
                playerAuth, slotIndex, result == SellActionResultCode.SUCCESS ? "SUCCESS" : result);
        }
        
        // Send sync packet back to client to update UI
        context.getLevelData().sendSellInventorySyncToClient(client, playerAuth);
        
        // Send error notification to client if operation failed
        if (result != SellActionResultCode.SUCCESS) {
            String errorKey = enable ? "enablefailed" : "disablefailed";
            client.sendPacket(new medievalsim.packets.PacketGEError(
                playerAuth, 
                medievalsim.grandexchange.net.GEFeedbackChannel.SELL, 
                errorKey
            ));
            ModLogger.debug("Failed to %s offer in slot %d: %s - error notification sent",
                enable ? "enable" : "disable", slotIndex, result);
        }
    }
}
