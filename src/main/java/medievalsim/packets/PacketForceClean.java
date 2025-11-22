package medievalsim.packets;

import medievalsim.util.ModLogger;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;
import medievalsim.zones.AdminZonesLevelData;

public class PacketForceClean extends Packet {
    public int centerX;
    public int centerY;
    public int radius;

    public PacketForceClean(byte[] data) {
        super(data);
        PacketReader r = new PacketReader(this);
        this.centerX = r.getNextInt();
        this.centerY = r.getNextInt();
        this.radius = r.getNextInt();
    }

    public PacketForceClean(int centerX, int centerY, int radius) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.radius = radius;
        PacketWriter w = new PacketWriter(this);
        w.putNextInt(centerX);
        w.putNextInt(centerY);
        w.putNextInt(radius);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            // Permission check
            if (client.getPermissionLevel().getLevel() < necesse.engine.commands.PermissionLevel.ADMIN.getLevel()) {
                ModLogger.warn("Player " + client.getName() + " attempted to force clean barriers without admin permission");
                return;
            }
            
            // Level validation
            Level level = server.world.getLevel(client);
            if (level == null) {
                ModLogger.error("Failed to get level for client " + client.getName() + " in PacketForceClean");
                return;
            }
            
            // Zone data validation
            AdminZonesLevelData data = AdminZonesLevelData.getZoneData(level, false);
            if (data == null) {
                ModLogger.error("Failed to get zone data for level " + level.getIdentifier() + " in PacketForceClean");
                return;
            }
            
            // Sanitize radius to safe range
            int r = Math.max(10, Math.min(500, this.radius));
            int tileX = this.centerX;
            int tileY = this.centerY;
            
            data.forceCleanAround(level, tileX, tileY, r, server);
            ModLogger.info("Force clean barriers around (" + tileX + "," + tileY + ") radius " + r + " by " + client.getName());
            
        } catch (Exception e) {
            ModLogger.error("Exception during forceClean", e);
        }
    }
}
