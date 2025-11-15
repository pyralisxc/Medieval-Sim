package medievalsim.packets;

import medievalsim.commandcenter.settings.AdminSetting;
import medievalsim.commandcenter.settings.SettingsRegistry;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Packet for syncing Command Center settings changes.
 * Client -> Server: request a setting change.
 * Server -> All clients: broadcast the applied setting value.
 */
public class PacketSyncSettings extends Packet {
    
    private String settingId;
    private String valueStr;
    private boolean success;
    
    /**
     * Receiving constructor (from network)
     */
    public PacketSyncSettings(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        
        this.settingId = reader.getNextString();
        this.valueStr = reader.getNextString();
        this.success = reader.getNextBoolean();
    }
    
    /**
     * Sending constructor
     */
    public PacketSyncSettings(String settingId, String valueStr) {
        this(settingId, valueStr, true);
    }
    
    /**
     * Sending constructor with success flag
     */
    public PacketSyncSettings(String settingId, String valueStr, boolean success) {
        this.settingId = settingId;
        this.valueStr = valueStr;
        this.success = success;
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextString(settingId);
        writer.putNextString(valueStr);
        writer.putNextBoolean(success);
    }
    
    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        // Permission check - only admins can change settings
        if (client.getPermissionLevel() == null || 
            client.getPermissionLevel().getLevel() < necesse.engine.commands.PermissionLevel.ADMIN.getLevel()) {
            ModLogger.warn("Player " + client.getName() + " attempted to change settings without permission");
            client.sendPacket(new PacketSyncSettings(settingId, valueStr, false));
            return;
        }
        
        AdminSetting<?> setting = SettingsRegistry.getSetting(settingId);
        if (setting == null) {
            ModLogger.error("Unknown setting: " + settingId);
            client.sendPacket(new PacketSyncSettings(settingId, valueStr, false));
            return;
        }
        
        if (setting.isReadOnly()) {
            ModLogger.warn("Attempted to modify read-only setting: " + settingId);
            client.sendPacket(new PacketSyncSettings(settingId, valueStr, false));
            return;
        }
        
        try {
            // Attempt to set the value
            boolean set = setSettingValue(setting, valueStr);
            
            if (set) {
                // Broadcast to all clients
                server.network.sendToAllClients(new PacketSyncSettings(settingId, valueStr, true));
                ModLogger.info("Player " + client.getName() + " changed setting " + settingId + " to " + valueStr);
            } else {
                client.sendPacket(new PacketSyncSettings(settingId, valueStr, false));
            }
            
        } catch (Exception e) {
            ModLogger.error("Error setting value for " + settingId + ": " + e.getMessage());
            client.sendPacket(new PacketSyncSettings(settingId, valueStr, false));
        }
    }
    
    @Override
    public void processClient(NetworkPacket packet, Client client) {
        if (success) {
            client.chat.addMessage("\u00a7aSetting updated: " + settingId);
        } else {
            client.chat.addMessage("\u00a7cFailed to update setting: " + settingId);
        }
    }
    
    /**
     * Helper to set setting value based on type
     */
    @SuppressWarnings("unchecked")
    private boolean setSettingValue(AdminSetting<?> setting, String valueStr) {
        switch (setting.getType()) {
            case INTEGER:
                return ((AdminSetting<Integer>) setting).setValue(Integer.parseInt(valueStr));
            case LONG:
                return ((AdminSetting<Long>) setting).setValue(Long.parseLong(valueStr));
            case FLOAT:
                return ((AdminSetting<Float>) setting).setValue(Float.parseFloat(valueStr));
            case BOOLEAN:
                return ((AdminSetting<Boolean>) setting).setValue(Boolean.parseBoolean(valueStr));
            case STRING:
                return ((AdminSetting<String>) setting).setValue(valueStr);
            default:
                return false;
        }
    }
}

