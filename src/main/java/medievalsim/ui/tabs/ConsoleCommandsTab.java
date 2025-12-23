package medievalsim.ui.tabs;

import medievalsim.commandcenter.domain.CommandCategory;
import medievalsim.commandcenter.wrapper.NecesseCommandMetadata;
import medievalsim.commandcenter.wrapper.NecesseCommandRegistry;
import medievalsim.commandcenter.wrapper.ParameterMetadata;
import medievalsim.commandcenter.wrapper.widgets.CheckboxWidget;
import medievalsim.commandcenter.wrapper.widgets.DropdownWidget;
import medievalsim.commandcenter.wrapper.widgets.MultiChoiceWidget;
import medievalsim.commandcenter.wrapper.widgets.NumberInputWidget;
import medievalsim.commandcenter.wrapper.widgets.ParameterWidget;
import medievalsim.commandcenter.wrapper.widgets.PlayerDropdownWidget;
import medievalsim.commandcenter.wrapper.widgets.RelativeIntInputWidget;
import medievalsim.commandcenter.wrapper.widgets.TextInputWidget;
import medievalsim.config.ModConfig;
import medievalsim.config.SettingsManager;
import medievalsim.packets.PacketExecuteCommand;
import medievalsim.ui.helpers.SearchableDropdown;
import medievalsim.ui.helpers.ParameterFormBuilder;
import medievalsim.util.Constants;
import medievalsim.util.ModLogger;
import necesse.engine.Settings;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.client.Client;
import necesse.gfx.GameColor;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormButton;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormDropdownSelectionButton;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Console Commands tab - handles command selection, parameter forms, and execution.
 * Extracted from CommandCenterPanel to improve maintainability.
 */
public class ConsoleCommandsTab {

    // Font options
    private static final FontOptions WHITE_TEXT_11 = new FontOptions(11).color(Color.WHITE);
    private static final int MARGIN = 10;

    private final Client client;
    private final Runnable onBackCallback;
    private final ParameterFormBuilder formBuilder;

    // UI Components
    private SearchableDropdown<NecesseCommandMetadata> commandDropdown;
    private FormDropdownSelectionButton<CommandCategory> categoryFilter;
    private FormContentBox favoriteButtonsBox;
    private FormLabel commandInfoLabel;
    private FormLabel commandPreviewLabel;
    private FormTextButton favoriteToggleButton;
    private FormContentBox parameterScrollArea;
    private FormTextButton clearButton;
    private FormTextButton executeButton;
    private FormTextButton backButton;

    // State
    private NecesseCommandMetadata currentCommand;
    private String currentCommandTail;
    private Map<String, Map<String, String>> commandParameterCache;
    private List<ParameterWidget> currentParameterWidgets;
    private List<FormComponent> allComponents;

    // Parent form reference for SearchableDropdown lifecycle
    private Form parentForm;

    public ConsoleCommandsTab(Client client, Runnable onBackCallback) {
        this.client = client;
        this.onBackCallback = onBackCallback;
        this.formBuilder = new ParameterFormBuilder(client);
        this.commandParameterCache = new HashMap<>();
        this.currentParameterWidgets = new ArrayList<>();
        this.allComponents = new ArrayList<>();
        this.currentCommandTail = "";
    }

