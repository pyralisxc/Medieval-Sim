package medievalsim.packets;

import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.GameRandom;
import necesse.engine.commands.ParsedCommand;

/**
 * Client → Server: Execute a command string
 * 
 * Flow:
 * 1. Client fills out Command Center form and clicks Execute
 * 2. Client sends command string to server
 * 3. Server validates permission level
 * 4. Server executes command
 * 5. Server sends result back via PacketCommandResult
 */
public class PacketExecuteCommand extends Packet {
    
    public final String commandString;
    public final int requestId; // For matching response to request
    
    /**
     * Receiving constructor (server-side)
     */
    public PacketExecuteCommand(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.commandString = reader.getNextString();
        this.requestId = reader.getNextInt();
    }
    
    /**
     * Sending constructor (client-side)
     */
    public PacketExecuteCommand(String commandString) {
        this.commandString = commandString;
        this.requestId = GameRandom.globalRandom.nextInt(); // Unique ID for this request
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextString(commandString);
        writer.putNextInt(requestId);
    }
    
    /**
     * Server-side processing
     */
    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        if (client == null) return;
        
        // Permission check using centralized system
        if (!medievalsim.commandcenter.service.CommandPermissions.canAccessCommandCenter(client)) {
            ModLogger.warn("PacketExecuteCommand: permission denied for client: %s", client.getName());
            server.network.sendPacket(new PacketCommandResult(
                requestId,
                false,
                medievalsim.commandcenter.service.CommandPermissions.getPermissionDeniedMessage(necesse.engine.commands.PermissionLevel.ADMIN),
                commandString
            ), client);
            return;
        }
        
        // Validate command string
        if (commandString == null || commandString.trim().isEmpty()) {
            server.network.sendPacket(new PacketCommandResult(
                requestId,
                false,
                "Empty command string",
                commandString
            ), client);
            return;
        }
        
        try {
            // Execute the command using Necesse's command system
            String trimmedCommand = commandString.trim();
            
            // Remove leading slash if present (some users might type "/help" instead of "help")
            if (trimmedCommand.startsWith("/")) {
                trimmedCommand = trimmedCommand.substring(1);
            }
            
            ModLogger.info("PacketExecuteCommand: executing command '%s' from client '%s'", trimmedCommand, client.getName());

            // Parse command into Necesse's ParsedCommand format
            ParsedCommand parsedCommand = new ParsedCommand(trimmedCommand);
            
            // Execute the command through Necesse's command manager
            // Let Necesse handle all logging - it will automatically send chat messages to the client
            boolean success = server.commandsManager.runServerCommand(parsedCommand, client);
            
            // Simple result message - chat feedback handles the details
            String resultMessage = success ? "Command executed" : "Command failed or invalid";
            
            // Send result back to client (simple success/failure notification)
            server.network.sendPacket(new PacketCommandResult(
                requestId,
                success,
                resultMessage,
                commandString
            ), client);
            
        } catch (Exception e) {
            ModLogger.error(String.format("PacketExecuteCommand: error executing command '%s' for client '%s'", commandString, client.getName()), e);

            server.network.sendPacket(new PacketCommandResult(
                requestId,
                false,
                "Error: " + e.getMessage(),
                commandString
            ), client);
        }
    }
}
