# Necesse Guilds & Federations - Development Worksheet

## Project Overview
**Concept**: A comprehensive expansion to Necesse's settlement and multiplayer systems that introduces persistent player organizations, shared land ownership, specialized research, and a global economy.

**Status**: 🚧 **Active Implementation** — Core systems complete, networking layer functional

---

## 📋 Latest Updates (2025-12-21)

### Guild Objects Added
- **Guild Teleport Stand** - Blue-glowing interactable for guild-to-guild teleportation
- **Guild Cauldron** - Brewing station with bubbling particles for guild buffs
- Both objects: permission-gated, registered, localized

### Research System Fixed
- Container now passes initial state in opening packet
- Added bidirectional refresh (client request → server response)
- Form reads from container state, not local variables
- Full data flow: `PacketOpenGuildResearch` → `GuildResearchContainer.openResearchUI()` → Form displays live data

---

## � Design Change Log (2025-12-21)

### Research System - Mage Integration (was: Scientist NPC)

**Change**: Instead of creating a new "Guild Scientist" NPC, guild research is now accessed through the **vanilla Mage NPC**.

**Rationale**:
- Necesse doesn't have an existing Scientist NPC to hook into
- The Mage is thematically appropriate (magic, research, knowledge)
- Reduces mod complexity by reusing existing game infrastructure
- Players already know where to find Mages in settlements

**Implementation**:
- Added "Guild Research" dialogue option to Mage when player is in a guild
- Created `GuildResearchContainer` and `GuildResearchContainerForm` 
- Research UI accessible via Guild Info Panel → "Research" button
- Automated research pulls still respect treasury minimum threshold

**Files Created/Modified**:
- `research/GuildResearchContainer.java` - Server-side container
- `research/GuildResearchContainerForm.java` - Research UI form
- `research/MageGuildResearchForm.java` - Extended Mage form (for future container override)
- `packets/PacketOpenGuildResearch.java` - Opens research UI
- `GuildData.java` - Added `getResearchManager()` method
- `GuildInfoPanelForm.java` - Added Research and Bank buttons

**Note**: References to "Scientist" in this document should be read as "Mage (for research)" or "automated research system".

---

## 📊 Implementation Progress (Updated 2025-12-21)

### ✅ Completed Systems

| Component | Status | Files |
|-----------|--------|-------|
| **Core Data Models** | ✅ Complete | `GuildData.java`, `GuildRank.java`, `GuildCrestDesign.java`, `PermissionType.java`, `PlayerGuildData.java`, `GuildAuditEntry.java` |
| **World Data Manager** | ✅ Complete | `GuildManager.java` - guild lifecycle, membership, persistence |
| **Permission System** | ✅ Complete | Rank-based permissions with customizable thresholds |
| **Guild Bank** | ✅ Complete | `bank/GuildBank.java` - treasury and shared storage |
| **Networking Layer** | ✅ Complete | 5 packets registered and tested |
| **Test Suite** | ✅ Passing | 29 tests (packets + flow tests) |

### 🔵 Guild Packets — COMPLETE

| Packet | Direction | Purpose | Status |
|--------|-----------|---------|--------|
| `PacketCreateGuild` | Client→Server | Create new guild | ✅ Tested |
| `PacketGuildCreated` | Server→Client | Announce guild creation | ✅ Tested |
| `PacketInviteMember` | Client→Server | Send invitation | ✅ Tested |
| `PacketGuildInvited` | Server→Client | Notify invited player | ✅ Tested |
| `PacketRespondInvite` | Client→Server | Accept/decline invite | ✅ Tested |

### 🟡 In Progress / Next Up

| Component | Status | Notes |
|-----------|--------|-------|
| Guild Artisan NPC | ✅ Complete | Mob, container, form all functional |
| Create Guild UI Form | ✅ Complete | Dialog in GuildArtisanContainerForm |
| Invite Dialog UI | ✅ Complete | `InviteDialog.java` - accept/decline buttons |
| Guild Management Packets | ✅ Complete | Promote/demote/kick/leave/disband/transfer |
| Crest Designer UI | 📋 Designed | Not yet coded |
| Guild Info Panel | 📋 Planned | View members, treasury, etc. |
| Guild Browser | 📋 Planned | Browse guilds to join |

### ⚪ Not Yet Started

- Route A: Guild Flag Object (settlement conversion)
- Route B: Admin Plot System (~~GuildZone integration~~ **GuildZone UI Complete - see below**)
- ~~Research System (nodes, contributions, scientist)~~ **Research UI via Mage - In Progress**
- Guild Cauldron & Pneumatic Chest objects
- Federation Layer (guild alliances)

### ✅ Research System (NEW - 2025-12-21)

| Component | Status | Notes |
|-----------|--------|-------|
| GuildResearchManager | ✅ Complete | Research state, progress, bonuses |
| ResearchProject | ✅ Complete | Project definitions with costs/effects |
| ResearchRegistry | ✅ Complete | All default projects registered |
| ResearchEffect | ✅ Complete | Effect types and display strings |
| GuildResearchContainer | ✅ Complete | Server-side container |
| GuildResearchContainerForm | ✅ Complete | Research UI form |
| PacketOpenGuildResearch | ✅ Complete | Opens research UI |
| Guild Info Panel Integration | ✅ Complete | Research button in overview tab |

### ✅ Guild Zone Admin UI (NEW - 2025-01-XX)

| Component | Status | Notes |
|-----------|--------|-------|
| GuildZone domain class | ✅ Complete | `zones/domain/GuildZone.java` - extends AdminZone |
| ZoneType enum | ✅ Complete | PROTECTED, PVP, GUILD types |
| Admin HUD Guild Zones Tab | ✅ Complete | In AdminToolsHudForm.java |
| Guild Zone Visualization | ✅ Complete | ZoneVisualizationHud with guild supplier |
| CreateOrExpandZoneTool | ✅ Complete | ZoneType support for GUILD |
| PacketDeleteZone | ✅ Complete | ZoneType enum support |
| PacketRenameZone | ✅ Complete | ZoneType enum support |
| Localization | ✅ Complete | "guildzones", "createguildzone" keys |

---

## Document Purpose
**Authoritative usage:** This project uses **two canonical files** for all guild work:
- **`docs/guilds_worksheet.md`** (this file) — Plain-English decisions, questions, and acceptance criteria. Use this file to record and resolve outstanding choices.
- **`docs/guilds_implementation_plan.md`** — Technical spec, APIs, code sketches, and implementation details. Use this file when coding.

---

## Decision Checklist — ✅ RESOLVED

**Notifications & Invites (Artisan)**:
- Add a **Notifications** panel to the Guild Artisan UI showing unread invites, guild notices, and admin messages. The Artisan should display a badge with the unread count in the Artisan menu. Notifications are expandable, individually clearable, and support a `Clear All` action. Packets: `PacketOpenNotifications`, `PacketClearNotification`, `PacketClearAllGuildNotifications`.

**Settlement Settings / Guild Flag (integration note)**:
- Do **not** create a separate Guild Flag top-level menu. Add Guild-specific tabs and buttons to the existing **Settlement Settings** UI (accessed with the C quick key), including `Guild Bank` tab and `Guild Settings` button (leader-only). Teleport Banners are managed under `GuildInfoPanel` and `ManageGuildsForm` (not under Settlement Settings).
- **Guild Flags may only be destroyed by a world admin or via the `Disband Guild` action in `GuildInfoPanel` (leader-only).**

**Banner placement limit (ModConfig)**:
- Add `ModConfig.Guilds.maxBannersPerSettlementPerGuild` (default **1**) to enforce **one** banner per settlement per guild per player. Placement and buy flows must enforce this server-side.

**Leadership semantics**:
- For guild permission and settlement ownership purposes, **the Guild Leader is considered the owner** (for UI visibility and owner-level settlement actions).

**Quest Unlock Flow (UX decision)**:
- When a player interacts with a Guild Artisan but has not yet met the requirements to create/join, the Artisan opens a *Quest Offer* screen that explains the unlock requirements and offers the `Prove Your Worth` quest with an **Accept** button. Upon quest completion the regular Create/Join options become available to that player.

**Guild Artisan UI decision**:
- The Guild Artisan will **not** provide direct access to the Guild Bank or Leader Guild Settings. Instead, the Artisan exposes **Manage Guilds**, **Create Guild**, and **Join Guild** flows. Per-guild leave and purchase banner actions are available inside `ManageGuildsForm`. (This centralizes settlement-level authority in the Settlement Settings.)
- DEV TASK: Remove any existing `Open Bank` / `Guild Settings` UI actions and packet calls from `GuildArtisanContainerForm` and related client handlers; bank/settings must open only from Settlement Settings (C key) or `GuildInfoPanel`.

All decisions have been finalized. Values are now authoritative for implementation.

### 1) Plot Pricing Tunables (Leader-Only Adjustable)
| Setting | Default Value | Status |
|---------|---------------|--------|
| `basePlotPrice` | **100,000** | ✅ Confirmed |
| `perSettlerWeight` | **1,000** | ✅ Confirmed |
| `perQualityRoomWeight` | **5,000** | ✅ Confirmed |
| `workstationTierMultipliers` | **[1.0, 1.5, 2.5]** | ✅ Confirmed |
| Work Zone Bonuses | Husbandry=50k, Forestry=30k, Farming=40k | ✅ Confirmed |

### 2) Guild Charter Behavior
✅ **REMOVED** — Guild Charter item is **not needed**. Players create guilds directly through the Guild Artisan without any charter item requirement.
- Remove `guildcharter.png` asset
- Remove `GuildCharter.java` item class from implementation plan
- Remove `requireGuildCharter` config option

### 3) Crest Rendering & Sizes
| Setting | Value | Status |
|---------|-------|--------|
| Crest sizes | **64 / 32 / 16 px** | ✅ Confirmed |
| Generation strategy | **Lazy + Cache** (Necesse standard) | ✅ Confirmed |

**Implementation**: Crests generated on first request, cached to `mod_cache/crests/crest_{crestUID}_{size}.png`. Invalidated on design change (revision++).

### 4) Disband Treasury Policy
✅ **Option B: Redistribute to Members** — When a guild is disbanded, the treasury balance is evenly distributed among all current members.

```java
// On disband:
long perMember = treasuryGold / members.size();
for (GuildMember member : members) {
    depositToPlayerInventory(member.playerAuth, perMember);
}
```

### 5) Asset Approvals
✅ **Assets approved** — See `docs/assets.csv` for complete mapping. Preview gallery at `docs/assets_preview/`.

### 6) Cached Crest Persistence
✅ **Yes** — Cached crest textures persist across mod updates. Cache only invalidated when crest design changes (revision increment).

### 7) Treasury Minimum Threshold (NEW)
✅ **Added** — The Scientist automation respects a configurable minimum treasury balance. Research pulls stop when treasury would fall below this threshold.

| Setting | Default Value | Range | Notes |
|---------|---------------|-------|-------|
| `treasuryMinimumThreshold` | **10,000** | 0 – ∞ | Leader/Officer configurable per guild |

**Implementation**: Scientist pull logic checks `if (treasuryGold - pullAmount >= treasuryMinimumThreshold)` before consuming resources.

### 8) Research Item Gating Policy (NEW)
✅ **No Gating** — If a player has the item to deposit, it counts toward research progress regardless of boss kills or progression state.

**Rationale**: Simplifies the system; players naturally gate themselves by item availability. No artificial boss-kill requirements for research nodes.

---

> **Implementation Ready**: All decisions finalized. Proceed to `docs/guilds_implementation_plan.md` for coding.

---

# Section 1: The Guild Artisan NPC

## 1.1 Concept Summary
The Guild Artisan is the gateway NPC for all guild-related features. Players interact with this NPC to create guilds, join existing guilds, purchase guild items, and manage their memberships.

## 1.2 NPC Class Implementation

### Base Class Selection
- **Parent Class**: `HumanMob` (extends `ItemAttackerMob`, implements `SettlerMob`, `CommandMob`, `EntityJobWorker`)
- **Why**: HumanMob provides full settler integration (beds, rooms, jobs, happiness, equipment) and fits naturally into the settlement ecosystem

### Classes to Create
| File | Package | Purpose |
|------|---------|---------|
| `GuildArtisanMob.java` | `medievalsim.mobs` | The NPC entity class |

### Key Methods to Override
```java
// From HumanMob - interaction opens guild container
@Override
public void interact(Level level, int x, int y, PlayerMob player) {
    if (level.isServer()) {
        ServerClient client = player.getServerClient();
        PacketOpenContainer packet = PacketOpenContainer.Mob(
            ContainerRegistry.GUILD_ARTISAN_CONTAINER, this);
        ContainerRegistry.openAndSendContainer(client, packet);
    }
}

// Display name with personality
@Override
public GameMessage getLocaleDisplayName() {
    return new LocalMessage("mob", "guildartisan");
}

// Standard settler combat - uses equipped weapons
@Override
public boolean canBeHurt() {
    return true; // Normal settler vulnerability
}
```

### NPC Behavior (DECIDED)
| Behavior | Decision | Implementation Notes |
|----------|----------|---------------------|
| Perform settler jobs | ✅ Yes | Acts like other NPCs - farming, hauling, crafting based on work priorities |
| Recruitable/dismissable | ✅ Yes | Can be moved between settlements, banished if desired |
| Combat abilities | ✅ Yes | Standard settler combat - equips weapons, defends settlement |
| Limit per settlement | ✅ ONE only | Check in spawn logic - skip if settlement already has one |

### Personality & Characterization
To make the Guild Artisan feel unique like other NPCs:
- **Idle Animations**: Shuffling papers, examining seals, polishing guild emblems
- **Thought Bubbles**: Shows guild-related thoughts (crests, gold coins, documents)
- **Dialogue Flavor**: Localization includes guild-themed greetings
- **Work Preference**: Prefers desk/administrative work when available, but will do other jobs

