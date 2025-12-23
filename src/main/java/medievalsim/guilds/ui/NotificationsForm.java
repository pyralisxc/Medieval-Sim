/*
 * NotificationsForm - Client UI for guild notifications inbox
 * Part of Medieval Sim Mod guild management system.
 * Per docs: shows invites, guild notices, admin messages with clear/clear-all actions.
 */
package medievalsim.guilds.ui;

import medievalsim.guilds.notifications.GuildNotification;
import medievalsim.util.ModLogger;
import necesse.engine.localization.Localization;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.presets.containerComponent.ContainerForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Client-side form for viewing guild notifications.
 * Displays a list of notifications with clear actions.
 */
public class NotificationsForm extends ContainerForm<NotificationsContainer> {

    private static final int FORM_WIDTH = 450;
    private static final int FORM_HEIGHT = 400;
    private static final int ROW_HEIGHT = 60;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, HH:mm");
    
    public NotificationsForm(Client client, NotificationsContainer container) {
        super(client, FORM_WIDTH, FORM_HEIGHT, container);
        
        ModLogger.info("NotificationsForm created with %d notification(s)", container.getNotifications().size());
        setupUI();
    }
    
    private void setupUI() {
        // Title
        FormLabel titleLabel = new FormLabel(
            Localization.translate("ui", "notifications.title"),
            new FontOptions(20),
            FormLabel.ALIGN_MID,
            FORM_WIDTH / 2, 15);
        addComponent(titleLabel);
        
        List<NotificationsContainer.NotificationInfo> notifications = container.getNotifications();
        
        // Clear All button (top right, only if there are notifications)
        if (!notifications.isEmpty()) {
            FormTextButton clearAllBtn = new FormTextButton(
                Localization.translate("ui", "notifications.clearall"),
                FORM_WIDTH - 110, 10, 100, FormInputSize.SIZE_24, ButtonColor.RED);
            clearAllBtn.onClicked(e -> onClearAll());
            addComponent(clearAllBtn);
        }
        
        if (notifications.isEmpty()) {
            // No notifications message
            FormLabel emptyLabel = new FormLabel(
                Localization.translate("ui", "notifications.empty"),
                new FontOptions(14).color(Color.GRAY),
                FormLabel.ALIGN_MID,
                FORM_WIDTH / 2, FORM_HEIGHT / 2 - 20);
            addComponent(emptyLabel);
        } else {
            // Notification list (scrollable area would be nice, for now just list)
            int y = 50;
            int maxVisible = (FORM_HEIGHT - 100) / ROW_HEIGHT;
            int count = Math.min(notifications.size(), maxVisible);
            
            for (int i = 0; i < count; i++) {
                addNotificationRow(notifications.get(i), y);
                y += ROW_HEIGHT;
            }
            
            // Show "and X more..." if truncated
            if (notifications.size() > maxVisible) {
                int remaining = notifications.size() - maxVisible;
                FormLabel moreLabel = new FormLabel(
                    String.format("... and %d more notification(s)", remaining),
                    new FontOptions(10).color(Color.GRAY),
                    FormLabel.ALIGN_MID,
                    FORM_WIDTH / 2, y + 5);
                addComponent(moreLabel);
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
    
    private void addNotificationRow(NotificationsContainer.NotificationInfo info, int y) {
        int x = 15;
        
        // Icon based on type (using text for now)
        String icon = getTypeIcon(info.type);
        Color iconColor = getTypeColor(info.type);
        FormLabel iconLabel = new FormLabel(
            icon,
            new FontOptions(16).color(iconColor),
            FormLabel.ALIGN_LEFT,
            x, y + 5);
        addComponent(iconLabel);
        
        // Title (bold if unread)
        Color titleColor = info.read ? Color.GRAY : Color.WHITE;
        FormLabel titleLabel = new FormLabel(
            info.title,
            new FontOptions(12).color(titleColor),
            FormLabel.ALIGN_LEFT,
            x + 30, y);
        addComponent(titleLabel);
        
        // Message (truncated)
        String displayMsg = info.message.length() > 50 ? 
            info.message.substring(0, 47) + "..." : info.message;
        FormLabel msgLabel = new FormLabel(
            displayMsg,
            new FontOptions(10).color(Color.LIGHT_GRAY),
            FormLabel.ALIGN_LEFT,
            x + 30, y + 18);
        addComponent(msgLabel);
        
        // Timestamp
        String timeStr = DATE_FORMAT.format(new Date(info.timestamp));
        FormLabel timeLabel = new FormLabel(
            timeStr,
            new FontOptions(9).color(Color.DARK_GRAY),
            FormLabel.ALIGN_LEFT,
            x + 30, y + 35);
        addComponent(timeLabel);
        
        // Action buttons (right side)
        int btnX = FORM_WIDTH - 15;
        
        // Clear (X) button
        FormTextButton clearBtn = new FormTextButton(
            "X",
            btnX - 30, y + 10, 25, FormInputSize.SIZE_20, ButtonColor.BASE);
        clearBtn.onClicked(e -> onClearNotification(info));
        addComponent(clearBtn);
        
        // Accept button for invites
        if (info.type == GuildNotification.NotificationType.GUILD_INVITE && info.actionData != null) {
            FormTextButton acceptBtn = new FormTextButton(
                Localization.translate("ui", "notifications.accept"),
                btnX - 100, y + 10, 65, FormInputSize.SIZE_20, ButtonColor.BASE);
            acceptBtn.onClicked(e -> onAcceptInvite(info));
            addComponent(acceptBtn);
        }
    }
    
    private String getTypeIcon(GuildNotification.NotificationType type) {
        switch (type) {
            case GUILD_INVITE: return "✉";
            case GUILD_NOTICE: return "📢";
            case RANK_CHANGED: return "⬆";
            case MEMBER_JOINED: return "➕";
            case MEMBER_LEFT: return "➖";
            case RESEARCH_COMPLETE: return "🔬";
            case ADMIN_MESSAGE: return "⚠";
            case TREASURY_UPDATE: return "💰";
            default: return "•";
        }
    }
    
    private Color getTypeColor(GuildNotification.NotificationType type) {
        switch (type) {
            case GUILD_INVITE: return new Color(100, 149, 237);  // Blue
            case RANK_CHANGED: return new Color(255, 215, 0);    // Gold
            case RESEARCH_COMPLETE: return new Color(144, 238, 144); // Green
            case ADMIN_MESSAGE: return new Color(255, 165, 0);   // Orange
            case TREASURY_UPDATE: return new Color(255, 223, 0); // Yellow
            default: return Color.WHITE;
        }
    }
    
    private void onClearNotification(NotificationsContainer.NotificationInfo info) {
        ModLogger.debug("Clear notification clicked: %d", info.notificationID);
        client.network.sendPacket(new medievalsim.packets.PacketClearNotification(info.notificationID));
        // TODO: Refresh UI or close and reopen
        client.closeContainer(true);
    }
    
    private void onClearAll() {
        ModLogger.debug("Clear all notifications clicked");
        client.network.sendPacket(new medievalsim.packets.PacketClearAllGuildNotifications());
        client.closeContainer(true);
    }
    
    private void onAcceptInvite(NotificationsContainer.NotificationInfo info) {
        ModLogger.debug("Accept invite clicked for notification %d, guild %s", 
            info.notificationID, info.actionData);
        try {
            int guildID = Integer.parseInt(info.actionData);
            client.network.sendPacket(new medievalsim.packets.PacketRespondInvite(guildID, true));
            client.closeContainer(true);
        } catch (NumberFormatException e) {
            ModLogger.error("Invalid guild ID in invite action data: %s", info.actionData);
        }
    }
    
    @Override
    public boolean shouldOpenInventory() {
        return false;
    }
}
