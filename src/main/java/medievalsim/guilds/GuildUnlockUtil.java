/*
 * GuildUnlockUtil - Utility for checking guild unlock requirements
 * Part of Medieval Sim Mod guild management system.
 */
package medievalsim.guilds;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import necesse.engine.network.server.ServerClient;

/**
 * Utility class for checking if a player has met guild unlock requirements.
 * Per docs: Player must defeat configured boss to unlock guild features.
 */
public class GuildUnlockUtil {

    /**
     * Check if a player has defeated the configured unlock boss.
     * @param client The ServerClient to check
     * @return true if the player has unlocked guilds (no boss required or boss defeated)
     */
    public static boolean hasUnlockedGuilds(ServerClient client) {
        if (client == null) {
            ModLogger.warn("Cannot check unlock status for null client");
            return false;
        }

        String unlockBoss = ModConfig.Guilds.unlockBoss;
        
        // If no boss required, guilds are unlocked
        if (unlockBoss == null || unlockBoss.isEmpty() || "NONE".equalsIgnoreCase(unlockBoss)) {
            return true;
        }

        // Convert boss config to mob string ID (lowercase, no spaces)
        String mobStringID = getBossStringID(unlockBoss);
        
        try {
            int kills = client.characterStats().mob_kills.getKills(mobStringID);
            boolean unlocked = kills > 0;
            
            ModLogger.debug("Guild unlock check for %s: boss=%s, kills=%d, unlocked=%b",
                client.getName(), mobStringID, kills, unlocked);
            
            return unlocked;
        } catch (Exception e) {
            ModLogger.error("Error checking boss kills for unlock", e);
            // Fail open - if we can't check, assume unlocked
            return true;
        }
    }
    
    /**
     * Get the required kill count for the unlock quest.
     * @return The number of times the boss must be killed (always 1 currently)
     */
    public static int getRequiredKillCount() {
        return 1;
    }
    
    /**
     * Get the current kill count for the unlock boss.
     * @param client The ServerClient to check
     * @return Current kill count, or 0 if error
     */
    public static int getCurrentKillCount(ServerClient client) {
        if (client == null) return 0;
        
        String unlockBoss = ModConfig.Guilds.unlockBoss;
        if (unlockBoss == null || unlockBoss.isEmpty() || "NONE".equalsIgnoreCase(unlockBoss)) {
            return 0;
        }
        
        String mobStringID = getBossStringID(unlockBoss);
        
        try {
            return client.characterStats().mob_kills.getKills(mobStringID);
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Get the display name of the unlock boss.
     * @return Display name for UI, or "None" if no boss required
     */
    public static String getUnlockBossDisplayName() {
        String unlockBoss = ModConfig.Guilds.unlockBoss;
        
        if (unlockBoss == null || unlockBoss.isEmpty() || "NONE".equalsIgnoreCase(unlockBoss)) {
            return "None";
        }
        
        // Convert PIRATE_CAPTAIN to "Pirate Captain"
        return formatBossName(unlockBoss);
    }
    
    /**
     * Check if guild unlock requires a boss kill.
     * @return true if a boss must be defeated
     */
    public static boolean requiresBossKill() {
        String unlockBoss = ModConfig.Guilds.unlockBoss;
        return unlockBoss != null && !unlockBoss.isEmpty() && !"NONE".equalsIgnoreCase(unlockBoss);
    }
    
    /**
     * Convert config boss name to mob registry string ID.
     * E.g., "PIRATE_CAPTAIN" -> "piratecaptain"
     */
    private static String getBossStringID(String configName) {
        if (configName == null) return "";
        // Remove underscores and convert to lowercase
        return configName.replace("_", "").toLowerCase();
    }
    
    /**
     * Format boss name for display.
     * E.g., "PIRATE_CAPTAIN" -> "Pirate Captain"
     */
    private static String formatBossName(String configName) {
        if (configName == null || configName.isEmpty()) return "Unknown";
        
        String[] parts = configName.split("_");
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(" ");
            if (parts[i].length() > 0) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) {
                    sb.append(parts[i].substring(1).toLowerCase());
                }
            }
        }
        
        return sb.toString();
    }
}
