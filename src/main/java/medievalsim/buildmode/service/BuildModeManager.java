package medievalsim.buildmode.service;
import medievalsim.buildmode.util.BuildModePreviewElement;
import necesse.engine.Settings;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.modLoader.LoadedMod;
import necesse.engine.modLoader.ModLoader;
import necesse.engine.network.client.Client;
import necesse.level.maps.Level;
import necesse.level.maps.hudManager.HudDrawElement;

public class BuildModeManager {
    private static BuildModeManager instance;
    public static final int SHAPE_NORMAL = 0;
    public static final int SHAPE_LINE = 1;
    public static final int SHAPE_CROSS = 2;
    public static final int SHAPE_L = 3;
    public static final int SHAPE_T = 4;
    public static final int SHAPE_SQUARE = 5;
    public static final int SHAPE_CIRCLE = 6;
    public static final int SHAPE_DIAMOND = 7;
    public static final int SHAPE_HALF_CIRCLE = 8;
    public static final int SHAPE_TRIANGLE = 9;
    public static final int DIRECTION_UP = 0;
    public static final int DIRECTION_DOWN = 1;
    public static final int DIRECTION_LEFT = 2;
    public static final int DIRECTION_RIGHT = 3;
    public boolean buildModeEnabled = false;
    public int selectedShape = 0;
    public boolean isHollow = false;
    public int lineLength = 5;
    public int squareSize = 5;
    public int circleRadius = 5;
    public int spacing = 1;
    public int direction = 0;
    private Client client;
    private BuildModePreviewElement previewElement;
    private Level currentLevel;
    private boolean settingsDirty = false;
    private long lastSettingsSaveTime = 0L;

    private BuildModeManager(Client client) {
        this.client = client;
        this.loadSettings();
    }

    public static BuildModeManager getInstance(Client client) {
        if (client == null) {
            throw new IllegalArgumentException("Client cannot be null when getting BuildModeManager instance");
        }
        if (instance == null) {
            instance = new BuildModeManager(client);
        } else if (BuildModeManager.instance.client == null) {
            // Instance exists but client is null - reinitialize with new client
            BuildModeManager.instance.client = client;
        } else if (BuildModeManager.instance.client != client) {
            // Different client - cleanup and create new instance
            instance.cleanup();
            instance = new BuildModeManager(client);
        }
        return instance;
    }

