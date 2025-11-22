package medievalsim.commandcenter.settings;

import medievalsim.util.ModLogger;
import necesse.engine.modLoader.LoadedMod;
import necesse.engine.modLoader.ModLoader;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Universal scanner that discovers ModConfig classes in ALL loaded mods.
 *
 * Scans for the pattern: <package>.config.ModConfig
 * Looks for @ConfigSection nested classes and @ConfigValue annotated fields.
 *
 * Works via reflection - doesn't require compile-time dependency on annotations.
 */
public class UniversalModConfigScanner {
    // Simple cache so we only pay the reflection cost once per session.
    // The ConfigurableSetting objects always read live field values, so
    // cached metadata will still display current values, not defaults.
    private static Map<LoadedMod, List<ModConfigSection>> cachedResults;
    private static boolean cacheInitialized;

    /**
     * Clear the cached scan results (primarily for testing or advanced use).
     */
    public static synchronized void clearCache() {
        cachedResults = null;
        cacheInitialized = false;
    }



    /**
     * Scan ALL loaded mods for ModConfig classes.
     * Results are cached so we only pay the reflection cost once per session.
     *
     * @return Map of LoadedMod to list of config sections found
     */
    public static synchronized Map<LoadedMod, List<ModConfigSection>> scanAllMods() {
        if (cacheInitialized && cachedResults != null) {
            return cachedResults;
        }

        ModLogger.info("Scanning for ModConfig classes across all enabled mods...");

        Map<LoadedMod, List<ModConfigSection>> result = new LinkedHashMap<>();

        for (LoadedMod mod : ModLoader.getEnabledMods()) {
            try {
                List<ModConfigSection> sections = scanMod(mod);
                if (!sections.isEmpty()) {
                    result.put(mod, sections);
                    ModLogger.info("Found ModConfig in mod '%s' with %d sections",
                                   mod.name, sections.size());
                }
            } catch (Exception e) {
                // Silently skip mods that don't have ModConfig or have errors
                ModLogger.debug("Skipping mod '%s': %s", mod.id, e.getMessage());
            }
        }

        ModLogger.info("Scanned %d mods, found ModConfig in %d mods",
                       ModLoader.getEnabledMods().size(), result.size());

        cachedResults = result;
        cacheInitialized = true;

        return cachedResults;
    }

    /**
     * Scan a single mod for config.ModConfig class
     * @return List of config sections found (empty if no ModConfig)
     */
    private static List<ModConfigSection> scanMod(LoadedMod mod) {
        try {
            // Try to find: <package>.config.ModConfig
            String basePackage = mod.id.replace(".", "");
            String configClassName = basePackage + ".config.ModConfig";

            ModLogger.debug("Trying to load: %s for mod %s", configClassName, mod.id);

            // Use SystemClassLoader (where mod classes are loaded by Necesse)
            ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
            Class<?> modConfigClass = Class.forName(configClassName, true, systemClassLoader);
            ModLogger.info("✓ Found ModConfig class: %s", configClassName);

            return scanModConfigClass(mod, modConfigClass);

        } catch (ClassNotFoundException e) {
            // This mod doesn't use ModConfig pattern - not an error
            ModLogger.debug("No ModConfig found for mod: %s (tried: %s)",
                           mod.id, mod.id.replace(".", "") + ".config.ModConfig");
            return Collections.emptyList();
        } catch (Exception e) {
            ModLogger.error("Error scanning mod: " + mod.id, e);
            return Collections.emptyList();
        }
    }

    /**
     * Scan ModConfig class for @ConfigSection nested classes
     */
    private static List<ModConfigSection> scanModConfigClass(LoadedMod mod, Class<?> configClass) {
        List<ModConfigSection> sections = new ArrayList<>();

        // Scan nested static classes (BuildMode, Zones, etc.)
        for (Class<?> nestedClass : configClass.getDeclaredClasses()) {
            if (!Modifier.isStatic(nestedClass.getModifiers())) {
                continue; // Skip non-static nested classes
            }

            // Extract section metadata
            String sectionName = extractSectionName(nestedClass);
            String sectionDescription = extractSectionDescription(nestedClass);

            // Scan fields in this section
            List<ConfigurableSetting> settings = scanSectionFields(mod, sectionName, nestedClass);

            if (!settings.isEmpty()) {
                ModConfigSection section = new ModConfigSection(sectionName, sectionDescription, settings);
                sections.add(section);
                ModLogger.debug("  Section '%s': %d settings", sectionName, settings.size());
            }
        }

        return sections;
    }

