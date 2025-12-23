package medievalsim.ui.dialogs;

import necesse.engine.GlobalData;
import necesse.engine.state.MainGame;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

import java.util.function.Consumer;

/**
 * Confirmation dialog for destructive or dangerous commands.
 * 
 * Shows a warning message and requires user confirmation before executing.
 * Used for commands like /clearall, /kick, /ban, /creativemode that have
 * significant consequences.
 */
public class ConfirmationDialog extends Form {
    
    private static final int DIALOG_WIDTH = 400;
    private static final int DIALOG_HEIGHT = 200;
    private static final int MARGIN = 20;
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 32;
    
    private static final FontOptions WHITE_TEXT_20 = new FontOptions(20).color(java.awt.Color.WHITE);
    private static final FontOptions WHITE_TEXT_14 = new FontOptions(14).color(java.awt.Color.WHITE);
    
    private final Consumer<Boolean> callback;
    
    /**
     * Create a confirmation dialog.
     * 
     * @param title Dialog title (e.g., "Confirm Action")
     * @param message Warning message (e.g., "This will clear all items...")
     * @param callback Called with true if confirmed, false if cancelled
     */
    public ConfirmationDialog(String title, String message, Consumer<Boolean> callback) {
        super("confirmation", DIALOG_WIDTH, DIALOG_HEIGHT);
        this.callback = callback;
        
        buildUI(title, message);
    }
    
    private void buildUI(String title, String message) {
        int currentY = MARGIN;
        
        // Title label (centered)
        FormLabel titleLabel = new FormLabel(
            title,
            WHITE_TEXT_20,
            -1, // ALIGN_CENTER
            MARGIN, currentY,
            DIALOG_WIDTH - (MARGIN * 2)
        );
        this.addComponent((FormComponent) titleLabel);
        currentY += 35;
        
        // Warning message - split into multiple lines if needed
        String[] lines = message.split("\\n");
        for (String line : lines) {
            FormLabel messageLabel = new FormLabel(
                line,
                WHITE_TEXT_14,
                -1, // ALIGN_CENTER
                MARGIN, currentY,
                DIALOG_WIDTH - (MARGIN * 2)
            );
            this.addComponent((FormComponent) messageLabel);
            currentY += 22;
        }
        
        // Buttons at bottom
        int buttonY = DIALOG_HEIGHT - BUTTON_HEIGHT - MARGIN;
        int spacing = 20;
        int buttonStartX = (DIALOG_WIDTH - (BUTTON_WIDTH * 2 + spacing)) / 2;
        
        // Cancel button (left)
        FormTextButton cancelButton = new FormTextButton(
            "Cancel",
            buttonStartX, buttonY,
            BUTTON_WIDTH, FormInputSize.SIZE_32,
            ButtonColor.BASE
        );
        cancelButton.onClicked(e -> {
            callback.accept(false);
            this.close();
        });
        this.addComponent((FormComponent) cancelButton);
        
        // Confirm button (right, red for danger)
        FormTextButton confirmButton = new FormTextButton(
            "Confirm",
            buttonStartX + BUTTON_WIDTH + spacing, buttonY,
            BUTTON_WIDTH, FormInputSize.SIZE_32,
            ButtonColor.BASE // Use BASE color (no PRIORITIZE in Necesse)
        );
        confirmButton.onClicked(e -> {
            callback.accept(true);
            this.close();
        });
        this.addComponent((FormComponent) confirmButton);
    }
    
    /**
     * Close the dialog.
     */
    private void close() {
        // Remove from form manager
        if (GlobalData.getCurrentState() instanceof MainGame) {
            MainGame mainGame = (MainGame) GlobalData.getCurrentState();
            mainGame.formManager.removeComponent((FormComponent) this);
        }
    }
    
    /**
     * Predefined confirmation for clearing inventory.
     */
    public static ConfirmationDialog forClearInventory(String playerName, Consumer<Boolean> callback) {
        return new ConfirmationDialog(
            "Clear All Items",
            "WARNING: This will permanently delete ALL items in " + playerName + "'s inventory.\nThis cannot be undone!",
            callback
        );
    }
    
    /**
     * Predefined confirmation for kicking player.
     */
    public static ConfirmationDialog forKick(String playerName, Consumer<Boolean> callback) {
        return new ConfirmationDialog(
            "Kick Player",
            "Kick " + playerName + " from the server?\nThey can reconnect unless banned.",
            callback
        );
    }
    
    /**
     * Predefined confirmation for banning player.
     */
    public static ConfirmationDialog forBan(String playerName, Consumer<Boolean> callback) {
        return new ConfirmationDialog(
            "Ban Player",
            "PERMANENTLY BAN " + playerName + " from this server?\nThey will not be able to reconnect.",
            callback
        );
    }
    
    /**
     * Predefined confirmation for enabling creative mode.
     */
    public static ConfirmationDialog forCreativeMode(Consumer<Boolean> callback) {
        return new ConfirmationDialog(
            "Enable Creative Mode",
            "WARNING: Enabling Creative Mode will PERMANENTLY disable achievements.\nThis cannot be reversed!",
            callback
        );
    }
    
    /**
     * Predefined confirmation for allowing cheats.
     */
    public static ConfirmationDialog forAllowCheats(Consumer<Boolean> callback) {
        return new ConfirmationDialog(
            "Allow Cheats",
            "WARNING: Allowing cheats will PERMANENTLY disable achievements.\nThis cannot be reversed!",
            callback
        );
    }
    
    /**
     * Predefined confirmation for clearing area (world editing).
     */
    public static ConfirmationDialog forClearArea(int width, int height, Consumer<Boolean> callback) {
        return new ConfirmationDialog(
            "Clear Area",
            String.format("Clear a %dx%d tile area?\nThis will remove all tiles, objects, and walls.", width, height),
            callback
        );
    }
    
    /**
     * Predefined confirmation for /clearall command.
     */
    public static ConfirmationDialog forClearAll(Consumer<Boolean> callback) {  
        return new ConfirmationDialog(
            "Clear All Entities",
            "WARNING: This will remove ALL mobs, dropped items, and projectiles from the current level.\nThis cannot be undone!",
            callback
        );
    }

    /**
     * Predefined confirmation for large Grand Exchange purchases.
     */
    public static ConfirmationDialog forLargePurchase(String itemName, int quantity, long totalCost, Consumer<Boolean> callback) {
        return new ConfirmationDialog(
            "Confirm Large Purchase",
            String.format("Purchase %d x %s for %,d coins?\nThis is a large transaction.", quantity, itemName, totalCost),
            callback
        );
    }
}