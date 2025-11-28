package medievalsim.packets;

import medievalsim.util.ModLogger;
import medievalsim.zones.domain.PvPZone;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

public class PacketConfigurePvPZone
extends Packet {
    public int zoneID;
    public float damageMultiplier;
    public int combatLockSeconds;
    public float dotDamageMultiplier;
    public float dotIntervalMultiplier;

    public PacketConfigurePvPZone(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader((Packet)this);
        this.zoneID = reader.getNextInt();
        this.damageMultiplier = reader.getNextFloat();
        this.dotDamageMultiplier = reader.getNextFloat();
        this.dotIntervalMultiplier = reader.getNextFloat();
        this.combatLockSeconds = reader.getNextInt();
    }

    public PacketConfigurePvPZone(int zoneID, float damageMultiplier, int combatLockSeconds, float dotDamageMultiplier, float dotIntervalMultiplier) {
        this.zoneID = zoneID;
        this.damageMultiplier = damageMultiplier;
        this.combatLockSeconds = combatLockSeconds;
        this.dotDamageMultiplier = dotDamageMultiplier;
        this.dotIntervalMultiplier = dotIntervalMultiplier;
        PacketWriter writer = new PacketWriter((Packet)this);
        writer.putNextInt(zoneID);
        writer.putNextFloat(damageMultiplier);
        writer.putNextFloat(dotDamageMultiplier);
        writer.putNextFloat(dotIntervalMultiplier);
        writer.putNextInt(combatLockSeconds);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            // Validate using ZoneAPI
            medievalsim.util.ZoneAPI.ZoneContext ctx = medievalsim.util.ZoneAPI.forClient(client)
                .withPacketName("PacketConfigurePvPZone")
                .requirePvPZone(this.zoneID)
                .build();
            if (!ctx.isValid()) return;

            PvPZone zone = ctx.getPvPZone();
            
            // Apply validated settings with clamping
            zone.damageMultiplier = Math.max(0.001f, Math.min(0.1f, this.damageMultiplier));
            zone.combatLockSeconds = Math.max(0, Math.min(10, this.combatLockSeconds));
            // DoT damage multiplier now maps directly to a 0-100% slider value
            zone.dotDamageMultiplier = Math.max(0.0f, Math.min(1.0f, this.dotDamageMultiplier));
            zone.dotIntervalMultiplier = Math.max(0.25f, Math.min(4.0f, this.dotIntervalMultiplier));
            
            // Silent update - logging removed to prevent spam during slider adjustments
            server.network.sendToAllClients((Packet)new PacketZoneChanged(zone, false));
            
        } catch (Exception e) {
            ModLogger.error("Exception in PacketConfigurePvPZone.processServer", e);
        }
    }
}

