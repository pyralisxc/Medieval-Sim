package medievalsim.ui.helpers;

import necesse.engine.localization.message.StaticMessage;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormInputSize;
import medievalsim.ui.fixes.InputFocusManager;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.ui.ButtonColor;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Searchable dropdown component with real-time filtering
 * 
 * Combines a text input field with a filtered list of options.
 * Filters automatically as the user types (uses tick-based polling).
 * 
 * @param <T> The type of items in the dropdown
 */
public class SearchableDropdown<T> {
    
    private final InputFocusManager.EnhancedTextInput searchInput;
    private final FormContentBox resultsBox;
    private final List<T> allItems;
    private final List<T> filteredItems;
    private final Function<T, String> displayFunction; // Convert item to display text
    private final Consumer<T> onSelected; // Callback when item selected
    
    private String lastFilter = "";
    private T selectedItem = null;
    private int tickCounter = 0;
    private boolean isDropdownOpen = false; // Track dropdown visibility
    private boolean hadFocusLastTick = false; // Track focus changes
    private Form parentForm = null; // Track parent form for add/remove
    private boolean isUpdating = false; // Block dropdown during rebuild operations
    
    /**
     * Create a searchable dropdown
     * 
     * @param x X position
     * @param y Y position
     * @param width Width of the component
     * @param dropdownHeight Height of the dropdown results area
     * @param placeholder Placeholder text for search field
     * @param items List of all items
     * @param displayFunction Function to convert item to display string
     * @param onSelected Callback when item is selected
     */
    public SearchableDropdown(int x, int y, int width, int dropdownHeight,
                             String placeholder, List<T> items,
                             Function<T, String> displayFunction,
                             Consumer<T> onSelected) {
        this.allItems = new ArrayList<>(items);
        this.filteredItems = new ArrayList<>(items);
        this.displayFunction = displayFunction;
        this.onSelected = onSelected;
        
        // Search input field (FormTextInput constructor: x, y, size, width, maxLength)
        this.searchInput = new InputFocusManager.EnhancedTextInput(x, y, FormInputSize.SIZE_32, width, 100);
        this.searchInput.placeHolder = new StaticMessage(placeholder);
        // Filter on submit (when user presses Enter)
        this.searchInput.onSubmit(e -> filterItems(this.searchInput.getText()));
        
        // Results box (scrollable list) with opaque background - will be added/removed dynamically
        this.resultsBox = new FormContentBox(x, y + 40, width, dropdownHeight, 
            necesse.gfx.GameBackground.textBox);
        buildResultsList();
    }
    
    /**
     * Tick update - poll for text changes and filter automatically
     * Also handles showing/hiding dropdown based on focus
     * Call this from the parent panel's tick/update method
     */
    public void tick(TickManager tickManager) {
        tickCounter++;
        
        // Skip dropdown logic during rebuild operations
        if (isUpdating) {
            return;
        }
        
        // Check if search input has focus (is being typed in)
        boolean hasFocus = searchInput.isTyping();
        
        // Detect focus change - open dropdown when clicking in field
        if (hasFocus && !hadFocusLastTick) {
            openDropdown();
        } else if (!hasFocus && hadFocusLastTick) {
            closeDropdown();
        }
        hadFocusLastTick = hasFocus;
        
        // Check for text changes every 5 ticks (smooth but not too often)
        if (tickCounter % 5 == 0) {
            String currentText = searchInput.getText();
            if (!currentText.equals(lastFilter)) {
                lastFilter = currentText;
                filterItems(currentText);
            }
        }
    }
    
    /**
     * Open the dropdown results list
     */
    private void openDropdown() {
        if (!isDropdownOpen && parentForm != null) {
            // Add with high z-index (100) so dropdown renders on top of other components
            parentForm.addComponent(resultsBox, 100);
            isDropdownOpen = true;
        }
    }
    
    /**
     * Close the dropdown results list
     */
    private void closeDropdown() {
        if (isDropdownOpen && parentForm != null) {
            parentForm.removeComponent(resultsBox);
            isDropdownOpen = false;
        }
    }
    
