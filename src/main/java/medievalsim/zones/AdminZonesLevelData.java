
package medievalsim.zones;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import necesse.engine.network.server.ServerClient;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.GameMath;
import necesse.level.maps.Level;
import necesse.level.maps.regionSystem.Region;

import medievalsim.config.ModConfig;
import medievalsim.packets.PacketZoneChanged;
import medievalsim.packets.PacketZoneRemoved;
import medievalsim.util.ModLogger;
import necesse.engine.network.server.Server;
import necesse.engine.network.Packet;
import necesse.level.maps.levelData.LevelData;

public class AdminZonesLevelData
extends LevelData implements necesse.entity.manager.RegionLoadedListenerEntityComponent {
    private final Map<Integer, ProtectedZone> protectedZones = new HashMap<Integer, ProtectedZone>();
    private final Map<Integer, PvPZone> pvpZones = new HashMap<Integer, PvPZone>();
    private final AtomicInteger nextUniqueID = new AtomicInteger(1);
    private boolean hasCreatedInitialBarriers = false;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public ProtectedZone getProtectedZone(int uniqueID) {
        Map<Integer, ProtectedZone> map = this.protectedZones;
        synchronized (map) {
            return this.protectedZones.get(uniqueID);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public PvPZone getPvPZone(int uniqueID) {
        Map<Integer, PvPZone> map = this.pvpZones;
        synchronized (map) {
            return this.pvpZones.get(uniqueID);
        }
    }

    public AdminZone getZone(int uniqueID) {
        ProtectedZone zone = this.getProtectedZone(uniqueID);
        if (zone != null) {
            return zone;
        }
        return this.getPvPZone(uniqueID);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public Map<Integer, ProtectedZone> getProtectedZones() {
        Map<Integer, ProtectedZone> map = this.protectedZones;
        synchronized (map) {
            return new HashMap<Integer, ProtectedZone>(this.protectedZones);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public Map<Integer, PvPZone> getPvPZones() {
        Map<Integer, PvPZone> map = this.pvpZones;
        synchronized (map) {
            return new HashMap<Integer, PvPZone>(this.pvpZones);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void forEachProtectedZone(Consumer<ProtectedZone> action) {
        Map<Integer, ProtectedZone> map = this.protectedZones;
        synchronized (map) {
            this.protectedZones.values().forEach(action);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void forEachPvPZone(Consumer<PvPZone> action) {
        Map<Integer, PvPZone> map = this.pvpZones;
        synchronized (map) {
            this.pvpZones.values().forEach(action);
        }
    }

    public Map<Integer, ProtectedZone> getProtectedZonesInternal() {
        return this.protectedZones;
    }

    public Map<Integer, PvPZone> getPvPZonesInternal() {
        return this.pvpZones;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public ProtectedZone addProtectedZone(String name, long creatorAuth, int colorHue) {
        int uniqueID = this.nextUniqueID.getAndIncrement();
        ProtectedZone zone = new ProtectedZone(uniqueID, name, creatorAuth, colorHue);
        Map<Integer, ProtectedZone> map = this.protectedZones;
        synchronized (map) {
            this.protectedZones.put(uniqueID, zone);
        }
        return zone;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void putProtectedZone(ProtectedZone zone) {
        Map<Integer, ProtectedZone> map = this.protectedZones;
        synchronized (map) {
            this.protectedZones.put(zone.uniqueID, zone);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public PvPZone addPvPZone(String name, long creatorAuth, int colorHue) {
        int uniqueID = this.nextUniqueID.getAndIncrement();
        PvPZone zone = new PvPZone(uniqueID, name, creatorAuth, colorHue);
        Map<Integer, PvPZone> map = this.pvpZones;
        synchronized (map) {
            this.pvpZones.put(uniqueID, zone);
        }
        return zone;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void putPvPZone(PvPZone zone) {
        Map<Integer, PvPZone> map = this.pvpZones;
        synchronized (map) {
            this.pvpZones.put(zone.uniqueID, zone);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void removeProtectedZone(int uniqueID) {
        Map<Integer, ProtectedZone> map = this.protectedZones;
        synchronized (map) {
            ProtectedZone zone = this.protectedZones.get(uniqueID);
            if (zone != null) {
                zone.remove();
                this.protectedZones.remove(uniqueID);
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void removePvPZone(int uniqueID) {
        Map<Integer, PvPZone> map = this.pvpZones;
        synchronized (map) {
            PvPZone zone = this.pvpZones.get(uniqueID);
            if (zone != null) {
                // remove placed barriers and queued tasks before removing zone
                try { zone.removeBarriers(this.level); } catch (Exception ex) { /* best-effort */ }
                BarrierPlacementWorker.removeQueuedTasksForZone(this.level, uniqueID);
                zone.remove();
                this.pvpZones.remove(uniqueID);
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void clearProtectedZones() {
        Map<Integer, ProtectedZone> map = this.protectedZones;
        synchronized (map) {
            this.protectedZones.clear();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void clearPvPZones() {
        Map<Integer, PvPZone> map = this.pvpZones;
        synchronized (map) {
            this.pvpZones.clear();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public ProtectedZone getProtectedZoneAt(int tileX, int tileY) {
        Map<Integer, ProtectedZone> map = this.protectedZones;
        synchronized (map) {
            for (ProtectedZone zone : this.protectedZones.values()) {
                Rectangle bounds = zone.zoning.getTileBounds();
                if (bounds == null || !bounds.contains(tileX, tileY) || !zone.containsTile(tileX, tileY)) continue;
                return zone;
            }
        }
        return null;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public PvPZone getPvPZoneAt(float x, float y) {
        int tileX = GameMath.getTileCoordinate((int)((int)x));
        int tileY = GameMath.getTileCoordinate((int)((int)y));
        Map<Integer, PvPZone> map = this.pvpZones;
        synchronized (map) {
            for (PvPZone zone : this.pvpZones.values()) {
                Rectangle bounds = zone.zoning.getTileBounds();
                if (bounds == null || !bounds.contains(tileX, tileY) || !zone.containsTile(tileX, tileY)) continue;
                return zone;
            }
        }
        return null;
    }

    public boolean canClientModifyTile(ServerClient client, int tileX, int tileY) {
        ProtectedZone zone = this.getProtectedZoneAt(tileX, tileY);
        if (zone == null) {
            return true;
        }
        return zone.canClientModify(client, this.level);
    }

    public boolean areBothInPvPZone(float x1, float y1, float x2, float y2) {
        PvPZone zone1 = this.getPvPZoneAt(x1, y1);
        PvPZone zone2 = this.getPvPZoneAt(x2, y2);
        return zone1 != null && zone2 != null && zone1 == zone2;
    }

    public PvPZone getPvPZoneContainingBoth(float x1, float y1, float x2, float y2) {
        PvPZone zone1 = this.getPvPZoneAt(x1, y1);
        PvPZone zone2 = this.getPvPZoneAt(x2, y2);
        if (zone1 != null && zone2 != null && zone1 == zone2) {
            return zone1;
        }
        return null;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void tick() {
        super.tick();
        if (this.level != null && this.level.isServer() && !this.hasCreatedInitialBarriers) {
            this.hasCreatedInitialBarriers = true;
            java.util.List<PvPZone> snapshot = new java.util.ArrayList<>();
            Map<Integer, PvPZone> map = this.pvpZones;
            synchronized (map) {
                snapshot.addAll(this.pvpZones.values());
            }
            ModLogger.info("Creating initial barriers for %d PvP zones...", snapshot.size());
            for (PvPZone zone : snapshot) {
                try {
                    ModLogger.info("Creating barriers for PvP zone '%s' (ID: %d)", zone.name, zone.uniqueID);
                    zone.createBarriers(this.level);
                }
                catch (Exception e) {
                    // Broad catch ensures one zone's failure doesn't break all zones in tick
                    // Per-zone isolation prevents cascading failures during level loading
                    ModLogger.error("Error creating initial barriers for zone '" + (zone != null ? zone.name : "<null>") + "'", e);
                }
            }
        }
        
        // Update protected zone buffs for all players
        if (this.level != null && this.level.isServer() && this.level.getServer() != null) {
            for (ServerClient client : this.level.getServer().getClients()) {
                if (client != null && client.playerMob != null && client.playerMob.getLevel() == this.level) {
                    int tileX = GameMath.getTileCoordinate((int) client.playerMob.x);
                    int tileY = GameMath.getTileCoordinate((int) client.playerMob.y);
                    
                    // Update protected zone buff
                    ProtectedZone protectedZone = this.getProtectedZoneAt(tileX, tileY);
                    ProtectedZoneTracker.updatePlayerZone(client, protectedZone);
                    
                    // Update PvP zone damage reduction buff (use player coordinates, not tiles)
                    PvPZone pvpZone = this.getPvPZoneAt(client.playerMob.x, client.playerMob.y);
                    PvPZoneTracker.updatePlayerZoneBuff(client, pvpZone);
                }
            }
        }
        
    // Process queued barrier placements per tick (bounded per level)
    BarrierPlacementWorker.processTick(this.level, ModConfig.Zones.barrierMaxTilesPerTick);
    // Apply PvP zone DoT modifications (mod-only handler)
    try {
        if (this.level != null && this.level.isServer()) {
            PvPZoneDotHandler.processLevelTick(this.level);
        }
    } catch (Throwable t) {
        ModLogger.error("Error running PvPZoneDotHandler", t);
    }
    }

    @Override
    public void onRegionLoaded(Region region) {
        if (region == null || this.level == null || !this.level.isServer()) return;

        // For each PvP zone, check if any edge tile lies within this region; if so, queue placement for this region
        Map<Integer, PvPZone> map = this.pvpZones;
        synchronized (map) {
            for (PvPZone zone : this.pvpZones.values()) {
                try {
                    necesse.engine.util.PointHashSet edge = zone.zoning.getEdgeTiles();
                    if (edge == null || edge.isEmpty()) continue;
                    boolean intersects = false;
                    for (Object o : edge) {
                        if (!(o instanceof java.awt.Point)) continue;
                        java.awt.Point p = (java.awt.Point)o;
                        int rX = this.level.regionManager.getRegionXByTileLimited(p.x);
                        int rY = this.level.regionManager.getRegionYByTileLimited(p.y);
                        if (rX == region.regionX && rY == region.regionY) {
                            intersects = true;
                            break;
                        }
                    }
                    if (intersects) {
                        BarrierPlacementWorker.queueZoneRegionPlacement(this.level, zone, region);
                    }
                } catch (Exception e) {
                    ModLogger.error("Error queuing barrier placement on region load", e);
                }
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);
        ModLogger.debug("Saving AdminZonesLevelData - protected=%d pvp=%d nextID=%d",
            this.protectedZones.size(), this.pvpZones.size(), this.nextUniqueID.get());
        save.addInt("nextUniqueID", this.nextUniqueID.get());
        save.addBoolean("hasCreatedInitialBarriers", this.hasCreatedInitialBarriers);
        SaveData protectedSave = new SaveData("PROTECTED_ZONES");
        Map<Integer, ProtectedZone> map = this.protectedZones;
        synchronized (map) {
            for (ProtectedZone zone : this.protectedZones.values()) {
                if (zone.shouldRemove()) continue;
                SaveData zoneSave = new SaveData("ZONE");
                zone.addSaveData(zoneSave);
                protectedSave.addSaveData(zoneSave);
            }
        }
        save.addSaveData(protectedSave);
        SaveData pvpSave = new SaveData("PVP_ZONES");
        Map<Integer, PvPZone> map2 = this.pvpZones;
        synchronized (map2) {
            for (PvPZone zone : this.pvpZones.values()) {
                if (zone.shouldRemove()) continue;
                SaveData zoneSave = new SaveData("ZONE");
                zone.addSaveData(zoneSave);
                pvpSave.addSaveData(zoneSave);
            }
        }
        save.addSaveData(pvpSave);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void applyLoadData(LoadData save) {
        LoadData pvpSave;
        super.applyLoadData(save);
        ModLogger.debug("Loading AdminZonesLevelData - nextUniqueID(before)=%d", this.nextUniqueID.get());
        this.nextUniqueID.set(save.getInt("nextUniqueID", 1));
        this.hasCreatedInitialBarriers = save.getBoolean("hasCreatedInitialBarriers", false);
        LoadData protectedSave = save.getFirstLoadDataByName("PROTECTED_ZONES");
        if (protectedSave != null) {
            Map<Integer, ProtectedZone> map = this.protectedZones;
            synchronized (map) {
                this.protectedZones.clear();
                for (LoadData zoneSave : protectedSave.getLoadDataByName("ZONE")) {
                    ProtectedZone zone = new ProtectedZone();
                    zone.applyLoadData(zoneSave);
                    this.protectedZones.put(zone.uniqueID, zone);
                }
            }
        }
        if ((pvpSave = save.getFirstLoadDataByName("PVP_ZONES")) != null) {
            Map<Integer, PvPZone> map = this.pvpZones;
            synchronized (map) {
                this.pvpZones.clear();
                for (LoadData zoneSave : pvpSave.getLoadDataByName("ZONE")) {
                    PvPZone pvPZone = new PvPZone();
                    pvPZone.applyLoadData(zoneSave);
                    this.pvpZones.put(pvPZone.uniqueID, pvPZone);
                }
            }
        }
        ModLogger.debug("Loaded AdminZonesLevelData - protected=%d pvp=%d nextID=%d",
            this.protectedZones.size(), this.pvpZones.size(), this.nextUniqueID.get());
        int maxID = 0;
    Map<Integer, ProtectedZone> map = this.protectedZones;
        synchronized (map) {
            for (ProtectedZone protectedZone : this.protectedZones.values()) {
                maxID = Math.max(maxID, protectedZone.uniqueID);
            }
        }
        Map<Integer, PvPZone> map2 = this.pvpZones;
        synchronized (map2) {
            for (PvPZone pvPZone : this.pvpZones.values()) {
                maxID = Math.max(maxID, pvPZone.uniqueID);
            }
        }
        if (this.nextUniqueID.get() <= maxID) {
            this.nextUniqueID.set(maxID + 1);
            ModLogger.info("Corrected nextUniqueID from %d to %d", save.getInt("nextUniqueID", 1), maxID + 1);
        }
    }

    public static AdminZonesLevelData getAdminZonesData(Level level) {
        return AdminZonesLevelData.getZoneData(level, false);
    }

    public static AdminZonesLevelData getZoneData(Level level) {
        return AdminZonesLevelData.getZoneData(level, true);
    }

    public static AdminZonesLevelData getZoneData(Level level, boolean createIfNull) {
        if (level == null) {
            return null;
        }
        LevelData data = level.getLevelData("adminzonesdata");
        if (data instanceof AdminZonesLevelData) {
            return (AdminZonesLevelData)data;
        }
        if (createIfNull) {
            AdminZonesLevelData newData = new AdminZonesLevelData();
            level.addLevelData("adminzonesdata", (LevelData)newData);
            return newData;
        }
        return null;
    }

    /**
     * Splits a potentially disconnected admin zone into one zone per connected component.
     *
     * Intended usage:
     * - Call after editing a single zone's tiles (add/remove) to ensure its geometry
     *   remains 4-neighbor connected.
     *
     * Side effects:
     * - The original {@code zone} keeps the largest connected component (its zoning is replaced).
     * - For each additional component, a new zone of the same type (PvP / protected) is created and registered.
     * - No packets or barrier updates are triggered here; callers are responsible for syncing and barriers.
     *
     * @param zone   zone to validate and possibly split (must belong to this level)
     * @param level  level that owns the zones
     * @return list of zones whose tiles were modified or created; always includes the original zone
     */
    public java.util.List<AdminZone> splitZoneIfDisconnected(AdminZone zone, Level level) {
        java.util.List<AdminZone> affected = new java.util.ArrayList<>();
        if (zone == null || level == null) return affected;

        // Collect all tiles in the zone
        necesse.engine.util.PointTreeSet tiles = zone.zoning.getTiles();
        if (tiles == null || tiles.isEmpty()) {
            affected.add(zone);
            return affected;
        }

        // Build a set of unvisited points
        java.util.HashSet<java.awt.Point> unvisited = new java.util.HashSet<>();
        for (Object o : tiles) {
            if (o instanceof java.awt.Point) {
                java.awt.Point p = (java.awt.Point)o;
                unvisited.add(new java.awt.Point(p));
            }
        }

        java.util.List<java.util.List<java.awt.Point>> components = new java.util.ArrayList<>();

        // 4-neighbor directions
        int[] dx = new int[]{0,1,0,-1};
        int[] dy = new int[]{-1,0,1,0};

        while (!unvisited.isEmpty()) {
            java.awt.Point start = unvisited.iterator().next();
            java.util.LinkedList<java.awt.Point> queue = new java.util.LinkedList<>();
            java.util.List<java.awt.Point> comp = new java.util.ArrayList<>();
            queue.add(start);
            unvisited.remove(start);
            while (!queue.isEmpty()) {
                java.awt.Point cur = queue.removeFirst();
                comp.add(cur);
                for (int i = 0; i < 4; ++i) {
                    java.awt.Point n = new java.awt.Point(cur.x + dx[i], cur.y + dy[i]);
                    if (unvisited.remove(n)) {
                        queue.add(n);
                    }
                }
            }
            components.add(comp);
        }

        if (components.size() <= 1) {
            affected.add(zone);
            return affected; // nothing to split
        }

        // Find largest component (keep in original zone)
        int bestIdx = 0;
        int bestSize = 0;
        for (int i = 0; i < components.size(); ++i) {
            int s = components.get(i).size();
            if (s > bestSize) { bestSize = s; bestIdx = i; }
        }

        // Create new zones for other components
        for (int i = 0; i < components.size(); ++i) {
            if (i == bestIdx) continue;
            java.util.List<java.awt.Point> comp = components.get(i);
            // Create new zone of same type
            if (zone instanceof PvPZone) {
                PvPZone newZone = this.addPvPZone(getUniqueZoneName(), zone.creatorAuth, zone.colorHue);
                for (java.awt.Point p : comp) newZone.zoning.addTile(p.x, p.y);
                this.putPvPZone(newZone);
                affected.add(newZone);
            } else if (zone instanceof ProtectedZone) {
                ProtectedZone newZone = this.addProtectedZone(getUniqueZoneName(), zone.creatorAuth, zone.colorHue);
                for (java.awt.Point p : comp) newZone.zoning.addTile(p.x, p.y);
                this.putProtectedZone(newZone);
                affected.add(newZone);
            }
        }

        // Replace original zone's zoning with largest component
        java.util.List<java.awt.Point> mainComp = components.get(bestIdx);
        necesse.engine.util.Zoning newZoning = new necesse.engine.util.Zoning(true);
        for (java.awt.Point p : mainComp) newZoning.addTile(p.x, p.y);
        zone.zoning = newZoning;
        affected.add(zone);

        ModLogger.debug("Split zone '%s' into %d parts", zone.name, components.size());
        return affected;
    }

    /**
     * Centralized post-edit resolver for zone changes.
     *
     * Intended usage:
     * - Call after any mutation to a single zone's tiles (create / expand / shrink / delete),
     *   passing the edited zone as {@code targetZone}.
     *
     * Behavior:
     * - Finds same-type zones that touch or overlap the target and merges them into a single
     *   "winner" zone when necessary.
     * - Runs splitZoneIfDisconnected on the winner to remove holes and ensure 4-neighbor connectivity.
     * - For PvP zones, updates barrier objects using PvPZoneBarrierManager and BarrierPlacementWorker,
     *   optionally using {@code oldEdgesByZoneID} for differential removal.
     * - Notifies clients of changed / removed zones via PacketZoneChanged and PacketZoneRemoved
     *   when {@code server} is non-null.
     *
     * This method does not synchronously save the level; rely on autosave or an explicit
     * admin-triggered save.
     *
     * @param targetZone        zone that was edited
     * @param level             level owning the zones
     * @param server            server instance, or {@code null} when running in a non-networked context
     * @param isProtectedZone   true when resolving protected zones; false for PvP zones
     * @param oldEdgesByZoneID  optional snapshot of old edge tiles per zone id used for
     *                          PvP barrier differential updates; may be {@code null}
     * @return list of zones that were modified or created as part of the resolution
     */
    public java.util.List<AdminZone> resolveAfterZoneChange(AdminZone targetZone, Level level, Server server, boolean isProtectedZone, java.util.Map<Integer, java.util.Collection<java.awt.Point>> oldEdgesByZoneID) {
        java.util.List<AdminZone> result = new java.util.ArrayList<>();
        if (targetZone == null || level == null) return result;

        // 1) Find same-type zones that touch or overlap the targetZone (4-neighbor adjacency or overlap)
        java.util.Set<AdminZone> candidates = findMergeCandidates(targetZone, isProtectedZone);

        // If only target exists, just run split detection and queue placements
        if (candidates.size() <= 1) {
            java.util.List<AdminZone> affected = this.splitZoneIfDisconnected(targetZone, level);
            // queue placements and notify; also use provided old edges to perform differential updates when available
            for (AdminZone az : affected) {
                if (az instanceof PvPZone) {
                    // If we have snapshot of old edges for this zone, use differential update to remove obsolete barriers
                    java.util.Collection<java.awt.Point> oldEdges = oldEdgesByZoneID != null ? oldEdgesByZoneID.get(az.uniqueID) : null;
                    if (oldEdges != null) {
                        PvPZoneBarrierManager.updateBarrier(level, (PvPZone)az, oldEdges);
                    } else {
                        necesse.engine.util.PointHashSet edge = az.zoning.getEdgeTiles();
                        if (edge != null) {
                            for (Object o : edge) {
                                if (!(o instanceof java.awt.Point)) continue;
                                java.awt.Point p = (java.awt.Point)o;
                                necesse.level.maps.regionSystem.Region region = level.regionManager.getRegionByTile(p.x, p.y, false);
                                if (region != null) BarrierPlacementWorker.queueZoneRegionPlacement(level, (PvPZone)az, region);
                            }
                        }
                    }
                }
                if (server != null) server.network.sendToAllClients((Packet)new PacketZoneChanged(az, isProtectedZone));
                result.add(az);
            }
            // Avoid performing a heavy synchronous save here; rely on autosave or an explicit admin-triggered save
            return result;
        }

        // 2) We have multiple candidate zones -> merge them into a single winner zone (largest area)
        // Build union set of candidates (ensure targetZone included)
        java.util.Set<AdminZone> mergeSet = new java.util.HashSet<>(candidates);
        mergeSet.add(targetZone);

        // Compute sizes
        AdminZone winner = null;
        int bestSize = -1;
        for (AdminZone z : mergeSet) {
            int sz = z.zoning.getTiles() != null ? z.zoning.getTiles().size() : 0;
            if (sz > bestSize) { bestSize = sz; winner = z; }
        }
        if (winner == null) { return result; }

        // Merge: collect tiles from all into union
        java.util.Set<java.awt.Point> union = new java.util.HashSet<>();
        for (AdminZone z : mergeSet) {
            necesse.engine.util.PointTreeSet t = z.zoning.getTiles();
            if (t == null) continue;
            for (Object o : t) {
                if (!(o instanceof java.awt.Point)) continue;
                java.awt.Point p = (java.awt.Point)o;
                union.add(new java.awt.Point(p));
            }
        }

        // Build new zoning for winner
        necesse.engine.util.Zoning newZoning = new necesse.engine.util.Zoning(true);
        for (java.awt.Point p : union) newZoning.addTile(p.x, p.y);

        // Remove losers from maps (but keep winner)
        java.util.List<AdminZone> removed = new java.util.ArrayList<>();
        if (isProtectedZone) {
            Map<Integer, ProtectedZone> map = this.protectedZones;
            synchronized (map) {
                for (AdminZone z : new java.util.ArrayList<>(mergeSet)) {
                    if (z == winner) continue;
                    if (z instanceof ProtectedZone) {
                        ProtectedZone pz = (ProtectedZone)z;
                        pz.remove();
                        this.protectedZones.remove(pz.uniqueID);
                        removed.add(pz);
                    }
                }
            }
        } else {
            Map<Integer, PvPZone> map = this.pvpZones;
            synchronized (map) {
                for (AdminZone z : new java.util.ArrayList<>(mergeSet)) {
                    if (z == winner) continue;
                    if (z instanceof PvPZone) {
                        PvPZone pz = (PvPZone)z;
                        // ensure barriers are removed for losers and queued tasks cleared
                        try { pz.removeBarriers(level); } catch (Exception ex) { /* best-effort */ }
                        BarrierPlacementWorker.removeQueuedTasksForZone(level, pz.uniqueID);
                        this.pvpZones.remove(pz.uniqueID);
                        removed.add(pz);
                    }
                }
            }
        }

        // Assign new zoning to winner
        winner.zoning = newZoning;

        // Save/update winner in maps
        if (isProtectedZone) this.putProtectedZone((ProtectedZone)winner); else this.putPvPZone((PvPZone)winner);

        // Run split detection in case union introduced holes
        java.util.List<AdminZone> affected = this.splitZoneIfDisconnected(winner, level);

        // For winner, compute combined old edges from snapshot map (if present) for differential removal
        java.util.Collection<java.awt.Point> combinedOld = computeCombinedOldEdges(oldEdgesByZoneID, mergeSet);

        // Queue placements and notify for affected; use differential update for winner when possible
        for (AdminZone az : affected) {
            if (az instanceof PvPZone) {
                if (az == winner && combinedOld != null) {
                    PvPZoneBarrierManager.updateBarrier(level, (PvPZone)az, combinedOld);
                } else {
                    necesse.engine.util.PointHashSet edge = az.zoning.getEdgeTiles();
                    if (edge != null) {
                        for (Object o : edge) {
                            if (!(o instanceof java.awt.Point)) continue;
                            java.awt.Point p = (java.awt.Point)o;
                            necesse.level.maps.regionSystem.Region region = level.regionManager.getRegionByTile(p.x, p.y, false);
                            if (region != null) BarrierPlacementWorker.queueZoneRegionPlacement(level, (PvPZone)az, region);
                        }
                    }
                }
            }
            if (server != null) server.network.sendToAllClients((Packet)new PacketZoneChanged(az, isProtectedZone));
            result.add(az);
        }

        // Notify removals for deleted zones
        for (AdminZone rem : removed) {
            if (server != null) server.network.sendToAllClients((Packet)new PacketZoneRemoved(rem.uniqueID, isProtectedZone));
        }

        // Avoid performing a heavy synchronous save here; rely on autosave or an explicit admin-triggered save

        return result;
    }

    /**
     * Force-clean stray PvP barrier objects around a center tile (client action -> server-side).
     *
     * Intended usage:
     * - Called on the server in response to an admin client requesting a clean-up.
     *
     * Behavior / side effects:
     * - Scans tiles in a square radius around {@code (centerTileX, centerTileY)} (bounded by config).
     * - Removes barrier objects that are not part of any PvP zone edge.
     * - Sends PacketPlaceObject updates for each removed barrier tile.
     * - Logs how many barrier tiles were removed.
     *
     * The method is a no-op when called on a non-server level, when {@code server} is null,
     * or when the barrier object id cannot be resolved.
     *
     * @param level        server level to clean
     * @param centerTileX  center tile x-coordinate
     * @param centerTileY  center tile y-coordinate
     * @param radius       radius in tiles to scan (square area)
     * @param server       server instance used to broadcast object updates
     */
    public void forceCleanAround(Level level, int centerTileX, int centerTileY, int radius, Server server) {
        if (level == null || !level.isServer() || server == null) return;
        int barrierID = PvPZoneBarrierManager.getBarrierObjectID();
        if (barrierID == -1) return;

        int minX = centerTileX - radius;
        int minY = centerTileY - radius;
        int maxX = centerTileX + radius;
        int maxY = centerTileY + radius;

        // Cap area to avoid huge synchronous scans; respect ModConfig
        long area = (long)(maxX - minX + 1) * (long)(maxY - minY + 1);
        long maxArea = Math.max(1000, ModConfig.Zones.maxBarrierTiles * 4);
        if (area > maxArea) {
            ModLogger.debug("forceClean area too large (%d), skipping", area);
            return;
        }

        int removed = 0;
        Map<Integer, PvPZone> map = this.pvpZones;
        synchronized (map) {
            for (int x = minX; x <= maxX; ++x) {
                for (int y = minY; y <= maxY; ++y) {
                    try {
                        int existing = level.getObjectID(0, x, y);
                        if (existing != barrierID) continue;
                        boolean isEdge = false;
                        for (PvPZone zone : this.pvpZones.values()) {
                            necesse.engine.util.PointHashSet edge = zone.zoning.getEdgeTiles();
                            if (edge != null && edge.contains(x, y)) { isEdge = true; break; }
                        }
                        if (!isEdge) {
                            level.setObject(x, y, 0);
                            server.network.sendToClientsWithTile(new necesse.engine.network.packet.PacketPlaceObject(level, null, 0, x, y, 0, 0, false, false), level, x, y);
                            removed++;
                        }
                    } catch (Exception e) {
                        // ignore per-tile errors
                    }
                }
            }
        }
        ModLogger.debug("forceClean removed %d stray barriers around (%d,%d) radius %d",
            removed, centerTileX, centerTileY, radius);
    }


    /**
     * Finds same-type admin zones that either are the target zone itself or
     * touch/overlap it (4-neighbor adjacency or overlap).
     */
    private java.util.Set<AdminZone> findMergeCandidates(AdminZone targetZone, boolean isProtectedZone) {
        java.util.Set<AdminZone> candidates = new java.util.HashSet<>();
        if (targetZone == null) return candidates;
        if (isProtectedZone) {
            Map<Integer, ProtectedZone> map = this.protectedZones;
            synchronized (map) {
                for (ProtectedZone z : this.protectedZones.values()) {
                    if (z == targetZone) {
                        candidates.add(z);
                        continue;
                    }
                    if (areZonesAdjacentOrOverlapping(targetZone, z)) {
                        candidates.add(z);
                    }
                }
            }
        } else {
            Map<Integer, PvPZone> map = this.pvpZones;
            synchronized (map) {
                for (PvPZone z : this.pvpZones.values()) {
                    if (z == targetZone) {
                        candidates.add(z);
                        continue;
                    }
                    if (areZonesAdjacentOrOverlapping(targetZone, z)) {
                        candidates.add(z);
                    }
                }
            }
        }
        return candidates;
    }

    /**
     * Combines old edge tiles for all zones in the merge set into one collection
     * that can be passed to PvPZoneBarrierManager.updateBarrier.
     */
    private java.util.Collection<java.awt.Point> computeCombinedOldEdges(
            java.util.Map<Integer, java.util.Collection<java.awt.Point>> oldEdgesByZoneID,
            java.util.Set<AdminZone> mergeSet) {
        if (oldEdgesByZoneID == null || mergeSet == null || mergeSet.isEmpty()) return null;
        java.util.Collection<java.awt.Point> combinedOld = new java.util.ArrayList<>();
        for (AdminZone z : mergeSet) {
            java.util.Collection<java.awt.Point> col = oldEdgesByZoneID.get(z.uniqueID);
            if (col != null) combinedOld.addAll(col);
        }
        return combinedOld;
    }

    private boolean areZonesAdjacentOrOverlapping(AdminZone a, AdminZone b) {
        if (a == null || b == null) return false;
        necesse.engine.util.PointTreeSet ta = a.zoning.getTiles();
        necesse.engine.util.PointTreeSet tb = b.zoning.getTiles();
        if ((ta == null || ta.isEmpty()) || (tb == null || tb.isEmpty())) return false;
        // Quick bounding box test first
        Rectangle ra = a.zoning.getTileBounds();
        Rectangle rb = b.zoning.getTileBounds();
        if (ra == null || rb == null) return false;
        // expand ra by 1 tile in each direction to detect adjacency
        Rectangle expanded = new Rectangle(ra.x - 1, ra.y - 1, ra.width + 2, ra.height + 2);
        if (!expanded.intersects(rb)) return false;
        // Fallback: check if any tile in a is within 1 tile of any tile in b
        for (Object oa : ta) {
            if (!(oa instanceof java.awt.Point)) continue;
            java.awt.Point pa = (java.awt.Point)oa;
            for (Object ob : tb) {
                if (!(ob instanceof java.awt.Point)) continue;
                java.awt.Point pb = (java.awt.Point)ob;
                int dx = Math.abs(pa.x - pb.x);
                int dy = Math.abs(pa.y - pb.y);
                if (dx + dy <= 1) return true; // 4-neighbor adjacency or overlap
            }
        }
        return false;
    }

    public String getUniqueZoneName() {
        String base = "New Zone";
        int idx = 1;
        java.util.Set<String> names = new java.util.HashSet<>();
        Map<Integer, ProtectedZone> pmap = this.protectedZones;
        synchronized (pmap) { for (ProtectedZone z : this.protectedZones.values()) names.add(z.name); }
        Map<Integer, PvPZone> pvmap = this.pvpZones;
        synchronized (pvmap) { for (PvPZone z : this.pvpZones.values()) names.add(z.name); }
        while (names.contains(base + " " + idx)) idx++;
        return base + " " + idx;
    }
}

