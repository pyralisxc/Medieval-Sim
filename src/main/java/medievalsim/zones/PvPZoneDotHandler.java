package medievalsim.zones;

import java.lang.reflect.Field;
import java.util.List;

import medievalsim.util.ModLogger;
import necesse.level.maps.Level;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.buffs.ActiveBuff;

/**
 * Mod-only handler that post-processes ActiveBuff.dotBuffer for mobs inside PvP zones.
 *
 * This avoids editing engine sources by using reflection to access the protected
 * dotBuffer field and scaling it according to the PvP zone settings.
 */
public class PvPZoneDotHandler {
    private static Field dotBufferField;

    static {
        try {
            dotBufferField = ActiveBuff.class.getDeclaredField("dotBuffer");
            dotBufferField.setAccessible(true);
        } catch (Exception e) {
            dotBufferField = null;
            ModLogger.error("PvPZoneDotHandler - failed to access ActiveBuff.dotBuffer", e);
        }
    }

    /**
     * Run on the server once per level tick (after engine DoT accumulation). This will
     * scale each ActiveBuff.dotBuffer for mobs inside a PvP zone using the zone's
     * dotDamageMultiplier and dotIntervalMultiplier.
     */
    public static void processLevelTick(Level level) {
        if (level == null || !level.isServer()) return;
        if (dotBufferField == null) return; // reflection unavailable

        AdminZonesLevelData zones = AdminZonesLevelData.getZoneData(level, false);
        if (zones == null) return;

        try {
            // Iterate all mobs on the level
            for (Mob mob : level.entityManager.mobs) {
                if (mob == null || mob.removed()) continue;

                PvPZone zone = zones.getPvPZoneAt(mob.getX(), mob.getY());
                if (zone == null) continue;

                float dmgMult = zone.dotDamageMultiplier;
                float intervalMult = zone.dotIntervalMultiplier;
                // If multipliers are default (1.0), skip
                if (dmgMult == 1.0f && intervalMult == 1.0f) continue;

                List<ActiveBuff> abList = mob.buffManager.getArrayBuffs();
                if (abList == null || abList.isEmpty()) continue;

                float combined = Math.max(0f, dmgMult) * Math.max(0f, intervalMult);

                for (ActiveBuff ab : abList) {
                    if (ab == null) continue;
                    try {
                        float buf = dotBufferField.getFloat(ab);
                        if (buf <= 0f) continue;
                        float newBuf = buf * combined;
                        dotBufferField.setFloat(ab, newBuf);
                    } catch (Exception ex) {
                        // fail-fast per-buff but continue others
                    }
                }
            }
        } catch (Exception e) {
            ModLogger.error("PvPZoneDotHandler error", e);
        }
    }
}
