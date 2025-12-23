/*
 * Guild Teleport Container Form for Medieval Sim Mod
 * Client-side UI showing all guild teleport banner destinations.
 * 
 * Based on Necesse's TeleportToTeamContainerForm pattern.
 */
package medievalsim.guilds.teleport;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import medievalsim.guilds.teleport.GuildTeleportContainer.TeleportDestination;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.Localization;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.client.Client;
import necesse.engine.window.GameWindow;
import necesse.engine.window.WindowManager;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.ContainerComponent;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.components.localComponents.FormLocalLabel;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.presets.containerComponent.ContainerForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

/**
 * UI form for guild teleport system.
 * Shows all banner destinations belonging to the player's guild.
 */
public class GuildTeleportContainerForm extends ContainerForm<GuildTeleportContainer> {
    
    protected int maxContentHeight = 350;
    protected FormContentBox contentBox;
    protected FormLocalTextButton closeButton;
    protected ArrayList<DestinationButton> destinationButtons = new ArrayList<>();
    
    public GuildTeleportContainerForm(Client client, GuildTeleportContainer container) {
        super(client, 400, 350, container);
        
        FormFlow flow = new FormFlow(8);
        
        // Header
        this.addComponent(flow.nextY(new FormLocalLabel("ui", "guildteleportheader", 
            new FontOptions(20), 0, this.getWidth() / 2, 0, this.getWidth() - 10), 5));
        
        // Subheader showing number of destinations
        LocalMessage countMsg = new LocalMessage("ui", "guildteleportcount", 
            "count", container.destinations.size());
        this.addComponent(flow.nextY(new FormLocalLabel(countMsg, 
            new FontOptions(14), 0, this.getWidth() / 2, 0, this.getWidth() - 10), 10));
        
        // Content box for destination list
        this.contentBox = this.addComponent(new FormContentBox(0, flow.next(), this.getWidth(), this.maxContentHeight));
        
        updateContent();
    }
    
    /**
     * Update the destination list.
     */
    public void updateContent() {
        // Clear existing components
        if (this.closeButton != null) {
            this.removeComponent(this.closeButton);
        }
        this.destinationButtons.clear();
        this.contentBox.clearComponents();
        
        FormFlow flow = new FormFlow(this.contentBox.getY());
        FormFlow contentFlow = new FormFlow(10);
        
        List<TeleportDestination> destinations = container.destinations;
        
        if (destinations.isEmpty()) {
            // No destinations message
            this.contentBox.addComponent(contentFlow.nextY(
                new FormLocalLabel("ui", "guildteleportnone", 
                    new FontOptions(16), 0, this.getWidth() / 2, 0, this.getWidth() - 10), 5));
            contentFlow.next(10);
        } else {
            // Create button for each destination
            for (int i = 0; i < destinations.size(); i++) {
                TeleportDestination dest = destinations.get(i);
                final int slotIndex = i;
                
                // Button text shows name and distance
                String buttonText = dest.displayName;
                if (dest.distance >= 0) {
                    buttonText += String.format(" (%.0f tiles)", dest.distance);
                } else {
                    buttonText += " (" + Localization.translate("ui", "differentisland") + ")";
                }
                
                FormTextButton destButton = this.contentBox.addComponent(
                    contentFlow.nextY(new FormTextButton(buttonText, 14, 0, 
                        this.getWidth() - 28, FormInputSize.SIZE_32, ButtonColor.BASE), 5));
                
                destButton.onClicked(e -> {
                    // Request teleport to this destination
                    container.teleportToSlotAction.runAndSend(slotIndex);
                    // Container will close from server side after teleport starts
                });
                
                this.destinationButtons.add(new DestinationButton(dest, destButton));
            }
        }
        
        // Calculate content height
        int contentHeight = contentFlow.next(4);
        if (contentHeight < this.maxContentHeight) {
            this.contentBox.setHeight(contentHeight);
        }
        this.contentBox.setContentBox(new Rectangle(0, 0, this.contentBox.getWidth(), contentHeight));
        
        flow.next(Math.min(contentHeight, this.maxContentHeight));
        
        // Close button
        this.closeButton = this.addComponent(flow.nextY(
            new FormLocalTextButton("ui", "closebutton", 4, 0, this.getWidth() - 8), 8));
        this.closeButton.onClicked(e -> this.client.closeContainer(true));
        
        this.setHeight(flow.next());
        this.onWindowResized(WindowManager.getWindow());
    }
    
    @Override
    public void onWindowResized(GameWindow window) {
        super.onWindowResized(window);
        ContainerComponent.setPosMiddle(this);
    }
    
    @Override
    public void draw(TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
        // Could update distance in real-time here if needed
        super.draw(tickManager, perspective, renderBox);
    }
    
    @Override
    public boolean shouldOpenInventory() {
        return false;
    }
    
    /**
     * Helper class to track destination buttons.
     */
    protected class DestinationButton {
        public final TeleportDestination destination;
        public final FormTextButton button;
        
        public DestinationButton(TeleportDestination destination, FormTextButton button) {
            this.destination = destination;
            this.button = button;
        }
    }
}
