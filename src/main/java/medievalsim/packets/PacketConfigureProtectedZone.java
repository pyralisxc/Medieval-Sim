package medievalsim.packets;

import medievalsim.util.ModLogger;
import medievalsim.zones.ProtectedZone;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

public class PacketConfigureProtectedZone extends Packet {
    public final int zoneID;
    public final String ownerName;      // Player name (UI input)
    public final boolean allowOwnerTeam;
    public final boolean canBreak;
    public final boolean canPlace;
    
    // Enhancement #5: 6 granular interaction permissions
    public final boolean canInteractDoors;
    public final boolean canInteractContainers;
    public final boolean canInteractStations;
    public final boolean canInteractSigns;
    public final boolean canInteractSwitches;
    public final boolean canInteractFurniture;
    
    // Receiving constructor
    public PacketConfigureProtectedZone(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.zoneID = reader.getNextInt();
        this.ownerName = reader.getNextString();
        this.allowOwnerTeam = reader.getNextBoolean();
        this.canBreak = reader.getNextBoolean();
        this.canPlace = reader.getNextBoolean();
        
        // Enhancement #5: Read 6 interaction permissions
        this.canInteractDoors = reader.getNextBoolean();
        this.canInteractContainers = reader.getNextBoolean();
        this.canInteractStations = reader.getNextBoolean();
        this.canInteractSigns = reader.getNextBoolean();
        this.canInteractSwitches = reader.getNextBoolean();
        this.canInteractFurniture = reader.getNextBoolean();
    }
    
    // Sending constructor
    public PacketConfigureProtectedZone(int zoneID, String ownerName, boolean allowOwnerTeam, 
                                       boolean canBreak, boolean canPlace,
                                       boolean canInteractDoors, boolean canInteractContainers,
                                       boolean canInteractStations, boolean canInteractSigns,
                                       boolean canInteractSwitches, boolean canInteractFurniture) {
        this.zoneID = zoneID;
        this.ownerName = ownerName != null ? ownerName : "";
        this.allowOwnerTeam = allowOwnerTeam;
        this.canBreak = canBreak;
        this.canPlace = canPlace;
        
        // Enhancement #5: Store 6 interaction permissions
        this.canInteractDoors = canInteractDoors;
        this.canInteractContainers = canInteractContainers;
        this.canInteractStations = canInteractStations;
        this.canInteractSigns = canInteractSigns;
        this.canInteractSwitches = canInteractSwitches;
        this.canInteractFurniture = canInteractFurniture;
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(zoneID);
        writer.putNextString(this.ownerName);
        writer.putNextBoolean(allowOwnerTeam);
        writer.putNextBoolean(canBreak);
        writer.putNextBoolean(canPlace);
        
        // Enhancement #5: Write 6 interaction permissions
        writer.putNextBoolean(canInteractDoors);
        writer.putNextBoolean(canInteractContainers);
        writer.putNextBoolean(canInteractStations);
        writer.putNextBoolean(canInteractSigns);
        writer.putNextBoolean(canInteractSwitches);
        writer.putNextBoolean(canInteractFurniture);
    }
    
    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            // Validate using ZoneAPI
            medievalsim.util.ZoneAPI.ZoneContext ctx = medievalsim.util.ZoneAPI.forClient(client)
                .withPacketName("PacketConfigureProtectedZone")
                .requireProtectedZone(zoneID)
                .build();
            if (!ctx.isValid()) return;

            // Get zone
            ProtectedZone zone = ctx.getProtectedZone();
            
            if (zone == null) {
                ModLogger.warn("PacketConfigureProtectedZone: Zone " + zoneID + " not found");
                return;
            }
            
            // Resolve owner name → auth ID
            long ownerAuth = -1L;
            String resolvedOwnerName = ""; // Store actual character name
            if (!ownerName.isEmpty()) {
                // Try to find online player by name
                ServerClient ownerClient = null;
                for (ServerClient sc : server.getClients()) {
                    if (sc.getName().equalsIgnoreCase(ownerName)) {
                        ownerClient = sc;
                        break;
                    }
                }
                
                if (ownerClient != null) {
                    ownerAuth = ownerClient.authentication;
                    resolvedOwnerName = ownerClient.getName(); // Capture actual character name
                    ModLogger.info("Resolved owner '" + ownerName + "' to auth " + ownerAuth + " (name: " + resolvedOwnerName + ")");
                } else {
                    // Player not online by name - try parsing as auth ID (for offline players OR auth ID entry)
                    try {
                        ownerAuth = Long.parseLong(ownerName);
                        
                        // Check if this auth ID is actually online (user entered Steam ID instead of name)
                        ServerClient authClient = server.getClientByAuth(ownerAuth);
                        if (authClient != null) {
                            // Player IS online! Capture their name
                            resolvedOwnerName = authClient.getName();
                            ModLogger.info("Resolved auth ID " + ownerAuth + " to online player '" + resolvedOwnerName + "'");
                        } else {
                            // Player truly offline
                            ModLogger.info("Using direct auth ID: " + ownerAuth + " (player offline, name will be empty)");
                            // resolvedOwnerName stays empty - will show auth ID until player logs in
                        }
                    } catch (NumberFormatException ex) {
                        ModLogger.warn("Owner '" + ownerName + "' not found online and not a valid auth ID");
                        client.sendChatMessage(necesse.engine.localization.Localization.translate("message", "zone.protected.ownernotfound", "player", ownerName));
                        return;
                    }
                }
            }
            
            // Update zone
            zone.setOwnerAuth(ownerAuth);
            zone.setOwnerName(resolvedOwnerName); // Store character name for display
            zone.setAllowOwnerTeam(allowOwnerTeam);
            zone.setCanBreak(canBreak);
            zone.setCanPlace(canPlace);
            
            // Enhancement #5: Set 6 granular interaction permissions
            zone.setCanInteractDoors(canInteractDoors);
            zone.setCanInteractContainers(canInteractContainers);
            zone.setCanInteractStations(canInteractStations);
            zone.setCanInteractSigns(canInteractSigns);
            zone.setCanInteractSwitches(canInteractSwitches);
            zone.setCanInteractFurniture(canInteractFurniture);

            // Sync to all clients (with name refresh)
            if (ctx.getZoneData() != null) {
                server.network.sendToAllClients(new PacketZoneSync(ctx.getZoneData(), server));
            }
            
            ModLogger.info("Zone " + zoneID + " configured: owner=" + (resolvedOwnerName.isEmpty() ? "auth:" + ownerAuth : resolvedOwnerName) + 
                          " (auth=" + ownerAuth + "), allowTeam=" + allowOwnerTeam + ", break=" + canBreak + ", place=" + canPlace + 
                          ", doors=" + canInteractDoors + ", containers=" + canInteractContainers + ", stations=" + canInteractStations + 
                          ", signs=" + canInteractSigns + ", switches=" + canInteractSwitches + ", furniture=" + canInteractFurniture);
            
        } catch (Exception ex) {
            ModLogger.error("Error configuring protected zone", ex);
        }
    }
}
