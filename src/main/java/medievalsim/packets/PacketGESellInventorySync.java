package medievalsim.packets;

import java.util.ArrayList;
import java.util.List;

import medievalsim.config.ModConfig;
import medievalsim.grandexchange.domain.GEOffer;
import medievalsim.grandexchange.model.snapshot.SellOfferSlotSnapshot;
import medievalsim.grandexchange.model.snapshot.SellOffersSnapshot;
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
 * Snapshot-based sell-offer sync packet (Phase 3).
 */
public class PacketGESellInventorySync extends AbstractPayloadPacket {
    public final long ownerAuth;
    private final SellOffersSnapshot snapshot;

    public SellOffersSnapshot getSnapshot() {
        return snapshot;
    }

    public PacketGESellInventorySync(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.ownerAuth = reader.getNextLong();
        int slotCapacity = reader.getNextByteUnsigned();
        int slotCount = reader.getNextByteUnsigned();
        List<SellOfferSlotSnapshot> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            boolean hasOffer = reader.getNextBoolean();
            if (!hasOffer) {
                slots.add(SellOfferSlotSnapshot.empty(i));
                continue;
            }
            long offerId = reader.getNextLong();
            String itemStringID = reader.getNextString();
            int quantityTotal = reader.getNextInt();
            int quantityRemaining = reader.getNextInt();
            int pricePerItem = reader.getNextInt();
            boolean enabled = reader.getNextBoolean();
            GEOffer.OfferState state = resolveState(reader.getNextByteUnsigned());
            int durationHours = reader.getNextInt();
            slots.add(new SellOfferSlotSnapshot(
                i,
                offerId,
                itemStringID,
                quantityTotal,
                quantityRemaining,
                pricePerItem,
                enabled,
                state,
                durationHours
            ));
        }
        this.snapshot = new SellOffersSnapshot(ownerAuth, slotCapacity, slots);
    }

    public PacketGESellInventorySync(long ownerAuth, GEOffer[] sellOffers) {
        this.ownerAuth = ownerAuth;
        this.snapshot = buildSnapshot(ownerAuth, sellOffers);

        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(ownerAuth);
        writer.putNextByteUnsigned(snapshot.slotCapacity());
        writer.putNextByteUnsigned(snapshot.slots().size());
        for (SellOfferSlotSnapshot slot : snapshot.slots()) {
            boolean hasOffer = slot != null && slot.isOccupied();
            writer.putNextBoolean(hasOffer);
            if (!hasOffer) {
                continue;
            }
            writer.putNextLong(slot.offerId());
            writer.putNextString(slot.itemStringID());
            writer.putNextInt(slot.quantityTotal());
            writer.putNextInt(slot.quantityRemaining());
            writer.putNextInt(slot.pricePerItem());
            writer.putNextBoolean(slot.enabled());
            writer.putNextByteUnsigned(slot.state().ordinal());
            writer.putNextInt(slot.durationHours());
        }
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        ModLogger.debug("Client: Received sell snapshot sync for player auth=%d", ownerAuth);
        Container container = client.getContainer();
        if (!(container instanceof GrandExchangeContainer geContainer)) {
            ModLogger.warn("Received sell snapshot but GE container not open");
            return;
        }
        if (geContainer.playerAuth != ownerAuth) {
            ModLogger.warn("Sell snapshot auth mismatch: container=%d, packet=%d",
                geContainer.playerAuth, ownerAuth);
            return;
        }
        try {
            GrandExchangeViewModel viewModel = geContainer.getViewModel();
            viewModel.applySellOffersSnapshot(snapshot);
            geContainer.onSellOffersUpdated();
        } catch (IllegalStateException ex) {
            ModLogger.error("Failed to apply sell snapshot on client: %s", ex.getMessage());
        }
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        ModLogger.warn("PacketGESellInventorySync.processServer called - server-to-client only!");
    }

    private static SellOffersSnapshot buildSnapshot(long ownerAuth, GEOffer[] sellOffers) {
        int slotCapacity = sellOffers == null ? ModConfig.GrandExchange.geInventorySlots : sellOffers.length;
        int slotCount = Math.max(0, slotCapacity);
        List<SellOfferSlotSnapshot> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            GEOffer offer = sellOffers == null || i >= sellOffers.length ? null : sellOffers[i];
            if (offer == null || offer.getItemStringID() == null) {
                slots.add(SellOfferSlotSnapshot.empty(i));
                continue;
            }
            slots.add(new SellOfferSlotSnapshot(
                i,
                offer.getOfferID(),
                offer.getItemStringID(),
                offer.getQuantityTotal(),
                offer.getQuantityRemaining(),
                offer.getPricePerItem(),
                offer.isEnabled(),
                offer.getState(),
                offer.getDurationHours()
            ));
        }
        return new SellOffersSnapshot(ownerAuth, slotCapacity, slots);
    }

    private static GEOffer.OfferState resolveState(int ordinal) {
        GEOffer.OfferState[] states = GEOffer.OfferState.values();
        if (ordinal < 0 || ordinal >= states.length) {
            return GEOffer.OfferState.DRAFT;
        }
        return states[ordinal];
    }
}
