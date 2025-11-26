/*
 * Packet for configuring settlement protection settings
 */
package medievalsim.packets;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import medievalsim.zones.SettlementProtectionData;
import medievalsim.zones.SettlementProtectionLevelData;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import necesse.level.maps.levelData.settlementData.NetworkSettlementData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;

public class PacketConfigureSettlementProtection extends Packet {
    public final int settlementTileX;
    public final int settlementTileY;
    public final boolean enabled;
    public final boolean allowOwnerTeam;
    public final boolean canBreak;
    public final boolean canPlace;
    
    // Granular interaction permissions
    public final boolean canInteractDoors;
    public final boolean canInteractContainers;
    public final boolean canInteractStations;
    public final boolean canInteractSigns;
    public final boolean canInteractSwitches;
    public final boolean canInteractFurniture;
    
    // Receiving constructor
    public PacketConfigureSettlementProtection(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.settlementTileX = reader.getNextInt();
        this.settlementTileY = reader.getNextInt();
        this.enabled = reader.getNextBoolean();
        this.allowOwnerTeam = reader.getNextBoolean();
        this.canBreak = reader.getNextBoolean();
        this.canPlace = reader.getNextBoolean();
        
        this.canInteractDoors = reader.getNextBoolean();
        this.canInteractContainers = reader.getNextBoolean();
        this.canInteractStations = reader.getNextBoolean();
        this.canInteractSigns = reader.getNextBoolean();
        this.canInteractSwitches = reader.getNextBoolean();
        this.canInteractFurniture = reader.getNextBoolean();
    }
    
    // Sending constructor
    public PacketConfigureSettlementProtection(int settlementTileX, int settlementTileY, 
                                              boolean enabled, boolean allowOwnerTeam, 
                                              boolean canBreak, boolean canPlace,
                                              boolean canInteractDoors, boolean canInteractContainers,
                                              boolean canInteractStations, boolean canInteractSigns,
                                              boolean canInteractSwitches, boolean canInteractFurniture) {
        this.settlementTileX = settlementTileX;
        this.settlementTileY = settlementTileY;
        this.enabled = enabled;
        this.allowOwnerTeam = allowOwnerTeam;
        this.canBreak = canBreak;
        this.canPlace = canPlace;
        
        this.canInteractDoors = canInteractDoors;
        this.canInteractContainers = canInteractContainers;
        this.canInteractStations = canInteractStations;
        this.canInteractSigns = canInteractSigns;
        this.canInteractSwitches = canInteractSwitches;
        this.canInteractFurniture = canInteractFurniture;
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(settlementTileX);
        writer.putNextInt(settlementTileY);
        writer.putNextBoolean(enabled);
        writer.putNextBoolean(allowOwnerTeam);
        writer.putNextBoolean(canBreak);
        writer.putNextBoolean(canPlace);
        
        writer.putNextBoolean(canInteractDoors);
        writer.putNextBoolean(canInteractContainers);
        writer.putNextBoolean(canInteractStations);
        writer.putNextBoolean(canInteractSigns);
        writer.putNextBoolean(canInteractSwitches);
        writer.putNextBoolean(canInteractFurniture);
    }
    
    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        if (client == null || client.playerMob == null) {
            ModLogger.warn("Received settlement protection config packet from null client");
            return;
        }
        
        Level level = client.playerMob.getLevel();
        if (level == null || !level.isServer()) {
            ModLogger.warn("Received settlement protection config packet on non-server level");
            return;
        }
        
        // Check if global settlement protection is enabled
        if (!ModConfig.Settlements.protectionEnabled) {
            client.sendChatMessage("Settlement protection is disabled in mod config");
            return;
        }

        // Get the settlement at this location to verify ownership
        SettlementsWorldData settlementsData = SettlementsWorldData.getSettlementsData(server);
        if (settlementsData == null) {
            ModLogger.warn("Client %s attempted to configure settlement protection but settlements data not found",
                client.getName());
            return;
        }

        ServerSettlementData settlement = settlementsData.getServerDataAtTile(
            level.getIdentifier(), settlementTileX, settlementTileY);

        if (settlement == null) {
            ModLogger.warn("Client %s attempted to configure settlement protection but no settlement found at %d,%d",
                client.getName(), settlementTileX, settlementTileY);
            return;
        }

        // Permission check: ONLY settlement owner can configure protection
        NetworkSettlementData networkData = settlement.networkData;
        long settlementOwner = networkData.getOwnerAuth();

        if (settlementOwner != client.authentication) {
            ModLogger.warn("Client %s attempted to configure settlement protection but is not the owner (owner auth: %d)",
                client.getName(), settlementOwner);
            client.sendChatMessage("Only the settlement owner can configure protection");
            return;
        }

        // Get settlement protection level data
        LevelData data = level.getLevelData("settlementprotectiondata");
        if (!(data instanceof SettlementProtectionLevelData)) {
            SettlementProtectionLevelData newData = new SettlementProtectionLevelData();
            level.addLevelData("settlementprotectiondata", newData);
            data = newData;
        }
        
        SettlementProtectionLevelData protectionData = (SettlementProtectionLevelData) data;
        
        // Get or create protection data for this settlement
        SettlementProtectionData protection = protectionData.getManager().getProtectionData(settlementTileX, settlementTileY);
        
        // Update protection settings
        protection.setEnabled(enabled);
        protection.setAllowOwnerTeam(allowOwnerTeam);
        protection.setCanBreak(canBreak);
        protection.setCanPlace(canPlace);
        protection.setCanInteractDoors(canInteractDoors);
        protection.setCanInteractContainers(canInteractContainers);
        protection.setCanInteractStations(canInteractStations);
        protection.setCanInteractSigns(canInteractSigns);
        protection.setCanInteractSwitches(canInteractSwitches);
        protection.setCanInteractFurniture(canInteractFurniture);
        
        ModLogger.info("Updated settlement protection at (%d, %d) - enabled=%b", 
            settlementTileX, settlementTileY, enabled);
        
        // Send confirmation to client
        client.sendChatMessage("Settlement protection updated");
    }
}

