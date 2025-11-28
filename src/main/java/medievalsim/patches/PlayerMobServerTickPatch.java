package medievalsim.patches;

import medievalsim.util.BroomRestrictionHelper;
import medievalsim.util.BroomRestrictionHelper.RestrictionSource;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(target = PlayerMob.class, name = "serverTick", arguments = {})
public class PlayerMobServerTickPatch {

    @Advice.OnMethodExit
    public static void onExit(@Advice.This PlayerMob player) {
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
        Mob mount = player.getMount();
        if (!BroomRestrictionHelper.isBroomMount(mount)) {
            return;
        }

        RestrictionSource source = BroomRestrictionHelper.getRestrictionSource(player, level);
        if (source != null) {
            BroomRestrictionHelper.enforceNoBroomRiding(client, source);
        }
    }
}
