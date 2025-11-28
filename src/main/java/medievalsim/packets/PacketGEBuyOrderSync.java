package medievalsim.packets;

import medievalsim.grandexchange.domain.BuyOrder;
import medievalsim.packets.core.AbstractPayloadPacket;
import medievalsim.util.ModLogger;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Packet to sync buy orders (3 slots) from server to client.
 * Includes buy order configuration and state for each slot.
 */
public class PacketGEBuyOrderSync extends AbstractPayloadPacket {
    public final long ownerAuth;
    
    // Per-slot data (null itemStringID = empty slot)
    public final long[] orderIDs;          // orderID per slot (0 = no order)
    public final String[] itemStringIDs;   // item being purchased (null = empty slot)
    public final int[] quantitiesTotal;
    public final int[] quantitiesRemaining;
    public final int[] pricesPerItem;
    public final boolean[] enabled;        // checkbox state
    public final int[] states;             // BuyOrderState enum ordinal
    public final int[] durationDays;
    
    /**
     * Receiving constructor (client-side).
     */
    public PacketGEBuyOrderSync(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.ownerAuth = reader.getNextLong();
        
        int slotCount = reader.getNextByteUnsigned();
        this.orderIDs = new long[slotCount];
        this.itemStringIDs = new String[slotCount];
        this.quantitiesTotal = new int[slotCount];
        this.quantitiesRemaining = new int[slotCount];
        this.pricesPerItem = new int[slotCount];
        this.enabled = new boolean[slotCount];
        this.states = new int[slotCount];
        this.durationDays = new int[slotCount];
        
        for (int i = 0; i < slotCount; i++) {
            orderIDs[i] = reader.getNextLong();
            boolean hasOrder = reader.getNextBoolean();
            if (hasOrder) {
                itemStringIDs[i] = reader.getNextString();
                quantitiesTotal[i] = reader.getNextInt();
                quantitiesRemaining[i] = reader.getNextInt();
                pricesPerItem[i] = reader.getNextInt();
                enabled[i] = reader.getNextBoolean();
                states[i] = reader.getNextByteUnsigned();
                durationDays[i] = reader.getNextInt();
            } else {
                itemStringIDs[i] = null;
            }
        }
    }
    
    /**
     * Sending constructor (server-side).
     */
    public PacketGEBuyOrderSync(long ownerAuth, BuyOrder[] buyOrders) {
        ModLogger.debug("PacketGEBuyOrderSync constructor: Creating packet for auth=%d with %d slots", 
            ownerAuth, buyOrders.length);
        this.ownerAuth = ownerAuth;
        
        int slotCount = buyOrders.length;
        this.orderIDs = new long[slotCount];
        this.itemStringIDs = new String[slotCount];
        this.quantitiesTotal = new int[slotCount];
        this.quantitiesRemaining = new int[slotCount];
        this.pricesPerItem = new int[slotCount];
        this.enabled = new boolean[slotCount];
        this.states = new int[slotCount];
        this.durationDays = new int[slotCount];
        
        for (int i = 0; i < slotCount; i++) {
            BuyOrder order = buyOrders[i];
            if (order != null) {
                orderIDs[i] = order.getOrderID();
                itemStringIDs[i] = order.getItemStringID();
                quantitiesTotal[i] = order.getQuantityTotal();
                quantitiesRemaining[i] = order.getQuantityRemaining();
                pricesPerItem[i] = order.getPricePerItem();
                enabled[i] = order.isEnabled();
                states[i] = order.getState().ordinal();
                durationDays[i] = order.getDurationDays();
            } else {
                orderIDs[i] = 0L;
                itemStringIDs[i] = null;
            }
        }
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(ownerAuth);
        writer.putNextByteUnsigned(slotCount);
        
        for (int i = 0; i < slotCount; i++) {
            writer.putNextLong(orderIDs[i]);
            boolean hasOrder = itemStringIDs[i] != null;
            writer.putNextBoolean(hasOrder);
            if (hasOrder) {
                writer.putNextString(itemStringIDs[i]);
                writer.putNextInt(quantitiesTotal[i]);
                writer.putNextInt(quantitiesRemaining[i]);
                writer.putNextInt(pricesPerItem[i]);
                writer.putNextBoolean(enabled[i]);
                writer.putNextByteUnsigned(states[i]);
                writer.putNextInt(durationDays[i]);
            }
        }
    }
    
    @Override
    public void processClient(necesse.engine.network.NetworkPacket packet, Client client) {
        ModLogger.info("[PACKET TRACE] processClient START: PacketGEBuyOrderSync for auth=%d", ownerAuth);
        ModLogger.debug("Client: Received buy order sync for player auth=%d", ownerAuth);
        
        // Get the active container
        necesse.inventory.container.Container container = client.getContainer();
        if (!(container instanceof medievalsim.grandexchange.ui.GrandExchangeContainer)) {
            ModLogger.warn("Received buy order sync but GE container not open");
            return;
        }
        
        medievalsim.grandexchange.ui.GrandExchangeContainer geContainer = 
            (medievalsim.grandexchange.ui.GrandExchangeContainer) container;
        
        // Only update if this packet is for the current player's inventory
        if (geContainer.playerAuth != this.ownerAuth) {
            ModLogger.warn("Buy order sync auth mismatch: container=%d, packet=%d",
                geContainer.playerAuth, this.ownerAuth);
            return;
        }
        
        // Reconstruct BuyOrder objects from packet data
        for (int i = 0; i < orderIDs.length; i++) {
            if (itemStringIDs[i] == null || orderIDs[i] == 0) {
                // No order in this slot - clear it
                geContainer.playerInventory.setBuyOrder(i, null);
                ModLogger.debug("Cleared buy order slot %d", i);
            } else {
                // Reconstruct BuyOrder from packet data
                BuyOrder.BuyOrderState state = BuyOrder.BuyOrderState.values()[states[i]];
                BuyOrder order = BuyOrder.fromPacketData(
                    orderIDs[i],
                    ownerAuth,
                    i, // slotIndex
                    itemStringIDs[i],
                    quantitiesTotal[i],
                    quantitiesRemaining[i],
                    pricesPerItem[i],
                    enabled[i],
                    state,
                    durationDays[i]
                );
                geContainer.playerInventory.setBuyOrder(i, order);
                ModLogger.info("[CLIENT SYNC] Updated buy order slot %d: %s x%d @ %d coins (state=%s)", 
                    i, itemStringIDs[i], quantitiesTotal[i], pricesPerItem[i], state);
            }
        }
        
        // Trigger UI refresh
        ModLogger.info("[PACKET TRACE] About to call onBuyOrdersUpdated() callback");
        geContainer.onBuyOrdersUpdated();
        ModLogger.info("[PACKET TRACE] Callback completed");
        
        ModLogger.debug("Client: Updated %d buy order slots", orderIDs.length);
    }
    
    @Override
    public void processServer(necesse.engine.network.NetworkPacket packet, Server server, ServerClient client) {
        ModLogger.warn("PacketGEBuyOrderSync.processServer called - server-to-client only!");
    }
}
