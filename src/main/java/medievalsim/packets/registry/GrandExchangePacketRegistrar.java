package medievalsim.packets.registry;

import java.util.List;

import medievalsim.packets.PacketGESync;
import medievalsim.packets.PacketOpenGrandExchange;
import medievalsim.packets.PacketGECreateBuyOrder;
import medievalsim.packets.PacketGEBuyOrderSync;
import medievalsim.packets.PacketGESellInventorySync;
import medievalsim.packets.core.PacketRegistrar;
import medievalsim.packets.core.PacketSpec;

public final class GrandExchangePacketRegistrar {
    private static final PacketRegistrar REGISTRAR = () -> List.of(
        new PacketSpec(PacketOpenGrandExchange.class, "grandexchange", "Client -> server open request"),
        new PacketSpec(PacketGESync.class, "grandexchange", "Server -> client listings sync"),
        new PacketSpec(PacketGECreateBuyOrder.class, "grandexchange", "Client -> server create buy order"),
        new PacketSpec(PacketGEBuyOrderSync.class, "grandexchange", "Server -> client buy orders sync"),
        new PacketSpec(PacketGESellInventorySync.class, "grandexchange", "Server -> client sell inventory sync")
    );

    private GrandExchangePacketRegistrar() {
    }

    public static List<PacketSpec> getSpecs() {
        return REGISTRAR.getSpecs();
    }
}