    /**
     * Build all tab components into the parent form
     */
    public void buildInto(Form parentForm, int startX, int startY, int width, int height) {
        this.parentForm = parentForm;
        
        // Clear existing state when rebuilding
        this.currentCommand = null;
        this.currentCommandTail = "";
        if (commandPreviewLabel != null) {
            commandPreviewLabel.setText("Command: /");
        }

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

        // Category filter dropdown
        categoryFilter = new FormDropdownSelectionButton<>(
            startX + MARGIN + 130, currentY - 5,
            FormInputSize.SIZE_20,
            ButtonColor.BASE,
            150,
            new StaticMessage("All Categories")
        );

        // Add "All" option
        categoryFilter.options.add(null, new StaticMessage("All Categories"));

        // Add categories with available commands
        if (!NecesseCommandRegistry.isInitialized()) {
            ModLogger.warn("Command Registry not initialized when building Console Commands tab");
        }

        List<NecesseCommandMetadata> allCommands = new ArrayList<>(NecesseCommandRegistry.getAllCommands());
        Set<CommandCategory> categoriesWithCommands = new HashSet<>();

        for (NecesseCommandMetadata cmd : allCommands) {
            if (cmd.isAvailableInWorld(client)) {
                categoriesWithCommands.add(cmd.getCategory());
            }
        }

        for (CommandCategory cat : CommandCategory.values()) {
            if (categoriesWithCommands.contains(cat)) {
                categoryFilter.options.add(cat, new StaticMessage(cat.getDisplayName()));
            }
        }

        // Set initial selection from settings
        if (SettingsManager.getInstance().getLastSelectedCategory() != null && 
            !SettingsManager.getInstance().getLastSelectedCategory().isEmpty()) {
            try {
                CommandCategory savedCategory = CommandCategory.valueOf(
                    SettingsManager.getInstance().getLastSelectedCategory());
                categoryFilter.setSelected(savedCategory, new StaticMessage(savedCategory.getDisplayName()));
            } catch (IllegalArgumentException e) {
                // Invalid category, ignore
            }
        }

        // Filter commands when category changes
        categoryFilter.onSelected(event -> {
            CommandCategory selectedCategory = event.value;
            SettingsManager.getInstance().setLastSelectedCategory(
                (selectedCategory != null) ? selectedCategory.name() : "");
            Settings.saveClientSettings();
            rebuildCommandDropdown(parentForm, startX, contentWidth, selectedCategory);
        });

        parentForm.addComponent(categoryFilter);
        allComponents.add(categoryFilter);
        currentY += 30;

        // Command dropdown with search
        CommandCategory initialCategory = null;
        if (SettingsManager.getInstance().getLastSelectedCategory() != null && 
            !SettingsManager.getInstance().getLastSelectedCategory().isEmpty()) {
            try {
                initialCategory = CommandCategory.valueOf(
                    SettingsManager.getInstance().getLastSelectedCategory());
            } catch (IllegalArgumentException e) {
                // Ignore
            }
        }

        List<NecesseCommandMetadata> displayCommands = getFilteredCommands(initialCategory);
        displayCommands.sort((a, b) -> a.getId().compareTo(b.getId()));

        commandDropdown = new SearchableDropdown<>(
            startX + MARGIN, currentY,
            contentWidth, 200,
            "Search commands...",
            displayCommands,
            cmd -> cmd.getId(),
            selectedCmd -> {
                if (selectedCmd != null) {
                    loadCommand(selectedCmd);
                }
            }
        );

        commandDropdown.addToForm(parentForm);
        currentY += 40;

        // Favorites bar
        favoriteButtonsBox = new FormContentBox(
            startX + MARGIN, currentY,
            contentWidth, Constants.CommandCenter.FAVORITES_HEIGHT
        );
        parentForm.addComponent(favoriteButtonsBox);
        allComponents.add(favoriteButtonsBox);
        buildFavoriteButtons();
        currentY += Constants.CommandCenter.FAVORITES_HEIGHT + 10;

        // Command info display
        commandInfoLabel = new FormLabel(
            "Select a command...",
            WHITE_TEXT_11,
            FormLabel.ALIGN_LEFT,
            startX + MARGIN, currentY, contentWidth - 120
        );
        parentForm.addComponent(commandInfoLabel);
        allComponents.add(commandInfoLabel);

        // Favorite toggle button
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

        // Calculate button positions
        int previewHeight = 20;
        int buttonRowHeight = 40;
        int buttonY = height - MARGIN - buttonRowHeight;
        int buttonWidth = (contentWidth - 10) / 3;
        int previewY = buttonY - previewHeight - 5;
        int availableHeight = previewY - currentY - MARGIN;

        // Parameter area
        parameterScrollArea = new FormContentBox(
            startX + MARGIN, currentY,
            contentWidth, availableHeight
        );
        parentForm.addComponent(parameterScrollArea);
        allComponents.add(parameterScrollArea);

        // Command preview
        commandPreviewLabel = new FormLabel(
            "",
            WHITE_TEXT_11,
            FormLabel.ALIGN_LEFT,
            startX + MARGIN, previewY, contentWidth
        );
        parentForm.addComponent(commandPreviewLabel);
        allComponents.add(commandPreviewLabel);

        // Action buttons
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
     * Tick method - updates dropdown and parameter widgets
     */
    public void tick(necesse.engine.gameLoop.tickManager.TickManager tickManager) {
        if (commandDropdown != null) {
            commandDropdown.tick(tickManager);
        }

        if (currentParameterWidgets != null) {
            for (ParameterWidget widget : currentParameterWidgets) {
                if (widget instanceof MultiChoiceWidget) {
                    ((MultiChoiceWidget) widget).updateSelectionIfChanged();
                } else if (widget instanceof PlayerDropdownWidget) {
                    ((PlayerDropdownWidget) widget).tick(tickManager);
                }
            }
        }

        if (clearButton != null && executeButton != null) {
            updateButtonStates();
        }
    }

    /**
     * Remove all components from parent form
     */
    public void removeFromForm(Form parentForm) {
        if (commandDropdown != null) {
            commandDropdown.removeFromForm(parentForm);
        }

        for (FormComponent component : allComponents) {
            parentForm.removeComponent(component);
        }
        allComponents.clear();
    }

    /**
     * Get filtered commands by category and world settings
     */
    private List<NecesseCommandMetadata> getFilteredCommands(CommandCategory category) {
        List<NecesseCommandMetadata> allCommands = new ArrayList<>(NecesseCommandRegistry.getAllCommands());
        List<NecesseCommandMetadata> filtered = new ArrayList<>();

        for (NecesseCommandMetadata cmd : allCommands) {
            if (!cmd.isAvailableInWorld(client)) {
                continue;
            }

            if (category != null && cmd.getCategory() != category) {
                continue;
            }

            filtered.add(cmd);
        }

        return filtered;
    }

    /**
     * Rebuild command dropdown with filtered commands
     */
    private void rebuildCommandDropdown(Form parentForm, int startX, int contentWidth, CommandCategory category) {
        if (commandDropdown == null) {
            return;
        }

        List<NecesseCommandMetadata> filteredCommands = getFilteredCommands(category);
        filteredCommands.sort((a, b) -> a.getId().compareTo(b.getId()));

        commandDropdown.beginUpdate();
        commandDropdown.updateItems(filteredCommands);
        commandDropdown.endUpdate();
    }

    /**
     * Build favorite quick-access buttons
     */
    private void buildFavoriteButtons() {
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

                favBtn.onClicked(e -> {
                    commandDropdown.setSelectedItem(cmd);
                    loadCommand(cmd);
                });

                favoriteButtonsBox.addComponent(favBtn);

                x += buttonWidth + spacing;

                if (x + buttonWidth > favoriteButtonsBox.getWidth()) {
                    break;
                }
            }
        }

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
        this.currentCommandTail = command.getId();

