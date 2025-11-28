package medievalsim.packets;

import medievalsim.grandexchange.domain.CollectionItem;
import medievalsim.grandexchange.domain.GrandExchangeLevelData;
import medievalsim.grandexchange.domain.PlayerGEInventory;
import medievalsim.packets.core.AbstractPayloadPacket;
import medievalsim.util.ModLogger;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;
import necesse.inventory.InventoryItem;

/**
 * Client-to-server packet to collect an item from collection box.
 * Item is transferred to player's open container (GE container's temp inventory).
 */
public class PacketGECollectItem extends AbstractPayloadPacket {
    public final int collectionIndex;
    public final boolean sendToBank;  // If true, send to bank instead of container
    private static final String COLLECTION_REASON = "grandexchange_collect";
    
    /**
     * Receiving constructor (server-side).
     */
    public PacketGECollectItem(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.collectionIndex = reader.getNextShortUnsigned();
        this.sendToBank = reader.getNextBoolean();
    }
    
    /**
     * Sending constructor (client-side).
     */
    public PacketGECollectItem(int collectionIndex, boolean sendToBank) {
        this.collectionIndex = collectionIndex;
        this.sendToBank = sendToBank;
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextShortUnsigned(collectionIndex);
        writer.putNextBoolean(sendToBank);
    }
    
    @Override
    public void processClient(necesse.engine.network.NetworkPacket packet, Client client) {
        ModLogger.warn("PacketGECollectItem.processClient called - client-to-server only!");
    }
    
    @Override
    public void processServer(necesse.engine.network.NetworkPacket packet, Server server, ServerClient client) {
        if (client == null || client.playerMob == null) {
            ModLogger.warn("Received PacketGECollectItem from invalid client");
            return;
        }
        
        Level level = client.playerMob.getLevel();
        GrandExchangeLevelData geData = GrandExchangeLevelData.getGrandExchangeData(level);
        if (geData == null) {
            ModLogger.error("GrandExchangeLevelData not available for collect item");
            return;
        }
        
        long playerAuth = client.authentication;
        PlayerGEInventory playerInventory = geData.getOrCreateInventory(playerAuth);
        if (playerInventory == null) {
            ModLogger.error("Failed to resolve PlayerGEInventory for auth=%d", playerAuth);
            return;
        }
        
        if (sendToBank) {
            int count = geData.collectAllToBank(level, playerAuth);
            ModLogger.info("Player auth=%d collected %d items from collection box to bank", playerAuth, count);
            sendCollectionSync(client, playerAuth, playerInventory);
            return;
        }
        
        CollectionItem removed = geData.collectFromCollectionBox(playerAuth, collectionIndex);
        if (removed == null) {
            ModLogger.warn("Player auth=%d tried to collect invalid index %d", playerAuth, collectionIndex);
            sendCollectionSync(client, playerAuth, playerInventory);
            return;
        }
        
        InventoryItem invItem = removed.toInventoryItem();
        if (invItem == null || invItem.item == null) {
            playerInventory.insertIntoCollectionBox(collectionIndex, removed);
            ModLogger.warn("Unknown collection item %s when collecting for auth=%d; restored entry",
                removed.getItemStringID(), playerAuth);
            sendCollectionSync(client, playerAuth, playerInventory);
            return;
        }
        
        boolean added = client.playerMob.getInv().main.addItem(level, client.playerMob, invItem, COLLECTION_REASON, null);
        if (added) {
            ModLogger.info("Player auth=%d collected item from collection box: %d x %s",
                playerAuth, removed.getQuantity(), removed.getItemStringID());
        } else {
            playerInventory.insertIntoCollectionBox(collectionIndex, removed);
            ModLogger.warn("Player auth=%d inventory full, cannot collect %s x%d", playerAuth,
                removed.getItemStringID(), removed.getQuantity());
        }
        
        sendCollectionSync(client, playerAuth, playerInventory);
    }
    
    private void sendCollectionSync(ServerClient client, long playerAuth, PlayerGEInventory playerInventory) {
        if (client == null || playerInventory == null) {
            return;
        }
        client.sendPacket(new PacketGECollectionSync(playerAuth, playerInventory.getCollectionBox()));
    }
}
