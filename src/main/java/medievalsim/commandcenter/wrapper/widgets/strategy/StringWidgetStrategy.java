package medievalsim.commandcenter.wrapper.widgets.strategy;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import medievalsim.commandcenter.wrapper.ParameterMetadata.ParameterHandlerType;
import medievalsim.commandcenter.wrapper.widgets.ParameterWidget;
import medievalsim.commandcenter.wrapper.widgets.TextInputWidget;
import medievalsim.commandcenter.wrapper.widgets.DropdownWidget;
import necesse.engine.network.client.Client;

/**
 * Strategy for creating string-based input widgets.
 * Handles STRING, PRESET_STRING, REST_STRING, and other text-based parameters.
 */
public class StringWidgetStrategy implements WidgetCreationStrategy {
    
    @Override
    public ParameterWidget createWidget(ParameterMetadata parameter, int x, int y, Client client, String commandId) {
        ParameterHandlerType type = parameter.getHandlerType();

        // REST_STRING is genuine free text (chat messages, etc.)
        if (type == ParameterHandlerType.REST_STRING) {
            int width = getWidthForStringType(type);
            return new TextInputWidget(parameter, x, y, width, null);
        }

        // If the handler exposes preset values, use a simple dropdown
        if (parameter.hasPresets()) {
            return new DropdownWidget(parameter, x, y, parameter.getPresets());
        }

        // Fallback: plain text for identifiers with no preset or autocomplete set
        int width = getWidthForStringType(type);
        return new TextInputWidget(parameter, x, y, width, null);
    }
    
    @Override
    public boolean canHandle(ParameterMetadata parameter) {
        ParameterHandlerType type = parameter.getHandlerType();
        return type == ParameterHandlerType.STRING ||
               type == ParameterHandlerType.PRESET_STRING ||
               type == ParameterHandlerType.REST_STRING ||
               type == ParameterHandlerType.STORED_PLAYER ||
               type == ParameterHandlerType.LEVEL_IDENTIFIER ||
               type == ParameterHandlerType.CMD_NAME ||
               type == ParameterHandlerType.UNBAN;
    }
    
    /**
     * Extract default value from parameter handler.
     */
    // Default extraction intentionally omitted for now; Necesse will
    // resolve defaults when executing the underlying chat command.
    
    /**
     * Get appropriate width for different string parameter types.
     */
    private int getWidthForStringType(ParameterHandlerType type) {
        switch (type) {
            case REST_STRING:
                return 400; // Wider for longer text
            case STORED_PLAYER:
            case LEVEL_IDENTIFIER:
                return 150; // Medium width for identifiers
            default:
                return 200; // Standard width
        }
    }
}
