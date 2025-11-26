package medievalsim.util;

import medievalsim.config.ModConfig;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.mobs.PlayerMob;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.CachedSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementBoundsManager;

import java.awt.Rectangle;
import java.util.Comparator;

/**
 * Shared utility for providing detailed settlement spacing information to players.
 */
public class SettlementSpacingHelper {
    
    /**
     * Sends detailed spacing information to the player when placement fails due to proximity.
     */
    public static void sendDetailedSpacingInfo(Level level, int x, int y, PlayerMob player) {
        Server server = level.getServer();
        SettlementsWorldData settlementsData = SettlementsWorldData.getSettlementsData(server);
        ServerClient client = player.getServerClient();
        
        // Get spacing configuration
        int customTier = ModConfig.SettlementSpacing.minimumTier;
        int customPadding = ModConfig.SettlementSpacing.customPadding;
        int effectiveTier = Math.max(0, customTier);
        
        // Calculate required spacing based on tier
        // NOTE: One region = 8×8 tiles (64 tiles total, not 64 tiles per dimension!)
        Rectangle uncenteredRect = SettlementBoundsManager.getUncenteredRegionRectangleFromTier(effectiveTier);
        int regionWidth = uncenteredRect.width;
        int regionHeight = uncenteredRect.height;
        int tilesPerRegion = 8; // 8 tiles per region dimension (8×8 = 64 tiles total)
        int baseSpacingTiles = Math.max(regionWidth, regionHeight) * tilesPerRegion;
        int totalSpacingTiles = baseSpacingTiles + (customPadding * 2 * tilesPerRegion);
        
        // Find the ACTUAL nearest settlement by simple distance calculation
        CachedSettlementData nearestSettlement = settlementsData.streamSettlements()
            .filter(s -> s.levelIdentifier.equals(level.getIdentifier()))
            .min(Comparator.comparingDouble(s -> {
                // getTileX/getTileY should return actual tile coordinates
                int settlementTileX = s.getTileX();
                int settlementTileY = s.getTileY();
                int deltaX = settlementTileX - x;
                int deltaY = settlementTileY - y;
                return Math.sqrt((double)(deltaX * deltaX + deltaY * deltaY));
            }))
            .orElse(null);
        
        if (nearestSettlement != null) {
            // Get coordinates - getTileX/getTileY return actual tile coordinates
            int settlementX = nearestSettlement.getTileX();
            int settlementY = nearestSettlement.getTileY();
            
            int dx = settlementX - x;
            int dy = settlementY - y;
            double distance = Math.sqrt((double)(dx * dx) + (double)(dy * dy));
            
            // Safety check for invalid distance
            if (Double.isNaN(distance) || Double.isInfinite(distance)) {
                ModLogger.warn("Invalid distance: settlement(%d,%d) placement(%d,%d)",
                    settlementX, settlementY, x, y);
                distance = 0;
            }
            
            String direction = getCardinalDirection(dx, dy);
            
            // Calculate safe distance (total spacing requirement)
            int safeDistance = (int) Math.ceil(totalSpacingTiles - distance);
            String oppositeDirection = getOppositeDirection(direction);
            
            // Send comprehensive info messages
            client.sendChatMessage(new StaticMessage(
                "§9━━━ Settlement Placement Failed ━━━"));
            client.sendChatMessage(new StaticMessage(
                String.format("§cNearest settlement: §f%d tiles %s", (int)distance, direction)));
            client.sendChatMessage(new StaticMessage(
                String.format("§eSpacing requirement: §f%d tiles §7(Tier %d + %d region padding)", 
                    totalSpacingTiles, effectiveTier, customPadding)));
            if (safeDistance > 0) {
                client.sendChatMessage(new StaticMessage(
                    String.format("§aMove §f%d tiles %s §afor valid placement", 
                        safeDistance, oppositeDirection)));
            }
        } else {
            // Fallback if we can't find the settlement
            client.sendChatMessage(new StaticMessage(
                String.format("§cToo close to another settlement. Required spacing: §f%d tiles §7(Tier %d)", 
                    totalSpacingTiles, effectiveTier)));
        }
    }
    
    /**
     * Gets cardinal direction from delta coordinates.
     */
    private static String getCardinalDirection(int dx, int dy) {
        double angle = Math.atan2(dy, dx) * 180 / Math.PI;
        
        if (angle >= -22.5 && angle < 22.5) return "East";
        if (angle >= 22.5 && angle < 67.5) return "Southeast";
        if (angle >= 67.5 && angle < 112.5) return "South";
        if (angle >= 112.5 && angle < 157.5) return "Southwest";
        if (angle >= 157.5 || angle < -157.5) return "West";
        if (angle >= -157.5 && angle < -112.5) return "Northwest";
        if (angle >= -112.5 && angle < -67.5) return "North";
        return "Northeast"; // -67.5 to -22.5
    }
    
    /**
     * Gets opposite cardinal direction for movement suggestion.
     */
    private static String getOppositeDirection(String direction) {
        switch (direction) {
            case "North": return "South";
            case "South": return "North";
            case "East": return "West";
            case "West": return "East";
            case "Northeast": return "Southwest";
            case "Northwest": return "Southeast";
            case "Southeast": return "Northwest";
            case "Southwest": return "Northeast";
            default: return "away";
        }
    }
}
