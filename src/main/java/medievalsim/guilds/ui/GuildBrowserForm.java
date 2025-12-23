/*
 * GuildBrowserForm - Browse public guilds and pending invitations
 * Part of Medieval Sim Mod guild management system.
 */
package medievalsim.guilds.ui;

import medievalsim.guilds.GuildSymbolDesign;
import medievalsim.packets.PacketJoinPublicGuild;
import medievalsim.packets.PacketListGuilds;
import medievalsim.packets.PacketRespondInvite;
import medievalsim.util.ModLogger;
import necesse.engine.GlobalData;
import necesse.engine.localization.Localization;
import necesse.engine.network.Packet;
import necesse.engine.network.client.Client;
import necesse.gfx.GameColor;
import necesse.gfx.forms.ContinueComponentManager;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.FormManager;
import necesse.gfx.forms.components.*;
import necesse.gfx.forms.presets.ContinueForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

import java.awt.Color;

/**
 * Guild Browser UI for viewing available guilds and pending invitations.
 * Accessible to non-guild members via Guild Artisan.
 */
public class GuildBrowserForm extends ContinueForm {

    private static final int WIDTH = 450;
    private static final int HEIGHT = 400;
    private static final int TAB_HEIGHT = 30;
    private static final int CONTENT_Y = 60;
    
    // Static instance for updating from packet response
    private static GuildBrowserForm activeInstance;
    
    private final Client client;
    private boolean hiddenOnPause = false;
    
    // Public guild data (mutable for updates)
    private int[] guildIDs = new int[0];
    private String[] guildNames = new String[0];
    private int[] memberCounts = new int[0];
    private long[] treasuries = new long[0];
    private int[] crestShapes = new int[0];
    private int[] crestColors = new int[0];
    private int[] crestEmblems = new int[0];
    
    // Pending invitations
    private int[] inviteGuildIDs = new int[0];
    private String[] inviteGuildNames = new String[0];
    
    // Tab forms
    private Form guildsTab;
    private Form invitesTab;
    private FormLabel loadingLabel;
    private FormTextButton guildsTabBtn;
    private FormTextButton invitesTabBtn;
    
    private int activeTab = 0;
    private boolean dataLoaded = false;
    
    /**
     * Private constructor - use show() to open the browser.
     */
    private GuildBrowserForm(Client client) {
        super("guild_browser", WIDTH, HEIGHT);
        this.client = client;
        setupUI();
    }
    
    /**
     * Opens the guild browser and requests data from server.
     * This is the main entry point - call this from button handlers.
     */
    public static void show(Client client) {
        GuildBrowserForm form = new GuildBrowserForm(client);
        activeInstance = form;
        
        FormManager formManager = GlobalData.getCurrentState().getFormManager();
        if (formManager instanceof ContinueComponentManager) {
            ((ContinueComponentManager) formManager).addContinueForm("guildbrowser", form);
            ModLogger.info("GuildBrowserForm opened, requesting guild list...");
            
            // Request guild data from server
            client.network.sendPacket(new PacketListGuilds());
        } else {
            ModLogger.warn("FormManager is not a ContinueComponentManager");
            client.chat.addMessage(GameColor.RED.getColorCode() + "Could not open guild browser");
        }
    }
    
    /**
     * Called from packet handler to update the browser with guild data.
     */
    public static void updateWithData(
            int[] guildIDs,
            String[] guildNames,
            int[] memberCounts,
            long[] treasuries,
            int[] crestShapes,
            int[] crestColors,
            int[] crestEmblems,
            int[] inviteGuildIDs,
            String[] inviteGuildNames) {
        
        if (activeInstance != null) {
            activeInstance.receiveData(
                guildIDs, guildNames, memberCounts, treasuries,
                crestShapes, crestColors, crestEmblems,
                inviteGuildIDs, inviteGuildNames
            );
        } else {
            ModLogger.warn("GuildBrowserForm.updateWithData called but no active instance");
        }
    }
    
    private void receiveData(
            int[] guildIDs,
            String[] guildNames,
            int[] memberCounts,
            long[] treasuries,
            int[] crestShapes,
            int[] crestColors,
            int[] crestEmblems,
            int[] inviteGuildIDs,
            String[] inviteGuildNames) {
        
        this.guildIDs = guildIDs;
        this.guildNames = guildNames;
        this.memberCounts = memberCounts;
        this.treasuries = treasuries;
        this.crestShapes = crestShapes;
        this.crestColors = crestColors;
        this.crestEmblems = crestEmblems;
        this.inviteGuildIDs = inviteGuildIDs;
        this.inviteGuildNames = inviteGuildNames;
        this.dataLoaded = true;
        
        // Hide loading by clearing text
        if (loadingLabel != null) {
            loadingLabel.setText("");
        }
        
        // Rebuild tabs with actual data
        rebuildTabs();
        
        ModLogger.info("GuildBrowserForm received %d guilds, %d invites", guildIDs.length, inviteGuildIDs.length);
    }
    
