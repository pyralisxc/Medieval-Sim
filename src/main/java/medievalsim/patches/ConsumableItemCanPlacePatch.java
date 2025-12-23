package medievalsim.patches;

import medievalsim.util.BossSummonRestrictionHelper;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.item.placeableItem.consumableItem.ConsumableItem;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

/**
 * Patch to intercept boss summon item placement attempts.
 * Validates against global config, zone settings, and settlement protection.
 */
@ModMethodPatch(target = ConsumableItem.class, name = "canPlace", arguments = {Level.class, int.class, int.class, ServerClient.class})
public class ConsumableItemCanPlacePatch {

    @Advice.OnMethodExit
    public static void onExit(
        @Advice.This ConsumableItem item,
        @Advice.Argument(0) Level level,
        @Advice.Argument(1) int tileX,
        @Advice.Argument(2) int tileY,
        @Advice.Argument(3) ServerClient client,
        @Advice.Return(readOnly = false) String returnValue
    ) {
        // Only validate on server
        if (level == null || !level.isServer() || client == null) {
            return;
        }

        // Check if this is a boss summon item
        if (!BossSummonRestrictionHelper.isBossSummonItem(item)) {
            return; // Not a boss summon, allow normal validation
        }

        // If already denied for other reasons, don't override
        if (returnValue != null) {
            return; // Already has an error message
        }

        // Check if boss summon is allowed at this location
        boolean allowed = BossSummonRestrictionHelper.canSummonBoss(client, level, tileX, tileY, item);
        
        if (!allowed) {
            // Prevent summon by returning error message
            // Message already sent to client via BossSummonRestrictionHelper
            returnValue = "Boss summons are not allowed here";
        }
    }
}
