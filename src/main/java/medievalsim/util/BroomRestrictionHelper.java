package medievalsim.util;

import medievalsim.zones.domain.AdminZonesLevelData;
import medievalsim.zones.domain.ProtectedZone;
import medievalsim.zones.settlement.SettlementProtectionData;
import medievalsim.zones.settlement.SettlementProtectionHelper;
import necesse.engine.localization.Localization;
import necesse.engine.network.packet.PacketMobMount;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.summon.summonFollowingMob.mountFollowingMob.WitchBroomMob;
import necesse.inventory.item.mountItem.MountItem;
import necesse.inventory.item.mountItem.WitchBroomMountItem;
import necesse.level.maps.Level;

/**
 * Centralizes broom restriction logic for protected zones and settlements.
 */
public final class BroomRestrictionHelper {

    private static final String MESSAGE_ZONE_KEY = "broomdisabledzone";
    private static final String MESSAGE_SETTLEMENT_KEY = "broomdisabledsettlement";

    private BroomRestrictionHelper() {
    }

    public enum RestrictionSource {
        PROTECTED_ZONE(MESSAGE_ZONE_KEY),
        SETTLEMENT(MESSAGE_SETTLEMENT_KEY);

        private final String messageKey;

        RestrictionSource(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }
    }

    public static boolean isBroomMountItem(MountItem item) {
        if (item == null) {
            return false;
        }
        if (item instanceof WitchBroomMountItem) {
            return true;
        }
        return item.mobStringID != null && item.mobStringID.toLowerCase().contains("broom");
    }

    public static boolean isBroomMount(Mob mob) {
        return mob instanceof WitchBroomMob;
    }

    public static String validateBroomMountUsage(PlayerMob player, Level level) {
        return validateBroomMountUsage(player, level, true);
    }

    public static RestrictionSource getRestrictionSource(PlayerMob player, Level level) {
        if (player == null || level == null || !player.isServerClient()) {
            return null;
        }
        ServerClient client = player.getServerClient();
        if (client == null) {
            return null;
        }
        return getRestrictionSource(client, level, player.getTileX(), player.getTileY());
    }

    private static RestrictionSource getRestrictionSource(ServerClient client, Level level, int tileX, int tileY) {
        boolean shouldCreateZoneData = level != null && level.isServer();
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, shouldCreateZoneData);
        if (zoneData != null) {
            ProtectedZone zone = zoneData.getProtectedZoneAt(tileX, tileY);
            if (zone != null && zone.shouldDisableBrooms(client, level)) {
                return RestrictionSource.PROTECTED_ZONE;
            }
        }

        SettlementProtectionHelper.SettlementProtectionContext ctx =
            SettlementProtectionHelper.getProtectionContext(level, tileX, tileY);
        if (ctx != null) {
            SettlementProtectionData data = ctx.data();
            if (data != null && data.isBroomRidingDisabled() &&
                !SettlementProtectionHelper.hasElevatedAccess(client, level, ctx.settlement())) {
                return RestrictionSource.SETTLEMENT;
            }
        }
        return null;
    }

    private static String validateBroomMountUsage(PlayerMob player, Level level, boolean sendChat) {
        RestrictionSource source = getRestrictionSource(player, level);
        if (source == null) {
            return null;
        }
        ServerClient client = player.getServerClient();
        if (client == null) {
            return null;
        }
        return sendRestrictionMessage(client, source.getMessageKey(), sendChat);
    }

    private static String sendRestrictionMessage(ServerClient client, String key, boolean sendChat) {
        String message = Localization.translate("message", key);
        if (sendChat && client != null) {
            client.sendChatMessage(message);
        }
        return message;
    }

    /**
     * Enforce broom riding restrictions by teleporting player to nearest safe edge and dismounting.
     * Provides better UX than instant dismount - player is moved to zone boundary first.
     */
    public static void enforceNoBroomRiding(ServerClient client, RestrictionSource source) {
        if (client == null || client.playerMob == null || client.getServer() == null) {
            return;
        }
        Mob mount = client.playerMob.getMount();
        if (!isBroomMount(mount)) {
            return;
        }

        Level level = client.playerMob.getLevel();
        if (level == null) {
            return;
        }

        // Find nearest safe position (outside restricted zone)
        java.awt.geom.Point2D.Float safePos = findNearestSafePosition(client, level);
        
        // Teleport player to safe position BEFORE dismounting (prevents fall damage)
        if (safePos != null) {
            client.playerMob.setPos(safePos.x, safePos.y, true);
        }

        // Now dismount at safe location
        client.playerMob.dismount();

        // Sync dismount with all clients
        Server server = client.getServer();
        float finalX = safePos != null ? safePos.x : client.playerMob.x;
        float finalY = safePos != null ? safePos.y : client.playerMob.y;
        
        server.network.sendToClientsWithEntity(
            new PacketMobMount(client.playerMob.getUniqueID(), -1, false, finalX, finalY),
            client.playerMob);

        // Send clear message explaining what happened
        String message = Localization.translate("message", source.getMessageKey());
        client.sendChatMessage(message);
    }

    /**
     * Find nearest position outside restricted zones using expanding square search.
     * Max radius: 20 tiles (640 pixels).
     * @return Safe position in world coordinates (tile center), or null if not found
     */
    private static java.awt.geom.Point2D.Float findNearestSafePosition(ServerClient client, Level level) {
        if (client == null || client.playerMob == null || level == null) {
            return null;
        }

        int currentTileX = client.playerMob.getTileX();
        int currentTileY = client.playerMob.getTileY();

        // Search in expanding square pattern
        for (int radius = 1; radius <= 20; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    // Only check perimeter tiles
                    if (Math.abs(dx) != radius && Math.abs(dy) != radius) {
                        continue;
                    }

                    int checkTileX = currentTileX + dx;
                    int checkTileY = currentTileY + dy;

                    // Check if this position is safe (no restrictions)
                    RestrictionSource restriction = getRestrictionSource(client, level, checkTileX, checkTileY);
                    if (restriction == null) {
                        // Found safe spot! Convert to world coords (tile center)
                        float worldX = checkTileX * 32.0f + 16.0f;
                        float worldY = checkTileY * 32.0f + 16.0f;
                        return new java.awt.geom.Point2D.Float(worldX, worldY);
                    }
                }
            }
        }

        // Couldn't find safe position - log warning
        ModLogger.warn("Could not find safe position for player %s within 20 tiles", client.getName());
        return null;
    }
}