### Settlement Limit Enforcement
```java
// In spawn check logic
public static boolean canSpawnInSettlement(ServerSettlementData settlement) {
    for (LevelSettler settler : settlement.settlers) {
        if (settler.getMob() instanceof GuildArtisanMob) {
            return false; // Already has one
        }
    }
    return true;
}

## 1.3 Spawn Logic

### Spawn Conditions
1. Player has placed a Settlement Flag (check via `SettlementsWorldData`)
2. Player has defeated the configured "Unlock Boss"

### Unlock Boss Configuration (DECIDED)
**Implementation**: Dropdown enum in ModConfig allowing server owners to choose which boss unlocks guilds.

```java
// In ModConfig.java
public static class Guilds {
    @ModConfigFieldName(name = "Guild Unlock Boss")
    @ModConfigDesc("Which boss must be defeated to unlock guild features")
    public static GuildUnlockBoss unlockBoss = GuildUnlockBoss.EVILS_PROTECTOR;
}

public enum GuildUnlockBoss {
    EVILS_PROTECTOR("evilsprotector", "Evil's Protector"),
    VOID_WIZARD("voidwizard", "Void Wizard"),
    PIRATE_CAPTAIN("piratecaptain", "Pirate Captain"), *Default*,
    ANCIENT_VULTURE("ancientvulture", "Ancient Vulture"),
    SWAMP_GUARDIAN("swampguardian", "Swamp Guardian"),
    QUEEN_SPIDER("queenspider", "Queen Spider"),
    NONE("none", "No Boss Required");
    
    public final String bossID;
    public final String displayName;
}
```

**Boss Kill Detection**: Check player's boss kill achievements via `PlayerMob.achievements` or world boss defeat flags.

### Spawn Method (DECIDED): Hybrid Approach

**Implementation Flow**:
```
Player enters settlement with Settlement Flag
    │
    ├─► Has NOT defeated unlock boss
    │       └─► If player talks to ANY Guild Artisan elsewhere:
    │               Show quest: "Prove Your Worth"
    │               Quest objective: Defeat [configured boss] and own a settlement
    │               Reward: Guild Artisan visits your settlement
    │
    └─► HAS defeated unlock boss
            └─► Add GuildArtisanVisitorSpawner to visitor pool
            └─► If player already has quest completed:
                    Add to Spawn as visitor
```

### Visitor Spawner Implementation
```java
public class GuildArtisanVisitorSpawner extends SettlementVisitorSpawner {
    
    @Override
    public boolean canSpawn(ServerSettlementData settlement) {
        // Check if settlement already has a Guild Artisan
        if (!GuildArtisanMob.canSpawnInSettlement(settlement)) {
            return false;
        }
        // Check if any owner has defeated the unlock boss
        return hasOwnerDefeatedUnlockBoss(settlement);
    }
    
    @Override
    public int getTickets(ServerSettlementData settlement) {
        return 100; // Moderate spawn weight
    }
    
    @Override
    public HumanMob createVisitor(ServerSettlementData settlement) {
        GuildArtisanMob artisan = new GuildArtisanMob();
        // Configure as recruit-seeking visitor
        return artisan;
    }
}
```

### Quest: "Prove Your Worth" *needs to also own a settlement*
| Property | Value |
|----------|-------|
| Quest ID | `medievalsim:guild_unlock_quest` |
| Quest Giver | Any existing Guild Artisan (in other settlements/towns) |
| Objective | Defeat the configured unlock boss |
| Reward | Guild Artisan immediately added to visitor queue |
| Repeatable | No (once per player) |

## 1.4 Guild Artisan UI Container

### Container Classes to Create
| File | Purpose |
|------|---------|
| `GuildArtisanContainer.java` | Server-side container logic |
| `GuildArtisanContainerForm.java` | Client-side UI form |

### UI Flow Logic
```
Player interacts with Guild Artisan
    │
    ├─► Player NOT in any guild
    │       └─► Show: [Create Guild] [Join Guild] [Browse Plots*]
    │
    └─► Player IS in guild(s)
            └─► Show: [View Guild Info] [Manage Guilds] [Create Guild] (if unlock/limits allow) [Join Guild] (if not at max memberships)

Notes:
- **The Artisan does NOT provide direct access to the Guild Bank or Guild Settings**; those are accessed via the Settlement Settings (C key) / Guild Flag.
- `Manage Guilds` opens `ManageGuildsForm` that lists the player's guild memberships; leaving a guild and purchasing a guild banner are per-guild actions inside that form.

* Browse Plots only visible if server config has plots enabled (Route B)
| Guild Description | Text Area | Optional, max 256 characters |
| Privacy Toggle | Checkbox | Public (joinable) or Private (invite-only) |
| Crest Designer | Button | Opens crest/flag design sub-form |
| Creation Cost Display | Label | Shows configured cost from ModConfig |
| Create Button | Button | Validates input, deducts gold, creates guild |

**Creation Cost (DECIDED)**:
```java
// ModConfig.java
@ModConfigFieldName(name = "Guild Creation Cost")
@ModConfigDesc("Gold required to create a new guild")
public static int guildCreationCost = 50000;
```

Manage Guilds wireframe (new):
```
+---------------------------------------------+
| Manage Guilds                               |
| ------------------------------------------  |
| Guilds you belong to:                       |
| 1) [Guild Name A]  [ Leave ] [ Buy Banner ] |
| 2) [Guild Name B]  [ Leave ] [ Buy Banner ] |
| 3) [Guild Name C]  [ Leave ] [ Buy Banner ] |
|                                             |
| [ Create Guild ]  [ Join Guild ]   [ Close ]|
+---------------------------------------------+
```

---

Route B (Plots) — Detailed Survey HUD & Test Cases
- Purpose: Provide a clear and testable spec for the Route B 'Survey & Purchase' flow used when plots are enabled.

Survey UI & Flow (wireframe):
```
+---------------------------------------------+
| Guild Plots Available                        |
| ------------------------------------------  |
| 1) Northhold Keep - Price: 1,000,000         |
|    - Includes: 10 settlers, 3 workstations    |
|    [ Survey ] [ Purchase ]                   |
| 2) Isle Outpost - Price: 750,000             |
|    - Includes: 6 settlers, 2 resources       |
|    [ Survey ] [ Purchase ]                   |
+---------------------------------------------+
```
- `Survey` (packet: `PacketSurveyPlot(plotID)`) teleports player into a **timed session** (default 10 minutes) within the plot and opens a temporary HUD with `Purchase`, `Leave` and optional `Pause` controls.
- While in a survey session the player:
  - Sees a visible session timer
  - Can `Purchase` (packet: `PacketPurchaseGuildPlot(plotID)`) — server atomically reserves + charges funds then teleports player into the new guild zone and begins a post-purchase tutorial HUD instructing flag placement
  - Can `Leave` to end the session and be teleported back to the Guild Artisan or origin point

Edge Cases & Server Behavior (tests to add):
- Server restart during session: session state persists server-side and resumes on reconnect; long disconnects (> `survey.sessionTimeout`) cancel the session and return the player to the Guild Artisan when they next interact.
- Concurrent surveyors on same plot: allowed; first `Purchase` wins. Later purchasers get a failure and are returned to Artisan or offered an alternate plot.
- Purchase race: server must reserve plot atomically (test: N clients attempt purchase simultaneously; only one succeeds and funds are deducted properly).
- Post-purchase flag placement failure: if player cannot place flag because of invalid placement, HUD instructs player; HUD persists until flag is placed or player cancels; provide refund or manual admin resolution per `ModConfig.Guilds.disbandFlagBehavior`.
- HUD pause / abandon semantics: `Pause` suspends timer for short configurable time; `Leave` cancels session and returns to Artisan.

Acceptance Criteria:
- Survey opens HUD with timer and controls; `PacketSurveyPlot` starts a server-reserved session.
- `PacketPurchaseGuildPlot` performs atomic purchase and teleports player into purchased zone.
- Race conditions and server restart/resume handled and covered by tests.

---

#### Section: Crest Designer Sub-Form
| Element | Type | Details |
|---------|------|---------|
| Guild List | Scrollable List | Shows public guilds |
| Search Box | Text Input | Filter by name,tier, etc |
| Guild Details | Panel | Selected guild info |
| Join Button | Button | Request to join / Join directly |
| Invitations Tab | Tab | Shows pending invites |

**Guild List Entry Shows**:
- Guild name + crest icon
- Member count / max members
- Description preview
- "Public" or "By Invitation" tag

#### Section: Guild Info (when in guild)
| Element | Details |
|---------|---------|
| Guild Name | With crest icon |
| Your Rank | Leader/Officer/Member/Recruit |
| Member Count | X / MaxMembers |
| Research Summary | "3/15 nodes unlocked" |
| Quick Actions | View Research, Open Bank, etc. |

#### Section: Purchase Items
| Item | Cost | Type | Description |
|------|------|------|-------------|
| Guild Teleport Bottle | 50 gold (configurable) | Reusable | Teleports to your guild's flag | 
*lets change this to a guild teleport stand and it will have their guild crest, that way you can interact with a placed item.* 
| Plot Survey | 100 gold (configurable) | Consumable | Preview available plots (Route B only) |

**Teleport Bottle (DECIDED)**:
```java
// ModConfig.java
@ModConfigFieldName(name = "Teleport Bottle Cost")
public static int teleportBottleCost = 50;

// GuildTeleportBottleItem.java
public class GuildTeleportBottleItem extends Item {
    // Reusable - no stack consumption on use
    // Cooldown: 60 seconds between uses
    // Teleports to guild flag location
}
```

#### Section: Manage Guilds
| Element | Details |
|---------|---------|
| Guild List | All guilds player belongs to |
| Per-Guild Options | View Details, Leave Guild |
| Leave Confirmation | "Are you sure?" dialog |

**Leave Guild Logic**:
- Cannot leave if you're the only Leader (must promote someone first or disband)
- Crest becomes inert immediately upon leaving

## 1.5 Multi-Guild Support

### Player Guild Data Storage
Each player tracks:
- List of guild IDs they belong to
- Rank within each guild
- Which crest they currently have equipped (if any)

### Multi-Guild Limits (DECIDED)
```java
// ModConfig.java
@ModConfigFieldName(name = "Max Guilds Per Player")
@ModConfigDesc("Maximum number of guilds a player can join simultaneously")
public static int maxGuildsPerPlayer = 3; // Adjustable by server

// Leadership restriction
// A player can only be LEADER of ONE guild at a time
// They can be Officer/Member/Recruit of other guilds
```

### Leadership Transfer Logic
When a Leader wants to join another guild as Leader:
1. They must first promote an Officer to Leader in their current guild
2. Then they become Officer in that guild
3. Now they can create/become Leader of a new guild

### PlayerGuildData Implementation
```java
public class PlayerGuildData {
    private long playerAuth;
    private List<GuildMembership> memberships = new ArrayList<>();
    
    public static class GuildMembership {
        int guildID;
        GuildRank rank;
        long joinedTime;
    }
    
    public boolean canJoinNewGuild() {
        return memberships.size() < ModConfig.Guilds.maxGuildsPerPlayer;
    }
    
    public boolean canBecomeLeader() {
        return memberships.stream()
            .noneMatch(m -> m.rank == GuildRank.LEADER);
    }
}

## 1.6 Required Assets

### Sprites Needed
| Asset | Size | Description |
|-------|------|-------------|
| `mobs/guildartisan.png` | 32x32+ | NPC sprite sheet (idle, walk, work animations) |
| `ui/guildartisan_icon.png` | 16x16 | Icon for UI elements |

### Localization Keys
```
mob.guildartisan = Guild Artisan
mob.guildartisan.description = A specialist in guild formation and management
ui.guildartisan.create = Create Guild
ui.guildartisan.join = Join Guild
ui.guildartisan.browse = Browse Plots
ui.guildartisan.teleport = Guild Teleport Bottle
ui.guildartisan.survey = Plot Survey
```

---

# Section 2: Land & Ownership Logic

## 2.1 Overview: Guild = Settlement Model (CLARIFIED)
**FUNDAMENTAL CHANGE**: A guild IS a settlement, not a separate entity.

### Core Rules
| Rule | Implementation |
|------|----------------|
| **Guild Leader** | Always the settlement owner |
| **One settlement per guild** | Guild cannot span multiple settlements |
| **One guild leadership** | Player cannot lead multiple guilds |
| **Settlement founder leaves** | Leadership transfers to **top contributing member** |
| **Leadership transfer** | Leader can manually transfer before leaving |

### Settlement-Guild Conversion
When a settlement owner creates a guild:
1. **Settlement becomes the guild** (1:1 relationship)
2. **Owner becomes guild leader** automatically  
3. **Settlement permissions become guild permissions**
4. **Other players can join the guild** (and thus access the settlement)
5. **Guild flag replaces settlement flag** (visual change only)

### Leadership Transfer Logic
```java
public void handleSettlementOwnerLeaves(Settlement settlement, long formerOwnerAuth) {
    GuildData guild = getGuildBySettlement(settlement.getID());
    if (guild == null) return;
    
    // Option 1: Leader manually transferred before leaving
    if (guild.getLeader() != formerOwnerAuth) {
        transferSettlementOwnership(settlement, guild.getLeader());
        return;
    }
    
    // Option 2: Auto-transfer to top contributor (by research donations)
    long topContributor = guild.getTopContributingMember();
    if (topContributor != -1) {
        guild.setLeader(topContributor);
        transferSettlementOwnership(settlement, topContributor);
    } else {
        // No suitable successor - disband guild, settlement becomes unowned
        disbandGuild(guild.getID());
    }
}
```

## 2.2 Route A: Settlement Conversion (Standard Play)

### Concept
Players may purchase a Guild Flag item from the Guild Artisan, but **management and settings for guild-owned settlements are exposed via the Settlement Settings (C key)**. Purchasing replaces the existing Settlement Flag and converts ownership; flag placement itself remains an item action while settings live in the standard settlement UI.

### Implementation Details

**Guild Flag Object**
| File | Purpose |
|------|---------|
| `GuildFlagObject.java` | The placeable object (extends `SettlementFlagObject`) |
| `GuildFlagObjectEntity.java` | Entity for the object (extends `SettlementFlagObjectEntity`) |
| `GuildFlagObjectItem.java` | Item version for inventory |

**Key Behaviors**
- Placement: Must replace existing Settlement Flag position
- On placement: Transfers all settlement data to guild ownership
- Ownership field: Changes from `long ownerAuth` (PlayerID) to `int guildID`
- Visual: Displays guild crest instead of player look

**Placement Validation**:
```java
@Override
public String canPlace(Level level, int layerID, int x, int y, int rotation, 
                       boolean byPlayer, boolean ignoreOtherLayers) {
    // Must be placed on surface
    if (!level.getIdentifier().equals(LevelIdentifier.SURFACE_IDENTIFIER)) {
        return "notsurface";
    }
    // Check if there's an existing settlement flag nearby to replace
    // OR if this is a valid new guild plot location
    return null;
}
```

### Permission System (DECIDED)

**Default Permissions** (Leader can customize these):
| Action                    | Leader | Officer | Member | Recruit |
|--------                   |:------:|:-------:|:------:|:-------:|
| Place blocks              | ✅    | ✅      | ❌     | ❌     |
| Break blocks              | ✅    | ✅      | ❌     | ❌     |
| Access settlement storage | ✅    | ✅      | ✅     | ✅     |
| Access Guild Bank         | ✅    | ✅      | ✅     | ❌     |
| Modify research priorities| ✅    | ✅      | ❌     | ❌     |
| Invite members            | ✅    | ✅      | ✅     | ❌     |
| Kick members              | ✅    | ✅      | ❌     | ❌     |
| Promote/demote            | ✅    | ❌      | ❌     | ❌     |
| Set taxation %            | ✅    | ❌      | ❌     | ❌     |
| Withdraw treasury         | ✅    | ✅      | ❌     | ❌     |

### Customizable Permissions Implementation
```java
public class GuildPermissions {
    // Each permission stored as a minimum rank required
    private Map<PermissionType, GuildRank> permissions = new HashMap<>();
    
