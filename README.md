# Medieval Sim - Necesse Administrative Tools Mod

An experimental Necesse mod providing advanced administrative tools, zone management, and command automation.

## Features

### 🎮 Command Center
- **Reflection-Based Command Registry**: Automatically discovers and wraps 86/96 Necesse commands (89.6% coverage)
- **Dynamic UI Generation**: Generates parameter input widgets automatically from command metadata
- **18 Widget Types**: Support for strings, integers, floats, booleans, enums, multi-choice, dropdown selections, and more
- **Command Categories**: Organized into Player Management, World Management, Server Management, and Debugging

### 🛠️ Universal Mod Settings
- **Annotation-Driven**: Use `@ConfigurableSetting` annotations for automatic settings registration
- **Enum Support**: Full dropdown support for enum types with formatted display names
- **Automatic UI Generation**: Settings UI automatically generated from annotated fields
- **Type Safety**: Strong typing with boolean, integer, float, string, and enum support

### 🗺️ Zone Management
- **Protected Zones**: Create and manage safe areas with configurable permissions
- **PvP Zones**: Designated combat areas with entry/exit dialogs and spawn points
- **Zone Tools**: Interactive create, expand, move, and delete tools with visual feedback
- **Permission System**: Fine-grained control over building, chest access, and other interactions

### 🏗️ Build Mode
- **Multi-Tile Placement**: Place large areas of tiles with single click
- **Geometric Shapes**: Support for rectangles, circles, and custom shapes
- **Visual Preview**: See placement area before confirming
- **Configurable Sizes**: Adjustable placement radius and dimensions

## Recent Refactoring (v2.0)

**Code Reduction**: Eliminated 1,204+ lines (~6.7% of codebase)
- Removed widget strategy pattern overhead (~500 lines)
- Deleted unused settings scanner system (~614 lines)
- Cleaned legacy zone methods and reserved fields (~180 lines)

**Performance Improvements**:
- Cached reflection results in `ParameterMetadata` (eliminated 45 calls per UI open)
- Simplified cache validation logic (removed mod hash calculations)
- Optimized UI initialization (40-90ms faster)

**New Features**:
- Enum dropdown support in settings system
- `ReflectionUtils` utility class for consistent reflection patterns
- Formatted enum display names (e.g., "WORLD_BOSS" → "World Boss")

**Architecture Improvements**:
- Command Center reflection now initialization-time only (not runtime)
- Switch-based widget factory (simpler than strategy pattern for fixed types)
- Consolidated reflection utilities reduce duplication

## Documentation

- **[Codebase Architecture](docs/medieval-sim-codebase.instructions.md)**: Package structure and development patterns
- **[Reflection Architecture](docs/REFLECTION_ARCHITECTURE.md)**: Command Center design and reflection usage
- **[Mod Config API](docs/MOD_CONFIG_API.md)**: Universal settings system documentation
- **[Command Center vs Debug Menu](docs/COMMAND_CENTER_VS_DEBUG_MENU_ANALYSIS.md)**: Architectural comparison

## Development

Built for Necesse using Gradle. Check out the [modding wiki page](https://necessewiki.com/Modding) for more.

### Building
```bash
./gradlew buildModJar
```

### Development Resources
See `NECESSE_MODDING_RESOURCES/` directory for API documentation and decompiled source code.