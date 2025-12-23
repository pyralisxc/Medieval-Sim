# ModConfig API - Universal In-Game Settings Editor

## Overview

Medieval Sim provides a **universal in-game mod settings editor** that allows players to adjust mod settings without leaving the game or editing config files manually.

**Any mod can use this system** by following the simple pattern described below. No dependencies required - just copy two annotation files and follow the convention!

---

## For Mod Authors: How to Make Your Mod Compatible

### Step 1: Copy the Annotation Files

Copy these two files into your mod's `config.annotations` package:

**`ConfigValue.java`:**
```java
package yourmod.config.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigValue {
    String defaultValue();
    String description() default "";
    double min() default Double.MIN_VALUE;
    double max() default Double.MAX_VALUE;
    boolean runtime() default false;
}
```

**`ConfigSection.java`:**
```java
package yourmod.config.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigSection {
    String value();
    String description() default "";
}
```

---

### Step 2: Create Your ModConfig Class

Create a class at `yourmod.config.ModConfig` (replace `yourmod` with your mod's package name).

**Example:**

```java
package yourmod.config;

import yourmod.config.annotations.ConfigSection;
import yourmod.config.annotations.ConfigValue;

public class ModConfig {
    
    @ConfigSection(value = "GAMEPLAY", description = "Gameplay settings")
    public static class Gameplay {
        
        @ConfigValue(
            defaultValue = "100",
            description = "Maximum stack size for items",
            min = 1,
            max = 999
        )
        public static int maxStackSize = 100;
        
        @ConfigValue(
            defaultValue = "1.5",
            description = "Damage multiplier for weapons",
            min = 0.1,
            max = 10.0
        )
        public static float damageMultiplier = 1.5f;
        
        @ConfigValue(
            defaultValue = "true",
            description = "Enable friendly fire"
        )
        public static boolean friendlyFire = true;
    }
    
    @ConfigSection(value = "WORLD_GEN", description = "World generation settings")
    public static class WorldGen {
        
        @ConfigValue(
            defaultValue = "2.0",
            description = "Ore spawn density multiplier",
            min = 0.1,
            max = 5.0
        )
        public static float oreDensity = 2.0f;
    }
}
```

---

### Step 3: Integrate with Necesse's Settings System

Create a settings class that extends `ModSettings`:

```java
package yourmod.config;

import necesse.engine.modLoader.ModSettings;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

public class YourModSettings extends ModSettings {
    
    @Override
    public void addSaveData(SaveData save) {
        // Save all config values
        save.addInt("maxStackSize", ModConfig.Gameplay.maxStackSize);
        save.addFloat("damageMultiplier", ModConfig.Gameplay.damageMultiplier);
        save.addBoolean("friendlyFire", ModConfig.Gameplay.friendlyFire);
        save.addFloat("oreDensity", ModConfig.WorldGen.oreDensity);
    }
    
    @Override
    public void applyLoadData(LoadData load) {
        // Load all config values
        ModConfig.Gameplay.maxStackSize = load.getInt("maxStackSize", 100);
        ModConfig.Gameplay.damageMultiplier = load.getFloat("damageMultiplier", 1.5f);
        ModConfig.Gameplay.friendlyFire = load.getBoolean("friendlyFire", true);
        ModConfig.WorldGen.oreDensity = load.getFloat("oreDensity", 2.0f);
    }
}
```

Register your settings in your mod's `@ModEntry` class:

```java
@ModEntry
public class YourMod {
    
    public void init() {
        Settings.registerModSettings(new YourModSettings());
    }
}
```

---

## How It Works

1. **Medieval Sim scans all loaded mods** for a class at `<package>.config.ModConfig`
2. **Discovers sections** using `@ConfigSection` annotations on nested static classes
3. **Discovers settings** using `@ConfigValue` annotations on public static fields
4. **Generates UI automatically** with appropriate input widgets (text boxes, checkboxes, etc.)
5. **Saves changes** via `Settings.saveClientSettings()` when values are modified

---

## Supported Field Types

| Java Type | UI Widget | Notes |
|-----------|-----------|-------|
| `int` / `Integer` | Text input | Supports min/max validation |
| `long` / `Long` | Text input | Supports min/max validation |
| `float` / `Float` | Text input | Supports min/max validation |
| `double` / `Double` | Text input | Treated as float for UI |
| `boolean` / `Boolean` | Checkbox | Simple on/off toggle |
| `String` | Text input | Max 100 characters |
| `enum` | Dropdown | Formatted display names (e.g., WORLD_BOSS → "World Boss") |

---

## Annotation Reference

### `@ConfigValue`

Marks a field as a configurable setting.

**Parameters:**
- `defaultValue` (required): String representation of the default value
- `description` (optional): Human-readable description shown in UI
- `min` (optional): Minimum value for numeric types (default: `Double.MIN_VALUE`)
- `max` (optional): Maximum value for numeric types (default: `Double.MAX_VALUE`)
- `runtime` (optional): If true, this is a runtime-only value (not shown in UI)

**Example:**
```java
@ConfigValue(
    defaultValue = "50",
    description = "Maximum number of players",
    min = 1,
    max = 100
)
public static int maxPlayers = 50;
```

---

### `@ConfigSection`

Marks a nested static class as a configuration section.

**Parameters:**
- `value` (required): Section name (e.g., "GAMEPLAY", "WORLD_GEN")
- `description` (optional): Human-readable description of the section

**Example:**
```java
@ConfigSection(value = "GAMEPLAY", description = "Core gameplay settings")
public static class Gameplay {
    // ... settings here
}
```

---

## Best Practices

1. **Use descriptive names**: Field names are converted to "Title Case" in the UI (e.g., `maxStackSize` → "Max Stack Size")
2. **Add descriptions**: Help players understand what each setting does
3. **Set reasonable min/max**: Prevent players from breaking the game with extreme values
4. **Group related settings**: Use sections to organize settings logically
5. **Test your defaults**: Make sure default values work well out-of-the-box

---

## FAQ

**Q: Do I need to add Medieval Sim as a dependency?**  
A: No! The system works via reflection and naming conventions. Just copy the annotation files.

**Q: What if I don't have Medieval Sim installed?**  
A: Your mod will work normally. The in-game editor is just a convenience feature.

**Q: Can I use this for server-side settings?**  
A: Currently, this is designed for client-side settings. Server settings should use Necesse's built-in server config system.

**Q: What if I want to add custom validation?**  
A: You can add validation logic in your `applyLoadData()` method before assigning values to fields.

---

## Example: Complete Mod

See `medievalsim.config.ModConfig` in the Medieval Sim source code for a complete, production-ready example with:
- Multiple sections (Build Mode, Zones, Command Center)
- Various field types (int, long, float, boolean)
- Min/max validation
- Runtime-only fields

---

## Support

If you have questions or need help implementing this in your mod:
- Check the Medieval Sim source code for examples
- Open an issue on the Medieval Sim GitHub repository
- Join the Necesse modding Discord

---

**Happy modding!** 🎮