    // Default constructor sets above defaults
    public GuildPermissions() {
        permissions.put(PermissionType.BUILD, GuildRank.OFFICER);
        permissions.put(PermissionType.BREAK, GuildRank.OFFICER);
        permissions.put(PermissionType.ACCESS_STORAGE, GuildRank.RECRUIT);
        permissions.put(PermissionType.ACCESS_GUILD_BANK, GuildRank.MEMBER);
        permissions.put(PermissionType.MODIFY_RESEARCH, GuildRank.OFFICER);
        permissions.put(PermissionType.INVITE_MEMBER, GuildRank.MEMBER);
        permissions.put(PermissionType.KICK_MEMBER, GuildRank.OFFICER);
        permissions.put(PermissionType.PROMOTE_DEMOTE, GuildRank.LEADER);
        permissions.put(PermissionType.SET_TAX_RATE, GuildRank.LEADER);
        permissions.put(PermissionType.WITHDRAW_TREASURY, GuildRank.OFFICER);
    }
    
    // Leader can modify via Guild Settings UI
    public void setPermission(PermissionType type, GuildRank minRank) {
        // PROMOTE_DEMOTE always requires LEADER - cannot be changed
        if (type == PermissionType.PROMOTE_DEMOTE) return;
        permissions.put(type, minRank);
    }
    
    public boolean hasPermission(GuildRank playerRank, PermissionType type) {
        GuildRank required = permissions.get(type);
        return playerRank.level >= required.level;
    }
}
```

### Permission Settings UI
Located in Guild Flag → Settings tab (Leader only):
- Dropdown for each permission type
- Options: Leader Only, Officer+, Member+, All Members
- Save button applies changes
- Reset to Defaults button

## 2.3 Route B: Admin Plot Management (Server Play)
*needs to be fleshed out. i want to make it so that we add a zone called guild zones to our zone tools. this way the admin can specify the shape of the guild. plotted guilds cant be upgraded without talking to the admins of the server, unlike regular guilds. while viewing a plot of land or from the plot list you should be allowed to purchase a plot for its cost. if you purchase a plot you are given a guild teleport stand(lets actually make it a picture), a guild flag, and are instantly teleported to your new guild and directed to place your guild flag so that you can get back there. after placing a guild flag you can not move it/destroy it.* 
### Concept
Server admins pre-define zones as purchasable "Guild Plots." Players browse and claim these through the Guild Artisan.

### Implementation Details

**Plot Definition System**
| File | Purpose |
|------|---------|
| `GuildPlot.java` | Data class for plot boundaries and metadata |
| `GuildPlotManager.java` | World-level manager for all plots |
| `AdminPlotToolItem.java` | Admin tool for defining plot corners |

**Plot Properties**
```java
public class GuildPlot {
    int plotID;                    // Unique identifier
    String plotName;               // Display name (e.g., "Northern Valley")
    Rectangle bounds;              // Tile boundaries (x, y, width, height)
    LevelIdentifier level;         // Which level/dimension
    int owningGuildID;             // 0 if unclaimed
    int purchasePrice;             // Gold cost to claim (admin-set)
    long claimedTime;              // When it was claimed
    String description;            // Optional flavor text
}
```

**Real Estate UI**
- World map overlay showing plot boundaries
- Color coding: Green = available, Red = claimed by others, Blue = yours
- Click to view details and purchase
- Shows plot name, size, price, current owner

### Plot Pricing (DECIDED)
*Automatic calculation based on number and quality rooms, workstations, storage, work zones with validation for whether husbandry has stables and troughs, forestry has planted trees, farming has access to fertilizer and soil. Judged based on capacity filled in a work zone.*

```java
public class PlotPricingCalculator {
    
    @ModConfigFieldName(name = "Base Plot Price")
    public static int basePlotPrice = 100000;  // Starting value
    
    public static int calculatePlotPrice(ServerSettlementData settlement) {
        int price = basePlotPrice;
        
        // Settler value: 1,000g per settler
        price += settlement.settlers.size() * 1000;
        
        // Room quality assessment
        int qualityRoomCount = 0;
        for (SettlementRoom room : settlement.rooms.getAllRooms()) {
            if (room.getRoomScore() >= 80) qualityRoomCount++;  // High quality
        }
        price += qualityRoomCount * 5000;  // 5k per quality room
        
        // Storage capacity: 500g per storage object
        price += settlement.storageManager.getStorageCount() * 500;
        
        // Workstation value: 2,000g per advanced workstation
        price += countAdvancedWorkstations(settlement) * 2000;
        
        // Work zone validation bonuses
        if (hasValidHusbandryZone(settlement)) price += 50000;
        if (hasValidForestryZone(settlement)) price += 30000;
        if (hasValidFarmingZone(settlement)) price += 40000;
        
        return price;
    }
    
    private static boolean hasValidHusbandryZone(ServerSettlementData settlement) {
        // Check for Husbandry work zone with:
        // - At least 1 animal stable object
        // - At least 1 feeding trough object
        // - Capacity filled > 50%
        WorkZone zone = findWorkZone(settlement, WorkZoneType.HUSBANDRY);
        if (zone == null) return false;
        
        boolean hasStable = zone.containsObjectType("animalpen");
        boolean hasTrough = zone.containsObjectType("feedingtrough");
        float capacityFilled = zone.getCapacityFilledPercentage();
        
        return hasStable && hasTrough && capacityFilled > 0.5f;
    }
    
    private static boolean hasValidForestryZone(ServerSettlementData settlement) {
        // Check for Forestry work zone with:
        // - At least 10 planted trees in zone
        // - Sawmill or similar workstation
        WorkZone zone = findWorkZone(settlement, WorkZoneType.FORESTRY);
        if (zone == null) return false;
        
        int plantedTrees = zone.countPlantedTrees();
        boolean hasWorkstation = zone.containsObjectType("sawmill");
        
        return plantedTrees >= 10 && hasWorkstation;
    }
    
    private static boolean hasValidFarmingZone(ServerSettlementData settlement) {
        // Check for Farming work zone with:
        // - Tilled soil tiles
        // - Fertilizer usage (compostheap nearby)
        // - Capacity filled > 50%
        WorkZone zone = findWorkZone(settlement, WorkZoneType.FARMING);
        if (zone == null) return false;
        
        boolean hasTilledSoil = zone.hasTilledSoilTiles();
        boolean hasFertilizer = zone.containsObjectType("compostheap");
        float capacityFilled = zone.getCapacityFilledPercentage();
        
        return hasTilledSoil && hasFertilizer && capacityFilled > 0.5f;
    }
}
```

**Pricing Range Examples**:
- **Undeveloped plot**: ~100,000g (base price)
- **Small developed settlement**: ~250,000g (10 settlers, few rooms)
- **Medium quality settlement**: ~500,000g (quality rooms, storage, some work zones)
- **Premium fully-developed**: ~1,000,000g+ (all bonuses, high capacity)

### Plot Survey Item
```java
public class PlotSurveyItem extends ConsumableItem {
    // Consumable - one use
    // When used: Opens plot browser UI
    // Player can click a plot to teleport there temporarily
    // After 60 seconds OR when they click "Return", teleports back
    // Cannot interact with anything while surveying
}
```

### Plot Mechanics (DECIDED)
| Rule | Decision | Implementation |
|------|----------|----------------|
| Multiple plots per guild | ❌ No | One plot per guild max |
| Sell back plots | ✅ Yes | Returns partial gold to treasury |
| Refund amount | Based on settlement value | See below |

### Plot Sellback Calculation
*Development should let you go way beyond for improving the sellback value*
When a guild sells their plot back:
```java
public int calculatePlotRefund(GuildPlot plot, ServerSettlementData settlement) {
    // Base refund: 50% of purchase price
    int baseRefund = plot.purchasePrice / 2;
    
    // Bonus based on settlement development
    int developmentBonus = 0;
    developmentBonus += settlement.settlers.size() * 100;      // Per settler
    developmentBonus += settlement.rooms.getRoomCount() * 50;  // Per room
    developmentBonus += settlement.storageManager.getStorageCount() * 25;
    
    // Cap bonus at 25% of original price
    int maxBonus = plot.purchasePrice / 4;
    developmentBonus = Math.min(developmentBonus, maxBonus);
    
    return baseRefund + developmentBonus;
    // Total refund: 50-75% of purchase price depending on development
}
```

### Admin Plot Tool 
*make zoning tool*
```java
public class AdminPlotToolItem extends ToolItem {
    // Right-click: Set corner 1
    // Right-click again: Set corner 2, creates plot
    // Shows preview rectangle while selecting
    // Opens naming/pricing dialog after corners set
    // Only usable by server admins (isAdmin check)
}
```

---

# Section 3: The Guild Heart & Shared Logistics

## 3.1 The Guild Flag (Heart)

### Core Functions
The Guild Flag serves as the central hub for all guild operations:
1. Guild Bank access point
2. Teleport destination for Guild Teleport Bottles
3. Research Tree access point
4. Guild management interface

### Container to Create
| File | Purpose |
|------|---------|
| `GuildFlagContainer.java` | Server container with tabs |
| `GuildFlagContainerForm.java` | Client UI with multiple sections |

## 3.2 Guild Bank

### Concept
A shared inventory accessible by all guild members from any Guild Flag. Separate from settlement storage - this is guild-wide.

### Implementation

**Storage Structure**
- Virtual inventory stored in `GuildData` (not tied to physical object)
- Multiple "tabs" or pages of 50 slots each
- Starts with 1 tab (50 slots), upgradeable via Research Tree

**Technical Approach**
```java
public class GuildBankInventory extends Inventory {
    private int unlockedTabs = 1;  // Start with 1 tab
    private static final int SLOTS_PER_TAB = 50;
    private static final int MAX_TABS = 4;  // 200 slots max
    
    public GuildBankInventory() {
        super(SLOTS_PER_TAB);  // Start with 50 slots
    }
    
    public void unlockTab() {
        if (unlockedTabs < MAX_TABS) {
            unlockedTabs++;
            // Resize inventory
            this.setSize(unlockedTabs * SLOTS_PER_TAB);
        }
    }
    
    public int getUnlockedSlots() {
        return unlockedTabs * SLOTS_PER_TAB;
    }
}
```

### Guild Bank Configuration (DECIDED)
| Setting | Value |
|---------|-------|
| Starting slots | 50 (1 tab) |
| Slots per tab | 50 |
| Maximum tabs | 4 |
| Maximum slots | 200 |

### Bank Access Permissions (DECIDED)
| Action | Minimum Rank |
|--------|--------------|
| Deposit items | All members (Recruit+) |
| Withdraw items | Member+ (not Recruits) |

```java
// In GuildBankContainer.java
@Override
public boolean canPlayerDeposit(PlayerMob player) {
    return GuildPermissionHelper.isPlayerInGuild(player.getUniqueID(), guildID);
}

@Override
public boolean canPlayerWithdraw(PlayerMob player) {
    GuildRank rank = GuildPermissionHelper.getPlayerRank(
        player.getUniqueID(), guildID);
    return rank != null && rank.level >= GuildRank.MEMBER.level;
}
```

### Guild Bank UI
```
┌─────────────────────────────────────────────────────┐
│  GUILD BANK                    Treasury: 125,430 G  │
├─────────────────────────────────────────────────────┤
│  [Tab 1] [Tab 2] [Tab 3🔒] [Tab 4🔒]               │
├─────────────────────────────────────────────────────┤
│  ┌──┬──┬──┬──┬──┬──┬──┬──┬──┬──┐                  │
│  │  │  │  │  │  │  │  │  │  │  │  (50 slots)      │
│  ├──┼──┼──┼──┼──┼──┼──┼──┼──┼──┤                  │
│  │  │  │  │  │  │  │  │  │  │  │                  │
│  ├──┼──┼──┼──┼──┼──┼──┼──┼──┼──┤                  │
│  │  │  │  │  │  │  │  │  │  │  │                  │
│  ├──┼──┼──┼──┼──┼──┼──┼──┼──┼──┤                  │
│  │  │  │  │  │  │  │  │  │  │  │                  │
│  ├──┼──┼──┼──┼──┼──┼──┼──┼──┼──┤                  │
│  │  │  │  │  │  │  │  │  │  │  │                  │
│  └──┴──┴──┴──┴──┴──┴──┴──┴──┴──┘                  │
│                                                     │
│  [Deposit Gold: ____] [Deposit]  [Withdraw: ____]  │
└─────────────────────────────────────────────────────┘

