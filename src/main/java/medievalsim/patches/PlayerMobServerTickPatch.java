package medievalsim.patches;

import medievalsim.util.BroomRestrictionHelper;
import medievalsim.util.BroomRestrictionHelper.RestrictionSource;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

/**
 * Continuously enforces broom restrictions by checking every 5 ticks (4 times per second).
 * This is more performant than checking every tick while still providing smooth enforcement.
 */
@ModMethodPatch(target = PlayerMob.class, name = "serverTick", arguments = {})
public class PlayerMobServerTickPatch {

    // Static tick counter shared across all players
    // MUST be public - bytecode injection runs in target class context
    public static int globalTickCounter = 0;

    @Advice.OnMethodExit
    public static void onExit(@Advice.This PlayerMob player) {
        // Performance optimization: Only check every 5 ticks (4 times/sec instead of 20 times/sec)
        globalTickCounter++;
        if (globalTickCounter % 5 != 0) {
            return;
        }

        if (player == null || !player.isPlayer) {
            return;
        }
        Level level = player.getLevel();
        if (level == null || !level.isServer()) {
            return;
        }
        ServerClient client = player.getServerClient();
        if (client == null) {
            return;
        }
        
        // Early exit if not mounted (most common case)
        Mob mount = player.getMount();
        if (mount == null) {
            return;
        }
        
        // Check if it's a broom mount
        if (!BroomRestrictionHelper.isBroomMount(mount)) {
            return;
        }

        // Check if player is in restricted zone
        RestrictionSource source = BroomRestrictionHelper.getRestrictionSource(player, level);
        if (source != null) {
            BroomRestrictionHelper.enforceNoBroomRiding(client, source);
        }
    }
}
