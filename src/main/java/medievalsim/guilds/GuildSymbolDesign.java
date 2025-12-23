package medievalsim.guilds;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

import java.awt.Color;

/**
 * Guild symbol visual design data.
 * Composed of background shape, colors, emblem, and border style.
 */
public class GuildSymbolDesign {
    // Background
    private int backgroundShape;      // 0=Shield, 1=Circle, 2=Banner, 3=Diamond, 4=Square
    private int primaryColor;         // RGB packed int (formerly backgroundColor)
    private int secondaryColor;       // RGB packed int

    // Emblem
    private int emblemID;             // Index into emblem sprite array
    private int emblemColor;          // RGB packed int

    // Border
    private int borderStyle;          // 0=None, 1=Simple, 2=Ornate, 3=Royal

    public static final String[] BACKGROUND_SHAPES = {"Shield", "Circle", "Banner", "Diamond", "Square"};
    public static final String[] BORDER_STYLES = {"None", "Simple", "Ornate", "Royal"};
    public static final String[] EMBLEM_NAMES = {
        "Sword", "Pickaxe", "Tree", "Crown", "Star", "Hammer", "Anvil", "Coin",
        "Shield", "Axe", "Wheat", "Gem", "Castle", "Dragon", "Lion", "Eagle",
        "Wolf", "Bear", "Skull", "Rose"
    };

    public static final int[] PRESET_COLORS = {
        0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF,
        0xFFFFFF, 0x000000, 0x808080, 0x800000, 0x008000, 0x000080,
        0x808000, 0x800080, 0x008080, 0xC0C0C0
    };

    // Human-readable names for preset colors (same length as PRESET_COLORS)
    public static final String[] PRESET_COLOR_NAMES = {
        "Red", "Green", "Blue", "Yellow", "Magenta", "Cyan",
        "White", "Black", "Gray", "Maroon", "Dark Green", "Navy",
        "Olive", "Purple", "Teal", "Silver"
    };

    // Background color (used for banners/flags/trinkets). Defaults to primary color for backwards compatibility
    private int backgroundColor; // RGB packed int

    /**
     * Create default symbol design.
     */
    public GuildSymbolDesign() {
        this.backgroundShape = 0; // Shield
        this.primaryColor = 0x0000FF; // Blue
        this.secondaryColor = 0x000080; // Dark Blue
        this.backgroundColor = this.primaryColor; // default background same as primary
        this.emblemID = 0; // Sword
        this.emblemColor = 0xFFFFFF; // White
        this.borderStyle = 1; // Simple
    }

    /**
     * Create symbol from specified values.
     */
    public GuildSymbolDesign(int backgroundShape, int primaryColor, int secondaryColor, int emblemID, int emblemColor, int borderStyle) {
        this.backgroundShape = Math.max(0, Math.min(backgroundShape, BACKGROUND_SHAPES.length - 1));
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.backgroundColor = primaryColor; // keep compatibility
        this.emblemID = Math.max(0, Math.min(emblemID, EMBLEM_NAMES.length - 1));
        this.emblemColor = emblemColor;
        this.borderStyle = Math.max(0, Math.min(borderStyle, BORDER_STYLES.length - 1));
    }
    
    /**
     * Create symbol from 5-parameter form.
     */
    public GuildSymbolDesign(int backgroundShape, int primaryColor, int emblemID, int emblemColor, int borderStyle) {
        this(backgroundShape, primaryColor, darken(primaryColor), emblemID, emblemColor, borderStyle);
    }

    /**
     * Load from save data.
     */
    public GuildSymbolDesign(LoadData save) {
        this.backgroundShape = save.getInt("bgShape", 0);
        this.primaryColor = save.getInt("primaryColor", save.getInt("bgColor", 0x0000FF));
        this.secondaryColor = save.getInt("secondaryColor", darken(this.primaryColor));
        this.backgroundColor = save.getInt("backgroundColor", this.primaryColor);
        this.emblemID = save.getInt("emblemID", 0);
        this.emblemColor = save.getInt("emblemColor", 0xFFFFFF);
        this.borderStyle = save.getInt("borderStyle", 1);
    }

