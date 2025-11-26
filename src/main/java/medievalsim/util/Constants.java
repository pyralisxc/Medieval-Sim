package medievalsim.util;

/**
 * Centralized constants for the Medieval Sim mod
 * Consolidates all configuration values, limits, and identifiers
 */
public final class Constants {
    
    // ===== MOD METADATA =====
    public static final String MOD_ID = "medieval.sim";
    public static final String MOD_NAME = "Medieval Sim";
    
    // ===== LOGGING =====
    public static final String LOG_PREFIX = "MedievalSim: ";
    public static final String LOG_ERROR_PREFIX = "MedievalSim: ";
    public static final String LOG_WARNING_PREFIX = "MedievalSim: ";
    public static final String LOG_INFO_PREFIX = "MedievalSim: ";
    
    // ===== BUILD MODE =====
    public static final class BuildMode {
        // Game Data Keys (immutable identifiers)
        public static final String GND_BUILD_MODE = "medievalsim_buildmode";
        public static final String GND_SHAPE = "medievalsim_shape";
        public static final String GND_HOLLOW = "medievalsim_isHollow";
        public static final String GND_LINE_LENGTH = "medievalsim_lineLength";
        public static final String GND_SQUARE_SIZE = "medievalsim_squareSize";
        public static final String GND_CIRCLE_RADIUS = "medievalsim_circleRadius";
        public static final String GND_SPACING = "medievalsim_spacing";
        public static final String GND_DIRECTION = "medievalsim_direction";

        // UI Dimensions (immutable layout constants)
        public static final int MAIN_MENU_WIDTH = 300;
        public static final int MAIN_MENU_HEIGHT = 350;
        public static final int BUILD_TOOLS_WIDTH = 460;
        public static final int BUILD_TOOLS_HEIGHT = 480;
        public static final int UI_PADDING = 5;
        public static final int BUTTON_HEIGHT = 30;
        public static final int LABEL_HEIGHT = 20;
        public static final int SLIDER_HEIGHT = 30;

        // Visual Settings (immutable rendering constants)
        public static final float PREVIEW_ALPHA = 0.5f;
        public static final int PREVIEW_PRIORITY = -100000;
        public static final int TOOLTIP_PRIORITY = Integer.MAX_VALUE;
        public static final int TOOLTIP_OFFSET_X = 20;
        public static final int TOOLTIP_OFFSET_Y = -10;
        public static final int TOOLTIP_PADDING = 4;
        public static final int TOOLTIP_FONT_SIZE = 16;
        public static final int TOOLTIP_BG_ALPHA = 180;

        // Placement Range (immutable game constant)
        public static final int BUILD_MODE_PLACEMENT_RANGE = -1;

        // Localization Keys (immutable identifiers)
        public static final String LOC_CATEGORY_UI = "ui";
        public static final String LOC_BUILD_MODE_BLOCK_COST = "buildmodeblockcost";

        // NOTE: Configurable limits (min/max/defaults) moved to ModConfig.BuildMode

        private BuildMode() {} // Prevent instantiation
    }
    
    // ===== ZONES =====
    public static final class Zones {
        // Hard Limits (immutable constraints)
        public static final int MAX_ZONE_NAME_LENGTH = 50;
        public static final int MIN_FORCE_CLEAN_RADIUS = 5;
        public static final int MAX_FORCE_CLEAN_RADIUS = 500;

        // NOTE: Configurable zone settings (cooldowns, damage multipliers, etc.) moved to ModConfig.Zones

        private Zones() {} // Prevent instantiation
    }
    
    // ===== NETWORKING =====
    public static final class Network {
        // Packet timeouts and limits could go here
        public static final int DEFAULT_PACKET_TIMEOUT_MS = 5000;
        
        private Network() {} // Prevent instantiation
    }
    
    // ===== ADMIN TOOLS =====
    public static final class AdminTools {
        // Admin-specific constants
        public static final int DEFAULT_SELECTION_RADIUS = 5;
        
        private AdminTools() {} // Prevent instantiation
    }
    
    // ===== UI SYSTEM =====
    public static final class UI {
        // Grid System (8px base unit for consistent spacing)
        public static final int UNIT_SIZE = 8;
        
