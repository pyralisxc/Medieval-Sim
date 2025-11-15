package medievalsim.commandcenter.wrapper.widgets.strategy;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import medievalsim.commandcenter.wrapper.ParameterMetadata.ParameterHandlerType;
import medievalsim.commandcenter.wrapper.widgets.ParameterWidget;
import medievalsim.commandcenter.wrapper.widgets.TextInputWidget;
import medievalsim.util.ModLogger;
import necesse.engine.network.client.Client;

/**
 * Fallback strategy for unsupported or filtered parameter types.
 * 
 * This handles creative-focused parameters that were filtered out of the command center
 * (like ITEM, BUFF, ENCHANTMENT) and unknown types, providing a text input fallback.
 */
public class FallbackWidgetStrategy implements WidgetCreationStrategy {
    
    @Override
    public ParameterWidget createWidget(ParameterMetadata parameter, int x, int y, Client client, String commandId) {
        ParameterHandlerType type = parameter.getHandlerType();
        
        // Log warning for filtered creative types
        if (isFilteredCreativeType(type)) {
            ModLogger.warn("Creating fallback widget for filtered creative parameter type: %s in command: %s", 
                    type, commandId != null ? commandId : "unknown");
        }
        
        // Create simple text input as fallback
        return new TextInputWidget(parameter, x, y, 200, null);
    }
    
    @Override
    public boolean canHandle(ParameterMetadata parameter) {
        // This strategy handles everything as a fallback
        return true;
    }
    
    /**
     * Check if this is a creative-focused parameter type that was filtered out.
     */
    private boolean isFilteredCreativeType(ParameterHandlerType type) {
        return type == ParameterHandlerType.ITEM ||
               type == ParameterHandlerType.BUFF ||
               type == ParameterHandlerType.ENCHANTMENT ||
               type == ParameterHandlerType.ARMOR_SET ||
               type == ParameterHandlerType.BIOME ||
               type == ParameterHandlerType.TILE;
    }
}