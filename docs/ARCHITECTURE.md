# Medieval Sim Mod - Architecture Documentation

**Last Updated:** December 16, 2025  
**Version:** Post-Refactoring v1.0

## Table of Contents
1. [Overview](#overview)
2. [Protection System Architecture](#protection-system-architecture)
3. [GameObject Classification System](#gameobject-classification-system)
4. [Helper Organization](#helper-organization)
5. [Packet System](#packet-system)
6. [Known Architectural Decisions](#known-architectural-decisions)

---

## Overview

The Medieval Sim mod is an experimental mod built primarily by AI agents, adding complex gameplay systems to Necesse including:
- **Admin Zones**: Protected zones and PvP zones with granular permissions
- **Settlement Protection**: Necesse settlement integration with build/break/interact controls
- **Build Mode**: Advanced placement tools with shapes and patterns
- **Grand Exchange**: Player-to-player marketplace with order matching and analytics
- **Banking**: Personal storage with PIN protection
- **Command Center**: In-game command execution UI

### Package Organization

```
medievalsim/
├── admintools/        # Admin tools integration (zone tools, build tools)
├── banking/           # Banking system
├── buildmode/         # Build mode service and utilities
├── buffs/             # Custom buffs (PvP zone buffs, etc.)
├── commandcenter/     # Command center UI and execution
├── config/            # Mod configuration system
├── grandexchange/     # Grand Exchange trading system
│   ├── domain/        # Core entities (GEOffer, BuyOrder, OrderBook)
│   ├── services/      # Business logic (NotificationService, MarketAnalytics)
│   ├── ui/            # Forms, handlers, layout
│   └── commands/      # Admin debug commands
├── inventory/         # Custom inventory extensions
├── objects/           # Custom game objects
├── packets/           # Network packet system
│   ├── core/          # Base packet classes and registrar
│   └── registry/      # Feature-based packet registrars
├── patches/           # ByteBuddy runtime patches
├── registries/        # Mod registration (buffs, items, packets, etc.)
├── ui/                # Custom UI forms and dialogs
│   ├── components/    # Reusable UI components (BaseForm)
│   └── tabs/          # Admin HUD tabs
├── util/              # Shared utilities
│   ├── Constants      # Centralized constants (nested classes)
│   ├── TimeConstants  # Time conversion constants
│   ├── ValidationUtil # Input validation helpers
│   ├── DiagnosticLogger # Production diagnostics
│   ├── ErrorMessageBuilder # Consistent error messages
│   └── [other utilities]
└── zones/             # Zone management and protection
    ├── domain/        # Zone data structures
    ├── protection/    # Protection facade
    ├── service/       # Zone helpers
    └── settlement/    # Settlement protection integration
```

---

## Core Utilities

### Recent Utility Classes (December 2025)

**TimeConstants** (`util/TimeConstants.java`)
- Time conversion constants to eliminate magic numbers
- `MILLIS_PER_SECOND`, `MILLIS_PER_MINUTE`, `MILLIS_PER_HOUR`, `MILLIS_PER_DAY`
- Common time periods: `TEN_MINUTES_MS`, `THIRTY_SECONDS_MS`
- Used throughout Grand Exchange services and notification systems

**BaseForm** (`ui/components/BaseForm.java`)
- Abstract base class for UI forms with standard helpers
- Standard fonts: `HEADER_FONT`, `BODY_FONT`, `SMALL_FONT`
- Component creation: `createSectionHeader()`, `createButton()`, `createNumberInput()`
- Layout helpers: `nextRow()`, `nextSection()`, `centerX()`
- Reduces UI code duplication by ~200 lines per form

**DiagnosticLogger** (`util/DiagnosticLogger.java`)
- Production diagnostic framework for troubleshooting
- Method tracing, performance timing, resource tracking, state transitions
- Hooks into `ModConfig.Logging.verboseDebug` configuration
- Thread-local storage for nested method tracking

**ErrorMessageBuilder** (`util/ErrorMessageBuilder.java`)
- Consistent user-facing error message formatting
- Categories: permission errors, validation errors, cooldown errors, resource errors
- Used in packet handlers for user-friendly error feedback

**ValidationUtil Enhancements** (`util/ValidationUtil.java`)
- Expanded from 96 to 256 lines with 8 new validation methods
- `validatePositiveInteger()`, `validateRange()`, `validateNonEmptyString()`
- `validatePriceInput()`, `validateQuantityInput()` for Grand Exchange
- Safe parsing: `parseIntOrDefault()`, `parseFloatOrDefault()`

**Constants Organization** (`util/Constants.java`)
- Centralized constants using nested classes
- `Constants.Zone`: Zone dimension limits (MAX_ZONE_WIDTH, MAX_ZONE_HEIGHT)
- `Constants.ZoneVisualization`: Zone overlay rendering constants
- `Constants.GrandExchange`: UI dimensions and default values
- `Constants.BuildMode`: Build mode configuration constants

### Thread-Safety Patterns

**Concurrent Collections**
- Grand Exchange services use `ConcurrentHashMap` for shared state
- OrderBook synchronizes all public methods
- NotificationService synchronizes list access within ConcurrentHashMap entries

**Double-Check Pattern**
- MarketAnalyticsService uses atomic get-and-lock with re-validation
- Prevents TOCTOU (Time-Of-Check-Time-Of-Use) bugs
- Example: Check null → acquire lock → re-check empty before processing

**Collection Size Limits**
- `MAX_NOTIFICATIONS_PER_PLAYER = 100` prevents unbounded growth
- Oldest-first removal when limits reached
- Iterator pattern for efficient cleanup without excessive lock contention

---

## Protection System Architecture

### Overview

The protection system enforces **settlement precedence over zone protection**, ensuring consistent permission checks across all operations (place, break, interact).

### Architecture Diagram

```
┌─────────────────────────────────────────────────┐
│           ProtectionFacade                       │
│  (Single Source of Truth for Protection)        │
│                                                  │
│  ┌──────────────────────────────────────────┐  │
│  │  canPlace(client, level, x, y, isObject) │  │
│  │  canBreak(client, level, x, y, isObject) │  │
│  │  canInteract(client, level, x, y, obj)   │  │
│  └──────────────────────────────────────────┘  │
│                                                  │
│  Precedence: #1 Settlement → #2 Zone            │
└─────────────────┬───────────────────────────────┘
                  │
         ┌────────┴────────┐
         │                 │
         ▼                 ▼
┌────────────────┐  ┌──────────────────┐
│ Settlement     │  │ Zone Protection  │
│ Protection     │  │                  │
│ Helper         │  │ ProtectedZone    │
│                │  │ .canClientPlace  │
│ .canClientPlace│  │ .canClientBreak  │
│ .canClientBreak│  │ .canClientInteract│
└────────────────┘  └──────────────────┘
```

### Protection Precedence Rules

**PRECEDENCE #1: Settlement Protection** (checked first)
- If tile is in a protected settlement, settlement rules apply
- Owner, team members, and world owner have elevated access
- Configurable per-settlement: `canPlace`, `canBreak`, `allowOwnerTeam`
- Settlement protection **takes precedence** over zone protection

**PRECEDENCE #2: Zone Protection** (checked if settlement allows)
- Only checked if NOT in a protected settlement, OR settlement allows the action
- Protected zones have granular permissions: doors, crafting stations, containers, signs, switches, furniture
- PvP zones apply buffs but don't restrict building

### ProtectionFacade API

**Location:** `medievalsim.zones.protection.ProtectionFacade`

#### Methods

```java
public static ProtectionResult canPlace(
    ServerClient client, Level level, int tileX, int tileY, boolean isObject
)
```
Check if client can place tile/object. Returns `ProtectionResult` with allow/deny + localized message.

```java
public static ProtectionResult canBreak(
    ServerClient client, Level level, int tileX, int tileY, boolean isObject
)
```
Check if client can break tile/object. Checks settlement first, then zone.

```java
public static ProtectionResult canInteract(
    ServerClient client, Level level, int tileX, int tileY, GameObject gameObject
)
```
Check if client can interact with GameObject. Uses GameObjectClassifier for consistent type detection.

#### ProtectionResult

```java
public static class ProtectionResult {
    public boolean isAllowed()          // true if action permitted
    public boolean isDenied()           // true if action blocked
    public String getMessage()          // Localized error message
    public String getZoneName()         // Zone/settlement name that blocked
}
```

### Usage in Patches

**Placement Protection:**
- `TileItemCanPlacePatch`: Tile placement validation
- `ObjectItemCanPlacePatch`: Object placement validation

**Breaking Protection:**
- `ToolDamageItemCanDamageTilePatch`: Breaking validation (layerID: -1 for tiles, 0+ for objects)

**Interaction Protection:**
- `GameObjectCanInteractPatch`: Interaction validation using GameObjectClassifier

---

## GameObject Classification System

### Overview

GameObjectClassifier provides **centralized GameObject type detection** with consistent behavior across all patches and protection checks.

### InteractionType Enum

**Location:** `medievalsim.util.GameObjectClassifier`

```java
public enum InteractionType {
    DOOR("doors", "door", "nopermissiondoor"),
    CRAFTING_STATION("stations", "craftingstation", "nopermissionstation"),
    CONTAINER("containers", "container", "nopermissioncontainer"),
    SIGN("signs", "sign", "nopermissionsign"),
    SWITCH("switches", "switch", "nopermissionswitch"),
    FURNITURE("furniture", "furniture", "nopermissionfurniture"),
    UNKNOWN("unknown", "object", "nopermissioninteract");
    
    final String permissionKey;    // Permission field name in ProtectedZone
    final String displayNameKey;   // UI display name localization key
    final String errorMessageKey;  // Error message localization key
}
```

### Classification Logic

**Necesse Type Hierarchy:**
```
GameObject
├── DoorObject          (InteractionType.DOOR)
├── CraftingStationObject
│   └── InventoryObject (InteractionType.CRAFTING_STATION if extends CraftingStation)
│       └── ...         (InteractionType.CONTAINER otherwise)
├── SignObject          (InteractionType.SIGN)
├── SwitchObject        (InteractionType.SWITCH)
├── ChairObject
│   └── ...             (InteractionType.FURNITURE)
└── TableObject
    └── ...             (InteractionType.FURNITURE)
```

**Important:** CraftingStation must be checked BEFORE InventoryObject due to inheritance (`CraftingStationObject extends InventoryObject`).

### API Methods

```java
public static InteractionType classify(GameObject gameObject)
```
Returns the InteractionType for a GameObject.

```java
public static String getBlockedMessage(GameObject gameObject)
```
Returns localized error message (e.g., "You don't have permission to open containers").

```java
public static String getDisplayName(GameObject gameObject)
```
Returns localized display name (e.g., "Door", "Crafting Station").

### Usage

```java
// In protection checks
InteractionType type = GameObjectClassifier.classify(gameObject);
if (!zone.canInteract(type)) {
    client.sendChatMessage(GameObjectClassifier.getBlockedMessage(gameObject));
    return;
}

// In ProtectedZone.canClientInteract
public boolean canClientInteract(ServerClient client, Level level, GameObject gameObject) {
    InteractionType type = GameObjectClassifier.classify(gameObject);
    switch (type) {
        case DOOR: return canInteractDoors;
        case CRAFTING_STATION: return canInteractStations;
        // ... etc
    }
}
```

---

## Helper Organization

### Naming Conventions

The mod follows strict naming conventions for utility classes:

- **Helper**: Static utility methods, no state (e.g., `ZoneHelper`, `AdminToolsHelper`)
- **Manager**: Lifecycle-managed services with state (e.g., `BuildModeManager`)
- **Service**: Business logic services (future pattern)

### Key Helpers

#### ZoneHelper
**Location:** `medievalsim.zones.service.ZoneHelper`  
**Pattern:** Static utility  
**Purpose:** Zone creation, deletion, queries

```java
public static ProtectedZone createProtectedZone(Level level, String name, ...)
public static PvPZone createPvPZone(Level level, String name, ...)
public static void deleteProtectedZone(Level level, long zoneID)
public static PvPZone getPvPZoneAt(Level level, int tileX, int tileY)
```

#### AdminToolsHelper
**Location:** `medievalsim.admintools.AdminToolsHelper`  
**Pattern:** Static utility  
**Purpose:** Admin UI integration, permission checks

```java
public static void setupAdminButton(Form form, Client client)
public static boolean hasAdminPermission(Client client)
```

#### SettlementProtectionHelper
**Location:** `medievalsim.zones.settlement.SettlementProtectionHelper`  
**Pattern:** Static utility  
**Purpose:** Settlement protection logic

```java
public static ServerSettlementData getProtectedSettlementAt(Level level, int x, int y)
public static boolean canClientPlace(ServerClient client, Level level, int x, int y)
public static boolean canClientBreak(ServerClient client, Level level, int x, int y)
public static boolean hasElevatedAccess(ServerClient client, Level level, ServerSettlementData settlement)
```

#### SettlementProtectionButtonHelper
**Location:** `medievalsim.zones.settlement.SettlementProtectionButtonHelper`  
**Pattern:** Static utility  
**Purpose:** UI integration for settlement protection button

```java
public static void addProtectionButton(Form settings, Client client, SettlementContainer container)
```

**Note:** Helpers are well-separated by concern - no consolidation needed.

---

## Packet System

### Overview

The packet system uses a **feature-based registrar pattern** with validation base classes.

### Architecture

```
packets/
├── core/
│   ├── PacketRegistrar.java          # Interface: () -> List<PacketSpec>
│   ├── PacketSpec.java                # (Class, category, description)
│   ├── AbstractMedievalPacket.java    # Base with packetName() helper
│   └── AbstractPayloadPacket.java     # Validation helpers (validateString, etc.)
├── registry/
│   ├── ZonePacketRegistrar.java       # 18 zone packets
│   ├── BankingPacketRegistrar.java    # Banking packets
│   ├── GrandExchangePacketRegistrar.java  # GE packets
│   ├── CommandPacketRegistrar.java    # Command system packets
│   └── AdminPacketRegistrar.java      # Admin tool packets
└── Packet*.java                       # 44 packet implementations
```

### Base Classes

**AbstractMedievalPacket**
- Provides `packetName()` helper for logging
- Base for all mod packets

**AbstractPayloadPacket extends AbstractMedievalPacket**
- Validation helpers: `validateString(value, min, max, fieldName)`
- Consistent null checks and length validation
- Used for data-heavy packets (GE, banking)

### Registration Pattern

```java
public final class ZonePacketRegistrar {
    private static final PacketRegistrar REGISTRAR = () -> List.of(
        new PacketSpec(PacketCreateZone.class, "zones", "Create new admin zone"),
        new PacketSpec(PacketExpandZone.class, "zones", "Expand zone"),
        // ... more packets
    );
    
    public static List<PacketSpec> getSpecs() {
        return REGISTRAR.getSpecs();
    }
}
```

### Benefits

- **Feature isolation**: Each registrar owns related packets
- **Discoverability**: Clear categorization (zones, banking, etc.)
- **Consistency**: Shared validation through base classes
- **Maintainability**: Easy to add new packets to appropriate registrar

**Status:** No refactoring needed - already follows best practices.

---

## Known Architectural Decisions

### 1. BuildModeManager Singleton (DEFERRED)

**Current State:**
- Singleton pattern with client reference
- 310 lines with preview elements, settings persistence
- getInstance() and getInstance(Client) dual API

**Decision:** Defer refactoring to Client-owned lifecycle
**Reason:** Complex with high bug risk. Requires:
- Client attachment system integration
- Preview element lifecycle management
- Settings persistence redesign
- Extensive testing

**Future Work:** Consider refactoring when can dedicate focused testing time.

### 2. Settlement Precedence

**Decision:** Settlement protection takes precedence over zone protection
**Rationale:** User requirement - settlements are player-owned, should have higher authority than admin zones
**Implementation:** ProtectionFacade checks settlements FIRST, zones SECOND

### 3. Package-by-Feature Structure

**Decision:** Organize by feature (zones/, banking/, grandexchange/) not by layer (services/, repositories/)
**Rationale:** AI-generated mod, emergent architecture based on feature additions
**Status:** Working well, maintain this structure

### 4. ByteBuddy Patches

**Pattern:** Prefix patches with target class name
- `GameObjectCanInteractPatch` patches `LevelObject.interact`
- `TileItemCanPlacePatch` patches `TileItem.canPlace`
- `ToolDamageItemCanDamageTilePatch` patches `ToolDamageItem.canDamageTile`

**Rationale:** Clear patch target identification, easier debugging

---

## Refactoring History

**December 16, 2025 - Architecture Consolidation**

1. **GameObjectClassifier Created**
   - Eliminated 100+ lines of duplication across 4 files
   - Centralized GameObject type detection
   - InteractionType enum with 7 types + UNKNOWN

2. **ProtectionFacade Created**
   - Single source of truth for all protection checks
   - Deleted ZoneProtectionValidator (~170 lines)
   - Added settlement protection to interaction checks (was zone-only)
   - Consistent settlement > zone precedence

3. **Naming Cleanup**
   - ZoneManager → ZoneHelper (static utility)
   - AdminToolsManager → AdminToolsHelper (static utility)
   - 9 files updated

4. **Patch Migrations**
   - TileItemCanPlacePatch → ProtectionFacade
   - ObjectItemCanPlacePatch → ProtectionFacade
   - GameObjectCanInteractPatch → ProtectionFacade (now includes settlement checks)
   - ToolDamageItemCanDamageTilePatch → ProtectionFacade

**Benefits:**
- ~270 lines of duplication eliminated
- Single protection API with consistent behavior
- Settlement protection now comprehensive (place + break + interact)
- Clear helper naming conventions

---

## Contributing

When extending the mod:

1. **Protection Checks:** Always use ProtectionFacade, never bypass to helpers
2. **GameObject Types:** Use GameObjectClassifier.classify() for type detection
3. **New Packets:** Add to appropriate registrar in `packets/registry/`
4. **Helpers:** Follow naming convention (Helper = static, Manager = stateful)
5. **Settlement Precedence:** Always check settlements before zones
6. **Testing:** Test protection with settlement owner, team member, and outsider roles

---

## Known Issues & Future Work

### High Priority
- **BuildMode Lifecycle:** Refactor singleton to Client-owned when time permits
- **Protection Testing:** Comprehensive test suite for precedence rules

### Medium Priority
- **Packet System:** Consider adding packet versioning for backward compatibility
- **Error Handling:** Standardize error responses across packets

### Low Priority
- **Documentation:** Add JavaDoc to all public APIs
- **Performance:** Profile protection checks at scale (1000+ zones)

---

**End of Architecture Documentation**
