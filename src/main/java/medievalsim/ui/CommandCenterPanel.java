package medievalsim.ui;

import medievalsim.commandcenter.settings.ConfigurableSetting;
import medievalsim.commandcenter.settings.ModConfigSection;
import medievalsim.commandcenter.settings.SettingType;
import medievalsim.commandcenter.settings.UniversalModConfigScanner;
import medievalsim.config.SettingsManager;
import medievalsim.config.ModConfig;
import medievalsim.commandcenter.CommandCategory;
import medievalsim.commandcenter.wrapper.NecesseCommandMetadata;
import medievalsim.commandcenter.wrapper.NecesseCommandRegistry;
import medievalsim.commandcenter.wrapper.ParameterMetadata;
import medievalsim.commandcenter.wrapper.widgets.ParameterWidget;
import medievalsim.commandcenter.wrapper.widgets.ParameterWidgetFactory;
import medievalsim.commandcenter.wrapper.widgets.PlayerDropdownWidget;
import medievalsim.commandcenter.wrapper.widgets.MultiChoiceWidget;
import medievalsim.commandcenter.wrapper.widgets.RelativeIntInputWidget;
import medievalsim.commandcenter.wrapper.widgets.CoordinatePairWrapperWidget;
import medievalsim.commandcenter.wrapper.widgets.AutocompleteDropdownWidget;

import medievalsim.packets.PacketExecuteCommand;
import medievalsim.util.Constants;
import medievalsim.util.ModLogger;


