/*
 * Crest Editor Form for Medieval Sim Mod
 * UI for designing guild crests.
 * 
 * Simplified implementation using basic Form components.
 */
package medievalsim.guilds.crest;

import medievalsim.guilds.GuildSymbolDesign;
import medievalsim.util.ModLogger;
import necesse.gfx.forms.Form;

import java.util.function.Consumer;

/**
 * Form for editing guild crest designs.
 * 
 * NOTE: This is a simplified placeholder that stores the design.
 * Full UI implementation requires more Necesse API research.
 */
public class CrestEditorForm extends Form {

    private static final int FORM_WIDTH = 300;
    private static final int FORM_HEIGHT = 400;

    private GuildSymbolDesign design;
    private Consumer<GuildSymbolDesign> onSave;
    
    // Track current selections
    private int selectedShape = 0;
    private int selectedEmblem = 0;
    private int selectedBorder = 1;
    private int primaryColor = 0x0000FF;
    private int emblemColor = 0xFFFFFF;

    public CrestEditorForm(GuildSymbolDesign existingDesign, Consumer<GuildSymbolDesign> onSave) {
        super("Crest Editor", FORM_WIDTH, FORM_HEIGHT);
        
        this.onSave = onSave;
        
        if (existingDesign != null) {
            this.design = existingDesign.copy();
            this.selectedShape = existingDesign.getBackgroundShape();
            this.selectedEmblem = existingDesign.getEmblemID();
            this.selectedBorder = existingDesign.getBorderStyle();
            this.primaryColor = existingDesign.getPrimaryColor();
            this.emblemColor = existingDesign.getEmblemColor();
        } else {
            this.design = new GuildSymbolDesign();
        }
        
        ModLogger.debug("CrestEditorForm created for shape=%d, emblem=%d", selectedShape, selectedEmblem);
    }

    /**
     * Cycle to next background shape.
     */
    public void nextShape() {
        selectedShape = (selectedShape + 1) % GuildSymbolDesign.BACKGROUND_SHAPES.length;
        design.setBackgroundShape(selectedShape);
    }

    /**
     * Cycle to previous background shape.
     */
    public void prevShape() {
        selectedShape = (selectedShape - 1 + GuildSymbolDesign.BACKGROUND_SHAPES.length) % GuildSymbolDesign.BACKGROUND_SHAPES.length;
        design.setBackgroundShape(selectedShape);
    }

    /**
     * Cycle to next emblem.
     */
    public void nextEmblem() {
        selectedEmblem = (selectedEmblem + 1) % GuildSymbolDesign.EMBLEM_NAMES.length;
        design.setEmblemID(selectedEmblem);
    }

    /**
     * Cycle to previous emblem.
     */
    public void prevEmblem() {
        selectedEmblem = (selectedEmblem - 1 + GuildSymbolDesign.EMBLEM_NAMES.length) % GuildSymbolDesign.EMBLEM_NAMES.length;
        design.setEmblemID(selectedEmblem);
    }

    /**
     * Cycle to next border style.
     */
    public void nextBorder() {
        selectedBorder = (selectedBorder + 1) % GuildSymbolDesign.BORDER_STYLES.length;
        design.setBorderStyle(selectedBorder);
    }

    /**
     * Set primary color from preset index.
     */
    public void setPrimaryColorPreset(int presetIndex) {
        if (presetIndex >= 0 && presetIndex < GuildSymbolDesign.PRESET_COLORS.length) {
            primaryColor = GuildSymbolDesign.PRESET_COLORS[presetIndex];
            design.setPrimaryColor(primaryColor);
        }
    }

    /**
     * Set emblem color from preset index.
     */
    public void setEmblemColorPreset(int presetIndex) {
        if (presetIndex >= 0 && presetIndex < GuildSymbolDesign.PRESET_COLORS.length) {
            emblemColor = GuildSymbolDesign.PRESET_COLORS[presetIndex];
            design.setEmblemColor(emblemColor);
        }
    }

    public GuildSymbolDesign getDesign() {
        return design;
    }
    
    public String getCurrentShapeName() {
        return GuildSymbolDesign.BACKGROUND_SHAPES[selectedShape];
    }
    
    public String getCurrentEmblemName() {
        return GuildSymbolDesign.EMBLEM_NAMES[selectedEmblem];
    }
    
    public String getCurrentBorderName() {
        return GuildSymbolDesign.BORDER_STYLES[selectedBorder];
    }

    public void save() {
        if (onSave != null) {
            onSave.accept(design);
        }
        ModLogger.debug("Crest design saved: shape=%s, emblem=%s", getCurrentShapeName(), getCurrentEmblemName());
    }
    
    public void cancel() {
        ModLogger.debug("Crest editor cancelled");
    }
}
