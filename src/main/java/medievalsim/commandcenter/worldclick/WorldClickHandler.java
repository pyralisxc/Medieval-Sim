package medievalsim.commandcenter.worldclick;

import necesse.engine.network.client.Client;
import necesse.gfx.GameColor;

import java.util.function.BiConsumer;

/**
 * Handles world-click coordinate selection for command parameters.
 * 
 * Flow:
 * 1. Widget calls startSelection() with a callback
 * 2. User clicks on the world map
 * 3. Callback receives the tile coordinates
 * 4. Widget updates its value
 * 
 * This is a singleton that manages the active selection state.
 */
public class WorldClickHandler {
    
    private static WorldClickHandler instance;
    
    private boolean isActive = false;
    private BiConsumer<Integer, Integer> coordinateCallback;
    
    // Visual feedback
    private int lastHoverTileX = -1;
    private int lastHoverTileY = -1;
    
    private WorldClickHandler() {
        // Private constructor for singleton
    }
    
    /**
     * Get the singleton instance
     */
    public static WorldClickHandler getInstance() {
        if (instance == null) {
            instance = new WorldClickHandler();
        }
        return instance;
    }
    
    /**
     * Start coordinate selection mode
     * 
     * @param client The client instance
     * @param callback Called with (tileX, tileY) when user clicks
     */
    public void startSelection(Client client, BiConsumer<Integer, Integer> callback) {
        this.coordinateCallback = callback;
        this.isActive = true;
        
        medievalsim.util.ModLogger.debug("WorldClickHandler: Started coordinate selection mode");
        
        // Show message to guide user
        if (client != null) {
            client.chat.addMessage(GameColor.YELLOW.getColorCode() + "Click on the world to select coordinates. Press ESC to cancel.");
        }
    }
    
    /**
     * Stop coordinate selection mode
     */
    public void stopSelection() {
        this.isActive = false;
        this.coordinateCallback = null;
        this.lastHoverTileX = -1;
        this.lastHoverTileY = -1;
        
        medievalsim.util.ModLogger.debug("WorldClickHandler: Stopped coordinate selection mode");
    }
    
    /**
     * Check if coordinate selection is currently active
     */
    public boolean isActive() {
        return isActive;
    }
    
    /**
     * Handle a world click event
     * Called from a game patch when the user clicks on the world
     * 
     * @param tileX The clicked tile X coordinate
     * @param tileY The clicked tile Y coordinate
     * @return true if the click was consumed (selection was active)
     */
    public boolean handleWorldClick(int tileX, int tileY) {
        if (!isActive) {
            return false; // Not in selection mode, don't consume click
        }
        
        medievalsim.util.ModLogger.debug("WorldClickHandler: World clicked at: %d, %d", tileX, tileY);
        
        // Invoke the callback
        if (coordinateCallback != null) {
            coordinateCallback.accept(tileX, tileY);
        }
        
        // Stop selection mode
        stopSelection();
        
        return true; // Consume the click event
    }
    
    /**
     * Update hover coordinates (for visual feedback)
     * 
     * @param tileX Current mouse tile X
     * @param tileY Current mouse tile Y
     */
    public void updateHoverCoordinates(int tileX, int tileY) {
        if (!isActive) {
            lastHoverTileX = -1;
            lastHoverTileY = -1;
            return;
        }
        
        this.lastHoverTileX = tileX;
        this.lastHoverTileY = tileY;
    }
    
    /**
     * Get current hover tile X (for rendering)
     */
    public int getHoverTileX() {
        return lastHoverTileX;
    }
    
    /**
     * Get current hover tile Y (for rendering)
     */
    public int getHoverTileY() {
        return lastHoverTileY;
    }
    
    /**
     * Get display string for current hover position
     */
    public String getHoverDisplayString() {
        if (!isActive || lastHoverTileX < 0 || lastHoverTileY < 0) {
            return null;
        }
        
        return "Click to select: (" + lastHoverTileX + ", " + lastHoverTileY + ")";
    }
}
