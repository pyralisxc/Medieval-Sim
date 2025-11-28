package medievalsim.patches;
import medievalsim.zones.domain.PvPZone;
import medievalsim.zones.service.PvPZoneTracker;
import medievalsim.zones.service.ZoneManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.packet.PacketSpawnPlayer;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.GameMath;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

public class ServerClientSubmitSpawnPacketPatch {

    @ModMethodPatch(target=ServerClient.class, name="submitSpawnPacket", arguments={PacketSpawnPlayer.class})
    public static class SubmitSpawnPacket {
        @Advice.OnMethodExit
        static void onExit(@Advice.This ServerClient client, @Advice.FieldValue(value="server") Server server, @Advice.FieldValue(value="hasSpawned") boolean hasSpawned) {
            if (client.playerMob == null || client.isDead()) {
                return;
            }
            int tileX = GameMath.getTileCoordinate((int)((int)client.playerMob.x));
            int tileY = GameMath.getTileCoordinate((int)((int)client.playerMob.y));
            Level level = client.playerMob.getLevel();
            if (level == null) {
                return;
            }
            PvPZone zone = ZoneManager.getPvPZoneAt(level, tileX, tileY);
            if (zone != null) {
                long serverTime = server.world.worldEntity.getTime();
                PvPZoneTracker.handleSpawnInZone(client, zone, server, serverTime);
            }
            
            // Initialize ProtectedZone buff if spawning in a protected zone
            medievalsim.zones.domain.AdminZonesLevelData zoneData =
                medievalsim.zones.domain.AdminZonesLevelData.getZoneData(level, false);
            if (zoneData != null) {
                medievalsim.zones.domain.ProtectedZone protectedZone = zoneData.getProtectedZoneAt(tileX, tileY);
                medievalsim.zones.service.ProtectedZoneTracker.updatePlayerZone(client, protectedZone);
            }

            // Initialize Settlement Protection buff if spawning in a protected settlement
            medievalsim.zones.settlement.SettlementProtectionTracker.updatePlayerSettlement(client, tileX, tileY);
        }
    }
}

