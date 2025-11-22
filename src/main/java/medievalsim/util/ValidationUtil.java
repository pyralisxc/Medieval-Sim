package medievalsim.util;

import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;

/**
 * Common validation utilities for Medieval Sim
 * Reduces code duplication and provides consistent validation patterns
 */
public final class ValidationUtil {
    
    private ValidationUtil() {
        // Utility class - no instantiation
    }
    
    /**
     * Validate that level is non-null and is a server level
     * @param level Level to validate
     * @return true if valid server level, false otherwise
     */
    public static boolean isValidServerLevel(Level level) {
        return level != null && level.isServer();
    }
    
    /**
     * Validate that client is non-null and authenticated
     * @param client Client to validate
     * @return true if valid authenticated client, false otherwise
     */
    public static boolean isValidClient(ServerClient client) {
        return client != null && client.authentication != -1L;
    }
    
    /**
     * Validate server level and log appropriate error if invalid
     * @param level Level to validate
     * @param operation Description of operation being attempted
     * @return true if valid, false if invalid (and logged)
     */
    public static boolean validateServerLevel(Level level, String operation) {
        if (!isValidServerLevel(level)) {
            ModLogger.warn("Attempted %s with invalid level (null or not server)", operation);
            return false;
        }
        return true;
    }
    
    /**
     * Validate client and log appropriate error if invalid
     * @param client Client to validate
     * @param operation Description of operation being attempted
     * @return true if valid, false if invalid (and logged)
     */
    public static boolean validateClient(ServerClient client, String operation) {
        if (!isValidClient(client)) {
            ModLogger.warn("Attempted %s with invalid client (null or not authenticated)", operation);
            return false;
        }
        return true;
    }
    
    /**
     * Validate string is non-null and not empty after trimming
     * @param value String to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidString(String value) {
        return value != null && !value.trim().isEmpty();
    }
    
    /**
     * Validate coordinate is within reasonable bounds
     * Necesse uses tile coordinates, so very large values may cause issues
     * @param coordinate Coordinate to validate
     * @return true if coordinate is reasonable, false otherwise
     */
    public static boolean isValidCoordinate(int coordinate) {
        // Reasonable bounds for Necesse tile coordinates
        return coordinate >= -100000 && coordinate <= 100000;
    }
    
    /**
     * Validate rectangle has positive dimensions and reasonable coordinates
     * @param x X coordinate
     * @param y Y coordinate  
     * @param width Width (must be positive)
     * @param height Height (must be positive)
     * @return true if valid rectangle, false otherwise
     */
    public static boolean isValidRectangle(int x, int y, int width, int height) {
        return isValidCoordinate(x) && isValidCoordinate(y) && 
               width > 0 && height > 0 && 
               width <= 10000 && height <= 10000; // Prevent excessive sizes
    }
}
