package medievalsim.commandcenter;

import medievalsim.commandcenter.wrapper.NecesseCommandMetadata;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.network.server.ServerClient;

/**
 * Centralized permission validation for Command Center operations
 */
public class CommandPermissions {
    
    /**
     * Check if a client can execute a specific Necesse command
     */
    public static boolean canExecuteCommand(ServerClient client, String commandId) {
        if (client == null) return false;
        
        // Get command metadata from Necesse registry
        NecesseCommandMetadata cmd = medievalsim.commandcenter.wrapper.NecesseCommandRegistry.getCommand(commandId);
        if (cmd == null) return false;
        
        // Check permission level
        return hasPermissionLevel(client, cmd.getPermission());
    }
    
    /**
     * Check if a client can execute a custom AdminCommand
     */
    public static boolean canExecuteAdminCommand(ServerClient client, AdminCommand command) {
        if (client == null || command == null) return false;
        
        return hasPermissionLevel(client, command.getRequiredPermission());
    }
    
    /**
     * Check if a client has at least the specified permission level
     */
    public static boolean hasPermissionLevel(ServerClient client, PermissionLevel requiredLevel) {
        if (client == null || requiredLevel == null) return false;
        
        PermissionLevel clientLevel = client.getPermissionLevel();
        if (clientLevel == null) return false;
        
        return clientLevel.getLevel() >= requiredLevel.getLevel();
    }
    
    /**
     * Check if a client can access the Command Center at all
     */
    public static boolean canAccessCommandCenter(ServerClient client) {
        return hasPermissionLevel(client, PermissionLevel.ADMIN);
    }
    
    /**
     * Get a descriptive error message for permission denial
     */
    public static String getPermissionDeniedMessage(PermissionLevel required) {
        return String.format("Permission denied. Requires %s access or higher.", required.name());
    }
}
