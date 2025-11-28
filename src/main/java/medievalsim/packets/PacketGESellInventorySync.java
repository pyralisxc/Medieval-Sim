package medievalsim.packets;

import medievalsim.packets.core.AbstractPayloadPacket;
import medievalsim.util.ModLogger;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Packet to sync sell offer states (10 slots) from server to client.
 * Does not sync inventory - that's handled by container sync.
 * Just syncs offer metadata (enabled state, prices, quantities).
 */
public class PacketGESellInventorySync extends AbstractPayloadPacket {
    public final long ownerAuth;
    public final long[] offerIDs;          // offerID per slot (0 = no offer)
    public final String[] itemStringIDs;   // item string ID per slot
    public final int[] quantityTotal;      // original quantity per slot
    public final boolean[] offerEnabled;   // checkbox state per slot
    public final int[] offerStates;        // state enum ordinal per slot
    public final int[] offerPrices;        // price per item per slot
    public final int[] offerQuantitiesRemaining; // remaining quantity per slot
    
    /**
     * Receiving constructor (client-side).
     */
    public PacketGESellInventorySync(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.ownerAuth = reader.getNextLong();
        
        int slotCount = reader.getNextByteUnsigned();
        this.offerIDs = new long[slotCount];
        this.itemStringIDs = new String[slotCount];
        this.quantityTotal = new int[slotCount];
        this.offerEnabled = new boolean[slotCount];
        this.offerStates = new int[slotCount];
        this.offerPrices = new int[slotCount];
        this.offerQuantitiesRemaining = new int[slotCount];
        
        for (int i = 0; i < slotCount; i++) {
            offerIDs[i] = reader.getNextLong();
            itemStringIDs[i] = reader.getNextString();
            quantityTotal[i] = reader.getNextInt();
            offerEnabled[i] = reader.getNextBoolean();
            offerStates[i] = reader.getNextByteUnsigned();
            offerPrices[i] = reader.getNextInt();
            offerQuantitiesRemaining[i] = reader.getNextInt();
        }
    }
    
    /**
     * Sending constructor (server-side).
     */
    public PacketGESellInventorySync(long ownerAuth, long[] offerIDs, String[] itemStringIDs,
                                      int[] quantityTotal, boolean[] offerEnabled, 
                                      int[] offerStates, int[] offerPrices, 
                                      int[] offerQuantitiesRemaining) {
        this.ownerAuth = ownerAuth;
        this.offerIDs = offerIDs;
        this.itemStringIDs = itemStringIDs;
        this.quantityTotal = quantityTotal;
        this.offerEnabled = offerEnabled;
        this.offerStates = offerStates;
        this.offerPrices = offerPrices;
        this.offerQuantitiesRemaining = offerQuantitiesRemaining;
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(ownerAuth);
        writer.putNextByteUnsigned(offerIDs.length);
        
        for (int i = 0; i < offerIDs.length; i++) {
            writer.putNextLong(offerIDs[i]);
            writer.putNextString(itemStringIDs[i] != null ? itemStringIDs[i] : "");
            writer.putNextInt(quantityTotal[i]);
            writer.putNextBoolean(offerEnabled[i]);
            writer.putNextByteUnsigned(offerStates[i]);
            writer.putNextInt(offerPrices[i]);
            writer.putNextInt(offerQuantitiesRemaining[i]);
        }
    }
    
    @Override
    public void processClient(necesse.engine.network.NetworkPacket packet, Client client) {
        ModLogger.debug("Client: Received sell inventory sync for player auth=%d", ownerAuth);
        
        // Get the active container
        necesse.inventory.container.Container container = client.getContainer();
        if (!(container instanceof medievalsim.grandexchange.ui.GrandExchangeContainer)) {
            ModLogger.warn("Received sell inventory sync but GE container not open");
            return;
        }
        
        medievalsim.grandexchange.ui.GrandExchangeContainer geContainer = 
            (medievalsim.grandexchange.ui.GrandExchangeContainer) container;
        
        // Only update if this packet is for the current player's inventory
        if (geContainer.playerAuth != this.ownerAuth) {
            ModLogger.warn("Sell inventory sync auth mismatch: container=%d, packet=%d",
                geContainer.playerAuth, this.ownerAuth);
            return;
        }
        
        ModLogger.info("[SELL OFFER SYNC] Received sync for %d slots", offerIDs.length);
        
        // Reconstruct GEOffer objects from packet data and update client's playerInventory
        for (int i = 0; i < offerIDs.length; i++) {
            if (offerIDs[i] == 0) {
                // No offer in this slot - clear it
                geContainer.playerInventory.setSlotOffer(i, null);
                ModLogger.debug("[SELL OFFER SYNC] Cleared slot %d (no offer)", i);
            } else {
                // Reconstruct offer from packet data using itemStringID and quantities from packet
                medievalsim.grandexchange.domain.GEOffer.OfferState state = 
                    medievalsim.grandexchange.domain.GEOffer.OfferState.values()[offerStates[i]];
                
                medievalsim.grandexchange.domain.GEOffer offer = 
                    medievalsim.grandexchange.domain.GEOffer.fromPacketData(
                        offerIDs[i], ownerAuth, i, itemStringIDs[i],
                        quantityTotal[i], offerQuantitiesRemaining[i],
                        offerPrices[i], offerEnabled[i], state
                    );
                
                geContainer.playerInventory.setSlotOffer(i, offer);
                ModLogger.info("[SELL OFFER SYNC] Updated slot %d: item=%s, offer ID=%d, state=%s, enabled=%s, quantity=%d/%d",
                    i, itemStringIDs[i], offerIDs[i], state, offerEnabled[i], offerQuantitiesRemaining[i], quantityTotal[i]);
            }
        }
        
        // Trigger UI refresh to display updated data
        geContainer.onSellOffersUpdated();
        
        ModLogger.debug("Client: Reconstructed offers for %d slots", offerIDs.length);
    }
    
    @Override
    public void processServer(necesse.engine.network.NetworkPacket packet, Server server, ServerClient client) {
        ModLogger.warn("PacketGESellInventorySync.processServer called - server-to-client only!");
    }
}
