package medievalsim.zones.service;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import medievalsim.packets.PacketZoneChanged;
import medievalsim.packets.PacketZoneRemoved;
import medievalsim.util.ModLogger;
import medievalsim.zones.domain.AdminZone;
import medievalsim.zones.domain.GuildZone;
import medievalsim.zones.domain.ProtectedZone;
import medievalsim.zones.domain.PvPZone;
import medievalsim.zones.domain.ZoneType;
import necesse.engine.network.Packet;
import necesse.engine.network.server.Server;
import necesse.engine.util.PointTreeSet;
import necesse.engine.util.Zoning;
import necesse.level.maps.Level;

/**
 * Handles topology operations such as split/merge along with barrier updates
 * and packet notifications.
 */
public class ZoneTopologyResolver {

    private final ZoneRepository repository;
    private final PvPBarrierService barrierService;
    private final Supplier<String> uniqueNameSupplier;

    public ZoneTopologyResolver(ZoneRepository repository, PvPBarrierService barrierService, Supplier<String> uniqueNameSupplier) {
        this.repository = repository;
        this.barrierService = barrierService;
        this.uniqueNameSupplier = uniqueNameSupplier;
    }

    public List<AdminZone> splitZoneIfDisconnected(AdminZone zone, Level level) {
        List<AdminZone> affected = new ArrayList<>();
        if (zone == null || level == null) {
            return affected;
        }

        PointTreeSet tiles = zone.zoning.getTiles();
        if (tiles == null || tiles.isEmpty()) {
            affected.add(zone);
            return affected;
        }

        HashSet<Point> unvisited = new HashSet<>();
        for (Object o : tiles) {
            if (o instanceof Point) {
                Point p = (Point) o;
                unvisited.add(new Point(p));
            }
        }

        List<List<Point>> components = new ArrayList<>();
        int[] dx = new int[] {0, 1, 0, -1};
        int[] dy = new int[] {-1, 0, 1, 0};

        while (!unvisited.isEmpty()) {
            Point start = unvisited.iterator().next();
            LinkedList<Point> queue = new LinkedList<>();
            List<Point> comp = new ArrayList<>();
            queue.add(start);
            unvisited.remove(start);
            while (!queue.isEmpty()) {
                Point cur = queue.removeFirst();
                comp.add(cur);
                for (int i = 0; i < 4; ++i) {
                    Point n = new Point(cur.x + dx[i], cur.y + dy[i]);
                    if (unvisited.remove(n)) {
                        queue.add(n);
                    }
                }
            }
            components.add(comp);
        }

        if (components.size() <= 1) {
            affected.add(zone);
            return affected;
        }

        int bestIdx = 0;
        int bestSize = 0;
        for (int i = 0; i < components.size(); ++i) {
            int s = components.get(i).size();
            if (s > bestSize) {
                bestSize = s;
                bestIdx = i;
            }
        }

        for (int i = 0; i < components.size(); ++i) {
            if (i == bestIdx) {
                continue;
            }
            List<Point> comp = components.get(i);
            if (zone instanceof PvPZone) {
                PvPZone newZone = repository.addPvPZone(uniqueNameSupplier.get(), zone.creatorAuth, zone.colorHue);
                for (Point p : comp) {
                    newZone.zoning.addTile(p.x, p.y);
                }
                repository.putPvPZone(newZone);
                affected.add(newZone);
            } else if (zone instanceof ProtectedZone) {
                ProtectedZone newZone = repository.addProtectedZone(uniqueNameSupplier.get(), zone.creatorAuth, zone.colorHue);
                for (Point p : comp) {
                    newZone.zoning.addTile(p.x, p.y);
                }
                repository.putProtectedZone(newZone);
                affected.add(newZone);
            }
        }

        List<Point> mainComp = components.get(bestIdx);
        Zoning newZoning = new Zoning(true);
        for (Point p : mainComp) {
            newZoning.addTile(p.x, p.y);
        }
        zone.zoning = newZoning;
        affected.add(zone);

        ModLogger.debug("Split zone '%s' into %d parts", zone.name, components.size());
        return affected;
    }

