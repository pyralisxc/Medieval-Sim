/*
 * Patch: GameObject.getInteractTip
 * Purpose: Override interact tooltips to show zone protection messages instead of "[E] Interact"
 */
package medievalsim.patches;

import medievalsim.zones.domain.AdminZonesLevelData;
import medievalsim.zones.domain.ProtectedZone;
import necesse.engine.localization.Localization;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(
    target = GameObject.class,
    name = "getInteractTip",
    arguments = {Level.class, int.class, int.class, PlayerMob.class, boolean.class}
)
public class GameObjectGetInteractTipPatch {

    @Advice.OnMethodExit
    public static void onExit(
        @Advice.This GameObject gameObject,
        @Advice.Argument(0) Level level,
        @Advice.Argument(1) int x,
        @Advice.Argument(2) int y,
        @Advice.Argument(3) PlayerMob player,
        @Advice.Argument(4) boolean debug,
        @Advice.Return(readOnly = false) String result
    ) {
        // Only override on client side with valid player
        if (!level.isClient() || player == null || !player.isPlayer) {
            return;
        }

        // Get tile coordinates
        int tileX = GameMath.getTileCoordinate(x);
        int tileY = GameMath.getTileCoordinate(y);

        // Check if position is in a protected zone
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        if (zoneData == null) {
            return; // No zones, keep original tooltip
        }

        ProtectedZone zone = zoneData.getProtectedZoneAt(tileX, tileY);
        if (zone == null) {
            return; // Not in a protected zone, keep original tooltip
        }

        // On client side, check buff to determine permissions
        // The buff contains permission data synced from server
        necesse.entity.mobs.buffs.ActiveBuff zoneBuff = player.buffManager.getBuff(
            necesse.engine.registries.BuffRegistry.getBuffID("protectedzone")
        );
        
        if (zoneBuff != null) {
            boolean isElevated = zoneBuff.getGndData().getBoolean("isElevated");
            
            // If elevated (owner/creator/world owner), allow all interactions
            if (isElevated) {
                return; // Keep original tooltip
            }
            
            // Check specific permission from buff data using GameObjectClassifier
            medievalsim.util.GameObjectClassifier.InteractionType type = 
                medievalsim.util.GameObjectClassifier.classify(gameObject);
            boolean hasPermission = checkPermissionFromBuff(zoneBuff, type);
            
            if (!hasPermission) {
                // Player doesn't have permission - override tooltip to show zone protection message
                String objectType = medievalsim.util.GameObjectClassifier.getDisplayName(gameObject);
                result = Localization.translate("ui", "zoneprotectiontooltip", 
                    "zone", zone.name, 
                    "object", objectType);
            }
        }
        // If no buff or player has permission, keep the original interact tooltip
    }
    
    /**
     * Check if player has permission based on buff data and GameObject type.
     * Uses GameObjectClassifier for consistent type detection.
     */
    private static boolean checkPermissionFromBuff(
        necesse.entity.mobs.buffs.ActiveBuff zoneBuff, 
        medievalsim.util.GameObjectClassifier.InteractionType type
    ) {
        // Map InteractionType to buff permission flags
        switch (type) {
            case DOOR:
                return zoneBuff.getGndData().getBoolean("canDoors");
            case CRAFTING_STATION:
                return zoneBuff.getGndData().getBoolean("canStations");
            case CONTAINER:
                return zoneBuff.getGndData().getBoolean("canChests");
            case SIGN:
                return zoneBuff.getGndData().getBoolean("canSigns");
            case SWITCH:
                return zoneBuff.getGndData().getBoolean("canSwitches");
            case FURNITURE:
                return zoneBuff.getGndData().getBoolean("canFurniture");
            case UNKNOWN:
            default:
                return false; // Unknown types denied by default
        }
    }
}