        // Base Spacing Constants
        public static final int MARGIN = UNIT_SIZE * 2;      // 16px - Standard margin
        public static final int PADDING = UNIT_SIZE;         // 8px - Standard padding
        public static final int SECTION_SPACING = UNIT_SIZE * 3; // 24px - Between sections
        public static final int ELEMENT_SPACING = UNIT_SIZE; // 8px - Between elements
        
        // Button Dimensions - Increased for better text readability
        public static final int MIN_BUTTON_WIDTH = UNIT_SIZE * 20;  // 160px (was 120px)
        public static final int STANDARD_BUTTON_WIDTH = UNIT_SIZE * 30; // 240px (was 200px)
        public static final int WIDE_BUTTON_WIDTH = UNIT_SIZE * 40;     // 320px (was 280px)
        public static final int BUTTON_HEIGHT = UNIT_SIZE * 4;          // 32px
        public static final int SMALL_BUTTON_HEIGHT = UNIT_SIZE * 3;    // 24px
        
        // Icon Sizing
        public static final int SMALL_ICON_SIZE = 16;   // SIZE_16
        public static final int MEDIUM_ICON_SIZE = 20;  // SIZE_20
        public static final int STANDARD_ICON_SIZE = 24; // SIZE_24
        public static final int LARGE_ICON_SIZE = 32;   // SIZE_32
        
        // Text and Input
        public static final int LABEL_HEIGHT = UNIT_SIZE * 3;    // 24px
        public static final int INPUT_HEIGHT = UNIT_SIZE * 4;    // 32px
        public static final int TEXT_PADDING = UNIT_SIZE;        // 8px on each side
        
        // Layout Helpers
        public static final int DIALOG_MIN_WIDTH = UNIT_SIZE * 40;  // 320px
        public static final int CONTENT_MAX_WIDTH = UNIT_SIZE * 60; // 480px

        // Slider Debounce (for preventing packet spam during slider adjustments)
        public static final long SLIDER_DEBOUNCE_MS = 200L;

        // Zone List Entry Dimensions
        public static final int ZONE_ENTRY_BUTTON_WIDTH = 180; // Width for zone action buttons

        private UI() {} // Prevent instantiation
    }
    
    // ===== COMMAND CENTER =====
    public static final class CommandCenter {
        // UI Dimensions (sized to fit on standard screens ~1080p)
        public static final int MIN_WIDTH = 400;
        public static final int MIN_HEIGHT = 300;
        public static final int MAX_WIDTH = 800;
        public static final int MAX_HEIGHT = 900;

        // Resize Handles
        public static final int RESIZE_EDGE_THRESHOLD = 5; // Pixels from edge to trigger resize
        public static final int RESIZE_CORNER_SIZE = 15; // Size of corner resize areas

        // Tab System
        public static final int TAB_BAR_HEIGHT = 35;
        public static final int TAB_BUTTON_WIDTH = 150;
        public static final int TAB_SPACING = 5;

        // Layout
        public static final int HEADER_HEIGHT = 70; // Search + dropdown area
        public static final int FAVORITES_HEIGHT = 40; // Favorites bar
        public static final int COMMAND_INFO_HEIGHT = 60; // Command name + description
        public static final int ACTION_BAR_HEIGHT = 40; // Bottom buttons
        public static final int SCROLL_BAR_WIDTH = 12; // Scrollbar width

        // Favorites
        public static final int FAVORITE_BUTTON_WIDTH = 55; // Width of each favorite button
        public static final int FAVORITE_BUTTON_HEIGHT = 28;

        // Dropdown
        public static final int DROPDOWN_MAX_VISIBLE_ITEMS = 12; // Max items before scrolling
        public static final int DROPDOWN_ITEM_HEIGHT = 20;

        // Parameter Area
        public static final int PARAM_LABEL_WIDTH = 120;
        public static final int PARAM_WIDGET_WIDTH = 250;
        public static final int PARAM_ROW_HEIGHT = 35;
        public static final int PARAM_PADDING = 8;
        
        // Tabs
        public enum Tab {
            CONSOLE_COMMANDS,
            MOD_SETTINGS,
            COMMAND_HISTORY
        }
        
        private CommandCenter() {} // Prevent instantiation
    }
    
    private Constants() {} // Prevent instantiation of main class
}
