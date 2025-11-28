package medievalsim.packets;
import medievalsim.ui.AdminToolsHudForm;
import medievalsim.ui.AdminToolsHudManager;
import medievalsim.zones.domain.AdminZonesLevelData;
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
    private final boolean isProtectedZone;

    public PacketZoneRemoved(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader((Packet)this);
        this.uniqueID = reader.getNextInt();
        this.isProtectedZone = reader.getNextBoolean();
    }

    public PacketZoneRemoved(int uniqueID, boolean isProtectedZone) {
        this.uniqueID = uniqueID;
        this.isProtectedZone = isProtectedZone;
        PacketWriter writer = new PacketWriter((Packet)this);
        writer.putNextInt(uniqueID);
        writer.putNextBoolean(isProtectedZone);
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
        if (this.isProtectedZone) {
            zoneData.getProtectedZones().remove(this.uniqueID);
        } else {
            zoneData.getPvPZones().remove(this.uniqueID);
        }
        AdminToolsHudForm hudForm = AdminToolsHudManager.getHudForm();
        if (hudForm != null) {
            hudForm.onZoneRemoved(this.uniqueID, this.isProtectedZone);
        }
    }
}

