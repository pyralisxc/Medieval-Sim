# Guild UI Paths — Structured Reference

This document is the authoritative UI path reference for all guild-related screens. Each major entry point is split into its flows (initial entry, conditional/edge paths, and submenus) with wireframes and acceptance criteria so developers and QA can implement and validate each screen.

---

Table of Contents
- Guild Artisan (entry flows, quest offer, create, join, manage)  🌟
- Guild Info Panel (Overview, Members, Banners, Settings)
- Manage Guilds (per-player list, buy banner, leave)
- Create Guild (normal and plots-enabled flows)
- Join Guild (browser and invite flows)
- Settlement Settings (Guild Flag — Guild Bank & Settings tabs only)
- Teleport Banners (placement, purchase, management via Guild Info/Manage Guilds)
- Research (Mage)
- Farmer & Shop UI
- Guild Bank
- Crest Editor
- Packets & Navigation Map
- Acceptance Criteria & Developer TODOs
- Wireframes Appendix

---

## Conventions
- Use `GameColor` constants for consistent theming: `FontOptions(...).color(GameColor.<COLOR>)`.
- Buttons use: `FormTextButton(text, x, y, width, FormInputSize.SIZE_<N>, ButtonColor.<COLOR>)`.
- Containers open via `PacketOpenContainer(CONTAINER_ID, content)`; use explicit packet names in Packet list sections.
- Acceptance Criteria are testable; each submenu includes expected packets and server validations.

---

## Guild Artisan (Full Paths)
Entry: Interact with `GuildArtisanMob` → sends `PacketOpenContainer(GuildArtisanContainer.CONTAINER_ID)` to server.

1. Quest Offer Path (player missing unlock requirements)
- Trigger: Player interacts and server-side checks show unlock requirements not met (boss kill, settlement ownership, or other config).
- UI: `QuestOfferContainer` (Quest Offer screen)
  - Wireframe:
  ```
  +---------------------------------------------+
  | Guild Artisan - Unlock Quest                 |
  | ------------------------------------------  |
  | You must complete the quest "Prove Your Worth" to unlock guild features. |
  | Requirements: Defeat: [Configured Boss]; Own a settlement: [Yes/No].        |
  |                                             |
  | [ Accept Quest ]     [ Close ]               |
  +---------------------------------------------+
  ```
  - Packets: `PacketOpenQuestOffer` (server→client), `PacketRequestGuildUnlockQuestAccept` (client→server)
  - Acceptance Criteria:
    - `QuestOfferContainer` opens instead of Create/Join.
    - `Accept Quest` assigns quest and returns success UI message.
    - After quest completion, Artisan opens normal unlocked flows for that player.

2. Unlocked & Not In Guild (Create / Join)
- UI: Main Artisan menu shows `Create Guild` and `Join Guild` entries (plus `View Guild Info` and `Manage Guilds`).
- Create: opens `CreateGuildModal` (See Create Guild section).
- Join: opens `GuildBrowserForm`. Packet: `PacketListGuilds` / `PacketRequestJoinGuild(guildID)`.
- Acceptance Criteria:
  - `Join` visible only if player has not reached `ModConfig.Guilds.maxMemberships`.
  - `Create` validates cost and settlement/quest prerequisites per server rules.

3. In-Guild (Member)
- If player is in one guild: `View Guild Info` directly opens the `GuildInfoPanel` for that guild.
- If in multiple guilds: `View Guild Info` triggers `PacketRequestGuildInfoSelection` and opens `GuildSelectionForm` to pick which guild to view.

4. Manage Guilds
- `Manage Guilds` opens `ManageGuildsForm` which lists the player's guild memberships (See Manage Guilds section for details).
- From Manage Guilds players can leave or buy banners for each guild (server validates funds & permissions).

5. Quick Notes (Artisan UI)
- The Artisan **must not** open Guild Bank or Guild Settings. Add a dev TODO to remove residual `PacketOpenGuildBank` or UI entries in `GuildArtisanContainerForm.java` if present.
- `View Guild Info` becomes `GuildSelectionForm` when multiple memberships exist.

---

