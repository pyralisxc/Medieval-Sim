package medievalsim.ui.helpers;

/**
 * Data structure for player dropdown entries.
 * Stores player information for the owner selection dropdown.
 */
public class PlayerDropdownEntry implements Comparable<PlayerDropdownEntry> {
    public final String characterName;
    public final long steamAuth;
    public final boolean isOnline;
    public final long lastLogin; // Timestamp in milliseconds
    
    public PlayerDropdownEntry(String characterName, long steamAuth, boolean isOnline, long lastLogin) {
        this.characterName = characterName;
        this.steamAuth = steamAuth;
        this.isOnline = isOnline;
        this.lastLogin = lastLogin;
    }
    
    /**
     * Sort alphabetically, with online players first
     */
    @Override
    public int compareTo(PlayerDropdownEntry other) {
        // Online players come first
        if (this.isOnline != other.isOnline) {
            return this.isOnline ? -1 : 1;
        }
        // Then sort alphabetically by character name
        return this.characterName.compareToIgnoreCase(other.characterName);
    }
    
    /**
     * Check if this entry matches the filter text
     */
    public boolean matchesFilter(String filter) {
        if (filter == null || filter.trim().isEmpty()) {
            return true;
        }
        String lowerFilter = filter.toLowerCase();
        return characterName.toLowerCase().contains(lowerFilter) 
            || String.valueOf(steamAuth).contains(lowerFilter);
    }
}
