package medievalsim.commandcenter.wrapper.widgets.strategy;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import medievalsim.commandcenter.wrapper.ParameterMetadata.ParameterHandlerType;
import medievalsim.commandcenter.wrapper.widgets.ParameterWidget;
import medievalsim.commandcenter.wrapper.widgets.ToggleButtonWidget;
import medievalsim.commandcenter.wrapper.widgets.EnumDropdownWidget;
import medievalsim.commandcenter.wrapper.widgets.LanguageDropdownWidget;
import medievalsim.commandcenter.wrapper.widgets.PermissionLevelDropdownWidget;
import medievalsim.commandcenter.wrapper.widgets.MultiChoiceWidget;
import necesse.engine.network.client.Client;

/**
 * Strategy for creating specialized dropdown and selection widgets.
 * Handles BOOL, ENUM, LANGUAGE, PERMISSION_LEVEL, and MULTI parameters.
 */
public class SpecializedWidgetStrategy implements WidgetCreationStrategy {
    
    @Override
    public ParameterWidget createWidget(ParameterMetadata parameter, int x, int y, Client client, String commandId) {
        ParameterHandlerType type = parameter.getHandlerType();
        
        switch (type) {
            case BOOL:
                String defaultValue = extractDefaultValue(parameter);
                return new ToggleButtonWidget(parameter, x, y, client, defaultValue);
                
            case ENUM:
                return new EnumDropdownWidget(parameter, x, y);
                
            case LANGUAGE:
                return new LanguageDropdownWidget(parameter, x, y);
                
            case PERMISSION_LEVEL:
                return new PermissionLevelDropdownWidget(parameter, x, y);
                
            case MULTI:
                return new MultiChoiceWidget(parameter, x, y, client);
                
            default:
                throw new IllegalArgumentException("Unsupported specialized type: " + type);
        }
    }
    
    @Override
    public int getRecommendedHeight() {
        // Multi-choice widgets may need more space
        return 30; // Standard for now, could be parameter-specific
    }
    
    @Override
    public boolean canHandle(ParameterMetadata parameter) {
        ParameterHandlerType type = parameter.getHandlerType();
        return type == ParameterHandlerType.BOOL ||
               type == ParameterHandlerType.ENUM ||
               type == ParameterHandlerType.LANGUAGE ||
               type == ParameterHandlerType.PERMISSION_LEVEL ||
               type == ParameterHandlerType.MULTI;
    }
    
    private String extractDefaultValue(ParameterMetadata parameter) {
        // Simplified extraction - could be enhanced with caching
        return null;
    }
}
