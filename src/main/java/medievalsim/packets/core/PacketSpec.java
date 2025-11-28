package medievalsim.packets.core;

import java.util.Objects;

import necesse.engine.network.Packet;

/**
 * Simple descriptor used by registrar classes so packet registration stays
 * declarative and grouped by subsystem.
 */
public record PacketSpec(Class<? extends Packet> type, String domain, String description) {

    public PacketSpec {
        Objects.requireNonNull(type, "type");
        domain = domain != null ? domain : "core";
        description = description != null ? description : type.getSimpleName();
    }
}
