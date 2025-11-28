package medievalsim.packets.core;

import java.util.List;

/**
 * Contract for domain registrars. Each subsystem returns the packets it owns so
 * the central registry can iterate them uniformly.
 */
@FunctionalInterface
public interface PacketRegistrar {
    List<PacketSpec> getSpecs();
}
