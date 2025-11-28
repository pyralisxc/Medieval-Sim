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
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
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

    public static void enforceNoBroomRiding(ServerClient client, RestrictionSource source) {
        if (client == null || client.playerMob == null || client.getServer() == null) {
            return;
        }
        Mob mount = client.playerMob.getMount();
        if (!isBroomMount(mount)) {
            return;
        }

        float previousX = client.playerMob.x;
        float previousY = client.playerMob.y;
        client.playerMob.dismount();
        client.playerMob.setPos(previousX, previousY, true);

        Server server = client.getServer();
        server.network.sendToClientsWithEntity(
            new PacketMobMount(client.playerMob.getUniqueID(), -1, false, previousX, previousY),
            client.playerMob);

        client.sendChatMessage(Localization.translate("message", source.getMessageKey()));
    }
}
