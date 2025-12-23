package medievalsim.ui.tabs;

import medievalsim.commandcenter.settings.ConfigurableSetting;
import medievalsim.commandcenter.settings.ModConfigSection;
import medievalsim.commandcenter.settings.SettingType;
import medievalsim.commandcenter.settings.UniversalModConfigScanner;
import medievalsim.config.SettingsManager;
import medievalsim.util.Constants;
import medievalsim.util.ModLogger;
import necesse.engine.Settings;
import necesse.engine.modLoader.LoadedMod;
import necesse.engine.modLoader.ModLoader;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormBreakLine;
import necesse.gfx.forms.components.FormButton;
import necesse.gfx.forms.components.FormCheckBox;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormDropdownSelectionButton;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormMouseHover;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.gameTooltips.GameTooltips;
import necesse.gfx.gameTooltips.StringTooltips;
import necesse.gfx.ui.ButtonColor;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Mod Settings tab - universal in-game editor for ModConfig classes.
 * Extracted from CommandCenterPanel to improve maintainability.
 */
public class ModSettingsTab {

    private static final FontOptions WHITE_TEXT_14 = new FontOptions(14).color(Color.WHITE);
    private static final FontOptions WHITE_TEXT_11 = new FontOptions(11).color(Color.WHITE);
    private static final int MARGIN = 10;

    private final Runnable onBackCallback;
    private List<FormComponent> allComponents;
    private Form parentForm;

    // Store original panel dimensions for rebuilding
    private int panelStartX, panelStartY, panelWidth, panelHeight;

    public ModSettingsTab(Runnable onBackCallback) {
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

        // Scan all mods for ModConfig classes
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

        // Create scrollable content area
        int actionBarHeight = Constants.CommandCenter.ACTION_BAR_HEIGHT;
        int scrollAreaHeight = height - (currentY - startY) - (actionBarHeight + MARGIN);
        FormContentBox scrollArea = new FormContentBox(
            startX, currentY,
            width, scrollAreaHeight
        );
        scrollArea.shouldLimitDrawArea = true;
        parentForm.addComponent(scrollArea);
        allComponents.add(scrollArea);

        int scrollY = 10;

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
            scrollY += 15;
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
            scrollY += 15;
        }