        commandInfoLabel.setText(command.getAction());

        boolean isFavorite = SettingsManager.getInstance().getFavoriteCommands().contains(command.getId());
        favoriteToggleButton.setText(isFavorite ? "Remove Favorite" : "Add to Favorites");
        favoriteToggleButton.setActive(true);

        cleanupParameterWidgets();
        parameterScrollArea.clearComponents();
        currentParameterWidgets.clear();

        List<ParameterMetadata> params = command.getParameters();
        if (params == null || params.isEmpty()) {
            parameterScrollArea.addComponent(new FormLabel(
                "This command has no parameters.",
                WHITE_TEXT_11,
                FormLabel.ALIGN_LEFT,
                10, 10, parameterScrollArea.getWidth() - 20
            ));
        } else {
            // Build parameter widgets using helper
            formBuilder.buildParameterWidgets(
                params,
                parentForm,
                parameterScrollArea,
                currentParameterWidgets,
                commandParameterCache,
                command.getId(),
                () -> {
                    // Update command preview live as user types/changes fields
                    updateCommandPreviewFromWidgets();
                    updateButtonStates();
                }
            );
        }

        int contentHeight = formBuilder.calculateContentHeight(params, parameterScrollArea.getHeight());
        parameterScrollArea.setContentBox(new Rectangle(
            0, 0,
            parameterScrollArea.getWidth(),
            contentHeight
        ));

