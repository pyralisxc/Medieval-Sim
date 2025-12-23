package medievalsim.util;

import medievalsim.config.ModConfig;
import medievalsim.zones.domain.AdminZonesLevelData;
import medievalsim.zones.domain.ProtectedZone;
import medievalsim.zones.domain.PvPZone;
import medievalsim.zones.settlement.SettlementProtectionLevelData;
import necesse.engine.localization.Localization;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.item.Item;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;

/**
 * Centralized helper for validating boss summon attempts across all zone types.
 * Handles global config, per-zone settings, and settlement protection.
 */
public class BossSummonRestrictionHelper {

    /**
     * Check if a boss summon item is allowed at the specified location.
     * Respects global config, zone settings, and user permissions.
     * 
     * @param client The client attempting to summon
     * @param level The level where summon is attempted
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @param item The boss summon item being used
     * @return true if summon is allowed, false if blocked
     */
    public static boolean canSummonBoss(ServerClient client, Level level, int tileX, int tileY, Item item) {
        if (client == null || level == null || !level.isServer()) {
            return true; // Client-side check, allow (server will validate)
        }

        // Check 1: Global boss summons disabled?
        if (!ModConfig.BossSummons.allowBossSummonsGlobally) {
            sendDenialMessage(client, "Boss summons are disabled globally");
            return false;
        }

        PlayerMob player = client.playerMob;
        if (player == null) {
            return true; // Shouldn't happen, but be safe
        }

        // Check 2: Inside Protected Zone?
        RestrictionSource protectedZoneRestriction = checkProtectedZone(client, level, tileX, tileY);
        if (protectedZoneRestriction != null) {
            sendDenialMessage(client, protectedZoneRestriction.getMessage());
            return false;
        }

        // Check 3: Inside PvP Zone?
        RestrictionSource pvpZoneRestriction = checkPvPZone(level, tileX, tileY);
        if (pvpZoneRestriction != null) {
            sendDenialMessage(client, pvpZoneRestriction.getMessage());
            return false;
        }

        // Check 4: Inside Settlement with protection enabled?
        RestrictionSource settlementRestriction = checkSettlement(client, level, tileX, tileY);
        if (settlementRestriction != null) {
            sendDenialMessage(client, settlementRestriction.getMessage());
            return false;
        }

        // All checks passed
        return true;
    }

    /**
     * Check if boss summon is blocked by a Protected Zone.
     * @return RestrictionSource if blocked, null if allowed
     */
    private static RestrictionSource checkProtectedZone(ServerClient client, Level level, int tileX, int tileY) {
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        if (zoneData == null) {
            return null; // No zone data
        }

        ProtectedZone zone = zoneData.getProtectedZoneAt(tileX, tileY);
        if (zone == null) {
            return null; // Not in a protected zone
        }

        // Check if zone allows boss summons (respects elevation/ownership)
        if (zone.canSummonBoss(client, level)) {
            return null; // Allowed
        }

        return new RestrictionSource(
            "protectedzone",
            "Boss summons are not allowed in this Protected Zone"
        );
    }

    /**
     * Check if boss summon is blocked by a PvP Zone.
     * @return RestrictionSource if blocked, null if allowed
     */
    private static RestrictionSource checkPvPZone(Level level, int tileX, int tileY) {
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        if (zoneData == null) {
            return null;
        }

        PvPZone zone = zoneData.getPvPZoneAt(tileX, tileY);
        if (zone == null) {
            return null; // Not in PvP zone
        }

        // Check global config first
        if (!ModConfig.BossSummons.allowBossSummonsGlobally) {
            return new RestrictionSource("pvpzone", "Boss summons are disabled globally");
        }

        // Check zone-specific setting
        if (!zone.allowBossSummons) {
            return new RestrictionSource(
                "pvpzone",
                "Boss summons are not allowed in this PvP Zone"
            );
        }

        return null; // Allowed
    }

    /**
     * Check if boss summon is blocked by Settlement protection.
     * @return RestrictionSource if blocked, null if allowed
     */
    private static RestrictionSource checkSettlement(ServerClient client, Level level, int tileX, int tileY) {
        if (!ModConfig.Settlements.protectionEnabled) {
            return null; // Settlement protection disabled globally
        }

        // Get settlement protection level data
        LevelData protectionData = level.getLevelData("settlementprotectiondata");
        if (!(protectionData instanceof SettlementProtectionLevelData)) {
            return null; // No settlement protection data
        }

        SettlementProtectionLevelData spld = (SettlementProtectionLevelData) protectionData;
        medievalsim.zones.settlement.SettlementProtectionData protection = 
            spld.getManager().getProtectionData(tileX, tileY);

        if (protection == null || !protection.isEnabled()) {
            return null; // No protection at this location or not enabled
        }

        // Check if boss summons are allowed
        if (protection.getAllowBossSummons()) {
            return null; // Explicitly allowed
        }

        // Boss summons not allowed - check if player has settlement access
        PlayerMob player = client.playerMob;
        if (player == null) {
            return null;
        }

        // TODO: Add proper settlement membership check when settlement owner/member APIs are available
        // For now, we just block non-owners based on protection settings

        return new RestrictionSource(
            "settlement",
            "Boss summons are not allowed in this Settlement"
        );
    }

    /**
     * Send denial message to client.
     */
    private static void sendDenialMessage(ServerClient client, String message) {
        if (client != null && message != null) {
            client.sendChatMessage(message);
        }
    }

    /**
     * Check if an item is a boss summon item.
     * Boss summon items are ConsumableItems that spawn mobs.
     */
    public static boolean isBossSummonItem(Item item) {
        if (item == null) {
            return false;
        }

        // Check if it's a ConsumableItem (boss summons are consumable items)
        if (!(item instanceof necesse.inventory.item.placeableItem.consumableItem.ConsumableItem)) {
            return false;
        }

        // Known boss summon items by string ID
        String itemStringID = item.getStringID();
        return itemStringID.equals("royalspideregg") ||      // Royal Spider Queen
               itemStringID.equals("swampmothspore") ||      // Swamp Guardian
               itemStringID.equals("nightvestige") ||         // Void Wizard
               itemStringID.equals("ancientvolcanocore") ||   // Fallen Wizard
               itemStringID.equals("ancientazztechcore") ||   // Pest Warden
               itemStringID.equals("reapersummon") ||         // Reaper
               itemStringID.equals("piratecaptainmessage") || // Pirate Captain
               itemStringID.equals("frostsummon") ||          // Frosty
               itemStringID.equals("zombieevent");            // Zombie Horde (event)
    }

    /**
     * Data class for restriction source (zone type + message).
     */
    public static class RestrictionSource {
        private final String sourceType;
        private final String message;

        public RestrictionSource(String sourceType, String message) {
            this.sourceType = sourceType;
            this.message = message;
        }

        public String getSourceType() {
            return sourceType;
        }

        public String getMessage() {
            return message;
        }
    }
}
