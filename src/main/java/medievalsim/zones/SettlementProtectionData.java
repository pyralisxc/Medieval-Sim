/*
 * Settlement Protection Data for Medieval Sim Mod
 * Stores protection settings for a settlement (mirrors ProtectedZone permissions)
 */
package medievalsim.zones;

import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

/**
 * Data class that stores protection settings for a settlement.
 * This mirrors the permission system from ProtectedZone but is tied to settlement bounds.
 */
public class SettlementProtectionData {
    
    // Protection enabled flag
    private boolean enabled = false;
    
    // Permission flags (same as ProtectedZone)
    private boolean allowOwnerTeam = true;  // Allow owner's team access?
    private boolean canBreak = false;       // Team members can break
    private boolean canPlace = false;       // Team members can place
    
    // Granular interaction permissions (all default false)
    private boolean canInteractDoors = false;      // Can open/close doors
    private boolean canInteractContainers = false; // Can open chests/storage
    private boolean canInteractStations = false;   // Can use crafting stations
    private boolean canInteractSigns = false;      // Can edit sign text
    private boolean canInteractSwitches = false;   // Can use levers/switches
    private boolean canInteractFurniture = false;  // Can use beds/chairs
    
    public SettlementProtectionData() {
    }
    
    // ===== GETTERS/SETTERS =====
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean getAllowOwnerTeam() {
        return allowOwnerTeam;
    }
    
    public void setAllowOwnerTeam(boolean allowOwnerTeam) {
        this.allowOwnerTeam = allowOwnerTeam;
    }
    
    public boolean getCanBreak() {
        return canBreak;
    }
    
    public void setCanBreak(boolean canBreak) {
        this.canBreak = canBreak;
    }
    
    public boolean getCanPlace() {
        return canPlace;
    }
    
    public void setCanPlace(boolean canPlace) {
        this.canPlace = canPlace;
    }
    
    public boolean getCanInteractDoors() {
        return canInteractDoors;
    }
    
    public void setCanInteractDoors(boolean canInteractDoors) {
        this.canInteractDoors = canInteractDoors;
    }
    
    public boolean getCanInteractContainers() {
        return canInteractContainers;
    }
    
    public void setCanInteractContainers(boolean canInteractContainers) {
        this.canInteractContainers = canInteractContainers;
    }
    
    public boolean getCanInteractStations() {
        return canInteractStations;
    }
    
    public void setCanInteractStations(boolean canInteractStations) {
        this.canInteractStations = canInteractStations;
    }
    
    public boolean getCanInteractSigns() {
        return canInteractSigns;
    }
    
    public void setCanInteractSigns(boolean canInteractSigns) {
        this.canInteractSigns = canInteractSigns;
    }
    
    public boolean getCanInteractSwitches() {
        return canInteractSwitches;
    }
    
    public void setCanInteractSwitches(boolean canInteractSwitches) {
        this.canInteractSwitches = canInteractSwitches;
    }
    
    public boolean getCanInteractFurniture() {
        return canInteractFurniture;
    }
    
    public void setCanInteractFurniture(boolean canInteractFurniture) {
        this.canInteractFurniture = canInteractFurniture;
    }
    
    // ===== CONVENIENCE METHODS =====
    
    /**
     * Set all interaction permissions to the same value
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

    // ===== SAVE/LOAD =====

    public void addSaveData(SaveData save) {
        save.addBoolean("enabled", this.enabled);
        save.addBoolean("allowOwnerTeam", this.allowOwnerTeam);
        save.addBoolean("canBreak", this.canBreak);
        save.addBoolean("canPlace", this.canPlace);
        save.addBoolean("canInteractDoors", this.canInteractDoors);
        save.addBoolean("canInteractContainers", this.canInteractContainers);
        save.addBoolean("canInteractStations", this.canInteractStations);
        save.addBoolean("canInteractSigns", this.canInteractSigns);
        save.addBoolean("canInteractSwitches", this.canInteractSwitches);
        save.addBoolean("canInteractFurniture", this.canInteractFurniture);
    }

    public void applyLoadData(LoadData save) {
        this.enabled = save.getBoolean("enabled", false);
        this.allowOwnerTeam = save.getBoolean("allowOwnerTeam", true);
        this.canBreak = save.getBoolean("canBreak", false);
        this.canPlace = save.getBoolean("canPlace", false);
        this.canInteractDoors = save.getBoolean("canInteractDoors", false);
        this.canInteractContainers = save.getBoolean("canInteractContainers", false);
        this.canInteractStations = save.getBoolean("canInteractStations", false);
        this.canInteractSigns = save.getBoolean("canInteractSigns", false);
        this.canInteractSwitches = save.getBoolean("canInteractSwitches", false);
        this.canInteractFurniture = save.getBoolean("canInteractFurniture", false);
    }

    // ===== PACKET SERIALIZATION =====

    public void writePacket(PacketWriter writer) {
        writer.putNextBoolean(this.enabled);
        writer.putNextBoolean(this.allowOwnerTeam);
        writer.putNextBoolean(this.canBreak);
        writer.putNextBoolean(this.canPlace);
        writer.putNextBoolean(this.canInteractDoors);
        writer.putNextBoolean(this.canInteractContainers);
        writer.putNextBoolean(this.canInteractStations);
        writer.putNextBoolean(this.canInteractSigns);
        writer.putNextBoolean(this.canInteractSwitches);
        writer.putNextBoolean(this.canInteractFurniture);
    }

    public void readPacket(PacketReader reader) {
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
}
