# Guild System - Complete Implementation Plan
**Status**: *Authoritative technical implementation plan — use `docs/guilds_worksheet.md` for decisions and `docs/guilds_implementation_plan.md` for coding.*  
**Last Updated**: 2025-12-20

---

## 📊 VISUAL ASSETS REQUIRED

### Total Asset Count: **48 Images**

#### **NPC Sprites** (10 images)
| File Path | Size | Type | Purpose |
|-----------|------|------|---------|
| `mobs/guildartisan_idle.png` | 32x32 | Sprite | NPC idle animation frame 1 |
| `mobs/guildartisan_idle2.png` | 32x32 | Sprite | NPC idle animation frame 2 |
| `mobs/guildartisan_walk1.png` | 32x32 | Sprite | NPC walk cycle frame 1 |
| `mobs/guildartisan_walk2.png` | 32x32 | Sprite | NPC walk cycle frame 2 |
| `mobs/guildartisan_walk3.png` | 32x32 | Sprite | NPC walk cycle frame 3 |
| `mobs/guildartisan_walk4.png` | 32x32 | Sprite | NPC walk cycle frame 4 |
| `mobs/guildartisan_work1.png` | 32x32 | Sprite | Working animation frame 1 |
| `mobs/guildartisan_work2.png` | 32x32 | Sprite | Working animation frame 2 |
| `mobs/guildartisan_portrait.png` | 64x64 | Icon | UI/dialogue portrait |
| `mobs/guildartisan_icon.png` | 16x16 | Icon | Minimap/small icon |

**Animation Notes**: 
- Idle: 2 frames, shuffling papers/examining seals
- Walk: 4 frames, standard HumanMob cycle
- Work: 2 frames, administrative desk work

---

#### **Item Sprites** (5 images)
| File Path | Size | Type | Purpose |
|-----------|------|------|---------|
| `items/guildcrest.png` | 32x32 | Item | Guild crest trinket (inventory) |
| `items/guildcrest_inert.png` | 32x32 | Item | Inactive crest (left guild) |
| `items/guildteleportstand.png` | 32x32 | Item | Teleport stand item |
| `items/plotsurvey.png` | 32x32 | Item | Plot preview tool |

> **Note**: Guild Charter item removed — guilds created directly via Guild Artisan.

---

#### **Object Sprites** (8 images)
| File Path | Size | Type | Purpose |
|-----------|------|------|---------|
| `objects/guildflag.png` | 32x32 | Object | Guild flag (replaces settlement flag) |
| `objects/guildflag_shadow.png` | 32x32 | Shadow | Flag shadow layer |
| `objects/guildteleportstand.png` | 32x32 | Object | Placed teleport stand |
| `objects/guildteleportstand_glow.png` | 32x32 | Effect | Glowing effect layer |
| `objects/guildchest.png` | 48x48 | Object | Pneumatic chest object |
| `objects/guildchest_open.png` | 48x48 | Object | Chest open state |
| `objects/guildcauldron.png` | 48x48 | Object | Guild cauldron |
| `objects/guildcauldron_active.png` | 48x48 | Effect | Cauldron with active buff |

---

#### **Guild Crest Components** (20+ images)
| Category | Count | Size | Files |
|----------|-------|------|-------|
| **Background Shapes** | 5 | 64x64 | `ui/crest_bg_shield.png`, `_circle.png`, `_banner.png`, `_diamond.png`, `_square.png` |
| **Emblems** | 20 | 32x32 | `ui/emblem_sword.png`, `_pickaxe.png`, `_tree.png`, `_crown.png`, `_star.png`, `_hammer.png`, `_anvil.png`, `_coin.png`, `_shield.png`, `_axe.png`, `_wheat.png`, `_gem.png`, `_castle.png`, `_dragon.png`, `_lion.png`, `_eagle.png`, `_wolf.png`, `_bear.png`, `_skull.png`, `_rose.png` |
| **Border Styles** | 4 | 64x64 | `ui/crest_border_none.png`, `_simple.png`, `_ornate.png`, `_royal.png` |

**Total Crest Components**: 29 images

---

## Detailed Additions (moved from temporary spec)

### Plot Pricing (Detailed)
- **Leader-Adjustable Weights**: Leaders may modify per-guild pricing weights via `GuildSettings` (only leader permission). Fields:
  - `basePlotPrice:int` (default 100000)
  - `perSettlerWeight:int` (default 1000)
  - `perQualityRoomWeight:int` (default 5000)
  - `perStorageSlotValue:int` (default 500)
  - `workstationBaseValue:int` (default 2000)
  - `workstationTierMultipliers:float[]` (default [1.0, 1.5, 2.5])
  - `zoneBonuses:map` (husbandry=50000, forestry=30000, farming=40000)
  - `locationMultiplier:map` (admin-configurable)

**Quality scoring**:
- Each room has a `roomQualityScore` (1..5) based on furniture, decoration, object counts and space per settler.
- Workstation tiers identify advanced equipment (Tier1, Tier2, Tier3).

**Formula**:
```
price = base + settlers*perSettlerWeight + sum_over_rooms(roomBase * roomQualityScore * perQualityRoomWeightFactor) + storageSlots*perStorageSlotValue + sum_workstations(workstationBaseValue * tierMultiplier) + zoneBonuses
price *= locationMultiplier
price = roundToNearest(price, 100)
```