🔒 = Locked (requires research to unlock)
```

## 3.3 The Siphon (Taxation System)

### Concept
When a Merchant NPC is present in a guild settlement, a percentage of Grand Exchange sales by guild members is automatically deposited into the Guild Treasury.

### Implementation

**Integration Point**
- Hook into Grand Exchange transaction completion
- Check if seller is guild member
- Check if guild has Merchant in settlement
- Calculate and transfer percentage

**Files to Modify/Create**
| File | Purpose |
|------|---------|
| `GuildTaxationHelper.java` | Calculation and transfer logic |
| `GrandExchangePatch.java` | ByteBuddy patch to hook transactions |

### Taxation Configuration (DECIDED)
*Multiple guilds stack ADDITIVELY - each guild takes their cut from your trades.*

**Stacking Method**: Additive (2% + 2% = 4% total)

**Example Scenario**:
- Player is in 2 guilds
- Guild A: 2% tax rate
- Guild B: 3% tax rate  
- Player sells item for 10,000 gold

**Calculation**:
- Guild A takes: 10,000 × 0.02 = 200g
- Guild B takes: 10,000 × 0.03 = 300g
- Total tax: 500g (5%)
- Player receives: 9,500g

```java
// ModConfig.java
public static class Guilds {
    @ModConfigFieldName(name = "Default Tax Rate")
    @ModConfigDesc("Default Grand Exchange tax rate for new guilds (percentage)")
    public static float defaultTaxRate = 2.0f;  // 2%
    
    @ModConfigFieldName(name = "Minimum Tax Rate")
    public static float minTaxRate = 0.1f;      // 0.1%
    
    @ModConfigFieldName(name = "Maximum Tax Rate")
    public static float maxTaxRate = 5.0f;      // 5% per guild
    
    // Note: Theoretical max is 5% × 3 guilds = 15% total if player in 3 guilds
}

// GuildData.java
private float taxRate = ModConfig.Guilds.defaultTaxRate;

public void setTaxRate(float rate) {
    this.taxRate = Math.max(ModConfig.Guilds.minTaxRate, 
                   Math.min(rate, ModConfig.Guilds.maxTaxRate));
}
```

**Tax Rate Adjustment**: Leader only (per permissions table)

### Tax Display (DECIDED)
- ✅ Show tax in Grand Exchange UI
- ❌ No separate notification - visible in GE UI

**Grand Exchange UI Modification**:
```
When listing an item for sale:
┌─────────────────────────────────────────┐
│  Sell: Iron Bar x50                     │
│  Price per unit: 100 gold               │
│  Total: 5,000 gold                      │
│  Guild Tax (2%): -100 gold              │  ← New line
│  You receive: 4,900 gold                │  ← New line
│  [Confirm Sale]                         │
└─────────────────────────────────────────┘
```

### Tax Collection Logic
```java
public class GuildTaxationHelper {
    
    public static void onGrandExchangeSale(PlayerMob seller, int saleAmount) {
        // Get seller's active guild
        PlayerGuildData playerData = GuildManager.getPlayerData(seller.getUniqueID());
        if (playerData == null || playerData.memberships.isEmpty()) return;
        
        // Check each guild the player is in
        for (GuildMembership membership : playerData.memberships) {
            GuildData guild = GuildManager.getGuild(membership.guildID);
            if (guild == null) continue;
            
            // Check if guild has a Merchant in any settlement
            if (!guildHasMerchant(guild)) continue;
            
            // Calculate and apply tax
            int taxAmount = (int)(saleAmount * (guild.getTaxRate() / 100f));
            if (taxAmount > 0) {
                guild.depositToTreasury(taxAmount);
                // Log transaction for guild records
                guild.logTransaction(TransactionType.TAX, seller.getUniqueID(), taxAmount);
            }
        }
    }
    
    private static boolean guildHasMerchant(GuildData guild) {
        // Check all guild settlements for a Merchant NPC
        for (ServerSettlementData settlement : guild.getSettlements()) {
            for (LevelSettler settler : settlement.settlers) {
                if (settler.getMob() instanceof MerchantHumanMob) {
                    return true;
                }
            }
        }
        return false;
    }
}
```

### Merchant Requirement
- Tax only collected if guild has at least ONE Merchant settler
- Encourages players to recruit merchants
- Merchant doesn't need to be in same settlement as seller

## 3.4 Guild Treasury

### Concept
A gold pool separate from the Guild Bank (items). Used for:
- Research material costs (value equivalent)
- Plot purchases (Route B)
- Guild-wide purchases
- Displayed at top of the Guild Bank UI

### Treasury Implementation
```java
// In GuildData.java
private long treasuryGold = 0;

public void depositToTreasury(long amount) {
    treasuryGold += amount;
    syncTreasuryToClients();
    logTransaction(TransactionType.DEPOSIT, amount);
}

public boolean withdrawFromTreasury(long amount, long playerAuth) {
    if (treasuryGold >= amount) {
        treasuryGold -= amount;
        syncTreasuryToClients();
        logTransaction(TransactionType.WITHDRAW, playerAuth, amount);
        return true;
    }
    return false;
}

// Transaction log for audit trail
private List<TreasuryTransaction> transactionLog = new ArrayList<>();
```

### Treasury Access (DECIDED)
| Action | Who Can Do It |
|--------|---------------|
| View balance | ✅ All members |
| Deposit gold | ✅ All members |
| Withdraw gold | Officer+ (with optional limit) |

### Withdraw Limits (Optional Enhancement)
```java
// ModConfig.java
@ModConfigFieldName(name = "Officer Withdraw Limit")
@ModConfigDesc("Max gold Officers can withdraw per day. 0 = unlimited. Leaders always unlimited.")
public static int officerDailyWithdrawLimit = 0;  // 0 = no limit

// In GuildData
private Map<Long, DailyWithdrawTracker> withdrawTrackers = new HashMap<>();

public boolean canOfficerWithdraw(long playerAuth, long amount) {
    if (ModConfig.Guilds.officerDailyWithdrawLimit == 0) return true;
    
    DailyWithdrawTracker tracker = withdrawTrackers.get(playerAuth);
    if (tracker == null || tracker.isNewDay()) {
        tracker = new DailyWithdrawTracker();
        withdrawTrackers.put(playerAuth, tracker);
    }
    
    return tracker.getTodayTotal() + amount <= ModConfig.Guilds.officerDailyWithdrawLimit;
}
```

### Treasury UI (in Guild Bank)
```
┌─────────────────────────────────────────────────────┐
│  GUILD TREASURY                                      │
├─────────────────────────────────────────────────────┤
│  Balance: 125,430 gold                              │
│                                                      │
│  [Deposit: _______] [Deposit]                       │
│  [Withdraw: ______] [Withdraw]  (Officer+ only)     │
│                                                      │
│  Recent Transactions:                                │
│  • +500g Tax from PlayerName (2 min ago)            │
│  • -10,000g Research: Swift Feet (1 hour ago)       │
│  • +1,000g Deposit by OfficerName (3 hours ago)     │
└─────────────────────────────────────────────────────┘
```

---

# Section 4: NPC Role Expansion

## 4.1 Overview
Instead of creating new NPCs, existing settler types gain special "Guild Tasks" when living within a Guild Zone. This leverages the existing job system in `HumanMob`.

## 4.2 Role Definitions

### Scientist → Lead Researcher

**Guild Task**: Manages automated research by pulling materials from Guild Bank.

**Implementation - Automated Research System**
The Scientist automatically pulls resources from the Guild Bank at regular intervals to progress research. Leaders/Officers set research priorities.

**Core Mechanics**:
```java
public class GuildResearchManager {
    private static final int BASE_PULL_RATE = 10000;  // Resources per hour (UPDATED: was 1000)
    private static final int MAX_PULL_RATE = 10000;   // Base rate is now max
    
    private List<String> researchQueue = new ArrayList<>();  // Up to 3 priorities
    
    // NEW: Treasury Minimum Threshold - Scientist stops pulling when treasury would fall below
    private long treasuryMinimumThreshold = 10000;  // Default 10k gold, Leader/Officer configurable
    
    public int getCurrentPullRate() {
        return BASE_PULL_RATE;  // Simplified: no upgrade levels needed
    }
    
    // Called every in-game hour by settlement tick
    public void processResearchTick(GuildData guild) {
        if (researchQueue.isEmpty()) return;
        if (!hasScientistInSettlement(guild)) return;
        
        String currentResearch = researchQueue.get(0);
        ResearchNode node = ResearchTree.getNode(currentResearch);
        
        int pullAmount = getCurrentPullRate();
        
        // NEW: Respect treasury minimum threshold
        long currentTreasury = guild.getTreasury().getBalance();
        if (currentTreasury - pullAmount < treasuryMinimumThreshold) {
            pullAmount = (int) Math.max(0, currentTreasury - treasuryMinimumThreshold);
            if (pullAmount <= 0) return;  // Treasury at or below minimum
        }
        
        int pulled = pullMaterialsFromBank(guild, node, pullAmount);
        
        if (pulled > 0) {
            node.addProgress(pulled);
            if (node.isComplete()) {
                completeResearch(guild, node);
                researchQueue.remove(0);
            }
        }
    }
}
```

### Treasury Minimum Threshold (NEW FEATURE)
Leaders and Officers can set a minimum treasury balance that the Scientist will not pull below:

| Setting | Default | Range | Who Can Modify |
|---------|---------|-------|----------------|
| `treasuryMinimumThreshold` | 10,000 gold | 0 – ∞ | Leader, Officer |

**UI**: Added to Scientist interaction form:
```
┌─────────────────────────────────────────────────────┐
│  LEAD RESEARCHER - Research Management              │
├─────────────────────────────────────────────────────┤
│  Pull Rate: 10,000 resources/hour                   │
│  Treasury Minimum: [___10,000___] gold              │
│  (Research pauses if treasury falls below this)     │
│                                                      │
│  Research Queue:                                     │
│  1. [Swift Feet] - 45% complete                     │
│  2. [Expanded Vaults] - Queued                      │
│  3. [Empty - Click to add]                          │
│                                                      │
│  [Manage Queue]  [View Research Tree]               │
└─────────────────────────────────────────────────────┘
```

### Research Item Gating Policy — ✅ NO GATING
**Decision**: If a player has the item to deposit, it counts toward research progress. No boss-kill requirements for research nodes.

**Rationale**: 
- Simplifies the system
- Players naturally gate themselves by item availability
- Avoids complex progression tracking
- More sandbox-friendly approach

**Research Cost Structure**:
All research costs are material-based (no XP system). Starting at Tier 1:
| Tier | Base Material Cost | Example |
|------|-------------------|---------|
| 1 | 50,000 resource value | 500 iron bars OR 250 gold bars, etc. |
| 2 | 100,000 resource value | |
| 3 | 200,000 resource value | |
| 4 | 400,000 resource value | |
| 5 | 800,000 resource value | |

**Material Value Calculation**:
Uses item broker value from Necesse's item system:
```java
public int getItemValue(InventoryItem item) {
    return item.item.getBrokerValue() * item.getAmount();
}
```

**Pull Rate Upgrades** (via Research Tree - Logistics Branch):
| Upgrade Level | Pull Rate | Research Node |
|---------------|-----------|---------------|
| 0 (base) | 1,000/hour | - |
| 1-3 | 2,000-4,000/hour | Logistics Tier 2 |
| 4-6 | 5,000-7,000/hour | Logistics Tier 4 |
| 7-9 | 8,000-10,000/hour | Logistics Tier 5 |

**Container Files**
| File | Purpose |
|------|---------|
| `ScientistGuildContainer.java` | Research queue management |
| `ScientistGuildContainerForm.java` | UI for setting priorities |

---

### Farmer → Provisioner

**Guild Task**: Manages a "Guild Cauldron" providing food buffs to crest-wearing members.

**Implementation**
```java
public class GuildCauldronObject extends GameObject {
    // Farmer auto-stocks from guild bank's food items
    // Provides passive buff to all guild members wearing crest
    // Buff is global - works anywhere, not just in settlement
}
```
*lets have the food quality of food increase the bonuse of by a set amount similar to how settlers get happines from getting a better meal. we should be able to specify in the cauldron as a leader what type of diet we are feeding the guild. the cauldrons hunger rate should be based on the amount of guild memebers*
**How It Works**:

1. Place Guild Cauldron in guild settlement
2. Farmer settler automatically feeds it from guild storage
3. While cauldron is "fed" (has food buff active):
   - All crest-wearing members get the buff
   - Buff tied to crest trinket, not location

**Buff Details (DECIDED)**:
Buff is attached to Guild Crest trinket and scales with research:

```java
public class CauldronFoodBuff extends TrinketBuff {
    // Base values (no upgrades)
    private float hungerReduction = 0.0f;      // Starts at 0%
    private float healthRegen = 0.0f;          // Starts at 0/sec
    
    // Max values (fully upgraded via research)
    // Hunger reduction: up to 20%
    // Health regen: up to 5/second
    
    // Upgrades come from Combat/Alchemy research branch
}
```

**Upgrade Path** (in Research Tree):
| Research Node | Bonus |
|---------------|-------|
| Vital Aura (Combat T2) | +5% hunger reduction, +1 hp/sec |
| Guild Potions (Combat T5) | +15% hunger reduction, +4 hp/sec |
| **Total** | **20% hunger reduction, 5 hp/sec** |

**Cauldron Feeding**:
- Farmer checks cauldron every 10 minutes
- Pulls food from guild storage (not bank) 
- Higher quality food = longer buff duration
- Base duration: 30 minutes, max: 2 hours

---

### Blacksmith → Armorer
*lets give him a configurable cost, and all 25% of the proceeds go to the treasury

**Guild Task**: Provides temporary equipment enhancement to guild members.

**Implementation - Equipment Boost System**:
When players interact with a Blacksmith in a guild settlement, they can receive a temporary boost to their equipped items.

**Boost Mechanics**:
```java
public class ArmorerBoostBuff extends Buff {
    // Temporary boost to equipped weapon/tool damage
    // Duration: 30 minutes base, upgradeable to 2 hours
    // Strength: +5% base, upgradeable to +20%
    
