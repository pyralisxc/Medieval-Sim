package medievalsim.zones;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import necesse.engine.network.Packet;
import necesse.engine.network.packet.PacketPlaceObject;
import necesse.engine.network.server.Server;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.util.PointHashSet;
import necesse.engine.util.Zoning;
import necesse.level.maps.Level;

public class PvPZoneBarrierManager {
    private static int barrierObjectID = -1;

    public static int getBarrierObjectID() {
        if (barrierObjectID == -1) {
            barrierObjectID = ObjectRegistry.getObjectID((String)"pvpzonebarrier");
        }
        return barrierObjectID;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void createBarrier(Level level, PvPZone zone) {
        PointHashSet edgeTiles;
        if (!level.isServer()) {
            return;
        }
        Server server = level.getServer();
        if (server == null) {
            ModLogger.error("Cannot create barrier - server is null");
            return;
        }
        Zoning zoning = zone.zoning;
        synchronized (zoning) {
            edgeTiles = zone.zoning.getEdgeTiles();
            if (edgeTiles == null || edgeTiles.isEmpty()) {
                ModLogger.warn("Cannot create barrier for zone '%s' - no edge tiles", zone.name);
                return;
            }
        }
        PointHashSet barrierPositions = edgeTiles;
        ModLogger.debug("Creating %d barriers on edge tiles of zone '%s'", barrierPositions.size(), zone.name);
        if (barrierPositions.size() > ModConfig.Zones.maxBarrierTiles) {
            ModLogger.warn("Zone '%s' requires %d barriers! This is too large. Skipping barrier creation.", zone.name, barrierPositions.size());
            return;
        }
        int barrierID = PvPZoneBarrierManager.getBarrierObjectID();
        if (barrierID == -1) {
            ModLogger.error("Barrier object not registered!");
            return;
        }
        int placedCount = 0;
        int replacedCount = 0;
        int skippedUnloaded = 0;
        try {
            int batchPlaced = 0;
            int batchReplaced = 0;
            int processedInBatch = 0;
            int batchSize = ModConfig.Zones.barrierAddBatchSize;
            for (Point barrierPos : barrierPositions) {
                // Optimization: Skip barriers in unloaded regions to prevent unnecessary region loading
                if (!level.regionManager.isTileLoaded(barrierPos.x, barrierPos.y)) {
                    ++skippedUnloaded;
                    continue;
                }
                try {
                    int existingObjectID = level.getObjectID(0, barrierPos.x, barrierPos.y);
                    if (existingObjectID == barrierID) {
                        ++placedCount;
                        ++batchPlaced;
                    } else {
                        boolean replacing = existingObjectID != 0;
                        level.setObject(barrierPos.x, barrierPos.y, barrierID);
                        server.network.sendToClientsWithTile((Packet)new PacketPlaceObject(level, null, 0, barrierPos.x, barrierPos.y, barrierID, 0, false, false), level, barrierPos.x, barrierPos.y);
                        ++placedCount;
                        ++batchPlaced;
                        if (replacing) {
                            ++replacedCount;
                            ++batchReplaced;
                        }
                    }
                }
                catch (Exception tileException) {
                    ModLogger.error("Failed to place barrier at (%d, %d)", barrierPos.x, barrierPos.y);
                }
                processedInBatch++;
                if (processedInBatch >= batchSize) {
                    ModLogger.debug("Placed batch: %d barriers (replaced: %d) for zone '%s'", batchPlaced, batchReplaced, zone.name);
                    // yield briefly to avoid long continuous blocking
                    try {
                        Thread.sleep(1L);
                    }
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    processedInBatch = 0;
                    batchPlaced = 0;
                    batchReplaced = 0;
                }
            }
            if (processedInBatch > 0) {
                ModLogger.debug("Placed final batch: %d barriers (replaced: %d) for zone '%s'", batchPlaced, batchReplaced, zone.name);
            }
            if (skippedUnloaded > 0) {
                ModLogger.debug("Skipped %d barriers in unloaded regions for zone '%s' (optimization)", skippedUnloaded, zone.name);
            }
            if (replacedCount > 0) {
                ModLogger.debug("Created %d barriers for PVP zone '%s' (replaced %d existing objects)", placedCount, zone.name, replacedCount);
            } else {
                ModLogger.debug("Created %d barriers for PVP zone '%s'", placedCount, zone.name);
            }
        }
        catch (Exception e) {
            // Broad catch is intentional: barrier placement must never crash game loop
            // Can fail from: region loading, level access, object placement, concurrent modification
            ModLogger.error("Error creating barriers for zone '" + zone.name + "'", e);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void removeBarrier(Level level, PvPZone zone) {
        PointHashSet edgeTiles;
        if (!level.isServer()) {
            return;
        }
        Server server = level.getServer();
        if (server == null) {
            return;
        }
        int barrierID = PvPZoneBarrierManager.getBarrierObjectID();
        if (barrierID == -1) {
            ModLogger.error("Barrier object not registered!");
            return;
        }
        Zoning zoning = zone.zoning;
        synchronized (zoning) {
            edgeTiles = zone.zoning.getEdgeTiles();
            if (edgeTiles == null || edgeTiles.isEmpty()) {
                ModLogger.info("No edge tiles for zone '%s', cannot remove barriers", zone.name);
                return;
            }
        }
        ModLogger.debug("Removing barriers from %d edge tiles of zone '%s'", edgeTiles.size(), zone.name);
        int removedCount = 0;
        int skippedUnloaded = 0;
        for (Point pos : edgeTiles) {
            // Optimization: Skip barriers in unloaded regions
            if (!level.regionManager.isTileLoaded(pos.x, pos.y)) {
                ++skippedUnloaded;
                continue;
            }
            try {
                int existingObjectID = level.getObjectID(0, pos.x, pos.y);
                if (existingObjectID != barrierID) continue;
                level.setObject(pos.x, pos.y, 0);
                server.network.sendToClientsWithTile((Packet)new PacketPlaceObject(level, null, 0, pos.x, pos.y, 0, 0, false, false), level, pos.x, pos.y);
                ++removedCount;
            }
            catch (Exception e) {
                ModLogger.error("Failed to remove barrier at (%d, %d): %s", pos.x, pos.y, e.getMessage());
            }
        }
        if (skippedUnloaded > 0) {
            ModLogger.debug("Skipped %d barriers in unloaded regions for zone '%s' (optimization)", skippedUnloaded, zone.name);
        }
        ModLogger.debug("Removed %d barriers for zone '%s'", removedCount, zone.name);
    }

    public static void updateBarrier(Level level, PvPZone zone) {
        if (!level.isServer()) {
            return;
        }
        PvPZoneBarrierManager.removeBarrier(level, zone);
        PvPZoneBarrierManager.createBarrier(level, zone);
    }

    /**
     * Update barriers using a diff between the provided old edge positions and the
     * current edge tiles in the zone. This avoids failing to remove barriers that
     * were on tiles that are no longer edges after a shrink operation.
     */
    public static void updateBarrier(Level level, PvPZone zone, java.util.Collection<Point> oldEdgePositions) {
        if (!level.isServer()) {
            return;
        }
        Server server = level.getServer();
        if (server == null) {
            return;
        }
        int barrierID = PvPZoneBarrierManager.getBarrierObjectID();
        if (barrierID == -1) {
            ModLogger.error("Barrier object not registered!");
            return;
        }

        Zoning zoning = zone.zoning;
        PointHashSet newEdgeTiles;
        synchronized (zoning) {
            newEdgeTiles = zone.zoning.getEdgeTiles();
            if (newEdgeTiles == null) {
                newEdgeTiles = new PointHashSet();
            }
        }

        Set<Point> newSet = new HashSet<>();
        for (Point p : newEdgeTiles) {
            newSet.add(new Point(p));
        }

        Set<Point> oldSet = new HashSet<>();
        if (oldEdgePositions != null) {
            for (Point p : oldEdgePositions) {
                oldSet.add(new Point(p));
            }
        }

        // Positions to remove = oldSet - newSet
        Set<Point> toRemove = new HashSet<>(oldSet);
        toRemove.removeAll(newSet);

        // Additionally: scan the zone's bounding box for stray barrier objects that are
        // not part of the new edge set. This catches corrupted barriers that are no
        // longer edge tiles (inside/outside). Do this in a limited, batched manner to
        // avoid huge synchronous scans.
        try {
            Rectangle bounds = zone.zoning.getTileBounds();
            if (bounds != null) {
                // Expand bounds by 1 to catch nearby stray tiles
                Rectangle sweep = new Rectangle(bounds.x - 1, bounds.y - 1, bounds.width + 2, bounds.height + 2);
                long area = (long)sweep.width * (long)sweep.height;
                // only sweep if area is reasonably bounded
                long maxArea = Math.max(1000, ModConfig.Zones.maxBarrierTiles * 4);
                if (area <= maxArea) {
                    for (int sx = sweep.x; sx < sweep.x + sweep.width; ++sx) {
                        for (int sy = sweep.y; sy < sweep.y + sweep.height; ++sy) {
                            try {
                                int existing = level.getObjectID(0, sx, sy);
                                if (existing != barrierID) continue;
                                Point p = new Point(sx, sy);
                                // If this tile is in the new edge set, keep it
                                if (newSet.contains(p)) continue;
                                // If this tile is an edge of any other PvP zone, keep it
                                boolean isEdgeOfOther = false;
                                AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
                                if (zoneData != null) {
                                    Map<Integer, PvPZone> map = zoneData.getPvPZonesInternal();
                                    synchronized (map) {
                                        for (PvPZone other : map.values()) {
                                            if (other.uniqueID == zone.uniqueID) continue;
                                            necesse.engine.util.PointHashSet otherEdge = other.zoning.getEdgeTiles();
                                            if (otherEdge != null && otherEdge.contains(p.x, p.y)) { isEdgeOfOther = true; break; }
                                        }
                                    }
                                }
                                if (!isEdgeOfOther) toRemove.add(p);
                            } catch (Exception e) {
                                // ignore per-tile errors
                            }
                        }
                    }
                } else {
                    ModLogger.info("Skipping bbox sweep for zone '%s' (area too large: %d)", zone.name, area);
                }
            }
        } catch (Exception sweepEx) {
            ModLogger.error("Error during bbox sweep for zone '" + zone.name + "'", sweepEx);
        }

        // Positions to add = newSet - oldSet
        Set<Point> toAdd = new HashSet<>(newSet);
        toAdd.removeAll(oldSet);

        int removedCount = 0;
        int addedCount = 0;

        try {
            // Remove obsolete barriers in batches
            int processedRemove = 0;
            int batchSize = ModConfig.Zones.barrierAddBatchSize;
            int batchRemoved = 0;
            for (Point pos : toRemove) {
                try {
                    int existingObjectID = level.getObjectID(0, pos.x, pos.y);
                    if (existingObjectID == barrierID) {
                        level.setObject(pos.x, pos.y, 0);
                        server.network.sendToClientsWithTile((Packet)new PacketPlaceObject(level, null, 0, pos.x, pos.y, 0, 0, false, false), level, pos.x, pos.y);
                        ++removedCount;
                        ++batchRemoved;
                    }
                }
                catch (Exception e) {
                    ModLogger.error("Failed to remove barrier at (%d, %d): %s", pos.x, pos.y, e.getMessage());
                }
                processedRemove++;
                if (processedRemove >= batchSize) {
                    ModLogger.info("Removed batch: %d barriers for zone '%s'", batchRemoved, zone.name);
                    try {
                        Thread.sleep(1L);
                    }
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    processedRemove = 0;
                    batchRemoved = 0;
                }
            }
            if (processedRemove > 0) {
                ModLogger.info("Removed final batch: %d barriers for zone '%s'", batchRemoved, zone.name);
            }

            // Add missing barriers
            if (toAdd.size() > ModConfig.Zones.maxBarrierTiles) {
                ModLogger.warn("Zone '%s' requires %d barriers to add! This is too large. Skipping barrier creation.", zone.name, toAdd.size());
            } else {
                int processedAdd = 0;
                int batchAdded = 0;
                int batchReplaced = 0;
                for (Point pos : toAdd) {
                    try {
                        int existingObjectID = level.getObjectID(0, pos.x, pos.y);
                        if (existingObjectID == barrierID) {
                            ++addedCount;
                            ++batchAdded;
                        } else {
                            boolean replacing = existingObjectID != 0;
                            level.setObject(pos.x, pos.y, barrierID);
                            server.network.sendToClientsWithTile((Packet)new PacketPlaceObject(level, null, 0, pos.x, pos.y, barrierID, 0, false, false), level, pos.x, pos.y);
                            ++addedCount;
                            ++batchAdded;
                            if (replacing) {
                                ++batchReplaced;
                            }
                        }
                    }
                    catch (Exception tileException) {
                        ModLogger.error("Failed to place barrier at (%d, %d): %s", pos.x, pos.y, tileException.getMessage());
                    }
                    processedAdd++;
                    if (processedAdd >= batchSize) {
                        ModLogger.info("Added batch: %d barriers (replaced: %d) for zone '%s'", batchAdded, batchReplaced, zone.name);
                        try {
                            Thread.sleep(1L);
                        }
                        catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        processedAdd = 0;
                        batchAdded = 0;
                        batchReplaced = 0;
                    }
                }
                if (processedAdd > 0) {
                    ModLogger.info("Added final batch: %d barriers (replaced: %d) for zone '%s'", batchAdded, batchReplaced, zone.name);
                }
            }

            ModLogger.info("Updated barriers for PVP zone '%s' (added %d, removed %d)", zone.name, addedCount, removedCount);
        }
        catch (Exception e) {
            ModLogger.error("Error updating barriers for zone '" + zone.name + "'", e);
        }

        // Reconcile players who may have been affected by the barrier change:
        try {
            // Iterate all connected clients on this server and ensure their PvPTracker state matches their position
            for (necesse.engine.network.server.ServerClient playerClient : server.getClients()) {
                if (playerClient == null || playerClient.playerMob == null) continue;
                PvPZoneTracker.PlayerPvPState state = PvPZoneTracker.getPlayerState(playerClient);
                boolean previouslyInZone = state.currentZoneID == zone.uniqueID;
                boolean nowInZone = zone.containsPosition(playerClient.playerMob.x, playerClient.playerMob.y);

                long serverTime = server.world.worldEntity.getTime();

                if (previouslyInZone && !nowInZone) {
                    // Player was in the zone but now outside -> remove from zone
                    PvPZoneTracker.exitZone(playerClient, serverTime);
                    if (playerClient.pvpEnabled && !server.world.settings.forcedPvP) {
                        playerClient.pvpEnabled = false;
                        server.network.sendToAllClients((Packet)new necesse.engine.network.packet.PacketPlayerPvP(playerClient.slot, false));
                    }
                    playerClient.sendChatMessage(necesse.engine.localization.Localization.translate("message", "zone.pvp.removed"));
                    // Try to place player on nearest outside tile
                    java.awt.Point outsideTile = PvPZoneTracker.findClosestTileOutsideZone(zone, playerClient.playerMob.x, playerClient.playerMob.y);
                    if (outsideTile != null) {
                        float nx = outsideTile.x * 32 + 16;
                        float ny = outsideTile.y * 32 + 16;
                        playerClient.playerMob.dx = 0.0f;
                        playerClient.playerMob.dy = 0.0f;
                        playerClient.playerMob.setPos(nx, ny, true);
                        server.network.sendToClientsWithEntity((Packet)new necesse.engine.network.packet.PacketPlayerMovement(playerClient, true), (necesse.level.maps.regionSystem.RegionPositionGetter)playerClient.playerMob);
                    }
                } else if (!previouslyInZone && nowInZone) {
                    // Player has been encompassed by the zone expansion -> enter the zone
                    if (PvPZoneTracker.canReEnter(playerClient, serverTime)) {
                        PvPZoneTracker.enterZone(playerClient, zone);
                        if (!playerClient.pvpEnabled && !server.world.settings.forcedPvP) {
                            playerClient.pvpEnabled = true;
                            server.network.sendToAllClients((Packet)new necesse.engine.network.packet.PacketPlayerPvP(playerClient.slot, true));
                        }
                        if (playerClient.playerMob != null) {
                            playerClient.playerMob.addBuff(new necesse.entity.mobs.buffs.ActiveBuff("pvpimmunity", (necesse.entity.mobs.Mob)playerClient.playerMob, 5.0f, null), true);
                        }
                        playerClient.sendChatMessage(necesse.engine.localization.Localization.translate("message", "zone.pvp.entered"));
                    } else {
                        // cannot re-enter yet; optionally teleport to nearest in-zone tile later or prompt
                        playerClient.sendChatMessage(necesse.engine.localization.Localization.translate("message", "zone.pvp.cooldown"));
                    }
                }
            }
        }
        catch (Exception reconcileEx) {
            ModLogger.error("Error reconciling players after barrier update for zone '" + zone.name + "'", reconcileEx);
        }
    }
}

