package medievalsim.packets;

import medievalsim.banking.domain.BankingLevelData;
import medievalsim.banking.domain.PlayerBank;
import medievalsim.config.ModConfig;
import medievalsim.packets.core.AbstractLevelPacket;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
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
public class PacketSetBankPIN extends AbstractLevelPacket {
    
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
            Level level = requireLevel(client);
            if (level == null) {
                return;
            }
            
            // Validate new PIN length
            if (ModConfig.Banking.requirePIN && !validateString(this.newPin, ModConfig.Banking.pinLength, ModConfig.Banking.pinLength, "newPin")) {
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
                    client.sendChatMessage(necesse.engine.localization.Localization.translate("message", "banking.pin.oldrequired"));
                    return;
                }
                if (!validateString(this.oldPin, ModConfig.Banking.pinLength, ModConfig.Banking.pinLength, "oldPin")) {
                    return;
                }

                if (!bank.validatePin(this.oldPin)) {
                    ModLogger.debug("PacketSetBankPIN: Invalid old PIN for auth=%d", client.authentication);
                    client.sendChatMessage(necesse.engine.localization.Localization.translate("message", "banking.pin.invalid"));
                    return;
                }
            }
            
            // Set new PIN
            boolean wasSet = bank.isPinSet();
            if (bank.setPin(this.newPin)) {
                ModLogger.info("PIN %s for player auth=%d", 
                    wasSet ? "changed" : "set", client.authentication);
                String messageKey = wasSet ? "banking.pin.changed" : "banking.pin.set";
                client.sendChatMessage(necesse.engine.localization.Localization.translate("message", messageKey));
            } else {
                ModLogger.error("PacketSetBankPIN: Failed to set PIN for auth=%d", client.authentication);
                client.sendChatMessage(necesse.engine.localization.Localization.translate("message", "banking.pin.error"));
            }
            
        } catch (Exception e) {
            ModLogger.error("Exception in PacketSetBankPIN.processServer", e);
        }
    }
}

