/*
 * Guild Artisan Container Form for Medieval Sim Mod
 * Client-side UI for the guild artisan NPC.
 */
package medievalsim.guilds.mobs;

import medievalsim.guilds.GuildUnlockUtil;
import medievalsim.guilds.ui.CreateGuildDialog;
import medievalsim.guilds.ui.GuildBrowserForm;
import medievalsim.packets.PacketAcceptGuildUnlockQuest;
import medievalsim.util.ModLogger;
import necesse.engine.localization.Localization;
import necesse.engine.network.client.Client;
import necesse.gfx.GameColor;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.presets.containerComponent.ContainerForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

import java.awt.Color;

/**
 * Client-side form for guild artisan UI.
 * Provides options for guild management and crafting.
 */
public class GuildArtisanContainerForm extends ContainerForm<GuildArtisanContainer> {

    private static final int FORM_WIDTH = 350;
    private static final int FORM_HEIGHT = 300;
    private static final int BUTTON_WIDTH = 180;
    
    // Track if showing quest offer UI vs normal UI
    private boolean showingQuestOffer = false;
    
    public GuildArtisanContainerForm(Client client, GuildArtisanContainer container) {
        super(client, FORM_WIDTH, FORM_HEIGHT, container);
        
        ModLogger.info("GuildArtisanContainerForm created, playerGuildID=%d, unlocked=%b", 
            container.getPlayerGuildID(), container.hasUnlockedGuilds());
        
        // Check if player needs to see quest offer instead of normal UI
        if (!container.hasUnlockedGuilds() && !container.isPlayerInGuild()) {
            showingQuestOffer = true;
            setupQuestOfferUI();
        } else {
            setupUI();
        }
    }
    
    private void setupQuestOfferUI() {
        // Build quest offer UI directly as components (no nested Form)
        int y = 15;
        
        // Title
        addComponent(new FormLabel(
            Localization.translate("ui", "guildquestoffer.title"),
            new FontOptions(18),
            FormLabel.ALIGN_MID,
            FORM_WIDTH / 2, y
        ));
        y += 35;
        
        // Description
        addComponent(new FormLabel(
            Localization.translate("ui", "guildquestoffer.desc"),
            new FontOptions(12).color(Color.LIGHT_GRAY),
            FormLabel.ALIGN_MID,
            FORM_WIDTH / 2, y
        ));
        y += 25;
        
        // Requirements section
        addComponent(new FormLabel(
            Localization.translate("ui", "guildquestoffer.requirements"),
            new FontOptions(14),
            FormLabel.ALIGN_LEFT,
            20, y
        ));
        y += 22;
        
        // Boss requirement
        if (GuildUnlockUtil.requiresBossKill()) {
            String bossName = GuildUnlockUtil.getUnlockBossDisplayName();
            String reqText = String.format("• Defeat: %s", bossName);
            addComponent(new FormLabel(
                reqText,
                new FontOptions(12).color(Color.YELLOW),
                FormLabel.ALIGN_LEFT,
                30, y
            ));
            y += 20;
        }
        
        y += 30;
        
        // Buttons
        int buttonWidth = 120;
        int buttonSpacing = 20;
        int totalWidth = buttonWidth * 2 + buttonSpacing;
        int startX = (FORM_WIDTH - totalWidth) / 2;
        
        // Accept Quest button
        FormTextButton acceptBtn = addComponent(new FormTextButton(
            Localization.translate("ui", "guildquestoffer.accept"),
            startX, y, buttonWidth, FormInputSize.SIZE_32, ButtonColor.BASE
        ));
        acceptBtn.onClicked(e -> {
            ModLogger.debug("Accepting guild unlock quest");
            client.network.sendPacket(new PacketAcceptGuildUnlockQuest());
            client.closeContainer(true);
        });
        
        // Close button
        FormTextButton closeBtn = addComponent(new FormTextButton(
            Localization.translate("ui", "close"),
            startX + buttonWidth + buttonSpacing, y, buttonWidth, 
            FormInputSize.SIZE_32, ButtonColor.BASE
        ));
        closeBtn.onClicked(e -> client.closeContainer(true));
        
        ModLogger.debug("Showing quest offer UI - player hasn't unlocked guilds");
    }
    
