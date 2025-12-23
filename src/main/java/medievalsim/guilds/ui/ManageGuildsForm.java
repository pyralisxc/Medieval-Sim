/*
 * ManageGuildsForm - Client UI for managing guild memberships
 * Part of Medieval Sim Mod guild management system.
 * Per docs: handles per-guild leave, buy banner actions.
 */
package medievalsim.guilds.ui;

import medievalsim.guilds.GuildRank;
import medievalsim.packets.PacketLeaveGuild;
import medievalsim.util.ModLogger;
import necesse.engine.localization.Localization;
import necesse.engine.network.Packet;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.presets.containerComponent.ContainerForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

import java.awt.Color;
import java.util.List;

/**
 * Client-side form for managing guild memberships.
 * Displays a list of guilds the player belongs to with actions.
 */
public class ManageGuildsForm extends ContainerForm<ManageGuildsContainer> {

    private static final int FORM_WIDTH = 400;
    private static final int FORM_HEIGHT = 350;
    private static final int BUTTON_WIDTH = 100;
    private static final int ROW_HEIGHT = 50;
    
    public ManageGuildsForm(Client client, ManageGuildsContainer container) {
        super(client, FORM_WIDTH, FORM_HEIGHT, container);
        
        ModLogger.info("ManageGuildsForm created with %d guild(s)", container.getMemberships().size());
        setupUI();
    }
    
    private void setupUI() {
        // Title
        FormLabel titleLabel = new FormLabel(
            Localization.translate("ui", "manageguilds.title"),
            new FontOptions(20),
            FormLabel.ALIGN_MID,
            FORM_WIDTH / 2, 15);
        addComponent(titleLabel);
        
        List<ManageGuildsContainer.GuildMembershipInfo> memberships = container.getMemberships();
        
        if (memberships.isEmpty()) {
            // No guilds message
            FormLabel noGuildsLabel = new FormLabel(
                Localization.translate("ui", "manageguilds.noguilds"),
                new FontOptions(14).color(Color.GRAY),
                FormLabel.ALIGN_MID,
                FORM_WIDTH / 2, FORM_HEIGHT / 2 - 20);
            addComponent(noGuildsLabel);
        } else {
            // Guild list
            int y = 50;
            
            for (ManageGuildsContainer.GuildMembershipInfo info : memberships) {
                addGuildRow(info, y);
                y += ROW_HEIGHT;
            }
        }
        
        // Close button at bottom
        int closeY = FORM_HEIGHT - 45;
        FormTextButton closeButton = new FormTextButton(
            Localization.translate("ui", "close"),
            (FORM_WIDTH - 150) / 2, closeY, 150, FormInputSize.SIZE_32, ButtonColor.BASE);
        closeButton.onClicked(e -> client.closeContainer(true));
        addComponent(closeButton);
    }
    
    private void addGuildRow(ManageGuildsContainer.GuildMembershipInfo info, int y) {
        int x = 15;
        
        // Guild name
        FormLabel nameLabel = new FormLabel(
            info.guildName,
            new FontOptions(14),
            FormLabel.ALIGN_LEFT,
            x, y);
        addComponent(nameLabel);
        
        // Rank badge
        GuildRank rank = GuildRank.fromLevel(info.rankLevel);
        Color rankColor = getRankColor(rank);
        FormLabel rankLabel = new FormLabel(
            rank.getDisplayName(),
            new FontOptions(10).color(rankColor),
            FormLabel.ALIGN_LEFT,
            x, y + 18);
        addComponent(rankLabel);
        
        // Member count and treasury
        FormLabel statsLabel = new FormLabel(
            String.format("%d members • %,d gold", info.memberCount, info.treasury),
            new FontOptions(10).color(Color.LIGHT_GRAY),
            FormLabel.ALIGN_LEFT,
            x + 100, y + 18);
        addComponent(statsLabel);
        
        // Action buttons (right side)
        int btnX = FORM_WIDTH - 15 - BUTTON_WIDTH;
        
        // Buy Banner button (per docs: banner purchases happen in ManageGuilds)
        if (rank.level >= GuildRank.OFFICER.level) {
            FormTextButton bannerBtn = new FormTextButton(
                Localization.translate("ui", "manageguilds.buybanner"),
                btnX - BUTTON_WIDTH - 10, y + 5, BUTTON_WIDTH, FormInputSize.SIZE_24, ButtonColor.BASE);
            bannerBtn.onClicked(e -> onBuyBanner(info));
            addComponent(bannerBtn);
        }
        
        // Leave Guild button (per docs: leave is per-guild in ManageGuilds)
        // Leaders cannot leave (must transfer or disband first)
        if (rank != GuildRank.LEADER) {
            FormTextButton leaveBtn = new FormTextButton(
                Localization.translate("ui", "manageguilds.leave"),
                btnX, y + 5, BUTTON_WIDTH, FormInputSize.SIZE_24, ButtonColor.RED);
            leaveBtn.onClicked(e -> onLeaveGuild(info));
            addComponent(leaveBtn);
        }
    }
    
    private Color getRankColor(GuildRank rank) {
        switch (rank) {
            case LEADER: return new Color(255, 215, 0);   // Gold
            case OFFICER: return new Color(100, 149, 237); // Cornflower blue
            case MEMBER: return new Color(144, 238, 144);  // Light green
            case RECRUIT: return Color.LIGHT_GRAY;
            default: return Color.WHITE;
        }
    }
    
    private void onBuyBanner(ManageGuildsContainer.GuildMembershipInfo info) {
        ModLogger.debug("Buy banner clicked for guild %d (%s)", info.guildID, info.guildName);
        // TODO: Open BuyBannerModal - per docs this opens a modal showing cost and placement options
        // For now, send packet to server to open the buy banner modal
        client.network.sendPacket(new medievalsim.packets.PacketRequestBuyBanner(info.guildID));
    }
    
    private void onLeaveGuild(ManageGuildsContainer.GuildMembershipInfo info) {
        ModLogger.debug("Leave guild clicked for guild %d (%s)", info.guildID, info.guildName);
        // Send leave guild packet
        client.network.sendPacket((Packet) new PacketLeaveGuild(info.guildID));
        client.closeContainer(true);
    }
    
    @Override
    public boolean shouldOpenInventory() {
        return false;
    }
}
