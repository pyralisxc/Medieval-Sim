package medievalsim.commandcenter.registries;

import medievalsim.util.ModLogger;

/**
 * Registry for custom Medieval Sim commands.
 * These are commands specific to our mod that don't exist in base Necesse.
 * 
 * Command History and custom commands will use this system.
 */
public class MedievalSimCommands {
    
    public static void registerCommands() {
        ModLogger.info("Registering Medieval Sim custom commands...");

        // Custom commands can be added here in the future
        // The mod currently uses Necesse's built-in commands via the Command Center UI

        ModLogger.info("Medieval Sim custom commands registered");
    }
}
