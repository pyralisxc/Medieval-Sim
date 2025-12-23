package medievalsim.packets;
import medievalsim.ui.AdminToolsHudForm;
import medievalsim.ui.AdminToolsHudManager;
import medievalsim.zones.domain.AdminZonesLevelData;
import medievalsim.zones.domain.ZoneType;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;

public class PacketZoneRemoved
extends Packet {
    private final int uniqueID;
    private final ZoneType zoneType;

    public PacketZoneRemoved(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader((Packet)this);
        this.uniqueID = reader.getNextInt();
        this.zoneType = ZoneType.fromId(reader.getNextInt());
    }

    public PacketZoneRemoved(int uniqueID, ZoneType zoneType) {
        this.uniqueID = uniqueID;
        this.zoneType = zoneType;
        PacketWriter writer = new PacketWriter((Packet)this);
        writer.putNextInt(uniqueID);
        writer.putNextInt(zoneType.getId());
    }

    /**
     * Legacy constructor for backward compatibility.
     * @deprecated Use {@link #PacketZoneRemoved(int, ZoneType)} instead
     */
    @Deprecated
    public PacketZoneRemoved(int uniqueID, boolean isProtectedZone) {
        this(uniqueID, isProtectedZone ? ZoneType.PROTECTED : ZoneType.PVP);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
    }

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
        switch (this.zoneType) {
            case PVP:
                zoneData.getPvPZones().remove(this.uniqueID);
                break;
            case GUILD:
                zoneData.getGuildZones().remove(this.uniqueID);
                break;
            default:
                zoneData.getProtectedZones().remove(this.uniqueID);
                break;
        }
        AdminToolsHudForm hudForm = AdminToolsHudManager.getHudForm();
        if (hudForm != null) {
            hudForm.onZoneRemoved(this.uniqueID, this.zoneType);
        }
    }
}

