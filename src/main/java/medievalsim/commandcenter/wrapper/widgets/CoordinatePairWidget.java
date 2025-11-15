package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.worldclick.WorldClickHandler;
import medievalsim.commandcenter.worldclick.WorldClickIntegration;
import medievalsim.ui.fixes.InputFocusManager;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.client.Client;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.ui.ButtonColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordinate pair widget for commands that need both X and Y coordinates.
 * 
 * Combines two RELATIVE_INT parameters into a single UI with shared buttons:
 *   - "Click World" button: Click on game world to fill both X and Y
 *   - "Use Current" button: Use player's current position for both X and Y
 * 
 * This provides better UX than having separate inputs with separate buttons.
 * 
 * Used for commands like /setposition that have both tileX and tileY parameters.
 */
public class CoordinatePairWidget {
    
    private InputFocusManager.EnhancedTextInput xInput;
    private InputFocusManager.EnhancedTextInput yInput;
    private FormTextButton clickWorldButton;
    private FormTextButton useCurrentButton;
    private Client client;
    
    private String xParamName;
    private String yParamName;
    private boolean xRequired;
    private boolean yRequired;
    
    private static final int INPUT_WIDTH = 100;
    private static final int INPUT_SPACING = 10;
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_SPACING = 5;
    
    /**
     * Constructor
     * 
     * @param x X position for the widget
     * @param y Y position for the widget
     * @param xParamName Name of X parameter (for validation messages)
     * @param yParamName Name of Y parameter (for validation messages)
     * @param xRequired Is X parameter required?
     * @param yRequired Is Y parameter required?
     */
    public CoordinatePairWidget(int x, int y, String xParamName, String yParamName, 
                               boolean xRequired, boolean yRequired) {
        this.xParamName = xParamName;
        this.yParamName = yParamName;
        this.xRequired = xRequired;
        this.yRequired = yRequired;
        
        // X coordinate input
        xInput = new InputFocusManager.EnhancedTextInput(
            x, y,
            FormInputSize.SIZE_16,
            INPUT_WIDTH,
            200,
            10
        );
        xInput.placeHolder = new StaticMessage("X (e.g. 1000)");
        
        // Y coordinate input (to the right of X)
        int yInputX = x + INPUT_WIDTH + INPUT_SPACING;
        yInput = new InputFocusManager.EnhancedTextInput(
            yInputX, y,
            FormInputSize.SIZE_16,
            INPUT_WIDTH,
            200,
            10
        );
        yInput.placeHolder = new StaticMessage("Y (e.g. 1000)");
        
        // "Click World" button (fills both X and Y from clicked tile)
        int buttonX = yInputX + INPUT_WIDTH + BUTTON_SPACING;
        clickWorldButton = new FormTextButton(
            "Click World",
            buttonX, y,
            BUTTON_WIDTH, FormInputSize.SIZE_16,
            ButtonColor.BASE
        );
        clickWorldButton.onClicked(btn -> onClickWorldPressed());
        
        // "Use Current" button (fills both X and Y from player position)
        buttonX += BUTTON_WIDTH + BUTTON_SPACING;
        useCurrentButton = new FormTextButton(
            "Current Pos",
            buttonX, y,
            BUTTON_WIDTH, FormInputSize.SIZE_16,
            ButtonColor.BASE
        );
        useCurrentButton.onClicked(btn -> onUseCurrentPressed());
    }
    
    /**
     * Set the client instance (needed for world-click and current position)
     */
    public void setClient(Client client) {
        this.client = client;
    }
    
    /**
     * Called when "Click World" button is pressed
     */
    private void onClickWorldPressed() {
        if (client == null) {
            System.err.println("[CoordinatePairWidget] Cannot start world-click: client is null");
            return;
        }
        
        // Start world-click selection mode
        WorldClickHandler.getInstance().startSelection(client, (tileX, tileY) -> {
            // Fill both inputs with clicked coordinates
            xInput.setText(String.valueOf(tileX));
            yInput.setText(String.valueOf(tileY));
        });
        
        // Register integration (overlay, tick, input hooks)
        WorldClickIntegration.startIntegration(client);
    }
    