        updateCommandPreviewFromWidgets();
        updateButtonStates();
    }



    /**
     * Clear all parameter input values
     */
    private void clearParameters() {
        if (currentCommand == null) return;

        for (ParameterWidget widget : currentParameterWidgets) {
            widget.setValue("");
        }

        commandParameterCache.remove(currentCommand.getId());
        updateCommandPreviewFromWidgets();
        updateButtonStates();
    }

    /**
     * Cleanup parameter widgets before clearing
     */
    private void cleanupParameterWidgets() {
        if (parentForm == null) return;

        for (ParameterWidget widget : currentParameterWidgets) {
            if (widget instanceof RelativeIntInputWidget) {
                medievalsim.commandcenter.worldclick.WorldClickHandler handler =
                    medievalsim.commandcenter.worldclick.WorldClickHandler.getInstance();
                if (handler.isActive()) {
                    handler.stopSelection();
                    medievalsim.commandcenter.worldclick.WorldClickIntegration.stopIntegration();
                }
            }
        }
    }

    /**
     * Execute the command
     */
    private void executeCommand() {
        if (currentCommand == null) return;

        try {
            updateCommandPreviewFromWidgets();

            if (currentCommandTail == null || currentCommandTail.trim().isEmpty()) {
                return;
            }

            String finalCommand = "/" + currentCommandTail.trim();
            client.network.sendPacket(new PacketExecuteCommand(finalCommand));

        } catch (Exception e) {
            ModLogger.error("Error building command: " + e.getMessage());
            client.chat.addMessage(GameColor.RED.getColorCode() + "[Command Center] Error: " + e.getMessage());
        }
    }

    /**
     * Toggle favorite status for current command
     */
    private void toggleFavorite() {
        if (currentCommand == null) return;

        String cmdId = currentCommand.getId();

        if (SettingsManager.getInstance().getFavoriteCommands().contains(cmdId)) {
            SettingsManager.getInstance().getFavoriteCommands().remove(cmdId);
            favoriteToggleButton.setText("Add to Favorites");
        } else {
            int maxFavorites = ModConfig.CommandCenter.maxFavorites;
            if (SettingsManager.getInstance().getFavoriteCommands().size() >= maxFavorites) {
                return;
            }
            SettingsManager.getInstance().getFavoriteCommands().add(cmdId);
            favoriteToggleButton.setText("Remove Favorite");
        }

        Settings.saveClientSettings();
        buildFavoriteButtons();
    }

    /**
     * Update button enabled states
     */
    private void updateButtonStates() {
        boolean hasCommand = currentCommand != null;

        boolean canExecute = hasCommand;
        if (hasCommand && !currentParameterWidgets.isEmpty()) {
            for (ParameterWidget widget : currentParameterWidgets) {
                if (widget.getParameter().isRequired()) {
                    String value = normalizeWidgetValue(widget.getValue());
                    if (value == null) {
                        canExecute = false;
                        break;
                    }
                }
            }
        }

        if (clearButton != null) {
            clearButton.setActive(hasCommand && !currentParameterWidgets.isEmpty());
        }
        if (executeButton != null) {
            executeButton.setActive(canExecute);
        }
    }

    /**
     * Rebuild currentCommandTail from current widgets
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
            if (fullCommand.startsWith("/")) {
                currentCommandTail = fullCommand.substring(1);
            } else {
                currentCommandTail = fullCommand;
            }
        } catch (IllegalArgumentException e) {
            currentCommandTail = currentCommand.getId();
        }
        if (commandPreviewLabel != null) {
            commandPreviewLabel.setText("Command: /" + currentCommandTail);
        }
    }

    /**
     * Build argument array from current widgets
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
     * Normalize widget value
     */
    private String normalizeWidgetValue(String value) {
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
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
}
