package medievalsim.commandcenter.wrapper;

import medievalsim.commandcenter.CommandCategory;
import medievalsim.util.ModLogger;

import java.util.Map;

/**
 * Test utility for verifying NecesseCommandRegistry metadata parsing.
 * Call from postInit() to log command information.
 */
public class CommandMetadataTest {
    
    /**
     * Print detailed statistics about parsed commands.
     */
    public static void printStatistics() {
        if (!NecesseCommandRegistry.isInitialized()) {
            ModLogger.error("CommandMetadataTest: Registry not initialized!");
            return;
        }
        
        ModLogger.info("=== Necesse Command Registry Statistics ===");
        
        Map<String, Integer> stats = NecesseCommandRegistry.getStatistics();
        ModLogger.info("Total commands parsed: %d", stats.get("total_commands"));
        ModLogger.info("Total categories: %d", stats.get("total_categories"));
        
        ModLogger.info("\nCommands by category:");
        for (CommandCategory category : NecesseCommandRegistry.getAvailableCategories()) {
            int count = NecesseCommandRegistry.getCommandsByCategory(category).size();
            ModLogger.info("  %s: %d commands", category.getDisplayName(), count);
        }
        
        ModLogger.info("===========================================");
    }
    
    /**
     * Print detailed information about a specific command.
     */
    public static void printCommandDetails(String commandId) {
        NecesseCommandMetadata cmd = NecesseCommandRegistry.getCommand(commandId);
        
        if (cmd == null) {
            ModLogger.warn("Command not found: %s", commandId);
            return;
        }
        
        ModLogger.info("\n=== Command Details: %s ===", commandId);
        ModLogger.info("Display Name: %s", cmd.getDisplayName());
        ModLogger.info("Description: %s", cmd.getAction());
        ModLogger.info("Permission: %s", cmd.getPermission());
        ModLogger.info("Is Cheat: %s", cmd.isCheat());
        ModLogger.info("Category: %s", cmd.getCategory().getDisplayName());
        ModLogger.info("Parameters (%d):", cmd.getParameters().size());
        
        for (ParameterMetadata param : cmd.getParameters()) {
            String optionalStr = param.isOptional() ? " (optional)" : " (required)";
            String worldClickStr = param.supportsWorldClick() ? " [supports world-click]" : "";
            String presetsStr = param.hasPresets() ? " [presets: " + String.join(", ", param.getPresets()) + "]" : "";
            
            ModLogger.info("  - %s: %s%s%s%s",
                    param.getName(),
                    param.getHandlerType(),
                    optionalStr,
                    worldClickStr,
                    presetsStr);
        }
        
        ModLogger.info("===================================\n");
    }
    
    /**
     * Test command string building.
     */
    public static void testCommandStringBuilder() {
        ModLogger.info("\n=== Testing Command String Builder ===");
        
        // Test setposition command
        NecesseCommandMetadata setPos = NecesseCommandRegistry.getCommand("setposition");
        if (setPos != null) {
            String cmd1 = setPos.buildCommandString(new String[]{"player1", "surface", "1000", "1000"});
            ModLogger.info("setposition test: %s", cmd1);
            
            String cmd2 = setPos.buildCommandString(new String[]{"player1", "", "%+100", "%-50"});
            ModLogger.info("setposition relative: %s", cmd2);
        }
        
        // Test give command
        NecesseCommandMetadata give = NecesseCommandRegistry.getCommand("give");
        if (give != null) {
            String cmd3 = give.buildCommandString(new String[]{"player1", "ironpickaxe", "10"});
            ModLogger.info("give test: %s", cmd3);
        }
        
        // Test teleport command
        NecesseCommandMetadata teleport = NecesseCommandRegistry.getCommand("teleport");
        if (teleport != null) {
            String cmd4 = teleport.buildCommandString(new String[]{"player1", "spawn"});
            ModLogger.info("teleport test: %s", cmd4);
        }
        
        ModLogger.info("======================================\n");
    }
    
    /**
     * Run all tests.
     */
    public static void runAllTests() {
        printStatistics();
        printCommandDetails("setposition");
        printCommandDetails("give");
        printCommandDetails("buff");
        printCommandDetails("teleport");
        testCommandStringBuilder();
    }
}