**API & Validation**:
- `getPlotPricingWeights()` - public read; `updatePlotPricingWeights(GuildSettings)` - leader-only write.
- Values validated server-side; changes recorded in guild audit log.

---

### Guild Charter — ❌ REMOVED
> **Decision**: Guild Charter is not needed. Guild creation happens directly through Guild Artisan interaction.
> - Remove `guildcharter.png` from assets
> - Remove `GuildCharter.java` item class
> - Remove `requireGuildCharter` from ModConfig
> - Simplify creation flow: Pay gold → Create guild (no charter item)

---

### Crest Rendering & UID Scheme — ✅ FINALIZED
- Crest design composed of background shape, background color, emblem sprite (icon and color), border style and revision number.
- **Sizes**: 64px, 32px, 16px (all three generated)
- **Generation Strategy**: Lazy + Cache (Necesse standard)
- **Deterministic UID**: `crestUID = CRC32(guildID + '|' + designJSON + '|' + revision)` (32-bit int). Stored in `GuildData` and `GuildCrestItem`.
- **Texture generation**: server composes layers into PNGs and caches under `mod_cache/crests/crest_{crestUID}_{size}.png`
- **Invalidation**: on design change (revision++), delete old cache and push `PacketGuildCrestUpdated(guildID, crestUID)` to clients.
- **Persistence**: Cached crests persist across mod updates (only invalidated on design change)
- Flags/stands overlay the 64px crest texture at render time.

---

### Visual Assets Mapping (in-project)
- I copied initial candidate assets into the project under `src/main/resources` for review. Current mapping:
  - `src/main/resources/objects/guild_flag.png` — base flag sprite (overlay crest)
  - `src/main/resources/objects/guild_teleport_stand.png` — teleport stand base
  - `src/main/resources/objects/guild_portal_effect.png` — portal effect (optional)
  - `src/main/resources/objects/guild_chest.png` — guild chest
  - `src/main/resources/objects/guild_cauldron.png` — guild cauldron
  - `src/main/resources/items/guildcrest_shape_shield.png` — crest shape (background)
  - `src/main/resources/items/guild_emblem_sword.png` — emblem sword
  - `src/main/resources/items/guild_emblem_tree.png` — emblem tree
  - `src/main/resources/items/guild_emblem_wheat.png` — emblem wheat
  - `src/main/resources/items/guild_emblem_diamond.png` — emblem diamond
  - `src/main/resources/items/guild_emblem_hammer.png` — emblem hammer
  - `src/main/resources/items/guild_emblem_crown.png` — emblem crown
  - `src/main/resources/mobs/guildartisan_icon.png` — guild artisan icon
  - `src/main/resources/mobs/blacksmith_icon.png` — blacksmith icon

**Assets mapping produced**: See `docs/assets.csv` for in-project paths, original source paths, usage notes and recommended sizes.

---

### Notes
- I have merged these technical sections into this implementation plan so that **this file is authoritative** for coding. Please use `docs/guilds_worksheet.md` for decisions and completed checklists.

---

#### **UI Elements** (5 images)
| File Path | Size | Type | Purpose |
|-----------|------|------|---------|
| `ui/guild_icon.png` | 16x16 | Icon | Guild menu icon |
| `ui/research_tree_bg.png` | 512x512 | UI | Research tree background |
| `ui/research_node_locked.png` | 48x48 | UI | Locked research node |
| `ui/research_node_unlocked.png` | 48x48 | UI | Unlocked research node |
| `ui/research_node_progress.png` | 48x48 | UI | Research in progress |

---

## 🔧 MEDIEVAL SIM INTEGRATION OPPORTUNITIES

### **1. Reuse Existing Zone System** ✅ **IMPLEMENTED**
**Current System**: `AdminZone.java` base class with `ProtectedZone`, `PvPZone`, and now `GuildZone` implementations

**Implementation Status**:
- ✅ `ZoneType` enum created: `PROTECTED(0), PVP(1), GUILD(2)`
- ✅ `GuildZone` extends `AdminZone` with guild-specific properties
- ✅ `AdminZonesLevelData` supports guild zone CRUD
- ✅ `ZoneRepository` provides `copyGuildZones()` and `forEachGuildZone()`
- ✅ `AdminToolsHudForm` has Guild Zones tab in Zone Tools menu
- ✅ `ZoneVisualizationHud` supports guild zone overlay display
- ✅ `CreateOrExpandZoneTool` supports `ZoneType.GUILD`
- ✅ `PacketDeleteZone` and `PacketRenameZone` use `ZoneType` enum

**Code Location**:
```java
// zones/domain/GuildZone.java
public class GuildZone extends AdminZone {
    private int guildID;
    private String guildName;  // Cached for display
    private boolean allowNonMembers;
    private boolean isPurchasable;
    private long purchaseCost;
    private Set<Integer> allyGuildIDs;
    
    @Override
    public String getTypeID() {
        return "guild";
    }
    
    // Permission checks delegate to GuildManager
    public boolean canPlayerInteract(ServerClient client, PermissionType permission) {
        // Implementation uses guild permission system
    }
}
```

**Benefits Achieved**:
- ✅ Reuses existing `Zoning` rectangle management
- ✅ Integrates with `ZoneVisualizationHud` (overlay display)
- ✅ Uses existing `CreateOrExpandZoneTool` for admin zone creation
- ✅ No need to recreate zone rendering/editing tools

