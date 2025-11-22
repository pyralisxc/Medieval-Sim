package medievalsim.commandcenter.wrapper.widgets.strategy;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import medievalsim.commandcenter.wrapper.ParameterMetadata.ParameterHandlerType;
import medievalsim.commandcenter.wrapper.widgets.ParameterWidget;
import medievalsim.commandcenter.wrapper.widgets.PlayerDropdownWidget;
import medievalsim.commandcenter.wrapper.widgets.TeamDropdownWidget;
import necesse.engine.network.client.Client;

/**
 * Strategy for creating player and team selection widgets.
 * Handles SERVER_CLIENT, CLIENT_CLIENT, and TEAM parameters.
 */
public class PlayerTeamWidgetStrategy implements WidgetCreationStrategy {
    
    @Override
    public ParameterWidget createWidget(ParameterMetadata parameter, int x, int y, Client client, String commandId) {
        ParameterHandlerType type = parameter.getHandlerType();
        
        switch (type) {
            case SERVER_CLIENT:
            case CLIENT_CLIENT:
                return new PlayerDropdownWidget(parameter, x, y, client);
                
            case TEAM:
                return new TeamDropdownWidget(parameter, x, y);
                
            default:
                throw new IllegalArgumentException("Unsupported player/team type: " + type);
        }
    }
    
    @Override
    public boolean canHandle(ParameterMetadata parameter) {
        ParameterHandlerType type = parameter.getHandlerType();
        return type == ParameterHandlerType.SERVER_CLIENT ||
               type == ParameterHandlerType.CLIENT_CLIENT ||
               type == ParameterHandlerType.TEAM;
    }
}
