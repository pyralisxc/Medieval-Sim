/*
 * GuildInfoPanelForm - Full guild information panel with tabs
 * Part of Medieval Sim Mod guild management system.
 */
package medievalsim.guilds.ui;

import medievalsim.guilds.GuildSymbolDesign;
import medievalsim.guilds.GuildRank;
import medievalsim.guilds.client.ClientGuildManager;
import medievalsim.guilds.client.GuildEventListener;
import medievalsim.guilds.client.ClientGuildManager.GuildMemberUpdateType;
import medievalsim.packets.*;
import medievalsim.util.ModLogger;
import necesse.engine.localization.Localization;
import necesse.engine.network.Packet;
import necesse.engine.network.client.Client;
import necesse.gfx.GameColor;
import necesse.gfx.drawOptions.DrawOptions;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.*;
import necesse.gfx.forms.presets.ContinueForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Full guild information panel with tabs for:
 * - Overview: Guild name, crest, description, treasury
 * - Members: List of members with ranks, promote/demote/kick buttons
 * - Banners: List of guild teleport banners with teleport/rename/unclaim actions
 * - Settings: Leader-only settings (crest, tax rates, disband)
 * 
 * Implements GuildEventListener for auto-refresh on guild events.
 */
public class GuildInfoPanelForm extends ContinueForm implements GuildEventListener {

    private static final int WIDTH = 500;
    private static final int HEIGHT = 420;
    private static final int TAB_HEIGHT = 30;
    private static final int CONTENT_Y = 50;
    
    // Guild data
    private final Client client;
    private final int guildID;
    private final String guildName;
    private final String description;
    private final boolean isPublic;
    private final long treasury;
    private final GuildSymbolDesign symbol;
    private final List<MemberData> members;
    private final String[] auditEntries;
    private final long[] auditTimestamps;
    
    // Tab forms
    private Form overviewTab;
    private Form membersTab;
    private Form bannersTab;
    private Form settingsTab;
    
    // Banner list (loaded dynamically)
    private List<PacketGuildBannersResponse.BannerInfo> cachedBanners = new ArrayList<>();
    
    // Active tab tracking
    private int activeTab = 0;
    
    // Player's rank for permission checks
    private GuildRank myRank;
    
    /**
     * Member data structure for display.
     */
    public static class MemberData {
        public final long auth;
        public final String name;
        public final GuildRank rank;
        public final boolean online;
        
        public MemberData(long auth, String name, GuildRank rank, boolean online) {
            this.auth = auth;
            this.name = name;
            this.rank = rank;
            this.online = online;
        }
    }
    
    // Store the player's auth (passed from server)
    private final long myAuth;
    
    private GuildInfoPanelForm(
            Client client,
            int guildID,
            String guildName,
            String description,
            boolean isPublic,
            long treasury,
            GuildSymbolDesign symbol,
            List<MemberData> members,
            String[] auditEntries,
            long[] auditTimestamps,
            long requesterAuth) {
        super("guild_info_panel", WIDTH, HEIGHT);
        
        this.client = client;
        this.guildID = guildID;
        this.guildName = guildName;
        this.description = description;
        this.isPublic = isPublic;
        this.treasury = treasury;
        this.symbol = symbol;
        this.members = members;
        this.auditEntries = auditEntries;
        this.auditTimestamps = auditTimestamps;
        this.myAuth = requesterAuth;
        
        // Find my rank
        myRank = GuildRank.RECRUIT; // Default
        for (MemberData m : members) {
            if (m.auth == myAuth) {
                myRank = m.rank;
                break;
            }
        }
        
        setupUI();
        
        // Subscribe to guild events for auto-refresh
        ClientGuildManager.get().addListener(this);
    }
    
    // ==================== GuildEventListener Implementation ====================
    
    @Override
    public void onGuildLeft(int guildID, String guildName, boolean wasKicked, String reason) {
        // Close this panel if we left/were kicked from the guild we're viewing
        if (guildID == this.guildID) {
            closeWithCleanup();
        }
    }
    
    @Override
    public void onRankChanged(int guildID, GuildRank oldRank, GuildRank newRank) {
        if (guildID == this.guildID) {
            // Our rank changed - request fresh data
            requestRefresh();
        }
    }
    
    @Override
    public void onMemberUpdate(int guildID, long memberAuth, GuildMemberUpdateType updateType) {
        if (guildID == this.guildID) {
            // A member's state changed - request fresh data
            requestRefresh();
        }
    }
    