    /**
     * Resolve zone changes after expand/shrink operations.
     * Handles merge candidates, split detection, barrier updates, and packet notifications.
     * 
     * @param targetZone The zone that was modified
     * @param level The level containing the zone
     * @param server The server for broadcasting packets
     * @param zoneType The type of zone being modified
     * @param oldEdgesByZoneID Optional old edge snapshot for barrier differential updates
     * @return List of zones that were modified or created as part of the resolution
     */
    public List<AdminZone> resolveAfterZoneChange(AdminZone targetZone, Level level, Server server, ZoneType zoneType,
                                                  Map<Integer, Collection<Point>> oldEdgesByZoneID) {
        List<AdminZone> result = new ArrayList<>();
        if (targetZone == null || level == null) {
            return result;
        }

        Set<AdminZone> candidates = findMergeCandidates(targetZone, zoneType);
        if (candidates.size() <= 1) {
            List<AdminZone> affected = splitZoneIfDisconnected(targetZone, level);
            for (AdminZone az : affected) {
                if (az instanceof PvPZone) {
                    Collection<Point> oldEdges = oldEdgesByZoneID != null ? oldEdgesByZoneID.get(az.uniqueID) : null;
                    barrierService.updateBarriers(level, (PvPZone) az, oldEdges);
                }
                if (server != null) {
                    server.network.sendToAllClients((Packet) new PacketZoneChanged(az));
                }
                result.add(az);
            }
            return result;
        }

        Set<AdminZone> mergeSet = new HashSet<>(candidates);
        mergeSet.add(targetZone);

        AdminZone winner = null;
        int bestSize = -1;
        for (AdminZone z : mergeSet) {
            PointTreeSet tiles = z.zoning.getTiles();
            int sz = tiles != null ? tiles.size() : 0;
            if (sz > bestSize) {
                bestSize = sz;
                winner = z;
            }
        }
        if (winner == null) {
            return result;
        }

        Set<Point> union = new HashSet<>();
        for (AdminZone z : mergeSet) {
            PointTreeSet t = z.zoning.getTiles();
            if (t == null) {
                continue;
            }
            for (Object o : t) {
                if (o instanceof Point) {
                    union.add(new Point((Point) o));
                }
            }
        }

        Zoning newZoning = new Zoning(true);
        for (Point p : union) {
            newZoning.addTile(p.x, p.y);
        }
        winner.zoning = newZoning;

        List<AdminZone> removed = new ArrayList<>();
        switch (zoneType) {
            case PVP: {
                Map<Integer, PvPZone> map = repository.getPvPZonesInternal();
                synchronized (map) {
                    for (AdminZone z : new ArrayList<>(mergeSet)) {
                        if (z == winner || !(z instanceof PvPZone)) {
                            continue;
                        }
                        PvPZone pz = (PvPZone) z;
                        barrierService.removeZoneArtifacts(level, pz);
                        map.remove(pz.uniqueID);
                        removed.add(pz);
                    }
                }
                repository.putPvPZone((PvPZone) winner);
                break;
            }
            case GUILD: {
                Map<Integer, GuildZone> map = repository.getGuildZonesInternal();
                synchronized (map) {
                    for (AdminZone z : new ArrayList<>(mergeSet)) {
                        if (z == winner || !(z instanceof GuildZone)) {
                            continue;
                        }
                        GuildZone gz = (GuildZone) z;
                        gz.remove();
                        map.remove(gz.uniqueID);
                        removed.add(gz);
                    }
                }
                repository.putGuildZone((GuildZone) winner);
                break;
            }
            default: { // PROTECTED
                Map<Integer, ProtectedZone> map = repository.getProtectedZonesInternal();
                synchronized (map) {
                    for (AdminZone z : new ArrayList<>(mergeSet)) {
                        if (z == winner || !(z instanceof ProtectedZone)) {
                            continue;
                        }
                        ProtectedZone pz = (ProtectedZone) z;
                        pz.remove();
                        map.remove(pz.uniqueID);
                        removed.add(pz);
                    }
                }
                repository.putProtectedZone((ProtectedZone) winner);
                break;
            }
        }

        List<AdminZone> affected = splitZoneIfDisconnected(winner, level);
        Collection<Point> combinedOld = computeCombinedOldEdges(oldEdgesByZoneID, mergeSet);

        for (AdminZone az : affected) {
            if (az instanceof PvPZone) {
                Collection<Point> diff = (az == winner) ? combinedOld : null;
                barrierService.updateBarriers(level, (PvPZone) az, diff);
            }
            if (server != null) {
                server.network.sendToAllClients((Packet) new PacketZoneChanged(az));
            }
            result.add(az);
        }

        for (AdminZone rem : removed) {
            if (server != null) {
                server.network.sendToAllClients((Packet) new PacketZoneRemoved(rem.uniqueID, ZoneType.fromZone(rem)));
            }
        }

        return result;
    }