## Guild Info Panel (Entry: `GuildInfoPanel` / PacketRequestGuildInfo)
Primary UI: multi-tab form with Overview, Members, Banners, Settings (Settings leader-only).

Wireframe & Subpaths
- Overview tab
  - Crest preview, treasury, description, quick links (View Members, View Banners, View Research Tiers)
  - `View Research Tiers` shows the guild's current research tiers and progress in a compact panel (see Research section).
- Members tab
  - List members, ranks.
  - Promote/Demote/Kick actions are **visible and actionable only to the Leader** (packets: `PacketPromoteMember`, `PacketKickMember`).
  - Officers may view member lists but cannot perform leader-only actions unless explicitly granted via permissions.
  - Server-side permission checks required for each action.
- Banners tab (MANAGEMENT)
  - Lists guild-owned banners: `Name` | `Distance` | [Teleport] [Rename] [Unclaim/Delete]
  - Banners are **player-owned placements**: only the player who placed a banner (or leader) may `Rename` or `Unclaim/Delete` it. Teleport destinations list all guild banners but rename/unclaim is restricted.
  - Placement restriction & ModConfig: `ModConfig.Guilds.maxBannersPerSettlementPerGuild = 1` (default 1). A player may place **at most 1 banner per settlement per guild** they belong to; if a settlement already has a banner for that guild, placement is blocked.
  - `Rename` sends `PacketRenameGuildBanner(guildID,bannerID,newName)`
  - `Unclaim/Delete` sends `PacketUnclaimGuildBanner(bannerID)` (server removes banner entity or clears owner)
  - Placement validation: when placing, server verifies the player is a member of the banner's guild, that the placement is inside a settlement the player owns or is allowed to manage, and that no existing banner for that guild exists in that settlement.
  
  Wireframe (Banners Management - Read-only list for info/teleport):
  ```
  +---------------------------------------------+
  | Banners - <Guild Name>                       |
  | ------------------------------------------  |
  | 1) Main Hall - Placed by: Alice - [Teleport] [Rename] [Unclaim]
  | 2) Mine - Placed by: Bob - [Teleport]         |
  |                                             |
  | [ View Purchase Options ] -> opens Manage Guilds/Buys
  +---------------------------------------------+
  ```
  Acceptance:
  - Rename/unclaim only shown for owners or leader.
  - Purchase options are surfaced by opening `ManageGuildsForm` (no direct buy in Banners tab).

  Wireframe (Banners Management):
  ```
  +---------------------------------------------+
  | Banners - <Guild Name>                       |
  | ------------------------------------------  |
  | 1) Main Hall - Placed by: Alice - [Teleport] [Rename] [Unclaim]
  | 2) Mine - Placed by: Bob - [Teleport]         |
  |                                             |
  |                                             |
  +---------------------------------------------+
  ```
  Acceptance:
  - Rename/unclaim only shown for owners or leader.
  - **Purchasing banners is done via `ManageGuildsForm` / `BuyBannerModal`; no Add/Buy button is present in `GuildInfoPanel` Banners tab.**
  - `BuyBannerModal` enforces `ModConfig.Guilds.maxBannersPerSettlementPerGuild` and shows tooltip with price and remaining purchases allowed.
- Settings tab (Leader-only)
  - Edit Crest (opens CrestDesignerForm), tax rates, disband guild (danger button), permissions matrix
  - **Sell / Plot Controls**: if `ModConfig.Guilds.plotsEnabled` is true, show `Sell Plot` / `Plot Controls` and an option to sell or list owned plots (packets: `PacketSellGuildPlot`).
  - Packets: `PacketGuildCrestUpdated`, `PacketUpdateGuildSettings`

Acceptance Criteria:
- Only leaders see Settings tab; leader is canonical owner for settlement actions.
- Banners' management actions perform server-side validation and send confirmation messages.
---

