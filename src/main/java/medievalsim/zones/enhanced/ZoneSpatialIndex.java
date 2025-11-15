package medievalsim.zones.enhanced;

import java.awt.Rectangle;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * High-performance spatial index for zone lookups optimized for 100+ players.
 * 
 * Simplified approach that leverages Necesse's existing Zoning system optimizations
 * instead of duplicating spatial indexing. The key insight is that Necesse's Zoning
 * class already uses PointTreeSet internally for O(log n) containment checks.
 * 
 * PERFORMANCE TARGETS:
 * - O(log n) zone lookup by coordinates (via Necesse's Zoning)
 * - O(1) zone lookup by ID
 * - Thread-safe for concurrent server access
 * - Memory efficient for unlimited zones
 */
public class ZoneSpatialIndex {
    
    // Fast zone lookup: Zone ID -> Zone object
    private final Map<Integer, EnhancedAdminZone> zoneCache = new ConcurrentHashMap<>();
    
    // Zones by type for efficient filtering
    private final Map<Integer, EnhancedProtectedZone> protectedZones = new ConcurrentHashMap<>();
    private final Map<Integer, EnhancedAdminZone> pvpZones = new ConcurrentHashMap<>(); // Will add EnhancedPvPZone later
    
    // Thread safety for bulk operations
    private final ReadWriteLock bulkOperationLock = new ReentrantReadWriteLock();
    
    /**
     * Add a zone to the index.
     * O(1) operation since spatial indexing is handled by the zone's internal Zoning.
     */
    public void addZone(EnhancedAdminZone zone) {
        int zoneId = zone.getUniqueID();
        
        // Add to fast cache
        zoneCache.put(zoneId, zone);
        
        // Add to type-specific cache for efficient filtering
        if (zone instanceof EnhancedProtectedZone) {
            protectedZones.put(zoneId, (EnhancedProtectedZone) zone);
        } else {
            pvpZones.put(zoneId, zone);
        }
    }
    
    /**
     * Remove a zone from the index.
     * O(1) operation.
     */
    public void removeZone(int zoneId) {
        EnhancedAdminZone zone = zoneCache.remove(zoneId);
        if (zone == null) return;
        
        // Remove from type-specific caches
        if (zone instanceof EnhancedProtectedZone) {
            protectedZones.remove(zoneId);
        } else {
            pvpZones.remove(zoneId);
        }
    }
    
    /**
     * Get all zones containing a specific tile.
     * 
     * This leverages Necesse's optimized Zoning.containsTile() which uses
     * PointTreeSet internally for O(log n) performance per zone.
     * 
     * Total performance: O(z * log n) where z is total zone count and n is 
     * average tiles per zone. For most use cases, z is small so this is very fast.
     */
    public List<EnhancedAdminZone> getZonesAt(int tileX, int tileY) {
        List<EnhancedAdminZone> result = new ArrayList<>();
        
        // Check all zones using their optimized containsTile() method
        for (EnhancedAdminZone zone : zoneCache.values()) {
            if (zone.containsTile(tileX, tileY)) {
                result.add(zone);
            }
        }
        
        return result;
    }
    
    /**
     * Get protected zones containing a specific tile.
     * Optimized for protection checks which are the most frequent operation.
     */
    public List<EnhancedProtectedZone> getProtectedZonesAt(int tileX, int tileY) {
        List<EnhancedProtectedZone> result = new ArrayList<>();
        
        // Only check protected zones for performance
        for (EnhancedProtectedZone zone : protectedZones.values()) {
            if (zone.containsTile(tileX, tileY)) {
                result.add(zone);
            }
        }
        
        return result;
    }
    
    /**
     * Get all zones intersecting a rectangle.
     * Uses Necesse's optimized rectangle intersection logic.
     */
    public List<EnhancedAdminZone> getZonesIntersecting(Rectangle area) {
        List<EnhancedAdminZone> result = new ArrayList<>();
        
        for (EnhancedAdminZone zone : zoneCache.values()) {
            // Check if any of the zone's rectangles intersect the area
            for (Rectangle zoneRect : zone.getTileRectangles()) {
                if (zoneRect.intersects(area)) {
                    result.add(zone);
                    break; // Found intersection, no need to check other rectangles
                }
            }
        }
        
        return result;
    }
    
    /**
     * Update zone in index (when zone is modified).
     * Since we don't maintain separate spatial structures, this is a no-op.
     * The zone's internal Zoning handles spatial updates automatically.
     */
    public void updateZone(EnhancedAdminZone zone) {
        // No action needed - zone's internal Zoning handles spatial updates
        // Just ensure it's in our cache
        zoneCache.put(zone.getUniqueID(), zone);
    }
    
    /**
     * Get zone by ID (O(1) lookup).
     */
    public EnhancedAdminZone getZone(int zoneId) {
        return zoneCache.get(zoneId);
    }
    
    /**
     * Get protected zone by ID (O(1) lookup).
     */
    public EnhancedProtectedZone getProtectedZone(int zoneId) {
        return protectedZones.get(zoneId);
    }
    
    /**
     * Get all zones (for admin operations).
     */
    public Collection<EnhancedAdminZone> getAllZones() {
        return zoneCache.values();
    }
    
    /**
     * Get all protected zones.
     */
    public Collection<EnhancedProtectedZone> getAllProtectedZones() {
        return protectedZones.values();
    }
    
    /**
     * Get total zone count.
     */
    public int getZoneCount() {
        return zoneCache.size();
    }
    
    /**
     * Get zone count by type.
     */
    public IndexStats getStats() {
        return new IndexStats(
            zoneCache.size(),
            protectedZones.size(),
            pvpZones.size()
        );
    }
    
    /**
     * Bulk operation: replace all zones (for loading from save).
     */
    public void replaceAllZones(Collection<EnhancedAdminZone> zones) {
        bulkOperationLock.writeLock().lock();
        try {
            // Clear existing
            zoneCache.clear();
            protectedZones.clear();
            pvpZones.clear();
            
            // Add all new zones
            for (EnhancedAdminZone zone : zones) {
                addZone(zone);
            }
        } finally {
            bulkOperationLock.writeLock().unlock();
        }
    }
    
    /**
     * Statistics for monitoring index performance.
     */
    public static class IndexStats {
        public final int totalZones;
        public final int protectedZones;
        public final int pvpZones;
        
        public IndexStats(int totalZones, int protectedZones, int pvpZones) {
            this.totalZones = totalZones;
            this.protectedZones = protectedZones;
            this.pvpZones = pvpZones;
        }
        
        @Override
        public String toString() {
            return String.format("ZoneIndex[total=%d, protected=%d, pvp=%d]",
                    totalZones, protectedZones, pvpZones);
        }
    }
}