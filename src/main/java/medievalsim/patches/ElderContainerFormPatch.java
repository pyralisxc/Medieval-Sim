package medievalsim.patches;

import medievalsim.config.ModConfig;
import medievalsim.packets.PacketOpenBank;
import medievalsim.banking.PinDialog;
import necesse.gfx.forms.ContinueComponentManager;
import medievalsim.util.ModLogger;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.presets.containerComponent.mob.DialogueForm;
import necesse.gfx.forms.presets.containerComponent.mob.ElderContainerForm;
import necesse.inventory.container.mob.ElderContainer;
import net.bytebuddy.asm.Advice;

/**
 * Patch to add banking dialogue option to Elder NPC.
 * Hooks into ElderContainerForm.addQuestsDialogueOption() to inject the "Bank" option.
 */
@ModMethodPatch(target = ElderContainerForm.class, name = "addQuestsDialogueOption", arguments = {})
public class ElderContainerFormPatch {
    
    @Advice.OnMethodExit
    public static void onExit(@Advice.This ElderContainerForm<? extends ElderContainer> elderForm,
                              @Advice.FieldValue(value = "dialogueForm") DialogueForm dialogueForm,
                              @Advice.FieldValue(value = "client") Client client) {

        // Check if banking is enabled in config
        if (!ModConfig.Banking.enabled) {
            ModLogger.debug("Banking is disabled in config, skipping Elder dialogue option");
            return;
        }

        try {
            // Add the "Bank" dialogue option using a helper method to avoid lambda access issues
            addBankDialogueOption(dialogueForm, client);

            ModLogger.debug("Successfully added banking dialogue option to Elder NPC");

        } catch (Exception e) {
            ModLogger.error("Failed to add banking dialogue option to Elder NPC", e);
        }
    }

    /**
     * Helper method to add bank dialogue option.
     * Public static method to avoid IllegalAccessError with ByteBuddy lambda injection.
     */
    public static void addBankDialogueOption(DialogueForm dialogueForm, Client client) {
        dialogueForm.addDialogueOption(
            new LocalMessage("ui", "medievalsim_openbank"),
                () -> {
                    // If bank PIN is required, show a dialog to enter PIN and send it to server
                    if (ModConfig.Banking.requirePIN) {
                        PinDialog dialog = PinDialog.createEnterPinDialog(client, (pin) -> {
                            client.network.sendPacket(new PacketOpenBank(pin));
                            ModLogger.debug("Sent PacketOpenBank with PIN to server from Elder dialogue");
                        });

                        if (dialogueForm.getManager() instanceof ContinueComponentManager) {
                            ((ContinueComponentManager) dialogueForm.getManager()).addContinueForm(null, dialog);
                        }
                    } else {
                        // Not requiring PIN - open directly
                        client.network.sendPacket(new PacketOpenBank());
                        ModLogger.debug("Sent PacketOpenBank to server from Elder dialogue");
                    }
                }
        );
    }
}