    private void setupUI() {
        // Title
        addComponent(new FormLabel(
            Localization.translate("ui", "guildbrowser.title"),
            new FontOptions(20),
            FormLabel.ALIGN_MID,
            WIDTH / 2, 10
        ));
        
        // Loading label (shown until data arrives)
        loadingLabel = addComponent(new FormLabel(
            Localization.translate("ui", "loading"),
            new FontOptions(14).color(Color.GRAY),
            FormLabel.ALIGN_MID,
            WIDTH / 2, HEIGHT / 2 - 20
        ));
        
        // Tab buttons (will be updated when data arrives)
        int tabWidth = WIDTH / 2;
        guildsTabBtn = addComponent(new FormTextButton(
            Localization.translate("ui", "guildbrowser.tab.guilds"),
            0, TAB_HEIGHT, tabWidth, FormInputSize.SIZE_24, ButtonColor.BASE
        ));
        guildsTabBtn.onClicked(e -> switchTab(0));
        
        invitesTabBtn = addComponent(new FormTextButton(
            Localization.translate("ui", "guildbrowser.tab.invites"),
            tabWidth, TAB_HEIGHT, tabWidth, FormInputSize.SIZE_24, ButtonColor.BASE
        ));
        invitesTabBtn.onClicked(e -> switchTab(1));
        
        // Close button
        FormTextButton closeBtn = addComponent(new FormTextButton(
            Localization.translate("ui", "close"),
            (WIDTH - 150) / 2, HEIGHT - 40, 150, FormInputSize.SIZE_32, ButtonColor.BASE
        ));
        closeBtn.onClicked(e -> {
            activeInstance = null;
            applyContinue();
        });
    }
    
    private void rebuildTabs() {
        // Update tab button labels
        guildsTabBtn.setText(Localization.translate("ui", "guildbrowser.tab.guilds") + " (" + guildIDs.length + ")");
        invitesTabBtn.setText(Localization.translate("ui", "guildbrowser.tab.invites") + " (" + inviteGuildIDs.length + ")");
        
        // Remove old tabs if they exist
        if (guildsTab != null) {
            removeComponent(guildsTab);
        }
        if (invitesTab != null) {
            removeComponent(invitesTab);
        }
        
        // Build new tabs with data
        setupGuildsTab();
        setupInvitesTab();
        
        // Start on invites tab if there are pending invites
        switchTab(inviteGuildIDs.length > 0 ? 1 : 0);
    }
    
    private void setupGuildsTab() {
        int contentHeight = HEIGHT - CONTENT_Y - 50;
        guildsTab = addComponent(new Form("guilds", WIDTH - 20, contentHeight));
        guildsTab.setPosition(10, CONTENT_Y);
        guildsTab.drawBase = false;
        
        int y = 5;
        int rowHeight = 50;
        
        if (guildIDs.length == 0) {
            guildsTab.addComponent(new FormLabel(
                Localization.translate("ui", "guildbrowser.noguilds"),
                new FontOptions(14).color(Color.GRAY),
                FormLabel.ALIGN_MID, (WIDTH - 20) / 2, y + 50
            ));
        } else {
            for (int i = 0; i < guildIDs.length && y < contentHeight - rowHeight; i++) {
                final int guildID = guildIDs[i];
                final String name = guildNames[i];
                
                // Crest color indicator
                Color crestColor = new Color(crestColors[i]);
                String shapeSymbol = switch(crestShapes[i]) {
                    case 0 -> "◆"; // Shield
                    case 1 -> "●"; // Circle
                    case 2 -> "▼"; // Banner
                    case 3 -> "◇"; // Diamond
                    default -> "■"; // Square
                };
                
                guildsTab.addComponent(new FormLabel(
                    shapeSymbol,
                    new FontOptions(18).color(crestColor),
                    FormLabel.ALIGN_LEFT, 10, y + 5
                ));
                
                // Guild name
                guildsTab.addComponent(new FormLabel(
                    name,
                    new FontOptions(14),
                    FormLabel.ALIGN_LEFT, 40, y
                ));
                
                // Member count and treasury
                String info = memberCounts[i] + " " + Localization.translate("ui", "guildbrowser.members") + 
                              " | " + formatGold(treasuries[i]) + " gold";
                guildsTab.addComponent(new FormLabel(
                    info,
                    new FontOptions(11).color(Color.LIGHT_GRAY),
                    FormLabel.ALIGN_LEFT, 40, y + 20
                ));
                
                // Join button (public guilds can be joined directly)
                FormTextButton joinBtn = guildsTab.addComponent(new FormTextButton(
                    Localization.translate("ui", "guildbrowser.join"),
                    WIDTH - 110, y + 5, 80, FormInputSize.SIZE_24, ButtonColor.BASE
                ));
                joinBtn.onClicked(e -> joinGuild(guildID, name));
                
                y += rowHeight;
            }
            
            if (guildIDs.length > (contentHeight - 5) / rowHeight) {
                guildsTab.addComponent(new FormLabel(
                    "... " + Localization.translate("ui", "guildbrowser.andmore"),
                    new FontOptions(11).color(Color.GRAY),
                    FormLabel.ALIGN_LEFT, 40, y
                ));
            }
        }
    }
    