    @Override
    public void onGuildDataChanged(int guildID) {
        if (guildID == this.guildID) {
            requestRefresh();
        }
    }
    
    private void requestRefresh() {
        // Request updated guild info from server
        client.network.sendPacket((Packet)new PacketRequestGuildInfo(guildID));
        client.chat.addMessage(GameColor.GRAY.getColorCode() + "Refreshing guild info...");
    }
    
    // ==================== Cleanup ====================
    
    /**
     * Called when the form is closed. Unsubscribes from events.
     */
    private void cleanup() {
        ClientGuildManager.get().removeListener(this);
    }
    
    /**
     * Override close behavior to clean up listener subscription.
     */
    private void closeWithCleanup() {
        cleanup();
        applyContinue();
    }
    
    private void setupUI() {
        // Title bar with guild name
        addComponent(new FormLabel(
            guildName,
            new FontOptions(20),
            FormLabel.ALIGN_MID,
            WIDTH / 2, 10
        ));
        
        // Tab buttons (4 tabs now: Overview, Members, Banners, Settings)
        int tabWidth = WIDTH / 4;
        FormTextButton overviewBtn = addComponent(new FormTextButton(
            Localization.translate("ui", "guildinfo.tab.overview"),
            0, TAB_HEIGHT, tabWidth, FormInputSize.SIZE_24, ButtonColor.BASE
        ));
        overviewBtn.onClicked(e -> switchTab(0));
        
        FormTextButton membersBtn = addComponent(new FormTextButton(
            Localization.translate("ui", "guildinfo.tab.members"),
            tabWidth, TAB_HEIGHT, tabWidth, FormInputSize.SIZE_24, ButtonColor.BASE
        ));
        membersBtn.onClicked(e -> switchTab(1));
        
        FormTextButton bannersBtn = addComponent(new FormTextButton(
            Localization.translate("ui", "guildinfo.tab.banners"),
            tabWidth * 2, TAB_HEIGHT, tabWidth, FormInputSize.SIZE_24, ButtonColor.BASE
        ));
        bannersBtn.onClicked(e -> switchTab(2));
        
        // Settings tab - only visible to leaders
        if (myRank == GuildRank.LEADER) {
            FormTextButton settingsBtn = addComponent(new FormTextButton(
                Localization.translate("ui", "guildinfo.tab.settings"),
                tabWidth * 3, TAB_HEIGHT, tabWidth, FormInputSize.SIZE_24, ButtonColor.BASE
            ));
            settingsBtn.onClicked(e -> switchTab(3));
        }
        
        // Create tab content forms
        setupOverviewTab();
        setupMembersTab();
        setupBannersTab();
        if (myRank == GuildRank.LEADER) {
            setupSettingsTab();
        }
        
        // Close button at bottom
        FormTextButton closeBtn = addComponent(new FormTextButton(
            Localization.translate("ui", "close"),
            (WIDTH - 150) / 2, HEIGHT - 40, 150, FormInputSize.SIZE_32, ButtonColor.BASE
        ));
        closeBtn.onClicked(e -> closeWithCleanup());
        
        // Start with overview tab visible
        switchTab(0);
        
        // Request banner data for the Banners tab
        client.network.sendPacket(new PacketRequestGuildBanners(guildID));
    }
    
