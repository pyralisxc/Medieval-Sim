/*
 * Mob damage calculation patch for PvP zones
 * Handles PvP damage multipliers and immunity checks
 */
package medievalsim.patches;

import medievalsim.zones.domain.AdminZonesLevelData;
import medievalsim.zones.domain.PvPZone;
import medievalsim.zones.service.PvPZoneTracker;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.MobBeforeHitCalculatedEvent;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;

public class MobBeforeHitCalculatedPatch {

    @ModMethodPatch(target=Mob.class, name="doBeforeHitCalculatedLogic", arguments={MobBeforeHitCalculatedEvent.class})
    public static class DoBeforeHitCalculatedLogic {
        @Advice.OnMethodExit
        static void onExit(@Advice.This Mob target, @Advice.Argument(value=0) MobBeforeHitCalculatedEvent event) {
            if (!target.isServer()) {
                return;
            }
            if (event.attacker == null) {
                return;
            }
            Mob attackOwner = event.attacker.getAttackOwner();
            if (attackOwner == null || !attackOwner.isPlayer) {
                return;
            }
            AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(target.getLevel(), false);
            if (zoneData == null) {
                return;
            }
            PvPZone attackerZone = zoneData.getPvPZoneAt(attackOwner.x, attackOwner.y);
            if (attackerZone == null) {
                return;
            }
            PvPZone targetZone = zoneData.getPvPZoneAt(target.x, target.y);
            if (targetZone != attackerZone) {
                return;
            }
            PlayerMob attackerPlayer = (PlayerMob)attackOwner;
            ServerClient attackerClient = attackerPlayer.getServerClient();
            if (attackerClient != null) {
                long serverTime = target.getLevel().getServer().world.worldEntity.getTime();
                if (target.isPlayer) {
                    PlayerMob targetPlayer = (PlayerMob)target;
                    ServerClient targetClient = targetPlayer.getServerClient();
                    if (targetClient != null) {
                        boolean hasImmunity = targetPlayer.buffManager.hasBuff("pvpimmunity");
                        if (hasImmunity) {
                            // Target has PvP immunity - preventing damage
                            event.prevent();
                            return;
                        }
                        event.damage = Math.max(1, (int)((float)event.damage * attackerZone.damageMultiplier));
                        // PvP damage applied with multiplier
                        PvPZoneTracker.recordCombat(attackerClient, serverTime);
                        PvPZoneTracker.recordCombat(targetClient, serverTime);
                    }
                } else {
                    event.damage = Math.max(1, (int)((float)event.damage * attackerZone.damageMultiplier));
                    // Training dummy damage applied with PvP multiplier
                }
            }
        }
    }
}

