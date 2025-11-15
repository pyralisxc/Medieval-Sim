package medievalsim.packets;

import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;

/**
 * Abstract base class for PvP zone dialog packets.
 * Eliminates duplication across Entry, Exit, and Spawn dialog packets.
 */
public abstract class PvPZoneDialogPacket extends Packet {

    // Common fields for all PvP zone dialogs
    public final int zoneID;
    public final String zoneName;
    public final float damageMultiplier;
    public final int combatLockSeconds;

    /**
     * Receiving constructor - reads common fields from packet data
     */
    protected PvPZoneDialogPacket(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.zoneID = reader.getNextInt();
        this.zoneName = reader.getNextString();
        this.damageMultiplier = reader.getNextFloat();
        this.combatLockSeconds = reader.getNextInt();

        // Allow subclasses to read additional fields
        readAdditionalFields(reader);
    }

    /**
     * Sending constructor - writes common fields to packet
     */
    protected PvPZoneDialogPacket(int zoneID, String zoneName, float damageMultiplier, int combatLockSeconds) {
        this.zoneID = zoneID;
        this.zoneName = zoneName;
        this.damageMultiplier = damageMultiplier;
        this.combatLockSeconds = combatLockSeconds;

        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(zoneID);
        writer.putNextString(zoneName);
        writer.putNextFloat(damageMultiplier);
        writer.putNextInt(combatLockSeconds);

        // Allow subclasses to write additional fields
        writeAdditionalFields(writer);
    }

    /**
     * Hook for subclasses to read additional fields after common fields
     */
    protected void readAdditionalFields(PacketReader reader) {
        // Default: no additional fields
    }

    /**
     * Hook for subclasses to write additional fields after common fields
     */
    protected void writeAdditionalFields(PacketWriter writer) {
        // Default: no additional fields
    }
}

