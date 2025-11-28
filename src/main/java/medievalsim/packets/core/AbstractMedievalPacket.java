package medievalsim.packets.core;

import medievalsim.util.ModLogger;
import necesse.engine.network.Packet;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;

/**
 * Base packet that centralizes simple validation helpers so higher level
 * packets can stay focused on gameplay logic. Subclasses should use the
 * provided guard methods before touching game state.
 */
public abstract class AbstractMedievalPacket extends Packet {

    protected AbstractMedievalPacket() {
        super();
    }

    protected AbstractMedievalPacket(byte[] data) {
        super(data);
    }

    protected final String packetName() {
        return getClass().getSimpleName();
    }

    protected final boolean ensureClient(ServerClient client, String packetName) {
        if (client == null || client.playerMob == null) {
            ModLogger.warn("%s: invalid server client context", packetName);
            return false;
        }
        return true;
    }

    protected final Level requireLevel(ServerClient client, String packetName) {
        if (!ensureClient(client, packetName)) {
            return null;
        }
        Level level = client.playerMob.getLevel();
        if (level == null) {
            ModLogger.warn("%s: missing level for auth=%d", packetName, client.authentication);
            return null;
        }
        return level;
    }
}