## Manage Guilds (Entry: `ManageGuildsForm`)
Purpose: Per-player membership management UI (leave guilds, buy banners, quick info)
Wireframe:
```
+---------------------------------------------+
| Manage Guilds                               |
| ------------------------------------------  |
| Guilds you belong to:                       |
| 1) [Guild Name A]  [ Leave ] [ Buy Banner ] |
|    Rank: Officer/Member; Members: 12        |
| 2) [Guild Name B]  [ Leave ] [ Buy Banner ] |
|    Rank: Member; Members: 4                 |
|                                             |
| [ Create Guild ]  [ Join Guild ]   [ Close ]|
+---------------------------------------------+
```
Acceptance Criteria:
- `Leave` shows a confirmation dialog and sends `PacketLeaveGuild(guildID)` on confirm.
- `Buy Banner` opens `BuyBannerModal` for purchase and immediate inventory delivery (packets: `PacketBuyGuildBanner`).
- Purchase limit: players may purchase banners up to their number of eligible owned settlements (or server-configured cap). The `BuyBannerModal` tooltip must display the price and remaining purchases allowed for the player.
- Pagination for long lists; server responds with `ManageGuildsContainer` initial content packet.

---

## Create Guild (Entry: `CreateGuildModal`)
Paths: Normal Create & Plots-enabled variant (Route B)

Create Guild Wireframe (normal):
```
+---------------------------------------------+
| Create Guild                                |
| ------------------------------------------  |
| Name: [___________]                         |
| Description: [___________________________]  |
| Privacy: ( ) Public  ( ) Private             |
| Crest: [ Open Crest Designer ]               |
| Cost: 50,000 Gold                            |
|                                             |
| [ Create ]                 [ Cancel ]        |
+---------------------------------------------+
```
Create Guild (plots-enabled: Route B)
- Flow: Instead of the normal creation form, present a **Guild Plots List** showing admin-provisioned guild zones (each entry shows price and what is included in the purchase, derived from plot pricing). Entries are expandable with a `Survey` (teleport) action.
- Wireframe:
```
+---------------------------------------------+
| Guild Plots Available                        |
| ------------------------------------------  |
| 1) Northhold Keep - Price: 1,000,000         |
|    - Includes: X settlers, Y workstations     |
|    [ Survey ] [ Purchase ]                   |
| 2) Isle Outpost - Price: 750,000             |
|    - Includes: X settlers, Y resources       |
|    [ Survey ] [ Purchase ]                   |
+---------------------------------------------+
```
- Survey flow: `Survey` teleports player to the plot and opens a temporary HUD with `Purchase` and `Leave` options and a visible timer (e.g., 10 minutes). If player chooses `Purchase`, `PacketPurchaseGuildPlot(plotID)` executes; on success player is teleported to the new guild zone and instructed via HUD to place their guild flag. If player leaves the plot area before purchasing they are teleported back to the Guild Artisan (or center of plot if applicable). The HUD persists until paused or flag placement.

Edge cases & server behaviors (Route B HUD):
- Server restart during an active survey session: session state persists server-side; on reconnect the player resumes the survey HUD with remaining time; if player disconnects for longer than `survey.sessionTimeout` (configurable), session cancels and the player returns to the Guild Artisan on next interact.
- Multiple players surveying same plot: allowed; purchases are first-come basis. If one player purchases a plot while others are surveying it, surveying players receive an update and are teleported back or see status `Purchased by <Name>` and may choose another plot.
- Race on purchase: server verifies funds and atomically reserves the plot; on conflict the later purchaser receives a failure packet and is returned to Artisan.
- HUD pause / abandon: `Leave` immediately teleports player back to Artisan; `Pause` suspends timer for short configurable duration (e.g., 5 minutes) but counts against `survey.sessionTimeout` overall.
- Post-purchase flag placement failures (no valid placement): server queues the placement request and prompts player to move to a valid spot; HUD remains until the flag is placed or player cancels and is offered a refund or an admin manual resolution path.

Acceptance Criteria:
  - Guild Plots list is paginated and expandable; `Survey` uses `PacketSurveyPlot(plotID)` and begins a timed session.
  - `Purchase` deducts funds and assigns the plot to the newly created guild, teleporting the player into the zone and locking HUD instructions until flag placement.
  - Prevent leaving the plot zone without consequences: if leaving during survey, teleport back to start; during post-purchase tutorial, leaving teleports to center of guild zone.
  - Special cases handled: server restart resume, multiple surveyors, purchase race, flag-placement fallback.

