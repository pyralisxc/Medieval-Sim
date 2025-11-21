/*
 * Protected Zone Implementation for Medieval Sim Mod
 * Provides area protection functionality with granular permissions
 */
package medievalsim.zones;

import java.util.HashSet;
import java.util.Set;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.level.maps.Level;

public class ProtectedZone
extends AdminZone {
    public static final String TYPE_ID = "protected";
    public Set<Integer> allowedTeamIDs = new HashSet<Integer>();
    
    // NEW FIELDS: Owner and permissions
    private long ownerAuth = -1L;           // Owner's Steam ID/auth
    private String ownerName = "";          // Owner's character name (for display)
    private boolean allowOwnerTeam = true;  // Allow owner's team access?
    private boolean canBreak = false;       // Team members can break
    private boolean canPlace = false;       // Team members can place
    
    // Enhancement #5: Granular interaction permissions (all default false)
    private boolean canInteractDoors = false;      // Can open/close doors
    private boolean canInteractContainers = false; // Can open chests/storage
    private boolean canInteractStations = false;   // Can use crafting stations
    private boolean canInteractSigns = false;      // Can edit sign text
    private boolean canInteractSwitches = false;   // Can use levers/switches
    private boolean canInteractFurniture = false;  // Can use beds/chairs

    public ProtectedZone() {
    }

    public ProtectedZone(int uniqueID, String name, long creatorAuth, int colorHue) {
        super(uniqueID, name, creatorAuth, colorHue);
        this.ownerAuth = creatorAuth; // Default owner is creator
    }

    @Override
    public String getTypeID() {
        return TYPE_ID;
    }

    // Legacy method - kept for backward compatibility
    public boolean canClientModify(ServerClient client, Level level) {
        return canClientBreak(client, level) && canClientPlace(client, level);
    }

    // NEW: Specific permission checks
    /**
     * Helper: Check if client has elevated access (world owner, zone owner, or creator)
     */
    private boolean hasElevatedAccess(ServerClient client, Level level) {
        if (client == null) {
            return false;
        }

        // World owner and explicit zone owner/creator always have full access
        if (this.isWorldOwner(client, level) || this.isOwner(client) || this.isCreator(client)) {
            return true;
        }

        // When enabled, members of the owner's team are treated as fully trusted.
        // They should not be constrained by per-flag checkboxes.
        return allowOwnerTeam && isOnOwnerTeam(client, level);
    }

    /**
     * Helper: Check if client has team-based permission (non-owner teams).
     *
     * Owner team is handled as "elevated" in hasElevatedAccess so that
     * they behave like the owner and are not constrained by the granular
     * permission flags. This method is now only for additional teams the
     * admin has explicitly allowed in the UI.
     */
    private boolean hasTeamPermission(ServerClient client, Level level) {
        if (client == null) {
            return false;
        }

        int clientTeamID = client.getTeamID();
        return clientTeamID != -1 && this.allowedTeamIDs.contains(clientTeamID);
    }

    public boolean canClientBreak(ServerClient client, Level level) {
        if (client == null) {
            return false;
        }

        // Elevated access (world owner, zone owner, creator)
        if (hasElevatedAccess(client, level)) {
            return true;
        }

        // Team-based permission
        if (hasTeamPermission(client, level)) {
            return canBreak;
        }

        return false; // Deny by default
    }
    
    public boolean canClientPlace(ServerClient client, Level level) {
        if (client == null) {
            return false;
        }

        // Elevated access (world owner, zone owner, creator)
        if (hasElevatedAccess(client, level)) {
            return true;
        }

        // Team-based permission
        if (hasTeamPermission(client, level)) {
            return canPlace;
        }

        return false;
    }
    
    // Legacy canClientInteract - kept for backward compatibility, always returns true
    // Use canClientInteract(ServerClient, Level, GameObject) instead for granular checks
    public boolean canClientInteract(ServerClient client, Level level) {
        // This is now just a base permission check - specific interaction types
        // are handled by the GameObject overload below
        return this.canClientBreak(client, level) || this.canClientPlace(client, level);
    }
    
    // NEW: Granular interaction permission check based on GameObject type
    public boolean canClientInteract(ServerClient client, Level level, necesse.level.gameObject.GameObject gameObject) {
        if (client == null || gameObject == null) {
            return false;
        }

        // Elevated access (world owner, zone owner, creator)
        if (hasElevatedAccess(client, level)) {
            return true;
        }

        // Determine permission based on GameObject type (order matters!)
        boolean hasPermission = false;

        // 1. Doors (most specific check first)
        if (gameObject.isDoor) {
            hasPermission = canInteractDoors;
        }
        // 2. Crafting stations (check BEFORE InventoryObject - CraftingStationObject extends InventoryObject)
        else if (gameObject instanceof necesse.level.gameObject.container.CraftingStationObject ||
                 gameObject instanceof necesse.level.gameObject.container.FueledCraftingStationObject) {
            hasPermission = canInteractStations;
        }
        // 3. Containers/Chests (after crafting stations)
        else if (gameObject instanceof necesse.level.gameObject.container.InventoryObject) {
            hasPermission = canInteractContainers;
        }
        // 4. Signs
        else if (gameObject instanceof necesse.level.gameObject.SignObject) {
            hasPermission = canInteractSigns;
        }
        // 5. Switches/Levers (but not doors - already checked above)
        else if (gameObject.isSwitch || gameObject.isPressurePlate) {
            hasPermission = canInteractSwitches;
        }
        // 6. Furniture (beds, chairs, decorative objects)
        else if (gameObject instanceof necesse.level.gameObject.furniture.FurnitureObject) {
            hasPermission = canInteractFurniture;
        }
        // Unknown type - deny by default in protected zones
        else {
            // Log unknown object types for debugging
            System.out.println("DEBUG: Unknown GameObject type in protected zone: " +
                gameObject.getClass().getSimpleName() + " (ID: " + gameObject.getStringID() + ")");
            return false;
        }

        // Team-based permission
        if (hasTeamPermission(client, level)) {
            return hasPermission;
        }

        return false; // Deny by default
    }
    
    // Helper: Check if client is on owner's team
    private boolean isOnOwnerTeam(ServerClient client, Level level) {
        if (ownerAuth == -1L) {
            return false; // No owner set
        }
        
        if (level == null || level.getServer() == null) {
            return false;
        }
        
        // Get owner's teamID
        int ownerTeamID = level.getServer().world.getTeams().getPlayerTeamID(ownerAuth);
        if (ownerTeamID == -1) {
            return false; // Owner not on a team
        }
        
        // Check if client is on same team
        int clientTeamID = client.getTeamID();
        return clientTeamID != -1 && clientTeamID == ownerTeamID;
    }
    
    public boolean isOwner(ServerClient client) {
        if (ownerAuth == -1L) {
            return false;
        }
        return client.authentication == ownerAuth;
    }
    
    // Getters/Setters for UI
    public long getOwnerAuth() { 
        return ownerAuth; 
    }
    
    public void setOwnerAuth(long auth) { 
        this.ownerAuth = auth; 
    }
    
    public String getOwnerName() {
        return ownerName;
    }
    
    public void setOwnerName(String name) {
        this.ownerName = (name != null) ? name : "";
    }
    
    public boolean getAllowOwnerTeam() { 
        return allowOwnerTeam; 
    }
    
    public void setAllowOwnerTeam(boolean value) { 
        this.allowOwnerTeam = value; 
    }
    
    public boolean getCanBreak() { 
        return canBreak; 
    }
    
    public void setCanBreak(boolean value) { 
        this.canBreak = value; 
    }
    
    public boolean getCanPlace() { 
        return canPlace; 
    }
    
    public void setCanPlace(boolean value) { 
        this.canPlace = value; 
    }
    
    // Enhancement #5: Getters/Setters for granular interaction permissions
    public boolean getCanInteractDoors() {
        return canInteractDoors;
    }
    
    public void setCanInteractDoors(boolean value) {
        this.canInteractDoors = value;
    }
    
    public boolean getCanInteractContainers() {
        return canInteractContainers;
    }
    
    public void setCanInteractContainers(boolean value) {
        this.canInteractContainers = value;
    }
    
    public boolean getCanInteractStations() {
        return canInteractStations;
    }
    
    public void setCanInteractStations(boolean value) {
        this.canInteractStations = value;
    }
    
    public boolean getCanInteractSigns() {
        return canInteractSigns;
    }
    
    public void setCanInteractSigns(boolean value) {
        this.canInteractSigns = value;
    }
    
    public boolean getCanInteractSwitches() {
        return canInteractSwitches;
    }
    
    public void setCanInteractSwitches(boolean value) {
        this.canInteractSwitches = value;
    }
    
    public boolean getCanInteractFurniture() {
        return canInteractFurniture;
    }
    
    public void setCanInteractFurniture(boolean value) {
        this.canInteractFurniture = value;
    }
    
    // Get owner name (for UI display) - no server required (client-side safe)
    public String getOwnerDisplayName() {
        if (ownerAuth == -1L) {
            return "None";
        }
        // Prefer stored character name, fallback to auth ID
        if (ownerName != null && !ownerName.isEmpty()) {
            return ownerName;
        }
        return String.valueOf(ownerAuth);
    }
    
    // Get owner name (for server-side or detailed UI display)
    public String getOwnerName(Server server) {
        if (ownerAuth == -1L) {
            return "None";
        }
        
        if (server == null) {
            return "ID:" + ownerAuth;
        }
        
        // Try to find online client
        ServerClient client = server.getClientByAuth(ownerAuth);
        if (client != null) {
            return client.getName();
        }
        
        // Fallback: return auth ID
        return "ID:" + ownerAuth;
    }

    public void addAllowedTeam(int teamID) {
        if (teamID != -1) {
            this.allowedTeamIDs.add(teamID);
        }
    }

    public void removeAllowedTeam(int teamID) {
        this.allowedTeamIDs.remove(teamID);
    }

    public void clearAllowedTeams() {
        this.allowedTeamIDs.clear();
    }

    // ========================================
    // CONVENIENCE METHODS FOR BULK PERMISSION MANAGEMENT
    // ========================================
    
    /**
     * Set all interaction permissions to the same value
     * Useful for quickly enabling/disabling all interactions
     */
    public void setAllInteractionPermissions(boolean value) {
        this.canInteractDoors = value;
        this.canInteractContainers = value; 
        this.canInteractStations = value;
        this.canInteractSigns = value;
        this.canInteractSwitches = value;
        this.canInteractFurniture = value;
    }
    
    /**
     * Enable common "visitor" permissions (doors, furniture, no containers/stations)
     * Good for public areas where people can move around but not access storage
     */
    public void enableVisitorPermissions() {
        this.canBreak = false;
        this.canPlace = false;
        this.canInteractDoors = true;
        this.canInteractContainers = false;
        this.canInteractStations = false;
        this.canInteractSigns = false;
        this.canInteractSwitches = false;
        this.canInteractFurniture = true;
    }
    
    /**
     * Enable "trusted member" permissions (build + most interactions except signs)
     * Good for team members who can build but shouldn't edit important signs
     */
    public void enableTrustedMemberPermissions() {
        this.canBreak = true;
        this.canPlace = true;
        this.canInteractDoors = true;
        this.canInteractContainers = true;
        this.canInteractStations = true;
        this.canInteractSigns = false; // Keep signs protected
        this.canInteractSwitches = true;
        this.canInteractFurniture = true;
    }
    
    /**
     * Enable full permissions (equivalent to owner access for team members)
     */
    public void enableFullPermissions() {
        this.canBreak = true;
        this.canPlace = true;
        this.setAllInteractionPermissions(true);
    }
    
    /**
     * Get a human-readable summary of current permissions
     * Useful for admin UI display
     */
    public String getPermissionSummary() {
        StringBuilder summary = new StringBuilder();
        
        // Basic permissions
        if (canBreak && canPlace) {
            summary.append("Build & Break, ");
        } else if (canBreak) {
            summary.append("Break only, ");
        } else if (canPlace) {
            summary.append("Place only, ");
        }
        
        // Count interaction permissions
        int interactionCount = 0;
        if (canInteractDoors) interactionCount++;
        if (canInteractContainers) interactionCount++;
        if (canInteractStations) interactionCount++;
        if (canInteractSigns) interactionCount++;
        if (canInteractSwitches) interactionCount++;
        if (canInteractFurniture) interactionCount++;
        
        if (interactionCount == 6) {
            summary.append("All Interactions");
        } else if (interactionCount == 0) {
            summary.append("No Interactions");
        } else {
            summary.append(interactionCount).append("/6 Interactions");
        }
        
        if (summary.length() == 0) {
            return "No Permissions";
        }
        
        return summary.toString();
    }
    
    /**
     * Validate zone configuration and return any warnings
     * Helps admins avoid common misconfigurations
     */
    public java.util.List<String> getConfigurationWarnings() {
        java.util.List<String> warnings = new java.util.ArrayList<>();
        
        // Check for overly restrictive configurations
        if (!canBreak && !canPlace && !allowOwnerTeam) {
            warnings.add("Zone allows no building and team access is disabled - only owner can modify");
        }
        
        if (canInteractContainers && !canInteractDoors) {
            warnings.add("Container access enabled but door access disabled - users may not reach containers");
        }
        
        if (canInteractStations && !canInteractContainers) {
            warnings.add("Crafting station access enabled but container access disabled - limited crafting functionality");
        }
        
        // Check for potentially confusing configurations  
        if (canBreak && !canPlace) {
            warnings.add("Break enabled but place disabled - users can destroy but not rebuild");
        }
        
        if (allowedTeamIDs.isEmpty() && !allowOwnerTeam) {
            warnings.add("No teams allowed and owner team disabled - only owner has access");
        }
        
        return warnings;
    }
    
    /**
     * Check if this zone has any meaningful protections enabled
     * A zone with no protections might be misconfigured
     */
    public boolean hasProtections() {
        // If building is allowed and all interactions are allowed, this zone provides no protection
        return !(canBreak && canPlace && 
                canInteractDoors && canInteractContainers && canInteractStations && 
                canInteractSigns && canInteractSwitches && canInteractFurniture);
    }

    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);
        save.addIntArray("allowedTeams", this.allowedTeamIDs.stream().mapToInt(Integer::intValue).toArray());
        
        // Save owner and permissions
        save.addLong("ownerAuth", this.ownerAuth);
        save.addUnsafeString("ownerName", this.ownerName);
        save.addBoolean("allowOwnerTeam", this.allowOwnerTeam);
        save.addBoolean("canBreak", this.canBreak);
        save.addBoolean("canPlace", this.canPlace);
        
        // Enhancement #5: Save 6 granular interaction permissions
        save.addBoolean("canInteractDoors", this.canInteractDoors);
        save.addBoolean("canInteractContainers", this.canInteractContainers);
        save.addBoolean("canInteractStations", this.canInteractStations);
        save.addBoolean("canInteractSigns", this.canInteractSigns);
        save.addBoolean("canInteractSwitches", this.canInteractSwitches);
        save.addBoolean("canInteractFurniture", this.canInteractFurniture);
    }

    @Override
    public void applyLoadData(LoadData save) {
        super.applyLoadData(save);
        this.allowedTeamIDs.clear();
        int[] teams = save.getIntArray("allowedTeams", new int[0], false);
        for (int teamID : teams) {
            this.allowedTeamIDs.add(teamID);
        }
        
        // Load owner and permissions
        this.ownerAuth = save.getLong("ownerAuth", -1L);
        this.ownerName = save.getUnsafeString("ownerName", "");
        this.allowOwnerTeam = save.getBoolean("allowOwnerTeam", true);
        this.canBreak = save.getBoolean("canBreak", false);
        this.canPlace = save.getBoolean("canPlace", false);
        
        // Enhancement #5: Load 6 granular interaction permissions
        // Migration strategy (Option C): All default to false, admins must reconfigure
        this.canInteractDoors = save.getBoolean("canInteractDoors", false);
        this.canInteractContainers = save.getBoolean("canInteractContainers", false);
        this.canInteractStations = save.getBoolean("canInteractStations", false);
        this.canInteractSigns = save.getBoolean("canInteractSigns", false);
        this.canInteractSwitches = save.getBoolean("canInteractSwitches", false);
        this.canInteractFurniture = save.getBoolean("canInteractFurniture", false);
    }

    @Override
    public void writePacket(PacketWriter writer) {
        super.writePacket(writer);
        writer.putNextInt(this.allowedTeamIDs.size());
        for (int teamID : this.allowedTeamIDs) {
            writer.putNextInt(teamID);
        }
        
        // Write owner and permissions
        writer.putNextLong(this.ownerAuth);
        writer.putNextBoolean(this.allowOwnerTeam);
        writer.putNextBoolean(this.canBreak);
        writer.putNextBoolean(this.canPlace);
        
        // Enhancement #5: Write 6 granular interaction permissions
        writer.putNextBoolean(this.canInteractDoors);
        writer.putNextBoolean(this.canInteractContainers);
        writer.putNextBoolean(this.canInteractStations);
        writer.putNextBoolean(this.canInteractSigns);
        writer.putNextBoolean(this.canInteractSwitches);
        writer.putNextBoolean(this.canInteractFurniture);
    }

    @Override
    public void readPacket(PacketReader reader) {
        super.readPacket(reader);
        this.allowedTeamIDs.clear();
        int teamCount = reader.getNextInt();
        for (int i = 0; i < teamCount; ++i) {
            this.allowedTeamIDs.add(reader.getNextInt());
        }
        
        // Read owner and permissions
        this.ownerAuth = reader.getNextLong();
        this.allowOwnerTeam = reader.getNextBoolean();
        this.canBreak = reader.getNextBoolean();
        this.canPlace = reader.getNextBoolean();
        
        // Enhancement #5: Read 6 granular interaction permissions
        this.canInteractDoors = reader.getNextBoolean();
        this.canInteractContainers = reader.getNextBoolean();
        this.canInteractStations = reader.getNextBoolean();
        this.canInteractSigns = reader.getNextBoolean();
        this.canInteractSwitches = reader.getNextBoolean();
        this.canInteractFurniture = reader.getNextBoolean();
    }
}

