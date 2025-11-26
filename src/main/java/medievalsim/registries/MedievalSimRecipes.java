package medievalsim.registries;

import medievalsim.objects.PlotFlagRecipe;
import necesse.inventory.recipe.Recipes;

/**
 * Registry for Medieval Sim custom recipes
 */
public class MedievalSimRecipes {

    public static void registerCore() {
        // Register plot flag recipe (admin-only, appears in crafting menu)
        Recipes.registerModRecipe(new PlotFlagRecipe());
    }
}