    private void setupOverviewTab() {
        int contentHeight = HEIGHT - CONTENT_Y - 50;
        overviewTab = addComponent(new Form("overview", WIDTH - 20, contentHeight));
        overviewTab.setPosition(10, CONTENT_Y);
        overviewTab.drawBase = false;
        
        int y = 10;
        
        // Crest placeholder (could render actual crest here)
        FormLabel crestLabel = overviewTab.addComponent(new FormLabel(
            "[" + GuildSymbolDesign.BACKGROUND_SHAPES[symbol.getShape()] + " Symbol]",
            new FontOptions(14).color(new Color(symbol.getPrimaryColor())),
            FormLabel.ALIGN_LEFT,
            10, y
        ));
        
        // Emblem info
        y += 25;
        overviewTab.addComponent(new FormLabel(
            "Emblem: " + GuildSymbolDesign.EMBLEM_NAMES[symbol.getEmblem()],
            new FontOptions(12).color(Color.LIGHT_GRAY),
            FormLabel.ALIGN_LEFT, 10, y
        ));
        
        y += 30;
        
        // Description
        if (description != null && !description.isEmpty()) {
            overviewTab.addComponent(new FormLabel(
                "\"" + description + "\"",
                new FontOptions(12).color(Color.LIGHT_GRAY),
                FormLabel.ALIGN_LEFT, 10, y, WIDTH - 40
            ));
            y += 40;
        }
        
        // Guild status
        String status = isPublic ? 
            Localization.translate("ui", "guildinfo.public") : 
            Localization.translate("ui", "guildinfo.private");
        overviewTab.addComponent(new FormLabel(
            Localization.translate("ui", "guildinfo.status") + ": " + status,
            new FontOptions(14),
            FormLabel.ALIGN_LEFT, 10, y
        ));
        y += 25;
        
        // Member count
        overviewTab.addComponent(new FormLabel(
            Localization.translate("ui", "guildinfo.members") + ": " + members.size(),
            new FontOptions(14),
            FormLabel.ALIGN_LEFT, 10, y
        ));
        y += 25;
        
        // Treasury
        overviewTab.addComponent(new FormLabel(
            Localization.translate("ui", "guildinfo.treasury") + ": " + formatGold(treasury),
            new FontOptions(14).color(Color.YELLOW),
            FormLabel.ALIGN_LEFT, 10, y
        ));
        y += 35;
        
        // Your rank
        overviewTab.addComponent(new FormLabel(
            Localization.translate("ui", "guildinfo.yourrank") + ": " + myRank.getDisplayName(),
            new FontOptions(14).color(getRankColor(myRank)),
            FormLabel.ALIGN_LEFT, 10, y
        ));
        y += 40;
        
        // Quick action buttons
        int btnWidth = 120;
        int btnSpacing = 10;
        int btnStartX = 10;
        
        // Research button - opens research UI
        FormTextButton researchBtn = overviewTab.addComponent(new FormTextButton(
            Localization.translate("ui", "guildinfo.research"),
            btnStartX, y, btnWidth, FormInputSize.SIZE_24, ButtonColor.BASE
        ));
        researchBtn.onClicked(e -> openResearch());
        
        // Bank button - opens guild bank
        FormTextButton bankBtn = overviewTab.addComponent(new FormTextButton(
            Localization.translate("ui", "guildinfo.bank"),
            btnStartX + btnWidth + btnSpacing, y, btnWidth, FormInputSize.SIZE_24, ButtonColor.BASE
        ));
        bankBtn.onClicked(e -> openBank());
    }
    
    /**
     * Open the guild research UI.
     */
    private void openResearch() {
        // Send packet to server to open research container
        client.network.sendPacket(new PacketOpenGuildResearch());
    }
    
    /**
     * Open the guild bank UI.
     */
    private void openBank() {
        // Send request to open the guild bank (tab 0)
        client.network.sendPacket(new medievalsim.packets.PacketOpenGuildBank(0));
    }
    
