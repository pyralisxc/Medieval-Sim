package medievalsim.zones.enhanced;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced ProtectedZone optimized for high-performance servers (100+ players).
 * 
 * Key improvements over current ProtectedZone:
 * - Uses Necesse's high-performance Zoning system for O(log n) spatial queries
 * - ConcurrentHashMap for thread-safe permission lookups
 * - Optimized serialization format
 * - Reduced memory footprint for large zone counts
 */
public class EnhancedProtectedZone extends EnhancedAdminZone {
    
    // ===== PERMISSION SYSTEM (Thread-safe, optimized) =====
    private final Set<Long> allowedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<Integer> allowedTeams = ConcurrentHashMap.newKeySet();
    private final Set<String> trustedPlayerNames = ConcurrentHashMap.newKeySet();
    
    // Permission flags (packed into single int for performance)
    private int permissionFlags = 0;
    
    // Permission bit flags (more memory efficient than separate booleans)
    private static final int FLAG_BUILD = 1;
    private static final int FLAG_BREAK = 2;
    private static final int FLAG_DOORS = 4;
    private static final int FLAG_CONTAINERS = 8;
    private static final int FLAG_STATIONS = 16;
    private static final int FLAG_SIGNS = 32;
    private static final int FLAG_SWITCHES = 64;
    private static final int FLAG_FURNITURE = 128;
    
    // Owner data
    private long ownerAuth = -1L;
    private String ownerName = "";
    
    public EnhancedProtectedZone(int uniqueID, String name, long creatorAuth, int colorHue) {
        super(uniqueID, name, colorHue);
        this.ownerAuth = creatorAuth;
        
        // Default permissions - visitors can't do anything
        this.permissionFlags = 0;
    }
    
    public EnhancedProtectedZone(LoadData save, int tileXOffset, int tileYOffset) {
        super(save, tileXOffset, tileYOffset);
        loadPermissionData(save);
    }
    
    // ===== HIGH-PERFORMANCE PERMISSION CHECKS =====
    
    /**
     * Fast permission check using bit operations
     */
    public boolean canBuild() {
        return (permissionFlags & FLAG_BUILD) != 0;
    }
    
    public boolean canBreak() {
        return (permissionFlags & FLAG_BREAK) != 0;
    }
    
    public boolean canUseDoors() {
        return (permissionFlags & FLAG_DOORS) != 0;
    }
    
    public boolean canUseContainers() {
        return (permissionFlags & FLAG_CONTAINERS) != 0;
    }
    
    // Add all other permission methods...
    
    /**
     * Fast player permission check using ConcurrentHashMap O(1) lookup
     */
    public boolean isPlayerAllowed(long playerAuth) {
        if (playerAuth == ownerAuth) return true;
        return allowedPlayers.contains(playerAuth);
    }
    
    /**
     * Fast team permission check
     */
    public boolean isTeamAllowed(int teamID) {
        return allowedTeams.contains(teamID);
    }
    
    // ===== CONVENIENCE METHODS (maintain your current API) =====
    
    public void enableVisitorPermissions() {
        permissionFlags |= (FLAG_DOORS | FLAG_SIGNS | FLAG_SWITCHES);
    }
    
    public void enableTrustedMemberPermissions() {
        permissionFlags |= (FLAG_BUILD | FLAG_BREAK | FLAG_DOORS | 
                           FLAG_CONTAINERS | FLAG_STATIONS | FLAG_SIGNS | 
                           FLAG_SWITCHES | FLAG_FURNITURE);
    }
    
    public void enableFullPermissions() {
        permissionFlags = 0xFF; // All flags set
    }
    
    // ===== SAVE/LOAD OPTIMIZATION =====
    
    @Override
    protected void addZoneSpecificSaveData(SaveData save) {
        // Efficient serialization
        save.addLong("ownerAuth", ownerAuth);
        save.addUnsafeString("ownerName", ownerName);
        save.addInt("permissionFlags", permissionFlags);
        
        // Serialize collections efficiently
        if (!allowedPlayers.isEmpty()) {
            long[] playerArray = allowedPlayers.stream().mapToLong(Long::longValue).toArray();
            save.addLongArray("allowedPlayers", playerArray);
        }
        
        if (!allowedTeams.isEmpty()) {
            int[] teamArray = allowedTeams.stream().mapToInt(Integer::intValue).toArray();
            save.addIntArray("allowedTeams", teamArray);
        }
        
        if (!trustedPlayerNames.isEmpty()) {
            save.addStringArray("trustedPlayerNames", trustedPlayerNames.toArray(new String[0]));
        }
    }
    
    private void loadPermissionData(LoadData save) {
        this.ownerAuth = save.getLong("ownerAuth", -1L);
        this.ownerName = save.getUnsafeString("ownerName", "");
        this.permissionFlags = save.getInt("permissionFlags", 0);
        
        // Load collections
        long[] playerArray = save.getLongArray("allowedPlayers");
        if (playerArray != null) {
            for (long auth : playerArray) {
                allowedPlayers.add(auth);
            }
        }
        
        int[] teamArray = save.getIntArray("allowedTeams");
        if (teamArray != null) {
            for (int teamID : teamArray) {
                allowedTeams.add(teamID);
            }
        }
        
        String[] nameArray = save.getStringArray("trustedPlayerNames");
        if (nameArray != null) {
            for (String name : nameArray) {
                trustedPlayerNames.add(name);
            }
        }
    }
    
    // ===== GETTERS/SETTERS =====
    
    public long getOwnerAuth() { return ownerAuth; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    
    public void addAllowedPlayer(long playerAuth) {
        allowedPlayers.add(playerAuth);
    }
    
    public void removeAllowedPlayer(long playerAuth) {
        allowedPlayers.remove(playerAuth);
    }
    
    public void addAllowedTeam(int teamID) {
        allowedTeams.add(teamID);
    }
    
    public void removeAllowedTeam(int teamID) {
        allowedTeams.remove(teamID);
    }
    
    @Override
    public String toString() {
        return String.format("EnhancedProtectedZone[id=%d, name='%s', owner='%s', players=%d, teams=%d]",
                uniqueID, name, ownerName, allowedPlayers.size(), allowedTeams.size());
    }
}