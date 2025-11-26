# Medieval Sim - Necesse Administrative Tools Mod

**Version 1.0.0** - Production-ready administrative tools, zone management, settlement protection, and command automation for Necesse servers.

## 🎯 Overview

Medieval Sim is a comprehensive server administration mod that provides powerful tools for managing Necesse multiplayer servers. From zone protection to settlement management, build tools to command automation, Medieval Sim gives server administrators everything they need to create and manage thriving communities.

## ✨ Features

### 🏰 Settlement Protection System (NEW in 1.0.0)
- **Settlement-Based Protection**: Protect entire settlements with granular permissions
- **Owner-Only Configuration**: Only settlement owners can configure protection settings
- **Team Permissions**: Allow owner's team to build and interact
- **Granular Controls**: 9 permission types (break, place, doors, containers, stations, signs, switches, furniture)
- **Visual Indicator**: Settlement Protection buff shows when inside protected settlements
- **Seamless Integration**: Protection button appears in settlement settings UI
- **Full Localization**: All UI text properly localized

### 🗺️ Zone Management
- **Protected Zones**: Create safe areas with configurable permissions
  - Owner assignment with online/offline player dropdown
  - Team-based permissions (owner's team can be granted access)
  - 9 granular interaction permissions (doors, chests, stations, signs, switches, furniture, break, place)
  - Visual boundaries and zone names
  - Full network synchronization
- **PvP Zones**: Designated combat areas with entry/exit dialogs
  - Configurable damage multipliers (0.05x to 10x)
  - Combat lock system (prevents instant escape)
  - DoT (Damage over Time) control
  - Spawn point management
  - PvP immunity on spawn (configurable duration)
  - Re-entry cooldown system
  - Barrier placement system (visual boundaries)
  - PvP damage reduction buff indicator
- **Zone Tools**: Create, expand, shrink, rename, delete zones
- **Visual Preview**: See zone boundaries during creation/editing
- **Zone List UI**: View and manage all zones with filtering

### 🎮 Command Center
- **Reflection-Based Command Registry**: Automatically discovers and wraps 86/96 Necesse commands (89.6% coverage)
- **Dynamic UI Generation**: Generates parameter input widgets automatically from command metadata
- **18 Widget Types**: Support for strings, integers, floats, booleans, enums, multi-choice, dropdown selections, and more
- **Command Categories**: Organized into Player Management, World Management, Server Management, and Debugging
- **Strategic Filtering**: Focuses on server administration commands, avoiding overlap with F10 debug menu
- **Command History**: Track recently used commands
- **Favorites System**: Save frequently used commands for quick access
- **World Click Integration**: Click-to-target for position-based commands

### 🏗️ Build Mode
- **Multi-Tile Placement**: Place large areas of tiles with a single click
- **10+ Geometric Shapes**: Rectangle, circle, line, cross, L-shape, T-shape, diamond, half-circle, triangle, and more
- **Hollow/Filled Options**: Toggle for all applicable shapes
- **Visual Preview**: See exactly what will be placed before confirming
- **Configurable Sizes**: Adjustable radius, length, and dimensions
- **Direction Control**: 4-way directional placement for asymmetric shapes
- **Spacing Control**: Adjustable spacing between placed tiles
- **Permission-Based**: Requires ADMIN permission level

### 🏘️ Settlement Spacing System
- **Configurable Minimum Tier**: Set minimum settlement tier for spacing (0-6)
- **Custom Padding**: Add extra region padding beyond minimum tier
- **Tier-Based Spacing**: Tier 0=40 tiles, Tier 1=56 tiles, Tier 2=72 tiles, etc.
- **Validation**: Prevents settlement placement if too close to existing settlements
- **Clear Feedback**: Error messages when placement fails
- **Backward Compatible**: Existing settlements unaffected

### 🏴 Plot Flag System
- **Purchasable Plots**: Admins can create plot flags with custom pricing
- **Coin-Based Economy**: Players purchase plots with in-game currency
- **Automatic Conversion**: Plot flags convert to settlement flags on purchase
- **Ownership Transfer**: Purchaser becomes settlement owner
- **Configurable Pricing**: Set coin cost per plot flag
- **Global Toggle**: Enable/disable plot flag system via mod settings
- **Settlement Spacing Integration**: Plot flags respect settlement spacing rules

### 🛠️ Universal Mod Settings
- **Annotation-Driven**: Use `@ConfigurableSetting` annotations for automatic settings registration
- **Enum Support**: Full dropdown support for enum types with formatted display names
- **Automatic UI Generation**: Settings UI automatically generated from annotated fields
- **Type Safety**: Strong typing with boolean, integer, float, string, and enum support
- **Section Organization**: Group related settings with `@ConfigSection`
- **Validation**: Min/max constraints with automatic validation
- **Persistence**: Settings save/load automatically

### 🎨 Admin Tools HUD
- **Central Hub**: Access all admin features from one interface
- **Permission-Based**: Only visible to ADMIN+ users
- **Tabbed Interface**: Organized tabs for Zones, Build Mode, Command Center, Settings
- **Responsive Design**: Adapts to screen size and content
- **Visual Feedback**: Clear indicators for active tools and modes
- **Quick Access**: Hotkey toggle for admin tools panel

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

### Logging / Production

The mod has a configurable `LOGGING.verboseDebug` flag in `ModConfig` that defaults to false.
- Set to true during local development to enable verbose placement, spacing, and debug logs.
- Leave as false in production to reduce noise and avoid performance overhead from excessive logging.

To enable verbose logs temporarily, set the flag using the `ModConfig.Logging.setVerboseDebug(true)` API at runtime, or update the saved config.

### Development Resources
See `NECESSE_MODDING_RESOURCES/` directory for API documentation and decompiled source code.