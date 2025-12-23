/*
 * CrestDesignerForm - Guild crest visual designer
 * Part of Medieval Sim Mod guild management system.
 */
package medievalsim.guilds.ui;

import medievalsim.guilds.GuildSymbolDesign;
import medievalsim.guilds.crest.GuildCrestRenderer;
import medievalsim.packets.PacketUpdateGuildSymbol;
import medievalsim.util.ModLogger;
import necesse.engine.localization.Localization;
import necesse.engine.network.client.Client;
import necesse.engine.window.GameWindow;
import necesse.engine.window.WindowManager;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.GameColor;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.*;
import necesse.gfx.forms.presets.ContinueForm;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

import java.awt.Color;
import java.awt.Rectangle;

/**
 * Interactive crest designer allowing players to customize their guild's crest.
 * Features:
 * - Background shape selector (Shield, Circle, Banner, Diamond, Square)
 * - Primary/Secondary color palettes
 * - Emblem selector with 20 emblems
 * - Border style selector (None, Simple, Ornate, Royal)
 * - Live preview of the crest
 */
public class CrestDesignerForm extends ContinueForm {

    private static final int WIDTH = 560; // increased to fit controls
    private static final int HEIGHT = 540; // increased height for layout
    
    // Tracks whether we hid this form because the client was paused
    private boolean hiddenOnPause = false;
    
    // Current design being edited
    private GuildSymbolDesign workingDesign;
    private final GuildSymbolDesign originalDesign;
    
    // Client reference for sending packets
    private final Client client;
    private final int guildID;
    
    // Selection tracking
    private int selectedShape = 0;
    private int selectedPrimaryColor = 0;
    private int selectedSecondaryColor = 0;
    private int selectedEmblem = 0;
    private int selectedEmblemColor = 0;
    private int selectedBorder = 0;
    
    // Color palette editing mode: 0=primary, 1=secondary, 2=emblem, 3=background
    private int colorEditMode = 0;

    // Preview target constants
    private static final int PREVIEW_CREST = 0;
    private static final int PREVIEW_BANNER = 1;
    private static final int PREVIEW_FLAG = 2;

    private int previewTarget = PREVIEW_CREST;

    // Preview selector buttons
    private necesse.gfx.forms.components.FormTextButton previewCrestBtn;
    private necesse.gfx.forms.components.FormTextButton previewBannerBtn;
    private necesse.gfx.forms.components.FormTextButton previewFlagBtn;
    
    // UI components for updates
    private FormLabel colorModeLabel;
    private FormLabel shapeLabel;
    private FormLabel emblemLabel;
    private FormLabel borderLabel;

    // Color mode buttons (so we can visually highlight the active mode)
    private necesse.gfx.forms.components.FormTextButton shapeColorBtn;
    private necesse.gfx.forms.components.FormTextButton borderColorBtn;
    private necesse.gfx.forms.components.FormTextButton emblemColorBtn;
    private necesse.gfx.forms.components.FormTextButton backgroundColorBtn;

    // Color swatch buttons so we can highlight the selected swatch
    private necesse.gfx.forms.components.FormTextButton[] colorSwatchButtons;
    
    // Static instance reference for packet callback
    private static CrestDesignerForm activeInstance;
    
    // Callback for preview mode (guild creation - no packets)
    private java.util.function.Consumer<GuildSymbolDesign> onDesignConfirmed;
    private boolean previewMode = false;
    
    public CrestDesignerForm(Client client, int guildID, GuildSymbolDesign currentSymbol) {
        super("crest_designer", WIDTH, HEIGHT);
        
        this.client = client;
        this.guildID = guildID;
        this.originalDesign = currentSymbol != null ? currentSymbol.copy() : new GuildSymbolDesign();
        this.workingDesign = originalDesign.copy();
        
        activeInstance = this;
        
        initializeFromDesign();
        setupUI();
    }
    
    /**
     * Constructor for preview mode (used during guild creation).
     * No packets sent - design returned via callback.
     */
    public CrestDesignerForm(Client client, GuildSymbolDesign currentSymbol, java.util.function.Consumer<GuildSymbolDesign> callback) {
        super("crest_designer", WIDTH, HEIGHT);
        
        this.client = client;
        this.guildID = -1; // No guild yet
        this.originalDesign = currentSymbol != null ? currentSymbol.copy() : new GuildSymbolDesign();
        this.workingDesign = originalDesign.copy();
        this.onDesignConfirmed = callback;
        this.previewMode = true;
        
        activeInstance = this;
        
        initializeFromDesign();
        setupUI();
    }
    