    /**
     * Extract section name from @ConfigSection annotation or class name
     */
    private static String extractSectionName(Class<?> sectionClass) {
        // Look for @ConfigSection annotation (by name, not class reference)
        for (Annotation annotation : sectionClass.getAnnotations()) {
            if (annotation.annotationType().getSimpleName().equals("ConfigSection")) {
                try {
                    Method valueMethod = annotation.annotationType().getMethod("value");
                    String value = (String) valueMethod.invoke(annotation);
                    if (value != null && !value.isEmpty()) {
                        return value;
                    }
                } catch (Exception e) {
                    // Fall through to default
                }
            }
        }

        // Default: use class simple name
        return sectionClass.getSimpleName();
    }

    /**
     * Extract section description from @ConfigSection annotation
     */
    private static String extractSectionDescription(Class<?> sectionClass) {
        for (Annotation annotation : sectionClass.getAnnotations()) {
            if (annotation.annotationType().getSimpleName().equals("ConfigSection")) {
                try {
                    Method descMethod = annotation.annotationType().getMethod("description");
                    return (String) descMethod.invoke(annotation);
                } catch (Exception e) {
                    // No description
                }
            }
        }
        return "";
    }

    /**
     * Scan fields in a section class for @ConfigValue annotations
     */
    private static List<ConfigurableSetting> scanSectionFields(LoadedMod mod, String sectionName, Class<?> sectionClass) {
        List<ConfigurableSetting> settings = new ArrayList<>();

        for (Field field : sectionClass.getDeclaredFields()) {
            // Must be public static
            if (!Modifier.isPublic(field.getModifiers()) || !Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            // Look for @ConfigValue annotation
            ConfigValueMetadata metadata = extractConfigValueMetadata(field);
            if (metadata != null) {
                ConfigurableSetting setting = createSetting(mod, sectionName, field, metadata);
                if (setting != null) {
                    settings.add(setting);
                }
            }
        }

        return settings;
    }

    /**
     * Extract metadata from @ConfigValue annotation
     */
    private static ConfigValueMetadata extractConfigValueMetadata(Field field) {
        for (Annotation annotation : field.getAnnotations()) {
            if (annotation.annotationType().getSimpleName().equals("ConfigValue")) {
                try {
                    Method defaultValueMethod = annotation.annotationType().getMethod("defaultValue");
                    Method descriptionMethod = annotation.annotationType().getMethod("description");
                    Method minMethod = annotation.annotationType().getMethod("min");
                    Method maxMethod = annotation.annotationType().getMethod("max");
                    Method runtimeMethod = annotation.annotationType().getMethod("runtime");

                    return new ConfigValueMetadata(
                        (String) defaultValueMethod.invoke(annotation),
                        (String) descriptionMethod.invoke(annotation),
                        (double) minMethod.invoke(annotation),
                        (double) maxMethod.invoke(annotation),
                        (boolean) runtimeMethod.invoke(annotation)
                    );
                } catch (Exception e) {
                    ModLogger.warn("Failed to extract @ConfigValue metadata from field %s: %s",
                                   field.getName(), e.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * Create a ConfigurableSetting from field and metadata
     */
    private static ConfigurableSetting createSetting(LoadedMod mod, String sectionName,
                                                     Field field, ConfigValueMetadata metadata) {
        SettingType type = determineSettingType(field);
        if (type == null) {
            ModLogger.warn("Unsupported field type for %s: %s", field.getName(), field.getType());
            return null;
        }

        return new ConfigurableSetting(
            mod,
            sectionName,
            field,
            metadata.description,
            type,
            metadata.min,
            metadata.max,
            metadata.defaultValue,
            metadata.runtime
        );
    }

    /**
     * Determine SettingType from field type
     */
    private static SettingType determineSettingType(Field field) {
        Class<?> type = field.getType();

        if (type == int.class || type == Integer.class) {
            return SettingType.INTEGER;
        } else if (type == long.class || type == Long.class) {
            return SettingType.LONG;
        } else if (type == float.class || type == Float.class) {
            return SettingType.FLOAT;
        } else if (type == double.class || type == Double.class) {
            // Treat double as float for UI purposes
            return SettingType.FLOAT;
        } else if (type == boolean.class || type == Boolean.class) {
            return SettingType.BOOLEAN;
        } else if (type == String.class) {
            return SettingType.STRING;
        }

        return null; // Unsupported type
    }

    /**
     * Helper class to hold @ConfigValue annotation metadata
     */
    private static class ConfigValueMetadata {
        final String defaultValue;
        final String description;
        final double min;
        final double max;
        final boolean runtime;

        ConfigValueMetadata(String defaultValue, String description, double min, double max, boolean runtime) {
            this.defaultValue = defaultValue;
            this.description = description;
            this.min = min;
            this.max = max;
            this.runtime = runtime;
        }
    }
}

