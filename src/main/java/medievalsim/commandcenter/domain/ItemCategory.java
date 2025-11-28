package medievalsim.commandcenter.domain;

/**
 * Item categories for filtering in dropdowns
 */
public enum ItemCategory {
    ALL("All Items"),
    WEAPON("Weapons"),
    ARMOR("Armor"),
    TOOL("Tools"),
    MATERIAL("Materials"),
    CONSUMABLE("Consumables"),
    POTION("Potions"),
    ACCESSORY("Accessories"),
    FURNITURE("Furniture"),
    SEED("Seeds"),
    QUEST("Quest Items"),
    MISC("Miscellaneous");
    
    private final String displayName;
    
    ItemCategory(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
