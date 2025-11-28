package medievalsim.registries;

import medievalsim.grandexchange.commands.DumpOrderBooksCommand;
import medievalsim.grandexchange.commands.MarketDepthCommand;
import medievalsim.commandcenter.service.CommandRegistry;
import medievalsim.util.ModLogger;

/**
 * Register AdminCommands for Medieval Sim (Command Center)
 */
public class MedievalSimAdminCommands {
    public static void registerCore() {
        try {
            CommandRegistry.register(new DumpOrderBooksCommand());
            // MarketDepthCommand is useful too
            CommandRegistry.register(new MarketDepthCommand());
            ModLogger.debug("Registered GE admin commands: DumpOrderBooks, MarketDepth");
        } catch (Exception e) {
            ModLogger.error("Failed to register admin commands", e);
        }
    }
}
