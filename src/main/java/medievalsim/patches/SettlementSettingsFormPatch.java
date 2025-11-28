/*
 * Patch: SettlementSettingsForm.update
 * Purpose: Add "Protection" button to settlement settings form at the top
 */
package medievalsim.patches;

import medievalsim.zones.settlement.SettlementProtectionButtonHelper;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementSettingsForm;
import necesse.inventory.container.settlement.SettlementContainer;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(
    target = SettlementSettingsForm.class,
    name = "update",
    arguments = {}
)
public class SettlementSettingsFormPatch {

    @Advice.OnMethodExit
    public static void onUpdateExit(
        @Advice.This SettlementSettingsForm<?> form,
        @Advice.FieldValue("client") Client client,
        @Advice.FieldValue("container") SettlementContainer container,
        @Advice.FieldValue("settings") necesse.gfx.forms.Form settings
    ) {
        // Delegate to helper class to avoid ByteBuddy access issues
        // This will be called after the vanilla update() completes
        SettlementProtectionButtonHelper.addProtectionButton(settings, client, container);
    }
}

