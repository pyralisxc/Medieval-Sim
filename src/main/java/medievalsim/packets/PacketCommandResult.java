package medievalsim.packets;

import medievalsim.config.SettingsManager;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.Settings;

/**
 * Server → Client: Result of command execution
 * 
 * Sent after server processes PacketExecuteCommand
 */
public class PacketCommandResult extends Packet {
    
    public final int requestId; // Matches the request
    public final boolean success;
    public final String message;
    public final String commandString; // The command that was executed
    
    /**
     * Receiving constructor (client-side)
     */
    public PacketCommandResult(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.requestId = reader.getNextInt();
        this.success = reader.getNextBoolean();
        this.message = reader.getNextString();
        this.commandString = reader.getNextString();
    }
    
    /**
     * Sending constructor (server-side)
     */
    public PacketCommandResult(int requestId, boolean success, String message, String commandString) {
        this.requestId = requestId;
        this.success = success;
        this.message = message;
        this.commandString = commandString;
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(requestId);
        writer.putNextBoolean(success);
        writer.putNextString(message);
        writer.putNextString(commandString);
    }
    
    /**
     * Client-side processing
     */
    @Override
    public void processClient(NetworkPacket packet, Client client) {
        // Display result in game chat or UI
        ModLogger.info("CommandResult [%s] %s", success ? "SUCCESS" : "FAILURE", message);

        // Add to command history if execution was successful
        if (success && commandString != null && !commandString.trim().isEmpty()) {
            try {
                // Add to history using SettingsManager
                SettingsManager.getInstance().addCommandToHistory(commandString);

                // Save settings
                Settings.saveClientSettings();
            } catch (Exception e) {
                ModLogger.error("PacketCommandResult: failed to save command history", e);
            }
        }
        
        // Show in chat
        if (client.getLevel() != null && client.getLevel().getClient() != null) {
            String chatMessage = (success ? "§a" : "§c") + message; // Green for success, red for error
            client.getLevel().getClient().chat.addMessage(chatMessage);
        }
    }
}
