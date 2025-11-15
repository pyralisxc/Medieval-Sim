package medievalsim.commandcenter.settings;

import medievalsim.config.ModConfig;
import medievalsim.util.Constants;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Scans ModConfig and Constants classes to automatically discover
 * configurable settings and generate AdminSetting objects.
 *
 * NOTE: This class is currently UNUSED - the Command Center doesn't use this reflection-based
 * settings system. It's kept for potential future use.
 */
public class SettingsScanner {

    /**
     * Scan all constants and configurable settings
     * @return Map of category name to list of settings
     */
    public static Map<String, List<AdminSetting<?>>> scanAllSettings() {
        Map<String, List<AdminSetting<?>>> settingsByCategory = new HashMap<>();

        // Scan static constants (read-only)
        scanConstantsClass(Constants.BuildMode.class, "Build Mode Constants", settingsByCategory, true);
        scanConstantsClass(Constants.Zones.class, "Zone Constants", settingsByCategory, true);
        scanConstantsClass(Constants.AdminTools.class, "Admin Tools Constants", settingsByCategory, true);
        scanConstantsClass(Constants.Network.class, "Network Constants", settingsByCategory, true);

        // Scan ModConfig sections (configurable settings)
        scanModConfigClass(ModConfig.BuildMode.class, "Build Mode Settings", settingsByCategory);
        scanModConfigClass(ModConfig.Zones.class, "Zone Settings", settingsByCategory);
        scanModConfigClass(ModConfig.CommandCenter.class, "Command Center Settings", settingsByCategory);

        return settingsByCategory;
    }
    
    /**
     * Scan a static constants class (read-only settings)
     */
    private static void scanConstantsClass(Class<?> clazz, String category, 
                                          Map<String, List<AdminSetting<?>>> map, 
                                          boolean requiresRestart) {
        List<AdminSetting<?>> settings = new ArrayList<>();
        
        for (Field field : clazz.getDeclaredFields()) {
            // Only scan public static final fields
            int modifiers = field.getModifiers();
            if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) {
                continue;
            }
            
            try {
                field.setAccessible(true);
                Object value = field.get(null);
                AdminSetting<?> setting = createReadOnlySetting(field.getName(), value, category, requiresRestart);
                if (setting != null) {
                    settings.add(setting);
                }
            } catch (Exception e) {
                // Skip fields that can't be accessed
            }
        }
        
        if (!settings.isEmpty()) {
            map.put(category, settings);
        }
    }
    
    /**
     * Scan a ModConfig section class (configurable settings with public static fields)
     */
    private static void scanModConfigClass(Class<?> clazz, String category,
                                           Map<String, List<AdminSetting<?>>> map) {
        List<AdminSetting<?>> settings = new ArrayList<>();

        // Scan public static fields (ModConfig uses public static fields)
        for (Field field : clazz.getDeclaredFields()) {
            if (!Modifier.isPublic(field.getModifiers()) || !Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            try {
                Object value = field.get(null);
                AdminSetting<?> setting = createModConfigSetting(field.getName(), value, category, field);
                if (setting != null) {
                    settings.add(setting);
                }
            } catch (IllegalAccessException e) {
                // Skip fields we can't access
            }
        }

        if (!settings.isEmpty()) {
            map.put(category, settings);
        }
    }
    
    /**
     * Create a read-only setting from a constant field
     */
    private static AdminSetting<?> createReadOnlySetting(String fieldName, Object value, 
                                                         String category, boolean requiresRestart) {
        if (value instanceof Integer) {
            return new AdminSetting.Builder<Integer>(
                fieldName,
                formatFieldName(fieldName),
                SettingType.INTEGER
            )
            .category(category)
            .defaultValue((Integer) value)
            .requiresRestart(requiresRestart)
            .getter(() -> (Integer) value)
            .build();
        } else if (value instanceof Long) {
            return new AdminSetting.Builder<Long>(
                fieldName,
                formatFieldName(fieldName),
                SettingType.LONG
            )
            .category(category)
            .defaultValue((Long) value)
            .requiresRestart(requiresRestart)
            .getter(() -> (Long) value)
            .build();
        } else if (value instanceof Float) {
            return new AdminSetting.Builder<Float>(
                fieldName,
                formatFieldName(fieldName),
                SettingType.FLOAT
            )
            .category(category)
            .defaultValue((Float) value)
            .requiresRestart(requiresRestart)
            .getter(() -> (Float) value)
            .build();
        } else if (value instanceof Boolean) {
            return new AdminSetting.Builder<Boolean>(
                fieldName,
                formatFieldName(fieldName),
                SettingType.BOOLEAN
            )
            .category(category)
            .defaultValue((Boolean) value)
            .requiresRestart(requiresRestart)
            .getter(() -> (Boolean) value)
            .build();
        } else if (value instanceof String) {
            return new AdminSetting.Builder<String>(
                fieldName,
                formatFieldName(fieldName),
                SettingType.STRING
            )
            .category(category)
            .defaultValue((String) value)
            .requiresRestart(requiresRestart)
            .getter(() -> (String) value)
            .build();
        }
        
        return null; // Unsupported type
    }
    
    /**
     * Create a ModConfig setting from a public static field
     */
    private static AdminSetting<?> createModConfigSetting(String fieldName, Object value,
                                                          String category, Field field) {
        Class<?> fieldType = field.getType();

        try {
            if (fieldType == int.class || fieldType == Integer.class) {
                return new AdminSetting.Builder<Integer>(
                    fieldName,
                    formatFieldName(fieldName),
                    SettingType.INTEGER
                )
                .category(category)
                .getter(() -> {
                    try {
                        return (Integer) field.get(null);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .setter(value2 -> {
                    try {
                        field.set(null, value2);
                    } catch (Exception e) {
                        // Handle error
                    }
                })
                .build();
            } else if (fieldType == long.class || fieldType == Long.class) {
                return new AdminSetting.Builder<Long>(
                    fieldName,
                    formatFieldName(fieldName),
                    SettingType.LONG
                )
                .category(category)
                .getter(() -> {
                    try {
                        return (Long) field.get(null);
                    } catch (Exception e) {
                        return 0L;
                    }
                })
                .setter(value2 -> {
                    try {
                        field.set(null, value2);
                    } catch (Exception e) {
                        // Handle error
                    }
                })
                .build();
            } else if (fieldType == float.class || fieldType == Float.class) {
                return new AdminSetting.Builder<Float>(
                    fieldName,
                    formatFieldName(fieldName),
                    SettingType.FLOAT
                )
                .category(category)
                .getter(() -> {
                    try {
                        return (Float) field.get(null);
                    } catch (Exception e) {
                        return 0.0f;
                    }
                })
                .setter(value2 -> {
                    try {
                        field.set(null, value2);
                    } catch (Exception e) {
                        // Handle error
                    }
                })
                .build();
            }
        } catch (Exception e) {
            // Skip if error
        }

        return null;
    }
    
    /**
     * Format a field name into a display name
     * Example: "MAX_LINE_LENGTH" -> "Max Line Length"
     */
    private static String formatFieldName(String fieldName) {
        String[] words = fieldName.split("_");
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            if (result.length() > 0) {
                result.append(" ");
            }
            // Capitalize first letter, lowercase the rest
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }
        
        return result.toString();
    }
}