**Files Modified**:
- `zones/domain/ZoneType.java` - New enum with PROTECTED, PVP, GUILD
- `zones/domain/GuildZone.java` - New guild zone implementation
- `zones/service/ZoneRepository.java` - Guild zone storage and retrieval
- `zones/ui/ZoneVisualizationHud.java` - Guild zone rendering support
- `zones/ui/CreateOrExpandZoneTool.java` - ZoneType support
- `packets/PacketDeleteZone.java` - ZoneType enum integration
- `packets/PacketRenameZone.java` - ZoneType enum integration
- `ui/AdminToolsHudForm.java` - Guild Zones tab in Admin HUD

---

### **2. Leverage Existing Settlement Protection**
**Current System**: `SettlementProtectionData.java` - granular permission system

**Integration Plan**:
```java
// REUSE: GuildPermissions mirrors SettlementProtectionData
public class GuildPermissions {
    // Same permission flags as SettlementProtectionData
    private boolean canBreak;
    private boolean canPlace;
    private boolean canInteractDoors;
    private boolean canInteractContainers;
    // ... etc
    
    // Add rank-based logic
    private Map<PermissionType, GuildRank> minimumRanks = new HashMap<>();
}
```

**Benefits**:
- ✅ Consistent permission model across settlements and guilds
- ✅ Reuse existing protection tracker hooks
- ✅ Same UI patterns for permission management
- ✅ No duplication of permission logic

---

### **3. Hook into Grand Exchange for Taxation**
**Current System**: `TradeTransaction.java` - already calculates tax

**Integration Plan**:
```java
// In TradeTransaction.commit():
int tax = ModConfig.GrandExchange.getSalesTax(totalCoins);

// ADD: Guild tax split
if (isSellerInGuild(sellOffer.getPlayerAuth())) {
    GuildData guild = getPlayerActiveGuild(sellOffer.getPlayerAuth());
    int guildTax = (int)(totalCoins * guild.getTaxRate());
    tax += guildTax;
    
    // Deposit to guild treasury
    guild.getTreasury().deposit(guildTax);
    
    // Log transaction
    guild.logTaxRevenue(sellOffer.getPlayerAuth(), guildTax, timestamp);
}

int sellerProceeds = totalCoins - tax;
```

**Benefits**:
- ✅ Minimal code changes (single insertion point)
- ✅ Uses existing tax calculation infrastructure
- ✅ Automatic transaction logging
- ✅ No performance impact (single lookup)

**Files to Modify**:
- `TradeTransaction.java` - Add guild tax calculation
- `ModConfig.java` - Add guild tax config options

---

### **4. Integrate with Command Center**
**Current System**: Command Center provides centralized UI hub

**Integration Plan**:
- Add "Guild" tab to Command Center
- Reuse existing tab navigation system
- Guild research/bank accessible from Command Center
- Consistent UI patterns with existing features

**Benefits**:
- ✅ Central access point for all features
- ✅ Consistent user experience
- ✅ No duplicate UI frameworks
- ✅ Easy discoverability

---

### **5. Use Existing Banking System**
**Current System**: `PlayerBank.java` - manages player coin storage

**Integration Plan**:
```java
// NEW: Guild treasury extends same patterns
public class GuildTreasury {
    private int coins;
    private List<TreasuryTransaction> transactionLog;
    
    // Mirror PlayerBank methods
    public boolean addCoins(int amount) { /* ... */ }
    public boolean removeCoins(int amount) { /* ... */ }
    public int getCoins() { return coins; }
    
    // Additional guild-specific tracking
    public void logTaxDeposit(long playerAuth, int amount, long timestamp) {
        transactionLog.add(new TreasuryTransaction(
            TreasuryTransaction.Type.TAX,
            playerAuth, amount, timestamp
        ));
    }
}
```

**Benefits**:
- ✅ Consistent coin management patterns
- ✅ Reuse existing validation logic
- ✅ Compatible with Grand Exchange systems
- ✅ Familiar code structure

---

## 🎯 NECESSE API LEVERAGE POINTS

### **1. Settlement System Integration**
**Necesse System**: `ServerSettlementData`, `SettlementFlagObject`, `SettlerMob`

**Better Approach**:
```java
// INSTEAD OF: Creating separate guild territory
// USE: Extend settlement ownership model

public class GuildFlagObject extends SettlementFlagObject {
    @Override
    public void setupPlacement(Level level, int x, int y) {
        super.setupPlacement(level, x, y);
        // Convert settlement to guild on placement
        ServerSettlementData settlement = getSettlement(level, x, y);
        if (settlement != null) {
            GuildManager.convertSettlementToGuild(settlement, placerAuth);
        }
    }
}
```

**Benefits**:
- ✅ Reuses all settlement logic (bounds, NPCs, happiness)
- ✅ No duplicate territory management
- ✅ Compatible with existing settler AI
- ✅ Works with settlement overlay/UI

**Files to Understand**:
- `necesse.entity.mobs.friendly.human.HumanMob` - NPC base
- `necesse.level.maps.levelData.settlementData.ServerSettlementData` - Settlement management
- `necesse.inventory.item.placeableItem.objectItem.SettlementFlagObjectItem` - Flag items

