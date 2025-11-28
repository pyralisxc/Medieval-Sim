package medievalsim.zones.service;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import medievalsim.zones.domain.AdminZone;
import medievalsim.zones.domain.ProtectedZone;
import medievalsim.zones.domain.PvPZone;
import necesse.engine.util.GameMath;

/**
 * Thread-safe repository for protected and PvP zones that centralizes
 * read/write access and unique ID generation.
 */
public class ZoneRepository {
    private final Map<Integer, ProtectedZone> protectedZones = new HashMap<>();
    private final Map<Integer, PvPZone> pvpZones = new HashMap<>();
    private final AtomicInteger nextUniqueID = new AtomicInteger(1);

    public ProtectedZone getProtectedZone(int uniqueID) {
        synchronized (protectedZones) {
            return protectedZones.get(uniqueID);
        }
    }

    public PvPZone getPvPZone(int uniqueID) {
        synchronized (pvpZones) {
            return pvpZones.get(uniqueID);
        }
    }

    public AdminZone getZone(int uniqueID) {
        ProtectedZone zone = getProtectedZone(uniqueID);
        if (zone != null) {
            return zone;
        }
        return getPvPZone(uniqueID);
    }

    public Map<Integer, ProtectedZone> copyProtectedZones() {
        synchronized (protectedZones) {
            return new HashMap<>(protectedZones);
        }
    }

    public Map<Integer, PvPZone> copyPvPZones() {
        synchronized (pvpZones) {
            return new HashMap<>(pvpZones);
        }
    }

    public void forEachProtectedZone(Consumer<ProtectedZone> action) {
        synchronized (protectedZones) {
            protectedZones.values().forEach(action);
        }
    }

    public void forEachPvPZone(Consumer<PvPZone> action) {
        synchronized (pvpZones) {
            pvpZones.values().forEach(action);
        }
    }

    public ProtectedZone addProtectedZone(String name, long creatorAuth, int colorHue) {
        int uniqueID = nextUniqueID.getAndIncrement();
        ProtectedZone zone = new ProtectedZone(uniqueID, name, creatorAuth, colorHue);
        synchronized (protectedZones) {
            protectedZones.put(uniqueID, zone);
        }
        return zone;
    }

    public void putProtectedZone(ProtectedZone zone) {
        synchronized (protectedZones) {
            protectedZones.put(zone.uniqueID, zone);
        }
    }

    public PvPZone addPvPZone(String name, long creatorAuth, int colorHue) {
        int uniqueID = nextUniqueID.getAndIncrement();
        PvPZone zone = new PvPZone(uniqueID, name, creatorAuth, colorHue);
        synchronized (pvpZones) {
            pvpZones.put(uniqueID, zone);
        }
        return zone;
    }

    public void putPvPZone(PvPZone zone) {
        synchronized (pvpZones) {
            pvpZones.put(zone.uniqueID, zone);
        }
    }

    public void removeProtectedZone(int uniqueID) {
        synchronized (protectedZones) {
            protectedZones.remove(uniqueID);
        }
    }

    public void removePvPZone(int uniqueID) {
        synchronized (pvpZones) {
            pvpZones.remove(uniqueID);
        }
    }

    public void clearProtectedZones() {
        synchronized (protectedZones) {
            protectedZones.clear();
        }
    }

    public void clearPvPZones() {
        synchronized (pvpZones) {
            pvpZones.clear();
        }
    }

    public void overwriteProtectedZones(Iterable<ProtectedZone> zones) {
        synchronized (protectedZones) {
            protectedZones.clear();
            if (zones != null) {
                for (ProtectedZone zone : zones) {
                    if (zone != null) {
                        protectedZones.put(zone.uniqueID, zone);
                    }
                }
            }
        }
    }

    public void overwritePvPZones(Iterable<PvPZone> zones) {
        synchronized (pvpZones) {
            pvpZones.clear();
            if (zones != null) {
                for (PvPZone zone : zones) {
                    if (zone != null) {
                        pvpZones.put(zone.uniqueID, zone);
                    }
                }
            }
        }
    }

    public ProtectedZone getProtectedZoneAt(int tileX, int tileY) {
        synchronized (protectedZones) {
            for (ProtectedZone zone : protectedZones.values()) {
                Rectangle bounds = zone.zoning.getTileBounds();
                if (bounds == null || !bounds.contains(tileX, tileY) || !zone.containsTile(tileX, tileY)) {
                    continue;
                }
                return zone;
            }
        }
        return null;
    }

    public PvPZone getPvPZoneAt(float x, float y) {
        int tileX = GameMath.getTileCoordinate((int) x);
        int tileY = GameMath.getTileCoordinate((int) y);
        synchronized (pvpZones) {
            for (PvPZone zone : pvpZones.values()) {
                Rectangle bounds = zone.zoning.getTileBounds();
                if (bounds == null || !bounds.contains(tileX, tileY) || !zone.containsTile(tileX, tileY)) {
                    continue;
                }
                return zone;
            }
        }
        return null;
    }

    public boolean areBothInSamePvPZone(float x1, float y1, float x2, float y2) {
        PvPZone zone1 = getPvPZoneAt(x1, y1);
        PvPZone zone2 = getPvPZoneAt(x2, y2);
        return zone1 != null && zone1 == zone2;
    }

    public PvPZone getPvPZoneContainingBoth(float x1, float y1, float x2, float y2) {
        PvPZone zone1 = getPvPZoneAt(x1, y1);
        PvPZone zone2 = getPvPZoneAt(x2, y2);
        if (zone1 != null && zone1 == zone2) {
            return zone1;
        }
        return null;
    }

    public int getNextUniqueIdValue() {
        return nextUniqueID.get();
    }

    public void setNextUniqueIdValue(int value) {
        nextUniqueID.set(value);
    }

    public void ensureNextIdAbove(int maxId) {
        if (nextUniqueID.get() <= maxId) {
            nextUniqueID.set(maxId + 1);
        }
    }

    public int computeHighestZoneId() {
        int maxID = 0;
        synchronized (protectedZones) {
            for (ProtectedZone zone : protectedZones.values()) {
                maxID = Math.max(maxID, zone.uniqueID);
            }
        }
        synchronized (pvpZones) {
            for (PvPZone zone : pvpZones.values()) {
                maxID = Math.max(maxID, zone.uniqueID);
            }
        }
        return maxID;
    }

    public List<PvPZone> snapshotPvPZones() {
        synchronized (pvpZones) {
            return new ArrayList<>(pvpZones.values());
        }
    }

    public List<ProtectedZone> snapshotProtectedZones() {
        synchronized (protectedZones) {
            return new ArrayList<>(protectedZones.values());
        }
    }

    public Map<Integer, ProtectedZone> getProtectedZonesInternal() {
        return protectedZones;
    }

    public Map<Integer, PvPZone> getPvPZonesInternal() {
        return pvpZones;
    }
}