    /**
     * Filter items based on search text
     */
    private void filterItems(String filter) {
        String filterLower = filter.toLowerCase();
        this.filteredItems.clear();
        
        if (filter.trim().isEmpty()) {
            this.filteredItems.addAll(allItems);
        } else {
            for (T item : allItems) {
                String displayText = displayFunction.apply(item).toLowerCase();
                if (displayText.contains(filterLower)) {
                    filteredItems.add(item);
                }
            }
        }
        
        buildResultsList();
    }
    
    /**
     * Rebuild the results list UI
     */
    private void buildResultsList() {
        resultsBox.clearComponents();
        
        int yPos = 5;
        int buttonWidth = resultsBox.getWidth() - 10;
        
        for (T item : filteredItems) {
            String displayText = displayFunction.apply(item);
            
            FormTextButton itemButton = new FormTextButton(
                displayText,
                5, yPos,
                buttonWidth, FormInputSize.SIZE_20,
                ButtonColor.BASE
            );
            
            itemButton.onClicked(e -> {
                selectedItem = item;
                searchInput.setText(displayText);
                
                // Stop typing mode to unfocus the search input
                searchInput.setTyping(false);
                hadFocusLastTick = false;
                
                // Trigger callback BEFORE closing dropdown to ensure proper UI updates
                if (onSelected != null) {
                    onSelected.accept(item);
                }
                
                // Close dropdown after callback completes
                closeDropdown();
            });
            
            resultsBox.addComponent(itemButton);
            yPos += 25;
        }
        
        // Update content bounds
        int contentHeight = Math.max(filteredItems.size() * 25 + 10, resultsBox.getHeight());
        resultsBox.setContentBox(new Rectangle(0, 0, resultsBox.getWidth(), contentHeight));
    }
    
    /**
     * Add this dropdown to a parent form
     */
    public void addToForm(necesse.gfx.forms.Form form) {
        this.parentForm = form;
        form.addComponent(searchInput);
        // Don't add resultsBox here - it will be added when user clicks in the search field
    }
    
    /**
     * Remove from parent form
     */
    public void removeFromForm(necesse.gfx.forms.Form form) {
        closeDropdown(); // Make sure dropdown is closed first
        form.removeComponent(searchInput);
        this.parentForm = null;
    }
    
    /**
     * Get the search input component
     */
    public InputFocusManager.EnhancedTextInput getSearchInput() {
        return searchInput;
    }
    
    /**
     * Get the results box component
     */
    public FormContentBox getResultsBox() {
        return resultsBox;
    }
    
    /**
     * Get currently selected item
     */
    public T getSelectedItem() {
        return selectedItem;
    }
    
    /**
     * Get the text typed in the search input (manual input)
     * This allows users to type values directly instead of selecting from dropdown
     * 
     * @return The text currently in the search input field
     */
    public String getTypedText() {
        return searchInput.getText();
    }
    
    /**
     * Check if user has typed text (manual input) without selecting an item
     * 
     * @return true if there's typed text and no item selected
     */
    public boolean hasManualInput() {
        String typed = searchInput.getText().trim();
        return !typed.isEmpty() && selectedItem == null;
    }
    
    /**
     * Set selected item programmatically
     */
    public void setSelectedItem(T item) {
        this.selectedItem = item;
        if (item != null) {
            searchInput.setText(displayFunction.apply(item));
        } else {
            searchInput.setText("");
        }
    }
    
    /**
     * Clear search and show all items
     */
    public void reset() {
        searchInput.setText("");
        lastFilter = "";
        filterItems("");
    }
    
    /**
     * Begin a rebuild operation (blocks dropdown from opening)
     */
    public void beginUpdate() {
        isUpdating = true;
    }
    
    /**
     * End a rebuild operation (allows dropdown to open again)
     */
    public void endUpdate() {
        isUpdating = false;
    }
    
    /**
     * Update the full list of items (refresh data)
     */
    public void updateItems(List<T> newItems) {
        this.allItems.clear();
        this.allItems.addAll(newItems);
        
        // Reset search filter and close dropdown
        searchInput.setText("");
        lastFilter = "";
        hadFocusLastTick = false;
        closeDropdown();
        
        // Rebuild with all new items
        filterItems("");
    }
}
