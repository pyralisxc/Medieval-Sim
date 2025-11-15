package medievalsim.commandcenter.wrapper.widgets.strategy;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import medievalsim.commandcenter.wrapper.widgets.ParameterWidget;
import necesse.engine.network.client.Client;

/**
 * Strategy interface for creating parameter widgets.
 * 
 * This enables a clean separation of widget creation logic and makes the system
 * easily extensible for new parameter types without modifying the factory.
 */
public interface WidgetCreationStrategy {
    
    /**
     * Create a parameter widget for the given metadata.
     * 
     * @param parameter The parameter metadata
     * @param x X position for the widget
     * @param y Y position for the widget
     * @param client The client instance (may be null for some strategies)
     * @param commandId The command ID for context-aware creation (may be null)
     * @return A configured ParameterWidget instance
     */
    ParameterWidget createWidget(ParameterMetadata parameter, int x, int y, Client client, String commandId);
    
    /**
     * Get the recommended height for widgets created by this strategy.
     * 
     * @return Recommended height in pixels
     */
    default int getRecommendedHeight() {
        return 30; // Standard height
    }
    
    /**
     * Check if this strategy can handle the given parameter type.
     * 
     * @param parameter The parameter metadata to check
     * @return true if this strategy can create a widget for this parameter
     */
    boolean canHandle(ParameterMetadata parameter);
}