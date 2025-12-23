package medievalsim.packets;

import java.util.ArrayList;
import java.util.List;

import medievalsim.grandexchange.domain.CollectionItem;
import medievalsim.grandexchange.model.snapshot.CollectionEntrySnapshot;
import medievalsim.grandexchange.model.snapshot.CollectionPageSnapshot;
import medievalsim.grandexchange.ui.GrandExchangeContainer;
import medievalsim.grandexchange.ui.viewmodel.GrandExchangeViewModel;
import medievalsim.grandexchange.util.CollectionPaginator;
import medievalsim.packets.core.AbstractPayloadPacket;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Packet to sync collection box items from server to client.
 * Collection box can have unlimited items (list-based, not inventory slots).
 */
public class PacketGECollectionSync extends AbstractPayloadPacket {
    public final long ownerAuth;
    private final CollectionPageSnapshot snapshot;

    public CollectionPageSnapshot getSnapshot() {
        return snapshot;
    }
    
    /**
     * Receiving constructor (client-side).
     */
    public PacketGECollectionSync(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.ownerAuth = reader.getNextLong();
        int pageIndex = reader.getNextInt();
        int totalPages = reader.getNextInt();
        int totalItems = reader.getNextInt();
        int pageSize = reader.getNextInt();
        boolean depositToBankPreferred = reader.getNextBoolean();
        boolean autoSendToBank = reader.getNextBoolean();
        boolean notifyPartialSales = reader.getNextBoolean();
        boolean playSoundOnSale = reader.getNextBoolean();

        int itemCount = reader.getNextShortUnsigned();
        List<CollectionEntrySnapshot> entries = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            int globalIndex = reader.getNextInt();
            String itemStringID = reader.getNextString();
            int quantity = reader.getNextInt();
            long timestamp = reader.getNextLong();
            String source = reader.getNextString();
            entries.add(new CollectionEntrySnapshot(globalIndex, itemStringID, quantity, timestamp, source));
        }
        this.snapshot = new CollectionPageSnapshot(
            ownerAuth,
            pageIndex,
            totalPages,
            totalItems,
            pageSize,
            depositToBankPreferred,
            autoSendToBank,
            notifyPartialSales,
            playSoundOnSale,
            entries
        );
    }
    
    /**
     * Sending constructor (server-side).
     */
    public PacketGECollectionSync(long ownerAuth,
                                  CollectionPaginator.Page page,
                                  boolean depositPreference,
                                  boolean autoSendToBank,
                                  boolean notifyPartialSales,
                                  boolean playSoundOnSale) {
        this.ownerAuth = ownerAuth;
        List<CollectionEntrySnapshot> entries = new ArrayList<>(page.getEntries().size());
        for (CollectionPaginator.Entry entry : page.getEntries()) {
            CollectionItem item = entry.getItem();
            entries.add(new CollectionEntrySnapshot(
                entry.getGlobalIndex(),
                item.getItemStringID(),
                item.getQuantity(),
                item.getTimestamp(),
                item.getSource()
            ));
        }
        this.snapshot = new CollectionPageSnapshot(
            ownerAuth,
            page.getPageIndex(),
            page.getTotalPages(),
            page.getTotalItems(),
            page.getPageSize(),
            depositPreference,
            autoSendToBank,
            notifyPartialSales,
            playSoundOnSale,
            entries
        );

        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(ownerAuth);
        writer.putNextInt(snapshot.pageIndex());
        writer.putNextInt(snapshot.totalPages());
        writer.putNextInt(snapshot.totalItems());
        writer.putNextInt(snapshot.pageSize());
        writer.putNextBoolean(snapshot.depositToBankPreferred());
        writer.putNextBoolean(snapshot.autoSendToBank());
        writer.putNextBoolean(snapshot.notifyPartialSales());
        writer.putNextBoolean(snapshot.playSoundOnSale());
        writer.putNextShortUnsigned(entries.size());

        for (CollectionEntrySnapshot entry : entries) {
            writer.putNextInt(entry.globalIndex());
            writer.putNextString(entry.itemStringID());
            writer.putNextInt(entry.quantity());
            writer.putNextLong(entry.timestamp());
            writer.putNextString(entry.source());
        }
    }
    
    @Override
    public void processClient(NetworkPacket packet, Client client) {
        ModLogger.debug("Client: Received collection box sync for player auth=%d (%d items)",
            ownerAuth, snapshot.entries().size());

        necesse.inventory.container.Container openContainer = client.getContainer();
        if (!(openContainer instanceof GrandExchangeContainer geContainer)) {
            ModLogger.warn("Received collection sync but GE container not open");
            return;
        }

        if (geContainer.playerAuth != this.ownerAuth) {
            ModLogger.warn("Collection sync auth mismatch: container=%d, packet=%d",
                geContainer.playerAuth, this.ownerAuth);
            return;
        }

        try {
            GrandExchangeViewModel viewModel = geContainer.getViewModel();
            viewModel.applyCollectionSnapshot(snapshot);
            // Maintain legacy container metadata for actions still referencing these fields.
            geContainer.collectionPageIndex = snapshot.pageIndex();
            geContainer.collectionTotalPages = snapshot.totalPages();
            geContainer.collectionTotalItems = snapshot.totalItems();
            geContainer.collectionPageSize = snapshot.pageSize();
            geContainer.collectionDepositToBankPreferred = snapshot.depositToBankPreferred();
            int[] mapping = new int[snapshot.entries().size()];
            for (int i = 0; i < snapshot.entries().size(); i++) {
                mapping[i] = snapshot.entries().get(i).globalIndex();
            }
            geContainer.collectionPageGlobalIndices = mapping;
            geContainer.onCollectionBoxUpdated();
        } catch (IllegalStateException ex) {
            ModLogger.error("Failed to apply collection snapshot on client: %s", ex.getMessage());
        }
    }
    
    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        ModLogger.warn("PacketGECollectionSync.processServer called - server-to-client only!");
    }
}