    @Override
    public void init() {
        // Boost applies to tool/weapon damage
        this.addModifier(new ModifierValue<>(BuffModifiers.ALL_DAMAGE, 0.05f));
    }
}
```

**Upgrade Path** (in Research Tree - Industry Branch):
| Research Node | Bonus |
|---------------|-------|
| Efficient Tools (Industry T1) | +5% damage, 30 min |
| Master Builder (Industry T5) | +15% damage, +90 min duration |
| **Total** | **+20% damage, 2 hour duration** |

**Cooldown**: 4 hours between boosts per player

**Blacksmith Interaction**:
```
┌─────────────────────────────────────────────────────┐
│  ARMORER - Equipment Enhancement                     │
├─────────────────────────────────────────────────────┤
│  "Let me sharpen your gear, friend!"                │
│                                                      │
│  Current Boost Available:                            │
│  • +15% Tool/Weapon Damage                          │
│  • Duration: 1 hour 30 minutes                      │
│                                                      │
│  [Enhance Equipment]                                 │
│                                                      │
│  Cooldown: Ready! (or "3:42:15 remaining")          │
└─────────────────────────────────────────────────────┘
```

---

### Explorer → Cartographer

**Guild Task**: Syncs discovered map areas across all guild members.

**Implementation**
Map discovery data is merged when triggered, sharing fog-of-war reveals.

**Sync Mechanics (DECIDED)**:
- **Trigger**: Manual - Player talks to Explorer and clicks "Sync Maps"
- **Interval**: No automatic sync (to reduce server load)
- **Cooldown**: 300 seconds (5 minutes) per player

```java
public class CartographerMapSync {
    private static final int SYNC_COOLDOWN_MS = 300000;  // 5 minutes
    
    public void syncMapsForPlayer(PlayerMob player, GuildData guild) {
        // Get all online guild members
        List<ServerClient> guildMembers = getOnlineGuildMembers(guild);
        
        // Collect all discovered regions
        Set<DiscoveredRegion> allDiscovered = new HashSet<>();
        for (ServerClient member : guildMembers) {
            allDiscovered.addAll(member.getDiscoveredMap().getRegions());
        }
        
        // Send merged map data to requesting player
        PacketMapSync packet = new PacketMapSync(allDiscovered);
        player.getServerClient().sendPacket(packet);
    }
}
```

**Explorer Interaction**:
```
┌─────────────────────────────────────────────────────┐
│  CARTOGRAPHER - Map Synchronization                  │
├─────────────────────────────────────────────────────┤
│  "I've been comparing notes with your guildmates!"  │
│                                                      │
│  Guild Members Online: 5                             │
│  Combined Explored Regions: 847                      │
│  Your Explored Regions: 623                          │
│  New Areas Available: 224                            │
│                                                      │
│  [Sync Maps]                                         │
│                                                      │
│  Cooldown: Ready! (or "2:15 remaining")             │
└─────────────────────────────────────────────────────┘
```

---

# Section 5: The Guild Trinket - "The Crest"
*crests should have the icon of the guild as their image*

## 5.1 Concept Summary
The Crest is a trinket item that links a player to their guild's power. Unlike standard trinkets with fixed stats, the Crest dynamically reads the guild's Research Tree and applies all unlocked bonuses.

## 5.2 Trinket Implementation

### Base Class
- **Parent Class**: `TrinketItem` (abstract class in `necesse.inventory.item.trinketItem`)
- **Key Interface**: `Enchantable<EquipmentItemEnchant>` (inherited from TrinketItem)

### Class to Create
| File | Package | Purpose |
|------|---------|---------|
| `GuildCrestItem.java` | `medievalsim.items` | The trinket item class |
| `GuildCrestBuff.java` | `medievalsim.buffs` | Dynamic buff that reads research |

### Key Methods to Override

**From TrinketItem:**
- `getBuffs(InventoryItem item)` - Return array of `TrinketBuff` based on guild research
- `getPreEnchantmentTooltips()` - Show current bonuses from research
- `isAbilityTrinket()` - Return false (no active ability)

### Dynamic Stat Calculation
```
When player equips Crest:
    1. Get player's guild ID from Crest item data
    2. Lookup GuildData from GuildManager
    3. Read ResearchTree unlocked nodes
    4. Sum all stat bonuses from unlocked nodes
    5. Apply as TrinketBuff modifiers
```

## 5.3 Item Data Storage

### GNDItemMap Storage
Each Crest item instance needs to store:
- `guildID` (int) - Which guild this crest belongs to
- `isValid` (boolean) - Whether player is still a member

### Validation Check
On equip and periodically:
1. Check if player is still member of stored guildID
2. If not member: Set `isValid = false`, provide no bonuses
3. Display "Inert" in tooltip when invalid

## 5.4 Available Buff Modifiers

Based on Necesse API research, these `BuffModifiers` are available for Crest bonuses:

### Movement & Speed
| Modifier | Type | Description |
|----------|------|-------------|
| `BuffModifiers.SPEED` | Float | Movement speed multiplier |
| `BuffModifiers.ATTACK_SPEED` | Float | Attack speed multiplier |

### Damage & Combat
| Modifier | Type | Description |
|----------|------|-------------|
| `BuffModifiers.ALL_DAMAGE` | Float | All damage multiplier |
| `BuffModifiers.MELEE_DAMAGE` | Float | Melee damage multiplier |
| `BuffModifiers.RANGED_DAMAGE` | Float | Ranged damage multiplier |
| `BuffModifiers.MAGIC_DAMAGE` | Float | Magic damage multiplier |
| `BuffModifiers.SUMMON_DAMAGE` | Float | Summon damage multiplier |
| `BuffModifiers.CRIT_CHANCE` | Float | Critical hit chance |

### Defense & Health
| Modifier | Type | Description |
|----------|------|-------------|
| `BuffModifiers.ARMOR_FLAT` | Integer | Flat armor bonus |
| `BuffModifiers.MAX_HEALTH` | Integer | Maximum health bonus |
| `BuffModifiers.MAX_MANA` | Integer | Maximum mana bonus |
| `BuffModifiers.HEALTH_REGEN` | Float | Health regeneration rate |
| `BuffModifiers.MANA_REGEN` | Float | Mana regeneration rate |

### Utility & Gathering
| Modifier | Type | Description |
|----------|------|-------------|
| `BuffModifiers.MINING_SPEED` | Float | Mining speed multiplier |
| `BuffModifiers.BUILDING_SPEED` | Float | Building speed multiplier |
| `BuffModifiers.TOOL_TIER` | Integer | Effective tool tier bonus |

## 5.5 Multi-Crest Stacking (CLARIFIED)

### Concept
Players can wear multiple Crests from different guilds simultaneously in their trinket slots. Bonuses from different research trees stack additively, but within the same tree, only the highest tier applies.

### Stacking Rules (FINAL)
| Rule | Decision |
|------|----------|
| **Multiple crests equippable** | ✅ **YES** - Can wear one crest per guild membership |
| **Different research trees** | ✅ **STACK ADDITIVELY** - Industry + Combat + Logistics all apply |
| **Same research tree** | ❌ **HIGHEST ONLY** - If two guilds have Industry researched, take the better one |
| **Within-tree upgrades** | ✅ **STACK ADDITIVELY** - T1 (+10%) + T5 (+10%) = +20% total |
| Maximum Crests | Limited by guild memberships (max 3 guilds = max 3 crests) |

### Stacking Examples

**Example 1 - Different Trees Stack**:
- **Guild A** researched: Industry T5 (+20% damage), Logistics T1 (+5% movement)
- **Guild B** researched: Combat T3 (+10% max health)
- **Wearing both crests**: +20% damage + 5% movement + 10% health ✅ All apply

**Example 2 - Same Tree Takes Highest**:
- **Guild A** researched: Industry T5 (+20% damage)
- **Guild B** researched: Industry T1 (+10% damage)
- **Wearing both crests**: +20% damage only (Guild A's is higher, Guild B's ignored)

**Example 3 - Complex Multi-Guild**:
- **Guild A**: Industry T5 (+20% damage), Logistics T1 (+5% movement)
- **Guild B**: Industry T1 (+10% damage), Combat T5 (+15% max health)
- **Guild C**: Logistics T5 (+15% movement)
- **Wearing all 3 crests**:
  - Industry: 20% (Guild A wins, Guild B ignored)
  - Logistics: 15% (Guild C wins, Guild A ignored)
  - Combat: 15% (Guild B only one with Combat)
  - **Total**: +20% damage, +15% movement, +15% max health

### Implementation
```java
public class GuildCrestStackingHelper {
    
    // Research tree categories for conflict resolution
    public enum ResearchTree {
        LOGISTICS,
        INDUSTRY,
        COMBAT
    }
    
    public static Map<ResearchTree, Map<BuffModifiers<?>, Number>> 
            calculateCombinedBonuses(PlayerMob player) {
        
        // Group bonuses by research tree
        Map<ResearchTree, Map<BuffModifiers<?>, Number>> treeMaxBonuses = new HashMap<>();
        
        // Get all equipped guild crests
        for (InventoryItem trinket : player.getEquipment().getTrinkets()) {
            if (!(trinket.item instanceof GuildCrestItem)) continue;
            
            GuildCrestItem crest = (GuildCrestItem) trinket.item;
            Map<ResearchTree, Map<BuffModifiers<?>, Number>> crestBonuses = 
                crest.getBonusesByTree(trinket);
            
            // For each tree, keep highest bonuses
            for (Map.Entry<ResearchTree, Map<BuffModifiers<?>, Number>> treeEntry : 
                 crestBonuses.entrySet()) {
                
                ResearchTree tree = treeEntry.getKey();
                Map<BuffModifiers<?>, Number> bonuses = treeEntry.getValue();
                
                if (!treeMaxBonuses.containsKey(tree)) {
                    treeMaxBonuses.put(tree, new HashMap<>());
                }
                
                Map<BuffModifiers<?>, Number> currentMax = treeMaxBonuses.get(tree);
                
                // Compare and take highest value for each modifier in this tree
                for (Map.Entry<BuffModifiers<?>, Number> bonus : bonuses.entrySet()) {
                    Number existing = currentMax.get(bonus.getKey());
                    if (existing == null || 
                        bonus.getValue().floatValue() > existing.floatValue()) {
                        currentMax.put(bonus.getKey(), bonus.getValue());
                    }
                }
            }
        }
        
        return treeMaxBonuses;
    }
    
    // Flatten to final bonuses - different trees stack additively
    public static Map<BuffModifiers<?>, Number> getFinalBonuses(PlayerMob player) {
        Map<ResearchTree, Map<BuffModifiers<?>, Number>> treeBonuses = 
            calculateCombinedBonuses(player);
        
        Map<BuffModifiers<?>, Number> finalBonuses = new HashMap<>();
        
        // Add all bonuses from different trees additively
        for (Map<BuffModifiers<?>, Number> bonuses : treeBonuses.values()) {
            for (Map.Entry<BuffModifiers<?>, Number> entry : bonuses.entrySet()) {
                Number existing = finalBonuses.get(entry.getKey());
                if (existing == null) {
                    finalBonuses.put(entry.getKey(), entry.getValue());
                } else {
                    // Additive stacking across different trees
                    float newValue = existing.floatValue() + entry.getValue().floatValue();
                    finalBonuses.put(entry.getKey(), newValue);
                }
            }
        }
        
        return finalBonuses;
    }
}
```

### Why Multiple Crests?
Players benefit significantly from multiple guild memberships:
- **Strategic synergy**: Combine bonuses from different research paths (damage + movement + health)
- **Optimization**: Join guilds that specialize in different trees for maximum benefit
- **Flexible builds**: Combat-focused + Logistics-focused guilds = mobile warrior
- **Access**: Multiple guild banks, teleport destinations, and social networks

## 5.6 Crest Acquisition

### How Players Get Crests (DECIDED)
- **NOT free on join** - Must be purchased
- **Purchase Location**: **Primary**: Guild Flag / Settlement Settings (purchase and placement). **Secondary**: `ManageGuildsForm` (buy a banner item to place later).
- **Cost**: 1,000 gold (from player's inventory, not treasury)
- **One per guild**: Players can only own ONE crest per guild

### Purchase Flow
```
Player at Guild Flag → [Purchase Crest] button
    │
    ├─► Already owns crest for this guild
    │       └─► "You already have a crest for this guild"
    │
    └─► Does not own crest
            └─► Deduct 1,000 gold from player
            └─► Create GuildCrestItem with this guild's ID
            └─► Add to player inventory