---

### **2. Visitor Spawning System**
**Necesse System**: `SettlementVisitorSpawner`, `VisitorSpawnerData`

**Usage**:
```java
// Already planned correctly - good!
public class GuildArtisanVisitorSpawner extends SettlementVisitorSpawner {
    @Override
    public boolean canSpawnInSettlement(ServerSettlementData settlement) {
        // Check boss defeat, no existing guild artisan
        return PlayerQuestManager.hasDefeated(owner, unlockBoss) 
            && !settlement.hasGuildArtisan();
    }
}
```

**Additional Consideration**:
- Add to `SettlementVisitorManager` registry
- Set spawn priority (higher = spawns earlier)
- Configure spawn cooldown

---

### **3. Container System**
**Necesse System**: `Container`, `ContainerForm`, `ContainerRegistry`

**Optimization**:
```java
// Guild Bank should use SharedInventory pattern
public class GuildBankContainer extends Container {
    // IMPORTANT: Mark as shared inventory
    @Override
    public boolean isLocalSave() {
        return false; // Server-side only
    }
    
    // Sync to all guild members
    @Override
    public void onInventoryUpdated(Inventory inventory) {
        super.onInventoryUpdated(inventory);
        GuildManager.broadcastBankUpdate(guildID);
    }
}
```

**Benefits**:
- ✅ Proper client-server sync
- ✅ Multiple players can access simultaneously
- ✅ No item duplication bugs
- ✅ Automatic conflict resolution

---

### **4. Buff System Integration**
**Necesse System**: `Buff`, `BuffModifiers`, `ActiveBuff`

**Implementation**:
```java
// Guild Crest should be dynamic buff
public class GuildCrestBuff extends ActiveBuff {
    private int guildID;
    
    @Override
    public void init(ActiveBuff parent, BuffEventSubscriber eventSubscriber) {
        GuildData guild = GuildManager.getGuild(guildID);
        
        // Apply all research bonuses
        for (ResearchNode node : guild.getCompletedResearch()) {
            for (BuffModifier modifier : node.getModifiers()) {
                this.addModifier(modifier);
            }
        }
    }
}
```

**Key Point**: Buffs recalculate on equip/unequip, so research updates apply automatically

---

### **5. Quest System for Unlock**
**Necesse System**: `QuestManager`, `Quest`, `QuestTask`

**Implementation**:
```java
public class GuildUnlockQuest extends Quest {
    @Override
    public void initializeTasks() {
        addTask(new DefeatBossTask(ConfiguredBoss.get()));
    }
    
    @Override
    public void giveReward(ServerClient client, boolean isCompleted) {
        if (isCompleted) {
            // Add Guild Artisan to visitor queue
            SettlementVisitorManager.queueVisitor(
                client.getSettlement(), 
                new GuildArtisanVisitorSpawner()
            );
        }
    }
}
```

---

## 🚨 CRITICAL GAPS & EDGE CASES

### **1. Settlement Ownership Transfer (EDGE CASE HANDLING)**
**Gap**: What if settlement owner is removed from world (kicked, banned, etc)?

**Solution**:
```java
// Hook into settlement ownership change
@Patch(target = "necesse.level.maps.levelData.settlementData.ServerSettlementData", 
       method = "setOwner")
public static void onSettlementOwnerChange(ServerSettlementData settlement, 
                                          long newOwner, long oldOwner) {
    // If settlement is a guild
    if (settlement.hasGuild()) {
        GuildData guild = GuildManager.getGuildBySettlement(settlement);
        
        // EDGE CASE: Old owner removed from world (kicked/banned)
        if (newOwner == -1 || newOwner == 0) {
            // Auto-transfer to top contributor
            long topContributor = guild.getTopContributingMember();
            if (topContributor != -1) {
                settlement.setOwner(topContributor);
                guild.setLeader(topContributor);
                
                // Notify all online members
                guild.broadcastMessage(
                    "Leadership automatically transferred to " + 
                    getPlayerName(topContributor) + 
                    " (top contributor)"
                );
            } else {
                // No suitable successor - disband guild
                disbandGuild(guild.getID());
            }
            return;
        }
        
        // NORMAL CASE: Check if new owner is in guild
        if (!guild.hasMember(newOwner)) {
            // PREVENT: Cannot transfer to non-member
            throw new IllegalStateException(
                "Cannot transfer guild settlement to non-member"
            );
        }
        
        // Transfer guild leadership
        guild.setLeader(newOwner);
    }
}
```

---

### **2. Guild Disbanding**
**Gap**: What happens to guild bank, treasury, and research on disband?

**Solution**:
```java
public void disbandGuild(int guildID) {
    GuildData guild = GuildManager.getGuild(guildID);
    
    // 1. Drop all guild bank items on ground at flag location
    Point flagPos = guild.getFlagPosition();
    for (InventoryItem item : guild.getBank().getItems()) {
        level.entityManager.dropItem(flagPos.x, flagPos.y, item);
    }
    
    // 2. Distribute treasury evenly to all members
    int memberCount = guild.getMemberCount();
    int sharePerMember = guild.getTreasury().getCoins() / memberCount;
    for (long memberAuth : guild.getMembers()) {
        PlayerBank bank = BankingManager.getPlayerBank(memberAuth);
        bank.addCoins(sharePerMember);
    }
    
    // 3. Invalidate all crests
    for (long memberAuth : guild.getMembers()) {
        invalidatePlayerCrests(memberAuth, guildID);
    }
    
    // 4. Convert guild flag back to settlement flag
    convertGuildToSettlement(guild.getSettlement());
    
    // 5. Remove from registry
    GuildManager.removeGuild(guildID);
}
```

