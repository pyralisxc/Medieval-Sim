package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.ui.ButtonColor;
import necesse.engine.network.client.Client;

/**
 * Toggle button widget for BOOL parameter types.
 * Displays current state visually with green "Enabled" or red "Disabled".
 * 
 * <p>Much clearer UX than checkbox - users can see what clicking will do.
 * Follows Necesse's WorldSettingsForm pattern for boolean settings.
 */
public class ToggleButtonWidget extends ParameterWidget {
    
    private FormTextButton button;
    private boolean currentState;
    private final Client client;
    private final int x;
    private final int y;
    
    /**
     * Create a toggle button widget.
     * 
     * @param parameter The parameter metadata
     * @param x X position
     * @param y Y position
     * @param client Client instance (to read current world settings)
     */
    public ToggleButtonWidget(ParameterMetadata parameter, int x, int y, Client client) {
        this(parameter, x, y, client, null);
    }
    
    /**
     * Create a toggle button widget with default value.
     * 
     * @param parameter The parameter metadata
     * @param x X position
     * @param y Y position
     * @param client Client instance (to read current world settings)
     * @param defaultValue Default value ("true" or "false", null to read from world)
     */
    public ToggleButtonWidget(ParameterMetadata parameter, int x, int y, Client client, String defaultValue) {
        super(parameter);
        this.client = client;
        this.x = x;
        this.y = y;
        
        // Try to get current value from world settings or use default
        if (defaultValue != null) {
            this.currentState = Boolean.parseBoolean(defaultValue);
        } else {
            this.currentState = getCurrentStateFromWorld(parameter.getName());
        }
        
        // Create button with current state
        createButton();
    }
    
    /**
     * Create or recreate the button with current state.
     */
    private void createButton() {
        this.button = new FormTextButton(
            getButtonText(),
            x, y,
            200, FormInputSize.SIZE_32,
            getButtonColor()
        );
        
        // Toggle on click
        button.onClicked(e -> {
            currentState = !currentState;
            createButton(); // Recreate to change color
        });
    }
    
    /**
     * Try to get current boolean value from world settings.
     * Returns false if parameter is not a world setting.
     */
    private boolean getCurrentStateFromWorld(String paramName) {
        if (client == null || client.worldSettings == null) {
            return false;
        }
        
        necesse.engine.world.WorldSettings settings = client.worldSettings;
        
        // Map parameter names to world settings
        try {
            switch (paramName.toLowerCase()) {
                case "creative":
                case "creativemode":
                    return settings.creativeMode;
                    
                case "hunger":
                case "playerhunger":
                    return settings.playerHunger;
                    
                case "cheats":
                case "allowcheats":
                    return settings.allowCheats;
                    
                case "pvp":
                case "forcedpvp":
                    return settings.forcedPvP;
                    
                case "mobspawns":
                case "disablemobspawns":
                    return settings.disableMobSpawns;
                    
                case "mobai":
                case "disablemobai":
                    return settings.disableMobAI;
                    
                case "survival":
                case "survivalmode":
                    return settings.survivalMode;
                    
                // Add more as needed
                default:
                    return false; // Default to false for unknown settings
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get button text based on current state.
     */
    private String getButtonText() {
        return currentState ? "Enabled" : "Disabled";
    }
    
    /**
     * Get button color based on current state.
     * Green for enabled, red for disabled.
     */
    private ButtonColor getButtonColor() {
        return currentState ? ButtonColor.GREEN : ButtonColor.RED;
    }
    
    /**
     * Update button appearance to reflect current state.
     */
    private void updateButtonAppearance() {
        // Recreate button (simpler than trying to change color)
        createButton();
    }
    
    @Override
    public String getValue() {
        return currentState ? "true" : "false";
    }
    
    @Override
    public void setValue(String value) {
        if (value == null) {
            currentState = false;
        } else {
            currentState = value.equalsIgnoreCase("true") || 
                          value.equals("1") || 
                          value.equalsIgnoreCase("yes");
        }
        updateButtonAppearance();
    }
    
    @Override
    public boolean validateValue() {
        // Boolean values are always valid
        return true;
    }
    
    @Override
    public void reset() {
        currentState = getCurrentStateFromWorld(parameter.getName());
        updateButtonAppearance();
        isValid = true;
        validationError = null;
    }
    
    @Override
    public void onFocus() {
        // Refresh current state from world when focused
        boolean worldState = getCurrentStateFromWorld(parameter.getName());
        if (worldState != currentState) {
            currentState = worldState;
            updateButtonAppearance();
        }
    }
    
    @Override
    public FormComponent getComponent() {
        return button;
    }
    
    /**
     * Set the toggle state.
     */
    public void setState(boolean state) {
        currentState = state;
        updateButtonAppearance();
    }
    
    /**
     * Get the current toggle state.
     */
    public boolean getState() {
        return currentState;
    }
}
