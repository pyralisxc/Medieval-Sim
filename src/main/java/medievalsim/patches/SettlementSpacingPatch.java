package medievalsim.patches;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.util.GameMath;
import necesse.engine.util.LevelIdentifier;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.level.maps.levelData.settlementData.SettlementBoundsManager;
import net.bytebuddy.asm.Advice;

import java.awt.Rectangle;

/**
 * Patches settlement spacing validation to support custom spacing configurations.
 * Allows configuring minimum spacing based on upgrade tiers.
 */
public class SettlementSpacingPatch {

    @ModMethodPatch(
        target = SettlementsWorldData.class,
        name = "canPlaceSettlementFlagAt",
        arguments = {LevelIdentifier.class, int.class, int.class, int.class}
    )
    public static class CanPlaceSettlementFlagAt {
        
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        static boolean onEnter(
            @Advice.This SettlementsWorldData worldData,
            @Advice.Argument(0) LevelIdentifier levelIdentifier,
            @Advice.Argument(1) int tileX,
            @Advice.Argument(2) int tileY,
            @Advice.Argument(3) int flagTier
        ) {
            // Get custom spacing settings
            int customTier = ModConfig.SettlementSpacing.minimumTier;
            int customRegionPadding = ModConfig.SettlementSpacing.customPadding;

            // Only apply custom spacing if tier > 0 or custom padding is set
            if (customTier == 0 && customRegionPadding == 0) {
                return false; // Use vanilla logic (tier 0, no padding)
            }

            // ALWAYS skip vanilla method when custom spacing is enabled
            // We'll set the correct return value in OnMethodExit
            return true;
        }
        
        @Advice.OnMethodExit
        static void onExit(
            @Advice.Return(readOnly = false) boolean returnValue,
            @Advice.Enter boolean skipped,
            @Advice.This SettlementsWorldData worldData,
            @Advice.Argument(0) LevelIdentifier levelIdentifier,
            @Advice.Argument(1) int tileX,
            @Advice.Argument(2) int tileY,
            @Advice.Argument(3) int flagTier
        ) {
            // If we skipped the original method, set the return value based on our custom validation
            if (skipped) {
                int customTier = ModConfig.SettlementSpacing.minimumTier;
                int customRegionPadding = ModConfig.SettlementSpacing.customPadding;
                returnValue = canPlaceWithCustomSpacing(worldData, levelIdentifier, tileX, tileY, 
                                                       flagTier, customTier, customRegionPadding);
            }
        }

        /**
         * Custom spacing validation that enforces minimum center-to-center distance between settlements.
         * 
         * @param worldData The settlements world data
         * @param levelIdentifier The level identifier
         * @param tileX The X tile coordinate
         * @param tileY The Y tile coordinate
         * @param flagTier The flag tier being placed
         * @param customTier The minimum tier determining spacing requirement
         * @param customRegionPadding Additional region padding
         * @return true if placement is allowed, false otherwise
         */
        public static boolean canPlaceWithCustomSpacing(
            SettlementsWorldData worldData,
            LevelIdentifier levelIdentifier,
            int tileX, int tileY,
            int flagTier,
            int customTier,
            int customRegionPadding
        ) {
            // Calculate required spacing based on tier
            int effectiveTier = Math.max(flagTier, customTier);
            Rectangle tierRectangle = SettlementBoundsManager.getUncenteredRegionRectangleFromTier(effectiveTier);
            
            // Convert region rectangle to tile spacing
            // Note: One region = 8×8 tiles (64 tiles total, not 64 tiles per dimension!)
            // For tier 1: Rectangle(-3, -3, 7, 7) = 7 regions wide = 7 * 8 = 56 tiles
            int regionSpacing = tierRectangle.width; // Width in regions
            int tileSpacing = regionSpacing * 8; // Convert to tiles (8 tiles per region dimension)
            
            // Add custom padding (in regions, convert to tiles)
            if (customRegionPadding > 0) {
                tileSpacing += customRegionPadding * 8;
            }
            
            if (medievalsim.config.ModConfig.Logging.verboseDebug) {
                ModLogger.debug("Spacing check at (%d, %d): flagTier=%d, customTier=%d, effectiveTier=%d, required spacing=%d tiles",
                              tileX, tileY, flagTier, customTier, effectiveTier, tileSpacing);
            }
            
            // Check distance to ALL settlements in this level
            var settlements = worldData.streamSettlements().toList();
            for (var settlement : settlements) {
                if (settlement == null) continue;
                
                // Only check settlements in the same level
                if (!settlement.levelIdentifier.equals(levelIdentifier)) continue;
                
                // Calculate center-to-center distance
                int dx = settlement.getTileX() - tileX;
                int dy = settlement.getTileY() - tileY;
                double distance = Math.sqrt((double)(dx * dx) + (double)(dy * dy));
                
                // Check if it's an upgrade (placement at same location as existing settlement)
                if (distance < 10) {
                    if (medievalsim.config.ModConfig.Logging.verboseDebug) {
                        ModLogger.debug("Settlement at (%d, %d) - upgrade detected (distance=%.1f tiles)",
                                      settlement.getTileX(), settlement.getTileY(), distance);
                    }
                    continue; // Allow upgrades
                }
                
                // Enforce minimum spacing
                if (distance < tileSpacing) {
                    if (medievalsim.config.ModConfig.Logging.verboseDebug) {
                        ModLogger.debug("Spacing BLOCKED: Settlement at (%d, %d) too close (distance=%.1f tiles, required=%d tiles)",
                                      settlement.getTileX(), settlement.getTileY(), distance, tileSpacing);
                    }
                    return false;
                }
                
                if (medievalsim.config.ModConfig.Logging.verboseDebug) {
                    ModLogger.debug("Settlement at (%d, %d) OK (distance=%.1f tiles >= required=%d tiles)",
                                  settlement.getTileX(), settlement.getTileY(), distance, tileSpacing);
                }
            }

            if (medievalsim.config.ModConfig.Logging.verboseDebug) {
                ModLogger.debug("Spacing check PASSED: placement at (%d, %d) flagTier=%d, minTier=%d",
                              tileX, tileY, flagTier, customTier);
            }
            return true;
        }
    }
}
