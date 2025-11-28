package medievalsim.zones.service;

import medievalsim.zones.domain.PvPZone;

import medievalsim.zones.domain.AdminZone;

import medievalsim.zones.domain.AdminZonesLevelData;

import java.awt.Point;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.ArrayList;

import medievalsim.util.ModLogger;
import necesse.engine.util.PointHashSet;
import necesse.level.maps.Level;
import necesse.level.maps.regionSystem.Region;

/**
 * Simple per-level placement queue that processes up to N tiles per tick.
 * Tasks are idempotent: they check existing objects before placing.
 */
public final class BarrierPlacementWorker {
    private static final Map<Level, Queue<PlacementTask>> queues = new ConcurrentHashMap<>();

    private BarrierPlacementWorker() {}

    public static void queueZoneRegionPlacement(Level level, AdminZone zone, Region region) {
        if (level == null || zone == null || region == null) return;
        Queue<PlacementTask> q = queues.computeIfAbsent(level, k -> new ConcurrentLinkedQueue<>());
        q.add(new PlacementTask(zone.uniqueID, region.regionX, region.regionY));
        ModLogger.debug("Queued barrier placement for zone '%s' region (%d,%d)", zone.name, region.regionX, region.regionY);
    }

    public static void processTick(Level level, int maxTilesPerTick) {
        if (level == null) return;
        Queue<PlacementTask> q = queues.get(level);
        if (q == null) return;

        int processed = 0;
        while (processed < maxTilesPerTick) {
            PlacementTask task = q.peek();
            if (task == null) break;
            int allowed = maxTilesPerTick - processed;
            int did = task.processUpTo(allowed, level);
            processed += did;
            if (task.isComplete()) q.poll();
            if (did == 0) break;
        }
        // Note: Processed X tiles - logging removed to reduce spam
    }

    /** Remove queued placement tasks for a specific zone on a level. */
    public static void removeQueuedTasksForZone(Level level, int zoneID) {
        if (level == null) return;
        Queue<PlacementTask> q = queues.get(level);
        if (q == null) return;
        try {
            q.removeIf(task -> task.zoneID == zoneID);
        } catch (Exception e) {
            // Broad catch protects concurrent queue operations from crashing game
            ModLogger.error("Failed to remove queued tasks for zone %s", e, zoneID);
        }
    }

    // Task that represents placing barriers for a single zone in a single region
    static final class PlacementTask {
        final int zoneID;
        final int regionX;
        final int regionY;
        // iterator state
        private Iterator<Point> remainingIterator = null;
        private boolean initialized = false;

        PlacementTask(int zoneID, int regionX, int regionY) {
            this.zoneID = zoneID;
            this.regionX = regionX;
            this.regionY = regionY;
        }

        private void init(Level level) {
            if (initialized) return;
            initialized = true;
            AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
            if (zoneData == null) return;
            PvPZone zone = zoneData.getPvPZone(this.zoneID);
            // If the zone no longer exists or is marked for removal, abort initialization so
            // the task will be considered complete and removed from the queue.
            if (zone == null || zone.shouldRemove()) {
                this.remainingIterator = null;
                return;
            }
            // compute list of edge tiles within this region
            PointHashSet edge = zone.zoning.getEdgeTiles();
            List<Point> points = new ArrayList<>();
            if (edge != null) {
                for (Object o : edge) {
                    if (o instanceof Point) {
                        Point p = (Point)o;
                        // region bounds check: convert tile to region coords
                        int rX = level.regionManager.getRegionXByTileLimited(p.x);
                        int rY = level.regionManager.getRegionYByTileLimited(p.y);
                        if (rX == this.regionX && rY == this.regionY) {
                            points.add(new Point(p.x, p.y));
                        }
                    }
                }
            }
            this.remainingIterator = points.iterator();
        }

        int processUpTo(int maxTiles, Level level) {
            if (!initialized) init(level);
            if (this.remainingIterator == null) return 0;
            int did = 0;
            while (did < maxTiles && this.remainingIterator.hasNext()) {
                Point p = this.remainingIterator.next();
                try {
                    PvPZone zone = AdminZonesLevelData.getZoneData(level, false).getPvPZone(this.zoneID);
                    // Safety: if the zone was removed while this task was processing, abort remaining work.
                    if (zone == null || zone.shouldRemove()) {
                        // mark as complete
                        this.remainingIterator = null;
                        return did;
                    }
                    // get barrier object id
                    int barrierID = PvPZoneBarrierManager.getBarrierObjectID();
                    if (barrierID == -1) break;
                    int existing = level.getObjectID(0, p.x, p.y);
                    if (existing == barrierID) {
                        // already placed
                    } else {
                        level.setObject(p.x, p.y, barrierID);
                        if (level.getServer() != null) {
                            // send single-tile object packet
                            level.getServer().network.sendToClientsWithTile(new necesse.engine.network.packet.PacketPlaceObject(level, null, 0, p.x, p.y, barrierID, 0, false, false), level, p.x, p.y);
                        }
                    }
                    did++;
                } catch (Exception e) {
                    ModLogger.error("Failed to place barrier tile", e);
                }
            }
            return did;
        }

        boolean isComplete() {
            return this.remainingIterator == null || !this.remainingIterator.hasNext();
        }
    }
}
