package medievalsim.admintools.service;

import medievalsim.ui.AdminToolsHudManager;
import necesse.engine.Settings;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.MainGameFormManager;

/**
 * Static utilities for admin tools UI integration and permission checks.
 * Renamed from AdminToolsManager to follow Helper naming convention for all-static utility classes.
 */
public class AdminToolsHelper {
    
    public static void setupAdminButton(MainGameFormManager manager, Client client) {
        AdminToolsHudManager.init(client);
        
        // Add admin tools button to right quickbar with permission check
        manager.rightQuickbar.addButton(
            "medievalsim_admintools", 
            Settings.UI.quickbar_quests, 
            e -> AdminToolsHudManager.toggleHud(), 
            (GameMessage)new StaticMessage("Admin Tools"), 
            () -> hasAdminPermission(client)
        );
    }

    /**
     * Check if the client has admin permission
     */
    public static boolean hasAdminPermission(Client client) {
        if (client == null) {
            return false;
        }
        PermissionLevel level = client.getPermissionLevel();
        return level != null && level.getLevel() >= PermissionLevel.ADMIN.getLevel();
    }
}

