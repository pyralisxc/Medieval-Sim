package medievalsim.ui;

import medievalsim.config.SettingsManager;
import medievalsim.ui.tabs.CommandHistoryTab;
import medievalsim.ui.tabs.ConsoleCommandsTab;
import medievalsim.ui.tabs.ModSettingsTab;
import medievalsim.util.Constants;
import necesse.engine.Settings;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Command Center Panel - Main coordinator for the tabbed interface.
 *
 * REFACTORED: This class now delegates to specialized tab classes instead of
 * handling all logic internally. Reduced from 1914 lines to ~200 lines.
 *
 * Features:
 * - Tab management (Console Commands, Mod Settings, Command History)
 * - Delegates all tab-specific logic to tab classes
 * - Cleaner separation of concerns
 */
public class CommandCenterPanel {

    // Font options
    private static final FontOptions WHITE_TEXT_20 = new FontOptions(20).color(Color.WHITE);
    private static final int MARGIN = 10;

    private final Client client;
    private final Runnable onBackCallback;

    // UI Components
    private FormLabel titleLabel;
    private FormTextButton consoleCommandsTabButton;
    private FormTextButton modSettingsTabButton;
    private FormTextButton commandHistoryTabButton;
    private List<FormComponent> sharedComponents; // Title and tab buttons

    // Tab instances
    private ConsoleCommandsTab consoleTab;
    private ModSettingsTab settingsTab;
    private CommandHistoryTab historyTab;

    // Panel dimensions (for rebuilding)
    private int panelStartX, panelStartY, panelWidth, panelHeight;
    private Form parentForm;

    /**
     * Constructor
     */
    public CommandCenterPanel(Client client, Runnable onBackCallback) {
        this.client = client;
        this.onBackCallback = onBackCallback;
        this.sharedComponents = new ArrayList<>();
    }

    /**
     * Tick method - delegates to active tab
     */
    public void tick(TickManager tickManager) {
        int activeTab = SettingsManager.getInstance().getActiveTab();
        
        switch (activeTab) {
            case 0:
                if (consoleTab != null) {
                    consoleTab.tick(tickManager);
                }
                break;
            case 1:
                // ModSettingsTab has no tick logic
                break;
            case 2:
                if (historyTab != null) {
                    historyTab.tick(tickManager);
                }
                break;
        }
    }

    /**
     * Build all components into the parent form
     *
     * @param parentForm The AdminToolsHudForm to add components to
     * @param startX Starting X position
     * @param startY Starting Y position
     * @param width Panel width
     * @param height Panel height
     */
    public void buildComponents(Form parentForm, int startX, int startY, int width, int height) {
        // Store parent form + dimensions for rebuilds
        this.parentForm = parentForm;
        this.panelStartX = startX;
        this.panelStartY = startY;
        this.panelWidth = width;
        this.panelHeight = height;

        int currentY = startY + MARGIN;
        int contentWidth = width - MARGIN * 2;

        // Title
        titleLabel = new FormLabel(
            "Command Center",
            WHITE_TEXT_20,
            FormLabel.ALIGN_LEFT,
            startX + MARGIN, currentY, contentWidth
        );
        parentForm.addComponent(titleLabel);
        sharedComponents.add(titleLabel);
        currentY += 35;

        // Tab Bar
        buildTabButtons(parentForm, startX, currentY, width);
        currentY += Constants.CommandCenter.TAB_BAR_HEIGHT + MARGIN;

        // Build content for active tab
        buildCurrentTabContent(parentForm, startX, currentY, width, height - (currentY - startY));
    }

    /**
     * Build tab buttons
     */
    private void buildTabButtons(Form parentForm, int startX, int currentY, int width) {
        int tabX = startX + MARGIN;
        int tabWidth = Constants.CommandCenter.TAB_BUTTON_WIDTH;
        int tabSpacing = Constants.CommandCenter.TAB_SPACING;

        // Calculate positions explicitly
        int tab1X = tabX;
        int tab2X = tabX + tabWidth + tabSpacing;
        int tab3X = tabX + (tabWidth + tabSpacing) * 2;

        consoleCommandsTabButton = new FormTextButton(
            "Console Commands",
            tab1X, currentY,
            tabWidth, FormInputSize.SIZE_32,
            SettingsManager.getInstance().getActiveTab() == 0 ? ButtonColor.RED : ButtonColor.BASE
        );
        consoleCommandsTabButton.onClicked(e -> switchToTab(0));
        parentForm.addComponent(consoleCommandsTabButton);
        sharedComponents.add(consoleCommandsTabButton);

        modSettingsTabButton = new FormTextButton(
            "Mod Settings",
            tab2X, currentY,
            tabWidth, FormInputSize.SIZE_32,
            SettingsManager.getInstance().getActiveTab() == 1 ? ButtonColor.RED : ButtonColor.BASE
        );
        modSettingsTabButton.onClicked(e -> switchToTab(1));
        parentForm.addComponent(modSettingsTabButton);
        sharedComponents.add(modSettingsTabButton);

        commandHistoryTabButton = new FormTextButton(
            "Command History",
            tab3X, currentY,
            tabWidth, FormInputSize.SIZE_32,
            SettingsManager.getInstance().getActiveTab() == 2 ? ButtonColor.RED : ButtonColor.BASE
        );
        commandHistoryTabButton.onClicked(e -> switchToTab(2));
        parentForm.addComponent(commandHistoryTabButton);
        sharedComponents.add(commandHistoryTabButton);
    }

    /**
     * Switch to a different tab
     */
    private void switchToTab(int tabIndex) {
        if (SettingsManager.getInstance().getActiveTab() == tabIndex) {
            return; // Already on this tab
        }

        // Remove components BEFORE changing active tab (so we remove the correct tab's components)
        removeComponents(parentForm);

        // Now switch to the new tab
        SettingsManager.getInstance().setActiveTab(tabIndex);
        Settings.saveClientSettings();

        // Rebuild entire panel to update tab button colors
        buildComponents(parentForm, panelStartX, panelStartY, panelWidth, panelHeight);
    }

    /**
     * Build content for the currently active tab
     */
    private void buildCurrentTabContent(Form parentForm, int startX, int startY, int width, int height) {
        int activeTab = SettingsManager.getInstance().getActiveTab();

        switch (activeTab) {
            case 0:
                consoleTab = new ConsoleCommandsTab(client, onBackCallback);
                consoleTab.buildInto(parentForm, startX, startY, width, height);
                break;
            case 1:
                settingsTab = new ModSettingsTab(onBackCallback);
                settingsTab.buildInto(parentForm, startX, startY, width, height);
                break;
            case 2:
                historyTab = new CommandHistoryTab(client, onBackCallback);
                historyTab.buildInto(parentForm, startX, startY, width, height);
                break;
        }
    }

    /**
     * Remove all components from parent form (cleanup)
     */
    public void removeComponents(Form parentForm) {
        // Remove active tab components
        int activeTab = SettingsManager.getInstance().getActiveTab();
        
        switch (activeTab) {
            case 0:
                if (consoleTab != null) {
                    consoleTab.removeFromForm(parentForm);
                }
                break;
            case 1:
                if (settingsTab != null) {
                    settingsTab.removeFromForm(parentForm);
                }
                break;
            case 2:
                if (historyTab != null) {
                    historyTab.removeFromForm(parentForm);
                }
                break;
        }

        // Remove shared components (title + tab buttons)
        for (FormComponent component : sharedComponents) {
            parentForm.removeComponent(component);
        }
        sharedComponents.clear();
    }
}
