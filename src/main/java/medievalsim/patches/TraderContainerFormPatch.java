package medievalsim.patches;

import medievalsim.config.ModConfig;
import medievalsim.packets.PacketOpenGrandExchange;
import medievalsim.util.ModLogger;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.presets.containerComponent.mob.DialogueForm;
import necesse.gfx.forms.presets.containerComponent.mob.TraderHumanContainerForm;
import necesse.inventory.container.mob.TraderHumanContainer;
import net.bytebuddy.asm.Advice;

/**
 * Patches TraderHumanContainerForm to add Grand Exchange dialogue option.
 * Uses ByteBuddy to inject code after the addShopDialogueOptions() method.
 */
@ModMethodPatch(target = TraderHumanContainerForm.class, name = "addShopDialogueOptions", arguments = {})
public class TraderContainerFormPatch {

    @Advice.OnMethodExit
    public static void onExit(@Advice.This TraderHumanContainerForm<? extends TraderHumanContainer> traderForm,
                              @Advice.FieldValue(value = "dialogueForm") DialogueForm dialogueForm,
                              @Advice.FieldValue(value = "client") Client client) {

        // Check if Grand Exchange is enabled
        if (!ModConfig.GrandExchange.enabled) {
            ModLogger.debug("Grand Exchange is disabled in config, skipping Trader dialogue option");
            return;
        }

        try {
            // Add Grand Exchange dialogue option using helper method to avoid lambda access issues
            addGrandExchangeDialogueOption(dialogueForm, client);

            ModLogger.debug("Successfully added Grand Exchange dialogue option to Trader NPC");

        } catch (Exception e) {
            ModLogger.error("Failed to add Grand Exchange dialogue option to Trader NPC", e);
        }
    }

    /**
     * Helper method to add Grand Exchange dialogue option.
     * Public static method to avoid IllegalAccessError with ByteBuddy lambda injection.
     */
    public static void addGrandExchangeDialogueOption(DialogueForm dialogueForm, Client client) {
        dialogueForm.addDialogueOption(
            new StaticMessage("Grand Exchange"),
            () -> {
                // Send packet to open Grand Exchange
                client.network.sendPacket(new PacketOpenGrandExchange());
                ModLogger.debug("Sent PacketOpenGrandExchange to server from Trader dialogue");
            }
        );
    }
}