    private void setupMembersTab() {
        int contentHeight = HEIGHT - CONTENT_Y - 50;
        membersTab = addComponent(new Form("members", WIDTH - 20, contentHeight));
        membersTab.setPosition(10, CONTENT_Y);
        membersTab.drawBase = false;
        membersTab.setHidden(true);
        
        int y = 5;
        int rowHeight = 30;
        
        // Header
        membersTab.addComponent(new FormLabel(
            Localization.translate("ui", "guildinfo.memberlist"),
            new FontOptions(14),
            FormLabel.ALIGN_LEFT, 10, y
        ));
        y += 25;
        
        // Sort members by rank (Leader first)
        members.sort((a, b) -> b.rank.level - a.rank.level);
        
        // Member rows
        for (MemberData member : members) {
            // Name with online indicator
            Color nameColor = member.online ? Color.GREEN : Color.GRAY;
            String onlineIndicator = member.online ? "●" : "○";
            
            membersTab.addComponent(new FormLabel(
                onlineIndicator + " " + member.name,
                new FontOptions(12).color(nameColor),
                FormLabel.ALIGN_LEFT, 10, y
            ));
            
            // Rank
            membersTab.addComponent(new FormLabel(
                member.rank.getDisplayName(),
                new FontOptions(12).color(getRankColor(member.rank)),
                FormLabel.ALIGN_LEFT, 180, y
            ));
            
            // Action buttons (if we have permission)
            if (member.auth != myAuth) {
                int btnX = 280;
                
                // Can we promote? (Officers can promote recruits, Leaders can promote anyone below Leader)
                if (canPromote(member)) {
                    FormTextButton promoteBtn = membersTab.addComponent(new FormTextButton(
                        "▲", btnX, y - 5, 30, FormInputSize.SIZE_20, ButtonColor.BASE
                    ));
                    promoteBtn.onClicked(e -> promoteMember(member));
                    btnX += 35;
                }
                
                // Can we demote?
                if (canDemote(member)) {
                    FormTextButton demoteBtn = membersTab.addComponent(new FormTextButton(
                        "▼", btnX, y - 5, 30, FormInputSize.SIZE_20, ButtonColor.BASE
                    ));
                    demoteBtn.onClicked(e -> demoteMember(member));
                    btnX += 35;
                }
                
                // Can we kick?
                if (canKick(member)) {
                    FormTextButton kickBtn = membersTab.addComponent(new FormTextButton(
                        "X", btnX, y - 5, 30, FormInputSize.SIZE_20, ButtonColor.RED
                    ));
                    kickBtn.onClicked(e -> kickMember(member));
                }
            }
            
            y += rowHeight;
            
            // Limit displayed members
            if (y > contentHeight - 20) {
                membersTab.addComponent(new FormLabel(
                    "... and " + (members.size() - members.indexOf(member)) + " more",
                    new FontOptions(12).color(Color.GRAY),
                    FormLabel.ALIGN_LEFT, 10, y
                ));
                break;
            }
        }
    }
    
    private void setupBannersTab() {
        int contentHeight = HEIGHT - CONTENT_Y - 50;
        bannersTab = addComponent(new Form("banners", WIDTH - 20, contentHeight));
        bannersTab.setPosition(10, CONTENT_Y);
        bannersTab.drawBase = false;
        bannersTab.setHidden(true);
        
        int y = 5;
        
        // Header
        bannersTab.addComponent(new FormLabel(
            Localization.translate("ui", "guildinfo.banners.title"),
            new FontOptions(14),
            FormLabel.ALIGN_LEFT, 10, y
        ));
        y += 25;
        
        // Check if we have cached banner data
        List<PacketGuildBannersResponse.BannerInfo> banners = ClientGuildManager.get().getCachedBanners(guildID);
        if (banners == null || banners.isEmpty()) {
            bannersTab.addComponent(new FormLabel(
                Localization.translate("ui", "guildinfo.banners.none"),
                new FontOptions(12).color(Color.GRAY),
                FormLabel.ALIGN_LEFT, 10, y
            ));
            
            // Info text about getting banners
            y += 25;
            bannersTab.addComponent(new FormLabel(
                Localization.translate("ui", "guildinfo.banners.howtobuy"),
                new FontOptions(11).color(Color.LIGHT_GRAY),
                FormLabel.ALIGN_LEFT, 10, y, WIDTH - 40
            ));
        } else {
            // Show banner list
            for (PacketGuildBannersResponse.BannerInfo banner : banners) {
                if (y > contentHeight - 40) {
                    bannersTab.addComponent(new FormLabel(
                        "... and " + (banners.size() - banners.indexOf(banner)) + " more",
                        new FontOptions(12).color(Color.GRAY),
                        FormLabel.ALIGN_LEFT, 10, y
                    ));
                    break;
                }
                
                // Banner name and placer
                bannersTab.addComponent(new FormLabel(
                    banner.name + " - by " + banner.placedByName,
                    new FontOptions(12),
                    FormLabel.ALIGN_LEFT, 10, y
                ));
                
                int btnX = 280;
                
                // Teleport button (always available)
                FormTextButton teleportBtn = bannersTab.addComponent(new FormTextButton(
                    Localization.translate("ui", "guildinfo.banners.teleport"),
                    btnX, y - 5, 70, FormInputSize.SIZE_20, ButtonColor.BASE
                ));
                teleportBtn.onClicked(e -> onTeleportToBanner(banner));
                btnX += 75;
                
                // Rename button (if has permission)
                if (banner.canRename) {
                    FormTextButton renameBtn = bannersTab.addComponent(new FormTextButton(
                        Localization.translate("ui", "guildinfo.banners.rename"),
                        btnX, y - 5, 60, FormInputSize.SIZE_20, ButtonColor.BASE
                    ));
                    renameBtn.onClicked(e -> onRenameBanner(banner));
                    btnX += 65;
                }
                
                // Unclaim button (if has permission)
                if (banner.canUnclaim) {
                    FormTextButton unclaimBtn = bannersTab.addComponent(new FormTextButton(
                        Localization.translate("ui", "guildinfo.banners.unclaim"),
                        btnX, y - 5, 60, FormInputSize.SIZE_20, ButtonColor.RED
                    ));
                    unclaimBtn.onClicked(e -> onUnclaimBanner(banner));
                }
                
                y += 30;
            }
        }
        
        // Note about purchasing via ManageGuilds
        y = contentHeight - 30;
        bannersTab.addComponent(new FormLabel(
            Localization.translate("ui", "guildinfo.banners.purchasenote"),
            new FontOptions(10).color(Color.GRAY),
            FormLabel.ALIGN_LEFT, 10, y, WIDTH - 40
        ));
    }
    
