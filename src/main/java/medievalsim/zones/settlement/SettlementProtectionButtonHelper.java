package medievalsim.zones.settlement;

import medievalsim.config.ModConfig;
import medievalsim.ui.SettlementProtectionDialog;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.components.FormButton;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.events.FormEventListener;
import necesse.gfx.forms.events.FormInputEvent;
import necesse.inventory.container.settlement.SettlementContainer;
import necesse.inventory.container.settlement.SettlementDependantContainer;

/**
 * Helper class to add the Protection button to settlement settings form.
 * This is called from SettlementSettingsFormPatch.
 */
public class SettlementProtectionButtonHelper {

    /**
     * Add the Protection button to the settlement settings form.
     * This method is public and static so it can be called from the ByteBuddy patch.
     *
     * The button is inserted at the top (after the title label) by shifting all existing
     * components down by 45 pixels (40px button + 5px spacing).
     *
     * @param settings The settings form
     * @param client The client instance
     * @param container The settlement container
     */
    public static void addProtectionButton(necesse.gfx.forms.Form settings, Client client, SettlementContainer container) {
        // Only add protection button if settlement protection is enabled in config
        if (!ModConfig.Settlements.protectionEnabled) {
            return;
        }

        // Check if player is settlement owner
        boolean isOwner = ((SettlementDependantContainer) container).isSettlementOwner(client);

        // Shift all existing components down by 45 pixels to make room for the Protection button
        // (Skip the first component which is the title label)
        final int BUTTON_HEIGHT_WITH_SPACING = 45;
        settings.getComponents().stream()
            .skip(1)  // Skip title label
            .forEach(component -> {
                if (component instanceof necesse.gfx.forms.position.FormPositionContainer) {
                    necesse.gfx.forms.position.FormPositionContainer positionable =
                        (necesse.gfx.forms.position.FormPositionContainer) component;
                    positionable.setY(positionable.getY() + BUTTON_HEIGHT_WITH_SPACING);
                }
            });

        // Create protection button at the top (Y=45, right after the title label which is at Y=10)
        FormLocalTextButton protectionButton = settings.addComponent(
            new FormLocalTextButton(
                "ui",
                "settlementprotection",
                40,
                45,  // Fixed Y position at top (after title)
                settings.getWidth() - 80
            )
        );

        // Create a public listener class to avoid ByteBuddy access issues
        ProtectionButtonListener listener = new ProtectionButtonListener(client, container);
        protectionButton.onClicked(listener);

        protectionButton.setActive(isOwner);

        if (!protectionButton.isActive()) {
            protectionButton.setLocalTooltip(new LocalMessage("ui",
                ((SettlementDependantContainer) container).hasSettlementOwner()
                    ? "settlementowneronly"
                    : "settlementclaimfirst"));
        } else {
            protectionButton.setLocalTooltip(new LocalMessage("ui", "settlementprotectiontip"));
        }

        // Increase settings form height to accommodate new button
        settings.setHeight(settings.getHeight() + BUTTON_HEIGHT_WITH_SPACING);
    }
    
    /**
     * Public static listener class for the protection button.
     * This is a separate public class to avoid ByteBuddy access issues with anonymous classes.
     */
    public static class ProtectionButtonListener implements FormEventListener<FormInputEvent<FormButton>> {
        private final Client client;
        private final SettlementContainer container;
        
        public ProtectionButtonListener(Client client, SettlementContainer container) {
            this.client = client;
            this.container = container;
        }
        
        @Override
        public void onEvent(FormInputEvent<FormButton> event) {
            if (container.settlementData != null) {
                // Open settlement protection dialog
                SettlementProtectionDialog dialog = new SettlementProtectionDialog(
                    client,
                    container.settlementData.tileX,
                    container.settlementData.tileY
                );
                dialog.openDialog();
            }
        }
    }
}

