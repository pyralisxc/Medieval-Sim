package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import medievalsim.ui.fixes.InputFocusManager;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormDropdownSelectionButton;
import necesse.gfx.ui.ButtonColor;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.client.Client;
import necesse.engine.gameLoop.tickManager.TickManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Hybrid player input widget for SERVER_CLIENT parameters.
 * Allows both manual text input (for offline players) and quick selection from online players.
 */
public class PlayerDropdownWidget extends ParameterWidget {
    
    private InputFocusManager.EnhancedTextInput textInput; // Manual player name entry
    private FormDropdownSelectionButton<String> dropdown; // Quick selection from online players
    private Client client;
    private List<String> playerNames;
    
    /**
     * Create a player dropdown widget.
     * 
     * @param parameter The parameter metadata
     * @param x X position
     * @param y Y position
     * @param client The client instance (to get online players)
     */
    public PlayerDropdownWidget(ParameterMetadata parameter, int x, int y, Client client) {
        super(parameter);
        
        this.client = client;
        this.playerNames = new ArrayList<>();
        
        // Create text input for manual entry (supports offline players)
        this.textInput = new InputFocusManager.EnhancedTextInput(x, y, FormInputSize.SIZE_32, 200, 50);
        this.textInput.placeHolder = new StaticMessage("Type player name or use dropdown");
        
        // Create the dropdown for quick selection (online players only)
        this.dropdown = new FormDropdownSelectionButton<>(
            x, y + 40, // Position below text input
            FormInputSize.SIZE_16, 
            ButtonColor.BASE, 
            200, 
            new StaticMessage("Select from online players")
        );
        
        // Populate with online players
        refreshPlayerList();
        
        // Listen to dropdown selection - update text input when player selected
        dropdown.onSelected(event -> {
            if (event.value != null) {
                textInput.setText(event.value);
                currentValue = event.value;
                notifyValueChanged(); // Notify parent of value change for validation
            }
        });
        
        // Listen to text input changes for real-time validation
        textInput.onSubmit(event -> {
            currentValue = textInput.getText();
            notifyValueChanged(); // Notify parent of value change for validation
        });
    }
    
    /**
     * Refresh the player list from the current online players
     */
    public void refreshPlayerList() {
        playerNames.clear();
        dropdown.options.clear();
        
        // Add "Self" as first option (represents current player)
        playerNames.add("self");
        dropdown.options.add("self", new StaticMessage("Self (You)"));
        
        // Add all online players from client (client-side view of connected players)
        if (client != null) {
            try {
                // Try to get current player's name
                necesse.engine.network.client.ClientClient myClient = client.getClient();
                if (myClient != null && myClient.getName() != null) {
                    String myName = myClient.getName();
                    if (!playerNames.contains(myName) && !myName.equalsIgnoreCase("self")) {
                        playerNames.add(myName);
                        dropdown.options.add(myName, new StaticMessage(myName + " (You)"));
                    }
                }
                
                // Get online players from the client stream
                if (client.streamClients() != null) {
                    client.streamClients()
                        .filter(clientPlayer -> clientPlayer != null && clientPlayer.getName() != null)
                        .forEach(clientPlayer -> {
                            String playerName = clientPlayer.getName();
                            if (!playerNames.contains(playerName)) {
                                playerNames.add(playerName);
                                dropdown.options.add(playerName, new StaticMessage(playerName));
                            }
                        });
                }
            } catch (Exception e) {
                // Silently handle any errors - just show "self" option
                // This can happen if client stream isn't ready yet
            }
        }
        
        // Select "self" by default if required
        if (parameter.isRequired() && playerNames.size() > 0) {
            dropdown.setSelected("self", new StaticMessage("Self (You)"));
            currentValue = "self";
        }
    }
    
    @Override
    public String getValue() {
        // Priority 1: Manual text input (supports offline players)
        String typedText = textInput.getText().trim();
        if (!typedText.isEmpty()) {
            // Convert "self" to actual player name
            if (typedText.equalsIgnoreCase("self") && client != null) {
                // Get current player's client representation
                necesse.engine.network.client.ClientClient myClient = client.getClient();
                if (myClient != null && myClient.getName() != null) {
                    return myClient.getName();
                }
            }
            return typedText;
        }
        
        // Priority 2: Dropdown selection
        String selected = dropdown.getSelected();
        if (selected != null && !selected.isEmpty()) {
            // Convert "self" to actual player name
            if (selected.equalsIgnoreCase("self") && client != null) {
                necesse.engine.network.client.ClientClient myClient = client.getClient();
                if (myClient != null && myClient.getName() != null) {
                    return myClient.getName();
                }
            }
            return selected;
        }
        
        // Don't auto-default to "self" - let validation handle empty values
        return null;
    }
    
    @Override
    public void setValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            textInput.setText("");
            if (!parameter.isRequired()) {
                dropdown.setSelected(null, new StaticMessage(""));
            } else {
                textInput.setText("self");
                dropdown.setSelected("self", new StaticMessage("Self (You)"));
            }
        } else {
            textInput.setText(value);
            // If value is in dropdown, select it
            if (playerNames.contains(value)) {
                dropdown.setSelected(value, new StaticMessage(value));
            }
        }
        currentValue = value;
    }
    
    @Override
    public boolean validateValue() {
        String value = getValue();
        
        // Required params must have a value
        if (parameter.isRequired() && (value == null || value.trim().isEmpty())) {
            validationError = "Please enter or select a player name";
            return false;
        }
        
        // Accept any non-empty string (supports offline players)
        // No strict validation since offline players aren't in the dropdown
        
        validationError = null;
        return true;
    }
    
    @Override
    public necesse.gfx.forms.components.FormComponent getComponent() {
        return textInput; // Return text input as primary component
    }
    
    /**
     * Get the text input component
     */
    public InputFocusManager.EnhancedTextInput getTextInput() {
        return textInput;
    }
    
    /**
     * Get the underlying dropdown component
     */
    public FormDropdownSelectionButton<String> getDropdown() {
        return dropdown;
    }
    
    /**
     * Tick method to periodically refresh player list.
     * Call this every few seconds to keep the dropdown up-to-date.
     * 
     * @param tickManager Tick manager (not used, but standard pattern)
     */
    private int tickCounter = 0;
    private static final int REFRESH_INTERVAL = 60; // Refresh every 60 ticks (1 second at 60 FPS)
    
    public void tick(TickManager tickManager) {
        tickCounter++;
        
        // Refresh player list periodically
        if (tickCounter >= REFRESH_INTERVAL) {
            tickCounter = 0;
            
            // Store current selection to restore after refresh
            String previousSelection = textInput.getText();
            
            refreshPlayerList();
            
            // Restore previous selection if it's still valid
            if (previousSelection != null && !previousSelection.isEmpty()) {
                textInput.setText(previousSelection);
            }
        }
    }
}
