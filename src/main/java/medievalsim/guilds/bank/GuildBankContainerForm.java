/*
 * Guild Bank Container Form for Medieval Sim Mod
 * Client-side UI for the guild bank.
 */
package medievalsim.guilds.bank;

import medievalsim.util.ModLogger;
import necesse.engine.localization.Localization;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.containerSlot.FormContainerSlot;
import necesse.gfx.forms.presets.containerComponent.ContainerFormSwitcher;
import necesse.gfx.gameFont.FontOptions;

import java.awt.Color;

/**
 * Client-side form for guild bank UI.
 * Displays the bank tabs and inventory grid.
 */
public class GuildBankContainerForm extends ContainerFormSwitcher<GuildBankContainer> {

    // UI dimensions
    private static final int SLOTS_PER_ROW = 10;
    private static final int ROWS = 5;
    private static final int SLOT_SIZE = 40;
    private static final int SLOT_PADDING = 4;
    
    private FormContentBox bankSlotsBox;
    
    public GuildBankContainerForm(Client client, GuildBankContainer container) {
        super(client, container);
        
        ModLogger.debug("GuildBankContainerForm created for guild %d, tab %d", 
            container.getGuildID(), container.getCurrentTab());
    }

    @Override
    protected void init() {
        super.init();
        
        // Calculate dimensions
        int slotAreaWidth = SLOTS_PER_ROW * (SLOT_SIZE + SLOT_PADDING);
        int slotAreaHeight = ROWS * (SLOT_SIZE + SLOT_PADDING);
        
        // Title label
        FormLabel titleLabel = new FormLabel(
            Localization.translate("ui", "guildbank"),
            new FontOptions(20),
            FormLabel.ALIGN_MID, 
            slotAreaWidth / 2, 10);
        addComponent(titleLabel);
        
        // Guild name subtitle
        FormLabel guildLabel = new FormLabel(
            Localization.translate("ui", "guild") + " #" + container.getGuildID(),
            new FontOptions(14).color(new Color(200, 200, 200)),
            FormLabel.ALIGN_MID,
            slotAreaWidth / 2, 35
        );
        addComponent(guildLabel);
        
        // Tab indicator
        FormLabel tabLabel = new FormLabel(
            Localization.translate("ui", "tab") + " " + (container.getCurrentTab() + 1),
            new FontOptions(12).color(new Color(150, 150, 150)),
            FormLabel.ALIGN_MID,
            slotAreaWidth / 2, 55
        );
        addComponent(tabLabel);
        
        // Bank slots area
        int slotsStartY = 75;
        bankSlotsBox = new FormContentBox(0, slotsStartY, slotAreaWidth, slotAreaHeight);
        addComponent(bankSlotsBox);
        
        // Add inventory slots if available
        if (container.BANK_SLOTS_START >= 0) {
            int slotIndex = container.BANK_SLOTS_START;
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < SLOTS_PER_ROW; col++) {
                    if (slotIndex <= container.BANK_SLOTS_END) {
                        int x = col * (SLOT_SIZE + SLOT_PADDING);
                        int y = row * (SLOT_SIZE + SLOT_PADDING);
                        FormContainerSlot slot = new FormContainerSlot(
                            client, container, slotIndex, x, y
                        );
                        bankSlotsBox.addComponent(slot);
                        slotIndex++;
                    }
                }
            }
        }
    }

    @Override
    public boolean shouldOpenInventory() {
        // Guild bank UI should show player inventory for deposit/withdraw
        return true;
    }
}