```

### Crest Item Creation
```java
public static InventoryItem createCrestForGuild(int guildID) {
    GuildCrestItem crest = (GuildCrestItem) ItemRegistry.getItem("medievalsim:guildcrest");
    InventoryItem item = new InventoryItem(crest, 1);
    
    // Store guild ID in item's GND data
    GNDItemMap gnd = item.getGndData();
    gnd.setInt("guildID", guildID);
    gnd.setBoolean("isValid", true);
    
    return item;
}
```

### Crest Invalidation
When a player leaves or is kicked from a guild:
```java
public void invalidatePlayerCrests(long playerAuth, int guildID) {
    // Find all crest items in player's inventory/equipment
    // Set isValid = false for crests matching guildID
    // Crest remains in inventory but provides no bonuses
    // Tooltip shows "Inert - You are no longer a member of [Guild Name]"
}
```

## 5.7 Required Assets

### Sprites Needed
| Asset | Size | Description |
|-------|------|-------------|
| `items/guildcrest.png` | 32x32 | Inventory icon |
| `items/guildcrest_inert.png` | 32x32 | Inert/invalid version |

### Localization Keys
```
item.guildcrest = Guild Crest
item.guildcrest.tooltip = Channels your guild's research bonuses
item.guildcrest.inert = Inert (Not a guild member)
item.guildcrest.guild = Guild: %s
item.guildcrest.bonuses = Current Bonuses:
```

---

# Section 6: The Research Tree

## 6.1 Overview
The Research Tree is a progression system where guilds spend XP and materials to unlock permanent bonuses. All bonuses apply to guild members via the Crest trinket.

## 6.2 Research Tree Structure

### Three Main Branches
1. **Logistics Branch** - Movement, storage, transport
2. **Industry Branch** - Mining, building, crafting
3. **Combat/Alchemy Branch** - Damage, defense, potions

### Node Structure
Each research node contains:
- `nodeID` (String) - Unique identifier
- `branch` (enum) - LOGISTICS, INDUSTRY, COMBAT
- `tier` (int) - 1-5, determines prerequisites
- `materialCost` (Map<String, Integer>) - Items required
- `bonuses` (List<ModifierValue>) - Stat bonuses granted
- `unlocks` (List<String>) - Special feature unlocks

## 6.3 Logistics Branch Nodes

### Tier 1: Swift Feet
| Property | Value |
|----------|-------|
| Node ID | `logistics_swift_feet` |
| Material Cost | 100,000 gold + Resources |
| Required Resources | 5000 leather + 2000 cloth + 1000 rope |
| Crest Bonus | +5% movement speed (`BuffModifiers.SPEED`, 0.05f) |
| Unlock | None |

**Thematic Resources**: Leather boots, cloth wraps, rope for quick movement gear

---

### Tier 2: Expanded Vaults (Repeatable)
| Property | Value |
|----------|-------|
| Node ID | `logistics_expanded_vaults` |
| Material Cost | 500,000 gold + Resources per unlock (DOUBLES each time) |
| Required Resources | 10000 ironbar + 5000 woodlog + 1000 copperbar (DOUBLES each time) |
| Crest Bonus | None |
| Unlock | +1 Guild Bank tab (50 slots) |
| Repeatable | Yes - up to 3 times (unlocks tabs 2, 3, 4) |

**Cost per Unlock**:
- **Unlock 1 (Tab 2)**: 500,000g + 10,000 ironbar + 5,000 woodlog + 1,000 copperbar
- **Unlock 2 (Tab 3)**: 1,000,000g + 20,000 ironbar + 10,000 woodlog + 2,000 copperbar
- **Unlock 3 (Tab 4)**: 2,000,000g + 40,000 ironbar + 20,000 woodlog + 4,000 copperbar

**Total Investment**: 3,500,000 gold + 70,000 ironbar + 35,000 woodlog + 7,000 copperbar to max out
**Thematic Resources**: Iron for strongboxes, wood for shelving, copper for locks

---

### Tier 3: Fleet Settlers
| Property | Value |
|----------|-------|
| Node ID | `logistics_fleet_settlers` |
| Material Cost | 2,500,000 gold + Resources |
| Required Resources | 700 mysteriousportal + 10000 stone + 5000 rope + 3000 leather |
| Crest Bonus | None (settlement buff) |
| Unlock | Settlers in guild settlements move 10% faster |

**Implementation**: Applied as settlement-level modifier to all HumanMobs in guild zones.
**Thematic Resources**: Mysterious portals for fast travel, stone for paths, rope/leather for gear

---

### Tier 4: Pneumatic Network
| Property | Value |
|----------|-------|
| Node ID | `logistics_pneumatic_network` |
| Material Cost | 10,000,000 gold + Resources |
| Required Resources | 20000 ironbar + 10000 copperbar + 5000 goldbar + 2000 ancientfossilbar |
| Crest Bonus | None |
| Unlock | Guild Chest object |

**Thematic Resources**: Iron/copper for pneumatic tubes, gold for mechanisms, ancient fossil for magic

**Guild Chest Object**:
```java
public class GuildChestObject extends ChestObject {
    // Player places in any settlement
    // Configurable: Select which guild it sends to
    // Every hour: Contents transferred to Guild Bank
    // Requires research to craft
}
```

**Crafting Recipe** (unlocked by this research):
- 1 Large Chest
- 10 Gold Bars
- 5 Ancient Fossil Bars
- 1 Teleportation Scroll

---

### Tier 5: Master Logistics
| Property | Value |
|----------|-------|
| Node ID | `logistics_master` |
| Material Cost | 20,000,000 gold + Resources |
| Required Resources | 50000 steelbar + 10000 goldbar + 5000 diamond + 2000 voidessence |
| Crest Bonus | +10% movement speed (total +15% with T1) |
| Settlement Bonus | +10% settler speed (total +20% with T3) |
| Unlock | Research pull rate upgrades 7-9 (8,000-10,000/hour) |

**Thematic Resources**: Steel for advanced engineering, gold for masterwork mechanisms, diamonds/void essence for magical enhancement

## 6.4 Industry Branch Nodes

### Tier 1: Efficient Tools
| Property | Value |
|----------|-------|
| Node ID | `industry_efficient_tools` |
| Material Cost | 100,000 gold + Resources |
| Required Resources | 5000 ironbar + 2000 steelbar + 1000 copperbar + 500 goldbar |
| Crest Bonus | +10% tool/weapon damage (`BuffModifiers.ALL_DAMAGE`, 0.10f) |
| Unlock | Blacksmith boost available (+5%, 30 min) |

**Note**: Changed from mining speed to ALL_DAMAGE per your request - affects both tools and weapons.
**Thematic Resources**: Iron/steel for tool enhancement, copper/gold for crafting upgrade

---

### Tier 2: Blueprint Ghosts
| Property | Value |
|----------|-------|
| Node ID | `industry_blueprints` |
| Material Cost | 500,000 gold + Resources |
| Required Resources | 10000 paper + 5000 ink + 2000 stone + 1000 goldbar |
| Crest Bonus | None |
| Unlock | Blueprint system |

**Thematic Resources**: Paper/ink for blueprints, stone for measurement, gold for master templates

**Blueprint System**:
```java
public class BlueprintItem extends Item {
    // Save: Select area, saves all object placements
    // Load: Ghost preview shows where objects will be placed
    // Auto-build: If materials in inventory, places objects
    
    private List<BlueprintEntry> entries;  // Object ID + relative position
    private int width, height;
}
```

**Usage**:
1. Craft "Blank Blueprint" (paper + ink)
2. Use to enter selection mode
3. Select rectangle area
4. Blueprint saves all objects in that area
5. Use blueprint to preview and place

---

### Tier 3: Collector's Luck
| Property | Value |
|----------|-------|
| Node ID | `industry_collectors_luck` |
| Material Cost | 2,500,000 gold + Resources |
| Required Resources | 5000 magicshard + 10000 ancientfossilbar + 2000 diamond + 1000 mysteriousportal |
| Crest Bonus | +15% bonus materials from harvesting/mining |
| Unlock | None |

**Thematic Resources**: Magic shards for fortune, ancient fossil for mining luck, diamonds/portals for enchantment

**Implementation**:
```java
// Hook into loot drop calculation
public static int modifyDropAmount(int baseAmount, PlayerMob player) {
    if (hasResearch(player, "industry_collectors_luck")) {
        float bonus = 0.15f;
        int bonusAmount = (int)(baseAmount * bonus);
        // Random chance for the fractional part
        if (GameRandom.globalRandom.nextFloat() < (baseAmount * bonus - bonusAmount)) {
            bonusAmount++;
        }
        return baseAmount + bonusAmount;
    }
    return baseAmount;
}
```

---

### Tier 4: Fortified Construction
| Property | Value |
|----------|-------|
| Node ID | `industry_fortified` |
| Material Cost | 10,000,000 gold + Resources |
| Required Resources | 30000 steelbar + 10000 stone + 5000 ironbar + 2000 ancientfossilbar |
| Crest Bonus | None (zone effect) |
| Unlock | Objects in guild zone have +50% health |

**Implementation**: Modify `GameObject.getMaxHealth()` when in guild zone.
**Thematic Resources**: Steel for reinforcement, stone for structure, iron for foundation, ancient fossil for durability magic

---

### Tier 5: Master Builder
| Property | Value |
|----------|-------|
| Node ID | `industry_master` |
| Material Cost | 20,000,000 gold + Resources |
| Required Resources | 50000 tungstenbar + 20000 goldbar + 10000 diamond + 5000 voidessence |
| Crest Bonus | +10% tool/weapon damage (total +20% with T1) |
| Unlock | Blacksmith boost upgraded (+15%, 90 min) |

**Combined with Tier 1**: 
- Crest gives +20% damage passive
- Blacksmith boost gives additional +15% for 90 minutes
- **Max possible**: +35% damage when boosted

**Thematic Resources**: Tungsten for master tools, gold for superior equipment, diamonds for master blueprints, void essence for ultimate enhancement

## 6.5 Combat/Alchemy Branch Nodes

### Tier 1: Battle Ready
| Property | Value |
|----------|-------|
| Node ID | `combat_battle_ready` |
| Material Cost | 100,000 gold + Resources |
| Required Resources | 5000 ironbar + 2000 steelbar + 1000 leather + 500 copperbar |
| Crest Bonus | +5% all damage (`BuffModifiers.ALL_DAMAGE`, 0.05f) |
| Unlock | None |

**Note**: This stacks with Industry tree damage bonuses (highest wins per crest, but different crests can provide different sources).
**Thematic Resources**: Iron/steel for weapons, leather for armor, copper for shields

---

### Tier 2: Vital Aura
| Property | Value |
|----------|-------|
| Node ID | `combat_vital_aura` |
| Material Cost | 500,000 gold + Resources |
| Required Resources | 10000 healthpotion + 5000 magicshard + 2000 apple + 1000 wheat |
| Crest Bonus | +10 max health (`BuffModifiers.MAX_HEALTH`, 10) |
| Unlock | Cauldron provides +5% hunger reduction, +1 hp/sec |

**Cauldron Buff Update**: When this research is complete, the Guild Cauldron buff improves.
**Thematic Resources**: Health potions for vitality, magic shards for life force, apple/wheat for natural healing

---

### Tier 3: Mana Flow
| Property | Value |
|----------|-------|
| Node ID | `combat_mana_flow` |
| Material Cost | 2,500,000 gold + Resources |
| Required Resources | 10000 manapotion + 5000 magicshard + 2000 diamond + 1000 ancientfossilbar |
| Crest Bonus | +20 max mana (`BuffModifiers.MAX_MANA`, 20) |
| Unlock | +0.5 mana regen in guild zone |

**Thematic Resources**: Mana potions for mana capacity, magic shards for channeling, diamonds for magic power, ancient fossil for knowledge

**Zone Buff Implementation**:
```java
// Applied when player is in guild settlement bounds
public class GuildZoneManaRegenBuff extends Buff {
    @Override
    public void init() {
        this.addModifier(new ModifierValue<>(BuffModifiers.MANA_REGEN, 0.5f));
    }
}
```

---

### Tier 4: Boss Tracker
| Property | Value |
|----------|-------|
| Node ID | `combat_boss_tracker` |
| Material Cost | 10,000,000 gold + Resources |
| Required Resources | 20000 magicshard + 10000 diamond + 5000 mysteriousportal + 2000 voidessence |
| Crest Bonus | None |
| Unlock | Boss spawn locations visible on world map |

**Thematic Resources**: Magic shards for divination, diamonds for scrying, mysterious portals for distant sight, void essence for prophecy

**Implementation**:
```java
// When player opens world map and has this research:
// Show icons for known boss spawn locations
// Icon shows boss type and whether it's currently alive/spawnable

