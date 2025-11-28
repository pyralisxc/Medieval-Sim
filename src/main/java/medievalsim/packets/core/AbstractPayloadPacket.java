package medievalsim.packets.core;

import medievalsim.util.ModLogger;

/**
 * Base for packets that primarily move payload data and need consistent
 * validation helpers (string length limits, null checks, etc.).
 */
public abstract class AbstractPayloadPacket extends AbstractMedievalPacket {

    protected AbstractPayloadPacket() {
        super();
    }

    protected AbstractPayloadPacket(byte[] data) {
        super(data);
    }

    protected final boolean validateString(String value, int minLength, int maxLength, String fieldName) {
        if (value == null) {
            ModLogger.warn("%s: field '%s' was null", packetName(), fieldName);
            return false;
        }
        int length = value.length();
        if (length < minLength || length > maxLength) {
            ModLogger.warn("%s: field '%s' length %d outside range [%d,%d]", packetName(), fieldName, length, minLength, maxLength);
            return false;
        }
        return true;
    }
}
