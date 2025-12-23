/*
 * CreateGuildDialog - Standalone dialog for creating a new guild
 * Follows the ContinueForm pattern like SettlementProtectionDialog
 * Per GUILD_UI_PATHS.md: Name, Description, Privacy, Crest Designer, Cost display
 */
package medievalsim.guilds.ui;

import medievalsim.config.ModConfig;
import medievalsim.guilds.GuildSymbolDesign;
import medievalsim.packets.PacketCreateGuild;
import medievalsim.util.ModLogger;
import necesse.engine.GlobalData;
import necesse.engine.localization.Localization;
import necesse.engine.network.Packet;
import necesse.engine.network.client.Client;
import necesse.engine.window.GameWindow;
import necesse.engine.window.WindowManager;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.GameColor;
import necesse.gfx.forms.ContinueComponentManager;
import necesse.gfx.forms.FormManager;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.forms.presets.ContinueForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;
import medievalsim.guilds.crest.GuildCrestRenderer;

import java.awt.Color;

/**
 * Dialog for creating a new guild.
 * Opens as a standalone ContinueForm on top of the game,
 * following the pattern used by SettlementProtectionDialog.
 * 
 * Per docs wireframe includes: Name, Description, Privacy toggle, 
 * Crest Designer button, Cost display, Create/Cancel buttons.
 */
public class CreateGuildDialog extends ContinueForm {

    private static final int DIALOG_WIDTH = 350;
    private static final int DIALOG_HEIGHT = 340;
    
    private final Client client;
    
    // Form inputs
    private FormTextInput guildNameInput;
    private FormTextInput guildDescInput;
    private FormLabel publicLabel;
    private FormLabel crestPreviewLabel;
    private boolean isPublicGuild = true;
    private boolean hiddenOnPause = false;

    // Inline preview offsets (relative to dialog origin)
    private int thisInlinePreviewX = -1;
    private int thisInlinePreviewY = -1;
    
    // Symbol design (default until player customizes)
    private GuildSymbolDesign symbolDesign = new GuildSymbolDesign();

    public CreateGuildDialog(Client client) {
        super("createguild", DIALOG_WIDTH, DIALOG_HEIGHT);
        this.client = client;
        
        buildUI();
    }
    
    private void buildUI() {
        // Title
        addComponent(new FormLabel(
            Localization.translate("ui", "guildartisan.createguild.title"),
            new FontOptions(18),
            FormLabel.ALIGN_MID,
            DIALOG_WIDTH / 2, 12));
        
        int y = 40;
        
        // Name input
        addComponent(new FormLabel(
            Localization.translate("ui", "guildname"),
            new FontOptions(12),
            FormLabel.ALIGN_LEFT,
            15, y));
        y += 18;
        
        guildNameInput = addComponent(new FormTextInput(
            15, y, FormInputSize.SIZE_24, DIALOG_WIDTH - 30, 32));
        y += 36;
        
        // Description input
        addComponent(new FormLabel(
            Localization.translate("ui", "guilddesc"),
            new FontOptions(12),
            FormLabel.ALIGN_LEFT,
            15, y));
        y += 18;
        
        guildDescInput = addComponent(new FormTextInput(
            15, y, FormInputSize.SIZE_24, DIALOG_WIDTH - 30, 128));
        y += 36;
        
        // Public/Private toggle - simple text, no fancy colors
        addComponent(new FormLabel(
            Localization.translate("ui", "guildartisan.visibility"),
            new FontOptions(12),
            FormLabel.ALIGN_LEFT,
            15, y));
        
        publicLabel = addComponent(new FormLabel(
            Localization.translate("ui", "publicguild"),
            new FontOptions(12),
            FormLabel.ALIGN_LEFT,
            100, y));
        
        FormTextButton toggleBtn = addComponent(new FormTextButton(
            Localization.translate("ui", "guildartisan.toggle"),
            DIALOG_WIDTH - 85, y - 5, 70, FormInputSize.SIZE_24, ButtonColor.BASE));
        toggleBtn.onClicked(e -> togglePublicPrivate());
        y += 32;
        
        // Crest Designer button
        addComponent(new FormLabel(
            Localization.translate("ui", "guildcrest"),
            new FontOptions(12),
            FormLabel.ALIGN_LEFT,
            15, y));
        
        // Small placeholder label; we draw a proper preview on the right to avoid overlap
        // Crest preview placeholder is empty text; preview is drawn inline beside the edit button
        crestPreviewLabel = addComponent(new FormLabel(
            "",
            new FontOptions(11),
            FormLabel.ALIGN_LEFT,
            DIALOG_WIDTH - 120, y));
        
        // Shortened button text to fit and add tooltip
        FormTextButton crestBtn = addComponent(new FormTextButton(
            Localization.translate("ui", "guildartisan.editcrest"),
            Localization.translate("ui", "crestdesigner.open"),
            DIALOG_WIDTH - 210, y - 5, 100, FormInputSize.SIZE_24, ButtonColor.BASE));
        crestBtn.onClicked(e -> openCrestDesigner());

        // Store an inline preview offset (drawn to the right of the edit button)
        thisInlinePreviewX = DIALOG_WIDTH - 44; // relative inside dialog
        // Lower the inline preview slightly to avoid overlapping nearby text
        thisInlinePreviewY = y - 6;

        y += 35;
        
        // Cost display - simple text
        long guildCost = ModConfig.Guilds.guildCreationCost;
        String costText = String.format("%s: %,d %s", 
            Localization.translate("ui", "cost"),
            guildCost,
            Localization.translate("ui", "gold"));
        addComponent(new FormLabel(
            costText,
            new FontOptions(14),
            FormLabel.ALIGN_MID,
            DIALOG_WIDTH / 2, y));
        y += 25;
        
        // Info text - darker for readability
        addComponent(new FormLabel(
            Localization.translate("ui", "guildartisan.createinfo"),
            new FontOptions(10).color(Color.DARK_GRAY),
            FormLabel.ALIGN_MID,
            DIALOG_WIDTH / 2, y, DIALOG_WIDTH - 20));
        
        // Buttons
        int btnWidth = 100;
        int btnY = DIALOG_HEIGHT - 45;
        
        FormTextButton confirmBtn = addComponent(new FormTextButton(
            Localization.translate("ui", "confirm"),
            (DIALOG_WIDTH / 2) - btnWidth - 5, btnY, btnWidth, FormInputSize.SIZE_32, ButtonColor.BASE));
        confirmBtn.onClicked(e -> confirmCreateGuild());
        
        FormTextButton cancelBtn = addComponent(new FormTextButton(
            Localization.translate("ui", "cancel"),
            (DIALOG_WIDTH / 2) + 5, btnY, btnWidth, FormInputSize.SIZE_32, ButtonColor.RED));
        cancelBtn.onClicked(e -> closeDialog());
    }
    
