package medievalsim.commandcenter.worldclick;

import necesse.engine.network.client.Client;

/**
 * Client-side tick manager for world-click coordinate selection.
 * 
 * This manager runs each tick and:
 * - Updates hover coordinates when selection is active
 * - Handles cleanup and state management
 * 
 * Register this with the client's tick cycle during mod initialization.
 */
public class WorldClickTickManager {
    
    private static WorldClickTickManager instance;
    private Client client;
    
    private WorldClickTickManager() {
        // Private constructor for singleton
    }
    
    /**
     * Get or create the singleton instance
     */
    public static WorldClickTickManager getInstance() {
        if (instance == null) {
            instance = new WorldClickTickManager();
        }
        return instance;
    }
    
    /**
     * Initialize the tick manager with a client instance
     * 
     * @param client The client to monitor
     */
    public void init(Client client) {
        this.client = client;
        medievalsim.util.ModLogger.debug("WorldClickTickManager: Initialized with client");
    }
    
    /**
     * Called each client tick.
     * Updates hover position if world-click selection is active.
     */
    public void tick() {
        if (client == null) {
            return;
        }
        
        WorldClickHandler handler = WorldClickHandler.getInstance();
        
        // Update hover coordinates if selection is active
        if (handler.isActive()) {
            WorldClickIntegration.updateHoverPosition();
        }
    }
}
