package medievalsim.packets;

import medievalsim.banking.ui.BankContainer;
import medievalsim.packets.core.AbstractPayloadPacket;
import medievalsim.util.ModLogger;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.InventoryItem;

/**
 * Packet to sync a single bank inventory slot from server to client.
 * Similar to PacketOEInventoryUpdate but for bank inventories.
 */
public class PacketBankInventoryUpdate extends AbstractPayloadPacket {
    public final long ownerAuth;
    public final int inventorySlot;
    public final Packet itemContent;

    /**
     * Receiving constructor (client-side).
     */
    public PacketBankInventoryUpdate(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.ownerAuth = reader.getNextLong();
        this.inventorySlot = reader.getNextShortUnsigned();
        this.itemContent = reader.getNextContentPacket();
    }

    /**
     * Sending constructor (server-side).
     */
    public PacketBankInventoryUpdate(long ownerAuth, int inventorySlot, InventoryItem item) {
        this.ownerAuth = ownerAuth;
        this.inventorySlot = inventorySlot;
        this.itemContent = InventoryItem.getContentPacket(item);
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(ownerAuth);
        writer.putNextShortUnsigned(inventorySlot);
        writer.putNextContentPacket(this.itemContent);
    }

    @Override
    public void processClient(necesse.engine.network.NetworkPacket packet, Client client) {
        // Update the bank inventory slot on the client side
        necesse.inventory.container.Container container = client.getContainer();
        if (container instanceof BankContainer) {
            BankContainer bankContainer = (BankContainer) container;

            // Only update if this packet is for the current player's bank
            if (bankContainer.ownerAuth == this.ownerAuth) {
                InventoryItem item = InventoryItem.fromContentPacket(this.itemContent);
                bankContainer.getBank().getInventory().setItem(this.inventorySlot, item);
                ModLogger.debug("Client: Updated bank slot %d for player auth=%d",
                    this.inventorySlot, this.ownerAuth);
            }
        }
    }

    @Override
    public void processServer(necesse.engine.network.NetworkPacket packet, Server server, ServerClient client) {
        // This packet is only sent from server to client, so this should never be called
        ModLogger.warn("PacketBankInventoryUpdate.processServer called - this should not happen!");
    }
}

