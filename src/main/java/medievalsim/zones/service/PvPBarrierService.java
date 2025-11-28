package medievalsim.zones.service;

import java.awt.Point;
import java.util.Collection;
import java.util.List;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import medievalsim.zones.service.BarrierPlacementWorker;
import medievalsim.zones.domain.PvPZone;
import medievalsim.zones.service.PvPZoneBarrierManager;
import necesse.engine.network.Packet;
import necesse.engine.network.packet.PacketPlaceObject;
import necesse.engine.network.server.Server;
import necesse.engine.util.PointHashSet;
import necesse.level.maps.Level;
import necesse.level.maps.regionSystem.Region;

/**
 * Encapsulates all PvP barrier placement, removal, and maintenance logic so
 * callers do not need to interact with BarrierPlacementWorker directly.
 */
public class PvPBarrierService {

    public void createInitialBarriers(Level level, List<PvPZone> zones) {
        if (level == null || zones == null) {
            return;
        }
        ModLogger.info("Creating initial barriers for %d PvP zones...", zones.size());
        for (PvPZone zone : zones) {
            try {
                ModLogger.info("Creating barriers for PvP zone '%s' (ID: %d)", zone.name, zone.uniqueID);
                zone.createBarriers(level);
            } catch (Exception e) {
                ModLogger.error("Error creating initial barriers for zone '" + (zone != null ? zone.name : "<null>") + "'", e);
            }
        }
    }

    public void removeZoneArtifacts(Level level, PvPZone zone) {
        if (zone == null || level == null) {
            return;
        }
        try {
            zone.removeBarriers(level);
        } catch (Exception ex) {
            if (ModConfig.Logging.verboseDebug) {
                ModLogger.debug("Failed to remove barriers for zone %d during removal: %s", zone.uniqueID, ex.getMessage());
            }
        }
        BarrierPlacementWorker.removeQueuedTasksForZone(level, zone.uniqueID);
    }

    public void queueEdgePlacements(Level level, PvPZone zone) {
        if (zone == null || level == null) {
            return;
        }
        PointHashSet edges = zone.zoning.getEdgeTiles();
        if (edges == null) {
            return;
        }
        for (Object o : edges) {
            if (!(o instanceof Point)) {
                continue;
            }
            Point point = (Point) o;
            Region region = level.regionManager.getRegionByTile(point.x, point.y, false);
            if (region != null) {
                BarrierPlacementWorker.queueZoneRegionPlacement(level, zone, region);
            }
        }
    }

    public void updateBarriers(Level level, PvPZone zone, Collection<Point> oldEdges) {
        if (zone == null || level == null) {
            return;
        }
        if (oldEdges != null) {
            PvPZoneBarrierManager.updateBarrier(level, zone, oldEdges);
        } else {
            queueEdgePlacements(level, zone);
        }
    }

    public void onRegionLoaded(Level level, Region region, List<PvPZone> zones) {
        if (region == null || level == null || zones == null) {
            return;
        }
        for (PvPZone zone : zones) {
            try {
                PointHashSet edge = zone.zoning.getEdgeTiles();
                if (edge == null || edge.isEmpty()) {
                    continue;
                }
                boolean intersects = false;
                for (Object o : edge) {
                    if (!(o instanceof Point)) {
                        continue;
                    }
                    Point p = (Point) o;
                    int rX = level.regionManager.getRegionXByTileLimited(p.x);
                    int rY = level.regionManager.getRegionYByTileLimited(p.y);
                    if (rX == region.regionX && rY == region.regionY) {
                        intersects = true;
                        break;
                    }
                }
                if (intersects) {
                    BarrierPlacementWorker.queueZoneRegionPlacement(level, zone, region);
                }
            } catch (Exception e) {
                ModLogger.error("Error queuing barrier placement on region load", e);
            }
        }
    }

    public void processTick(Level level) {
        if (level == null) {
            return;
        }
        BarrierPlacementWorker.processTick(level, ModConfig.Zones.barrierMaxTilesPerTick);
    }

    public void forceCleanAround(Level level, int centerTileX, int centerTileY, int radius, Server server, Iterable<PvPZone> zones) {
        if (level == null || !level.isServer() || server == null) {
            return;
        }
        int barrierID = PvPZoneBarrierManager.getBarrierObjectID();
        if (barrierID == -1) {
            return;
        }

        int minX = centerTileX - radius;
        int minY = centerTileY - radius;
        int maxX = centerTileX + radius;
        int maxY = centerTileY + radius;

        long area = (long) (maxX - minX + 1) * (maxY - minY + 1);
        long maxArea = Math.max(1000, ModConfig.Zones.maxBarrierTiles * 4L);
        if (area > maxArea) {
            ModLogger.debug("forceClean area too large (%d), skipping", area);
            return;
        }

        int removed = 0;
        for (int x = minX; x <= maxX; ++x) {
            for (int y = minY; y <= maxY; ++y) {
                try {
                    int existing = level.getObjectID(0, x, y);
                    if (existing != barrierID) {
                        continue;
                    }
                    boolean isEdge = false;
                    if (zones != null) {
                        for (PvPZone zone : zones) {
                            PointHashSet edge = zone.zoning.getEdgeTiles();
                            if (edge != null && edge.contains(x, y)) {
                                isEdge = true;
                                break;
                            }
                        }
                    }
                    if (!isEdge) {
                        level.setObject(x, y, 0);
                        server.network.sendToClientsWithTile((Packet) new PacketPlaceObject(level, null, 0, x, y, 0, 0, false, false), level, x, y);
                        removed++;
                    }
                } catch (Exception ignored) {
                    // ignore per-tile errors
                }
            }
        }
        ModLogger.debug("forceClean removed %d stray barriers around (%d,%d) radius %d", removed, centerTileX, centerTileY, radius);
    }

    public void notifyZoneChanged(Server server, PvPZone zone, boolean isProtectedZone) {
        if (server == null || zone == null) {
            return;
        }
        server.network.sendToAllClients((Packet) new medievalsim.packets.PacketZoneChanged(zone, isProtectedZone));
    }

    public void notifyZoneRemoved(Server server, int zoneId, boolean isProtectedZone) {
        if (server == null) {
            return;
        }
        server.network.sendToAllClients((Packet) new medievalsim.packets.PacketZoneRemoved(zoneId, isProtectedZone));
    }
}
