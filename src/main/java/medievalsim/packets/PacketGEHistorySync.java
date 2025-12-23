package medievalsim.packets;

import java.util.ArrayList;
import java.util.List;

import medievalsim.grandexchange.domain.PlayerGEInventory;
import medievalsim.grandexchange.domain.SaleNotification;
import medievalsim.grandexchange.model.snapshot.HistoryEntrySnapshot;
import medievalsim.grandexchange.model.snapshot.HistoryTabSnapshot;
import medievalsim.grandexchange.ui.GrandExchangeContainer;
import medievalsim.grandexchange.ui.viewmodel.GrandExchangeViewModel;
import medievalsim.packets.core.AbstractPayloadPacket;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.Container;

/**
 * Server -> client packet delivering Grand Exchange sale history snapshots.
 */
public class PacketGEHistorySync extends AbstractPayloadPacket {
    private final long ownerAuth;
    private final HistoryTabSnapshot snapshot;

    public PacketGEHistorySync(long ownerAuth, PlayerGEInventory inventory) {
        this.ownerAuth = ownerAuth;
        this.snapshot = buildSnapshot(ownerAuth, inventory);

        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(ownerAuth);
        writer.putNextInt(snapshot.totalItemsPurchased());
        writer.putNextInt(snapshot.totalItemsSold());
        writer.putNextInt(snapshot.totalSellOffersCreated());
        writer.putNextInt(snapshot.totalSellOffersCompleted());
        writer.putNextInt(snapshot.totalBuyOrdersCreated());
        writer.putNextInt(snapshot.totalBuyOrdersCompleted());
        writer.putNextLong(snapshot.lastHistoryViewedTimestamp());

        List<HistoryEntrySnapshot> entries = snapshot.entries();
        writer.putNextShortUnsigned(entries.size());
        for (HistoryEntrySnapshot entry : entries) {
            writer.putNextString(entry.itemStringID());
            writer.putNextInt(entry.quantityTraded());
            writer.putNextInt(entry.pricePerItem());
            writer.putNextInt(entry.totalCoins());
            writer.putNextBoolean(entry.partial());
            writer.putNextBoolean(entry.counterpartyName() != null);
            if (entry.counterpartyName() != null) {
                writer.putNextString(entry.counterpartyName());
            }
            writer.putNextLong(entry.timestamp());
            writer.putNextBoolean(entry.isSale());
        }
    }

    public PacketGEHistorySync(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.ownerAuth = reader.getNextLong();
        int totalItemsPurchased = reader.getNextInt();
        int totalItemsSold = reader.getNextInt();
        int totalSellOffersCreated = reader.getNextInt();
        int totalSellOffersCompleted = reader.getNextInt();
        int totalBuyOrdersCreated = reader.getNextInt();
        int totalBuyOrdersCompleted = reader.getNextInt();
        long lastHistoryViewedTimestamp = reader.getNextLong();

        int entryCount = reader.getNextShortUnsigned();
        List<HistoryEntrySnapshot> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            String itemStringID = reader.getNextString();
            int quantityTraded = reader.getNextInt();
            int pricePerItem = reader.getNextInt();
            int totalCoins = reader.getNextInt();
            boolean partial = reader.getNextBoolean();
            String counterpartyName = null;
            if (reader.getNextBoolean()) {
                counterpartyName = reader.getNextString();
            }
            long timestamp = reader.getNextLong();
            boolean isSale = reader.getNextBoolean();
            entries.add(new HistoryEntrySnapshot(
                itemStringID,
                quantityTraded,
                pricePerItem,
                totalCoins,
                partial,
                counterpartyName,
                timestamp,
                isSale
            ));
        }

        this.snapshot = new HistoryTabSnapshot(
            ownerAuth,
            entries,
            totalItemsPurchased,
            totalItemsSold,
            totalSellOffersCreated,
            totalSellOffersCompleted,
            totalBuyOrdersCreated,
            totalBuyOrdersCompleted,
            lastHistoryViewedTimestamp
        );
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        ModLogger.warn("PacketGEHistorySync received on server - this packet is server -> client only");
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        Container active = client.getContainer();
        if (!(active instanceof GrandExchangeContainer geContainer)) {
            ModLogger.warn("Received GE history sync but player is not viewing the Grand Exchange");
            return;
        }
        if (geContainer.playerAuth != ownerAuth) {
            ModLogger.warn("GE history sync auth mismatch: container=%d packet=%d", geContainer.playerAuth, ownerAuth);
            return;
        }
        try {
            GrandExchangeViewModel viewModel = geContainer.getViewModel();
            viewModel.applyHistorySnapshot(snapshot);
            geContainer.onHistoryUpdated();
        } catch (IllegalStateException ex) {
            ModLogger.error("Failed to apply history snapshot: %s", ex.getMessage());
        }
    }

    private static HistoryTabSnapshot buildSnapshot(long ownerAuth, PlayerGEInventory inventory) {
        List<HistoryEntrySnapshot> entries = new ArrayList<>();
        if (inventory != null) {
            for (SaleNotification notification : inventory.getSaleHistory()) {
                entries.add(new HistoryEntrySnapshot(
                    notification.getItemStringID(),
                    notification.getQuantityTraded(),
                    notification.getPricePerItem(),
                    notification.getTotalCoins(),
                    notification.isPartial(),
                    notification.getCounterpartyName(),
                    notification.getTimestamp(),
                    notification.isSale()
                ));
            }
        }
        return new HistoryTabSnapshot(
            ownerAuth,
            entries,
            inventory != null ? inventory.getTotalItemsPurchased() : 0,
            inventory != null ? inventory.getTotalItemsSold() : 0,
            inventory != null ? inventory.getTotalSellOffersCreated() : 0,
            inventory != null ? inventory.getTotalSellOffersCompleted() : 0,
            inventory != null ? inventory.getTotalBuyOrdersCreated() : 0,
            inventory != null ? inventory.getTotalBuyOrdersCompleted() : 0,
            inventory != null ? inventory.getLastHistoryViewedTimestamp() : 0L
        );
    }
}
