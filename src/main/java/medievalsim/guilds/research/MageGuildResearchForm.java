/*
 * Extended Mage Container Form for Medieval Sim Mod
 * Adds "Guild Research" option to the Mage's dialogue.
 * 
 * This extends the vanilla MageContainerForm to inject guild research functionality.
 */
package medievalsim.guilds.research;

import medievalsim.guilds.client.ClientGuildManager;
import medievalsim.packets.PacketOpenGuildResearch;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.client.Client;
import necesse.inventory.container.mob.MageContainer;
import necesse.gfx.forms.presets.containerComponent.mob.MageContainerForm;

/**
 * Extended Mage form that adds Guild Research option.
 * 
 * Note: This requires replacing the container handler registration
 * to use this form instead of the vanilla MageContainerForm.
 */
public class MageGuildResearchForm extends MageContainerForm<MageContainer> {

    public MageGuildResearchForm(Client client, MageContainer container) {
        super(client, container);
    }

    @Override
    protected void addShopDialogueOptions() {
        // Add vanilla options first
        super.addShopDialogueOptions();

        // Add Guild Research option if player is in a guild
        if (isPlayerInGuild()) {
            this.dialogueForm.addDialogueOption(
                new LocalMessage("ui", "mageguildresearch"),
                this::openGuildResearch
            );
        }
    }

    /**
     * Check if the player is in a guild.
     */
    private boolean isPlayerInGuild() {
        // Use client-side guild manager
        return ClientGuildManager.get().isInGuild();
    }

    /**
     * Open the guild research interface.
     */
    private void openGuildResearch() {
        // Send packet to server to open research container
        client.network.sendPacket(new PacketOpenGuildResearch());
    }
}