---

## Join Guild (Entry: `GuildBrowserForm`)
Wireframe:
```
+---------------------------------------------+
| Join a Guild                                 |
| ------------------------------------------   |
| Search: [________]   Filters: [region][size] |
| 1) Guild A - Members: 10 - Join [Request]    |
| 2) Guild B - Members: 5  - Join [Request]    |
|                                              |
| [ Back ]                                     |
+---------------------------------------------+
```
Acceptance Criteria:
- `Request` sends `PacketRequestJoinGuild(guildID)` and shows success/failure feedback.
- Guild entries are expandable to show current research tiers and the full guild description (client-side expandable panel).
- `Request` blocked if it would exceed `ModConfig.Guilds.maxMemberships`.

---

## Settlement Settings (Guild Flag) — exact UX changes
- Entry: open Settlement Settings with C (focuses flagged settlement)
- Tabs to add/ensure:
  - Overview (existing)
  - **Guild Bank** (new tab) — opens `GuildBankContainer` (calls `PacketOpenGuildBank(tabIndex)`) — **visible only to players with bank access**
  - **Guild Settings** (leader-only) — opens `GuildInfoPanel` Settings tab for leader actions
- Visual placement: `Guild Bank` and `Guild Settings` buttons should be centered in the top-row of the settlement bottom-screen button bar (prominent and consistent with settlement UX). Add wireframe:
```
+------------------------------------------------+
| ... [  Other settlement buttons  ] [Guild Bank] [ Guild Settings ] [ ... ]
+------------------------------------------------+
```
- Important: **Do not** add a Teleport Banners management tab here; banner management is in `GuildInfoPanel` and `ManageGuildsForm`.

Acceptance Criteria:
- `Guild Bank` tab visible only on guild-owned settlements.
- `Guild Settings` button/tab visible only to leaders/officers as configured.
- No banner claim or teleport management controls present in Settlement Settings.

---

## Teleport Banners (Placement & Usage)
- Banners are placeable `GuildTeleportBannerObject` with `GuildTeleportBannerObjectEntity` storing `ownerGuildID` and `customName`.
- Purchase flows via `Buy Banner` (ManageGuilds/Create flows) or Special vendor item; placing assigns ownership if player has permission.
- Teleport rules:
  - Teleports only work between banners sharing the same `ownerGuildID` (same guild).
  - Teleport UI: `GuildTeleportContainer` lists destinations limited to the guild's banners and shows distance and availability.
  - Teleport uses `TeleportEvent` and enforces delay/cooldown/sickness; server validates and prevents abuse.

Management:
- Rename, unclaim/delete functionality present in `GuildInfoPanel` and `ManageGuildsForm`.
- No `Claim Banner` quick-flow in Settlement Settings — prevents orphaned/uncoupled banners.

Acceptance Criteria:
- Teleport destination filtering based on `ownerGuildID` server-side.
- Rename/unclaim actions require leader/officer permissions and confirmed server responses.

---

## Research (Mage) UI
- Access: Mage NPC or Artisan shortcut (both open `GuildResearchContainer` for members).
- Wireframe:
```
+---------------------------------------------+
| Guild Research                              |
| ------------------------------------------  |
| Current Tier: 2   Progress: [#####---]      |
| Projects:
| - Project A  [ Start ] [ Contribute ] [ Info ]
| - Project B  [ Locked - requires Project A ]
| [ Donate Bulk ]  [ Refresh ]                 |
+---------------------------------------------+
```
- Acceptance criteria:
  - `GuildResearchContainer` sends initial state on open and handles `Contribute` and `Start` operations with server validation.
  - Research tiers display in `GuildInfoPanel Overview` for quick glance.

---


---