    /**
     * Save to save data.
     */
    public void addSaveData(SaveData save) {
        save.addInt("bgShape", backgroundShape);
        save.addInt("primaryColor", primaryColor);
        save.addInt("secondaryColor", secondaryColor);
        save.addInt("backgroundColor", backgroundColor);
        save.addInt("emblemID", emblemID);
        save.addInt("emblemColor", emblemColor);
        save.addInt("borderStyle", borderStyle);
    }

    // === Getters ===

    public int getBackgroundShape() { return backgroundShape; }
    public int getShape() { return backgroundShape; }
    public int getBackgroundColor() { return backgroundColor; }
    public int getPrimaryColor() { return primaryColor; }
    public int getSecondaryColor() { return secondaryColor; }
    public int getEmblemID() { return emblemID; }
    public int getEmblem() { return emblemID; }
    public int getEmblemColor() { return emblemColor; }
    public int getBorderStyle() { return borderStyle; }
    public int getBorder() { return borderStyle; }
    
    public Color getPrimaryColorObject() { return new Color(primaryColor); }
    public Color getSecondaryColorObject() { return new Color(secondaryColor); }
    public Color getEmblemColorObject() { return new Color(emblemColor); }

    public String getBackgroundShapeName() {
        return BACKGROUND_SHAPES[backgroundShape];
    }

    public String getBorderStyleName() {
        return BORDER_STYLES[borderStyle];
    }

    public String getEmblemName() {
        return EMBLEM_NAMES[emblemID];
    }

    // === Setters ===

    public void setBackgroundShape(int shape) {
        this.backgroundShape = Math.max(0, Math.min(shape, BACKGROUND_SHAPES.length - 1));
    }

    public void setBackgroundColor(int color) {
        this.backgroundColor = color;
    }
    
    public void setPrimaryColor(int color) {
        this.primaryColor = color;
    }
    
    public void setSecondaryColor(int color) {
        this.secondaryColor = color;
    }

    public void setEmblemID(int id) {
        this.emblemID = Math.max(0, Math.min(id, EMBLEM_NAMES.length - 1));
    }

    public void setEmblemColor(int color) {
        this.emblemColor = color;
    }

    public void setBorderStyle(int style) {
        this.borderStyle = Math.max(0, Math.min(style, BORDER_STYLES.length - 1));
    }
    
    // === Utility ===
    
    /**
     * Create a copy of this design.
     */
    public GuildSymbolDesign copy() {
        GuildSymbolDesign copy = new GuildSymbolDesign(backgroundShape, primaryColor, secondaryColor, emblemID, emblemColor, borderStyle);
        copy.backgroundColor = this.backgroundColor;
        return copy;
    }
    
    private static int darken(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return ((r / 2) << 16) | ((g / 2) << 8) | (b / 2);
    }

    /**
     * Generate a JSON-like string for UID calculation.
     */
    public String toDesignString() {
        return backgroundShape + "|" + primaryColor + "|" + secondaryColor + "|" + backgroundColor + "|" + emblemID + "|" + emblemColor + "|" + borderStyle;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof GuildSymbolDesign other)) return false;
        return backgroundShape == other.backgroundShape
            && primaryColor == other.primaryColor
            && secondaryColor == other.secondaryColor
            && backgroundColor == other.backgroundColor
            && emblemID == other.emblemID
            && emblemColor == other.emblemColor
            && borderStyle == other.borderStyle;
    }

    @Override
    public int hashCode() {
        int result = backgroundShape;
        result = 31 * result + primaryColor;
        result = 31 * result + secondaryColor;
        result = 31 * result + backgroundColor;
        result = 31 * result + emblemID;
        result = 31 * result + emblemColor;
        result = 31 * result + borderStyle;
        return result;
    }}