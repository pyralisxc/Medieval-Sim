package medievalsim.packets;
import medievalsim.ui.AdminToolsHudForm;
import medievalsim.ui.AdminToolsHudManager;
import medievalsim.zones.domain.AdminZone;
import medievalsim.zones.domain.AdminZonesLevelData;
import medievalsim.zones.domain.ProtectedZone;
import medievalsim.zones.domain.PvPZone;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.Zoning;
import necesse.level.maps.Level;

public class PacketZoneChanged
extends Packet {
    private final int uniqueID;
    private final boolean isProtectedZone;
    private final String name;
    private final long creatorAuth;
    private final int colorHue;
    private final byte[] zoningData;
    private final float damageMultiplier;
    private final int combatLockSeconds;

    public PacketZoneChanged(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader((Packet)this);
        this.uniqueID = reader.getNextInt();
        this.isProtectedZone = reader.getNextBoolean();
        this.name = reader.getNextString();
        this.creatorAuth = reader.getNextLong();
        this.colorHue = reader.getNextInt();
        int zoningDataLength = reader.getNextInt();
        this.zoningData = reader.getNextBytes(zoningDataLength);
        if (!this.isProtectedZone) {
            this.damageMultiplier = reader.getNextFloat();
            this.combatLockSeconds = reader.getNextInt();
        } else {
            // Provide sensible defaults when data isn't present for protected zones
            this.damageMultiplier = medievalsim.config.ModConfig.Zones.defaultDamageMultiplier;
            this.combatLockSeconds = medievalsim.config.ModConfig.Zones.defaultCombatLockSeconds;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public PacketZoneChanged(AdminZone zone, boolean isProtectedZone) {
        this.uniqueID = zone.uniqueID;
        this.isProtectedZone = isProtectedZone;
        this.name = zone.name;
        this.creatorAuth = zone.creatorAuth;
        this.colorHue = zone.colorHue;
        if (!isProtectedZone && zone instanceof PvPZone) {
            PvPZone pvpZone = (PvPZone)zone;
            this.damageMultiplier = pvpZone.damageMultiplier;
            this.combatLockSeconds = pvpZone.combatLockSeconds;
        } else {
            this.damageMultiplier = medievalsim.config.ModConfig.Zones.defaultDamageMultiplier;
            this.combatLockSeconds = medievalsim.config.ModConfig.Zones.defaultCombatLockSeconds;
        }
        Packet zoningPacket = new Packet();
        PacketWriter zoningWriter = new PacketWriter(zoningPacket);
        Zoning zoning = zone.zoning;
        synchronized (zoning) {
            zone.zoning.writeZonePacket(zoningWriter);
        }
        this.zoningData = zoningPacket.getBytes(0, zoningPacket.getSize());
        PacketWriter writer = new PacketWriter((Packet)this);
        writer.putNextInt(this.uniqueID);
        writer.putNextBoolean(isProtectedZone);
        writer.putNextString(this.name);
        writer.putNextLong(this.creatorAuth);
        writer.putNextInt(this.colorHue);
        writer.putNextInt(this.zoningData.length);
        writer.putNextBytes(this.zoningData);
        if (!isProtectedZone) {
            writer.putNextFloat(this.damageMultiplier);
            writer.putNextInt(this.combatLockSeconds);
        }
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void processClient(NetworkPacket packet, Client client) {
        Level level = client.getLevel();
        if (level == null) {
            return;
        }
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, true);
        if (zoneData == null) {
            return;
        }
        AdminZone zone = this.isProtectedZone ? new ProtectedZone(this.uniqueID, this.name, this.creatorAuth, this.colorHue) : new PvPZone(this.uniqueID, this.name, this.creatorAuth, this.colorHue, this.damageMultiplier, this.combatLockSeconds);
        Packet tempPacket = new Packet(this.zoningData);
        PacketReader zoningReader = new PacketReader(tempPacket);
        Zoning zoning = zone.zoning;
        synchronized (zoning) {
            zone.zoning.readZonePacket(zoningReader);
        }
        if (this.isProtectedZone) {
            zoneData.putProtectedZone((ProtectedZone)zone);
        } else {
            zoneData.putPvPZone((PvPZone)zone);
        }
        AdminToolsHudForm hudForm = AdminToolsHudManager.getHudForm();
        if (hudForm != null) {
            hudForm.onZoneChanged(zone, this.isProtectedZone);
        }
    }
}