import necesse.engine.localization.message.StaticMessage;
import necesse.engine.modLoader.LoadedMod;
import necesse.engine.modLoader.ModLoader;
import necesse.engine.network.client.Client;
import necesse.engine.Settings;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.*;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.gameTooltips.GameTooltips;
import necesse.gfx.gameTooltips.StringTooltips;
import necesse.gfx.ui.ButtonColor;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Command Center Panel - Component-based UI (NOT a Form)
 *
 * This class builds FormComponents directly into a parent Form (AdminToolsHudForm).
 * It does NOT extend Form, eliminating the nested-form anti-pattern.
 *
 * Features:
 * - Dropdown list with all commands (sorted alphabetically)
 * - Favorites system with checkboxes (unicode doesn't render in Necesse fonts)
 * - Dynamic parameter form generation from command metadata
 * - Parameter value caching (preserved when switching commands)
 * - Scrollable parameter area
 */
public class CommandCenterPanel {

    // Font options
    private static final FontOptions WHITE_TEXT_20 = new FontOptions(20).color(Color.WHITE);
    private static final FontOptions WHITE_TEXT_14 = new FontOptions(14).color(Color.WHITE);
    private static final FontOptions WHITE_TEXT_11 = new FontOptions(11).color(Color.WHITE);

    private static final int MARGIN = 10;

    private final Client client;
    private final Runnable onBackCallback;
    private Form parentForm; // Store parent form reference for SearchableDropdown lifecycle

    // UI Components (stored so we can manipulate them)
    private FormLabel titleLabel;

    // Tab buttons
    private FormTextButton consoleCommandsTabButton;
    private FormTextButton modSettingsTabButton;
    private FormTextButton commandHistoryTabButton;

    // Console Commands tab components
    private SearchableDropdown<NecesseCommandMetadata> commandDropdown;
    private necesse.gfx.forms.components.FormDropdownSelectionButton<medievalsim.commandcenter.CommandCategory> categoryFilter;
    private FormContentBox favoriteButtonsBox;
    private FormLabel commandInfoLabel;
    // Read-only preview of the actual chat command string ("/command args")
    private FormLabel commandPreviewLabel;
    private FormTextButton favoriteToggleButton;
    private FormContentBox parameterScrollArea;
    private FormTextButton clearButton;
    private FormTextButton executeButton;
    private FormTextButton backButton;

    // State
    private NecesseCommandMetadata currentCommand;
    // Current command string without leading slash, e.g. "tp player 100 200"
    private String currentCommandTail;
    private Map<String, Map<String, String>> commandParameterCache;
    private List<ParameterWidget> currentParameterWidgets;
    private List<FormComponent> allComponents; // Track all components for cleanup

    // Panel dimensions (for rebuilding)
    private int panelStartX, panelStartY, panelWidth, panelHeight;

    /**
     * Constructor
     */
    public CommandCenterPanel(Client client, Runnable onBackCallback) {
        this.client = client;
        this.onBackCallback = onBackCallback;
        this.commandParameterCache = new HashMap<>();
        this.currentParameterWidgets = new ArrayList<>();
        this.allComponents = new ArrayList<>();
    }

    /**
     * Tick method - must be called from parent form to enable search filtering
     */
    public void tick(necesse.engine.gameLoop.tickManager.TickManager tickManager) {
        // Tick the command dropdown for search filtering
        if (commandDropdown != null) {
            commandDropdown.tick(tickManager);
        }

        // Tick parameter widgets that need it (remaining server admin widgets only)
        if (currentParameterWidgets != null) {
            for (ParameterWidget widget : currentParameterWidgets) {
                if (widget instanceof MultiChoiceWidget) {
                    // Check if dropdown selection changed and swap sub-widgets if needed
                    ((MultiChoiceWidget) widget).updateSelectionIfChanged();
                } else if (widget instanceof PlayerDropdownWidget) {
                    // Refresh player list periodically (when players join/leave)
                    ((PlayerDropdownWidget) widget).tick(tickManager);
                }
                // ❌ REMOVED: Creative widget instanceof checks (ItemDropdownWidget, BuffDropdownWidget,
                // EnchantmentDropdownWidget, BiomeDropdownWidget, TileDropdownWidget) - widgets deleted
            }
        }

        // Update button states based on parameter values
        // This ensures Execute button is disabled when required params are empty
        // Only update if components are initialized to prevent null pointer exceptions
        if (clearButton != null && executeButton != null) {
            updateButtonStates();
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
        // Store parent form + dimensions for rebuilds and SearchableDropdown lifecycle management
        this.parentForm = parentForm;
        this.panelStartX = startX;
        this.panelStartY = startY;
        this.panelWidth = width;
        this.panelHeight = height;

        // Clear existing state when rebuilding
        this.currentCommand = null;
        this.currentCommandTail = "";
        if (commandPreviewLabel != null) {
            commandPreviewLabel.setText("Command: /");
        }


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
        allComponents.add(titleLabel);
        currentY += 35;

        // Tab Bar
        int tabY = currentY;
        int tabX = startX + MARGIN;
        int tabWidth = Constants.CommandCenter.TAB_BUTTON_WIDTH;
        int tabSpacing = Constants.CommandCenter.TAB_SPACING;

        consoleCommandsTabButton = new FormTextButton(
            "Console Commands",
            tabX, tabY,
            tabWidth, FormInputSize.SIZE_32,
            SettingsManager.getInstance().getActiveTab() == 0 ? ButtonColor.RED : ButtonColor.BASE
        );
        consoleCommandsTabButton.onClicked(e -> switchToTab(0, parentForm, startX, startY, width, height));
        parentForm.addComponent(consoleCommandsTabButton);
        allComponents.add(consoleCommandsTabButton);

        modSettingsTabButton = new FormTextButton(
            "Mod Settings",
            tabX + tabWidth + tabSpacing, tabY,
            tabWidth, FormInputSize.SIZE_32,
            SettingsManager.getInstance().getActiveTab() == 1 ? ButtonColor.RED : ButtonColor.BASE
        );
        modSettingsTabButton.onClicked(e -> switchToTab(1, parentForm, startX, startY, width, height));
        parentForm.addComponent(modSettingsTabButton);
        allComponents.add(modSettingsTabButton);

        commandHistoryTabButton = new FormTextButton(
            "Command History",
            tabX + (tabWidth + tabSpacing) * 2, tabY,
            tabWidth, FormInputSize.SIZE_32,
            SettingsManager.getInstance().getActiveTab() == 2 ? ButtonColor.RED : ButtonColor.BASE
        );
        commandHistoryTabButton.onClicked(e -> switchToTab(2, parentForm, startX, startY, width, height));
        parentForm.addComponent(commandHistoryTabButton);
        allComponents.add(commandHistoryTabButton);

        currentY += Constants.CommandCenter.TAB_BAR_HEIGHT + MARGIN;

        // Build content for active tab
        buildCurrentTabContent(parentForm, startX, currentY, width, height);
    }

    /**
     * Switch to a different tab
     */
    private void switchToTab(int tabIndex, Form parentForm, int startX, int startY, int width, int height) {
        if (SettingsManager.getInstance().getActiveTab() == tabIndex) {
            return; // Already on this tab
        }

        SettingsManager.getInstance().setActiveTab(tabIndex);
        Settings.saveClientSettings();

        // Rebuild entire panel to update tab button colors
        removeComponents(parentForm);
        buildComponents(parentForm, panelStartX, panelStartY, panelWidth, panelHeight);
    }

    /**
     * Build content for the currently active tab
     */
    private void buildCurrentTabContent(Form parentForm, int startX, int startY, int width, int height) {
        switch (SettingsManager.getInstance().getActiveTab()) {
            case 0:
                buildConsoleCommandsTab(parentForm, startX, startY, width, height);
                break;
            case 1:
                buildModSettingsTab(parentForm, startX, startY, width, height);
                break;
            case 2:
                buildCommandHistoryTab(parentForm, startX, startY, width, height);
                break;
        }
    }

    // clearTabContent() method removed - was never called locally, tab clearing handled by removeComponents()

    /**
     * Build Console Commands tab (existing functionality)
     */
    private void buildConsoleCommandsTab(Form parentForm, int startX, int startY, int width, int height) {
        int currentY = startY;
        int contentWidth = width - MARGIN * 2;

        // Category filter label + dropdown
        FormLabel categoryLabel = new FormLabel(
            "Filter by category:",
            WHITE_TEXT_11,
            FormLabel.ALIGN_LEFT,
            startX + MARGIN, currentY, 120
        );
        parentForm.addComponent(categoryLabel);
        allComponents.add(categoryLabel);

        // Category filter dropdown (150px width, positioned to the right of label)
        categoryFilter = new FormDropdownSelectionButton<CommandCategory>(
            startX + MARGIN + 130, currentY - 5,
            FormInputSize.SIZE_20,
            ButtonColor.BASE,
            150,
            new StaticMessage("All Categories")
        );

        // Add "All" option (null value = show all)
        categoryFilter.options.add(null, new StaticMessage("All Categories"));

        // Add only categories that have available commands (filter out empty categories)
        // Safety check: ensure command registry is initialized
        if (!NecesseCommandRegistry.isInitialized()) {
            ModLogger.warn("Command Registry not initialized when building Command Center - displaying empty interface");
        }

        List<NecesseCommandMetadata> allCommands = new ArrayList<>(NecesseCommandRegistry.getAllCommands());
        Set<CommandCategory> categoriesWithCommands = new HashSet<>();

        for (NecesseCommandMetadata cmd : allCommands) {
            if (cmd.isAvailableInWorld(client)) {
                categoriesWithCommands.add(cmd.getCategory());
            }
        }

        // Add categories that have at least one available command
        for (CommandCategory cat : CommandCategory.values()) {
            if (categoriesWithCommands.contains(cat)) {
                categoryFilter.options.add(cat, new StaticMessage(cat.getDisplayName()));
            }
        }

        // Set initial selection from settings
        if (SettingsManager.getInstance().getLastSelectedCategory() != null && !SettingsManager.getInstance().getLastSelectedCategory().isEmpty()) {
            try {
                CommandCategory savedCategory = CommandCategory.valueOf(SettingsManager.getInstance().getLastSelectedCategory());
                categoryFilter.setSelected(savedCategory, new StaticMessage(savedCategory.getDisplayName()));
            } catch (IllegalArgumentException e) {
                // Invalid category in settings, ignore
            }
        }

        // Filter commands when category changes
        categoryFilter.onSelected(event -> {
            CommandCategory selectedCategory = event.value;
            SettingsManager.getInstance().setLastSelectedCategory((selectedCategory != null) ? selectedCategory.name() : "");
            Settings.saveClientSettings();

            // Rebuild command dropdown with filtered commands
            rebuildCommandDropdown(parentForm, startX, contentWidth, selectedCategory);
        });

        parentForm.addComponent(categoryFilter);
        allComponents.add(categoryFilter);
        currentY += 30;

        // Command dropdown with search (all commands or filtered by category)
        CommandCategory initialCategory = null;
        if (SettingsManager.getInstance().getLastSelectedCategory() != null && !SettingsManager.getInstance().getLastSelectedCategory().isEmpty()) {
            try {
                initialCategory = CommandCategory.valueOf(SettingsManager.getInstance().getLastSelectedCategory());
            } catch (IllegalArgumentException e) {
                // Ignore
            }
        }

        List<NecesseCommandMetadata> displayCommands = getFilteredCommands(initialCategory);
        displayCommands.sort((a, b) -> a.getId().compareTo(b.getId()));

        commandDropdown = new SearchableDropdown<>(
            startX + MARGIN, currentY,
            contentWidth, 200, // width, dropdown height
            "Search commands...", // placeholder text
            displayCommands,
            cmd -> cmd.getId(), // display function
            selectedCmd -> {
                if (selectedCmd != null) {
                    loadCommand(selectedCmd);
                }
            }
        );

        commandDropdown.addToForm(parentForm);
        // Note: SearchableDropdown manages its own components, don't add to allComponents
        currentY += 40;

        // Favorites bar (horizontal row of favorite quick-access buttons)
        favoriteButtonsBox = new FormContentBox(
            startX + MARGIN, currentY,
            contentWidth, Constants.CommandCenter.FAVORITES_HEIGHT
        );
        parentForm.addComponent(favoriteButtonsBox);
        allComponents.add(favoriteButtonsBox);
        buildFavoriteButtons();
        currentY += Constants.CommandCenter.FAVORITES_HEIGHT + 10;

        // Command info display (shows selected command's action description)
        commandInfoLabel = new FormLabel(
            "Select a command...",
            WHITE_TEXT_11,
            FormLabel.ALIGN_LEFT,
            startX + MARGIN, currentY, contentWidth - 120 // Leave space for favorite button (100px + margin)
        );
        parentForm.addComponent(commandInfoLabel);
        allComponents.add(commandInfoLabel);

        // Favorite toggle button (checkbox-style: "Add to Favorites" / "Remove from Favorites")
        favoriteToggleButton = new FormTextButton(
            "Add to Favorites",
            startX + width - MARGIN - 100, currentY - 5,
            100, FormInputSize.SIZE_16,
            ButtonColor.BASE
        );
        favoriteToggleButton.onClicked(e -> toggleFavorite());
        favoriteToggleButton.setActive(false);
        parentForm.addComponent(favoriteToggleButton);
        allComponents.add(favoriteToggleButton);

        // Calculate button positions FIRST (fixed at bottom of entire panel)
        // Note: startY is already offset by tab bar, so we need to calculate from original bottom
        int previewHeight = 20;
        int buttonRowHeight = 40;
        int buttonY = height - MARGIN - buttonRowHeight;  // Absolute position from top of panel
        int buttonWidth = (contentWidth - 10) / 3;

        // Reserve space just above the buttons for the read-only command preview label
        int previewY = buttonY - previewHeight - 5; // small gap above buttons

        // Now calculate available space for parameters (between currentY and preview label)
        int availableHeight = previewY - currentY - MARGIN;

        // Parameter area - use scrollable FormContentBox only if content might overflow
        // Most commands have 1-3 parameters, so we have plenty of space (700px total height)
        parameterScrollArea = new FormContentBox(
            startX + MARGIN, currentY,
            contentWidth, availableHeight
        );
        parentForm.addComponent(parameterScrollArea);
        allComponents.add(parameterScrollArea);

        // Read-only command preview (shows the actual chat command string we will send)
        commandPreviewLabel = new FormLabel(
            "",
            WHITE_TEXT_11,
            FormLabel.ALIGN_LEFT,
            startX + MARGIN, previewY, contentWidth
        );
        parentForm.addComponent(commandPreviewLabel);
        allComponents.add(commandPreviewLabel);

        // Action buttons row (anchored at bottom - created after scroll area so they render on top)
        clearButton = new FormTextButton(
            "Clear",
            startX + MARGIN, buttonY,
            buttonWidth, FormInputSize.SIZE_32,
            ButtonColor.BASE
        );
        clearButton.onClicked(e -> clearParameters());
        parentForm.addComponent(clearButton);
        allComponents.add(clearButton);

        executeButton = new FormTextButton(
            "Execute",
            startX + MARGIN + buttonWidth + 5, buttonY,
            buttonWidth, FormInputSize.SIZE_32,
            ButtonColor.BASE
        );
        executeButton.onClicked(e -> executeCommand());
        parentForm.addComponent(executeButton);
        allComponents.add(executeButton);

        backButton = new FormTextButton(
            "Back",
            startX + MARGIN + buttonWidth * 2 + 10, buttonY,
            buttonWidth, FormInputSize.SIZE_32,
            ButtonColor.BASE
        );
        backButton.onClicked(e -> {
            if (onBackCallback != null) {
                onBackCallback.run();
            }
        });
        parentForm.addComponent(backButton);
        allComponents.add(backButton);

        updateButtonStates();
    }

    /**
     * Remove all components from parent form (cleanup)
     */
    public void removeComponents(Form parentForm) {
        // Remove SearchableDropdown first (it manages its own components)
        if (commandDropdown != null) {
            commandDropdown.removeFromForm(parentForm);
        }

        // Remove all other tracked components
        for (FormComponent component : allComponents) {
            parentForm.removeComponent(component);
        }
        allComponents.clear();
    }

    /**
     * Build Mod Settings tab (universal mod config editor)
     */
    private void buildModSettingsTab(Form parentForm, int startX, int startY, int width, int height) {
        // Note: Tab dimensions were removed as unused fields

        int currentY = startY;
        int contentWidth = width - MARGIN * 2;

        // Header
        FormLabel headerLabel = new FormLabel(
            "Mod Settings (Universal In-Game Editor)",
            WHITE_TEXT_14,
            FormLabel.ALIGN_LEFT,
            startX + MARGIN, currentY, contentWidth
        );
        parentForm.addComponent(headerLabel);
        allComponents.add(headerLabel);

        currentY += 30;

        // Scan all mods for ModConfig classes (results are cached by the scanner)
        Map<LoadedMod, List<ModConfigSection>> allModSettings = UniversalModConfigScanner.scanAllMods();
        ModLogger.info("Found %d mods with ModConfig", allModSettings.size());

        if (allModSettings.isEmpty()) {
            FormLabel noSettingsLabel = new FormLabel(
                "No mods with ModConfig found. Check logs for details.",
                WHITE_TEXT_11,
                FormLabel.ALIGN_LEFT,
                startX + MARGIN, currentY, contentWidth
            );
            parentForm.addComponent(noSettingsLabel);
            allComponents.add(noSettingsLabel);

            currentY += 30;

            // Debug info
            FormLabel debugLabel = new FormLabel(
                "Loaded mods: " + ModLoader.getEnabledMods().size(),
                WHITE_TEXT_11,
                FormLabel.ALIGN_LEFT,
                startX + MARGIN, currentY, contentWidth
            );
            parentForm.addComponent(debugLabel);
            allComponents.add(debugLabel);
            return;
        }

        // Create scrollable content area (leave room for bottom Back button
        // and its top divider line so the visual frame stops just above the
        // button row instead of running all the way to the bottom).
        int actionBarHeight = Constants.CommandCenter.ACTION_BAR_HEIGHT;
        int scrollAreaHeight = height - (currentY - startY) - (actionBarHeight + MARGIN);
        FormContentBox scrollArea = new FormContentBox(
            startX, currentY,
            width, scrollAreaHeight
        );
        scrollArea.shouldLimitDrawArea = true;
        parentForm.addComponent(scrollArea);
        allComponents.add(scrollArea);

        int scrollY = 10; // Start with padding

        // Medieval Sim first (if present)
        LoadedMod medievalSim = null;
        for (LoadedMod mod : ModLoader.getEnabledMods()) {
            if (mod.id.equals("medieval.sim")) {
                medievalSim = mod;
                break;
            }
        }

        if (medievalSim != null && allModSettings.containsKey(medievalSim)) {
            scrollY = buildModSection(scrollArea, medievalSim, allModSettings.get(medievalSim),
                                     10, scrollY, contentWidth - 20);
            scrollY += 15; // Spacing between mods
        }

        // Other mods alphabetically
        List<Map.Entry<LoadedMod, List<ModConfigSection>>> otherMods = new ArrayList<>();
        for (Map.Entry<LoadedMod, List<ModConfigSection>> entry : allModSettings.entrySet()) {
            if (!entry.getKey().id.equals("medieval.sim")) {
                otherMods.add(entry);
            }
        }
        otherMods.sort(Comparator.comparing(entry -> entry.getKey().name));

        for (Map.Entry<LoadedMod, List<ModConfigSection>> entry : otherMods) {
            scrollY = buildModSection(scrollArea, entry.getKey(), entry.getValue(),
                                     10, scrollY, contentWidth - 20);
            scrollY += 15; // Spacing between mods
        }

        // Set content box to enable scrolling. We clamp the visual content
        // to stop a bit above the action bar so the bottom divider line does
        // not visually collide with the Back button row.
        int maxContentHeight = scrollAreaHeight - 10;
        int effectiveContentHeight = Math.min(scrollY + 20, maxContentHeight);
        scrollArea.setContentBox(new Rectangle(0, 0, contentWidth - 20, effectiveContentHeight));

        // Back button at bottom to return to Admin Tools main menu
        int backButtonY = height - MARGIN - Constants.CommandCenter.ACTION_BAR_HEIGHT;
        int backButtonWidth = Constants.UI.MIN_BUTTON_WIDTH;
        backButton = new FormTextButton(
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
     * Build Command History tab (last 20 executed commands)
     */
    private void buildCommandHistoryTab(Form parentForm, int startX, int startY, int width, int height) {
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
            // Rebuild entire panel to show empty state
            removeComponents(parentForm);
            buildComponents(parentForm, panelStartX, panelStartY, panelWidth, panelHeight);
        });
        parentForm.addComponent(clearHistoryButton);
        allComponents.add(clearHistoryButton);

        currentY += 35;

        // Calculate available height for history box (from currentY to bottom, leaving room for Back button)
        int actionBarHeight = Constants.CommandCenter.ACTION_BAR_HEIGHT;
        int availableHeight = height - currentY - MARGIN - actionBarHeight;

        // Scrollable history list
        FormContentBox historyBox = new FormContentBox(
            startX + MARGIN, currentY,
            contentWidth, availableHeight
        );
        parentForm.addComponent(historyBox);
        allComponents.add(historyBox);

        if (SettingsManager.getInstance().getCommandHistory() == null || SettingsManager.getInstance().getCommandHistory().isEmpty()) {
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

                // Capture commandString in final variable for lambda
                final String cmdToExecute = commandString;
                reExecuteButton.onClicked(e -> {
                    // Send packet to execute this command
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

        // Back button at bottom to return to Admin Tools main menu
        int backButtonY = height - MARGIN - Constants.CommandCenter.ACTION_BAR_HEIGHT;
        int backButtonWidth = Constants.UI.MIN_BUTTON_WIDTH;
        backButton = new FormTextButton(
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
     * Get filtered commands by category AND world settings.
     *
     * Filtering rules:
     * 1. Filter by world availability (hide cheat commands in survival mode)
     * 2. Filter by category if specified
     */
    private List<NecesseCommandMetadata> getFilteredCommands(CommandCategory category) {
        List<NecesseCommandMetadata> allCommands = new ArrayList<>(NecesseCommandRegistry.getAllCommands());
        List<NecesseCommandMetadata> filtered = new ArrayList<>();

        for (NecesseCommandMetadata cmd : allCommands) {
            // FILTER 1: World settings (hide cheat commands in survival)
            if (!cmd.isAvailableInWorld(client)) {
                continue; // Skip this command (hidden in survival mode)
            }

            // FILTER 2: Category (if specified)
            if (category != null && cmd.getCategory() != category) {
                continue; // Skip (wrong category)
            }

            // Command passed both filters
            filtered.add(cmd);
        }

        return filtered;
    }

    /**
     * Rebuild command dropdown with filtered commands
     */
    private void rebuildCommandDropdown(Form parentForm, int startX, int contentWidth, CommandCategory category) {
        if (commandDropdown == null) {
            return; // Not initialized yet
        }

        // Get filtered commands
        List<NecesseCommandMetadata> filteredCommands = getFilteredCommands(category);
        filteredCommands.sort((a, b) -> a.getId().compareTo(b.getId()));

        // Block dropdown from opening during rebuild
        commandDropdown.beginUpdate();

        // Update the dropdown's items
        commandDropdown.updateItems(filteredCommands);

        // Allow dropdown to open again
        commandDropdown.endUpdate();
    }

    /**
     * Build favorite quick-access buttons
     */
    private void buildFavoriteButtons() {
        // Clear existing
        favoriteButtonsBox.clearComponents();

        if (SettingsManager.getInstance().getFavoriteCommands().isEmpty()) {
            favoriteButtonsBox.addComponent(new FormLabel(
                "No favorites yet. Select a command and click 'Add to Favorites'.",
                WHITE_TEXT_11,
                FormLabel.ALIGN_LEFT,
                5, 10, favoriteButtonsBox.getWidth() - 10
            ));
            return;
        }

        int buttonWidth = 80;
        int spacing = 5;
        int x = spacing;

        for (String favoriteId : SettingsManager.getInstance().getFavoriteCommands()) {
            NecesseCommandMetadata cmd = findCommandById(favoriteId);
            if (cmd != null) {
                FormTextButton favBtn = new FormTextButton(
                    favoriteId,
                    x, 5,
                    buttonWidth, FormInputSize.SIZE_16,
                    ButtonColor.BASE
                );

                // Event handler
                favBtn.onClicked(e -> {
                    commandDropdown.setSelectedItem(cmd);
                    loadCommand(cmd);
                });

                favoriteButtonsBox.addComponent(favBtn);

                x += buttonWidth + spacing;

                // Wrap to next line if needed (future enhancement)
                if (x + buttonWidth > favoriteButtonsBox.getWidth()) {
                    break;
                }
            }
        }

        // Update content box bounds
        favoriteButtonsBox.setContentBox(new Rectangle(
            0, 0,
            favoriteButtonsBox.getWidth(),
            Constants.CommandCenter.FAVORITES_HEIGHT
        ));
    }

    /**
     * Load a command and generate its parameter widgets
     */
    private void loadCommand(NecesseCommandMetadata command) {
        if (command == null) return;

        this.currentCommand = command;
        // Reset command tail to base command id (args will be appended as widgets change)
        this.currentCommandTail = command.getId();

        // Command loaded successfully (debug output removed for cleaner logs)

        // Update info label
        commandInfoLabel.setText(command.getAction());

        // Update favorite toggle button
        boolean isFavorite = SettingsManager.getInstance().getFavoriteCommands().contains(command.getId());
        favoriteToggleButton.setText(isFavorite ? "Remove Favorite" : "Add to Favorites");
        favoriteToggleButton.setActive(true);

        // Clean up old parameter widgets (especially SearchableDropdown widgets)
        cleanupParameterWidgets();

        // Clear old parameter widgets from UI
        parameterScrollArea.clearComponents();
        currentParameterWidgets.clear();

        // Generate new parameter widgets with labels and hints
        List<ParameterMetadata> params = command.getParameters();
        if (params == null || params.isEmpty()) {
            parameterScrollArea.addComponent(new FormLabel(
                "This command has no parameters.",
                WHITE_TEXT_11,
                FormLabel.ALIGN_LEFT,
                10, 10, parameterScrollArea.getWidth() - 20
            ));
        } else {
            int yPos = 10;
            int widgetX = 10;
            int widgetWidth = parameterScrollArea.getWidth() - 20;

            int i = 0;
            while (i < params.size()) {
                ParameterMetadata param = params.get(i);

                // Skip parameters not part of usage (hidden debug params)
                if (!param.isPartOfUsage()) {
                    i++;
                    continue;
                }

                // Check if this is an X/Y coordinate pair (consecutive RELATIVE_INT params with X/Y names)
                boolean isCoordinatePair = false;
                ParameterMetadata nextParam = null;
                if (i + 1 < params.size()) {
                    nextParam = params.get(i + 1);
                    isCoordinatePair = isXYCoordinatePair(param, nextParam);
                }

                if (isCoordinatePair) {
                    // Handle coordinate pair with CoordinatePairWidget
                    addCoordinatePairWidget(param, nextParam, widgetX, yPos, parameterScrollArea);
                    yPos += 80; // Extra space for combined widget
                    i += 2; // Skip next parameter (Y coordinate)
                    continue;
                }

                // Single parameter - standard widget creation
                // Parameter label with better required/optional indicators
                String labelText = param.getDisplayName();
                if (param.isRequired()) {
                    labelText += " (Required)";
                } else {
                    labelText += " (Optional)";
                }

                FormLabel paramLabel = new FormLabel(
                    labelText,
                    WHITE_TEXT_14,
                    FormLabel.ALIGN_LEFT,
                    widgetX, yPos, widgetWidth
                );
                parameterScrollArea.addComponent(paramLabel);
                yPos += 20;

                // Parameter hint (gray text, 11pt)
                String hintText = getParameterHint(param);
                FormLabel hintLabel = new FormLabel(
                    hintText,
                    WHITE_TEXT_11,
                    FormLabel.ALIGN_LEFT,
                    widgetX, yPos, widgetWidth
                );
                parameterScrollArea.addComponent(hintLabel);
                // Explicitly separate description from inputs so the player
                // can visually distinguish where the explanation ends and the
                // interactive controls begin.
                yPos += 28;

                // Determine if this widget needs parent-form coordinates (SearchableDropdown widgets)
                // SearchableDropdown widgets must be positioned relative to parent form, not scroll area
                boolean needsParentFormCoordinates = (
                    param.getHandlerType() == ParameterMetadata.ParameterHandlerType.ITEM ||
                    param.getHandlerType() == ParameterMetadata.ParameterHandlerType.BUFF ||
                    param.getHandlerType() == ParameterMetadata.ParameterHandlerType.ENCHANTMENT ||
                    param.getHandlerType() == ParameterMetadata.ParameterHandlerType.BIOME ||
                    param.getHandlerType() == ParameterMetadata.ParameterHandlerType.TILE
                );

                // Calculate widget coordinates with standardized positioning
                int actualWidgetX, actualWidgetY;
                if (needsParentFormCoordinates) {
                    // SearchableDropdown widgets need absolute positioning relative to parent form
                    // These widgets create their own popup components that must be positioned correctly
                    actualWidgetX = parameterScrollArea.getX() + widgetX;
                    actualWidgetY = parameterScrollArea.getY() + yPos;
                } else {
                    // Standard widgets use scroll-area-relative coordinates
                    actualWidgetX = widgetX;
                    actualWidgetY = yPos;
                }

                // Decide whether this parameter can use engine-driven autocomplete.
                ParameterWidget widget;
                boolean useEngineAutocomplete = isAutocompleteEligibleForDropdown(param);

                List<String> autocompleteOptions = null;
                if (useEngineAutocomplete) {
                    autocompleteOptions = getAutoCompleteOptionsForCurrentArgs();
                }

                if (autocompleteOptions != null && !autocompleteOptions.isEmpty()) {
                    // Use autocomplete-backed dropdown for this parameter.
                    widget = new AutocompleteDropdownWidget(
                        param,
                        actualWidgetX,
                        actualWidgetY,
                        autocompleteOptions
                    );
                } else {
                    // Fallback to standard widget factory (existing behavior).
                    widget = ParameterWidgetFactory.createWidget(param, actualWidgetX, actualWidgetY, client, command.getId());
                }

                // Add callback for real-time validation + dynamic form rebuild.
                widget.setOnValueChanged(() -> {
                    widget.validate(); // Ensure validation runs
                    cacheCurrentParameterValues(); // Remember current values before rebuild
                    // Rebuild the parameter form for this command so later fields/options
                    // refresh when earlier arguments change.
                    loadCommand(command);
                });

                // Restore cached value if exists
                Map<String, String> cachedValues = commandParameterCache.get(command.getId());
                if (cachedValues != null && cachedValues.containsKey(param.getName())) {
                    widget.setValue(cachedValues.get(param.getName()));
                }

                // Add widget component to scroll area
                if (widget instanceof MultiChoiceWidget) {
                    // MultiChoiceWidget has dropdown + selected sub-widget. Some
                    // sub-widgets (like PlayerDropdownWidget) expose multiple
                    // components, so we treat the selected widget specially to
                    // ensure all of its visible parts are added on first render.
                    MultiChoiceWidget multiWidget = (MultiChoiceWidget) widget;

                    // Set parent form reference for dynamic sub-widget swapping
                    multiWidget.setParentForm(parameterScrollArea);

                    // Add dropdown to scroll area
                    parameterScrollArea.addComponent(multiWidget.getComponent());

                    ParameterWidget selected = multiWidget.getSelectedWidget();
                    if (selected instanceof PlayerDropdownWidget) {
                        PlayerDropdownWidget playerWidget = (PlayerDropdownWidget) selected;
                        parameterScrollArea.addComponent(playerWidget.getTextInput());
                        parameterScrollArea.addComponent(playerWidget.getDropdown());
                        yPos += 25; // extra space for stacked components
                    } else {
                        // Add currently selected sub-widget's primary component
                        FormComponent subComponent = multiWidget.getSelectedSubComponent();
                        if (subComponent != null) {
                            parameterScrollArea.addComponent(subComponent);
                        }
                    }

                    // Note: Widget will auto-swap sub-components when dropdown selection changes (handled in tick())
                } else if (widget instanceof PlayerDropdownWidget) {
                    // PlayerDropdownWidget has TWO components: text input + dropdown
                    PlayerDropdownWidget playerWidget = (PlayerDropdownWidget) widget;
                    parameterScrollArea.addComponent(playerWidget.getTextInput()); // Text input first
                    parameterScrollArea.addComponent(playerWidget.getDropdown()); // Dropdown second
                    yPos += 25; // Extra space for the second component
                // ❌ REMOVED: Creative widget layout handling (ItemDropdownWidget, BuffDropdownWidget,
                // EnchantmentDropdownWidget, BiomeDropdownWidget, TileDropdownWidget) - widgets deleted
                } else if (widget instanceof RelativeIntInputWidget) {
                    // RelativeIntInputWidget has THREE components: text input + two buttons
                    RelativeIntInputWidget coordWidget = (RelativeIntInputWidget) widget;
                    coordWidget.setClient(client); // Pass client for world-click and current position
                    parameterScrollArea.addComponent(coordWidget.getComponent()); // Text input
                    parameterScrollArea.addComponent(coordWidget.getClickWorldButton()); // "Click World" button
                    parameterScrollArea.addComponent(coordWidget.getUseCurrentButton()); // "Use Current" button
                } else {
                    // Standard single-component widgets
                    parameterScrollArea.addComponent(widget.getComponent());
                }

                // Note: Visual validation feedback could be added here in the future
                // For now, validation feedback is provided through button states and error messages

                currentParameterWidgets.add(widget);

                yPos += 45; // Space between parameters
                i++; // Move to next parameter
            }
        }

        // Update scroll area content bounds
        int contentHeight = Math.max(
            params != null ? params.size() * 85 + 20 : 50,
            parameterScrollArea.getHeight()
        );
        parameterScrollArea.setContentBox(new Rectangle(
            0, 0,
            parameterScrollArea.getWidth(),
            contentHeight
        ));

        // Keep command preview in sync with initial/cached widget values
        updateCommandPreviewFromWidgets();
        updateButtonStates();
    }

    /**
     * Rebuild currentCommandTail from current widgets and update the preview label.
     * This keeps the GUI form and the underlying chat command string in sync.
     */
    private void updateCommandPreviewFromWidgets() {
        if (currentCommand == null) {
            currentCommandTail = "";
            if (commandPreviewLabel != null) {
                commandPreviewLabel.setText("Command: /");
            }
            return;
        }

        String[] args = buildArgsArrayFromWidgets();
        try {
            String fullCommand = currentCommand.buildCommandString(args);
            // Strip leading "/" so currentCommandTail stays in the "id + args" form
            if (fullCommand.startsWith("/")) {
                currentCommandTail = fullCommand.substring(1);
            } else {
                currentCommandTail = fullCommand;
            }
        } catch (IllegalArgumentException e) {
            // Required parameter missing or other validation error: keep tail at base id
            currentCommandTail = currentCommand.getId();
        }
        if (commandPreviewLabel != null) {
            commandPreviewLabel.setText("Command: /" + currentCommandTail);
        }
    }

    /**
     * Normalize a raw widget value: trim whitespace and treat empty strings as null.
     */
    private String normalizeWidgetValue(String value) {
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * Build an argument array from the current widgets, in the same order they appear in
     * the form. Empty values are skipped so we only pass meaningful args to the engine.
     */
    private String[] buildArgsArrayFromWidgets() {
        if (currentParameterWidgets == null || currentParameterWidgets.isEmpty()) {
            return new String[0];
        }

        List<String> args = new ArrayList<>();
        for (ParameterWidget widget : currentParameterWidgets) {
            String value = normalizeWidgetValue(widget.getValue());
            if (value != null) {
                args.add(value);
            }
        }

        return args.toArray(new String[0]);
    }

	    /**
	     * Cache the current parameter widget values for the active command so that when
	     * we rebuild the form (for example after autocomplete or branch changes) we can
	     * restore values that are still valid on the new path.
	     */
	    private void cacheCurrentParameterValues() {
	        if (currentCommand == null || currentParameterWidgets == null) {
	            return;
	        }

	        Map<String, String> values = new HashMap<>();
	        for (ParameterWidget widget : currentParameterWidgets) {
	            if (widget == null || widget.getParameter() == null) {
	                continue;
	            }
	            String value = widget.getValue();
	            if (value != null) {
	                value = value.trim();
	            }
	            if (value != null && !value.isEmpty()) {
	                values.put(widget.getParameter().getName(), value);
	            }
	        }

	        if (values.isEmpty()) {
	            commandParameterCache.remove(currentCommand.getId());
	        } else {
	            commandParameterCache.put(currentCommand.getId(), values);
	        }
	    }


    /**
     * Determine if this parameter is a good candidate for an autocomplete-driven dropdown.
     *
     * We limit this to string-like handler types where we currently show a plain text box,
     * so we don't override specialized widgets like coordinate pickers or player dropdowns.
     */
    private boolean isAutocompleteEligibleForDropdown(ParameterMetadata param) {
        ParameterMetadata.ParameterHandlerType type = param.getHandlerType();
        switch (type) {
            case STRING:
            case PRESET_STRING:
            case CMD_NAME:
            case UNBAN:
            case STORED_PLAYER:
            case LEVEL_IDENTIFIER:
                return true;
            default:
                return false;
        }
    }

    /**
     * Ask Necesse's command system for autocomplete options based on the current argument
     * list and backing CmdParameter[] for the active command.
     */
    private List<String> getAutoCompleteOptionsForCurrentArgs() {
        if (currentCommand == null || client == null) {
            return new ArrayList<>();
        }

        String[] args = buildArgsArrayFromWidgets();
        return currentCommand.getAutocompleteOptions(client, args);
    }



    /**
     * Check if two consecutive parameters form an X/Y coordinate pair
     */
    private boolean isXYCoordinatePair(ParameterMetadata param1, ParameterMetadata param2) {
        // Both must be RELATIVE_INT type
        if (param1.getHandlerType() != ParameterMetadata.ParameterHandlerType.RELATIVE_INT ||
            param2.getHandlerType() != ParameterMetadata.ParameterHandlerType.RELATIVE_INT) {
            return false;
        }

        // Check if names suggest X and Y coordinates
        String name1 = param1.getName().toLowerCase();
        String name2 = param2.getName().toLowerCase();

        // Pattern 1: "tileX" and "tileY"
        if ((name1.contains("x") && name2.contains("y")) ||
            (name1.equals("tilex") && name2.equals("tiley")) ||
            (name1.equals("tilexoffset") && name2.equals("tileyoffset"))) {
            return true;
        }

        return false;
    }

    /**
     * Add a coordinate pair widget for X/Y parameters
     */
    private void addCoordinatePairWidget(ParameterMetadata xParam, ParameterMetadata yParam,
                                        int widgetX, int yPos, FormContentBox scrollArea) {
        // Combined label for both coordinates
        String labelText = "Coordinates (X, Y)" + (xParam.isRequired() || yParam.isRequired() ? " *" : "");
        FormLabel paramLabel = new FormLabel(
            labelText,
            WHITE_TEXT_14,
            FormLabel.ALIGN_LEFT,
            widgetX, yPos,
            scrollArea.getWidth() - 20
        );
        scrollArea.addComponent(paramLabel);
        yPos += 20;

        // Hint text
        FormLabel hintLabel = new FormLabel(
            "Enter coordinates or use buttons to select from map (supports %+N relative syntax)",
            WHITE_TEXT_11,
            FormLabel.ALIGN_LEFT,
            widgetX, yPos,
            scrollArea.getWidth() - 20
        );
        scrollArea.addComponent(hintLabel);
        yPos += 18;

        // Create coordinate pair widget
        medievalsim.commandcenter.wrapper.widgets.CoordinatePairWidget coordWidget =
            new medievalsim.commandcenter.wrapper.widgets.CoordinatePairWidget(
                widgetX, yPos,
                xParam.getName(), yParam.getName(),
                xParam.isRequired(), yParam.isRequired()
            );
        coordWidget.setClient(client);

        // Add all components to scroll area
        for (necesse.gfx.forms.components.FormComponent component : coordWidget.getComponents()) {
            scrollArea.addComponent(component);
        }

        // Create wrapper widgets for the coordinate pair (for getValue() integration)
        CoordinatePairWrapperWidget xWrapper = new CoordinatePairWrapperWidget(xParam, coordWidget, true);
        CoordinatePairWrapperWidget yWrapper = new CoordinatePairWrapperWidget(yParam, coordWidget, false);

        // Restore cached values
        Map<String, String> cachedValues = commandParameterCache.get(currentCommand.getId());
        if (cachedValues != null) {
            if (cachedValues.containsKey(xParam.getName())) {
                coordWidget.setXValue(cachedValues.get(xParam.getName()));
            }
            if (cachedValues.containsKey(yParam.getName())) {
                coordWidget.setYValue(cachedValues.get(yParam.getName()));
            }
        }

        // Add to current widgets list (both X and Y wrappers)
        currentParameterWidgets.add(xWrapper);
        currentParameterWidgets.add(yWrapper);
    }

    /**
     * Get context-aware hint text for a parameter
     */
    private String getParameterHint(ParameterMetadata param) {
        // Use the parameter handler type enum
        switch (param.getHandlerType()) {
            case SERVER_CLIENT:
                return "Select a player or enter username (use 'self' for yourself)";
            case ITEM:
                return "Enter an item ID (e.g., woodsword) or search below";
            case BUFF:
                return "Enter a buff ID (e.g., fire) or search below";
            case RELATIVE_INT:
                return "Enter a number (use %+10 or %-5 for relative to current position)";
            case INT:
                String intDefault = getParameterDefaultHint(param);
                if (param.isOptional() && intDefault != null) {
                    return "Enter a whole number (e.g., 100) - Default: " + intDefault;
                }
                return param.isOptional() ?
                    "Enter a whole number (e.g., 100) - Leave empty to use default" :
                    "Enter a whole number (e.g., 100) - Required";
            case FLOAT:
                String floatDefault = getParameterDefaultHint(param);
                if (param.isOptional() && floatDefault != null) {
                    return "Enter a decimal number (e.g., 1.5) - Default: " + floatDefault;
                }
                return param.isOptional() ?
                    "Enter a decimal number (e.g., 1.5) - Leave empty to use default" :
                    "Enter a decimal number (e.g., 1.5) - Required";
            case BOOL:
                // Show current world setting value if available
                String currentValue = getCurrentBooleanValue(param.getName());
                if (currentValue != null) {
                    return "Current value: " + currentValue + " - Toggle to change";
                }
                return "Click checkbox to enable, uncheck to disable";
            case STRING:
                if (param.hasPresets()) {
                    return "Select from dropdown or enter text manually";
                }
                String stringDefault = getParameterDefaultHint(param);
                if (param.isOptional() && stringDefault != null) {
                    return "Enter text - Default: \"" + stringDefault + "\"";
                }
                return param.isOptional() ? "Enter text - Leave empty to use default" : "Enter text - Required";
            case MULTI:
                return "Select one option from the dropdown";
            case ENUM:
                return "Select a value from the dropdown";
            case TEAM:
                return "Enter team name (or select if dropdown available)";
            case UNBAN:
            case STORED_PLAYER:
                return "Enter player name (supports offline players)";
            default:
                return param.isOptional() ?
                    "Enter a value for " + param.getName() + " - Leave empty to use default" :
                    "Enter a value for " + param.getName() + " - Required";
        }
    }

    /**
     * Extract default value hint from parameter metadata for display purposes
     */
    private String getParameterDefaultHint(ParameterMetadata param) {
        if (param == null || !param.isOptional()) {
            return null; // Required parameters don't need default hints
        }

        // Try to get common default values by parameter name patterns
        String name = param.getName().toLowerCase();

        // Common parameter name patterns with known defaults
        switch (name) {
            case "amount":
            case "count":
            case "quantity":
                return "1";
            case "damage":
            case "health":
            case "durability":
                return "100";
            case "radius":
            case "range":
            case "distance":
                return "10";
            case "speed":
            case "velocity":
                return "1.0";
            case "time":
            case "duration":
            case "delay":
                return "60 seconds";
            case "level":
            case "tier":
                return "1";
            case "permission":
            case "permlevel":
                return "PLAYER";
            case "world":
            case "worldname":
                return "Current World";
            case "team":
                return "No Team";
            default:
                // Try handler-type specific defaults
                switch (param.getHandlerType()) {
                    case INT:
                        return "0";
                    case FLOAT:
                        return "0.0";
                    case BOOL:
                        return "false";
                    case STRING:
                        return "empty";
                    default:
                        return null;
                }
        }
    }

    /**
     * Get current boolean value from world settings for context
     */
    private String getCurrentBooleanValue(String paramName) {
        if (client == null || client.worldSettings == null) {
            return null;
        }

        necesse.engine.world.WorldSettings settings = client.worldSettings;

        // Map parameter names to world settings
        // This helps users understand what the command will change
        try {
            switch (paramName.toLowerCase()) {
                case "pausewhenempty":
                case "pause":
                    return "Not directly exposed"; // Necesse doesn't expose this via worldSettings
                case "creative":
                case "creativemode":
                    return settings.creativeMode ? "Enabled (Creative)" : "Disabled (Survival)";
                case "cheats":
                case "allowcheats":
                    return settings.allowCheats ? "Enabled" : "Disabled";
                case "hunger":
                case "playerhunger":
                    return settings.playerHunger ? "Enabled" : "Disabled";
                case "mobspawns":
                case "disablemobspawns":
                    return settings.disableMobSpawns ? "Disabled" : "Enabled";
                case "survivalmode":
                case "survival":
                    return settings.survivalMode ? "Enabled" : "Disabled";
                case "pvp":
                case "forcedpvp":
                    return settings.forcedPvP ? "Forced" : "Optional";
                case "mobai":
                case "disablemobai":
                    return settings.disableMobAI ? "Disabled" : "Enabled";
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Clear all parameter input values
     */
    private void clearParameters() {
        if (currentCommand == null) return;

        for (ParameterWidget widget : currentParameterWidgets) {
            widget.setValue("");
        }

        // Clear cache for this command
        commandParameterCache.remove(currentCommand.getId());

        // Reset command preview and button states to base command id
        updateCommandPreviewFromWidgets();
        updateButtonStates();
    }

    /**
     * Cleanup parameter widgets, especially SearchableDropdown-based widgets.
     * Must be called before clearing currentParameterWidgets list.
     */
    private void cleanupParameterWidgets() {
        if (parentForm == null) return;

        for (ParameterWidget widget : currentParameterWidgets) {
            // ❌ REMOVED: SearchableDropdown-based widget cleanup (ItemDropdownWidget, BuffDropdownWidget,
            // EnchantmentDropdownWidget, BiomeDropdownWidget, TileDropdownWidget) - widgets deleted

            // RelativeIntInputWidget: Stop any active world-click selection
            if (widget instanceof RelativeIntInputWidget) {
                medievalsim.commandcenter.worldclick.WorldClickHandler handler =
                    medievalsim.commandcenter.worldclick.WorldClickHandler.getInstance();
                if (handler.isActive()) {
                    handler.stopSelection();
                    medievalsim.commandcenter.worldclick.WorldClickIntegration.stopIntegration();
                }
            }
            // Other widgets don't need special cleanup - they're removed via parameterScrollArea.clearComponents()
        }
    }

    /**
     * Execute the command (build command string from currentCommandTail and send packet)
     */
    private void executeCommand() {
        if (currentCommand == null) return;

        try {
            // Ensure currentCommandTail & preview are up to date with widget values
            updateCommandPreviewFromWidgets();

            if (currentCommandTail == null || currentCommandTail.trim().isEmpty()) {
                return; // Nothing to execute
            }

            String finalCommand = "/" + currentCommandTail.trim();

            // Send directly to Necesse - let it handle validation
            client.network.sendPacket(new PacketExecuteCommand(finalCommand));

        } catch (Exception e) {
            ModLogger.error("Error building command: " + e.getMessage());
            client.chat.addMessage("§c[Command Center] Error: " + e.getMessage());
        }
    }

    /**
     * Get parameter value by name from current widgets.
     */

    /**
     * Toggle favorite status for current command
     */
    private void toggleFavorite() {
        if (currentCommand == null) return;

        String cmdId = currentCommand.getId();

        if (SettingsManager.getInstance().getFavoriteCommands().contains(cmdId)) {
            // Remove
            SettingsManager.getInstance().getFavoriteCommands().remove(cmdId);
            favoriteToggleButton.setText("Add to Favorites");
        } else {
            // Add (enforce max limit)
            int maxFavorites = ModConfig.CommandCenter.maxFavorites;
            if (SettingsManager.getInstance().getFavoriteCommands().size() >= maxFavorites) {
                ModLogger.debug("Max favorites reached (%d)", maxFavorites);
                return;
            }
            SettingsManager.getInstance().getFavoriteCommands().add(cmdId);
            favoriteToggleButton.setText("Remove Favorite");
        }

        // Save settings
        Settings.saveClientSettings();

        // Rebuild favorites bar
        buildFavoriteButtons();
    }

    /**
     * Update button enabled states.
     * Execute button is disabled if required parameters are missing or invalid.
     */
    private void updateButtonStates() {
        boolean hasCommand = currentCommand != null;

        // Check if all required parameters have values
        boolean canExecute = hasCommand;
        if (hasCommand && !currentParameterWidgets.isEmpty()) {
            for (ParameterWidget widget : currentParameterWidgets) {
                // Required parameters must have non-empty values
                if (widget.getParameter().isRequired()) {
                    String value = normalizeWidgetValue(widget.getValue());
                    if (value == null) {
                        canExecute = false;
                        break;
                    }
                }
            }
        }

        // Null safety: Only update buttons if they're initialized
        if (clearButton != null) {
            clearButton.setActive(hasCommand && !currentParameterWidgets.isEmpty());
        }
        if (executeButton != null) {
            executeButton.setActive(canExecute);
        }
    }

    /**
     * Find command by ID
     */
    private NecesseCommandMetadata findCommandById(String id) {
        Collection<NecesseCommandMetadata> allCommands = NecesseCommandRegistry.getAllCommands();
        for (NecesseCommandMetadata cmd : allCommands) {
            if (cmd.getId().equals(id)) {
                return cmd;
            }
        }
        return null;
    }

    // ===== MOD SETTINGS UI BUILDERS =====

    /**
     * Build a section for one mod's settings
     * @return new Y position after this section
     */
    private int buildModSection(FormContentBox scrollArea, LoadedMod mod,
                                List<ModConfigSection> sections,
                                int x, int y, int width) {
        int currentY = y;

        SettingsManager settings = SettingsManager.getInstance();
        // Default to collapsed when no explicit preference has been stored
        // yet. This keeps the initial Mod Settings view tidy while still
        // honoring any previously saved expansion state.
        boolean isExpanded = settings.isModExpanded(mod.id);

        // Mod header (clickable to expand/collapse) - use ASCII icons for compatibility
        String expandIcon = isExpanded ? "[-]" : "[+]";
        String modHeader = expandIcon + " " + mod.name + " v" + mod.version;

        FormTextButton modHeaderButton = new FormTextButton(
            modHeader,
            "Click to " + (isExpanded ? "collapse" : "expand"),
            x, currentY, width,
            FormInputSize.SIZE_24,
            ButtonColor.BASE
        );
        modHeaderButton.onClicked(e -> {
            boolean newState = !settings.isModExpanded(mod.id);
            settings.setModExpanded(mod.id, newState);
            Settings.saveClientSettings();
            // Rebuild the entire panel to ensure clean layout (no overlay)
            removeComponents(parentForm);
            buildComponents(parentForm, panelStartX, panelStartY, panelWidth, panelHeight);
        });
        scrollArea.addComponent(modHeaderButton);
        currentY += 30;

        // Build each section (only if expanded)
        if (isExpanded) {
            for (ModConfigSection section : sections) {
                currentY = buildConfigSection(scrollArea, mod, section, x + 10, currentY, width - 10);
                currentY += 5; // Spacing between sections
            }
        }

        // Add divider line after mod section
        FormBreakLine divider = new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, x, currentY, width, true);
        divider.color = new Color(100, 100, 100); // Gray divider
        scrollArea.addComponent(divider);
        currentY += 10;

        return currentY;
    }

    /**
     * Build a config section (e.g., "Build Mode Settings", "Zone Settings")
     * @return new Y position after this section
     */
    private int buildConfigSection(FormContentBox scrollArea, LoadedMod mod,
                                   ModConfigSection section,
                                   int x, int y, int width) {
        int currentY = y;

        SettingsManager settings = SettingsManager.getInstance();
        // Sections follow the same rule as mods: they start collapsed until
        // the player explicitly expands them, so large configs do not
        // overwhelm the initial view.
        boolean isExpanded = settings.isSectionExpanded(mod.id, section.getName());

        // Section header (clickable to expand/collapse) - use ASCII icons for compatibility
        String expandIcon = isExpanded ? "[-]" : "[+]";
        String sectionHeader = expandIcon + " " + section.getName();
        if (!section.getDescription().isEmpty()) {
            sectionHeader += " - " + section.getDescription();
        }

        FormTextButton sectionHeaderButton = new FormTextButton(
            sectionHeader,
            "Click to " + (isExpanded ? "collapse" : "expand"),
            x, currentY, width,
            FormInputSize.SIZE_16,
            ButtonColor.BASE
        );
        sectionHeaderButton.onClicked(e -> {
            boolean newState = !settings.isSectionExpanded(mod.id, section.getName());
            settings.setSectionExpanded(mod.id, section.getName(), newState);
            Settings.saveClientSettings();
            // Rebuild the entire panel to ensure clean layout (no overlay)
            removeComponents(parentForm);
            buildComponents(parentForm, panelStartX, panelStartY, panelWidth, panelHeight);
        });
        scrollArea.addComponent(sectionHeaderButton);
        currentY += 22;

        // Build each setting (only if expanded)
        if (isExpanded) {
            for (ConfigurableSetting setting : section.getSettings()) {
                currentY = buildSettingWidget(scrollArea, setting, x + 10, currentY, width - 10);
                currentY += 5; // Spacing between settings
            }
        }

        return currentY;
    }

    /**
     * Build a widget for a single setting
     * @return new Y position after this widget
     */
    private int buildSettingWidget(FormContentBox scrollArea, ConfigurableSetting setting,
                                   int x, int y, int width) {
        int currentY = y;

        // Setting label (truncate long descriptions)
        String labelText = setting.getDisplayName();
        String fullDescription = setting.getDescription();
        boolean hasLongDescription = false;

        if (!fullDescription.isEmpty()) {
            String desc = fullDescription;
            // Truncate if too long
            if (desc.length() > 60) {
                desc = desc.substring(0, 57) + "...";
                hasLongDescription = true;
            }
            labelText += ": " + desc;
        }

        FormLabel settingLabel = new FormLabel(
            labelText,
            WHITE_TEXT_11,
            FormLabel.ALIGN_LEFT,
            x, currentY, width - 160
        );
        scrollArea.addComponent(settingLabel);

        // Add tooltip for full description if truncated
        if (hasLongDescription) {
            FormMouseHover tooltip = new FormMouseHover(x, currentY, width - 160, 16) {
                @Override
                public GameTooltips getTooltips(PlayerMob perspective) {
                    return new StringTooltips(fullDescription, 400);
                }
            };
            scrollArea.addComponent(tooltip);
        }

        // Input widget (right-aligned, with space for reset button)
        int resetButtonWidth = 40;
        int inputX = x + width - 150 - resetButtonWidth - 5;
        int inputWidth = 140;
        int resetButtonX = x + width - resetButtonWidth;

        switch (setting.getType()) {
            case INTEGER:
            case LONG:
                FormTextInput intInput = new FormTextInput(
                    inputX, currentY,
                    FormInputSize.SIZE_16,
                    inputWidth, 200, 20
                );
                intInput.setText(String.valueOf(setting.getValue()));
                intInput.onSubmit(e -> {
                    try {
                        if (setting.getType() == SettingType.INTEGER) {
                            int value = Integer.parseInt(intInput.getText());
                            setting.setIntValue(value);
                        } else {
                            long value = Long.parseLong(intInput.getText());
                            setting.setLongValue(value);
                        }
                        Settings.saveClientSettings();
                        ModLogger.info("Updated %s to %s", setting.getFieldName(), intInput.getText());
                    } catch (NumberFormatException ex) {
                        ModLogger.warn("Invalid number: %s", intInput.getText());
                        intInput.setText(String.valueOf(setting.getValue()));
                    }
                });
                scrollArea.addComponent(intInput);

                // Reset button
                FormTextButton resetButton = new FormTextButton(
                    "↺",
                    "Reset to default: " + setting.getDefaultValue(),
                    resetButtonX, currentY, resetButtonWidth,
                    FormInputSize.SIZE_16,
                    ButtonColor.BASE
                );
                resetButton.onClicked(e -> {
                    setting.resetToDefault();
                    intInput.setText(String.valueOf(setting.getValue()));
                    Settings.saveClientSettings();
                    ModLogger.info("Reset %s to default: %s", setting.getFieldName(), setting.getValue());
                });
                scrollArea.addComponent(resetButton);
                break;

            case FLOAT:
                FormTextInput floatInput = new FormTextInput(
                    inputX, currentY,
                    FormInputSize.SIZE_16,
                    inputWidth, 200, 20
                );
                floatInput.setText(String.valueOf(setting.getValue()));
                floatInput.onSubmit(e -> {
                    try {
                        float value = Float.parseFloat(floatInput.getText());
                        setting.setFloatValue(value);
                        Settings.saveClientSettings();
                        ModLogger.info("Updated %s to %s", setting.getFieldName(), floatInput.getText());
                    } catch (NumberFormatException ex) {
                        ModLogger.warn("Invalid number: %s", floatInput.getText());
                        floatInput.setText(String.valueOf(setting.getValue()));
                    }
                });
                scrollArea.addComponent(floatInput);

                // Reset button
                FormTextButton floatResetButton = new FormTextButton(
                    "↺",
                    "Reset to default: " + setting.getDefaultValue(),
                    resetButtonX, currentY, resetButtonWidth,
                    FormInputSize.SIZE_16,
                    ButtonColor.BASE
                );
                floatResetButton.onClicked(e -> {
                    setting.resetToDefault();
                    floatInput.setText(String.valueOf(setting.getValue()));
                    Settings.saveClientSettings();
                    ModLogger.info("Reset %s to default: %s", setting.getFieldName(), setting.getValue());
                });
                scrollArea.addComponent(floatResetButton);
                break;

            case BOOLEAN:
                FormCheckBox checkbox = new FormCheckBox(
                    setting.getDisplayName(),
                    inputX, currentY,
                    inputWidth, setting.getBooleanValue()
                );
                checkbox.onClicked(e -> {
                    setting.setBooleanValue(checkbox.checked);
                    Settings.saveClientSettings();
                    ModLogger.info("Updated %s to %s", setting.getFieldName(), checkbox.checked);
                });
                scrollArea.addComponent(checkbox);

                // Reset button
                FormTextButton boolResetButton = new FormTextButton(
                    "↺",
                    "Reset to default: " + setting.getDefaultValue(),
                    resetButtonX, currentY, resetButtonWidth,
                    FormInputSize.SIZE_16,
                    ButtonColor.BASE
                );
                boolResetButton.onClicked(e -> {
                    setting.resetToDefault();
                    checkbox.checked = setting.getBooleanValue();
                    Settings.saveClientSettings();
                    ModLogger.info("Reset %s to default: %s", setting.getFieldName(), setting.getValue());
                });
                scrollArea.addComponent(boolResetButton);
                break;

            case STRING:
                FormTextInput stringInput = new FormTextInput(
                    inputX, currentY,
                    FormInputSize.SIZE_16,
                    inputWidth, 200, 100
                );
                stringInput.setText(setting.getStringValue());
                stringInput.onSubmit(e -> {
                    setting.setStringValue(stringInput.getText());
                    Settings.saveClientSettings();
                    ModLogger.info("Updated %s to %s", setting.getFieldName(), stringInput.getText());
                });
                scrollArea.addComponent(stringInput);

                // Reset button
                FormTextButton stringResetButton = new FormTextButton(
                    "↺",
                    "Reset to default: " + setting.getDefaultValue(),
                    resetButtonX, currentY, resetButtonWidth,
                    FormInputSize.SIZE_16,
                    ButtonColor.BASE
                );
                stringResetButton.onClicked(e -> {
                    setting.resetToDefault();
                    stringInput.setText(setting.getStringValue());
                    Settings.saveClientSettings();
                    ModLogger.info("Reset %s to default: %s", setting.getFieldName(), setting.getValue());
                });
                scrollArea.addComponent(stringResetButton);
                break;

            case ENUM:
                // TODO: Implement enum dropdown when needed
                FormLabel enumLabel = new FormLabel(
                    "ENUM (not yet supported)",
                    WHITE_TEXT_11,
                    FormLabel.ALIGN_LEFT,
                    inputX, currentY, inputWidth
                );
                scrollArea.addComponent(enumLabel);
                break;
        }

        currentY += 30;
        return currentY;
    }

}
