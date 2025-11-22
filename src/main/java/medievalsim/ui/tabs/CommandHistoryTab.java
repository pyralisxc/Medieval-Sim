package medievalsim.ui.tabs;

import medievalsim.config.ModConfig;
import medievalsim.config.SettingsManager;
import medievalsim.packets.PacketExecuteCommand;
import medievalsim.util.Constants;
import necesse.engine.Settings;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.*;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Command History tab - displays and allows replay of previously executed commands.
 * Extracted from CommandCenterPanel to improve maintainability.
 */
public class CommandHistoryTab {

    private static final FontOptions WHITE_TEXT_14 = new FontOptions(14).color(Color.WHITE);
    private static final FontOptions WHITE_TEXT_11 = new FontOptions(11).color(Color.WHITE);
    private static final int MARGIN = 10;

    private final Client client;
    private final Runnable onBackCallback;
    private List<FormComponent> allComponents;
    private Form parentForm;

    // Store original panel dimensions for rebuilding
    private int panelStartX, panelStartY, panelWidth, panelHeight;

    public CommandHistoryTab(Client client, Runnable onBackCallback) {
        this.client = client;
        this.onBackCallback = onBackCallback;
        this.allComponents = new ArrayList<>();
    }

    /**
     * Build all tab components into the parent form
     */
    public void buildInto(Form parentForm, int startX, int startY, int width, int height) {
        this.parentForm = parentForm;
        this.panelStartX = startX;
        this.panelStartY = startY;
        this.panelWidth = width;
        this.panelHeight = height;

        int currentY = startY;
        int contentWidth = width - MARGIN * 2;

        // Title and clear button
        FormLabel titleLabel = new FormLabel(
            "Command History (Last " + ModConfig.CommandCenter.maxHistory + ")",
            WHITE_TEXT_14,
            FormLabel.ALIGN_LEFT,
            startX + MARGIN, currentY, contentWidth - 100
        );
        parentForm.addComponent(titleLabel);
        allComponents.add(titleLabel);

        // Clear history button (top right)
        FormTextButton clearHistoryButton = new FormTextButton(
            "Clear History",
            startX + width - MARGIN - 100, currentY - 5,
            100, FormInputSize.SIZE_20,
            ButtonColor.RED
        );
        clearHistoryButton.onClicked(e -> {
            SettingsManager.getInstance().getCommandHistory().clear();
            Settings.saveClientSettings();
            // Rebuild entire tab to show empty state
            removeFromForm(parentForm);
            buildInto(parentForm, panelStartX, panelStartY, panelWidth, panelHeight);
        });
        parentForm.addComponent(clearHistoryButton);
        allComponents.add(clearHistoryButton);

        currentY += 35;

        // Calculate available height for history box
        int actionBarHeight = Constants.CommandCenter.ACTION_BAR_HEIGHT;
        int availableHeight = height - currentY - MARGIN - actionBarHeight;

        // Scrollable history list
        FormContentBox historyBox = new FormContentBox(
            startX + MARGIN, currentY,
            contentWidth, availableHeight
        );
        parentForm.addComponent(historyBox);
        allComponents.add(historyBox);

        if (SettingsManager.getInstance().getCommandHistory() == null || 
            SettingsManager.getInstance().getCommandHistory().isEmpty()) {
            // Empty state
            FormLabel emptyLabel = new FormLabel(
                "No commands executed yet. Execute a command from the Console Commands tab.",
                WHITE_TEXT_11,
                FormLabel.ALIGN_LEFT,
                10, 20, contentWidth - 20
            );
            historyBox.addComponent(emptyLabel);
        } else {
            // Build history entry list
            int entryY = 10;
            int index = 1;

            for (String commandString : SettingsManager.getInstance().getCommandHistory()) {
                // Command text (truncate if too long)
                String displayText = commandString;
                if (displayText.length() > 60) {
                    displayText = displayText.substring(0, 57) + "...";
                }

                FormLabel commandLabel = new FormLabel(
                    "#" + index + ": " + displayText,
                    WHITE_TEXT_11,
                    FormLabel.ALIGN_LEFT,
                    10, entryY, contentWidth - 120
                );
                historyBox.addComponent(commandLabel);

                // Re-execute button
                FormTextButton reExecuteButton = new FormTextButton(
                    "Re-Execute",
                    contentWidth - 100, entryY - 3,
                    90, FormInputSize.SIZE_16,
                    ButtonColor.BASE
                );

                // Capture commandString for lambda
                final String cmdToExecute = commandString;
                reExecuteButton.onClicked(e -> {
                    client.network.sendPacket(new PacketExecuteCommand(cmdToExecute));
                });

                historyBox.addComponent(reExecuteButton);

                entryY += 25;
                index++;
            }

            // Update content box bounds
            int contentHeight = Math.max(entryY + 10, historyBox.getHeight());
            historyBox.setContentBox(new Rectangle(0, 0, contentWidth, contentHeight));
        }

        // Back button at bottom
        int backButtonY = height - MARGIN - Constants.CommandCenter.ACTION_BAR_HEIGHT;
        int backButtonWidth = Constants.UI.MIN_BUTTON_WIDTH;
        FormTextButton backButton = new FormTextButton(
            "Back",
            startX + MARGIN, backButtonY,
            backButtonWidth, FormInputSize.SIZE_32,
            ButtonColor.BASE
        );
        backButton.onClicked(e -> {
            if (onBackCallback != null) {
                onBackCallback.run();
            }
        });
        parentForm.addComponent(backButton);
        allComponents.add(backButton);
    }

    /**
     * Remove all components from parent form
     */
    public void removeFromForm(Form parentForm) {
        for (FormComponent component : allComponents) {
            parentForm.removeComponent(component);
        }
        allComponents.clear();
    }

    /**
     * Tick method (currently no-op, but here for consistency)
     */
    public void tick(necesse.engine.gameLoop.tickManager.TickManager tickManager) {
        // No periodic updates needed for history tab
    }
}
