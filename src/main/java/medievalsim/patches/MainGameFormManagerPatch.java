package medievalsim.patches;
import medievalsim.admintools.service.AdminToolsHelper;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.MainGameFormManager;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(target=MainGameFormManager.class, name="setup", arguments={})
public class MainGameFormManagerPatch {
    @Advice.OnMethodExit
    static void onExit(@Advice.This MainGameFormManager mainGameFormManager, @Advice.FieldValue(value="client") Client client) {
        AdminToolsHelper.setupAdminButton(mainGameFormManager, client);
    }
}