---

### **3. Research Progress Rollback**
**Gap**: What if server crashes mid-research?

**Solution**:
```java
// Atomic research completion
public class ResearchProgress {
    private int goldContributed;
    private Map<String, Integer> itemsContributed;
    private volatile boolean committed = false;
    
    public synchronized boolean completeResearch() {
        if (committed) return false;
        
        try {
            // 1. Mark research complete
            guild.addCompletedResearch(nodeID);
            
            // 2. Apply bonuses
            guild.applyResearchBonuses(nodeID);
            
            // 3. Save immediately
            guild.save();
            
            // 4. Mark committed
            committed = true;
            
            return true;
        } catch (Exception e) {
            // Rollback on failure
            ModLogger.error("Research completion failed: %s", e);
            return false;
        }
    }
}
```

---

### **4. Multi-Guild Crest Confusion**
**Gap**: Player in 3 guilds, how do they know which crest is which?

**Solution**:
```java
// Crest tooltip shows guild name
@Override
public void appendTooltip(TooltipBuilder tooltip, InventoryItem item) {
    super.appendTooltip(tooltip, item);
    
    int guildID = item.getGndData().getInt("guildID");
    GuildData guild = GuildManager.getGuild(guildID);
    
    if (guild != null) {
        tooltip.append(new GameMessage(
            new LocalMessage("item", "guildcrest.guild", guild.getName())
        ));
        
        // Show if this is active guild
        if (guild.getID() == player.getActiveGuildID()) {
            tooltip.append(new GameMessage(
                new LocalMessage("item", "guildcrest.active")
            ).color(Color.GREEN));
        }
    } else {
        tooltip.append(new GameMessage(
            new LocalMessage("item", "guildcrest.inert")
        ).color(Color.RED));
    }
}
```

---

### **5. Offline Research Abuse**
**Gap**: Can scientist consume entire guild bank while members offline?

**Solution**:
```java
// Scientist research rate limiter with Treasury Minimum Threshold
public class ScientistResearchWorker {
    private static final int MAX_PULL_PER_HOUR = 10000; // Base default (was 1000, now 10000)
    private long lastPullTime = 0;
    private int pulledThisHour = 0;
    
    // NEW: Treasury minimum threshold - Leader/Officer configurable per guild
    private long treasuryMinimumThreshold = 10000; // Default 10k, range 0-∞
    
    public void doResearch(GuildData guild) {
        long currentTime = System.currentTimeMillis();
        
        // Reset hourly counter
        if (currentTime - lastPullTime > 3600000) { // 1 hour
            pulledThisHour = 0;
            lastPullTime = currentTime;
        }
        
        // Check rate limit
        if (pulledThisHour >= MAX_PULL_PER_HOUR) {
            return; // Hit rate limit
        }
        
        // NEW: Check treasury minimum threshold
        long currentTreasury = guild.getTreasury().getBalance();
        int pullAmount = calculatePullAmount();
        if (currentTreasury - pullAmount < treasuryMinimumThreshold) {
            // Would drop below minimum - pull only what's safe
            pullAmount = (int) Math.max(0, currentTreasury - treasuryMinimumThreshold);
            if (pullAmount <= 0) {
                return; // Treasury at or below minimum, no pulling
            }
        }
        
        // Pull resources from bank
        int amount = pullResourcesFromBank(pullAmount);
        pulledThisHour += amount;
    }
    
    // Getter/setter for threshold (Leader/Officer only)
    public long getTreasuryMinimumThreshold() { return treasuryMinimumThreshold; }
    public void setTreasuryMinimumThreshold(long threshold) { 
        this.treasuryMinimumThreshold = Math.max(0, threshold);
    }
}
```

---

## 💡 OPTIMIZATION OPPORTUNITIES

### **1. Crest Rendering Optimization**
**Issue**: Generating 29-component crests on the fly is expensive

**Solution**:
```java
// Cache generated crest textures with RGB color tinting
public class GuildCrestRenderer {
    private static Map<Integer, GameTexture> crestCache = new ConcurrentHashMap<>();
    
    public static GameTexture getCrestTexture(int guildID) {
        return crestCache.computeIfAbsent(guildID, id -> {
            GuildData guild = GuildManager.getGuild(id);
            return generateCrestTexture(guild.getCrestDesign());
        });
    }
    
    // Generate crest by layering components with RGB tinting
    private static GameTexture generateCrestTexture(GuildCrestDesign design) {
        BufferedImage canvas = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        
        // Layer 1: Background shape (tinted to backgroundColor)
        drawTintedSprite(g, design.backgroundShape, design.backgroundColor);
        
        // Layer 2: Emblem (tinted to emblemColor)
        drawTintedSprite(g, design.emblemID, design.emblemColor);
        
        // Layer 3: Border (tinted to border color or white)
        if (design.borderStyle > 0) {
            drawTintedSprite(g, design.borderStyle, Color.WHITE);
        }
        
        g.dispose();
        return new GameTexture(canvas);
    }
    
    // Apply RGB color tint to sprite
    private static void drawTintedSprite(Graphics2D g, int spriteID, Color tint) {
        BufferedImage sprite = getSpriteImage(spriteID);
        BufferedImage tinted = applyColorTint(sprite, tint);
        g.drawImage(tinted, 0, 0, null);
    }
    
    // Clear cache when crest changes
    public static void invalidateCrest(int guildID) {
        crestCache.remove(guildID);
    }
}
```

