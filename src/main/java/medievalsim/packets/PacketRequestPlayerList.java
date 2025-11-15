package medievalsim.packets;

import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import medievalsim.ui.AdminToolsHudForm;
import medievalsim.ui.AdminToolsHudManager;
import medievalsim.ui.PlayerDropdownEntry;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.GameUtils;
import necesse.engine.world.WorldFile;

/**
 * Packet to request/receive player list for the owner dropdown.
 * Client sends request, server responds with online + offline world players.
 */
public class PacketRequestPlayerList extends Packet {
    
    public List<PlayerDropdownEntry> players;
    
    // Client → Server: Request player list
    public PacketRequestPlayerList() {
        this.players = new ArrayList<>();
    }
    
    // Server → Client: Received player list
    public PacketRequestPlayerList(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        
        int count = reader.getNextShortUnsigned();
        this.players = new ArrayList<>(count);
        
        for (int i = 0; i < count; i++) {
            String name = reader.getNextString();
            long auth = reader.getNextLong();
            boolean isOnline = reader.getNextBoolean();
            long lastLogin = reader.getNextLong();
            
            players.add(new PlayerDropdownEntry(name, auth, isOnline, lastLogin));
        }
    }
    
    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            List<PlayerDropdownEntry> playerList = new ArrayList<>();
            Set<Long> onlineAuths = new HashSet<>();
            
            // Add online players
            for (ServerClient sc : server.getClients()) {
                if (sc != null && sc.playerMob != null) {
                    onlineAuths.add(sc.authentication);
                    String name = sc.getName();
                    long auth = sc.authentication;
                    playerList.add(new PlayerDropdownEntry(name, auth, true, System.currentTimeMillis()));
                }
            }
            
            // Add offline world players using discovered API:
            // server.world.fileSystem.getPlayerFiles() returns LinkedList<WorldFile>
            // Each file is named "{steamAuth}.dat"
            // Use server.world.loadClientName(file) to get character name
            // Use Files.readAttributes() to get last modified time
            try {
                for (WorldFile playerFile : server.world.fileSystem.getPlayerFiles()) {
                    try {
                        String authString = GameUtils.removeFileExtension(playerFile.getFileName().toString());
                        long auth = Long.parseLong(authString);
                        
                        // Skip if already added as online player
                        if (onlineAuths.contains(auth)) {
                            continue;
                        }
                        
                        // Load character name from save data
                        String characterName = server.world.loadClientName(playerFile);
                        if (characterName == null || characterName.equals("N/A") || characterName.isEmpty()) {
                            continue; // Skip invalid/incomplete player files
                        }
                        
                        // Get last modified time from file attributes
                        long lastLogin = 0L;
                        try {
                            BasicFileAttributes attrs = Files.readAttributes(
                                playerFile.toAbsolutePath(), 
                                BasicFileAttributes.class
                            );
                            lastLogin = attrs.lastModifiedTime().toMillis();
                        } catch (Exception e) {
                            // Fallback to 0 if we can't read file attributes
                            lastLogin = 0L;
                        }
                        
                        playerList.add(new PlayerDropdownEntry(
                            characterName,
                            auth,
                            false, // Offline
                            lastLogin
                        ));
                        
                    } catch (NumberFormatException e) {
                        // Skip files with invalid auth ID in filename
                        ModLogger.debug("Found invalid player file name: " + playerFile);
                    }
                }
            } catch (Exception e) {
                ModLogger.error("Error loading offline world players for dropdown: " + e.getMessage());
                e.printStackTrace();
            }
            
            // Sort the list (online first, then alphabetical)
            Collections.sort(playerList);
            
            // Send response to client
            PacketRequestPlayerList response = new PacketRequestPlayerList();
            response.players = playerList;
            
            PacketWriter writer = new PacketWriter(response);
            writer.putNextShortUnsigned(playerList.size());
            for (PlayerDropdownEntry entry : playerList) {
                writer.putNextString(entry.characterName);
                writer.putNextLong(entry.steamAuth);
                writer.putNextBoolean(entry.isOnline);
                writer.putNextLong(entry.lastLogin);
            }
            
            client.sendPacket(response);
            ModLogger.info("Sent player list to " + client.getName() + " (" + playerList.size() + " players: " + 
                          onlineAuths.size() + " online, " + (playerList.size() - onlineAuths.size()) + " offline)");
            
        } catch (Exception e) {
            ModLogger.error("Error processing player list request: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void processClient(NetworkPacket packet, Client client) {
        // Player list received - update the HUD form
        ModLogger.info("Received player list with " + players.size() + " entries");
        
        AdminToolsHudForm hudForm = AdminToolsHudManager.getHudForm();
        if (hudForm != null) {
            hudForm.updatePlayerList(this.players);
        }
    }
}

