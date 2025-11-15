package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.worldclick.WorldClickHandler;
import medievalsim.commandcenter.worldclick.WorldClickIntegration;
import medievalsim.commandcenter.wrapper.ParameterMetadata;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.client.Client;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.ui.ButtonColor;

/**
 * Widget for RELATIVE_INT parameter type (single coordinate input).
 * 
 * Supports both absolute coordinates (1000) and relative syntax (%+100, %-50).
 * Unlike the old CoordinateInputWidget, this handles ONE coordinate value,
 * matching Necesse's architecture where X and Y are separate parameters.
 * 
 * Enhanced with world-click coordinate selection:
 *   - "Click World" button: Click on game world to select coordinate
 *   - "Use Current" button: Use player's current position
 * 
 * Examples:
 *   - Absolute: Type "1000" → Returns "1000"
 *   - Relative positive: Type "%+100" → Returns "%+100"
 *   - Relative negative: Type "%-50" → Returns "%-50"
 */
public class RelativeIntInputWidget extends ParameterWidget {
    
    private FormTextInput input;
    private FormTextButton clickWorldButton;
    private FormTextButton useCurrentButton;
    private Client client;
    
    private static final int INPUT_WIDTH = 100;
    private static final int BUTTON_WIDTH = 70;
    private static final int BUTTON_SPACING = 5;
    
    /**
     * Constructor
     * 
     * @param parameter Parameter metadata
     * @param x X position
     * @param y Y position
     */
    public RelativeIntInputWidget(ParameterMetadata parameter, int x, int y) {
        super(parameter);
        
        // Single text input for coordinate
        input = new FormTextInput(
            x, y,
            FormInputSize.SIZE_16,
            INPUT_WIDTH,
            200,
            10
        );
        input.placeHolder = new StaticMessage("e.g. 1000 or %+100");
        
        // "Click World" button (to the right of input)
        int buttonX = x + INPUT_WIDTH + BUTTON_SPACING;
        clickWorldButton = new FormTextButton(
            "Click World",
            buttonX, y,
            BUTTON_WIDTH, FormInputSize.SIZE_16,
            ButtonColor.BASE
        );
        clickWorldButton.onClicked(btn -> onClickWorldPressed());
        
        // "Use Current" button (to the right of Click World)
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
            System.err.println("[RelativeIntInputWidget] Cannot start world-click: client is null");
            return;
        }
        
        // Start world-click selection mode
        WorldClickHandler.getInstance().startSelection(client, (tileX, tileY) -> {
            // Callback: user clicked a tile
            // Determine which coordinate this widget represents based on parameter name
            String paramName = parameter.getName().toLowerCase();
            
            if (paramName.contains("x") || paramName.equals("tilexoffset")) {
                input.setText(String.valueOf(tileX));
            } else if (paramName.contains("y") || paramName.equals("tileyoffset")) {
                input.setText(String.valueOf(tileY));
            } else {
                // Fallback: if we can't determine, use X
                input.setText(String.valueOf(tileX));
            }
            
            validate();
        });
        
        // Register integration (overlay, tick, input hooks)
        WorldClickIntegration.startIntegration(client);
    }
    
    /**
     * Called when "Use Current" button is pressed
     */
    private void onUseCurrentPressed() {
        if (client == null) {
            System.err.println("[RelativeIntInputWidget] Cannot use current position: client is null");
            return;
        }
        
        PlayerMob player = client.getPlayer();
        if (player == null) {
            System.err.println("[RelativeIntInputWidget] Cannot use current position: player is null");
            return;
        }
        
        // Get player's tile position
        int tileX = (int) (player.x / 32);
        int tileY = (int) (player.y / 32);
        
        // Determine which coordinate this widget represents
        String paramName = parameter.getName().toLowerCase();
        
        if (paramName.contains("x") || paramName.equals("tilexoffset")) {
            input.setText(String.valueOf(tileX));
        } else if (paramName.contains("y") || paramName.equals("tileyoffset")) {
            input.setText(String.valueOf(tileY));
        } else {
            // Fallback: if we can't determine, use X
            input.setText(String.valueOf(tileX));
        }
        
        validate();
    }
    
    @Override
    public FormComponent getComponent() {
        return input;
    }
    
    /**
     * Get the "Click World" button component
     * CommandCenterPanel should add this to the form separately
     */
    public FormTextButton getClickWorldButton() {
        return clickWorldButton;
    }
    
    /**
     * Get the "Use Current" button component
     * CommandCenterPanel should add this to the form separately
     */
    public FormTextButton getUseCurrentButton() {
        return useCurrentButton;
    }
    
    @Override
    public String getValue() {
        String value = input.getText().trim();
        
        // DON'T return "0" for empty - let validation handle it!
        // If parameter is required, validation will catch empty value
        // If parameter is optional, buildCommandString will skip it
        return value;
    }
    
    @Override
    public void setValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            input.setText("");
        } else {
            input.setText(value.trim());
        }
    }
    
    @Override
    protected boolean validateValue() {
        String value = input.getText().trim();
        
        // Empty is valid if not required
        if (value.isEmpty()) {
            return !parameter.isRequired();
        }
        
        // Check if it's relative syntax (%+100 or %-50)
        if (value.startsWith("%")) {
            String numPart = value.substring(1);
            if (numPart.isEmpty()) {
                validationError = "Relative syntax requires a number (e.g. %+100)";
                return false;
            }
            
            // Must have + or - prefix
            if (!numPart.startsWith("+") && !numPart.startsWith("-")) {
                validationError = "Relative syntax requires %+ or %- (e.g. %+100)";
                return false;
            }
            
            // Validate the number part
            String number = numPart.substring(1);
            if (number.isEmpty()) {
                validationError = "Relative syntax requires a number after +/- (e.g. %+100)";
                return false;
            }
            
            try {
                Integer.parseInt(number);
                return true;
            } catch (NumberFormatException e) {
                validationError = "Invalid number in relative syntax: " + number;
                return false;
            }
        } else {
            // Must be a valid integer (absolute coordinate)
            try {
                Integer.parseInt(value);
                return true;
            } catch (NumberFormatException e) {
                validationError = "Must be an integer or %+N syntax (e.g. 1000 or %+100)";
                return false;
            }
        }
    }
    
    @Override
    public void reset() {
        input.setText("");
        isValid = parameter.isOptional();
        validationError = null;
    }
    
    @Override
    public void onFocus() {
        input.setTyping(true);
    }
    
    @Override
    public void onBlur() {
        validate();
    }
}
