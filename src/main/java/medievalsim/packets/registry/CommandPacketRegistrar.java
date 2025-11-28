package medievalsim.packets.registry;

import java.util.List;

import medievalsim.packets.PacketCommandResult;
import medievalsim.packets.PacketExecuteCommand;
import medievalsim.packets.core.PacketRegistrar;
import medievalsim.packets.core.PacketSpec;

public final class CommandPacketRegistrar {
    private static final PacketRegistrar REGISTRAR = () -> List.of(
        new PacketSpec(PacketExecuteCommand.class, "command", "Client -> server command execution"),
        new PacketSpec(PacketCommandResult.class, "command", "Server -> client command results")
    );

    private CommandPacketRegistrar() {
    }

    public static List<PacketSpec> getSpecs() {
        return REGISTRAR.getSpecs();
    }
}
