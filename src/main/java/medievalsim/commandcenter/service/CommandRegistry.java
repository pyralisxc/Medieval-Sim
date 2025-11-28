package medievalsim.commandcenter.service;
import medievalsim.commandcenter.domain.CommandCategory;

import necesse.engine.commands.PermissionLevel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central registry for all Command Center commands
 * Organizes commands by category and provides filtering/search capabilities
 */
public class CommandRegistry {
    
    private static final Map<String, AdminCommand> commands = new ConcurrentHashMap<>();
    private static final Map<CommandCategory, List<AdminCommand>> commandsByCategory = new ConcurrentHashMap<>();
    
    /**
     * Register a command in the Command Center
     */
    public static void register(AdminCommand command) {
        commands.put(command.getId(), command);
        
        // Add to category map
        commandsByCategory
            .computeIfAbsent(command.getCategory(), k -> new ArrayList<>())
            .add(command);
    }
    
    /**
     * Get a command by ID
     */
    public static AdminCommand getCommand(String id) {
        return commands.get(id);
    }
    
    /**
     * Get all commands
     */
    public static Collection<AdminCommand> getAllCommands() {
        return commands.values();
    }
    
    /**
     * Get commands by category
     */
    public static List<AdminCommand> getCommandsByCategory(CommandCategory category) {
        return commandsByCategory.getOrDefault(category, Collections.emptyList());
    }
    
    /**
     * Get commands filtered by permission level
     */
    public static List<AdminCommand> getCommandsByPermission(PermissionLevel level) {
        return commands.values().stream()
            .filter(cmd -> cmd.getRequiredPermission().getLevel() <= level.getLevel())
            .collect(Collectors.toList());
    }
    
    /**
     * Search commands by name or description
     */
    public static List<AdminCommand> searchCommands(String query) {
        String lowerQuery = query.toLowerCase();
        return commands.values().stream()
            .filter(cmd -> 
                cmd.getDisplayName().toLowerCase().contains(lowerQuery) ||
                cmd.getDescription().toLowerCase().contains(lowerQuery)
            )
            .collect(Collectors.toList());
    }
    
    /**
     * Get all categories that have registered commands
     */
    public static Set<CommandCategory> getActiveCategories() {
        return commandsByCategory.keySet();
    }
    
    /**
     * Clear all registered commands (for testing)
     */
    public static void clear() {
        commands.clear();
        commandsByCategory.clear();
    }
}