        // Set content box bounds
        int maxContentHeight = scrollAreaHeight - 10;
        int effectiveContentHeight = Math.min(scrollY + 20, maxContentHeight);
        scrollArea.setContentBox(new Rectangle(0, 0, contentWidth - 20, effectiveContentHeight));

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
     * Build a section for one mod's settings
     */
    private int buildModSection(FormContentBox scrollArea, LoadedMod mod,
                                List<ModConfigSection> sections,
                                int x, int y, int width) {
        int currentY = y;

        SettingsManager settings = SettingsManager.getInstance();
        boolean isExpanded = settings.isModExpanded(mod.id);

        // Mod header (clickable to expand/collapse)
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
            // Rebuild entire tab
            removeFromForm(parentForm);
            buildInto(parentForm, panelStartX, panelStartY, panelWidth, panelHeight);
        });
        scrollArea.addComponent(modHeaderButton);
        currentY += 30;

        // Build each section (only if expanded)
        if (isExpanded) {
            for (ModConfigSection section : sections) {
                currentY = buildConfigSection(scrollArea, mod, section, x + 10, currentY, width - 10);
                currentY += 5;
            }
        }

        // Add divider line
        FormBreakLine divider = new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, x, currentY, width, true);
        divider.color = new Color(100, 100, 100);
        scrollArea.addComponent(divider);
        currentY += 10;

        return currentY;
    }

    /**
     * Build a config section
     */
    private int buildConfigSection(FormContentBox scrollArea, LoadedMod mod,
                                   ModConfigSection section,
                                   int x, int y, int width) {
        int currentY = y;

        SettingsManager settings = SettingsManager.getInstance();
        boolean isExpanded = settings.isSectionExpanded(mod.id, section.getName());

        // Section header (clickable to expand/collapse)
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
            // Rebuild entire tab
            removeFromForm(parentForm);
            buildInto(parentForm, panelStartX, panelStartY, panelWidth, panelHeight);
        });
        scrollArea.addComponent(sectionHeaderButton);
        currentY += 22;

        // Build each setting (only if expanded)
        if (isExpanded) {
            for (ConfigurableSetting setting : section.getSettings()) {
                currentY = buildSettingWidget(scrollArea, setting, x + 10, currentY, width - 10);
                currentY += 5;
            }
            
            // Special case: Add help text for Plot Flags section
            if (section.getName().equals("PLOT_FLAGS")) {
                FormLabel helpLabel = new FormLabel(
                    "Tip: Open your inventory crafting menu to create plot flags (admins only)",
                    WHITE_TEXT_11,
                    0,
                    x + 10, currentY,
                    width - 10
                );
                scrollArea.addComponent(helpLabel);
                currentY += 18;
            }
        }

        return currentY;
    }
    
    /**
     * Get the current player
     */
    private PlayerMob getPlayer() {
        if (necesse.engine.GlobalData.getCurrentState() instanceof necesse.engine.state.MainGame) {
            necesse.engine.state.MainGame mainGame = (necesse.engine.state.MainGame) necesse.engine.GlobalData.getCurrentState();
            necesse.engine.network.client.Client client = mainGame.getClient();
            if (client != null) {
                return client.getPlayer();
            }
        }
        return null;
    }

    /**
     * Check if current player is the world owner
     */
    private boolean isWorldOwner() {
        if (necesse.engine.GlobalData.getCurrentState() instanceof necesse.engine.state.MainGame) {
            necesse.engine.state.MainGame mainGame = (necesse.engine.state.MainGame) necesse.engine.GlobalData.getCurrentState();
            necesse.engine.network.client.Client client = mainGame.getClient();
            if (client != null) {
                // Singleplayer is always owner
                if (client.isSingleplayer()) {
                    return true;
                }
                // Check permission level
                necesse.engine.commands.PermissionLevel level = client.getPermissionLevel();
                if (level == necesse.engine.commands.PermissionLevel.OWNER) {
                    return true;
                }
                // Also check serverOwnerAuth for dedicated servers
                PlayerMob player = client.getPlayer();
                if (player != null && Settings.serverOwnerAuth != -1L 
                    && player.getUniqueID() == Settings.serverOwnerAuth) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Build a widget for a single setting
     */
    private int buildSettingWidget(FormContentBox scrollArea, ConfigurableSetting setting,
                                   int x, int y, int width) {
        int currentY = y;

        // Check if this setting is owner-only and player is not the owner
        boolean isOwnerOnlySetting = setting.isOwnerOnly();
        boolean canEdit = !isOwnerOnlySetting || isWorldOwner();
        
        // Setting label
        String labelText = setting.getDisplayName();
        if (isOwnerOnlySetting && !canEdit) {
            labelText += " [Owner Only]";
        }
        String fullDescription = setting.getDescription();
        boolean hasLongDescription = false;

        if (!fullDescription.isEmpty()) {
            String desc = fullDescription;
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

        // Input widget (right-aligned)
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
                intInput.setActive(canEdit);
                intInput.onSubmit(e -> {
                    if (!canEdit) return;
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

                FormTextButton resetButton = new FormTextButton(
                    "↺",
                    canEdit ? "Reset to default: " + setting.getDefaultValue() : "Owner only",
                    resetButtonX, currentY, resetButtonWidth,
                    FormInputSize.SIZE_16,
                    ButtonColor.BASE
                );
                resetButton.setActive(canEdit);
                resetButton.onClicked(e -> {
                    if (!canEdit) return;
                    setting.resetToDefault();
                    intInput.setText(String.valueOf(setting.getValue()));
                    Settings.saveClientSettings();
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
                floatInput.setActive(canEdit);
                floatInput.onSubmit(e -> {
                    if (!canEdit) return;
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

                FormTextButton floatResetButton = new FormTextButton(
                    "↺",
                    canEdit ? "Reset to default: " + setting.getDefaultValue() : "Owner only",
                    resetButtonX, currentY, resetButtonWidth,
                    FormInputSize.SIZE_16,
                    ButtonColor.BASE
                );
                floatResetButton.setActive(canEdit);
                floatResetButton.onClicked(e -> {
                    if (!canEdit) return;
                    setting.resetToDefault();
                    floatInput.setText(String.valueOf(setting.getValue()));
                    Settings.saveClientSettings();
                });
                scrollArea.addComponent(floatResetButton);
                break;

            case BOOLEAN:
                FormCheckBox checkbox = new FormCheckBox(
                    setting.getDisplayName(),
                    inputX, currentY,
                    inputWidth, setting.getBooleanValue()
                );
                checkbox.setActive(canEdit);
                checkbox.onClicked(e -> {
                    if (!canEdit) return;
                    setting.setBooleanValue(checkbox.checked);
                    Settings.saveClientSettings();
                    ModLogger.info("Updated %s to %s", setting.getFieldName(), checkbox.checked);
                });
                scrollArea.addComponent(checkbox);

                FormTextButton boolResetButton = new FormTextButton(
                    "↺",
                    canEdit ? "Reset to default: " + setting.getDefaultValue() : "Owner only",
                    resetButtonX, currentY, resetButtonWidth,
                    FormInputSize.SIZE_16,
                    ButtonColor.BASE
                );
                boolResetButton.setActive(canEdit);
                boolResetButton.onClicked(e -> {
                    if (!canEdit) return;
                    setting.resetToDefault();
                    checkbox.checked = setting.getBooleanValue();
                    Settings.saveClientSettings();
                });
                scrollArea.addComponent(boolResetButton);
                break;

            case STRING:
                // Standard text input for string fields
                FormTextInput stringInput = new FormTextInput(
                    inputX, currentY,
                    FormInputSize.SIZE_16,
                    inputWidth, 200, 100
                );
                stringInput.setText(setting.getStringValue());
                stringInput.setActive(canEdit);
                stringInput.onSubmit(e -> {
                    if (!canEdit) return;
                    setting.setStringValue(stringInput.getText());
                    Settings.saveClientSettings();
                    ModLogger.info("Updated %s to %s", setting.getFieldName(), stringInput.getText());
                });
                scrollArea.addComponent(stringInput);

                FormTextButton stringResetButton = new FormTextButton(
                    "↺",
                    canEdit ? "Reset to default: " + setting.getDefaultValue() : "Owner only",
                    resetButtonX, currentY, resetButtonWidth,
                    FormInputSize.SIZE_16,
                    ButtonColor.BASE
                );
                stringResetButton.setActive(canEdit);
                stringResetButton.onClicked(e -> {
                    if (!canEdit) return;
                    setting.resetToDefault();
                    stringInput.setText(setting.getStringValue());
                    Settings.saveClientSettings();
                });
                scrollArea.addComponent(stringResetButton);
                break;

            case ENUM:
                // Create enum dropdown
                @SuppressWarnings("rawtypes")
                Class enumClass = setting.getEnumClass();
                if (enumClass != null) {
                    Enum<?>[] enumValues = (Enum<?>[]) enumClass.getEnumConstants();
                    Enum<?> currentEnum = setting.getEnumValue();
                    
                    FormDropdownSelectionButton<Enum<?>> enumDropdown = new FormDropdownSelectionButton<Enum<?>>(
                        inputX, currentY,
                        FormInputSize.SIZE_16,
                        ButtonColor.BASE,
                        inputWidth,
                        new necesse.engine.localization.message.StaticMessage("Select...")
                    );
                    
                    // Add enum values to dropdown
                    if (enumValues != null) {
                        for (Enum<?> value : enumValues) {
                            String displayName = formatEnumName(value.name());
                            enumDropdown.options.add(value, new necesse.engine.localization.message.StaticMessage(displayName));
                        }
                        
                        // Set current value
                        if (currentEnum != null) {
                            String displayName = formatEnumName(currentEnum.name());
                            enumDropdown.setSelected(currentEnum, new necesse.engine.localization.message.StaticMessage(displayName));
                        }
                    }
                    
                    // Listen for changes
                    enumDropdown.onSelected(event -> {
                        if (event.value != null) {
                            setting.setValue(event.value);
                            Settings.saveClientSettings();
                        }
                    });
                    
                    scrollArea.addComponent(enumDropdown);
                    
                    // Reset button for enum
                    FormTextButton enumResetButton = new FormTextButton(
                        "↺",
                        resetButtonX, currentY,
                        30, // reset button width
                        FormInputSize.SIZE_16,
                        ButtonColor.BASE
                    );
                    enumResetButton.onClicked(e -> {
                        setting.resetToDefault();
                        Enum<?> resetValue = setting.getEnumValue();
                        if (resetValue != null) {
                            String displayName = formatEnumName(resetValue.name());
                            enumDropdown.setSelected(resetValue, new necesse.engine.localization.message.StaticMessage(displayName));
                        }
                        Settings.saveClientSettings();
                    });
                    scrollArea.addComponent(enumResetButton);
                } else {
                    // Fallback if enum class couldn't be determined
                    FormLabel enumLabel = new FormLabel(
                        "ENUM (error loading)",
                        WHITE_TEXT_11,
                        FormLabel.ALIGN_LEFT,
                        inputX, currentY, inputWidth
                    );
                    scrollArea.addComponent(enumLabel);
                }
                break;
        }

        currentY += 30;
        return currentY;
    }
    
    /**
     * Format enum constant name for display.
     * Converts "PEACEFUL" to "Peaceful", "WORLD_BOSS" to "World Boss", etc.
     */
    private String formatEnumName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        
        // Split on underscores and capitalize first letter of each word
        String[] parts = name.split("_");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            
            String part = parts[i].toLowerCase();
            if (part.length() > 0) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1));
                }
            }
        }
        
        return result.toString();
    }
}
