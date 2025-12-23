/*
 * GuildSelectionForm - UI for selecting which guild to view when in multiple guilds
 * Part of Medieval Sim Mod guild management system.
 * Per docs: Opens when player clicks "View Guild Info" and belongs to multiple guilds.
 */
package medievalsim.guilds.ui;

import medievalsim.guilds.GuildRank;
import medievalsim.packets.PacketRequestGuildInfo;
import medievalsim.util.ModLogger;
import necesse.engine.GlobalData;
import necesse.engine.localization.Localization;
import necesse.engine.network.client.Client;
import necesse.engine.window.GameWindow;
import necesse.engine.window.WindowManager;
import necesse.gfx.forms.ContinueComponentManager;
import necesse.gfx.forms.FormManager;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.presets.ContinueForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

import java.awt.Color;
import java.util.List;

/**
 * Form displayed when player is in multiple guilds and clicks "View Guild Info".
 * Allows selecting which guild to view details for.
 */
public class GuildSelectionForm extends ContinueForm {

    private static final int WIDTH = 350;
    private static final int HEADER_HEIGHT = 50;
    private static final int ROW_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 50;
    
    private final Client client;
    private final List<GuildEntry> guilds;
    
    /**
     * Represents a guild the player belongs to.
     */
    public static class GuildEntry {
        public final int guildID;
        public final String guildName;
        public final GuildRank rank;
        public final int memberCount;
        
        public GuildEntry(int guildID, String guildName, GuildRank rank, int memberCount) {
            this.guildID = guildID;
            this.guildName = guildName;
            this.rank = rank;
            this.memberCount = memberCount;
        }
    }
    
    public GuildSelectionForm(Client client, List<GuildEntry> guilds) {
        super("guild_selection", WIDTH, calculateHeight(guilds.size()));
        this.client = client;
        this.guilds = guilds;
        
        ModLogger.debug("GuildSelectionForm created with %d guilds", guilds.size());
        setupUI();
    }
    
    private static int calculateHeight(int guildCount) {
        return HEADER_HEIGHT + (guildCount * ROW_HEIGHT) + FOOTER_HEIGHT;
    }
    
    private void setupUI() {
        // Title
        addComponent(new FormLabel(
            Localization.translate("ui", "guildselection.title"),
            new FontOptions(18),
            FormLabel.ALIGN_MID,
            WIDTH / 2, 15
        ));
        
        int y = HEADER_HEIGHT;
        
        // Guild entries
        for (GuildEntry guild : guilds) {
            addGuildRow(guild, y);
            y += ROW_HEIGHT;
        }
        
        // Close button at bottom
        FormTextButton closeBtn = addComponent(new FormTextButton(
            Localization.translate("ui", "close"),
            (WIDTH - 100) / 2, y + 10, 100, FormInputSize.SIZE_24, ButtonColor.BASE
        ));
        closeBtn.onClicked(e -> applyContinue());
    }
    
    private void addGuildRow(GuildEntry guild, int y) {
        // Guild name (left side)
        addComponent(new FormLabel(
            guild.guildName,
            new FontOptions(14),
            FormLabel.ALIGN_LEFT,
            15, y + 5
        ));
        
        // Rank and member count (smaller, below name)
        String info = guild.rank.getDisplayName() + " • " + guild.memberCount + " members";
        addComponent(new FormLabel(
            info,
            new FontOptions(10).color(Color.LIGHT_GRAY),
            FormLabel.ALIGN_LEFT,
            15, y + 22
        ));
        
        // View button (right side)
        FormTextButton viewBtn = addComponent(new FormTextButton(
            Localization.translate("ui", "guildselection.view"),
            WIDTH - 90, y + 5, 75, FormInputSize.SIZE_24, ButtonColor.BASE
        ));
        viewBtn.onClicked(e -> onSelectGuild(guild));
    }
    
    private void onSelectGuild(GuildEntry guild) {
        ModLogger.debug("Selected guild %d (%s) to view", guild.guildID, guild.guildName);
        // Send request for full guild info
        client.network.sendPacket(new PacketRequestGuildInfo(guild.guildID));
        // Close this selection form
        applyContinue();
    }
    
    @Override
    protected void init() {
        super.init();
        centerDialog();
    }
    
    private void centerDialog() {
        GameWindow window = WindowManager.getWindow();
        if (window != null) {
            int centerX = window.getHudWidth() / 2 - this.getWidth() / 2;
            int centerY = window.getHudHeight() / 2 - this.getHeight() / 2;
            this.setPosMiddle(centerX + this.getWidth() / 2, centerY + this.getHeight() / 2);
        }
    }
    
    /**
     * Static factory to show the selection form.
     */
    public static void show(Client client, List<GuildEntry> guilds) {
        if (guilds == null || guilds.isEmpty()) {
            ModLogger.warn("Cannot show GuildSelectionForm with no guilds");
            return;
        }
        
        try {
            GuildSelectionForm form = new GuildSelectionForm(client, guilds);
            
            // Use proper FormManager to add continue form
            FormManager formManager = GlobalData.getCurrentState().getFormManager();
            if (formManager instanceof ContinueComponentManager) {
                ((ContinueComponentManager) formManager).addContinueForm("guildselection", form);
                ModLogger.debug("Guild selection form opened with %d guilds", guilds.size());
            } else {
                ModLogger.warn("FormManager is not a ContinueComponentManager, cannot show guild selection");
            }
        } catch (Exception e) {
            ModLogger.error("Failed to show guild selection form", e);
        }
    }
}
