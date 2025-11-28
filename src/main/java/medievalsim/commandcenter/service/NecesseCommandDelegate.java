package medievalsim.commandcenter.service;

import necesse.engine.commands.ParsedCommand;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Helper class to delegate command execution to Necesse's native CommandsManager
 * This ensures API compatibility while maintaining our custom UI/UX layer
 */
public class NecesseCommandDelegate {
    
    /**
     * Execute a Necesse native command and return a structured result
     * 
     * @param server The server instance
     * @param commandString The command to execute (e.g., "save" or "time day")
     * @param executor The client executing the command
     * @return CommandResult with success/error status
     */
    public static CommandResult executeNecesseCommand(Server server, String commandString, ServerClient executor) {
        try {
            boolean success = server.commandsManager.runServerCommand(
                new ParsedCommand(commandString), 
                executor
            );
            
            if (success) {
                return CommandResult.success("Command executed: " + commandString);
            } else {
                return CommandResult.error("Command failed: " + commandString);
            }
        } catch (Exception e) {
            return CommandResult.error("Error executing command: " + e.getMessage());
        }
    }
}

