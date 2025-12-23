package medievalsim.packets;

import java.util.ArrayList;
import java.util.List;

import medievalsim.grandexchange.model.snapshot.HistoryDeltaPayload;
import medievalsim.grandexchange.model.snapshot.HistoryEntrySnapshot;
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
 * Server -> client delta packet for GE history updates. Keeps badges in sync
 * without shipping the full snapshot every time a sale completes.
 */
public class PacketGEHistoryDelta extends AbstractPayloadPacket {
    private final HistoryDeltaPayload payload;

    public PacketGEHistoryDelta(HistoryDeltaPayload payload) {
        this.payload = payload;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(payload.ownerAuth());
        writer.putNextInt(payload.totalItemsPurchased());
        writer.putNextInt(payload.totalItemsSold());
        writer.putNextInt(payload.totalSellOffersCreated());
        writer.putNextInt(payload.totalSellOffersCompleted());
        writer.putNextInt(payload.totalBuyOrdersCreated());
        writer.putNextInt(payload.totalBuyOrdersCompleted());
        writer.putNextLong(payload.latestEntryTimestamp());
        writer.putNextLong(payload.serverBaselineTimestamp());
        List<HistoryEntrySnapshot> entries = payload.newEntries();
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

    public PacketGEHistoryDelta(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        long ownerAuth = reader.getNextLong();
        int totalItemsPurchased = reader.getNextInt();
        int totalItemsSold = reader.getNextInt();
        int totalSellOffersCreated = reader.getNextInt();
        int totalSellOffersCompleted = reader.getNextInt();
        int totalBuyOrdersCreated = reader.getNextInt();
        int totalBuyOrdersCompleted = reader.getNextInt();
        long latestEntryTimestamp = reader.getNextLong();
        long serverBaselineTimestamp = reader.getNextLong();
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
        this.payload = new HistoryDeltaPayload(
            ownerAuth,
            entries,
            totalItemsPurchased,
            totalItemsSold,
            totalSellOffersCreated,
            totalSellOffersCompleted,
            totalBuyOrdersCreated,
            totalBuyOrdersCompleted,
            latestEntryTimestamp,
            serverBaselineTimestamp
        );
    }

    public HistoryDeltaPayload payload() {
        return payload;
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        ModLogger.warn("PacketGEHistoryDelta received on server - this packet is server -> client only");
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        Container active = client.getContainer();
        if (!(active instanceof GrandExchangeContainer geContainer)) {
            ModLogger.warn("History delta received but player is not viewing the Grand Exchange");
            return;
        }
        try {
            GrandExchangeViewModel viewModel = geContainer.getViewModel();
            viewModel.applyHistoryDelta(payload);
            geContainer.onHistoryUpdated();
        } catch (IllegalStateException ex) {
            ModLogger.error("Failed to apply history delta: %s", ex.getMessage());
        }
    }
}
