package medievalsim.patches;

import medievalsim.util.BroomRestrictionHelper;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.mountItem.MountItem;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(target = MountItem.class, name = "canUseMount",
    arguments = {InventoryItem.class, PlayerMob.class, Level.class})
public class MountItemCanUseMountPatch {

    @Advice.OnMethodExit
    public static void onExit(@Advice.This MountItem mountItem,
                              @Advice.Argument(1) PlayerMob player,
                              @Advice.Argument(2) Level level,
                              @Advice.Return(readOnly = false) String result) {
        if (!BroomRestrictionHelper.isBroomMountItem(mountItem)) {
            return;
        }

        String restriction = BroomRestrictionHelper.validateBroomMountUsage(player, level);
        if (restriction != null) {
            result = restriction;
        }
    }
}
