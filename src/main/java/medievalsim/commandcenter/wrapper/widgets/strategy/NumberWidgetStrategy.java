package medievalsim.commandcenter.wrapper.widgets.strategy;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import medievalsim.commandcenter.wrapper.ParameterMetadata.ParameterHandlerType;
import medievalsim.commandcenter.wrapper.widgets.ParameterWidget;
import medievalsim.commandcenter.wrapper.widgets.NumberInputWidget;
import necesse.engine.network.client.Client;

/**
 * Strategy for creating number input widgets.
 * Handles INT, FLOAT, and RELATIVE_INT parameters.
 */
public class NumberWidgetStrategy implements WidgetCreationStrategy {
    
    @Override
    public ParameterWidget createWidget(ParameterMetadata parameter, int x, int y, Client client, String commandId) {
        ParameterHandlerType type = parameter.getHandlerType();
        String defaultValue = extractDefaultValue(parameter);
        
        switch (type) {
            case INT:
                return new NumberInputWidget(parameter, x, y, 100, false, defaultValue);
                
            case FLOAT:
                return new NumberInputWidget(parameter, x, y, 100, true, defaultValue);
                
            case RELATIVE_INT:
                // RelativeIntInputWidget is a specialized widget for relative coordinates
                return new medievalsim.commandcenter.wrapper.widgets.RelativeIntInputWidget(parameter, x, y);
                
            default:
                throw new IllegalArgumentException("Unsupported number type: " + type);
        }
    }
    
    @Override
    public boolean canHandle(ParameterMetadata parameter) {
        ParameterHandlerType type = parameter.getHandlerType();
        return type == ParameterHandlerType.INT ||
               type == ParameterHandlerType.FLOAT ||
               type == ParameterHandlerType.RELATIVE_INT;
    }
    
    private String extractDefaultValue(ParameterMetadata parameter) {
        // Simplified extraction - could be enhanced
        return null;
    }
}
