package medievalsim.zones.enhanced;

import necesse.engine.util.Zoning;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import java.awt.Rectangle;
import java.util.LinkedList;

/**
 * Enhanced zone implementation using Necesse's high-performance Zoning system.
 * 
 * This mirrors Necesse's RestrictZone approach but for admin zones:
 * - Uses PointTreeSet for O(log n) spatial queries
 * - Rectangle-based storage for efficient area operations
 * - Proper save/load integration with Necesse's data system
 * 
 * PERFORMANCE TARGET: 100+ players, unlimited zones
 */
public abstract class EnhancedAdminZone {
    
    protected final int uniqueID;
    protected String name;
    protected int colorHue;
    protected final Zoning zoning;
    
    /**
     * Enhanced zone constructor following Necesse's pattern
     */
    public EnhancedAdminZone(int uniqueID, String name, int colorHue) {
        this.uniqueID = uniqueID;
        this.name = name;
        this.colorHue = colorHue;
        
        // Use Necesse's high-performance Zoning system
        // This provides PointTreeSet-based spatial indexing
        this.zoning = new Zoning(true); // trackEdgeTiles = true for visualization
    }
    
    /**
     * Load constructor from save data (following your current pattern)
     */
    public EnhancedAdminZone(LoadData save, int tileXOffset, int tileYOffset) {
        this.uniqueID = save.getInt("uniqueID");
        this.name = save.getUnsafeString("name", ""); // Use your current pattern
        this.colorHue = save.getInt("colorHue", 120);
        
        this.zoning = new Zoning(true);
        this.zoning.applyZoneSaveData("zone", save, tileXOffset, tileYOffset);
    }
    
    // ===== HIGH-PERFORMANCE SPATIAL OPERATIONS =====
    
    /**
     * O(log n) point containment check using Necesse's PointTreeSet
     */
    public boolean containsTile(int tileX, int tileY) {
        return zoning.containsTile(tileX, tileY);
    }
    
    /**
     * Efficient rectangle expansion using Necesse's algorithms
     */
    public boolean expand(Rectangle rectangle) {
        return zoning.addRectangle(rectangle);
    }
    
    /**
     * Efficient rectangle shrinking
     */
    public boolean shrink(Rectangle rectangle) {
        return zoning.removeRectangle(rectangle);
    }
    
    /**
     * Get all rectangles for network sync (optimized representation)
     */
    public LinkedList<Rectangle> getTileRectangles() {
        return zoning.getTileRectangles();
    }
    
    /**
     * Check if zone is empty (optimization for cleanup)
     */
    public boolean isEmpty() {
        return zoning.isEmpty();
    }
    
    // ===== NECESSE-COMPATIBLE SAVE/LOAD =====
    
    /**
     * Save zone data using Necesse's proven format
     */
    public void addSaveData(SaveData save) {
        save.addInt("uniqueID", uniqueID);
        save.addUnsafeString("name", name); // Use your current pattern
        save.addInt("colorHue", colorHue);
        
        // Use Necesse's efficient zone save format
        zoning.addZoneSaveData("zone", save);
        
        // Add zone-specific data (override in subclasses)
        addZoneSpecificSaveData(save);
    }
    
    /**
     * Template method for zone-specific save data
     */
    protected abstract void addZoneSpecificSaveData(SaveData save);
    
    // ===== GETTERS =====
    
    public int getUniqueID() { return uniqueID; }
    public String getName() { return name; }
    public int getColorHue() { return colorHue; }
    
    public void setName(String name) { this.name = name; }
    public void setColorHue(int colorHue) { this.colorHue = colorHue; }
}