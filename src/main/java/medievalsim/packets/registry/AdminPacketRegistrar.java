package medievalsim.packets.registry;

import java.util.List;

import medievalsim.packets.PacketForceClean;
import medievalsim.packets.PacketRequestPlayerList;
import medievalsim.packets.core.PacketRegistrar;
import medievalsim.packets.core.PacketSpec;

public final class AdminPacketRegistrar {
    private static final PacketRegistrar REGISTRAR = () -> List.of(
        new PacketSpec(PacketForceClean.class, "admin", "Force clean tiles"),
        new PacketSpec(PacketRequestPlayerList.class, "admin", "Request online player list")
    );

    private AdminPacketRegistrar() {
    }

    public static List<PacketSpec> getSpecs() {
        return REGISTRAR.getSpecs();
    }
}