**For Guild Items**: Use same cached texture for flag, crest item, picture frame, etc.

---

### **2. Permission Check Caching**
**Issue**: Checking guild permissions on every block interaction is slow

**Solution**:
```java
// Cache player-guild permission results
public class GuildPermissionCache {
    private static LoadingCache<CacheKey, Boolean> cache = CacheBuilder.newBuilder()
        .expireAfterWrite(5, TimeUnit.SECONDS)
        .build(new CacheLoader<CacheKey, Boolean>() {
            public Boolean load(CacheKey key) {
                return computePermission(key);
            }
        });
    
    record CacheKey(long playerAuth, int guildID, PermissionType type) {}
}
```

---

### **3. Research Tree UI Lazy Loading**
**Issue**: Loading all 15 nodes + dependencies at once

**Solution**:
```java
// Only load visible branch initially
public class ResearchTreeForm extends FormSection {
    private ResearchBranch visibleBranch = ResearchBranch.LOGISTICS;
    
    @Override
    public void init() {
        // Only load one branch at a time
        loadBranch(visibleBranch);
    }
    
    public void switchBranch(ResearchBranch newBranch) {
        // Unload current
        unloadBranch(visibleBranch);
        
        // Load new
        loadBranch(newBranch);
        visibleBranch = newBranch;
    }
}
```

---

## 📋 IMPLEMENTATION CHECKLIST

### **Phase 0: Foundation** (BEFORE ANY CODE)
- [ ] Create all 48 visual assets
- [x] Set up `medievalsim.guilds` package structure
- [ ] Add ModConfig.Guilds section
- [x] Create GuildZone extending AdminZone ✅ **COMPLETE** - `zones/domain/GuildZone.java`
- [ ] Set up guild data persistence structure

### **Phase 1: Core Data (Week 1)**
- [x] `GuildData.java` - Core guild data structure ✅
- [x] `GuildManager.java` - World-level singleton ✅
- [x] `GuildRank.java` - Rank enum ✅
- [x] `PlayerGuildData.java` - Player membership tracking ✅
- [ ] Save/load integration
- [ ] Basic guild create/join/leave commands (testing)

### **Phase 2: Guild Artisan NPC (Week 2)**
- [x] `GuildArtisanMob.java` - NPC implementation ✅
- [x] `GuildArtisanContainer.java` - Server container ✅
- [x] `GuildArtisanContainerForm.java` - Client UI with **Create / View Guild Info / Manage Guilds** (Create and Join flows retained; **removed direct Bank/Guild Settings entries**). ✅
- [ ] **DEV TASK:** Remove residual `Open Bank` / `Guild Settings` UI elements and any `PacketOpenGuildBank` calls originating from `GuildArtisanContainerForm.java` to ensure Settlement Settings/GuildInfoPanel are the only sources for bank/settings.
- [ ] `ManageGuildsContainer.java` / `ManageGuildsForm.java` - lists player guilds; per-guild Leave and Buy Banner flows (server-validated)
- [ ] `GuildSelectionForm.java` - selection UI when player belongs to multiple guilds
- [ ] Visitor spawner integration
- [ ] Quest unlock system
- [ ] Create/Join eligibility checks: server-side validation for unlock boss, gold, and `ModConfig.Guilds.maxMemberships` limits
- [ ] Basic interaction testing

### New Packets & UX Containers (short list)
- `PacketOpenManageGuilds` → `ManageGuildsContainer` + `ManageGuildsForm` — manage memberships, buy banners (server-validated)
- `PacketRequestGuildInfoSelection` → `GuildSelectionForm` — allow selecting which guild to view when player has multiple memberships
- `PacketOpenTeleportBannersManager` → `TeleportBannersManagerForm` — **DEPRECATED**: teleport/banner management should not live in Settlement Settings; instead manage banners via `GuildInfoPanel` and `ManageGuildsForm`.
- `PacketOpenQuestOffer` / `QuestOfferContainer` — present the unlock quest when player lacks requirements; `PacketRequestGuildUnlockQuestAccept` to accept.
- Note: `PacketOpenGuildBank` will be invoked from **Settlement Settings (C-key)** `Guild Bank` tab; **Artisan no longer opens the bank or settings directly.**

