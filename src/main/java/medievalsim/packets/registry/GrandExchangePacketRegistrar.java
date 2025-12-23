package medievalsim.packets.registry;

import java.util.List;

import medievalsim.packets.PacketGEAutoClearSync;
import medievalsim.packets.PacketGECollectionSync;
import medievalsim.packets.PacketGECreateBuyOrder;
import medievalsim.packets.PacketGEBuyOrderSync;
import medievalsim.packets.PacketGEDefaultsConfig;
import medievalsim.packets.PacketGEError;
import medievalsim.packets.PacketGEFeedback;
import medievalsim.packets.PacketGEHistoryAck;
import medievalsim.packets.PacketGEHistoryBadge;
import medievalsim.packets.PacketGEHistoryDelta;
import medievalsim.packets.PacketGEHistorySync;
import medievalsim.packets.PacketGESaleEvent;
import medievalsim.packets.PacketGESellActionResult;
import medievalsim.packets.PacketGESellInventorySync;
import medievalsim.packets.PacketGESync;
import medievalsim.packets.PacketOpenGrandExchange;
import medievalsim.packets.core.PacketRegistrar;
import medievalsim.packets.core.PacketSpec;

public final class GrandExchangePacketRegistrar {
    private static final PacketRegistrar REGISTRAR = () -> List.of(
        new PacketSpec(PacketOpenGrandExchange.class, "grandexchange", "Client -> server open request"),
        new PacketSpec(PacketGESync.class, "grandexchange", "Server -> client listings sync"),
        new PacketSpec(PacketGECreateBuyOrder.class, "grandexchange", "Client -> server create buy order"),
        new PacketSpec(PacketGEBuyOrderSync.class, "grandexchange", "Server -> client buy orders sync"),
        new PacketSpec(PacketGESellInventorySync.class, "grandexchange", "Server -> client sell inventory sync"),
        new PacketSpec(PacketGECollectionSync.class, "grandexchange", "Server -> client collection box sync"),
        new PacketSpec(PacketGESellActionResult.class, "grandexchange", "Server -> client sell action result feedback"),
        new PacketSpec(PacketGEFeedback.class, "grandexchange", "Server -> client general feedback message"),
        new PacketSpec(PacketGEError.class, "grandexchange", "Server -> client error notification"),
        new PacketSpec(PacketGEAutoClearSync.class, "grandexchange", "Server -> client auto-clear preference sync"),
        new PacketSpec(PacketGEDefaultsConfig.class, "grandexchange", "Server -> client defaults tab config sync"),
        new PacketSpec(PacketGEHistorySync.class, "grandexchange", "Server -> client history tab sync"),
        new PacketSpec(PacketGEHistoryDelta.class, "grandexchange", "Server -> client incremental history delta"),
        new PacketSpec(PacketGEHistoryBadge.class, "grandexchange", "Server -> client history badge sync"),
        new PacketSpec(PacketGEHistoryAck.class, "grandexchange", "Client -> server history acknowledgement"),
        new PacketSpec(PacketGESaleEvent.class, "grandexchange", "Server -> client instant sale feedback")
    );

    private GrandExchangePacketRegistrar() {
    }

    public static List<PacketSpec> getSpecs() {
        return REGISTRAR.getSpecs();
    }
}
