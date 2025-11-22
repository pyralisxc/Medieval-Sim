package medievalsim.config;

import medievalsim.util.ModLogger;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;

/**
 * Unified configuration system for Medieval Sim mod.
 * 
 * This replaces the scattered Constants/RuntimeConstants/Settings pattern with
 * a clean, annotated configuration system that provides:
 * 
 * - Compile-time defaults
 * - Runtime validation
 * - Automatic persistence
 * - Type safety
 * - Clear documentation
 * 
 * USAGE:
 * ```java
 * // Get current value
 * int maxBlocks = ModConfig.BuildMode.maxBlocksPerPlacement;
 * 
 * // Set with validation
 * ModConfig.BuildMode.setMaxBlocksPerPlacement(500);
 * 
 * // Save/load automatically handled
 * ModConfig.save(saveData);
 * ModConfig.load(loadData);
 * ```
 */
public class ModConfig {
    
    // ===== ANNOTATIONS FOR CONFIGURATION =====
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface ConfigValue {
        /** Default value (for documentation) */
        String defaultValue() default "";
        /** Human-readable description */
        String description() default "";
        /** Minimum allowed value (for numbers) */
        double min() default Double.NEGATIVE_INFINITY;
        /** Maximum allowed value (for numbers) */
        double max() default Double.POSITIVE_INFINITY;
        /** Whether this value can be changed at runtime */
        boolean runtime() default true;
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface ConfigSection {
        /** Section name for save/load */
        String value();
        /** Section description */
        String description() default "";
    }
    
    // ===== BUILD MODE CONFIGURATION =====
    
    @ConfigSection(value = "BUILD_MODE", description = "Advanced construction tools configuration")
    public static class BuildMode {
        
        @ConfigValue(
            defaultValue = "500",
            description = "Maximum number of blocks that can be placed at once (anti-abuse limit)",
            min = 1, max = 1000
        )
        public static int maxBlocksPerPlacement = 500;
        
        @ConfigValue(
            defaultValue = "5",
            description = "Default line length for line shapes",
            min = 1, max = 50
        )
        public static int defaultLineLength = 5;
        
        @ConfigValue(
            defaultValue = "5", 
            description = "Default square size",
            min = 1, max = 25
        )
        public static int defaultSquareSize = 5;
        
        @ConfigValue(
            defaultValue = "5",
            description = "Default circle radius", 
            min = 1, max = 25
        )
        public static int defaultCircleRadius = 5;
        
        @ConfigValue(
            defaultValue = "1",
            description = "Default spacing between placements",
            min = 1, max = 10
        )
        public static int defaultSpacing = 1;
        
        // UI Preferences
        @ConfigValue(defaultValue = "false", description = "Remember build mode state between sessions")
        public static boolean rememberBuildModeState = false;
        
        // State persistence (runtime only)
        @ConfigValue(defaultValue = "0", description = "Last selected shape", runtime = true)
        public static int savedShape = 0;

        @ConfigValue(defaultValue = "false", description = "Last hollow state", runtime = true)
        public static boolean savedIsHollow = false;

        @ConfigValue(defaultValue = "5", description = "Last line length", runtime = true)
        public static int savedLineLength = 5;

        @ConfigValue(defaultValue = "5", description = "Last square size", runtime = true)
        public static int savedSquareSize = 5;

        @ConfigValue(defaultValue = "5", description = "Last circle radius", runtime = true)
        public static int savedCircleRadius = 5;

        @ConfigValue(defaultValue = "1", description = "Last spacing", runtime = true)
        public static int savedSpacing = 1;

        @ConfigValue(defaultValue = "0", description = "Last direction", runtime = true)
        public static int savedDirection = 0;

        // Setters with validation
        public static void setMaxBlocksPerPlacement(int value) {
            maxBlocksPerPlacement = validateInt(value, 1, 1000, "maxBlocksPerPlacement");
        }
        
        public static void setDefaultLineLength(int value) {
            defaultLineLength = validateInt(value, 1, 50, "defaultLineLength");
        }
        
        public static void setDefaultSquareSize(int value) {
            defaultSquareSize = validateInt(value, 1, 25, "defaultSquareSize");
        }
        
        public static void setDefaultCircleRadius(int value) {
            defaultCircleRadius = validateInt(value, 1, 25, "defaultCircleRadius");
        }
        
        public static void setDefaultSpacing(int value) {
            defaultSpacing = validateInt(value, 1, 10, "defaultSpacing");
        }
    }
    
    // ===== ZONE CONFIGURATION =====
    
    @ConfigSection(value = "ZONES", description = "Protected and PvP zone configuration")
    public static class Zones {
        
        @ConfigValue(
            defaultValue = "30000",
            description = "Re-entry cooldown (ms) after leaving a PvP zone",
            min = 0, max = 300000
        )
        public static long pvpReentryCooldownMs = 30000L;
        
        @ConfigValue(
            defaultValue = "10.0",
            description = "Seconds of immunity when entering/spawning in PvP zone",
            min = 0.0, max = 60.0
        )
        public static float pvpSpawnImmunitySeconds = 10.0f;
        
        @ConfigValue(
            defaultValue = "0.05",
            description = "Default PvP damage multiplier (0.05 = 5%)",
            min = 0.0, max = 1.0
        )
        public static float defaultDamageMultiplier = 0.05f;
        
        @ConfigValue(
            defaultValue = "1000",
            description = "Maximum barrier tiles to place before skipping",
            min = 10, max = 10000
        )
        public static int maxBarrierTiles = 1000;
        
        @ConfigValue(
            defaultValue = "50",
            description = "Batch size for barrier placement to avoid lag",
            min = 1, max = 200
        )
        public static int barrierAddBatchSize = 50;
        
        @ConfigValue(
            defaultValue = "10",
            description = "Max barrier tiles processed per server tick",
            min = 1, max = 100
        )
        public static int barrierMaxTilesPerTick = 10;
        
        @ConfigValue(
            defaultValue = "30",
            description = "Default combat lock duration (seconds)",
            min = 0, max = 300
        )
        public static int defaultCombatLockSeconds = 30;
        
        @ConfigValue(
            defaultValue = "100",
            description = "Default force-clean radius for PvP zones",
            min = 10, max = 500
        )
        public static int defaultForceCleanRadius = 100;
        
        // Setters with validation
        public static void setPvpReentryCooldownMs(long value) {
            pvpReentryCooldownMs = validateLong(value, 0L, 300000L, "pvpReentryCooldownMs");
        }
        
        public static void setPvpSpawnImmunitySeconds(float value) {
            pvpSpawnImmunitySeconds = validateFloat(value, 0.0f, 60.0f, "pvpSpawnImmunitySeconds");
        }
        
        public static void setDefaultDamageMultiplier(float value) {
            defaultDamageMultiplier = validateFloat(value, 0.0f, 1.0f, "defaultDamageMultiplier");
        }
        
        public static void setMaxBarrierTiles(int value) {
            maxBarrierTiles = validateInt(value, 10, 10000, "maxBarrierTiles");
        }
        
        public static void setBarrierAddBatchSize(int value) {
            barrierAddBatchSize = validateInt(value, 1, 200, "barrierAddBatchSize");
        }
        
        public static void setBarrierMaxTilesPerTick(int value) {
            barrierMaxTilesPerTick = validateInt(value, 1, 100, "barrierMaxTilesPerTick");
        }
        
        public static void setDefaultCombatLockSeconds(int value) {
            defaultCombatLockSeconds = validateInt(value, 0, 300, "defaultCombatLockSeconds");
        }
    }
    
    // ===== COMMAND CENTER CONFIGURATION =====
    
    @ConfigSection(value = "ADMIN_HUD", description = "Admin Tools HUD and Command Center UI configuration")
    public static class CommandCenter {

        @ConfigValue(defaultValue = "600", description = "Default Admin HUD width (applies to all admin tools screens)", min = 400, max = 1200)
        public static int defaultWidth = 600;

        @ConfigValue(defaultValue = "500", description = "Default Admin HUD height (applies to all admin tools screens)", min = 300, max = 800)
        public static int defaultHeight = 500;

        @ConfigValue(defaultValue = "10", description = "Maximum favorite commands", min = 1, max = 50)
        public static int maxFavorites = 10;

        @ConfigValue(defaultValue = "20", description = "Maximum history entries", min = 5, max = 100)
        public static int maxHistory = 20;

        @ConfigValue(defaultValue = "240", description = "Preferred width of Admin HUD main menu buttons", min = 160, max = 600)
        public static int mainMenuButtonWidth = 240;

        // Minimized HUD bar dimensions
        @ConfigValue(defaultValue = "160", description = "Width of minimized Admin HUD bar", min = 120, max = 400)
        public static int minimizedWidth = 160;

        @ConfigValue(defaultValue = "30", description = "Height of minimized Admin HUD bar", min = 20, max = 60)
        public static int minimizedHeight = 30;

        // HUD size state (runtime - last used size, per player)
        @ConfigValue(defaultValue = "600", description = "Last used Admin HUD width (per player)", runtime = true)
        public static int currentWidth = 600;

        @ConfigValue(defaultValue = "500", description = "Last used Admin HUD height (per player)", runtime = true)
        public static int currentHeight = 500;
    }

    // ===== SAVE/LOAD FUNCTIONALITY =====
    
    /**
     * Save all configuration sections to SaveData.
     * Uses reflection to automatically save all @ConfigValue annotated fields.
     */
    public static void saveToData(SaveData parentSave) {
        try {
            // Save BuildMode section
            SaveData buildModeData = new SaveData("BUILD_MODE");
            saveSectionToData(BuildMode.class, buildModeData);
            parentSave.addSaveData(buildModeData);
            
            // Save Zones section
            SaveData zonesData = new SaveData("ZONES");
            saveSectionToData(Zones.class, zonesData);
            parentSave.addSaveData(zonesData);
            
            // Save CommandCenter section
            SaveData commandCenterData = new SaveData("COMMAND_CENTER");
            saveSectionToData(CommandCenter.class, commandCenterData);
            parentSave.addSaveData(commandCenterData);
            
            ModLogger.debug("Saved configuration to data");
            
        } catch (Exception e) {
            ModLogger.error("Failed to save configuration: %s", e.getMessage());
        }
    }
    
    /**
     * Load all configuration sections from LoadData.
     * Uses reflection to automatically load all @ConfigValue annotated fields.
     */
    public static void loadFromData(LoadData parentLoad) {
        try {
            // Load BuildMode section
            LoadData buildModeData = parentLoad.getFirstLoadDataByName("BUILD_MODE");
            if (buildModeData != null) {
                loadSectionFromData(BuildMode.class, buildModeData);
            }
            
            // Load Zones section
            LoadData zonesData = parentLoad.getFirstLoadDataByName("ZONES");
            if (zonesData != null) {
                loadSectionFromData(Zones.class, zonesData);
            }
            
            // Load CommandCenter section
            LoadData commandCenterData = parentLoad.getFirstLoadDataByName("COMMAND_CENTER");
            if (commandCenterData != null) {
                loadSectionFromData(CommandCenter.class, commandCenterData);
            }
            
            ModLogger.debug("Loaded configuration from data");
            
        } catch (Exception e) {
            ModLogger.error("Failed to load configuration: %s", e.getMessage());
        }
    }
    
    /**
     * Reset all configuration to defaults.
     */
    public static void resetToDefaults() {
        // BuildMode defaults
        BuildMode.maxBlocksPerPlacement = 500;
        BuildMode.defaultLineLength = 5;
        BuildMode.defaultSquareSize = 5;
        BuildMode.defaultCircleRadius = 5;
        BuildMode.defaultSpacing = 1;
        BuildMode.rememberBuildModeState = false;
        BuildMode.savedShape = 0;
        BuildMode.savedIsHollow = false;
        BuildMode.savedLineLength = 5;
        BuildMode.savedSquareSize = 5;
        BuildMode.savedCircleRadius = 5;
        BuildMode.savedSpacing = 1;
        BuildMode.savedDirection = 0;
        
        // Zones defaults
        Zones.pvpReentryCooldownMs = 30000L;
        Zones.pvpSpawnImmunitySeconds = 10.0f;
        Zones.defaultDamageMultiplier = 0.05f;
        Zones.maxBarrierTiles = 1000;
        Zones.barrierAddBatchSize = 50;
        Zones.barrierMaxTilesPerTick = 10;
        Zones.defaultCombatLockSeconds = 30;
        Zones.defaultForceCleanRadius = 100;
        
        // CommandCenter defaults
        CommandCenter.defaultWidth = 600;
        CommandCenter.defaultHeight = 500;
        CommandCenter.maxFavorites = 10;
        CommandCenter.maxHistory = 20;
        CommandCenter.currentWidth = 600;
        CommandCenter.currentHeight = 500;

        ModLogger.info("Reset configuration to defaults");
    }
    
    // ===== REFLECTION HELPERS =====
    
    private static void saveSectionToData(Class<?> sectionClass, SaveData sectionData) {
        Field[] fields = sectionClass.getDeclaredFields();
        
        for (Field field : fields) {
            ConfigValue annotation = field.getAnnotation(ConfigValue.class);
            if (annotation == null) continue;
            
            try {
                field.setAccessible(true);
                String fieldName = field.getName();
                Object value = field.get(null); // Static field
                
                // Save based on type
                if (field.getType() == int.class) {
                    sectionData.addInt(fieldName, (Integer) value, annotation.description());
                } else if (field.getType() == long.class) {
                    sectionData.addLong(fieldName, (Long) value, annotation.description());
                } else if (field.getType() == float.class) {
                    sectionData.addFloat(fieldName, (Float) value, annotation.description());
                } else if (field.getType() == boolean.class) {
                    sectionData.addBoolean(fieldName, (Boolean) value, annotation.description());
                } else if (field.getType() == String.class) {
                    sectionData.addUnsafeString(fieldName, (String) value, annotation.description());
                }
                
            } catch (Exception e) {
                ModLogger.warn("Failed to save config field %s: %s", field.getName(), e.getMessage());
            }
        }
    }
    
    private static void loadSectionFromData(Class<?> sectionClass, LoadData sectionData) {
        Field[] fields = sectionClass.getDeclaredFields();
        
        for (Field field : fields) {
            ConfigValue annotation = field.getAnnotation(ConfigValue.class);
            if (annotation == null) continue;
            
            try {
                field.setAccessible(true);
                String fieldName = field.getName();
                
                // Load based on type with defaults
                if (field.getType() == int.class) {
                    int defaultValue = field.getInt(null);
                    int loadedValue = sectionData.getInt(fieldName, defaultValue);
                    
                    // Apply validation if specified
                    if (annotation.min() != Double.NEGATIVE_INFINITY || annotation.max() != Double.POSITIVE_INFINITY) {
                        loadedValue = validateInt(loadedValue, (int) annotation.min(), (int) annotation.max(), fieldName);
                    }
                    
                    field.setInt(null, loadedValue);
                    
                } else if (field.getType() == long.class) {
                    long defaultValue = field.getLong(null);
                    long loadedValue = sectionData.getLong(fieldName, defaultValue);
                    
                    if (annotation.min() != Double.NEGATIVE_INFINITY || annotation.max() != Double.POSITIVE_INFINITY) {
                        loadedValue = validateLong(loadedValue, (long) annotation.min(), (long) annotation.max(), fieldName);
                    }
                    
                    field.setLong(null, loadedValue);
                    
                } else if (field.getType() == float.class) {
                    float defaultValue = field.getFloat(null);
                    float loadedValue = sectionData.getFloat(fieldName, defaultValue);
                    
                    if (annotation.min() != Double.NEGATIVE_INFINITY || annotation.max() != Double.POSITIVE_INFINITY) {
                        loadedValue = validateFloat(loadedValue, (float) annotation.min(), (float) annotation.max(), fieldName);
                    }
                    
                    field.setFloat(null, loadedValue);
                    
                } else if (field.getType() == boolean.class) {
                    boolean defaultValue = field.getBoolean(null);
                    boolean loadedValue = sectionData.getBoolean(fieldName, defaultValue);
                    field.setBoolean(null, loadedValue);
                    
                } else if (field.getType() == String.class) {
                    String defaultValue = (String) field.get(null);
                    String loadedValue = sectionData.getUnsafeString(fieldName, defaultValue);
                    field.set(null, loadedValue);
                }
                
            } catch (Exception e) {
                ModLogger.warn("Failed to load config field %s: %s", field.getName(), e.getMessage());
            }
        }
    }
    
    /**
     * Get configuration summary for debugging.
     */
    public static String getConfigSummary() {
        StringBuilder sb = new StringBuilder("Configuration Summary:\n");
        
        sb.append("BuildMode:\n");
        sb.append(String.format("  maxBlocksPerPlacement: %d\n", BuildMode.maxBlocksPerPlacement));
        sb.append(String.format("  defaultLineLength: %d\n", BuildMode.defaultLineLength));
        sb.append(String.format("  defaultSquareSize: %d\n", BuildMode.defaultSquareSize));
        
        sb.append("Zones:\n");
        sb.append(String.format("  pvpReentryCooldownMs: %d\n", Zones.pvpReentryCooldownMs));
        sb.append(String.format("  defaultDamageMultiplier: %.2f\n", Zones.defaultDamageMultiplier));
        sb.append(String.format("  maxBarrierTiles: %d\n", Zones.maxBarrierTiles));
        
        sb.append("Admin HUD / Command Center:\n");
        sb.append(String.format("  defaultWidth: %d\n", CommandCenter.defaultWidth));
        sb.append(String.format("  defaultHeight: %d\n", CommandCenter.defaultHeight));
        sb.append(String.format("  minimizedWidth: %d\n", CommandCenter.minimizedWidth));
        sb.append(String.format("  minimizedHeight: %d\n", CommandCenter.minimizedHeight));
        sb.append(String.format("  currentWidth: %d\n", CommandCenter.currentWidth));
        sb.append(String.format("  currentHeight: %d\n", CommandCenter.currentHeight));
        sb.append(String.format("  maxFavorites: %d\n", CommandCenter.maxFavorites));
        sb.append(String.format("  maxHistory: %d\n", CommandCenter.maxHistory));
        sb.append(String.format("  mainMenuButtonWidth: %d\n", CommandCenter.mainMenuButtonWidth));

        return sb.toString();
    }
    
    // ===== VALIDATION HELPERS =====
    
    private static int validateInt(int value, int min, int max, String fieldName) {
        if (value < min || value > max) {
            ModLogger.warn("Config value %s (%d) out of range [%d, %d], clamping", fieldName, value, min, max);
            return Math.max(min, Math.min(max, value));
        }
        return value;
    }
    
    private static long validateLong(long value, long min, long max, String fieldName) {
        if (value < min || value > max) {
            ModLogger.warn("Config value %s (%d) out of range [%d, %d], clamping", fieldName, value, min, max);
            return Math.max(min, Math.min(max, value));
        }
        return value;
    }
    
    private static float validateFloat(float value, float min, float max, String fieldName) {
        if (value < min || value > max) {
            ModLogger.warn("Config value %s (%.2f) out of range [%.2f, %.2f], clamping", fieldName, value, min, max);
            return Math.max(min, Math.min(max, value));
        }
        return value;
    }
}