    /**
     * Teleport to a guild banner.
     */
    private void onTeleportToBanner(PacketGuildBannersResponse.BannerInfo banner) {
        client.network.sendPacket(new PacketTeleportToGuildBanner(
            guildID, banner.tileX, banner.tileY
        ));
        closeWithCleanup();
    }
    
    /**
     * Rename a guild banner.
     */
    private void onRenameBanner(PacketGuildBannersResponse.BannerInfo banner) {
        // TODO: Show rename dialog
        // For now, just send a test rename
        client.chat.addMessage(GameColor.YELLOW.getColorCode() + "Rename dialog coming soon!");
    }
    
    /**
     * Unclaim a guild banner.
     */
    private void onUnclaimBanner(PacketGuildBannersResponse.BannerInfo banner) {
        // TODO: Add confirmation dialog
        client.network.sendPacket(new PacketUnclaimGuildBanner(
            guildID, banner.tileX, banner.tileY
        ));
        client.chat.addMessage(GameColor.YELLOW.getColorCode() + "Banner unclaim request sent");
    }
    
    private void setupSettingsTab() {
        int contentHeight = HEIGHT - CONTENT_Y - 50;
        settingsTab = addComponent(new Form("settings", WIDTH - 20, contentHeight));
        settingsTab.setPosition(10, CONTENT_Y);
        settingsTab.drawBase = false;
        settingsTab.setHidden(true);
        
        int y = 5;
        
        // Header
        settingsTab.addComponent(new FormLabel(
            Localization.translate("ui", "guildinfo.settings.title"),
            new FontOptions(14),
            FormLabel.ALIGN_LEFT, 10, y
        ));
        y += 30;
        
        int btnWidth = 150;
        int btnSpacing = 10;
        
        // Edit Crest button
        FormTextButton crestBtn = settingsTab.addComponent(new FormTextButton(
            Localization.translate("ui", "guildinfo.settings.editcrest"),
            10, y, btnWidth, FormInputSize.SIZE_24, ButtonColor.BASE
        ));
        crestBtn.onClicked(e -> openCrestEditor());
        
        y += 35;
        
        // Guild visibility toggle
        String visibilityText = isPublic ? 
            Localization.translate("ui", "guildinfo.settings.makeprivate") :
            Localization.translate("ui", "guildinfo.settings.makepublic");
        FormTextButton visibilityBtn = settingsTab.addComponent(new FormTextButton(
            visibilityText,
            10, y, btnWidth, FormInputSize.SIZE_24, ButtonColor.BASE
        ));
        visibilityBtn.onClicked(e -> toggleVisibility());
        
        y += 35;
        
        // Tax rate label and adjustment
        settingsTab.addComponent(new FormLabel(
            Localization.translate("ui", "guildinfo.settings.taxrate"),
            new FontOptions(12),
            FormLabel.ALIGN_LEFT, 10, y
        ));
        // TODO: Add tax rate slider/input
        
        y += 40;
        
        // Danger zone
        settingsTab.addComponent(new FormLabel(
            GameColor.RED.getColorCode() + Localization.translate("ui", "guildinfo.settings.dangerzone"),
            new FontOptions(12).color(Color.RED),
            FormLabel.ALIGN_LEFT, 10, y
        ));
        y += 25;
        
        // Disband guild button
        FormTextButton disbandBtn = settingsTab.addComponent(new FormTextButton(
            Localization.translate("ui", "guildinfo.settings.disband"),
            10, y, btnWidth, FormInputSize.SIZE_24, ButtonColor.RED
        ));
        disbandBtn.onClicked(e -> confirmDisbandGuild());
    }
    
