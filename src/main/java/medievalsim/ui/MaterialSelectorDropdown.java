package medievalsim.ui;

import necesse.engine.localization.message.StaticMessage;
import necesse.engine.registries.ItemRegistry;
import necesse.gfx.forms.components.FormDropdownSelectionButton;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.ui.ButtonColor;
import necesse.inventory.item.Item;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Material selector dropdown with category filtering and text search.
 * Used for selecting materials for plot flag costs.
 */
public class MaterialSelectorDropdown {
    
    private final FormDropdownSelectionButton<String> dropdown;
    private final Map<String, List<String>> categorizedMaterials;
    private String currentCategory = "All";
    private String currentFilter = "";
    
    // Material categories for filtering
    public enum MaterialCategory {
        ALL("All Materials"),
        COINS("Coins & Currency"),
        BARS("Bars & Ingots"),
        GEMS("Gems & Crystals"),
        MATERIALS("Raw Materials"),
        MISC("Miscellaneous");
        
        private final String displayName;
        
        MaterialCategory(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * Create a material selector dropdown
     */
    public MaterialSelectorDropdown(int x, int y, int width, String initialValue, Consumer<String> onChange) {
        // Initialize categorized materials
        this.categorizedMaterials = categorizeMaterials();
        
        // Create dropdown
        this.dropdown = new FormDropdownSelectionButton<>(
            x, y,
            FormInputSize.SIZE_16,
            ButtonColor.BASE,
            width,
            new StaticMessage("Select Material")
        );
        
        // Populate with all materials initially
        populateDropdown();
        
        // Set initial value
        if (initialValue != null && !initialValue.isEmpty()) {
            Item item = ItemRegistry.getItem(initialValue);
            if (item != null) {
                dropdown.setSelected(initialValue, new StaticMessage(item.getDisplayName(item.getDefaultItem(null, 1))));
            }
        }
        
        // Listen for selection changes
        dropdown.onSelected(event -> {
            if (event.value != null && onChange != null) {
                onChange.accept(event.value);
            }
        });
    }
    
    /**
     * Categorize all materials from ItemRegistry
     */
    private Map<String, List<String>> categorizeMaterials() {
        Map<String, List<String>> categories = new HashMap<>();
        
        // Initialize category lists
        for (MaterialCategory cat : MaterialCategory.values()) {
            categories.put(cat.name(), new ArrayList<>());
        }
        
        // Get all items and categorize them
        List<Item> allItems = ItemRegistry.getItems();
        for (Item item : allItems) {
            if (item == null) continue;
            
            String stringId = item.getStringID();
            String displayName = item.getDisplayName(item.getDefaultItem(null, 1)).toLowerCase();
            
            // Add to ALL category
            categories.get(MaterialCategory.ALL.name()).add(stringId);
            
            // Categorize based on item properties
            if (isCoin(stringId, displayName)) {
                categories.get(MaterialCategory.COINS.name()).add(stringId);
            } else if (isBar(stringId, displayName)) {
                categories.get(MaterialCategory.BARS.name()).add(stringId);
            } else if (isGem(stringId, displayName)) {
                categories.get(MaterialCategory.GEMS.name()).add(stringId);
            } else if (isMaterial(stringId, displayName, item)) {
                categories.get(MaterialCategory.MATERIALS.name()).add(stringId);
            } else {
                categories.get(MaterialCategory.MISC.name()).add(stringId);
            }
        }
        
        // Sort each category alphabetically by display name
        for (List<String> categoryList : categories.values()) {
            categoryList.sort((a, b) -> {
                Item itemA = ItemRegistry.getItem(a);
                Item itemB = ItemRegistry.getItem(b);
                if (itemA == null || itemB == null) return 0;
                return itemA.getDisplayName(itemA.getDefaultItem(null, 1))
                    .compareToIgnoreCase(itemB.getDisplayName(itemB.getDefaultItem(null, 1)));
            });
        }
        
        return categories;
    }
    
    /**
     * Check if item is a coin/currency
     */
    private boolean isCoin(String stringId, String displayName) {
        return stringId.contains("coin") || displayName.contains("coin") || 
               displayName.contains("currency") || displayName.contains("gold");
    }
    
    /**
     * Check if item is a bar/ingot
     */
    private boolean isBar(String stringId, String displayName) {
        return stringId.contains("bar") || displayName.contains("bar") ||
               stringId.contains("ingot") || displayName.contains("ingot");
    }
    
    /**
     * Check if item is a gem/crystal
     */
    private boolean isGem(String stringId, String displayName) {
        return stringId.contains("gem") || displayName.contains("gem") ||
               stringId.contains("crystal") || displayName.contains("crystal") ||
               stringId.contains("diamond") || displayName.contains("diamond") ||
               stringId.contains("ruby") || displayName.contains("ruby") ||
               stringId.contains("sapphire") || displayName.contains("sapphire") ||
               stringId.contains("emerald") || displayName.contains("emerald");
    }
    
    /**
     * Check if item is a raw material
     */
    private boolean isMaterial(String stringId, String displayName, Item item) {
        // Check item type and categories
        return stringId.contains("ore") || displayName.contains("ore") ||
               stringId.contains("wood") || displayName.contains("wood") ||
               stringId.contains("stone") || displayName.contains("stone") ||
               stringId.contains("fiber") || displayName.contains("fiber") ||
               stringId.contains("leather") || displayName.contains("leather") ||
               item.getStringID().startsWith("material");
    }
    
    /**
     * Populate dropdown with filtered materials
     */
    private void populateDropdown() {
        dropdown.options.clear();
        
        // Get materials for current category
        List<String> materials = categorizedMaterials.getOrDefault(currentCategory, new ArrayList<>());
        
        // Apply text filter if any
        if (!currentFilter.isEmpty()) {
            String filterLower = currentFilter.toLowerCase();
            materials = materials.stream()
                .filter(stringId -> {
                    Item item = ItemRegistry.getItem(stringId);
                    if (item == null) return false;
                    return item.getDisplayName(item.getDefaultItem(null, 1)).toLowerCase().contains(filterLower) ||
                           stringId.toLowerCase().contains(filterLower);
                })
                .collect(Collectors.toList());
        }
        
        // Add filtered materials to dropdown (limit to 100 for performance)
        int count = 0;
        for (String stringId : materials) {
            if (count++ >= 100) break; // Limit dropdown size
            
            Item item = ItemRegistry.getItem(stringId);
            if (item != null) {
                dropdown.options.add(stringId, new StaticMessage(item.getDisplayName(item.getDefaultItem(null, 1))));
            }
        }
        
        // If no items match, show message
        if (dropdown.options.size() == 0) {
            dropdown.options.add("", new StaticMessage("No materials found"));
        }
    }
    
    /**
     * Set category filter
     */
    public void setCategory(String category) {
        this.currentCategory = category;
        populateDropdown();
    }
    
    /**
     * Set text filter
     */
    public void setTextFilter(String filter) {
        this.currentFilter = filter != null ? filter : "";
        populateDropdown();
    }
    
    /**
     * Get selected material string ID
     */
    public String getSelected() {
        return dropdown.getSelected();
    }
    
    /**
     * Set selected material
     */
    public void setSelected(String stringId) {
        if (stringId != null && !stringId.isEmpty()) {
            Item item = ItemRegistry.getItem(stringId);
            if (item != null) {
                dropdown.setSelected(stringId, new StaticMessage(item.getDisplayName(item.getDefaultItem(null, 1))));
            }
        }
    }
    
    /**
     * Get the underlying FormDropdownSelectionButton component
     */
    public FormDropdownSelectionButton<String> getComponent() {
        return dropdown;
    }
}
