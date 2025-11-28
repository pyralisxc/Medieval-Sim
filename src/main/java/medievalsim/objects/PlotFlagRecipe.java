package medievalsim.objects;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.registries.RecipeTechRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.Inventory;
import necesse.inventory.recipe.CanCraft;
import necesse.inventory.recipe.Ingredient;
import necesse.inventory.recipe.Recipe;
import necesse.level.maps.Level;

/**
 * Custom recipe for plot flags that only appears for admins when plot flags are enabled.
 * Creates 1 plot flag with no ingredient cost (free for admins).
 */
public class PlotFlagRecipe extends Recipe {
    
    public PlotFlagRecipe() {
        super(
            "plotflag",           // Result item
            1,                     // Result amount
            RecipeTechRegistry.NONE,  // No crafting station required (appears in inventory)
            new Ingredient[0],     // No ingredients required
            false,                 // Not hidden
            null                   // No special GND data
        );
        
        ModLogger.info("PlotFlagRecipe created");
    }
    
    /**
     * Helper method to check if player can craft plot flags
     */
    private boolean canPlayerCraft(Level level, PlayerMob player) {
        // Check if plot flags are enabled
        if (!ModConfig.PlotFlags.enabled) {
            return false;
        }

        // Check if player is admin
        if (level != null && level.isServer()) {
            necesse.engine.network.server.ServerClient serverClient = player.getServerClient();
            if (serverClient != null && serverClient.getPermissionLevel().getLevel() >= PermissionLevel.ADMIN.getLevel()) {
                ModLogger.debug("PlotFlagRecipe: Admin %s can craft plot flag", player.getDisplayName());
                return true;
            } else {
                ModLogger.debug("PlotFlagRecipe: Player %s is not admin, cannot craft", player.getDisplayName());
            }
        }

        return false;
    }

    /**
     * Override canCraft to only allow admins when plot flags are enabled
     */
    @Override
    public CanCraft canCraft(Level level, PlayerMob player, Inventory inv, boolean countAllIngredients) {
        if (canPlayerCraft(level, player)) {
            return super.canCraft(level, player, inv, countAllIngredients);
        }
        return new CanCraft(this.ingredients, false);
    }

    /**
     * Override canCraft for multiple inventories (same logic)
     */
    @Override
    public CanCraft canCraft(Level level, PlayerMob player, Iterable<Inventory> invList, boolean countAllIngredients) {
        if (canPlayerCraft(level, player)) {
            return super.canCraft(level, player, invList, countAllIngredients);
        }
        return new CanCraft(this.ingredients, false);
    }
}