    /**
     * Open the crest editor.
     */
    private void openCrestEditor() {
        // TODO: Open CrestDesignerForm
        client.chat.addMessage(GameColor.YELLOW.getColorCode() + "Crest editor coming soon!");
    }
    
    /**
     * Toggle guild visibility (public/private).
     */
    private void toggleVisibility() {
        // TODO: Send packet to toggle visibility
        client.chat.addMessage(GameColor.YELLOW.getColorCode() + "Visibility toggle coming soon!");
    }
    
    /**
     * Show confirmation for disbanding guild.
     */
    private void confirmDisbandGuild() {
        // TODO: Show confirmation dialog
        client.chat.addMessage(GameColor.RED.getColorCode() + "Disband confirmation coming soon!");
    }
    
    private void switchTab(int tabIndex) {
        activeTab = tabIndex;
        overviewTab.setHidden(tabIndex != 0);
        membersTab.setHidden(tabIndex != 1);
        if (bannersTab != null) bannersTab.setHidden(tabIndex != 2);
        if (settingsTab != null) settingsTab.setHidden(tabIndex != 3);
        
        // If switching to banners tab, refresh banner data
        if (tabIndex == 2) {
            refreshBannersTab();
        }
    }
    
    /**
     * Refresh the banners tab with latest cached data.
     */
    private void refreshBannersTab() {
        List<PacketGuildBannersResponse.BannerInfo> banners = ClientGuildManager.get().getCachedBanners(guildID);
        if (banners != null && !banners.equals(cachedBanners)) {
            cachedBanners = new ArrayList<>(banners);
            // Clear and rebuild the banners tab
            if (bannersTab != null) {
                bannersTab.clearComponents();
                rebuildBannersTabContent();
            }
        }
    }
    
    /**
     * Rebuild the banners tab content.
     */
    private void rebuildBannersTabContent() {
        int y = 5;
        
        // Header
        bannersTab.addComponent(new FormLabel(
            Localization.translate("ui", "guildinfo.banners.title"),
            new FontOptions(14),
            FormLabel.ALIGN_LEFT, 10, y
        ));
        y += 25;
        
        if (cachedBanners == null || cachedBanners.isEmpty()) {
            bannersTab.addComponent(new FormLabel(
                Localization.translate("ui", "guildinfo.banners.none"),
                new FontOptions(12).color(Color.GRAY),
                FormLabel.ALIGN_LEFT, 10, y
            ));
            
            // Info text about getting banners
            y += 25;
            bannersTab.addComponent(new FormLabel(
                Localization.translate("ui", "guildinfo.banners.howtobuy"),
                new FontOptions(11).color(Color.LIGHT_GRAY),
                FormLabel.ALIGN_LEFT, 10, y, WIDTH - 40
            ));
        } else {
            int contentHeight = HEIGHT - CONTENT_Y - 50;
            // Show banner list
            for (PacketGuildBannersResponse.BannerInfo banner : cachedBanners) {
                if (y > contentHeight - 40) {
                    bannersTab.addComponent(new FormLabel(
                        "... and " + (cachedBanners.size() - cachedBanners.indexOf(banner)) + " more",
                        new FontOptions(12).color(Color.GRAY),
                        FormLabel.ALIGN_LEFT, 10, y
                    ));
                    break;
                }
                
                // Banner name and placer
                bannersTab.addComponent(new FormLabel(
                    banner.name + " - by " + banner.placedByName,
                    new FontOptions(12),
                    FormLabel.ALIGN_LEFT, 10, y
                ));
                
                int btnX = 280;
                
                // Teleport button (always available)
                FormTextButton teleportBtn = bannersTab.addComponent(new FormTextButton(
                    Localization.translate("ui", "guildinfo.banners.teleport"),
                    btnX, y - 5, 70, FormInputSize.SIZE_20, ButtonColor.BASE
                ));
                teleportBtn.onClicked(e -> onTeleportToBanner(banner));
                btnX += 75;
                
                // Rename button (if has permission)
                if (banner.canRename) {
                    FormTextButton renameBtn = bannersTab.addComponent(new FormTextButton(
                        Localization.translate("ui", "guildinfo.banners.rename"),
                        btnX, y - 5, 60, FormInputSize.SIZE_20, ButtonColor.BASE
                    ));
                    renameBtn.onClicked(e -> onRenameBanner(banner));
                    btnX += 65;
                }
                
                // Unclaim button (if has permission)
                if (banner.canUnclaim) {
                    FormTextButton unclaimBtn = bannersTab.addComponent(new FormTextButton(
                        Localization.translate("ui", "guildinfo.banners.unclaim"),
                        btnX, y - 5, 60, FormInputSize.SIZE_20, ButtonColor.RED
                    ));
                    unclaimBtn.onClicked(e -> onUnclaimBanner(banner));
                }
                
                y += 30;
            }
        }
    }
    
