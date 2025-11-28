package medievalsim.packets.registry;

import java.util.List;

import medievalsim.packets.PacketBankInventoryUpdate;
import medievalsim.packets.PacketBankOpenResponse;
import medievalsim.packets.PacketBankSync;
import medievalsim.packets.PacketOpenBank;
import medievalsim.packets.PacketSetBankPIN;
import medievalsim.packets.core.PacketRegistrar;
import medievalsim.packets.core.PacketSpec;

public final class BankingPacketRegistrar {
    private static final PacketRegistrar REGISTRAR = () -> List.of(
        new PacketSpec(PacketOpenBank.class, "banking", "Client -> server bank open request"),
        new PacketSpec(PacketSetBankPIN.class, "banking", "Client -> server PIN set request"),
        new PacketSpec(PacketBankOpenResponse.class, "banking", "Server -> client response to open request"),
        new PacketSpec(PacketBankSync.class, "banking", "Server -> client full bank sync"),
        new PacketSpec(PacketBankInventoryUpdate.class, "banking", "Server -> client slot delta")
    );

    private BankingPacketRegistrar() {
    }

    public static List<PacketSpec> getSpecs() {
        return REGISTRAR.getSpecs();
    }
}
