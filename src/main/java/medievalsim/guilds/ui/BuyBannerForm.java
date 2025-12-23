/*
 * BuyBannerForm - Client UI for purchasing guild banners
 * Part of Medieval Sim Mod guild management system.
 * Per docs: shows cost, placement options, and enforces maxBannersPerSettlementPerGuild.
 */
package medievalsim.guilds.ui;

import medievalsim.util.ModLogger;
import necesse.engine.localization.Localization;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.presets.containerComponent.ContainerForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

import java.awt.Color;

/**
 * Client-side form for purchasing guild banners.
 * Shows cost and purchase options with limit enforcement.
 */
public class BuyBannerForm extends ContainerForm<BuyBannerContainer> {

    private static final int FORM_WIDTH = 320;
    private static final int FORM_HEIGHT = 220;
    private static final int BUTTON_WIDTH = 130;
    
    public BuyBannerForm(Client client, BuyBannerContainer container) {
        super(client, FORM_WIDTH, FORM_HEIGHT, container);
        
        ModLogger.info("BuyBannerForm created for guild %s", container.getGuildName());
        setupUI();
    }
    
    private void setupUI() {
        // Title
        FormLabel titleLabel = new FormLabel(
            Localization.translate("ui", "buybanner.title"),
            new FontOptions(18),
            FormLabel.ALIGN_MID,
            FORM_WIDTH / 2, 15);
        addComponent(titleLabel);
        
        int y = 50;
        
        // Guild name
        FormLabel guildLabel = new FormLabel(
            String.format("Guild: %s", container.getGuildName()),
            new FontOptions(12).color(Color.LIGHT_GRAY),
            FormLabel.ALIGN_MID,
            FORM_WIDTH / 2, y);
        addComponent(guildLabel);
        y += 25;
        
        // Cost display
        FormLabel costLabel = new FormLabel(
            String.format("Cost: %,d gold", container.getBannerCost()),
            new FontOptions(14).color(Color.YELLOW),
            FormLabel.ALIGN_MID,
            FORM_WIDTH / 2, y);
        addComponent(costLabel);
        y += 25;
        
        // Current/max banners
        String limitText = String.format("Banners: %d / %d", 
            container.getCurrentBannerCount(), container.getMaxBanners());
        Color limitColor = container.canBuyMore() ? Color.GREEN : Color.RED;
        FormLabel limitLabel = new FormLabel(
            limitText,
            new FontOptions(12).color(limitColor),
            FormLabel.ALIGN_MID,
            FORM_WIDTH / 2, y);
        addComponent(limitLabel);
        y += 35;
        
        // Action buttons
        int btnX = (FORM_WIDTH - (BUTTON_WIDTH * 2 + 10)) / 2;
        
        if (container.canBuyMore()) {
            // Purchase & Place button
            FormTextButton placeBtn = new FormTextButton(
                Localization.translate("ui", "buybanner.purchaseplace"),
                btnX, y, BUTTON_WIDTH, FormInputSize.SIZE_24, ButtonColor.BASE);
            placeBtn.onClicked(e -> onPurchaseAndPlace());
            addComponent(placeBtn);
            
            // Purchase to Inventory button
            FormTextButton invBtn = new FormTextButton(
                Localization.translate("ui", "buybanner.purchaseinv"),
                btnX + BUTTON_WIDTH + 10, y, BUTTON_WIDTH, FormInputSize.SIZE_24, ButtonColor.BASE);
            invBtn.onClicked(e -> onPurchaseToInventory());
            addComponent(invBtn);
        } else {
            // At max banners - show message
            FormLabel maxLabel = new FormLabel(
                Localization.translate("ui", "buybanner.atlimit"),
                new FontOptions(12).color(Color.RED),
                FormLabel.ALIGN_MID,
                FORM_WIDTH / 2, y + 5);
            addComponent(maxLabel);
        }
        
        // Close/Cancel button at bottom
        int closeY = FORM_HEIGHT - 45;
        FormTextButton closeButton = new FormTextButton(
            Localization.translate("ui", "cancel"),
            (FORM_WIDTH - 120) / 2, closeY, 120, FormInputSize.SIZE_24, ButtonColor.BASE);
        closeButton.onClicked(e -> client.closeContainer(true));
        addComponent(closeButton);
    }
    
    private void onPurchaseAndPlace() {
        ModLogger.debug("Purchase & Place banner clicked for guild %d", container.getGuildID());
        // Send packet to server to purchase banner and initiate placement
        client.network.sendPacket(new medievalsim.packets.PacketBuyGuildBanner(container.getGuildID(), true));
        client.closeContainer(true);
    }
    
    private void onPurchaseToInventory() {
        ModLogger.debug("Purchase to Inventory clicked for guild %d", container.getGuildID());
        // Send packet to server to purchase banner to inventory
        client.network.sendPacket(new medievalsim.packets.PacketBuyGuildBanner(container.getGuildID(), false));
        client.closeContainer(true);
    }
    
    @Override
    public boolean shouldOpenInventory() {
        return false;
    }
}
