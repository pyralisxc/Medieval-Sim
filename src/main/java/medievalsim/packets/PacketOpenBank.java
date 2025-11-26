package medievalsim.packets;

import medievalsim.banking.BankContainer;
import medievalsim.banking.BankingLevelData;
import medievalsim.banking.PlayerBank;
import medievalsim.config.ModConfig;
import medievalsim.registries.MedievalSimContainers;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketOpenContainer;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ContainerRegistry;
import necesse.level.maps.Level;

/**
 * Packet to request opening a player's bank.
 * Client -> Server: Request to open bank with optional PIN
 * Server validates PIN and opens container if valid.
 */
public class PacketOpenBank extends Packet {
    
    public String pin;  // Optional PIN for validation
    
    /**
     * Receiving constructor (server-side).
     */
    public PacketOpenBank(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.pin = reader.getNextString();
    }
    
    /**
     * Sending constructor (client-side).
     */
    public PacketOpenBank(String pin) {
        this.pin = pin != null ? pin : "";
        PacketWriter writer = new PacketWriter(this);
        writer.putNextString(this.pin);
    }
    
    /**
     * Sending constructor (no PIN).
     */
    public PacketOpenBank() {
        this("");
    }
    
    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            // Validate client
            if (client == null || client.playerMob == null) {
                ModLogger.warn("PacketOpenBank: Invalid client or player mob");
                return;
            }
            
            Level level = client.playerMob.getLevel();
            if (level == null) {
                ModLogger.warn("PacketOpenBank: Player has null level");
                return;
            }
            
            // Get banking data
            BankingLevelData bankingData = BankingLevelData.getBankingData(level);
            if (bankingData == null) {
                ModLogger.error("PacketOpenBank: Failed to get banking data for level");
                return;
            }
            
            // Get or create player bank
            PlayerBank bank = bankingData.getOrCreateBank(client.authentication);
            if (bank == null) {
                ModLogger.error("PacketOpenBank: Failed to get/create bank for player auth=%d", client.authentication);
                return;
            }
            
            // Validate PIN if required
            boolean pinValidated = true;
            final int MAX_ATTEMPTS = 3;
            final long COOLDOWN_MS = 5 * 60 * 1000L; // 5 minutes

            // Check for an active lock first
            if (bank.isLocked()) {
                long rem = bank.getLockRemainingMillis();
                long seconds = (rem + 999) / 1000;
                long minutesPart = seconds / 60;
                long secondsPart = seconds % 60;
                String msg = String.format("Too many failed PIN attempts. Try again in %dm %02ds.", minutesPart, secondsPart);
                server.network.sendPacket(new PacketBankOpenResponse(3, msg), client);
                ModLogger.debug("PacketOpenBank: bank locked for auth=%d, remaining=%dms", client.authentication, rem);
                return;
            }

            if (ModConfig.Banking.requirePIN && bank.isPinSet()) {
                if (this.pin == null || this.pin.isEmpty()) {
                    ModLogger.debug("PacketOpenBank: PIN required but not provided for auth=%d", client.authentication);
                    // Tell client to prompt for PIN
                    PacketBankOpenResponse response = new PacketBankOpenResponse(1, "Please enter your bank PIN.");
                    server.network.sendPacket(response, client);
                    return;
                } else if (!bank.validatePin(this.pin)) {
                    // Invalid PIN: record failed attempt and possibly lock
                    boolean locked = bank.recordFailedPinAttempt(MAX_ATTEMPTS, COOLDOWN_MS);
                    if (locked) {
                        String msg = String.format("Too many failed PIN attempts. Bank locked for %d minutes.", COOLDOWN_MS / 60000);
                        server.network.sendPacket(new PacketBankOpenResponse(3, msg), client);
                    } else {
                        int remaining = bank.getRemainingPinAttempts(MAX_ATTEMPTS);
                        String msg = String.format("Invalid PIN. %d attempt(s) remaining.", remaining);
                        server.network.sendPacket(new PacketBankOpenResponse(2, msg), client);
                    }
                    ModLogger.debug("PacketOpenBank: Invalid PIN for auth=%d", client.authentication);
                    return;
                } else {
                    // Successful validation: reset attempts
                    bank.resetFailedPinAttempts();
                }
            }
            
            // Open bank container
            Packet containerPacket = BankContainer.getOpenPacketContent(
                client.authentication,
                bank.getUpgradeLevel(),
                pinValidated,
                bank
            );

            PacketOpenContainer openPacket = new PacketOpenContainer(
                MedievalSimContainers.BANK_CONTAINER,
                containerPacket
            );
            ContainerRegistry.openAndSendContainer(client, openPacket);

            ModLogger.debug("Opened bank for player auth=%d (upgrade level: %d, PIN validated: %b)",
                client.authentication, bank.getUpgradeLevel(), pinValidated);
                
        } catch (Exception e) {
            ModLogger.error("Exception in PacketOpenBank.processServer", e);
        }
    }
}