public class BossTrackerMapOverlay {
    public void renderBossLocations(WorldMapForm map, PlayerMob player) {
        if (!hasResearch(player, "combat_boss_tracker")) return;
        
        for (BossSpawnLocation location : BossRegistry.getSpawnLocations()) {
            // Draw boss icon at location
            // Color: Green = spawnable, Red = on cooldown
        }
    }
}
```

---

### Tier 5: Guild Potions
| Property | Value |
|----------|-------|
| Node ID | `combat_guild_potions` |
| Material Cost | 20,000,000 gold + Resources |
| Required Resources | 30000 manapotion + 20000 healthpotion + 10000 magicshard + 5000 voidessence |
| Crest Bonus | +10% potion effectiveness |
| Unlock | Cauldron upgraded (+15% hunger, +4 hp/sec), Guild Potion recipes |

**Thematic Resources**: Mana/health potions for advanced brewing, magic shards for powerful potions, void essence for ultimate formulas

**Guild Potion Recipes** (unlocked):
| Potion | Effect | Duration | Recipe |
|--------|--------|----------|--------|
| Guild Health Potion | Heals 200 HP | Instant | 3 Health Potion + 1 Gold Bar |
| Guild Mana Potion | Restores 150 Mana | Instant | 3 Mana Potion + 1 Gold Bar |
| Guild Speed Potion | +30% Speed | 10 minutes | 3 Speed Potion + 1 Gold Bar |
| Guild Strength Potion | +25% Damage | 10 minutes | 3 Strength Potion + 1 Gold Bar |

**Crafting Location**: Alchemy Table in guild settlement only

---

## 6.6 Research Tree Summary

### Total Research Costs by Branch
| Branch | Nodes | Total Gold Cost | Real Necesse Resources |
|--------|-------|----------------|------------------------|
| Logistics | 5 (+3 repeatable) | 36,100,000g | 9,500+ items |
| Industry | 5 | 33,100,000g | 13,000+ items |
| Combat/Alchemy | 5 | 33,100,000g | 13,500+ items |
| **Grand Total** | **15 (+3)** | **~102,300,000g** | **36,000+ items** |

**Total Resource Value**: ~500M gold equivalent (item cost + gold cost)
**Note**: Expanded Vaults costs doubled per unlock (500k → 1M → 2M), adding 2M to Logistics branch

### Crest Bonus Summary (All Research Complete)
| Modifier | Total Bonus |
|----------|-------------|
| Movement Speed | +15% (Logistics tree) |
| All Damage | +25% (Industry +20%, Combat +5% - different trees stack) |
| Max Health | +10 (Combat tree) |
| Max Mana | +20 (Combat tree) |
| Bonus Materials | +15% (Industry tree) |

**Note**: **Multiple crests CAN be worn**. Different research trees stack additively (Industry + Combat + Logistics). Within the same tree, only the highest tier research from any guild applies. See Section 5.5 for detailed stacking rules.

### Real Necesse Items Used
| Category | Items | Purpose |
|----------|-------|---------|
| **Basic Materials** | ironbar, steelbar, copperbar, goldbar | Core construction |
| **Advanced Metals** | tungstenbar, ancientfossilbar | High-tier upgrades |
| **Natural Resources** | woodlog, stone, leather, rope, cloth | Basic components |
| **Consumables** | healthpotion, manapotion, apple, wheat | Alchemy/sustenance |
| **Magical** | magicshard, diamond, voidessence, mysteriousportal | Enchantment/magic |
| **Crafted** | paper, ink, torch | Blueprints/lighting |

## 6.7 Research UI Implementation

### Container Files
| File | Purpose |
|------|---------|
| `ResearchTreeContainer.java` | Server logic for research |
| `ResearchTreeContainerForm.java` | Client UI with tree visualization |

### UI Layout
```
┌─────────────────────────────────────────────────────────────────┐
│  GUILD RESEARCH TREE                                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  [LOGISTICS]        [INDUSTRY]        [COMBAT/ALCHEMY]          │
│                                                                  │
│  ┌─────────┐       ┌─────────┐       ┌─────────┐               │
│  │Swift    │       │Efficient│       │Battle   │               │
│  │Feet  ●  │       │Tools  ● │       │Ready  ● │   Tier 1      │
│  └────┬────┘       └────┬────┘       └────┬────┘               │
│       │                 │                 │                     │
│  ┌────┴────┐       ┌────┴────┐       ┌────┴────┐               │
│  │Expanded │       │Blueprint│       │Vital    │               │
│  │Vaults ◐ │       │Ghosts ○ │       │Aura   ○ │   Tier 2      │
│  └────┬────┘       └────┬────┘       └────┬────┘               │
│       │                 │                 │                     │
│  ┌────┴────┐       ┌────┴────┐       ┌────┴────┐               │
│  │Fleet    │       │Collect. │       │Mana     │               │
│  │Settlers○│       │Luck   ○ │       │Flow   ○ │   Tier 3      │
│  └────┬────┘       └────┬────┘       └────┬────┘               │
│       │                 │                 │                     │
│  ┌────┴────┐       ┌────┴────┐       ┌────┴────┐               │
│  │Pneumatic│       │Fortified│       │Boss     │               │
│  │Network ○│       │Constr. ○│       │Tracker ○│   Tier 4      │
│  └────┬────┘       └────┬────┘       └────┬────┘               │
│       │                 │                 │                     │
│  ┌────┴────┐       ┌────┴────┐       ┌────┴────┐               │
│  │Master   │       │Master   │       │Guild    │               │
│  │Logistics○       │Builder ○│       │Potions ○│   Tier 5      │
│  └─────────┘       └─────────┘       └─────────┘               │
│                                                                  │
│  ● = Unlocked   ◐ = In Progress   ○ = Locked                   │
│                                                                  │
├─────────────────────────────────────────────────────────────────┤
│  SELECTED: Expanded Vaults (2/3 completed)                      │
│  Cost: 100,000 material value                                   │
│  Effect: +50 Guild Bank slots                                   │
│  Progress: ████████░░ 80% (80,000 / 100,000)                   │
│                                                                  │
│  [Add to Queue]  (or [Already in Queue] if queued)             │
└─────────────────────────────────────────────────────────────────┘
```

## 6.8 Research Donation System (CLARIFIED)

### How Research Works
1. **Guild members donate resources** to research projects
2. **Resources are consumed immediately** when donated (NO STORAGE)
3. **Progress tracks toward unlock threshold**
4. **NO REFUNDS** if player leaves guild - resources are permanently consumed
5. **Multiple members can contribute** to same research simultaneously
6. **Scientist pulls from guild bank automatically** for configured research

### Donation Mechanics
```java
public class ResearchDonationContainer {
    
    public void donateResources(PlayerMob player, InventoryItem donation) {
        // 1. Check if item is needed for current research
        if (!isValidDonation(donation)) {
            player.message("This item is not needed for current research");
            return;
        }
        
        // 2. Remove items from player inventory immediately
        player.getInventory().removeItem(donation);
        
        // 3. Add to research progress (NO STORAGE, IMMEDIATE CONSUMPTION)
        guild.addResearchProgress(currentResearch, donation.getAmount() * getBrokerValue(donation.item));
        
        // 4. Check if research complete
        if (guild.getResearchProgress(currentResearch) >= getResearchCost(currentResearch)) {
            completeResearch(currentResearch);
        }
        
        // 5. Log contribution (for guild records)
        guild.logContribution(player.auth, donation);
    }
}
```

### Key Rules
| Rule | Implementation |
|------|----------------|
| **No cancellation** | Once research starts, cannot be stopped |
| **No refunds** | Resources consumed immediately on donation |
| **Offline research** | Scientist continues work when members offline |
| **Multi-contribution** | Any member can donate to any active research |
| **Progress tracking** | Guild tracks who contributed what (for records only) |

---

# Section 7: Technical Requirements

## 7.1 Core Data Structures

### GuildManager Class
**Location**: `medievalsim.guilds.GuildManager`

**Purpose**: World-level singleton managing all guilds

**Key Fields**:
```java
private Map<Integer, GuildData> guilds;      // guildID -> data
private int nextGuildID;                      // Auto-increment ID
private Map<Long, PlayerGuildData> players;   // playerAuth -> guild memberships
```

**Key Methods**:
- `createGuild(String name, long founderAuth)` - Create new guild
- `disbandGuild(int guildID)` - Delete guild
- `getGuild(int guildID)` - Get guild data
- `getPlayerGuilds(long playerAuth)` - Get all guilds for player
- `addMember(int guildID, long playerAuth, GuildRank rank)`
- `removeMember(int guildID, long playerAuth)`
- `save(SaveData)` / `load(LoadData)` - Persistence

### GuildData Class
**Location**: `medievalsim.guilds.GuildData`

**Purpose**: Data container for a single guild

**Key Fields**:
```java
int guildID;
String name;
String description;
long founderAuth;
long createdTime;
Map<Long, GuildRank> members;        // playerAuth -> rank
int treasuryGold;
Inventory guildBank;                 // Shared storage
Map<String, Boolean> researchNodes;  // Research progress
String currentResearch;
int researchXP;
int totalXP;
boolean isPublic;                    // Can be joined without invite
float taxRate;                       // Grand Exchange tax %
```

### GuildRank Enum
```java
public enum GuildRank {
    LEADER(4),      // Full permissions
    OFFICER(3),     // Most permissions
    MEMBER(2),      // Standard permissions
    RECRUIT(1);     // Limited permissions
    
    private final int level;
}
```

### PlayerGuildData Class
**Location**: `medievalsim.guilds.PlayerGuildData`

**Purpose**: Track player's guild memberships

**Key Fields**:
```java
long playerAuth;
List<Integer> guildIDs;          // All guilds player belongs to
int activeGuildID;               // Currently "active" guild
Map<Integer, GuildRank> ranks;   // Rank in each guild
```

## 7.2 Permission System

### Permission Hook Points
These Necesse methods need patches/overrides to check guild permissions:

| Method | Location | Purpose |
|--------|----------|---------|
| `canInteract()` | GameObject | Object interaction |
| `canPlace()` | Level/Object | Block placement |
| `canBreak()` | Level/Object | Block breaking |
| `canOpen()` | Container objects | Storage access |

### GuildPermissionHelper Class
**Location**: `medievalsim.guilds.GuildPermissionHelper`

**Key Methods**:
```java
static boolean canPlayerInteract(PlayerMob player, int guildID, PermissionType type)
static boolean isPlayerInGuild(long playerAuth, int guildID)
static GuildRank getPlayerRank(long playerAuth, int guildID)
static boolean hasPermission(GuildRank rank, PermissionType type)
```

### PermissionType Enum
```java
public enum PermissionType {
    BUILD,
    BREAK,
    INTERACT,
    ACCESS_STORAGE,
    ACCESS_GUILD_BANK,
    MODIFY_RESEARCH,
    INVITE_MEMBER,
    KICK_MEMBER,
    PROMOTE_DEMOTE,
    SET_TAX_RATE,
    WITHDRAW_TREASURY,
    MANAGE_SETTINGS
}
```

## 7.3 Network Packets

### Packets to Create
| Packet | Direction | Purpose |
|--------|-----------|---------|
| `PacketCreateGuild` | Client→Server | Request guild creation |
| `PacketGuildCreated` | Server→Client | Confirm creation, send ID |
| `PacketJoinGuild` | Client→Server | Request to join guild |
| `PacketLeaveGuild` | Client→Server | Leave a guild |
| `PacketGuildInvite` | Both | Send/receive invitations |
| `PacketGuildKick` | Client→Server | Kick a member |
| `PacketGuildPromote` | Client→Server | Change member rank |
| `PacketGuildUpdate` | Server→Client | Sync guild data changes |
| `PacketResearchStart` | Client→Server | Begin researching node |
| `PacketResearchComplete` | Server→Client | Research finished |
| `PacketGuildBankSync` | Server→Client | Sync bank contents |
| `PacketTreasuryUpdate` | Server→Client | Treasury balance changed |
| `PacketTaxDeposit` | Server→Client | Notify of tax collection |

### Packet Registration
All packets registered in `init()` via:
```java
PacketRegistry.registerPacket("medievalsim:packetname", PacketClass::new);
```

## 7.4 UI Windows

### Forms to Create
| Form | Access Point | Purpose |
|------|--------------|---------|
| `GuildArtisanContainerForm` | Guild Artisan NPC | Main guild management |
| `GuildFlagContainerForm` | Guild Flag object | Guild hub interface |
| `ResearchTreeContainerForm` | Guild Flag / Scientist | Research UI |
| `GuildBankContainerForm` | Guild Flag | Shared storage |
| `RealEstateMapForm` | Guild Artisan (Route B) | Plot browser |
| `GuildSettingsForm` | Guild Flag | Leader settings |
| `GuildMemberListForm` | Guild Flag | Member management |

## 7.5 Persistence

### Save Format
Guild data saved in world folder as `.ghdata` files (Guild Hub Data)

**File Structure**:
```
world/
├── guilds/
│   ├── guilds.ghdata         # Master guild list
│   ├── guild_1.ghdata        # Individual guild data
│   ├── guild_2.ghdata
│   └── players.ghdata        # Player-guild mappings
```

### Save/Load Integration
- Hook into `World.save()` and `World.load()`
- Use Necesse's `SaveData` and `LoadData` classes
- Save on world save, load on world load

### SaveData Methods Used
```java
save.addInt("guildID", guildID);
save.addUnsafeString("name", name);
save.addLong("founderAuth", founderAuth);
save.addSaveData(membersSave);
// etc.
```

### LoadData Methods Used
```java
guildID = load.getInt("guildID");
name = load.getUnsafeString("name");
founderAuth = load.getLong("founderAuth");
LoadData membersLoad = load.getFirstLoadDataByName("members");
// etc.
```

## 7.6 ModConfig Integration

### Config Options (DECIDED VALUES)
```java
public static class Guilds {
    // Core Enable/Disable
    public static boolean enableGuilds = true;
    public static boolean enableRouteBPlots = false;  // Admin plots mode
    
    // Guild Creation
    public static int guildCreationCost = 50000;      // DECIDED: 50,000 gold
    public static GuildUnlockBoss unlockAfterBoss = GuildUnlockBoss.QUEEN_SPIDER; // DECIDED: Configurable
    
    // Multi-Guild Limits
    public static int maxGuildsPerPlayer = 3;         // DECIDED: Adjustable
    public static int maxMembersPerGuild = 50;
    public static boolean canLeadMultipleGuilds = false; // DECIDED: No
    
    // Taxation
    public static float defaultTaxRate = 0.02f;       // DECIDED: 2%
    public static float minTaxRate = 0.001f;          // DECIDED: 0.1%
    public static float maxTaxRate = 0.05f;           // DECIDED: 5%
    
    // Guild Bank
    public static int guildBankStartingSlots = 50;    // DECIDED: 50 slots
    public static int guildBankMaxSlots = 200;        // DECIDED: 200 max
    public static int guildBankSlotsPerTab = 50;      // DECIDED: 50 per tab
    
    // Crest
    public static int crestPurchaseCost = 1000;       // DECIDED: 1,000 gold
    public static int teleportBottleCost = 50;        // DECIDED: 50 gold
    public static boolean teleportBottleReusable = true; // DECIDED: Reusable
    
    // Research System
    public static int scientistMaterialsPerHour = 5000; // DECIDED: 1,000-10,000 range, default 5k
    public static int maxResearchQueue = 3;           // DECIDED: Multiple simultaneous OK
    
    // NPC Cooldowns
    public static int blacksmithBoostCooldownMs = 14400000; // 4 hours
    public static int mapSyncCooldownMs = 300000;     // DECIDED: 300 seconds
    
    // PvP (Future)
    public static boolean allowGuildPvP = false;
}
```

### Boss Unlock Enum
```java
public enum GuildUnlockBoss {
    NONE("None - Always Available"),
    QUEEN_SPIDER("After Queen Spider"),
    EVIL_WIZARD("After Evil's Protector"),
    VOID_WIZARD("After Void Wizard"),
    PIRATE_CAPTAIN("After Pirate Captain"),
    FALLEN_WIZARD("After Fallen Wizard"),
    ANCIENT_VULTURE("After Ancient Vulture"),
    PEST_WARDEN("After Pest Warden"),
    REAPER("After The Reaper"),
    CRYO_QUEEN("After Cryo Queen"),
    SAGE_AND_GRIT("After Sage and Grit"),
    MOTHER_SLIME("After Mother Slime");
    
