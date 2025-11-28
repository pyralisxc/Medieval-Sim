package medievalsim.patches;
import medievalsim.buildmode.service.BuildModeManager;
import medievalsim.commandcenter.worldclick.WorldClickIntegration;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import net.bytebuddy.asm.Advice;

public class ClientTickPatch {

    @ModMethodPatch(target=Client.class, name="tick", arguments={})
    public static class Tick {
        @Advice.OnMethodExit
        static void onExit(@Advice.This Client client) {
            if (BuildModeManager.hasInstance()) {
                BuildModeManager.getInstance().tick();
            }
            
            // Update world-click hover coordinates (if active)
            WorldClickIntegration.updateHoverPosition();
        }
    }
}