## Farmer / Cauldron UI (not a HumanShop)
- The Farmer role is not a `HumanShop` entry; instead Farmers provide plot/farming support and handle Guild Cauldron operations and daily food quests.
- Wireframe (Farmer Interaction):
```
+---------------------------------------------+
| Farmer - Guild Services                      |
| ------------------------------------------  |
| [ Daily Quest ]  [ Guild Cauldron ] [ Trade Items ] |
|                                             |
| Daily Quest: Deliver 1x [Random Food Quality] -> Reward
| - Accept quest: PacketRequestFarmerQuestAccept
| - Submission: PacketSubmitFarmerQuestItems
+---------------------------------------------+
```
- Guild Cauldron Flow:
  - The farmer manages the cauldron UI where guild members may donate ingredients (Pack donation via `PacketDonateToCauldron`).
  - When brewing completes, a guild-wide buff is applied (default: 10% hunger reduction for 60 minutes) for online guild members.
  - Wireframe (Cauldron):
```
+---------------------------------------------+
| Guild Cauldron                               |
| ------------------------------------------  |
| Ingredients: [Slots]  Progress: [#####---]  |
| [ Add Ingredients ]  [ Start Brew ] [ Cancel ] |
| Status: Idle / Brewing (time left: 00:10:00)  |
+---------------------------------------------+
```
- Acceptance Criteria:
  - Cauldron requires configured ingredient combinations; brewing triggers an event that applies timed buffs to guild members.
  - Farmers can issue daily quests for food items (quality determined by guild leadership settings); quest rewards and buff durations are configurable.

---


## Guild Bank UI (detailed)
- Entry points:
  - `Settlement Settings -> Guild Bank` (primary)
  - `GuildInfoPanel -> Open Bank` (secondary)
- Container: `GuildBankContainer` — server-side shared storage and treasury. Use same packet: `PacketOpenGuildBank(tabIndex)`.
- Acceptance Criteria:
  - Permission checks: deposit by all members; withdraw limited to officer+/configured role.
  - Transaction logging and audit accessible via bank audit tab.

---

## Crest Editor (CrestDesignerForm) — Access: Guild Artisan & GuildInfoPanel Settings
- Location: Opened from `Create Guild` during creation (Artisan flow) and from `GuildInfoPanel -> Settings` (leader-only). The Artisan provides the initial creation path to design a crest at guild creation time.
- Wireframe:
```
+---------------------------------------------+
| Crest Designer                               |
| ------------------------------------------  |
| [Background Shapes]  [Emblem Grid]  [Preview]
| Primary color: [swatches]  Secondary: [swatches]
| Border: [none/simple/ornate]                 |
| [ Save ]           [ Cancel ]                |
+---------------------------------------------+
```
- Features: live preview, color swatches (`GameColor`), emblem selector, border styles, and save which increments crest revision and invalidates cached textures (sizes: 64/32/16).
- Acceptance Criteria:
  - Live preview, server validation, save broadcasts `PacketGuildCrestUpdated` to guild members.

## Guild Bank — Placement & Authority
- Location: accessible from **Settlement Settings (Guild Flag)** and `GuildInfoPanel -> Open Bank` only.
- Wireframe: (embedded in Settlement Settings as the `Guild Bank` tab; see Settlement Settings section for placement wireframe)
- Flag Protection: **Guild Flags may only be destroyed by a world admin or via the `Disband Guild` action in `GuildInfoPanel` (leader-only).**

Edge cases - Flag & Disband behavior:
- If a leader disbands a guild: treasury distribution and asset cleanup occurs atomically; banners are unclaimed and flagged for removal; flags unplaceable and require admin removal or player pickup depending on `ModConfig.Guilds.disbandFlagBehavior` (options: `REMOVE`, `CONVERT_TO_SETTLEMENT_FLAG`, `ALLOW_PICKUP`).
- If a world admin destroys a flag, notify the guild via queued `Notification` with reason; provide a grace window for leader recovery or appeals.
- If disband occurs during active surveys or HUD sessions, ensure HUDs are canceled and players are teleported out to safe locations.
- Acceptance Criteria: disband atomicity tests, admin destruction notification tests, HUD cancellation on disband, config-driven flag handling tested.


---

