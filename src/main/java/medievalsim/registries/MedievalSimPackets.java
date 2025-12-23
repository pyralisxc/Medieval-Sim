package medievalsim.registries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import medievalsim.packets.core.PacketSpec;
import medievalsim.packets.registry.AdminPacketRegistrar;
import medievalsim.packets.registry.BankingPacketRegistrar;
import medievalsim.packets.registry.CommandPacketRegistrar;
import medievalsim.packets.registry.GrandExchangePacketRegistrar;
import medievalsim.packets.registry.ZonePacketRegistrar;
import medievalsim.util.ModLogger;
import necesse.engine.registries.PacketRegistry;

public class MedievalSimPackets {

    private static final List<Supplier<List<PacketSpec>>> REGISTRARS = List.of(
        ZonePacketRegistrar::getSpecs,
        AdminPacketRegistrar::getSpecs,
        CommandPacketRegistrar::getSpecs,
        BankingPacketRegistrar::getSpecs,
        GrandExchangePacketRegistrar::getSpecs,
        medievalsim.packets.registry.GuildPacketRegistrar::getSpecs
    );

    public static void registerCore() {
        List<PacketSpec> specs = new ArrayList<>();
        for (Supplier<List<PacketSpec>> registrar : REGISTRARS) {
            specs.addAll(registrar.get());
        }
        specs.forEach(spec -> PacketRegistry.registerPacket(spec.type()));
        ModLogger.debug("Registered %d network packets across %d domains", specs.size(), REGISTRARS.size());
    }
}

