package medievalsim.packets.core;

import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;

/**
 * Shared base for packets that need access to a server-side level instance and
 * typically operate on payload data.
 */
public abstract class AbstractLevelPacket extends AbstractPayloadPacket {

    protected AbstractLevelPacket() {
        super();
    }

    protected AbstractLevelPacket(byte[] data) {
        super(data);
    }

    protected final Level requireLevel(ServerClient client) {
        return super.requireLevel(client, packetName());
    }
}