## Packets & Navigation Map (quick)
- Interact Guild Artisan → `PacketOpenContainer(GuildArtisanContainer.CONTAINER_ID)`
- From Artisan:
  - Quest Offer (pre-unlock) → `PacketOpenQuestOffer`, accept → `PacketRequestGuildUnlockQuestAccept`
  - Create Guild → `PacketCreateGuild(guildName, guildDesc, isPublic)`
  - Join Guild → `PacketListGuilds()` / `PacketRequestJoinGuild(guildID)`
  - View Guild Info → `PacketRequestGuildInfoSelection()` (when multiple) or `PacketRequestGuildInfo(guildID)`
  - Manage Guilds → `PacketOpenManageGuilds()`
  - Buy Banner → `PacketBuyGuildBanner`
- From Settlement Settings (C-key): `PacketOpenGuildBank(tabIndex)`, `PacketRequestGuildInfo(guildID)` for leader settings
- Teleport: Banner interact → `PacketOpenGuildTeleport(guildID)` → server opens `GuildTeleportContainer`

---

## Acceptance Criteria & Developer TODOs
- Developer TODO: Remove `Open Bank` / `Guild Settings` entries from `GuildArtisanContainer` and ensure no calls to `PacketOpenGuildBank` originate from Artisan flows.
- Add server-side validations for Create/Join prerequisites (boss kills, settlement ownership, `ModConfig.Guilds.maxMemberships`).
- Add `ModConfig.Guilds.maxBannersPerSettlementPerGuild` (default 1) and enforce it in purchase/place flows (server-side).
- Implement Notifications: `PacketOpenNotifications`, `PacketClearNotification`, `PacketClearAllGuildNotifications`, server-side storage/TTL for notices.
- Implement packet/handler work for banners: `PacketBuyGuildBanner`, `PacketRenameGuildBanner`, `PacketUnclaimGuildBanner`, `PacketToggleBannerTeleportAccess`.
- Implement Route B APIs: `PacketSurveyPlot(plotID)`, `PacketPurchaseGuildPlot(plotID)`, and temporary HUD orchestration for surveying/purchase sessions.
- Implement Cauldron APIs: `PacketDonateToCauldron`, `PacketStartBrew`, `PacketClaimCauldronReward` and the farming daily quest packets `PacketRequestFarmerQuestAccept`, `PacketSubmitFarmerQuestItems`.
- Ensure `leader` is canonical owner mapping for settlement-level actions and leader-only UI visibility.
- Ensure pages that may overflow (Guild Info, Join Guild, Create Guild (plots), ManageGuilds) support scrolling on client-side forms and perform lazy loading/pagination of lists.
- Add acceptance tests that validate banner placement/purchase limits, plot survey teleport correctness, HUD persistence until flag placement, and that `Guild Flags` may only be destroyed by world admins or `Disband Guild` action.

---

## Per-Form Acceptance Checklists ✅
- Purpose: Provide a compact, testable checklist for QA and developers for each form.

1) `CreateGuildModal`
- Opens from `GuildArtisan` with server-side prereq checks (funds, settlement ownership).
- Sends `PacketCreateGuild(name, crestData, useSettlement)` and receives `PacketGuildCreated` on success.
- Validates name uniqueness and curses/filters server-side; shows field-level errors on failure.
- Acceptance tests: name collision, insufficient funds, invalid characters, successful create and redirect to `GuildInfoPanel`.

2) `JoinGuildForm`
- Lists joinable guilds with pagination and search; `Request to Join` uses `PacketRequestJoin(guildID)`.
- Validates membership caps and per-player cooldowns server-side.
- Acceptance tests: search filtering, pagination, join request acceptance/rejection flows.

3) `ManageGuildsForm`
- Lists guild-owned settlements and banners with `Buy Banner` action opening `BuyBannerModal`.
- Enforces server-side `maxBannersPerSettlementPerGuild` and shows per-guild purchase counts.
- Acceptance tests: purchase flow, limit enforcement, UI reflects inventory updates.

4) `GuildInfoPanel` (including `Banners` and `Settings` tabs)
- Tabs render server-authoritative data; leader-only `Settings` only visible when `isLeader == true`.
- `Banners` list is read-only for buy actions (buy via `ManageGuildsForm` only).
- Acceptance tests: leader visibility, `Teleport` permission checks, banner list counts, proper `PacketGuildCrestUpdated` propagation.