    private void setupUI() {
        // Title
        FormLabel titleLabel = new FormLabel(
            Localization.translate("ui", "guildartisan.title"),
            new FontOptions(20), 
            FormLabel.ALIGN_MID, 
            FORM_WIDTH / 2, 20);
        addComponent(titleLabel);
        
        // Subtitle based on guild membership
        String subtitle;
        if (container.isPlayerInGuild()) {
            subtitle = Localization.translate("ui", "guildartisan.inmember");
        } else {
            subtitle = Localization.translate("ui", "guildartisan.notmember");
        }
        FormLabel subtitleLabel = new FormLabel(subtitle,
            new FontOptions(12).color(necesse.gfx.GameColor.ITEM_NORMAL.color.get()),
            FormLabel.ALIGN_MID,
            FORM_WIDTH / 2, 50);
        addComponent(subtitleLabel);
        
        // Button area
        int buttonX = (FORM_WIDTH - BUTTON_WIDTH) / 2;
        int buttonY = 80;
        int buttonSpacing = 45;
        
        if (container.isPlayerInGuild()) {
            // Guild member options - per docs: View Guild Info, Manage Guilds, Notifications
            // NOTE: Bank and Settings access removed per docs - use Settlement Settings (C-key) or GuildInfoPanel
            
            // View Guild Info
            FormTextButton infoButton = new FormTextButton(
                Localization.translate("ui", "guildartisan.viewguild"),
                buttonX, buttonY, BUTTON_WIDTH, FormInputSize.SIZE_32, ButtonColor.BASE);
            infoButton.onClicked(e -> onViewGuildInfo());
            addComponent(infoButton);
            buttonY += buttonSpacing;
            
            // Manage Guilds (per docs: handles per-guild leave, buy banner, etc.)
            FormTextButton manageButton = new FormTextButton(
                Localization.translate("ui", "guildartisan.manageguilds"),
                buttonX, buttonY, BUTTON_WIDTH, FormInputSize.SIZE_32, ButtonColor.BASE);
            manageButton.onClicked(e -> onManageGuilds());
            addComponent(manageButton);
            buttonY += buttonSpacing;
            
            // Notifications inbox (per docs: invites, guild notices, admin messages)
            FormTextButton notifButton = new FormTextButton(
                Localization.translate("ui", "guildartisan.notifications"),
                buttonX, buttonY, BUTTON_WIDTH, FormInputSize.SIZE_32, ButtonColor.BASE);
            notifButton.onClicked(e -> onOpenNotifications());
            addComponent(notifButton);
            buttonY += buttonSpacing;
            
        } else {
            // Non-member options
            
            // Create Guild
            FormTextButton createButton = new FormTextButton(
                Localization.translate("ui", "guildartisan.create"),
                buttonX, buttonY, BUTTON_WIDTH, FormInputSize.SIZE_32, ButtonColor.BASE);
            createButton.onClicked(e -> onCreateGuild());
            addComponent(createButton);
            buttonY += buttonSpacing;
            
            // Join Guild (browse)
            FormTextButton joinButton = new FormTextButton(
                Localization.translate("ui", "guildartisan.join"),
                buttonX, buttonY, BUTTON_WIDTH, FormInputSize.SIZE_32, ButtonColor.BASE);
            joinButton.onClicked(e -> onJoinGuild());
            addComponent(joinButton);
        }
        
        // Close button at bottom
        int closeY = FORM_HEIGHT - 50;
        FormTextButton closeButton = new FormTextButton(
            Localization.translate("ui", "close"),
            buttonX, closeY, BUTTON_WIDTH, FormInputSize.SIZE_32, ButtonColor.BASE);
        closeButton.onClicked(e -> client.closeContainer(true));
        addComponent(closeButton);
    }
    
    // === Button Handlers ===
    
    private void onViewGuildInfo() {
        ModLogger.debug("View guild info clicked");
        int guildID = container.getPlayerGuildID();
        if (guildID >= 0) {
            // Request full guild info from server - will open panel when response arrives
            client.network.sendPacket(new medievalsim.packets.PacketRequestGuildInfo(guildID));
            client.closeContainer(true);
        } else {
            client.chat.addMessage(GameColor.RED.getColorCode() + "Could not determine your guild.");
        }
    }
    
    private void onEditCrest() {
        ModLogger.debug("Edit crest clicked - requesting crest editor from server");
        // Request crest editor from server (validates permissions server-side)
        client.network.sendPacket(new medievalsim.packets.PacketRequestCrestEditor());
        client.closeContainer(true);
    }
    
    /**
     * Open Manage Guilds - per docs: per-guild leave, buy banner, etc.
     */
    private void onManageGuilds() {
        ModLogger.debug("Manage guilds clicked - requesting manage guilds form from server");
        client.network.sendPacket(new medievalsim.packets.PacketOpenManageGuilds());
        client.closeContainer(true);
    }
    
    /**
     * Open Notifications inbox - per docs: invites, guild notices, admin messages
     */
    private void onOpenNotifications() {
        ModLogger.debug("Notifications clicked - requesting notifications form from server");
        client.network.sendPacket(new medievalsim.packets.PacketOpenNotifications());
        client.closeContainer(true);
    }
    
    private void onCreateGuild() {
        ModLogger.debug("Create guild clicked");
        // Close the container first, then open the Create Guild dialog
        // This follows the proper ContinueForm pattern like SettlementProtectionDialog
        client.closeContainer(true);
        CreateGuildDialog.show(client);
    }
    
    private void onJoinGuild() {
        ModLogger.debug("Join guild clicked");
        // Close the container first, then open the browser
        // Browser will request data from server and display when it arrives
        client.closeContainer(true);
        GuildBrowserForm.show(client);
    }

    @Override
    public boolean shouldOpenInventory() {
        // Artisan UI doesn't need player inventory open
        return false;
    }
}