### **Phase 3: Guild Flag & Zones (Week 3)**
- [ ] Add `QuestOfferContainer` UX and server flow for `Prove Your Worth` quest acceptance and completion checks
- [ ] Add `PacketOpenNotifications`, `NotificationsContainer` and `NotificationsForm` to support Artisan notification inbox (invites, research, admin messages); implement `PacketClearNotification` and `PacketClearAllGuildNotifications` server handlers
- [ ] Add `ModConfig.Guilds.maxBannersPerSettlementPerGuild` (default 1) and enforce server-side placement/purchase limits; implement validations in `PacketBuyGuildBanner` and banner place handlers
- [ ] Implement `PacketToggleBannerTeleportAccess` to toggle whether a banner accepts guild teleports
- [ ] Overhaul Banner Interaction UI flow: implement non-member branch (expandable guild info with join/request invite) and member branch (teleport + toggle + destination list)
- [ ] Implement Route B HUD & packet flows: `PacketSurveyPlot(plotID)`, `PacketPurchaseGuildPlot(plotID)` and temporary HUD logic (session timer, Leave action, post-purchase tutorial until flag placed)
- [ ] Implement Cauldron APIs and Farmer flows: `PacketDonateToCauldron`, `PacketStartBrew`, `PacketClaimCauldronReward`, `PacketRequestFarmerQuestAccept`, `PacketSubmitFarmerQuestItems`
- [ ] Mark guild leader as canonical owner in permission resolution and settlement owner mapping
- [ ] Implement `ManageGuildsContainer` and `GuildSelectionForm` flows (per-guild leave and buy banner actions validated server-side)
- [ ] Add UI tests and integration tests for banner placement limits, notification flows, Route B survey sessions, HUD persistence, and flag destruction restrictions
- [ ] Produce high-fidelity PNG wireframes for all menus and submenus and create per-form acceptance checklists (docs/GUILD_UI_PATHS.md will be authoritative); ensure forms are scrollable and support pagination/lazy load for long lists.
- [ ] `GuildFlagObject.java` - Flag object
- [x] `GuildZone.java` - Zone implementation ✅ **COMPLETE**
- [x] Integrate with ZoneVisualizationHud ✅ **COMPLETE** - Added guild zones supplier
- [x] Admin HUD Guild Zones Tab ✅ **COMPLETE** - In AdminToolsHudForm.java
- [x] CreateOrExpandZoneTool ZoneType.GUILD support ✅ **COMPLETE**
- [x] PacketDeleteZone/PacketRenameZone ZoneType support ✅ **COMPLETE**
- [ ] Permission system implementation
- [ ] Settlement conversion logic
- [ ] Zone protection testing
- [ ] **Settlement Settings Integration** — add **Guild Bank** tab and **Guild Settings** button to existing Settlement Settings (C-key). **DEPRECATED:** do not add a `Teleport Banners` management tab; banner management is implemented in `GuildInfoPanel` and `ManageGuildsForm`.

### **Phase 4: Guild Bank & Treasury (Week 4)**
- [ ] `GuildBankInventory.java` - Shared inventory
- [ ] `GuildBankContainer.java` - Bank container
- [ ] `GuildTreasury.java` - Treasury management
- [ ] Grand Exchange taxation hook
- [ ] Transaction logging
- [ ] Multi-player access testing

### **Phase 5: Research System (Week 5-6)**
- [ ] `ResearchNode.java` - Node data structure
- [ ] `ResearchTreeContainer.java` - Research UI server
- [ ] `ResearchTreeContainerForm.java` - Research UI client
- [ ] Resource donation system
- [ ] Scientist NPC automation
- [ ] Research unlock testing

### **Phase 6: Guild Crest (Week 7)**
- [ ] `GuildCrestItem.java` - Trinket implementation
- [ ] `GuildCrestBuff.java` - Dynamic buff
- [ ] Crest designer UI
- [ ] Crest rendering system
- [ ] Buff recalculation testing

### **Phase 7: Additional Features (Week 8)**
- [ ] Guild teleport stand — server enforcement: banners only teleport **within same guild** (ownerGuildID equality); disallow cross-guild teleport destinations
- [ ] Cauldron buffs (disabled until design provided)
- [ ] Blacksmith boosts
- [ ] Map synchronization
- [ ] Guild chest object

### **Phase 8: Polish & Testing (Week 9-10)**
- [ ] Edge case handling

#### Edge Case Test Plan (detailed)
- Route B (Plot Surveying)
  - Test: concurrent purchases — simulate N clients attempting `PacketPurchaseGuildPlot(plotID)` simultaneously; assert exactly one success, funds deducted properly, and others receive failure response with no funds deducted.
  - Test: server restart during survey session — start survey, restart server, reconnect client; assert session resumes with correct remaining time or is properly cancelled per `survey.sessionTimeout` config.
  - Test: flag placement failure post-purchase — simulate invalid placement; assert HUD persists and player receives actionable error and either refund or admin resolution path according to `ModConfig.Guilds.disbandFlagBehavior`.

- Banner purchase & placement
  - Test: enforce `ModConfig.Guilds.maxBannersPerSettlementPerGuild` — try purchases up to and beyond limit; assert additional purchases are rejected and UI shows correct error.
  - Test: immediate-place fallback — purchase & place with blocked placement; item returned to inventory and purchase logged.

- Notifications
  - Test: TTL expiry — create notification with TTL; simulate time advancement and ensure notification disappears and is not returned on `PacketOpenNotifications`.
  - Test: clear single/all — verify `PacketClearNotification` and `PacketClearAllGuildNotifications` mutate server storage and that clients observe changes.

- Guild Flag and Disband
  - Test: flag destruction atomicity — disbanding a guild must cleanly remove zone, banners, and treasury atomically (or follow documented rollback semantics). Test admin-only destruction and `Disband Guild` flows.
  - Test: HUD cancellation on disband — any active survey/HUD related to affected guild should be canceled with correct client notification.

