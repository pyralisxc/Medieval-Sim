package medievalsim.packets;

import medievalsim.grandexchange.domain.BuyOrder;
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
 * Client-to-server packet to create a new buy order.
 */
public class PacketGECreateBuyOrder extends AbstractPayloadPacket {
    public final int slotIndex;
    public final String itemStringID;
    public final int quantity;
    public final int pricePerItem;
    public final int durationDays;
    
    /**
     * Receiving constructor (server-side).
     */
    public PacketGECreateBuyOrder(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.slotIndex = reader.getNextByteUnsigned();
        this.itemStringID = reader.getNextString();
        this.quantity = reader.getNextInt();
        this.pricePerItem = reader.getNextInt();
        this.durationDays = reader.getNextInt();
    }
    
    /**
     * Sending constructor (client-side).
     */
    public PacketGECreateBuyOrder(int slotIndex, String itemStringID, int quantity, 
                                   int pricePerItem, int durationDays) {
        this.slotIndex = slotIndex;
        this.itemStringID = itemStringID;
        this.quantity = quantity;
        this.pricePerItem = pricePerItem;
        this.durationDays = durationDays;
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextByteUnsigned(slotIndex);
        writer.putNextString(itemStringID);
        writer.putNextInt(quantity);
        writer.putNextInt(pricePerItem);
        writer.putNextInt(durationDays);
    }
    
    @Override
    public void processClient(necesse.engine.network.NetworkPacket packet, Client client) {
        ModLogger.warn("PacketGECreateBuyOrder.processClient called - client-to-server only!");
    }
    
    @Override
    public void processServer(necesse.engine.network.NetworkPacket packet, Server server, ServerClient client) {
        if (client == null || client.playerMob == null) {
            ModLogger.warn("Received PacketGECreateBuyOrder from invalid client");
            return;
        }
        
        Level level = client.playerMob.getLevel();
        GrandExchangeLevelData geData = GrandExchangeLevelData.getGrandExchangeData(level);
        if (geData == null) {
            ModLogger.error("GrandExchangeLevelData not available for create buy order");
            return;
        }
        
        long playerAuth = client.authentication;
        
        // Create buy order (DRAFT state)
        BuyOrder order = geData.createBuyOrder(playerAuth, slotIndex, itemStringID, 
            quantity, pricePerItem, durationDays);
        
        if (order != null) {
            ModLogger.info("Player auth=%d created buy order in slot %d: %d x %s @ %d coins/ea",
                playerAuth, slotIndex, quantity, itemStringID, pricePerItem);
            
            // Send sync packet back to client to update UI
            var playerInventory = geData.getOrCreateInventory(playerAuth);
            PacketGEBuyOrderSync syncPacket = new PacketGEBuyOrderSync(
                playerAuth, playerInventory.getBuyOrders());
            ModLogger.info("[SERVER SEND] About to send PacketGEBuyOrderSync to client auth=%d", playerAuth);
            client.sendPacket(syncPacket);
            ModLogger.info("[SERVER SEND] PacketGEBuyOrderSync sent successfully to client auth=%d", playerAuth);
        } else {
            ModLogger.warn("Player auth=%d failed to create buy order in slot %d", 
                playerAuth, slotIndex);
        }
    }
}