    private void initializeFromDesign() {
        selectedShape = workingDesign.getBackgroundShape();
        selectedEmblem = workingDesign.getEmblemID();
        selectedBorder = workingDesign.getBorderStyle();
        
        // Find matching color indices
        selectedPrimaryColor = findColorIndex(workingDesign.getPrimaryColor());
        selectedSecondaryColor = findColorIndex(workingDesign.getSecondaryColor());
        selectedEmblemColor = findColorIndex(workingDesign.getEmblemColor());
    }
    
    private int findColorIndex(int color) {
        for (int i = 0; i < GuildSymbolDesign.PRESET_COLORS.length; i++) {
            if (GuildSymbolDesign.PRESET_COLORS[i] == color) {
                return i;
            }
        }
        return 0; // Default to first color if not found
    }
    
    private void setupUI() {
        int leftCol = 20;
        int rightCol = 360; // moved right column further right for bigger preview
        int y = 15;
        
        // === Title ===
        addComponent(new FormLabel(
            Localization.translate("ui", "crestdesigner.title"),
            new FontOptions(18),
            FormLabel.ALIGN_MID, WIDTH / 2, y
        ));
        y += 35;
        
        // === LEFT COLUMN: Selectors ===
        
        // Background Shape
        addComponent(new FormLabel(
            Localization.translate("ui", "crestdesigner.shape"),
            new FontOptions(12),
            FormLabel.ALIGN_LEFT, leftCol, y
        ));
        y += 18;
        
        // Place the selected shape label above the buttons to avoid overlap
        shapeLabel = addComponent(new FormLabel(
            GuildSymbolDesign.BACKGROUND_SHAPES[selectedShape],
            new FontOptions(11).color(Color.YELLOW),
            FormLabel.ALIGN_LEFT, leftCol, y
        ));
        y += 18;
        
        int btnX = leftCol;
        for (int i = 0; i < GuildSymbolDesign.BACKGROUND_SHAPES.length; i++) {
            final int idx = i;
            String fullName = GuildSymbolDesign.BACKGROUND_SHAPES[i];
            String shortName = fullName.length() > 4 ? fullName.substring(0, 4) : fullName;
            FormTextButton btn = addComponent(new FormTextButton(
                shortName,
                fullName, // Tooltip with full name
                btnX, y, 38, FormInputSize.SIZE_20, ButtonColor.BASE
            ));
            btn.onClicked(e -> selectShape(idx));
            btnX += 40;
        }
        y += 32;
        
        // === Colors Section ===
        addComponent(new FormLabel(
            "Colors",
            new FontOptions(12),
            FormLabel.ALIGN_LEFT, leftCol, y
        ));
        y += 18;
        
        // Color mode buttons
        // Color mode buttons (renamed per request)
        // Color mode buttons (store references so we can highlight active mode)
        shapeColorBtn = addComponent(new FormTextButton(
            "Shape", Localization.translate("ui", "crestdesigner.mode.shape"), leftCol, y, 60, FormInputSize.SIZE_20, ButtonColor.BASE
        ));
        shapeColorBtn.onClicked(e -> setColorMode(0));
        
        borderColorBtn = addComponent(new FormTextButton(
            "Border", Localization.translate("ui", "crestdesigner.mode.border"), leftCol + 65, y, 70, FormInputSize.SIZE_20, ButtonColor.BASE
        ));
        borderColorBtn.onClicked(e -> setColorMode(1));
        
        emblemColorBtn = addComponent(new FormTextButton(
            "Emblem", Localization.translate("ui", "crestdesigner.mode.emblem"), leftCol + 150, y, 70, FormInputSize.SIZE_20, ButtonColor.BASE
        ));
        emblemColorBtn.onClicked(e -> setColorMode(2));
        
        backgroundColorBtn = addComponent(new FormTextButton(
            "Background", Localization.translate("ui", "crestdesigner.mode.background"), leftCol + 235, y, 90, FormInputSize.SIZE_20, ButtonColor.BASE
        ));
        backgroundColorBtn.onClicked(e -> setColorMode(3));
        y += 26;
        
        colorModeLabel = addComponent(new FormLabel(
            "Editing: Shape",
            new FontOptions(10).color(Color.CYAN),
            FormLabel.ALIGN_LEFT, leftCol, y
        ));
        y += 18;
        
        // Color palette (4 rows of 4) - store buttons so we can highlight selection
        int colorBtnSize = 28;
        colorSwatchButtons = new FormTextButton[GuildSymbolDesign.PRESET_COLORS.length];
        for (int i = 0; i < GuildSymbolDesign.PRESET_COLORS.length; i++) {
            final int colorIdx = i;
            int row = i / 4;
            int col = i % 4;
            int color = GuildSymbolDesign.PRESET_COLORS[i];
            String name = GuildSymbolDesign.PRESET_COLOR_NAMES[i];

            // Short code (2 letters) rendered using the color as text color to act as a swatch
            String shortName = name.length() > 2 ? name.substring(0, 2) : name;
            final int colorConst = color;
            FormTextButton btn = addComponent(new FormTextButton(shortName, name, leftCol + (col * (colorBtnSize + 8)), y + (row * (colorBtnSize + 8)), colorBtnSize + 4, FormInputSize.SIZE_20, ButtonColor.BASE) {
                @Override
                public java.awt.Color getTextColor() {
                    return new java.awt.Color(colorConst);
                }

                @Override
                protected void addTooltips(PlayerMob perspective) {
                    // show full color name tooltip
                    super.addTooltips(perspective);
                }
            });
            colorSwatchButtons[i] = btn;
            btn.tooltipMaxWidth = 160;
            btn.onClicked(e -> selectColor(colorIdx));
        }
        y += (colorBtnSize + 8) * 4 + 14;
        
        // === Emblem Section ===
        addComponent(new FormLabel(
            Localization.translate("ui", "crestdesigner.emblem"),
            new FontOptions(12),
            FormLabel.ALIGN_LEFT, leftCol, y
        ));
        
        emblemLabel = addComponent(new FormLabel(
            GuildSymbolDesign.EMBLEM_NAMES[selectedEmblem],
            new FontOptions(11).color(Color.YELLOW),
            FormLabel.ALIGN_LEFT, leftCol + 60, y
        ));
        y += 18;
        
        // Emblem buttons (2 rows of 9)
        int emblemBtnSize = 22;
        for (int i = 0; i < GuildSymbolDesign.EMBLEM_NAMES.length; i++) {
            final int emblemIdx = i;
            int row = i / 9;
            int col = i % 9;
            
            String fullName = GuildSymbolDesign.EMBLEM_NAMES[i];
            String initial = fullName.substring(0, 1);
            FormTextButton btn = addComponent(new FormTextButton(
                initial,
                fullName, // tooltip with full emblem name
                leftCol + (col * (emblemBtnSize + 1)), y + (row * (emblemBtnSize + 1)),
                emblemBtnSize, FormInputSize.SIZE_16, ButtonColor.BASE
            ));
            btn.onClicked(e -> selectEmblem(emblemIdx));
            btn.tooltipMaxWidth = 160;
        }
        y += (emblemBtnSize + 1) * 2 + 18;
        
        // === Border Section ===
        addComponent(new FormLabel(
            Localization.translate("ui", "crestdesigner.border"),
            new FontOptions(12),
            FormLabel.ALIGN_LEFT, leftCol, y
        ));
        
        borderLabel = addComponent(new FormLabel(
            GuildSymbolDesign.BORDER_STYLES[selectedBorder],
            new FontOptions(11).color(Color.YELLOW),
            FormLabel.ALIGN_LEFT, leftCol, y + 18
        ));
        y += 26;
        
        btnX = leftCol;
        for (int i = 0; i < GuildSymbolDesign.BORDER_STYLES.length; i++) {
            final int idx = i;
            String full = GuildSymbolDesign.BORDER_STYLES[i];
            String shortName = full.length() > 5 ? full.substring(0, 5) : full;
            FormTextButton btn = addComponent(new FormTextButton(
                shortName,
                full, // tooltip
                btnX, y, 48, FormInputSize.SIZE_20, ButtonColor.BASE
            ));
            btn.onClicked(e -> selectBorder(idx));
            btnX += 50;
        }
        
        // === RIGHT COLUMN: Live Preview ===
        // Place preview label and buttons directly above the preview area for clarity
        int previewSize = 220;
        int previewXLocal = WIDTH - previewSize - 25;
        int previewYLocal = HEIGHT - previewSize - 60;

        addComponent(new FormLabel(
            Localization.translate("ui", "symbolcreator.preview"),
            new FontOptions(14),
            FormLabel.ALIGN_MID, previewXLocal + (previewSize / 2), previewYLocal - 36
        ));

        // Preview mode selector buttons (Crest / Banner / Flag) - aligned above preview
        int btnY = previewYLocal - 22;
        int previewBtnX = previewXLocal + 8;
        previewCrestBtn = addComponent(new FormTextButton(Localization.translate("ui","symbolcreator.mode.crest"), Localization.translate("ui","symbolcreator.mode.crest"), previewBtnX, btnY, 60, FormInputSize.SIZE_20, ButtonColor.BASE));
        previewCrestBtn.onClicked(e -> setPreviewTarget(PREVIEW_CREST));
        previewBtnX += 66;
        previewBannerBtn = addComponent(new FormTextButton(Localization.translate("ui","symbolcreator.mode.banner"), Localization.translate("ui","symbolcreator.mode.banner"), previewBtnX, btnY, 70, FormInputSize.SIZE_20, ButtonColor.BASE));
        previewBannerBtn.onClicked(e -> setPreviewTarget(PREVIEW_BANNER));
        previewBtnX += 76;
        previewFlagBtn = addComponent(new FormTextButton(Localization.translate("ui","symbolcreator.mode.flag"), Localization.translate("ui","symbolcreator.mode.flag"), previewBtnX, btnY, 50, FormInputSize.SIZE_20, ButtonColor.BASE));
        previewFlagBtn.onClicked(e -> setPreviewTarget(PREVIEW_FLAG));

        // Ensure preview buttons show current selection
        setPreviewTarget(previewTarget);
        
        // === Bottom Buttons ===
        int bottomY = HEIGHT - 55;
        int btnWidth = 90;
        int spacing = 16;
        int totalWidth = (btnWidth * 3) + (spacing * 2);
        int startX = (WIDTH - totalWidth) / 2;
        
        FormTextButton saveBtn = addComponent(new FormTextButton(
            Localization.translate("ui", "savebutton"),
            startX, bottomY, btnWidth, FormInputSize.SIZE_24, ButtonColor.BASE
        ));
        saveBtn.onClicked(e -> saveCrest());
        
        FormTextButton resetBtn = addComponent(new FormTextButton(
            Localization.translate("ui", "reset"),
            startX + btnWidth + spacing, bottomY, btnWidth, FormInputSize.SIZE_24, ButtonColor.BASE
        ));
        resetBtn.onClicked(e -> resetDesign());
        
        FormTextButton cancelBtn = addComponent(new FormTextButton(
            Localization.translate("ui", "cancel"),
            startX + (btnWidth + spacing) * 2, bottomY, btnWidth, FormInputSize.SIZE_24, ButtonColor.RED
        ));
        cancelBtn.onClicked(e -> {
            activeInstance = null;
            applyContinue();
        });
    }
    
