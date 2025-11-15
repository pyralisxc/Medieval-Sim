package medievalsim.commandcenter.worldclick;

import necesse.engine.GlobalData;
import necesse.engine.input.InputEvent;
import necesse.engine.network.client.Client;
import necesse.engine.state.State;
import necesse.gfx.camera.GameCamera;
import necesse.level.maps.Level;

/**
 * Integrates the WorldClickHandler and WorldClickOverlay into the game.
 * 
 * Responsibilities:
 * - Registers the HUD overlay when selection starts
 * - Removes the HUD overlay when selection ends
 * - Updates hover coordinates each tick
 * - Intercepts mouse clicks during selection
 * 
 * This class bridges between the game's input/rendering system and our coordinate selection logic.
 */
public class WorldClickIntegration {
    
    private static WorldClickOverlay activeOverlay = null;
    private static Client activeClient = null;
    
    /**
     * Initialize world-click integration for the given client.
     * Call this when starting coordinate selection.
     * 
     * @param client The client instance
     */
    public static void startIntegration(Client client) {
        if (activeClient == client && activeOverlay != null) {
            return; // Already integrated
        }
        
        activeClient = client;
        
        // Create and register the HUD overlay
        activeOverlay = new WorldClickOverlay();
        
        Level level = client.getLevel();
        if (level != null) {
            level.hudManager.addElement(activeOverlay);
            System.out.println("[WorldClickIntegration] Overlay registered to HUD");
        }
    }
    
    /**
     * Stop world-click integration and clean up resources.
     * Call this when coordinate selection ends.
     */
    public static void stopIntegration() {
        if (activeOverlay != null) {
            activeOverlay.remove(); // HudDrawElement has remove() method
            System.out.println("[WorldClickIntegration] Overlay removed from HUD");
        }
        
        activeOverlay = null;
        activeClient = null;
    }
    
    /**
     * Update hover coordinates based on current mouse position.
     * Should be called each tick while selection is active.
     */
    public static void updateHoverPosition() {
        WorldClickHandler handler = WorldClickHandler.getInstance();
        
        if (!handler.isActive()) {
            return; // Not in selection mode
        }
        
        State currentState = GlobalData.getCurrentState();
        if (currentState == null) {
            return;
        }
        
        GameCamera camera = currentState.getCamera();
        if (camera == null) {
            return;
        }
        
        // Get current mouse tile position
        int tileX = camera.getMouseLevelTilePosX();
        int tileY = camera.getMouseLevelTilePosY();
        
        // Update handler's hover coordinates (for visual feedback)
        handler.updateHoverCoordinates(tileX, tileY);
    }
    
    /**
     * Handle a mouse click event.
     * Returns true if the click was consumed (selection was active).
     * 
     * @param event The input event (must be a left mouse click)
     * @return true if click was consumed, false otherwise
     */
    public static boolean handleClick(InputEvent event) {
        WorldClickHandler handler = WorldClickHandler.getInstance();
        
        if (!handler.isActive()) {
            return false; // Not in selection mode, don't consume click
        }
        
        // Only handle left click press (getID() == -100 is left click)
        if (!event.state || event.getID() != -100) {
            return false;
        }
        
        State currentState = GlobalData.getCurrentState();
        if (currentState == null) {
            return false;
        }
        
        GameCamera camera = currentState.getCamera();
        if (camera == null) {
            return false;
        }
        
        // Get clicked tile position
        int tileX = camera.getMouseLevelTilePosX(event);
        int tileY = camera.getMouseLevelTilePosY(event);
        
        // Pass to handler
        boolean consumed = handler.handleWorldClick(tileX, tileY);
        
        // If selection ended, clean up integration
        if (consumed && !handler.isActive()) {
            stopIntegration();
        }
        
        return consumed;
    }
    
    /**
     * Check if integration is currently active
     */
    public static boolean isActive() {
        return activeOverlay != null && activeClient != null;
    }
}