    private void togglePublicPrivate() {
        isPublicGuild = !isPublicGuild;
        if (isPublicGuild) {
            publicLabel.setText(Localization.translate("ui", "publicguild"));
        } else {
            publicLabel.setText(Localization.translate("ui", "privateguild"));
        }
    }
    
    private void openCrestDesigner() {
        // Open symbol designer in preview mode - returns design via callback
        CrestDesignerForm.showPreview(client, symbolDesign, this::setSymbolDesign);
    }
    
    /**
     * Called when crest design is updated (if we add inline designer later).
     */
    public void setSymbolDesign(GuildSymbolDesign design) {
        this.symbolDesign = design;
        crestPreviewLabel.setText(Localization.translate("ui", "crestcustomized"));
    }
    
    private void confirmCreateGuild() {
        String guildName = guildNameInput.getText().trim();
        String guildDesc = guildDescInput.getText().trim();
        
        if (guildName.length() < 3) {
            client.chat.addMessage(GameColor.RED.getColorCode() + "Guild name must be at least 3 characters.");
            return;
        }
        if (guildName.length() > 32) {
            client.chat.addMessage(GameColor.RED.getColorCode() + "Guild name must be 32 characters or less.");
            return;
        }
        
        // Note: Server validates gold requirement and will send error message if insufficient
        // We show the cost in the UI but don't block client-side
        
        // Send create guild packet with symbol design
        client.network.sendPacket((Packet)new PacketCreateGuild(guildName, guildDesc, isPublicGuild, symbolDesign));
        ModLogger.info("Sent create guild request: %s (public=%b)", guildName, isPublicGuild);
        
        // Close the dialog
        closeDialog();
    }
    
    @Override
    protected void init() {
        super.init();
        centerDialog();
    }

    @Override
    public void draw(necesse.engine.gameLoop.tickManager.TickManager tickManager, PlayerMob perspective, java.awt.Rectangle renderBox) {
        // Hide while paused but restore when unpaused
        if (client != null && client.isPaused()) {
            if (!this.isHidden()) {
                this.setHidden(true);
                this.hiddenOnPause = true;
            }
            return;
        } else if (this.hiddenOnPause) {
            this.setHidden(false);
            this.hiddenOnPause = false;
        }

        super.draw(tickManager, perspective, renderBox);

        // Draw inline crest preview (placed next to Edit button)
        if (crestPreviewLabel != null && GuildCrestRenderer.areTexturesLoaded() && thisInlinePreviewX > -1) {
            int size = 36;
            int px = this.getX() + thisInlinePreviewX;
            int py = this.getY() + thisInlinePreviewY;
            GuildCrestRenderer.drawCrest(symbolDesign, px, py, size);
        }
    }

    private void centerDialog() {
        // Center the dialog on screen
        GameWindow window = WindowManager.getWindow();
        if (window != null) {
            int centerX = window.getHudWidth() / 2 - this.getWidth() / 2;
            int centerY = window.getHudHeight() / 2 - this.getHeight() / 2;
            this.setPosMiddle(centerX + this.getWidth() / 2, centerY + this.getHeight() / 2);
        }
    }

    /**
     * Opens this dialog using the proper FormManager pattern.
     */
    public void openDialog() {
        FormManager formManager = GlobalData.getCurrentState().getFormManager();
        if (formManager instanceof ContinueComponentManager) {
            ((ContinueComponentManager) formManager).addContinueForm("createguild", this);
            ModLogger.debug("Create guild dialog opened");
        } else {
            ModLogger.warn("FormManager is not a ContinueComponentManager");
        }
    }

    private void closeDialog() {
        this.applyContinue();
    }
    
    /**
     * Static helper to show the create guild dialog.
     * Call this after closing the guild artisan container.
     */
    public static void show(Client client) {
        CreateGuildDialog dialog = new CreateGuildDialog(client);
        dialog.openDialog();
    }
}