    @Override
    public void draw(necesse.engine.gameLoop.tickManager.TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
        // Hide while paused but remember to show again when unpaused
        if (client != null && client.isPaused()) {
            if (!this.isHidden()) {
                this.setHidden(true);
                this.hiddenOnPause = true;
            }
            return;
        } else if (this.hiddenOnPause) {
            this.setHidden(false);
            this.hiddenOnPause = false;
        }

        super.draw(tickManager, perspective, renderBox);
        
        // Draw the live crest preview on the right side of the form (larger), moved down to avoid overlapping fields
        int previewSize = 220;
        int previewX = this.getX() + WIDTH - previewSize - 25;
        int previewY = this.getY() + HEIGHT - previewSize - 60; // put near bottom-right to avoid fields above
        
        // Draw the actual preview (crest / banner / flag depending on previewTarget)
        if (GuildCrestRenderer.areTexturesLoaded()) {
            switch (previewTarget) {
                case PREVIEW_BANNER:
                    GuildCrestRenderer.drawSymbolOnItem(workingDesign, "guildbanner", previewX, previewY, previewSize);
                    break;
                case PREVIEW_FLAG:
                    GuildCrestRenderer.drawSymbolOnItem(workingDesign, "guildflag", previewX, previewY, previewSize);
                    break;
                default:
                    GuildCrestRenderer.drawSymbolOnItem(workingDesign, "guildcrest", previewX, previewY, previewSize);
                    break;
            }
        }
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
    
    private void updateSelectionLabels() {
        // Update the selection indicator labels
        if (shapeLabel != null) {
            shapeLabel.setText("Shape: " + GuildSymbolDesign.BACKGROUND_SHAPES[selectedShape]);
        }
        if (emblemLabel != null) {
            emblemLabel.setText("Emblem: " + GuildSymbolDesign.EMBLEM_NAMES[selectedEmblem]);
        }
        if (borderLabel != null) {
            borderLabel.setText("Border: " + GuildSymbolDesign.BORDER_STYLES[selectedBorder]);
        }
        // Note: The live preview is rendered in draw(), no need to update it manually
        updateColorSwatchesHighlight();
    }
    
    private void updateColorModeLabel() {
        if (colorModeLabel != null) {
            String mode = switch(colorEditMode) {
                case 0 -> "Editing: Shape";
                case 1 -> "Editing: Border";
                case 2 -> "Editing: Emblem";
                case 3 -> "Editing: Background";
                default -> "Editing: Shape";
            };
            colorModeLabel.setText(mode);
        }
    }
    
    // === Selection Handlers ===
    
    private void selectShape(int index) {
        selectedShape = index;
        workingDesign.setBackgroundShape(index);
        updateSelectionLabels();
    }
    
    private void setColorMode(int mode) {
        colorEditMode = mode;
        updateColorModeLabel();
        updateColorModeButtons();
        updateColorSwatchesHighlight();
    }
    
    private void selectColor(int index) {
        int color = GuildSymbolDesign.PRESET_COLORS[index];
        
        switch (colorEditMode) {
            case 0: // Shape (primary)
                selectedPrimaryColor = index;
                workingDesign.setPrimaryColor(color);
                break;
            case 1: // Border (secondary)
                selectedSecondaryColor = index;
                workingDesign.setSecondaryColor(color);
                break;
            case 2: // Emblem
                selectedEmblemColor = index;
                workingDesign.setEmblemColor(color);
                break;
            case 3: // Background
                // Set the separate background color
                workingDesign.setBackgroundColor(color);
                break;
        }
        updateSelectionLabels();
        updateColorSwatchesHighlight();
    }
    
    private void selectEmblem(int index) {
        selectedEmblem = index;
        workingDesign.setEmblemID(index);
        updateSelectionLabels();
    }
    
    private void selectBorder(int index) {
        selectedBorder = index;
        workingDesign.setBorderStyle(index);
        updateSelectionLabels();
    }
    
    private void resetDesign() {
        workingDesign = originalDesign.copy();
        initializeFromDesign();
        updateSelectionLabels();
    }
    
    private void saveCrest() {
        if (previewMode && onDesignConfirmed != null) {
            // Preview mode - return design via callback, close form
            onDesignConfirmed.accept(workingDesign.copy());
            ModLogger.info("Crest design confirmed in preview mode");
            activeInstance = null;
            applyContinue();
        } else {
            // Normal mode - send update packet to server
            client.network.sendPacket(new PacketUpdateGuildSymbol(workingDesign));
            ModLogger.info("Sending crest update packet to server");
            // Keep form open - will close on success confirmation
        }
    }
    
    // === Static Methods ===
    
    /**
     * Show the crest designer for a guild.
     */
    public static void showDesigner(Client client, int guildID, GuildSymbolDesign currentSymbol) {
        try {
            CrestDesignerForm form = new CrestDesignerForm(client, guildID, currentSymbol);
            
            // Use proper FormManager pattern to show the form
            necesse.gfx.forms.FormManager formManager = necesse.engine.GlobalData.getCurrentState().getFormManager();
            if (formManager instanceof necesse.gfx.forms.ContinueComponentManager) {
                ((necesse.gfx.forms.ContinueComponentManager) formManager).addContinueForm("crestdesigner", form);
                // Ensure this form is on top in case parent dialogs were added with higher z-index
                ((FormComponent)form).tryPutOnTop();
                // Request a focus refresh - this helps ensure newly-added continue forms are re-evaluated for z-order
                try {
                    necesse.engine.input.controller.ControllerInput.submitNextRefreshFocusEvent();
                } catch (Throwable t) {}
                ModLogger.debug("Crest designer opened for guild %d", guildID);
            } else {
                ModLogger.warn("FormManager is not a ContinueComponentManager, cannot show crest designer");
                client.chat.addMessage(GameColor.RED.getColorCode() + "Could not open crest designer");
            }
        } catch (Exception e) {
            ModLogger.error("Failed to show crest designer", e);
            client.chat.addMessage(GameColor.RED.getColorCode() + "Failed to open crest designer");
        }
    }
    
    /**
     * Show the crest designer in preview mode (for guild creation).
     * Design is returned via callback instead of sending packets.
     */
    public static void showPreview(Client client, GuildSymbolDesign currentSymbol, java.util.function.Consumer<GuildSymbolDesign> callback) {
        try {
            CrestDesignerForm form = new CrestDesignerForm(client, currentSymbol, callback);
            
            necesse.gfx.forms.FormManager formManager = necesse.engine.GlobalData.getCurrentState().getFormManager();
            if (formManager instanceof necesse.gfx.forms.ContinueComponentManager) {
                ((necesse.gfx.forms.ContinueComponentManager) formManager).addContinueForm("crestdesigner", form);
                // Ensure preview appears above the create dialog
                ((FormComponent)form).tryPutOnTop();
                // Request a focus refresh so the manager re-evaluates z-order
                try {
                    necesse.engine.input.controller.ControllerInput.submitNextRefreshFocusEvent();
                } catch (Throwable t) {}
                ModLogger.debug("Crest designer opened in preview mode");
            } else {
                ModLogger.warn("FormManager is not a ContinueComponentManager, cannot show crest designer");
                client.chat.addMessage(GameColor.RED.getColorCode() + "Could not open crest designer");
            }
        } catch (Exception e) {
            ModLogger.error("Failed to show crest designer in preview mode", e);
            client.chat.addMessage(GameColor.RED.getColorCode() + "Failed to open crest designer");
        }
    }
    
    /**
     * Callback when server confirms crest update was successful.
     */
    public static void notifyUpdateSuccess(GuildSymbolDesign newDesign) {
        if (activeInstance != null) {
            activeInstance.onUpdateSuccess(newDesign);
        }
    }
    
    private void onUpdateSuccess(GuildSymbolDesign newDesign) {
        ModLogger.info("Guild symbol updated successfully!");
        if (activeInstance == this) {
            activeInstance = null;
        }
        applyContinue();
    }

    // Update visual state of the color mode buttons (highlight the active one)
    private void updateColorModeButtons() {
        if (shapeColorBtn != null) shapeColorBtn.color = (colorEditMode == 0) ? ButtonColor.GREEN : ButtonColor.BASE;
        if (borderColorBtn != null) borderColorBtn.color = (colorEditMode == 1) ? ButtonColor.GREEN : ButtonColor.BASE;
        if (emblemColorBtn != null) emblemColorBtn.color = (colorEditMode == 2) ? ButtonColor.GREEN : ButtonColor.BASE;
        if (backgroundColorBtn != null) backgroundColorBtn.color = (colorEditMode == 3) ? ButtonColor.GREEN : ButtonColor.BASE;
    }

    // Preview target setter
    private void setPreviewTarget(int target) {
        this.previewTarget = target;
        if (previewCrestBtn != null) previewCrestBtn.color = (target == PREVIEW_CREST) ? ButtonColor.GREEN : ButtonColor.BASE;
        if (previewBannerBtn != null) previewBannerBtn.color = (target == PREVIEW_BANNER) ? ButtonColor.GREEN : ButtonColor.BASE;
        if (previewFlagBtn != null) previewFlagBtn.color = (target == PREVIEW_FLAG) ? ButtonColor.GREEN : ButtonColor.BASE;
    }

    // Highlight the selected color swatch for the active color mode
    private void updateColorSwatchesHighlight() {
        if (colorSwatchButtons == null) return;
        int selectedIndex = 0;
        switch (colorEditMode) {
            case 0: selectedIndex = selectedPrimaryColor; break;
            case 1: selectedIndex = selectedSecondaryColor; break;
            case 2: selectedIndex = selectedEmblemColor; break;
            case 3: selectedIndex = findColorIndex(workingDesign.getBackgroundColor()); break;
        }
        for (int i = 0; i < colorSwatchButtons.length; i++) {
            try {
                if (colorSwatchButtons[i] == null) continue;
                colorSwatchButtons[i].color = (i == selectedIndex) ? ButtonColor.GREEN : ButtonColor.BASE;
            } catch (Exception ignored) {}
        }
    }
}