5) `CrestDesignerForm`
- Live-preview updates, saves via `PacketGuildCrestUpdated` with data validated server-side.
- Shows color/icon constraints and enforces asset licensing rules.
- Acceptance tests: preview correctness, save propagation, reverting changes.

6) `GuildTeleportContainer` / `Teleport Selection`
- Lists only same-guild banners and enforces cooldowns/teleport sickness.
- Acceptance tests: destination filtering, disabled out-of-range UI state, server teleport cooldown enforcement.

7) `NotificationsForm`
- Opens via Artisan; uses `PacketOpenNotifications/PacketClearNotification/PacketClearAllGuildNotifications`.
- Tests: inbox TTL expiry, clearing single/all, notification ordering and duplicates.

8) `Route B Survey HUD (Plot Survey)`
- `PacketSurveyPlot` opens temporary session HUD with visible timer and `Purchase`/`Leave`/`Pause` controls.
- `PacketPurchaseGuildPlot` performs atomic reservation and funds deduction then teleports to purchased zone.
- Tests: concurrent purchases (only 1 succeeds), server restart resume, HUD persistence until flag placement.

9) `BuyBannerModal`
- Shows cost, placement options, and processes purchases via `PacketBuyGuildBanner`.
- Tests: purchase & immediate placement, purchase to inventory, insufficient funds, blocked placement fallback.

---

## Wireframes Appendix (consolidated)
- All wireframes referenced above live inline in each section for developer clarity; use them when creating mockups or implementing forms.

---

*Generated by GitHub Copilot (Raptor mini Preview) — this file is now organized into ordered, developer-friendly sections.*
- `Buy Banner` opens `BuyBannerModal` where cost and immediate placement options are shown. Server validates funds and permissions.
- The list is paginated if long.

---

### 6) Buy Banner Modal
```
+---------------------------------------------+
| Purchase Guild Banner                        |
| ------------------------------------------  |
| Banner item: Guild Banner                    |
| Cost: 500 Gold                               |
| Available: In-stock / Purchase & Place       |
|                                             |
| [ Purchase & Place ]   [ Purchase to Inventory ] [ Cancel ] |
+---------------------------------------------+
```
Acceptance:
- Displays cost and permission hints (leader/officer required to place banners for the guild if required).
- `Purchase & Place` ensures a nearby placeable slot or returns item if none.
- Server processes transaction and returns `PacketUpdatePlayerInventory` and `PacketGuildBannerPurchased`.

---

### 7) GuildInfoPanel (multi-tab)
```
+---------------------------------------------+
| Guild Info - <Guild Name>                        |
| [ Overview ] [ Members ] [ Banners ] [ Settings ]|
| ------------------------------------------       |
| Overview Tab:                                    |
| Crest: [preview]   Treasury: 12,345              |
| Short desc: ...                                  |
| [ View Members ]  [ View Banners ]               |
|                                                  |
| Members Tab: (list)                              |
| - Alice (Leader)  - Promote/Demote/Kick          |
| - Bob (Officer)  - Promote/Demote/Kick           |
|                                                  |
| Banners Tab:                                     |
| - Main Hall - [Teleport] [Rename] [Unclaim/Delete] |
| - Mine Entrance - [Teleport] [Rename] [Unclaim/Delete] |
|                                             |
| Settings Tab (Leader-only):                   |
| - Edit Crest (opens CrestDesignerForm)
| - Tax Rate: [ 5% ] [ Save ]                   |
| - Disband Guild (danger button)               |
+---------------------------------------------+
```
Acceptance:
- `Overview` shows crest, treasury (read-only), and short info.
- `Members` shows full list with leader-only actions restricted server-side.
- `Banners` lists guild-owned banners; `Teleport` only appears for players with `USE_TELEPORT_STAND` permission and uses server validation to limit destinations to same guild.
- `Settings` is visible only to leaders (owner semantics); `Edit Crest` opens crest editor and on save sends `PacketGuildCrestUpdated`.

---

