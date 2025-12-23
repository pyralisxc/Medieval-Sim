
package medievalsim.zones.domain;

import java.util.Map;
import java.util.function.Consumer;
import necesse.engine.network.server.ServerClient;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.level.maps.Level;
import necesse.level.maps.regionSystem.Region;

import medievalsim.util.ModLogger;
import necesse.engine.network.server.Server;
import necesse.level.maps.levelData.LevelData;
import medievalsim.zones.service.PvPBarrierService;
import medievalsim.zones.service.ZoneEffectsService;
import medievalsim.zones.service.ZoneRepository;
import medievalsim.zones.service.ZoneTopologyResolver;

public class AdminZonesLevelData
extends LevelData implements necesse.entity.manager.RegionLoadedListenerEntityComponent {
    private final ZoneRepository repository = new ZoneRepository();
    private final PvPBarrierService barrierService = new PvPBarrierService();
    private final ZoneEffectsService effectsService = new ZoneEffectsService(repository, barrierService);
    private final ZoneTopologyResolver topologyResolver = new ZoneTopologyResolver(repository, barrierService, this::getUniqueZoneName);
    private boolean hasCreatedInitialBarriers = false;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public ProtectedZone getProtectedZone(int uniqueID) {
        return repository.getProtectedZone(uniqueID);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public PvPZone getPvPZone(int uniqueID) {
        return repository.getPvPZone(uniqueID);
    }

    public GuildZone getGuildZone(int uniqueID) {
        return repository.getGuildZone(uniqueID);
    }

    public AdminZone getZone(int uniqueID) {
        return repository.getZone(uniqueID);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public Map<Integer, ProtectedZone> getProtectedZones() {
        return repository.copyProtectedZones();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public Map<Integer, PvPZone> getPvPZones() {
        return repository.copyPvPZones();
    }

    public Map<Integer, GuildZone> getGuildZones() {
        return repository.copyGuildZones();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void forEachProtectedZone(Consumer<ProtectedZone> action) {
        repository.forEachProtectedZone(action);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void forEachPvPZone(Consumer<PvPZone> action) {
        repository.forEachPvPZone(action);
    }

    public void forEachGuildZone(Consumer<GuildZone> action) {
        repository.forEachGuildZone(action);
    }

    public Map<Integer, ProtectedZone> getProtectedZonesInternal() {
        return repository.getProtectedZonesInternal();
    }

    public Map<Integer, PvPZone> getPvPZonesInternal() {
        return repository.getPvPZonesInternal();
    }

    public Map<Integer, GuildZone> getGuildZonesInternal() {
        return repository.getGuildZonesInternal();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public ProtectedZone addProtectedZone(String name, long creatorAuth, int colorHue) {
        return repository.addProtectedZone(name, creatorAuth, colorHue);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void putProtectedZone(ProtectedZone zone) {
        repository.putProtectedZone(zone);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public PvPZone addPvPZone(String name, long creatorAuth, int colorHue) {
        return repository.addPvPZone(name, creatorAuth, colorHue);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void putPvPZone(PvPZone zone) {
        repository.putPvPZone(zone);
    }

    public GuildZone addGuildZone(String name, long creatorAuth, int colorHue) {
        return repository.addGuildZone(name, creatorAuth, colorHue);
    }

    public void putGuildZone(GuildZone zone) {
        repository.putGuildZone(zone);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void removeProtectedZone(int uniqueID) {
        ProtectedZone zone = repository.getProtectedZone(uniqueID);
        if (zone != null) {
            zone.remove();
            repository.removeProtectedZone(uniqueID);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void removePvPZone(int uniqueID) {
        PvPZone zone = repository.getPvPZone(uniqueID);
        if (zone != null) {
            barrierService.removeZoneArtifacts(this.level, zone);
            zone.remove();
            repository.removePvPZone(uniqueID);
        }
    }

    public void removeGuildZone(int uniqueID) {
        GuildZone zone = repository.getGuildZone(uniqueID);
        if (zone != null) {
            zone.remove();
            repository.removeGuildZone(uniqueID);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void clearProtectedZones() {
        repository.clearProtectedZones();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void clearPvPZones() {
        repository.clearPvPZones();
    }

    public void clearGuildZones() {
        repository.clearGuildZones();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public ProtectedZone getProtectedZoneAt(int tileX, int tileY) {
        return repository.getProtectedZoneAt(tileX, tileY);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public PvPZone getPvPZoneAt(float x, float y) {
        return repository.getPvPZoneAt(x, y);
    }

    public GuildZone getGuildZoneAt(int tileX, int tileY) {
        return repository.getGuildZoneAt(tileX, tileY);
    }

    public GuildZone getGuildZoneAt(float x, float y) {
        return repository.getGuildZoneAt(x, y);
    }

    public boolean canClientModifyTile(ServerClient client, int tileX, int tileY) {
        // Check protected zones first
        ProtectedZone protectedZone = this.getProtectedZoneAt(tileX, tileY);
        if (protectedZone != null && !protectedZone.canClientModify(client, this.level)) {
            return false;
        }
        
        // Check guild zones
        GuildZone guildZone = this.getGuildZoneAt(tileX, tileY);
        if (guildZone != null && !guildZone.canClientModify(client, this.level)) {
            return false;
        }
        
        return true;
    }

    public boolean areBothInPvPZone(float x1, float y1, float x2, float y2) {
        return repository.areBothInSamePvPZone(x1, y1, x2, y2);
    }

    public PvPZone getPvPZoneContainingBoth(float x1, float y1, float x2, float y2) {
        return repository.getPvPZoneContainingBoth(x1, y1, x2, y2);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void tick() {
        super.tick();
        if (this.level == null) {
            return;
        }
        this.hasCreatedInitialBarriers = effectsService.ensureInitialBarriers(this.level, this.hasCreatedInitialBarriers);
        effectsService.updatePlayerEffects(this.level);
        effectsService.processTick(this.level);
    }

    @Override
    public void onRegionLoaded(Region region) {
        if (region == null || this.level == null || !this.level.isServer()) return;
        effectsService.onRegionLoaded(this.level, region);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);
        int protectedCount;
        int pvpCount;
        int guildCount;
        Map<Integer, ProtectedZone> protectedMap = repository.getProtectedZonesInternal();
        synchronized (protectedMap) {
            protectedCount = protectedMap.size();
        }
        Map<Integer, PvPZone> pvpMap = repository.getPvPZonesInternal();
        synchronized (pvpMap) {
            pvpCount = pvpMap.size();
        }
        Map<Integer, GuildZone> guildMap = repository.getGuildZonesInternal();
        synchronized (guildMap) {
            guildCount = guildMap.size();
        }
        ModLogger.debug("Saving AdminZonesLevelData - protected=%d pvp=%d guild=%d nextID=%d",
            protectedCount, pvpCount, guildCount, repository.getNextUniqueIdValue());
        save.addInt("nextUniqueID", repository.getNextUniqueIdValue());
        save.addBoolean("hasCreatedInitialBarriers", this.hasCreatedInitialBarriers);
        SaveData protectedSave = new SaveData("PROTECTED_ZONES");
        repository.forEachProtectedZone(zone -> {
            if (zone.shouldRemove()) return;
            SaveData zoneSave = new SaveData("ZONE");
            zone.addSaveData(zoneSave);
            protectedSave.addSaveData(zoneSave);
        });
        save.addSaveData(protectedSave);
        SaveData pvpSave = new SaveData("PVP_ZONES");
        repository.forEachPvPZone(zone -> {
            if (zone.shouldRemove()) return;
            SaveData zoneSave = new SaveData("ZONE");
            zone.addSaveData(zoneSave);
            pvpSave.addSaveData(zoneSave);
        });
        save.addSaveData(pvpSave);
        SaveData guildSave = new SaveData("GUILD_ZONES");
        repository.forEachGuildZone(zone -> {
            if (zone.shouldRemove()) return;
            SaveData zoneSave = new SaveData("ZONE");
            zone.addSaveData(zoneSave);
            guildSave.addSaveData(zoneSave);
        });
        save.addSaveData(guildSave);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void applyLoadData(LoadData save) {
        LoadData pvpSave;
        super.applyLoadData(save);
        ModLogger.debug("Loading AdminZonesLevelData - nextUniqueID(before)=%d", repository.getNextUniqueIdValue());
        repository.setNextUniqueIdValue(save.getInt("nextUniqueID", 1));
        this.hasCreatedInitialBarriers = save.getBoolean("hasCreatedInitialBarriers", false);
        LoadData protectedSave = save.getFirstLoadDataByName("PROTECTED_ZONES");
        if (protectedSave != null) {
            java.util.List<ProtectedZone> loaded = new java.util.ArrayList<>();
            for (LoadData zoneSave : protectedSave.getLoadDataByName("ZONE")) {
                ProtectedZone zone = new ProtectedZone();
                zone.applyLoadData(zoneSave);
                loaded.add(zone);
            }
            repository.overwriteProtectedZones(loaded);
        }
        if ((pvpSave = save.getFirstLoadDataByName("PVP_ZONES")) != null) {
            java.util.List<PvPZone> loaded = new java.util.ArrayList<>();
            for (LoadData zoneSave : pvpSave.getLoadDataByName("ZONE")) {
                PvPZone zone = new PvPZone();
                zone.applyLoadData(zoneSave);
                loaded.add(zone);
            }
            repository.overwritePvPZones(loaded);
        }
        LoadData guildSave = save.getFirstLoadDataByName("GUILD_ZONES");
        if (guildSave != null) {
            java.util.List<GuildZone> loaded = new java.util.ArrayList<>();
            for (LoadData zoneSave : guildSave.getLoadDataByName("ZONE")) {
                GuildZone zone = new GuildZone();
                zone.applyLoadData(zoneSave);
                loaded.add(zone);
            }
            repository.overwriteGuildZones(loaded);
        }
        int protectedCount;
        int pvpCount;
        int guildCount;
        Map<Integer, ProtectedZone> protectedMap = repository.getProtectedZonesInternal();
        synchronized (protectedMap) {
            protectedCount = protectedMap.size();
        }
        Map<Integer, PvPZone> pvpMap = repository.getPvPZonesInternal();
        synchronized (pvpMap) {
            pvpCount = pvpMap.size();
        }
        Map<Integer, GuildZone> guildMap = repository.getGuildZonesInternal();
        synchronized (guildMap) {
            guildCount = guildMap.size();
        }
        ModLogger.debug("Loaded AdminZonesLevelData - protected=%d pvp=%d guild=%d nextID=%d",
            protectedCount, pvpCount, guildCount, repository.getNextUniqueIdValue());
        int maxID = repository.computeHighestZoneId();
        int originalNextId = repository.getNextUniqueIdValue();
        repository.ensureNextIdAbove(maxID);
        if (repository.getNextUniqueIdValue() != originalNextId) {
            ModLogger.info("Corrected nextUniqueID from %d to %d", originalNextId, repository.getNextUniqueIdValue());
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
        return topologyResolver.splitZoneIfDisconnected(zone, level);
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
    public java.util.List<AdminZone> resolveAfterZoneChange(AdminZone targetZone, Level level, Server server, ZoneType zoneType, java.util.Map<Integer, java.util.Collection<java.awt.Point>> oldEdgesByZoneID) {
        return topologyResolver.resolveAfterZoneChange(targetZone, level, server, zoneType, oldEdgesByZoneID);
    }

    /**
     * Legacy method for backward compatibility.
     * @deprecated Use {@link #resolveAfterZoneChange(AdminZone, Level, Server, ZoneType, Map)} instead
     */
    @Deprecated
    public java.util.List<AdminZone> resolveAfterZoneChange(AdminZone targetZone, Level level, Server server, boolean isProtectedZone, java.util.Map<Integer, java.util.Collection<java.awt.Point>> oldEdgesByZoneID) {
        ZoneType zoneType = isProtectedZone ? ZoneType.PROTECTED : ZoneType.PVP;
        return resolveAfterZoneChange(targetZone, level, server, zoneType, oldEdgesByZoneID);
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
        barrierService.forceCleanAround(level, centerTileX, centerTileY, radius, server, repository.snapshotPvPZones());
    }



    public String getUniqueZoneName() {
        String base = "New Zone";
        int idx = 1;
        java.util.Set<String> names = new java.util.HashSet<>();
        Map<Integer, ProtectedZone> pmap = repository.getProtectedZonesInternal();
        synchronized (pmap) { for (ProtectedZone z : pmap.values()) names.add(z.name); }
        Map<Integer, PvPZone> pvmap = repository.getPvPZonesInternal();
        synchronized (pvmap) { for (PvPZone z : pvmap.values()) names.add(z.name); }
        Map<Integer, GuildZone> gmap = repository.getGuildZonesInternal();
        synchronized (gmap) { for (GuildZone z : gmap.values()) names.add(z.name); }
        while (names.contains(base + " " + idx)) idx++;
        return base + " " + idx;
    }
}