    // Permission helpers
    private boolean canPromote(MemberData target) {
        if (myRank.level <= target.rank.level) return false;
        if (target.rank == GuildRank.LEADER) return false;
        if (myRank == GuildRank.LEADER) return target.rank.level < GuildRank.OFFICER.level;
        if (myRank == GuildRank.OFFICER) return target.rank == GuildRank.RECRUIT;
        return false;
    }
    
    private boolean canDemote(MemberData target) {
        if (myRank.level <= target.rank.level) return false;
        if (target.rank == GuildRank.RECRUIT) return false;
        if (myRank == GuildRank.LEADER) return target.rank != GuildRank.LEADER;
        return false;
    }
    
    private boolean canKick(MemberData target) {
        return myRank.level > target.rank.level && myRank.level >= GuildRank.OFFICER.level;
    }
    
    // Actions
    private void promoteMember(MemberData member) {
        client.network.sendPacket((Packet)new PacketPromoteMember(guildID, member.auth));
        client.chat.addMessage(GameColor.GREEN.getColorCode() + "Sent promotion request for " + member.name);
        refreshPanel();
    }
    
    private void demoteMember(MemberData member) {
        client.network.sendPacket((Packet)new PacketDemoteMember(guildID, member.auth));
        client.chat.addMessage(GameColor.YELLOW.getColorCode() + "Sent demotion request for " + member.name);
        refreshPanel();
    }
    
    private void kickMember(MemberData member) {
        // TODO: Add confirmation dialog
        client.network.sendPacket((Packet)new PacketKickMember(guildID, member.auth, "Kicked by " + myRank.getDisplayName()));
        client.chat.addMessage(GameColor.RED.getColorCode() + "Kicked " + member.name + " from guild");
        refreshPanel();
    }
    
    private void refreshPanel() {
        // Close and re-request info
        applyContinue();
        client.network.sendPacket(new PacketRequestGuildInfo(guildID));
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
    
    private Color getRankColor(GuildRank rank) {
        return switch (rank) {
            case LEADER -> Color.YELLOW;
            case OFFICER -> Color.CYAN;
            case MEMBER -> Color.WHITE;
            case RECRUIT -> Color.LIGHT_GRAY;
        };
    }
    
    @Override
    protected void init() {
        super.init();
        // Center the form - use a fixed default size
        try {
            this.setPosMiddle(400, 300);
        } catch (Exception ignored) {}
    }
    
    /**
     * Static factory method to show the panel.
     */
    public static void showPanel(
            Client client,
            int guildID,
            String guildName,
            String description,
            boolean isPublic,
            long treasury,
            GuildSymbolDesign symbol,
            List<MemberData> members,
            String[] auditEntries,
            long[] auditTimestamps,
            long requesterAuth) {
        
        try {
            GuildInfoPanelForm form = new GuildInfoPanelForm(
                client, guildID, guildName, description, isPublic,
                treasury, symbol, members, auditEntries, auditTimestamps, requesterAuth
            );
            
            // Use proper FormManager pattern to show the form
            necesse.gfx.forms.FormManager formManager = necesse.engine.GlobalData.getCurrentState().getFormManager();
            if (formManager instanceof necesse.gfx.forms.ContinueComponentManager) {
                ((necesse.gfx.forms.ContinueComponentManager) formManager).addContinueForm("guildinfopanel", form);
                ModLogger.debug("Guild info panel opened for guild %d", guildID);
            } else {
                ModLogger.warn("FormManager is not a ContinueComponentManager, cannot show guild info panel");
                client.chat.addMessage(GameColor.RED.getColorCode() + "Could not open guild info panel");
            }
        } catch (Exception e) {
            ModLogger.error("Failed to show guild info panel", e);
            client.chat.addMessage(GameColor.RED.getColorCode() + "Failed to open guild info panel");
        }
    }
}