    private final String displayName;
    GuildUnlockBoss(String displayName) { this.displayName = displayName; }
}
```

### Config Summary Table
| Config Option | Default | Range/Notes |
|---------------|---------|-------------|
| Guild Creation Cost | 50,000g | Adjustable |
| Unlock Boss | Queen Spider | Dropdown enum |
| Max Guilds per Player | 3 | 1-10 |
| Tax Rate | 2% | 0.1% - 5% | *should be decided by guild in guild specific settings*
| Bank Starting Slots | 50 | - |
| Bank Max Slots | 200 | 4 tabs total |
| Crest Cost | 1,000g | Per crest |
| Teleport Bottle Cost | 50g | Reusable |
| Research Per Hour | 5,000 | 1K-10K materials |
| Map Sync Cooldown | 5 min | 300 seconds |

### Timing & Cooldown Summary
| System | Duration/Cooldown | Upgradeable | Notes |
|--------|-------------------|-------------|-------|
| **Guild Teleport Bottle** | 60 second cooldown | ❌ No | Reusable, teleports to guild flag |
| **Map Sync (Cartographer)** | 5 minute cooldown | ❌ No | Manual button, shares map data |
| **Blacksmith Boost** | 30 min → 2 hours duration | ✅ Yes | +5% → +20% damage via research |
| **Blacksmith Cooldown** | 4 hours per player | ❌ No | Time between receiving boosts |
| **Cauldron Food Buff** | 30 min → 2 hours duration | ✅ Yes | Quality food = longer duration |
| **Cauldron Check** | 10 minute interval | ❌ No | Farmer checks/refills automatically |
| **Scientist Research Pull** | Hourly (5k materials/hr) | ✅ Yes | 1K-10K range configurable |
| **Plot Survey Preview** | 60 second duration | ❌ No | Temporary teleport to view plot |

## 7.7 File Summary - All Files to Create

### Core Guild System
| File | Package |
|------|---------|
| `GuildManager.java` | `medievalsim.guilds` |
| `GuildData.java` | `medievalsim.guilds` |
| `GuildRank.java` | `medievalsim.guilds` |
| `PlayerGuildData.java` | `medievalsim.guilds` |
| `GuildPermissionHelper.java` | `medievalsim.guilds` |
| `PermissionType.java` | `medievalsim.guilds` |

### NPCs
| File | Package |
|------|---------|
| `GuildArtisanMob.java` | `medievalsim.mobs` |

### Items
| File | Package |
|------|---------|
| `GuildCrestItem.java` | `medievalsim.items` |
| `GuildCharterItem.java` | `medievalsim.items` |
| `GuildTeleportBottleItem.java` | `medievalsim.items` |
| `PlotSurveyItem.java` | `medievalsim.items` |

### Objects
| File | Package |
|------|---------|
| `GuildFlagObject.java` | `medievalsim.objects` |
| `GuildFlagObjectEntity.java` | `medievalsim.objects` |
| `GuildFlagObjectItem.java` | `medievalsim.objects` |
| `GuildCauldronObject.java` | `medievalsim.objects` |

### Buffs
| File | Package |
|------|---------|
| `GuildCrestBuff.java` | `medievalsim.buffs` |
| `CauldronFoodBuff.java` | `medievalsim.buffs` |
| `DurabilityAuraBuff.java` | `medievalsim.buffs` |

### Containers
| File | Package |
|------|---------|
| `GuildArtisanContainer.java` | `medievalsim.containers` |
| `GuildFlagContainer.java` | `medievalsim.containers` |
| `ResearchTreeContainer.java` | `medievalsim.containers` |
| `GuildBankContainer.java` | `medievalsim.containers` |
| `ResearchDonationContainer.java` | `medievalsim.containers` |

### Container Forms (UI)
| File | Package |
|------|---------|
| `GuildArtisanContainerForm.java` | `medievalsim.forms` |
| `GuildFlagContainerForm.java` | `medievalsim.forms` |
| `ResearchTreeContainerForm.java` | `medievalsim.forms` |
| `GuildBankContainerForm.java` | `medievalsim.forms` |
| `RealEstateMapForm.java` | `medievalsim.forms` |
| `GuildSettingsForm.java` | `medievalsim.forms` |
| `GuildMemberListForm.java` | `medievalsim.forms` |

### Packets (13 total)
| File | Package |
|------|---------|
| `PacketCreateGuild.java` | `medievalsim.packets` |
| `PacketGuildCreated.java` | `medievalsim.packets` |
| `PacketJoinGuild.java` | `medievalsim.packets` |
| `PacketLeaveGuild.java` | `medievalsim.packets` |
| `PacketGuildInvite.java` | `medievalsim.packets` |
| `PacketGuildKick.java` | `medievalsim.packets` |
| `PacketGuildPromote.java` | `medievalsim.packets` |
| `PacketGuildUpdate.java` | `medievalsim.packets` |
| `PacketResearchStart.java` | `medievalsim.packets` |
| `PacketResearchComplete.java` | `medievalsim.packets` |
| `PacketGuildBankSync.java` | `medievalsim.packets` |
| `PacketTreasuryUpdate.java` | `medievalsim.packets` |
| `PacketTaxDeposit.java` | `medievalsim.packets` |

### Route B Specific (Admin Plots)
| File | Package |
|------|---------|
| `GuildPlot.java` | `medievalsim.guilds.plots` |
| `GuildPlotManager.java` | `medievalsim.guilds.plots` |
| `AdminPlotToolItem.java` | `medievalsim.items` |

### Patches/Helpers
| File | Package |
|------|---------|
| `GuildTaxationHelper.java` | `medievalsim.guilds` |
| `GuildZoneHelper.java` | `medievalsim.guilds` |

---

# Section 8: Implementation Order (Recommended)

## Phase 1: Foundation (Core Data)
1. `GuildData.java` - Data structure
2. `GuildRank.java` - Enum
3. `PlayerGuildData.java` - Player tracking
4. `GuildManager.java` - Central manager
5. Persistence (save/load)
6. ModConfig.Guilds options

## Phase 2: Basic UI & NPC
1. `GuildArtisanMob.java` - NPC
2. `GuildArtisanContainer.java` - Container
3. `GuildArtisanContainerForm.java` - UI
4. Basic packets (Create, Join, Leave)
5. `GuildCharterItem.java` - Creation item

## Phase 3: Guild Flag & Ownership
1. `GuildFlagObject.java` - Object
2. `GuildFlagObjectEntity.java` - Entity
3. `GuildFlagObjectItem.java` - Item
4. `GuildPermissionHelper.java` - Permissions
5. Permission patches for canInteract/canBuild

## Phase 4: Crest & Research
1. `GuildCrestItem.java` - Trinket
2. `GuildCrestBuff.java` - Dynamic buff
3. Research node data structure
4. `ResearchTreeContainer.java`
5. `ResearchTreeContainerForm.java`

## Phase 5: Shared Logistics
1. `GuildBankContainer.java` - Shared storage
2. `GuildFlagContainer.java` - Hub interface
3. `GuildTeleportBottleItem.java`
4. Treasury system
5. Taxation system (Grand Exchange hook)

## Phase 6: NPC Roles
1. Scientist - Research donation
2. Farmer - Cauldron buffs
3. Blacksmith - Durability aura
4. Explorer - Map sync
5. Guard - War windows

## Phase 7: Route B (Optional)
1. `GuildPlot.java` - Plot data
2. `GuildPlotManager.java` - Manager
3. `AdminPlotToolItem.java` - Admin tool
4. `RealEstateMapForm.java` - Browse UI
5. `PlotSurveyItem.java` - Preview item

---

# Appendix A: Decision Summary

✅ **ALL DECISIONS COMPLETE** - Ready for implementation

## NPC & Spawning
- [x] 1.2 - Should Guild Artisan perform settler jobs? → **YES** (acts like normal settler)
- [x] 1.2 - Should Guild Artisan be recruitable/dismissable? → **YES**
- [x] 1.2 - One per settlement or multiple? → **ONE per settlement**
- [x] 1.3 - Which boss(es) are "Tier 1"? → **Configurable dropdown (default: Queen Spider)**
- [x] 1.3 - Spawn method: Visitor/Quest/Hybrid? → **HYBRID** (visitor spawns OR quest reward)

## Costs & Pricing
- [x] 1.4 - Guild creation cost (gold) → **50,000 gold** (configurable)
- [x] 1.4 - Teleport bottle cost (gold) → **50 gold**
- [x] 1.4 - Plot survey cost (gold) → **100 gold**
- [x] 1.4 - Are teleport bottles consumable? → **NO** (reusable)
- [x] 2.3 - Plot pricing method → **AUTOMATIC CALCULATION** (based on settlement quality: rooms, workstations, storage, work zones)
- [x] 5.6 - Crest purchase cost (or free on join?) → **1,000 gold**

## Limits & Restrictions
- [x] 1.5 - Maximum guilds per player → **3** (adjustable in config)
- [x] 1.5 - Can player lead multiple guilds? → **NO**
- [x] 2.3 - Can guilds own multiple plots? → **YES**
- [x] 2.3 - Can plots be sold back? Refund %? → **YES, 50% refund**

## Permissions
- [x] All permissions customizable by Leader with rank-based defaults

## Guild Bank
- [x] 3.2 - Starting slots → **50 slots**
- [x] 3.2 - Maximum slots → **200 slots**
- [x] 3.2 - Slots per tab → **50 slots** (4 tabs total)
- [x] 3.2 - Deposit permissions → **All members (RECRUIT+)**
- [x] 3.2 - Withdraw permissions → **MEMBER+ by default, customizable**

## Taxation
- [x] 3.3 - Default tax rate → **2%**
- [x] 3.3 - Min/max tax rates → **0.1% - 5%**
- [x] 3.3 - Who can adjust rate? → **LEADER only**
- [x] 3.3 - Show tax in GE UI? → **YES** (on sale confirmation)

## NPC Roles
- [x] 4.2 - Research system → **Automated - Scientist pulls 1K-10K materials/hour from bank**
- [x] 4.2 - Cauldron buff stats → **Up to 20% hunger reduction, +5 hp/sec**
- [x] 4.2 - Blacksmith boost → **+5-20% damage, temporary buff**
- [x] 4.2 - Map sync → **Manual button, 300 second cooldown**
- [x] 4.2 - War windows → **Future feature**

## Crest & Stacking
- [x] 5.5 - Do identical modifiers stack? → **NO** (highest value only)
- [x] 5.5 - Maximum crests equippable → **Unlimited** (only highest applies)

## Research
- [x] 6.3-6.5 - All node costs → **Material value based (50K-800K per node)**
- [x] 6.7 - Can research multiple nodes? → **YES** (queue up to 3)
- [x] 6.7 - Can skip tiers? → **NO**
- [x] 6.7 - Require previous tier complete? → **YES**

---

## Quick Reference - Key Values

| Parameter | Value |
|-----------|-------|
| Guild Creation Cost | 50,000g |
| Unlock Boss | Configurable (default: Queen Spider) |
| Crest Cost | 1,000g |
| Teleport Bottle | 50g (reusable) |
| Tax Rate | 2% (0.1%-5% range) |
| Bank Slots | 50 start → 200 max |
| Max Guilds/Player | 3 |
| Research Queue | 3 simultaneous |
| Map Sync Cooldown | 5 minutes |
| **Total Research Cost** | **~500M gold equivalent (~100M gold + ~400M in resources)** |
| **Expanded Vaults Total** | **3.5M gold + 70k ironbar + 35k woodlog + 7k copperbar** (with doubling) |
| **Crest Stacking** | **YES** - Different trees stack, same tree takes highest |
| **Research Refunds** | **NO** - resources consumed on donation |

---

## Research Tree Quick Reference (REAL NECESSE ITEMS)

### Logistics Branch (36,100,000g total)
1. Swift Feet (100K) → +5% speed | 500 leather + 200 cloth + 100 rope
2. Expanded Vaults (3.5M total with doubling) → +150 bank slots | Costs double each unlock: 500k→1M→2M
   - Resources: 70k ironbar + 35k woodlog + 7k copperbar (total across all 3 unlocks)
3. Fleet Settlers (2.5M) → +10% settler speed | 700 mysteriousportal + 10k stone + 5k rope + 3k leather
4. Pneumatic Network (10M) → Guild Chest network | 20k ironbar + 10k copperbar + 5k goldbar + 2k ancientfossilbar
5. Master Logistics (20M) → +15% speed total | 50k steelbar + 10k goldbar + 5k diamond + 2k voidessence

### Industry Branch (33,100,000g total)
1. Efficient Tools (100K) → +10% damage | 5000 ironbar + 2000 steelbar + 1000 copperbar + 500 goldbar
2. Blueprint Ghosts (500K) → Blueprint items | 10000 paper + 5000 ink + 2000 stone + 1000 goldbar
3. Collector's Luck (2.5M) → +15% bonus materials | 5000 magicshard + 10k ancientfossilbar + 2000 diamond + 1000 mysteriousportal
4. Fortified Construction (10M) → +50% object health | 30k steelbar + 10k stone + 5000 ironbar + 2000 ancientfossilbar
5. Master Builder (20M) → +20% damage total | 50k tungstenbar + 20k goldbar + 10k diamond + 5000 voidessence

### Combat/Alchemy Branch (33,100,000g total)
1. Battle Ready (100K) → +5% damage | 5000 ironbar + 2000 steelbar + 1000 leather + 500 copperbar
2. Vital Aura (500K) → +10 health, cauldron upgrade | 10k healthpotion + 5000 magicshard + 2000 apple + 1000 wheat
3. Mana Flow (2.5M) → +20 mana, zone mana regen | 10k manapotion + 5000 magicshard + 2000 diamond + 1000 ancientfossilbar
4. Boss Tracker (10M) → Boss map overlay | 20k magicshard + 10k diamond + 5000 mysteriousportal + 2000 voidessence
5. Guild Potions (20M) → Guild potion recipes | 30k manapotion + 20k healthpotion + 10k magicshard + 5000 voidessence

---

*Document Version: 1.0*
*Last Updated: Session Complete*
*Status: ✅ READY FOR IMPLEMENTATION*