    private void setupInvitesTab() {
        int contentHeight = HEIGHT - CONTENT_Y - 50;
        invitesTab = addComponent(new Form("invites", WIDTH - 20, contentHeight));
        invitesTab.setPosition(10, CONTENT_Y);
        invitesTab.drawBase = false;
        invitesTab.setHidden(true);
        
        int y = 5;
        int rowHeight = 45;
        
        if (inviteGuildIDs.length == 0) {
            invitesTab.addComponent(new FormLabel(
                Localization.translate("ui", "guildbrowser.noinvites"),
                new FontOptions(14).color(Color.GRAY),
                FormLabel.ALIGN_MID, (WIDTH - 20) / 2, y + 50
            ));
        } else {
            invitesTab.addComponent(new FormLabel(
                Localization.translate("ui", "guildbrowser.pendinginvites"),
                new FontOptions(14),
                FormLabel.ALIGN_LEFT, 10, y
            ));
            y += 25;
            
            for (int i = 0; i < inviteGuildIDs.length && y < contentHeight - rowHeight; i++) {
                final int guildID = inviteGuildIDs[i];
                final String name = inviteGuildNames[i];
                
                // Guild name
                invitesTab.addComponent(new FormLabel(
                    "⚔ " + name,
                    new FontOptions(14),
                    FormLabel.ALIGN_LEFT, 20, y
                ));
                
                // Accept button
                FormTextButton acceptBtn = invitesTab.addComponent(new FormTextButton(
                    Localization.translate("ui", "acceptbutton"),
                    WIDTH - 180, y - 3, 80, FormInputSize.SIZE_24, ButtonColor.BASE
                ));
                acceptBtn.onClicked(e -> acceptInvite(guildID, name));
                
                // Decline button
                FormTextButton declineBtn = invitesTab.addComponent(new FormTextButton(
                    Localization.translate("ui", "declinebutton"),
                    WIDTH - 95, y - 3, 75, FormInputSize.SIZE_24, ButtonColor.RED
                ));
                declineBtn.onClicked(e -> declineInvite(guildID, name));
                
                y += rowHeight;
            }
        }
    }
    
    private void switchTab(int tabIndex) {
        activeTab = tabIndex;
        // Only switch if tabs exist (data has loaded)
        if (guildsTab != null) {
            guildsTab.setHidden(tabIndex != 0);
        }
        if (invitesTab != null) {
            invitesTab.setHidden(tabIndex != 1);
        }
    }
    
    // Actions
    private void joinGuild(int guildID, String guildName) {
        client.network.sendPacket((Packet)new PacketJoinPublicGuild(guildID));
        client.chat.addMessage(GameColor.GRAY.getColorCode() + "Requesting to join " + guildName + "...");
        applyContinue();
    }
    
    private void acceptInvite(int guildID, String guildName) {
        client.network.sendPacket((Packet)new PacketRespondInvite(guildID, true));
        client.chat.addMessage(GameColor.GREEN.getColorCode() + "Accepted invitation to " + guildName);
        applyContinue();
    }
    
    private void declineInvite(int guildID, String guildName) {
        client.network.sendPacket((Packet)new PacketRespondInvite(guildID, false));
        client.chat.addMessage(GameColor.RED.getColorCode() + "Declined invitation to " + guildName);
        applyContinue();
    }
    
    // Utility
    private String formatGold(long amount) {
        if (amount >= 1000000) {
            return String.format("%.1fM", amount / 1000000.0);
        } else if (amount >= 1000) {
            return String.format("%.1fK", amount / 1000.0);
        }
        return String.valueOf(amount);
    }
    
    @Override
    protected void init() {
        super.init();
        // Center the dialog on screen properly
        centerDialog();
    }

    @Override
    public void draw(necesse.engine.gameLoop.tickManager.TickManager tickManager, necesse.entity.mobs.PlayerMob perspective, java.awt.Rectangle renderBox) {
        // Hide while paused and restore when unpaused
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
    }
    
    private void centerDialog() {
        necesse.engine.window.GameWindow window = necesse.engine.window.WindowManager.getWindow();
        if (window != null) {
            int centerX = window.getHudWidth() / 2 - this.getWidth() / 2;
            int centerY = window.getHudHeight() / 2 - this.getHeight() / 2;
            this.setPosMiddle(centerX + this.getWidth() / 2, centerY + this.getHeight() / 2);
        }
    }
    
    /**
     * Called from packet handler - just updates the data if form is already open.
     * This maintains backwards compatibility with existing packet handler.
     */
    public static void showBrowser(
            Client client,
            int[] guildIDs,
            String[] guildNames,
            int[] memberCounts,
            long[] treasuries,
            int[] crestShapes,
            int[] crestColors,
            int[] crestEmblems,
            int[] inviteGuildIDs,
            String[] inviteGuildNames) {
        
        ModLogger.info("GuildBrowserForm.showBrowser: Received data for %d guilds", guildIDs.length);
        
        // Update the active instance if it exists
        updateWithData(
            guildIDs, guildNames, memberCounts, treasuries,
            crestShapes, crestColors, crestEmblems,
            inviteGuildIDs, inviteGuildNames
        );
    }
}