    /**
     * Legacy method for backward compatibility.
     * @deprecated Use {@link #resolveAfterZoneChange(AdminZone, Level, Server, ZoneType, Map)} instead
     */
    @Deprecated
    public List<AdminZone> resolveAfterZoneChange(AdminZone targetZone, Level level, Server server, boolean isProtectedZone,
                                                  Map<Integer, Collection<Point>> oldEdgesByZoneID) {
        ZoneType zoneType = isProtectedZone ? ZoneType.PROTECTED : ZoneType.PVP;
        return resolveAfterZoneChange(targetZone, level, server, zoneType, oldEdgesByZoneID);
    }

    private Set<AdminZone> findMergeCandidates(AdminZone targetZone, ZoneType zoneType) {
        Set<AdminZone> candidates = new HashSet<>();
        if (targetZone == null) {
            return candidates;
        }
        switch (zoneType) {
            case PVP: {
                Map<Integer, PvPZone> map = repository.getPvPZonesInternal();
                synchronized (map) {
                    for (PvPZone z : map.values()) {
                        if (z == targetZone || areZonesAdjacentOrOverlapping(targetZone, z)) {
                            candidates.add(z);
                        }
                    }
                }
                break;
            }
            case GUILD: {
                Map<Integer, GuildZone> map = repository.getGuildZonesInternal();
                synchronized (map) {
                    for (GuildZone z : map.values()) {
                        if (z == targetZone || areZonesAdjacentOrOverlapping(targetZone, z)) {
                            candidates.add(z);
                        }
                    }
                }
                break;
            }
            default: { // PROTECTED
                Map<Integer, ProtectedZone> map = repository.getProtectedZonesInternal();
                synchronized (map) {
                    for (ProtectedZone z : map.values()) {
                        if (z == targetZone || areZonesAdjacentOrOverlapping(targetZone, z)) {
                            candidates.add(z);
                        }
                    }
                }
                break;
            }
        }
        return candidates;
    }

    /**
     * Legacy method for backward compatibility.
     * @deprecated Use {@link #findMergeCandidates(AdminZone, ZoneType)} instead
     */
    @Deprecated
    private Set<AdminZone> findMergeCandidates(AdminZone targetZone, boolean isProtectedZone) {
        ZoneType zoneType = isProtectedZone ? ZoneType.PROTECTED : ZoneType.PVP;
        return findMergeCandidates(targetZone, zoneType);
    }

    private Collection<Point> computeCombinedOldEdges(Map<Integer, Collection<Point>> oldEdgesByZoneID, Set<AdminZone> mergeSet) {
        if (oldEdgesByZoneID == null || mergeSet == null || mergeSet.isEmpty()) {
            return null;
        }
        Collection<Point> combinedOld = new ArrayList<>();
        for (AdminZone z : mergeSet) {
            Collection<Point> col = oldEdgesByZoneID.get(z.uniqueID);
            if (col != null) {
                combinedOld.addAll(col);
            }
        }
        return combinedOld.isEmpty() ? null : combinedOld;
    }

    private boolean areZonesAdjacentOrOverlapping(AdminZone a, AdminZone b) {
        if (a == null || b == null) {
            return false;
        }
        PointTreeSet ta = a.zoning.getTiles();
        PointTreeSet tb = b.zoning.getTiles();
        if (ta == null || ta.isEmpty() || tb == null || tb.isEmpty()) {
            return false;
        }
        Rectangle ra = a.zoning.getTileBounds();
        Rectangle rb = b.zoning.getTileBounds();
        if (ra == null || rb == null) {
            return false;
        }
        Rectangle expanded = new Rectangle(ra.x - 1, ra.y - 1, ra.width + 2, ra.height + 2);
        if (!expanded.intersects(rb)) {
            return false;
        }
        for (Object oa : ta) {
            if (!(oa instanceof Point)) {
                continue;
            }
            Point pa = (Point) oa;
            for (Object ob : tb) {
                if (!(ob instanceof Point)) {
                    continue;
                }
                Point pb = (Point) ob;
                int dx = Math.abs(pa.x - pb.x);
                int dy = Math.abs(pa.y - pb.y);
                if (dx + dy <= 1) {
                    return true;
                }
            }
        }
        return false;
    }
}