    public static BuildModeManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("BuildModeManager not initialized! Call getInstance(Client) first.");
        }
        if (BuildModeManager.instance.client == null) {
            throw new IllegalStateException("BuildModeManager has null client! This should never happen.");
        }
        return instance;
    }

    public static boolean hasInstance() {
        return instance != null;
    }

    public static boolean isActive() {
        return instance != null && BuildModeManager.instance.buildModeEnabled;
    }

    private void cleanup() {
        this.removePreviewElement();
        this.buildModeEnabled = false;
    }

    private boolean isClientValid() {
        return this.client != null && !this.client.hasDisconnected();
    }

    public void setBuildModeEnabled(boolean enabled) {
        PermissionLevel level;
        if (!this.isClientValid()) {
            medievalsim.util.ModLogger.error("Cannot set build mode - client is invalid or disconnected");
            return;
        }
        if (enabled && ((level = this.client.getPermissionLevel()) == null || level.getLevel() < PermissionLevel.ADMIN.getLevel())) {
            medievalsim.util.ModLogger.warn("Player attempted to enable build mode without ADMIN permission");
            return;
        }
        this.buildModeEnabled = enabled;
        if (enabled) {
            this.addPreviewElement();
        } else {
            this.removePreviewElement();
        }
    }

    public void checkLevelChange() {
        Level level;
        if (!this.buildModeEnabled) {
            return;
        }
        level = this.isClientValid() ? this.client.getLevel() : null;
        if (level != this.currentLevel) {
            this.currentLevel = level;
            if (this.previewElement != null) {
                this.removePreviewElement();
                this.addPreviewElement();
            }
        }
    }

    private void addPreviewElement() {
        if (this.client == null || this.client.getPlayer() == null) {
            return;
        }
        Level level = this.client.getLevel();
        if (level == null || level.hudManager == null) {
            return;
        }
        this.removePreviewElement();
        this.previewElement = new BuildModePreviewElement(this.client);
        level.hudManager.addElement((HudDrawElement)this.previewElement);
        this.currentLevel = level;
    }

    private void removePreviewElement() {
        if (this.previewElement != null) {
            this.previewElement.remove();
            this.previewElement = null;
        }
        this.currentLevel = null;
    }

    public void setShape(int shape) {
        if (shape < 0 || shape > 9) {
            medievalsim.util.ModLogger.error("Invalid shape %d, must be 0-9", shape);
            return;
        }
        this.selectedShape = shape;
        this.saveSettings();
    }

    public void setHollow(boolean hollow) {
        this.isHollow = hollow;
        this.saveSettings();
    }

    public boolean canBeHollow() {
        return this.selectedShape == 5 || this.selectedShape == 6 || this.selectedShape == 7 || this.selectedShape == 8 || this.selectedShape == 9;
    }

    public void setLineLength(int length) {
        if (length < 1 || length > 50) {
            medievalsim.util.ModLogger.error("Invalid line length %d, must be 1-50", length);
            return;
        }
        this.lineLength = length;
        this.saveSettings();
    }

    public void setSquareSize(int size) {
        if (size < 1 || size > 25) {
            medievalsim.util.ModLogger.error("Invalid square size %d, must be 1-25", size);
            return;
        }
        this.squareSize = size;
        this.saveSettings();
    }

    public void setCircleRadius(int radius) {
        if (radius < 1 || radius > 25) {
            medievalsim.util.ModLogger.error("Invalid circle radius %d, must be 1-25", radius);
            return;
        }
        this.circleRadius = radius;
        this.saveSettings();
    }

    public void setSpacing(int spacing) {
        if (spacing < 1 || spacing > 10) {
            medievalsim.util.ModLogger.error("Invalid spacing %d, must be 1-10", spacing);
            return;
        }
        this.spacing = spacing;
        this.saveSettings();
    }

    public void setDirection(int direction) {
        if (direction < 0 || direction > 3) {
            medievalsim.util.ModLogger.error("Invalid direction %d, must be 0-3", direction);
            return;
        }
        this.direction = direction;
        this.saveSettings();
    }

    public String getShapeName(int shape, boolean hollow) {
        String baseName;
        switch (shape) {
            case 0: {
                return "Normal";
            }
            case 1: {
                return "Line";
            }
            case 2: {
                return "Cross";
            }
            case 3: {
                return "L";
            }
            case 4: {
                return "T";
            }
            case 5: {
                baseName = "Square";
                break;
            }
            case 6: {
                baseName = "Circle";
                break;
            }
            case 7: {
                baseName = "Diamond";
                break;
            }
            case 8: {
                baseName = "Half Circle";
                break;
            }
            case 9: {
                baseName = "Triangle";
                break;
            }
            default: {
                return "Unknown";
            }
        }
        if (hollow && this.canBeHollow()) {
            return "Hollow " + baseName;
        }
        return baseName;
    }

    private void loadSettings() {
        try {
            LoadedMod mod = ModLoader.getEnabledMods().stream().filter(m -> m.id.equals("medieval.sim")).findFirst().orElse(null);
            if (mod != null && mod.getSettings() instanceof medievalsim.config.UnifiedMedievalSimSettings) {
                // Load directly from ModConfig
                this.selectedShape = medievalsim.config.ModConfig.BuildMode.savedShape;
                this.isHollow = medievalsim.config.ModConfig.BuildMode.savedIsHollow;
                this.lineLength = medievalsim.config.ModConfig.BuildMode.savedLineLength;
                this.squareSize = medievalsim.config.ModConfig.BuildMode.savedSquareSize;
                this.circleRadius = medievalsim.config.ModConfig.BuildMode.savedCircleRadius;
                this.spacing = medievalsim.config.ModConfig.BuildMode.savedSpacing;
                this.direction = medievalsim.config.ModConfig.BuildMode.savedDirection;
                medievalsim.util.ModLogger.info("Loaded build mode settings from config");
            }
        }
        catch (Exception e) {
            medievalsim.util.ModLogger.warn("Failed to load build mode settings: %s", e.getMessage());
        }
    }

    public void saveSettings() {
        this.settingsDirty = true;
        this.lastSettingsSaveTime = System.currentTimeMillis();
    }

    private void saveSettingsNow() {
        try {
            LoadedMod mod = ModLoader.getEnabledMods().stream().filter(m -> m.id.equals("medieval.sim")).findFirst().orElse(null);
            if (mod != null && mod.getSettings() instanceof medievalsim.config.UnifiedMedievalSimSettings) {
                // Save directly to ModConfig
                medievalsim.config.ModConfig.BuildMode.savedShape = this.selectedShape;
                medievalsim.config.ModConfig.BuildMode.savedIsHollow = this.isHollow;
                medievalsim.config.ModConfig.BuildMode.savedLineLength = this.lineLength;
                medievalsim.config.ModConfig.BuildMode.savedSquareSize = this.squareSize;
                medievalsim.config.ModConfig.BuildMode.savedCircleRadius = this.circleRadius;
                medievalsim.config.ModConfig.BuildMode.savedSpacing = this.spacing;
                medievalsim.config.ModConfig.BuildMode.savedDirection = this.direction;
                Settings.saveClientSettings();
                medievalsim.util.ModLogger.info("Saved build mode settings to config");
                this.settingsDirty = false;
            }
        }
        catch (Exception e) {
            medievalsim.util.ModLogger.warn("Failed to save build mode settings: %s", e.getMessage());
        }
    }

    public void tick() {
        if (this.settingsDirty && System.currentTimeMillis() - this.lastSettingsSaveTime >= 2000L) {
            this.saveSettingsNow();
        }
    }
}