### 8) Teleport Selection (opened by interacting with a Banner)
```
+---------------------------------------------+
| Guild Teleport Network - <Your Guild>        |
| ------------------------------------------  |
| Destination:                                  |
| 1) Main Hall - 0m  [Teleport]                 |
| 2) Mine Entrance - 800m  [Teleport]           |
| 3) Outpost - 1200m  [Teleport Disabled: Out of range] |
|                                             |
| Note: Teleports restricted to banners belonging to same guild |
| [ Close ]                                   |
+---------------------------------------------+
```
Acceptance:
- Only lists banners whose `ownerGuildID` == player's guildID.
- `Teleport` triggers server-side `TeleportEvent` and enforces delay/cooldown/sickness rules.

---

### 9) Settlement Settings (C-key) — Guild Tabs (what changes visually)
```
+------------------------------------------------+
| Settlement Settings - Tabs: [Overview] [Guild Bank] [Guild Settings] |
| -------------------------------------------------------------- |
| Owner: <Player>      Guild: <GuildName>                        |
| Overview panel content...                                       |
| [ Guild Bank ] (opens GuildBankContainer)                       |
|   - Tabs: Shared Storage / Withdraw Rules / Audit Log           |
| [ Guild Settings ] - leader-only (opens GuildInfoPanel Settings)|
+------------------------------------------------+
```
Acceptance:
- `Guild Bank` tab is visible for guild-owned settlements and sends `PacketOpenGuildBank(tabIndex)` when interacted with.
- `Guild Settings` is visible only to leaders/officers and opens the leader-only settings panel (mirrors `GuildInfoPanel` Settings).
- No claim/teleport banner controls are present in Settlement Settings.

---

### 10) CrestDesignerForm
```
+---------------------------------------------+
| Crest Designer                               |
| ------------------------------------------  |
| [Shape grid]  [Preview pane]  [Emblem grid] |
| Primary color: [swatches]  Secondary: [swatches]|
| Border style: [none/simple/ornate]          |
| Preview: [live image 64px]                   |
|                                             |
| [ Save ]           [ Cancel ]                |
+---------------------------------------------+
```
Acceptance:
- Live preview updates as the user changes selections.
- Save validates choice; server stores design, invalidates old cached crests, and sends `PacketGuildCrestUpdated` to guild members.

---

### 11) Guild Browser (Join Flow)
```
+---------------------------------------------+
| Join a Guild                                 |
| ------------------------------------------  |
| Search: [________]   Filters: [region][size] |
| 1) Guild A - Members: 10 - Join [Request]    |
| 2) Guild B - Members: 5  - Join [Request]    |
|                                             |
| [ Back ]                                     |
+---------------------------------------------+
```
Acceptance:
- `Request` sends `PacketRequestJoinGuild(guildID)` and shows success/failure feedback.
- Player cannot request join if they would exceed `ModConfig.Guilds.maxMemberships`.

---

## UI Style Constants (source-anchored)
- Title size: `FontOptions(20)` (used in Artisan, Teleport headers)
- Section headers: `FontOptions(16)`
- Body text: `FontOptions(12)`
- Small info/hints: `FontOptions(10)` or `FontOptions(12).color(GameColor.GRAY)`
- Button sizes: `FormInputSize.SIZE_32` for primary action buttons, `SIZE_24` for dialog actions
- Button colors: `ButtonColor.BASE` for default, `ButtonColor.RED` for danger
- Panel dimensions (examples): Artisan 350×300, Teleport 400×350, Info Panel 450×400

---

## Recommendations & Notes
- This spec is actionable and will help by clarifying the implementation checklist, preventing duplicate/forgotten UI work, and enabling consistent styling.
- **Disabled features:** The `Guild Cauldron` and brewing UI are currently out-of-scope and should be marked disabled until a brewing design and requirements are provided.
- Keep this doc as the single source-of-truth and update it when new screens are added or changes are made.
- If desired, I can generate small mock images (wireframes) for each screen from this spec.

---

## Next actions I can take (choose one)
- Generate wireframe PNGs for each screen (visual mockups)
- Create per-form unit tests / open container flow tests
- Add missing UI screens (e.g., Cauldron brewing stub UI)

---

*Generated by GitHub Copilot (Raptor mini Preview) — let me know if you want this saved as a living doc in the repo (I already added it to `docs/GUILD_UI_PATHS.md`).*