- General
  - Add automated UI tests for per-form acceptance checklists (CreateGuildModal, JoinGuildForm, ManageGuildsForm, BuyBannerModal, CrestDesignerForm, NotificationsForm).
  - Add integration tests for packet flows, server-side validation, and permission checks (leader-only settings, teleport use permission, membership cap enforcement).

Implementation notes:
- Use existing test harness for server-client integration; where necessary, add a headless client simulation for concurrent purchase tests.
- Add documentation links back to `docs/GUILD_UI_PATHS.md` and `docs/guilds_worksheet.md` for test-case source-of-truth.

- [ ] Performance optimization
- [ ] Localization
- [ ] Documentation
- [ ] Multiplayer stress testing

---

## 🎨 ASSET CREATION GUIDELINES

### **NPC Sprite Sheet Layout**
```
[Idle1] [Idle2] [Walk1] [Walk2]
[Walk3] [Walk4] [Work1] [Work2]
```
- Each frame: 32x32 pixels
- Transparent background
- Necesse color palette
- Similar style to other settlers

### **Crest Components**
- **Backgrounds**: Simple shapes, solid colors
- **Emblems**: Iconic, readable at 16x16
- **Borders**: Decorative, don't obscure emblem
- All components should layer cleanly

### **Objects**
- Match Necesse object art style
- Include shadow layers where appropriate
- Active states for animated objects (cauldron, teleport stand)

---

## 🔍 DECISIONS FINALIZED

1. **Guild Names**: ✅ Unique required, NO profanity filter
2. **Plot Pricing**: ✅ Automatic calculation based on settlement quality (rooms, workstations, storage, work zones)
3. **Guild Size**: ✅ NO minimum member count (solo guilds allowed)
4. **Crest Colors**: ✅ Full RGB picker, generate dynamic textures for all guild items
5. **Leadership Transfer**: ✅ Automatic to top contributor (edge case fallback only)
6. **Expanded Vaults Costs**: ✅ DOUBLING each unlock (500k → 1M → 2M = 3.5M total)
7. **Tax Stacking**: ✅ ADDITIVE (2% + 3% = 5% total) across multiple guilds
8. **Crest Stacking**: ✅ **MAJOR CHANGE** - Multiple crests CAN be worn, different trees stack, same tree takes highest

---

## 🚨 CRITICAL SYSTEM CHANGES (Applied 2025-12-19)

### 1. Crest Stacking System Overhaul
**OLD SYSTEM** (outdated):
- ❌ Crests don't stack
- ❌ Only highest bonus applied per modifier

**NEW SYSTEM** (current):
- ✅ Players can wear multiple crests (one per guild membership, max 3)
- ✅ **Different research trees STACK ADDITIVELY** (Industry + Combat + Logistics)
- ✅ **Same research tree takes HIGHEST** (Industry T5 beats Industry T1)
- ✅ Within-tree upgrades stack (T1 +10% + T5 +10% = +20% total)

**Example**:
- Guild A: Industry T5 (+20% damage), Logistics T1 (+5% movement)
- Guild B: Combat T5 (+15% health), Industry T1 (+10% damage)
- **Wearing both crests**: +20% damage (Guild A Industry wins), +5% movement (Logistics), +15% health (Combat) = All bonuses active

**Implementation Impact**:
- Completely rewrites `GuildCrestStackingHelper.java`
- Requires research tree categorization system
- Makes multi-guild membership HIGHLY valuable
- Creates strategic optimization (join guilds with different specializations)

### 2. Expanded Vaults Doubling Cost
**OLD**: 1.5M total (500k × 3)  
**NEW**: 3.5M total (500k + 1M + 2M)

Each unlock now **doubles** the previous cost:
- Unlock 1 (Tab 2): 500,000g + 10k iron + 5k wood + 1k copper
- Unlock 2 (Tab 3): 1,000,000g + 20k iron + 10k wood + 2k copper
- Unlock 3 (Tab 4): 2,000,000g + 40k iron + 20k wood + 4k copper

### 3. Automatic Plot Pricing
**OLD**: Admin sets manual price per plot  
**NEW**: Automatic calculation based on settlement value

**Pricing Algorithm**:
```java
basePrice (100k) 
+ settlers × 1,000
+ quality rooms × 5,000
+ storage objects × 500
+ advanced workstations × 2,000
+ valid work zones: Husbandry (+50k), Forestry (+30k), Farming (+40k)
```

**Range**: ~100k (empty) → ~1M+ (fully developed)

### 4. Tax Stacking Clarification
**Confirmed ADDITIVE**: If player in 2 guilds with 2% and 3% tax:
- Total tax: 5% (not multiplicative 4.94%)
- On 10,000g sale: Player receives 9,500g
- Theoretical max: 3 guilds × 5% = 15% total

---

### ✅ Decisions Finalized

| Topic | Decision | Notes |
|-------|----------|-------|
| Research item gating | **No gating** | If player has the item, it counts toward research |
| Scientist treasury threshold | **10,000 gold default** | Leader/Officer configurable per guild |
| Guild Charter | **Removed** | Direct creation via Guild Artisan |
| Disband treasury | **Redistribute to members** | Evenly distributed |
| Crest caching | **Persistent** | Survives mod updates |

---

**NEXT STEP**: Phase 0 scaffolding — create package structure and core interfaces