    /**
     * Called when "Use Current" button is pressed
     */
    private void onUseCurrentPressed() {
        if (client == null) {
            System.err.println("[CoordinatePairWidget] Cannot use current position: client is null");
            return;
        }
        
        PlayerMob player = client.getPlayer();
        if (player == null) {
            System.err.println("[CoordinatePairWidget] Cannot use current position: player is null");
            return;
        }
        
        // Get player's tile position
        int tileX = (int) (player.x / 32);
        int tileY = (int) (player.y / 32);
        
        // Fill both inputs
        xInput.setText(String.valueOf(tileX));
        yInput.setText(String.valueOf(tileY));
    }
    
    /**
     * Get X coordinate value
     */
    public String getXValue() {
        String value = xInput.getText().trim();
        return value.isEmpty() ? "" : value;
    }
    
    /**
     * Get Y coordinate value
     */
    public String getYValue() {
        String value = yInput.getText().trim();
        return value.isEmpty() ? "" : value;
    }
    
    /**
     * Set X coordinate value
     */
    public void setXValue(String value) {
        xInput.setText(value == null ? "" : value.trim());
    }
    
    /**
     * Set Y coordinate value
     */
    public void setYValue(String value) {
        yInput.setText(value == null ? "" : value.trim());
    }
    
    /**
     * Get all form components for this widget
     */
    public List<FormComponent> getComponents() {
        List<FormComponent> components = new ArrayList<>();
        components.add(xInput);
        components.add(yInput);
        components.add(clickWorldButton);
        components.add(useCurrentButton);
        return components;
    }
    
    /**
     * Get the X input component
     */
    public InputFocusManager.EnhancedTextInput getXInput() {
        return xInput;
    }
    
    /**
     * Get the Y input component
     */
    public InputFocusManager.EnhancedTextInput getYInput() {
        return yInput;
    }
    
    /**
     * Get the "Click World" button
     */
    public FormTextButton getClickWorldButton() {
        return clickWorldButton;
    }
    
    /**
     * Get the "Use Current" button
     */
    public FormTextButton getUseCurrentButton() {
        return useCurrentButton;
    }
    
    /**
     * Reset both inputs to empty
     */
    public void reset() {
        xInput.setText("");
        yInput.setText("");
    }
    
    /**
     * Validate coordinate syntax for both X and Y
     * 
     * @return Error message if invalid, null if valid
     */
    public String validate() {
        String xValue = getXValue();
        String yValue = getYValue();
        
        // Check required parameters
        if (xRequired && xValue.isEmpty()) {
            return (xParamName != null ? xParamName : "X coordinate") + " is required";
        }
        if (yRequired && yValue.isEmpty()) {
            return (yParamName != null ? yParamName : "Y coordinate") + " is required";
        }
        
        // Validate X syntax if provided
        if (!xValue.isEmpty()) {
            String xError = validateCoordinate(xValue, xParamName != null ? xParamName : "X");
            if (xError != null) return xError;
        }
        
        // Validate Y syntax if provided
        if (!yValue.isEmpty()) {
            String yError = validateCoordinate(yValue, yParamName != null ? yParamName : "Y");
            if (yError != null) return yError;
        }
        
        return null; // Valid
    }
    
    /**
     * Validate a single coordinate value (supports absolute and relative syntax)
     */
    private String validateCoordinate(String value, String coordName) {
        value = value.trim();
        
        // Check if it's relative syntax (%+100 or %-50)
        if (value.startsWith("%")) {
            String numPart = value.substring(1);
            if (numPart.isEmpty()) {
                return coordName + ": Relative syntax requires a number (e.g. %+100)";
            }
            
            // Must have + or - prefix
            if (!numPart.startsWith("+") && !numPart.startsWith("-")) {
                return coordName + ": Relative syntax requires %+ or %- (e.g. %+100)";
            }
            
            // Validate the number part
            String number = numPart.substring(1);
            if (number.isEmpty()) {
                return coordName + ": Relative syntax requires a number after +/- (e.g. %+100)";
            }
            
            try {
                Integer.parseInt(number);
                return null; // Valid
            } catch (NumberFormatException e) {
                return coordName + ": Invalid number in relative syntax: " + number;
            }
        } else {
            // Must be a valid integer (absolute coordinate)
            try {
                Integer.parseInt(value);
                return null; // Valid
            } catch (NumberFormatException e) {
                return coordName + ": Must be an integer or %+N syntax (e.g. 1000 or %+100)";
            }
        }
    }
}
