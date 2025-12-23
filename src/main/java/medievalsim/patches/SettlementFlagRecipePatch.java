package medievalsim.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.recipe.CanCraft;
import necesse.inventory.recipe.Recipe;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;

/**
 * Patch to prevent settlement flag recipe crafting when plot flags are enabled.
 * When plot flags are enabled, players must use plot flags instead of directly crafting settlement flags.
 */
@ModMethodPatch(target = Recipe.class, name = "canCraft", arguments = {Level.class, PlayerMob.class, Iterable.class, boolean.class})
public class SettlementFlagRecipePatch {
    
    @Advice.OnMethodExit
    static void onCanCraft(
        @Advice.This Recipe recipe,
        @Advice.Argument(0) Level level,
        @Advice.Argument(1) PlayerMob player,
        @Advice.Return(readOnly = false) CanCraft canCraft
    ) {
        // Only block if plot flags are enabled
        if (!ModConfig.Settlements.plotFlagsEnabled) {
            return;
        }
        
        // Check if this is a settlement flag recipe
        if (recipe != null && "settlementflag".equals(recipe.resultStringID)) {
            ModLogger.debug("Blocked settlement flag recipe crafting for player %s (plot flags enabled)", 
                player != null ? player.playerName : "unknown");
            
            // Return cannot craft - player must use plot flags instead
            // Create CanCraft with recipe that will return false
            canCraft = new CanCraft(recipe, false);
        }
    }
}
