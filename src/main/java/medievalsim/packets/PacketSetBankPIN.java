package medievalsim.packets;

import medievalsim.banking.BankingLevelData;
import medievalsim.banking.PlayerBank;
import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;

/**
 * Packet to set or change a player's bank PIN.
 * Client -> Server: Request to set/change PIN
 * Server validates and updates PIN.
 */
public class PacketSetBankPIN extends Packet {
    
    public String oldPin;  // Current PIN (for verification when changing)
    public String newPin;  // New PIN to set
    
    /**
     * Receiving constructor (server-side).
     */
    public PacketSetBankPIN(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.oldPin = reader.getNextString();
        this.newPin = reader.getNextString();
    }
    
    /**
     * Sending constructor (client-side) - for changing PIN.
     */
    public PacketSetBankPIN(String oldPin, String newPin) {
        this.oldPin = oldPin != null ? oldPin : "";
        this.newPin = newPin != null ? newPin : "";
        PacketWriter writer = new PacketWriter(this);
        writer.putNextString(this.oldPin);
        writer.putNextString(this.newPin);
    }
    
    /**
     * Sending constructor (client-side) - for setting initial PIN.
     */
    public PacketSetBankPIN(String newPin) {
        this("", newPin);
    }
    
    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            // Validate client
            if (client == null || client.playerMob == null) {
                ModLogger.warn("PacketSetBankPIN: Invalid client or player mob");
                return;
            }
            
            Level level = client.playerMob.getLevel();
            if (level == null) {
                ModLogger.warn("PacketSetBankPIN: Player has null level");
                return;
            }
            
            // Validate new PIN length
            if (ModConfig.Banking.requirePIN && this.newPin.length() != ModConfig.Banking.pinLength) {
                ModLogger.warn("PacketSetBankPIN: Invalid PIN length for auth=%d (expected %d, got %d)",
                    client.authentication, ModConfig.Banking.pinLength, this.newPin.length());
                // TODO: Send error message to client
                return;
            }
            
            // Get banking data
            BankingLevelData bankingData = BankingLevelData.getBankingData(level);
            if (bankingData == null) {
                ModLogger.error("PacketSetBankPIN: Failed to get banking data for level");
                return;
            }
            
            // Get or create player bank
            PlayerBank bank = bankingData.getOrCreateBank(client.authentication);
            if (bank == null) {
                ModLogger.error("PacketSetBankPIN: Failed to get/create bank for player auth=%d", client.authentication);
                return;
            }
            
            // If PIN is already set, validate old PIN
            if (bank.isPinSet()) {
                if (this.oldPin == null || this.oldPin.isEmpty()) {
                    ModLogger.debug("PacketSetBankPIN: Old PIN required but not provided for auth=%d", client.authentication);
                    // TODO: Send error message to client
                    return;
                }
                
                if (!bank.validatePin(this.oldPin)) {
                    ModLogger.debug("PacketSetBankPIN: Invalid old PIN for auth=%d", client.authentication);
                    // TODO: Send error message to client
                    return;
                }
            }
            
            // Set new PIN
            if (bank.setPin(this.newPin)) {
                ModLogger.info("PIN %s for player auth=%d", 
                    bank.isPinSet() ? "changed" : "set", client.authentication);
                // TODO: Send success message to client
            } else {
                ModLogger.error("PacketSetBankPIN: Failed to set PIN for auth=%d", client.authentication);
                // TODO: Send error message to client
            }
            
        } catch (Exception e) {
            ModLogger.error("Exception in PacketSetBankPIN.processServer", e);
        }
    }
}

