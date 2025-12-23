package medievalsim.packets;

import java.util.ArrayList;
import java.util.List;

import medievalsim.grandexchange.domain.BuyOrder;
import medievalsim.grandexchange.domain.PlayerGEInventory;
import medievalsim.grandexchange.model.snapshot.BuyOrderSlotSnapshot;
import medievalsim.grandexchange.model.snapshot.BuyOrderSlotSnapshot.Analytics;
import medievalsim.grandexchange.model.snapshot.BuyOrderSlotSnapshot.PersonalHistory;
import medievalsim.grandexchange.model.snapshot.BuyOrdersSnapshot;
import medievalsim.grandexchange.services.RateLimitStatus;
import medievalsim.grandexchange.services.MarketAnalyticsService;
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

/**
 * Packet to sync buy orders (3 slots) from server to client.
 * Includes buy order configuration and state for each slot.
 */
public class PacketGEBuyOrderSync extends AbstractPayloadPacket {
    public final long ownerAuth;
    private final BuyOrdersSnapshot snapshot;
    
    public BuyOrdersSnapshot getSnapshot() {
        return snapshot;
    }
    
    /**
     * Receiving constructor (client-side).
     */
    public PacketGEBuyOrderSync(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.ownerAuth = reader.getNextLong();
        int slotCount = reader.getNextByteUnsigned();
        List<BuyOrderSlotSnapshot> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            long orderId = reader.getNextLong();
            boolean hasOrder = reader.getNextBoolean();
            if (!hasOrder) {
                slots.add(emptySlot(i));
                continue;
            }
            String itemStringID = reader.getNextString();
            int quantityTotal = reader.getNextInt();
            int quantityRemaining = reader.getNextInt();
            int pricePerItem = reader.getNextInt();
            boolean enabled = reader.getNextBoolean();
            BuyOrder.BuyOrderState state = resolveState(reader.getNextByteUnsigned());
            int durationDays = reader.getNextInt();

            Analytics analytics = null;
            if (reader.getNextBoolean()) {
                analytics = new Analytics(
                    reader.getNextInt(),
                    reader.getNextInt(),
                    reader.getNextInt(),
                    reader.getNextInt(),
                    reader.getNextInt(),
                    reader.getNextInt(),
                    reader.getNextLong()
                );
            }

            PersonalHistory personalHistory = null;
            if (reader.getNextBoolean()) {
                personalHistory = new PersonalHistory(
                    reader.getNextInt(),
                    reader.getNextInt(),
                    reader.getNextLong()
                );
            }

            slots.add(new BuyOrderSlotSnapshot(
                i,
                orderId,
                itemStringID,
                quantityTotal,
                quantityRemaining,
                pricePerItem,
                enabled,
                state,
                durationDays,
                analytics,
                personalHistory
            ));
        }
        float creationCooldownSeconds = reader.getNextFloat();
        float toggleCooldownSeconds = reader.getNextFloat();
        this.snapshot = new BuyOrdersSnapshot(ownerAuth, slots, creationCooldownSeconds, toggleCooldownSeconds);
    }

    /**
     * Sending constructor (server-side).
     */
    public PacketGEBuyOrderSync(long ownerAuth, BuyOrder[] buyOrders,
                                MarketAnalyticsService analyticsService,
                                PlayerGEInventory inventory) {
        this(ownerAuth, buyOrders, analyticsService, inventory, null, null);
    }

    public PacketGEBuyOrderSync(long ownerAuth, BuyOrder[] buyOrders,
                                MarketAnalyticsService analyticsService,
                                PlayerGEInventory inventory,
                                RateLimitStatus creationStatus,
                                RateLimitStatus toggleStatus) {
        ModLogger.debug("PacketGEBuyOrderSync constructor: Creating packet for auth=%d with %d slots",
            ownerAuth, buyOrders.length);
        this.ownerAuth = ownerAuth;
        this.snapshot = buildSnapshot(ownerAuth, buyOrders, analyticsService, inventory, creationStatus, toggleStatus);

        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(ownerAuth);
        writer.putNextByteUnsigned(snapshot.slots().size());
        for (BuyOrderSlotSnapshot slot : snapshot.slots()) {
            writer.putNextLong(slot.orderId());
            boolean hasOrder = slot.itemStringID() != null && !slot.itemStringID().isBlank();
            writer.putNextBoolean(hasOrder);
            if (!hasOrder) {
                continue;
            }
            writer.putNextString(slot.itemStringID());
            writer.putNextInt(slot.quantityTotal());
            writer.putNextInt(slot.quantityRemaining());
            writer.putNextInt(slot.pricePerItem());
            writer.putNextBoolean(slot.enabled());
            writer.putNextByteUnsigned(slot.state().ordinal());
            writer.putNextInt(slot.durationDays());
            Analytics analytics = slot.analytics();
            writer.putNextBoolean(analytics != null);
            if (analytics != null) {
                writer.putNextInt(analytics.guidePrice());
                writer.putNextInt(analytics.vwapPrice());
                writer.putNextInt(analytics.low24h());
                writer.putNextInt(analytics.high24h());
                writer.putNextInt(analytics.tradeVolume());
                writer.putNextInt(analytics.tradeCount());
                writer.putNextLong(analytics.lastTradeTimestamp());
            }
            PersonalHistory personal = slot.personalHistory();
            writer.putNextBoolean(personal != null);
            if (personal != null) {
                writer.putNextInt(personal.pricePerItem());
                writer.putNextInt(personal.quantity());
                writer.putNextLong(personal.timestamp());
            }
        }
        writer.putNextFloat(snapshot.creationCooldownSeconds());
        writer.putNextFloat(snapshot.toggleCooldownSeconds());
    }
    
    @Override
    public void processClient(NetworkPacket packet, Client client) {
        ModLogger.info("[PACKET TRACE] processClient START: PacketGEBuyOrderSync for auth=%d", ownerAuth);
        necesse.inventory.container.Container container = client.getContainer();
        if (!(container instanceof GrandExchangeContainer geContainer)) {
            ModLogger.warn("Received buy order sync but GE container not open");
            return;
        }

        if (geContainer.playerAuth != this.ownerAuth) {
            ModLogger.warn("Buy order sync auth mismatch: container=%d, packet=%d",
                geContainer.playerAuth, this.ownerAuth);
            return;
        }

        try {
            GrandExchangeViewModel viewModel = geContainer.getViewModel();
            viewModel.applyBuyOrdersSnapshot(snapshot);
            geContainer.onBuyOrdersUpdated();
        } catch (IllegalStateException ex) {
            ModLogger.error("Failed to apply buy order snapshot on client: %s", ex.getMessage());
        }

        ModLogger.debug("Client: Updated %d buy order slots", snapshot.slots().size());
    }
    
    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        ModLogger.warn("PacketGEBuyOrderSync.processServer called - server-to-client only!");
    }

    private static BuyOrdersSnapshot buildSnapshot(long ownerAuth,
                                                   BuyOrder[] buyOrders,
                                                   MarketAnalyticsService analyticsService,
                                                   PlayerGEInventory inventory,
                                                   RateLimitStatus creationStatus,
                                                   RateLimitStatus toggleStatus) {
        int slotCount = buyOrders == null ? 0 : buyOrders.length;
        List<BuyOrderSlotSnapshot> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            BuyOrder order = buyOrders[i];
            if (order == null || order.getItemStringID() == null) {
                slots.add(emptySlot(i));
                continue;
            }
            Analytics analytics = resolveAnalytics(order, analyticsService);
            PersonalHistory history = resolvePersonalHistory(order, inventory);
            slots.add(new BuyOrderSlotSnapshot(
                i,
                order.getOrderID(),
                order.getItemStringID(),
                order.getQuantityTotal(),
                order.getQuantityRemaining(),
                order.getPricePerItem(),
                order.isEnabled(),
                order.getState(),
                order.getDurationDays(),
                analytics,
                history
            ));
        }
        float createRemaining = creationStatus == null ? 0f : creationStatus.remainingSeconds();
        float toggleRemaining = toggleStatus == null ? 0f : toggleStatus.remainingSeconds();
        return new BuyOrdersSnapshot(ownerAuth, slots, createRemaining, toggleRemaining);
    }

    private static Analytics resolveAnalytics(BuyOrder order, MarketAnalyticsService analyticsService) {
        if (analyticsService == null || order.getItemStringID() == null) {
            return null;
        }
        var summary = analyticsService.getMarketSummary(order.getItemStringID());
        if (summary == null || !summary.hasTradeHistory()) {
            return null;
        }
        return new Analytics(
            summary.getGuidePrice(),
            summary.getVWAP(),
            summary.getLow24h(),
            summary.getHigh24h(),
            summary.getTradeVolume(),
            summary.getTradeCount(),
            analyticsService.getLastTradeTimestamp(order.getItemStringID())
        );
    }

    private static PersonalHistory resolvePersonalHistory(BuyOrder order, PlayerGEInventory inventory) {
        if (inventory == null || order.getItemStringID() == null) {
            return null;
        }
        PlayerGEInventory.PersonalTradeRecord record = inventory.getLastPurchaseRecord(order.getItemStringID());
        if (record == null) {
            return null;
        }
        return new PersonalHistory(
            record.getPricePerItem(),
            record.getQuantity(),
            record.getTimestamp()
        );
    }

    private static BuyOrderSlotSnapshot emptySlot(int slotIndex) {
        return new BuyOrderSlotSnapshot(
            slotIndex,
            0L,
            null,
            0,
            0,
            0,
            false,
            BuyOrder.BuyOrderState.DRAFT,
            0,
            null,
            null
        );
    }

    private static BuyOrder.BuyOrderState resolveState(int ordinal) {
        BuyOrder.BuyOrderState[] states = BuyOrder.BuyOrderState.values();
        if (ordinal < 0 || ordinal >= states.length) {
            return BuyOrder.BuyOrderState.DRAFT;
        }
        return states[ordinal];
    }
}
