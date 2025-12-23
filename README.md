# Medieval Sim - Necesse Administrative Tools Mod

**Version 1.0.0** - Production-ready administrative tools, zone management, settlement protection, and Grand Exchange marketplace for Necesse servers.

## 🎯 Overview

Medieval Sim is a comprehensive server administration and economy mod for Necesse multiplayer servers. Featuring zone protection, settlement management, a full marketplace system, build tools, and command automation - Medieval Sim provides everything needed to create and manage thriving communities.

**Status:** ✅ Production-ready, stable, thread-safe, and fully documented.

## ✨ Features

### 💹 Grand Exchange Marketplace
- **Player-to-Player Trading**: Asynchronous marketplace for buying and selling items
- **Order Matching**: Automatic matching of buy orders with sell offers
- **Price Discovery**: Real-time market analytics with guide prices, VWAP, and 24h high/low
- **Collection Box**: Items and coins delivered to personal collection inventory
- **Order Book System**: Priority-based matching (best price first)
- **Rate Limiting**: Cooldown system prevents spam
- **Notifications**: Player notifications for filled orders and expired offers
- **Trade History**: Track all completed trades with timestamps
- **Market Analytics**: Performance metrics, coin velocity, trade volume tracking

### 🏰 Settlement Protection System
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

### ⚔️ Guild System (In Development)
- **Guild Creation**: Players can create public or private guilds with custom names
- **Hierarchical Ranks**: 4-tier rank system (Recruit → Member → Officer → Leader)
- **Permission System**: 20+ configurable permissions (building, storage, treasury, research, etc.)
- **Guild Artisan NPC**: Dedicated NPC for guild management, crest editing, and crafting
- **Guild Bank**: Shared storage with deposit/withdraw permissions per rank
- **Guild Research**: Research projects with resource contribution and unlock system
- **Custom Crests**: Visual crest designer with shapes, colors, emblems, and borders
- **Teleport Network**: Guild Teleport Banners for fast travel between guild locations
- **Wearable Items**: Guild Banners and Crests displaying guild crests
- **Guild Cauldron**: Guild-wide potion brewing (placeholder)
- **Audit Log**: Track member actions and rank changes
- **Treasury System**: Gold storage with tax rates and thresholds

### 🛠️ Core Architecture & Utilities

**New Utility Classes:**
- **TimeConstants** - Time conversion constants (eliminates magic numbers like 60000, 3600000)
- **BaseForm** - Abstract UI component base class with standard helpers
- **DiagnosticLogger** - Production diagnostic framework (method tracing, performance timing, resource tracking)
- **ErrorMessageBuilder** - Consistent user-facing error messages
- **ValidationUtil** - 15+ input validation methods for common patterns
- **Constants** - Centralized constants organized by nested classes (Zone, GrandExchange, BuildMode, etc.)

**Architecture Patterns:**
- **Facade Pattern**: `ProtectionFacade` - single entry point for all protection checks
- **Service Layer**: Grand Exchange services (OrderBook, MarketAnalytics, Notifications)
- **Thread Safety**: ConcurrentHashMap + synchronized methods for all shared state
- **Input Validation**: All packet handlers validate via ZoneAPI and ValidationUtil

## Recent Improvements

### December 2025 - Architectural Enhancements
**Thread Safety & Stability:**
- Fixed race condition in NotificationService (synchronized collection access)
- Fixed TOCTOU bugs in MarketAnalyticsService (double-check pattern)
- Added defensive null checks in zone deletion packets

**Security Hardening:**
- Input sanitization for zone dimensions (prevents integer overflow)
- Collection size limits (MAX_NOTIFICATIONS_PER_PLAYER = 100)
- Zone dimension validation (1-10,000 tiles)

**Performance Optimization:**
- Notification cleanup algorithm: O(n×m) → O(n)
- Empty collection removal prevents memory leaks
- Batched barrier placement for PvP zones

**Developer Experience:**
- DiagnosticLogger connected to ModConfig.Logging.verboseDebug
- ErrorMessageBuilder integrated in packet handlers
- Comprehensive JavaDoc with usage examples

### November 2025 - Refactoring (v2.0)

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