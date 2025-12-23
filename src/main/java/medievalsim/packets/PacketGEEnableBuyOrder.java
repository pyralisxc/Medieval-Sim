package medievalsim.packets;

import medievalsim.grandexchange.application.GrandExchangeContext;
import medievalsim.grandexchange.application.GrandExchangeLedger;
import medievalsim.grandexchange.domain.PlayerGEInventory;
import medievalsim.grandexchange.services.RateLimitedAction;
import medievalsim.grandexchange.services.RateLimitStatus;
import medievalsim.packets.core.AbstractPayloadPacket;
import medievalsim.util.ModLogger;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;

/**
 * Client-to-server packet to enable/disable a buy order (checkbox toggle).
 */
public class PacketGEEnableBuyOrder extends AbstractPayloadPacket {
    public final int slotIndex;
    public final boolean enable;  // true = enable (escrow coins), false = disable (refund coins)
    
    /**
     * Receiving constructor (server-side).
     */
    public PacketGEEnableBuyOrder(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.slotIndex = reader.getNextByteUnsigned();
        this.enable = reader.getNextBoolean();
    }
    
    /**
     * Sending constructor (client-side).
     */
    public PacketGEEnableBuyOrder(int slotIndex, boolean enable) {
        this.slotIndex = slotIndex;
        this.enable = enable;
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextByteUnsigned(slotIndex);
        writer.putNextBoolean(enable);
    }
    
    @Override
    public void processClient(necesse.engine.network.NetworkPacket packet, Client client) {
        ModLogger.warn("PacketGEEnableBuyOrder.processClient called - client-to-server only!");
    }
    
    @Override
    public void processServer(necesse.engine.network.NetworkPacket packet, Server server, ServerClient client) {
        if (client == null || client.playerMob == null) {
            ModLogger.warn("Received PacketGEEnableBuyOrder from invalid client");
            return;
        }
        
        Level level = client.playerMob.getLevel();
        GrandExchangeContext context = GrandExchangeContext.resolve(level);
        if (context == null) {
            ModLogger.error("GrandExchangeLevelData not available for buy order enable/disable");
            return;
        }
        GrandExchangeLedger ledger = context.getLedger();
        
        long playerAuth = client.authentication;
        
        if (enable) {
            // Enable buy order (DRAFT → ACTIVE, escrow coins from bank)
            boolean success = ledger.enableBuyOrder(level, playerAuth, slotIndex);
            if (success) {
                ModLogger.info("Player auth=%d enabled buy order in slot %d", playerAuth, slotIndex);
            } else {
                ModLogger.warn("Player auth=%d failed to enable buy order in slot %d (insufficient coins?)", 
                    playerAuth, slotIndex);
            }
        } else {
            // Disable buy order (ACTIVE → DRAFT, refund coins to bank)
            boolean success = ledger.disableBuyOrder(level, playerAuth, slotIndex);
            ModLogger.info("Player auth=%d disabled buy order in slot %d: %s", 
                playerAuth, slotIndex, success ? "SUCCESS" : "FAILED");
        }
        
        // Send sync packet back to client to update UI
        try {
            PlayerGEInventory playerInventory = context.getOrCreateInventory(playerAuth);
            RateLimitStatus creationStatus = ledger.getRateLimitStatus(RateLimitedAction.BUY_CREATE, playerAuth);
            RateLimitStatus toggleStatus = ledger.getRateLimitStatus(RateLimitedAction.BUY_TOGGLE, playerAuth);
            PacketGEBuyOrderSync syncPacket = new PacketGEBuyOrderSync(
                playerAuth,
                playerInventory.getBuyOrders(),
                ledger.getAnalyticsService(),
                playerInventory,
                creationStatus,
                toggleStatus);
            client.sendPacket(syncPacket);
            ModLogger.debug("[SERVER SEND] PacketGEBuyOrderSync sent to auth=%d", playerAuth);
        } catch (Exception e) {
            ModLogger.error("Failed to send buy order sync to client auth=%d: %s", playerAuth, e.getMessage());
        }
    }
}
