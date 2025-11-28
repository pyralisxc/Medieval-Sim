package medievalsim.zones.service;

import java.util.List;

import medievalsim.util.ModLogger;
import medievalsim.zones.domain.ProtectedZone;
import medievalsim.zones.service.ProtectedZoneTracker;
import medievalsim.zones.domain.PvPZone;
import medievalsim.zones.service.PvPZoneDotHandler;
import medievalsim.zones.service.PvPZoneTracker;
import medievalsim.zones.settlement.SettlementProtectionTracker;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.GameMath;
import necesse.level.maps.Level;
import necesse.level.maps.regionSystem.Region;

/**
 * Coordinates per-tick zone side effects including buffs as well as barrier
 * maintenance hooks exposed via {@link PvPBarrierService}.
 */
public class ZoneEffectsService {
    private final ZoneRepository repository;
    private final PvPBarrierService barrierService;

    public ZoneEffectsService(ZoneRepository repository, PvPBarrierService barrierService) {
        this.repository = repository;
        this.barrierService = barrierService;
    }

    public boolean ensureInitialBarriers(Level level, boolean alreadyCreated) {
        if (alreadyCreated || level == null || !level.isServer()) {
            return alreadyCreated;
        }
        List<PvPZone> snapshot = repository.snapshotPvPZones();
        barrierService.createInitialBarriers(level, snapshot);
        return true;
    }

    public void updatePlayerEffects(Level level) {
        if (level == null || !level.isServer() || level.getServer() == null) {
            return;
        }
        for (ServerClient client : level.getServer().getClients()) {
            if (client == null || client.playerMob == null || client.playerMob.getLevel() != level) {
                continue;
            }
            int tileX = GameMath.getTileCoordinate((int) client.playerMob.x);
            int tileY = GameMath.getTileCoordinate((int) client.playerMob.y);

            ProtectedZone protectedZone = repository.getProtectedZoneAt(tileX, tileY);
            ProtectedZoneTracker.updatePlayerZone(client, protectedZone);

            PvPZone pvpZone = repository.getPvPZoneAt(client.playerMob.x, client.playerMob.y);
            PvPZoneTracker.updatePlayerZoneBuff(client, pvpZone);

            SettlementProtectionTracker.updatePlayerSettlement(client, tileX, tileY);
        }
    }

    public void processTick(Level level) {
        barrierService.processTick(level);
        try {
            if (level != null && level.isServer()) {
                PvPZoneDotHandler.processLevelTick(level);
            }
        } catch (Throwable t) {
            ModLogger.error("Error running PvPZoneDotHandler", t);
        }
    }

    public void onRegionLoaded(Level level, Region region) {
        barrierService.onRegionLoaded(level, region, repository.snapshotPvPZones());
    }
}
