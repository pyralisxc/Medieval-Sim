package medievalsim.packets;

import medievalsim.banking.domain.PlayerBank;
import medievalsim.packets.core.AbstractPayloadPacket;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Packet to sync bank data from server to client.
 * Server -> Client: Send updated bank data
 * Used after upgrades or other bank modifications.
 */
public class PacketBankSync extends AbstractPayloadPacket {
    
    public long ownerAuth;
    public int upgradeLevel;
    public boolean pinSet;
    public long totalDeposits;
    public long totalWithdrawals;
    public long totalCoinsReceived;
    
    /**
     * Receiving constructor (client-side).
     */
    public PacketBankSync(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.ownerAuth = reader.getNextLong();
        this.upgradeLevel = reader.getNextInt();
        this.pinSet = reader.getNextBoolean();
        this.totalDeposits = reader.getNextLong();
        this.totalWithdrawals = reader.getNextLong();
        this.totalCoinsReceived = reader.getNextLong();
    }
    
    /**
     * Sending constructor (server-side) - from PlayerBank.
     */
    public PacketBankSync(PlayerBank bank) {
        this.ownerAuth = bank.getOwnerAuth();
        this.upgradeLevel = bank.getUpgradeLevel();
        this.pinSet = bank.isPinSet();
        this.totalDeposits = bank.getTotalDeposits();
        this.totalWithdrawals = bank.getTotalWithdrawals();
        this.totalCoinsReceived = bank.getTotalCoinsReceived();
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(this.ownerAuth);
        writer.putNextInt(this.upgradeLevel);
        writer.putNextBoolean(this.pinSet);
        writer.putNextLong(this.totalDeposits);
        writer.putNextLong(this.totalWithdrawals);
        writer.putNextLong(this.totalCoinsReceived);
    }
    
    /**
     * Sending constructor (server-side) - manual values.
     */
    public PacketBankSync(long ownerAuth, int upgradeLevel, boolean pinSet, 
                          long totalDeposits, long totalWithdrawals, long totalCoinsReceived) {
        this.ownerAuth = ownerAuth;
        this.upgradeLevel = upgradeLevel;
        this.pinSet = pinSet;
        this.totalDeposits = totalDeposits;
        this.totalWithdrawals = totalWithdrawals;
        this.totalCoinsReceived = totalCoinsReceived;
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(this.ownerAuth);
        writer.putNextInt(this.upgradeLevel);
        writer.putNextBoolean(this.pinSet);
        writer.putNextLong(this.totalDeposits);
        writer.putNextLong(this.totalWithdrawals);
        writer.putNextLong(this.totalCoinsReceived);
    }
    
    @Override
    public void processClient(NetworkPacket packet, Client client) {
        try {
            // Client-side: Update local bank data cache if needed
            // For now, just log the sync
            ModLogger.debug("Received bank sync for auth=%d (upgrade level: %d, PIN set: %b)",
                this.ownerAuth, this.upgradeLevel, this.pinSet);
            
            // NOTE: UI refresh is handled automatically by the BankContainer's
            // custom actions system. This packet is primarily for background sync
            // of bank metadata (upgrade level, PIN status) when container is closed.
            
        } catch (Exception e) {
            ModLogger.error("Exception in PacketBankSync.processClient", e);
        }
    }
    
    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        // This packet is server -> client only, should not be processed on server
        ModLogger.warn("PacketBankSync received on server from client %s - this should not happen",
            client != null ? client.getName() : "unknown");
    }
}

