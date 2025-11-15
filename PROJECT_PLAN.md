# 🎯 Necesse Server Enhancement - Project Plan

**Goal:** Create admin/owner tools for enhanced server management and settlement control

**Status:** Research Phase  
**Last Updated:** 2025-10-18

---

## 📋 PROJECT OVERVIEW

### Features to Implement

1. **Admin Tools Button** - Add button to inventory UI for admin/owner access
2. **Admin Creation Tools** - Creative presets + multi-tile placement tool
3. **Zone Mapping System** - Protected zones and PvP zones with tile mapping
4. **Settlement Spacing** - Increase minimum distance between settlements by 50 tiles
5. **Plot Flag System** - Purchasable settlement plots with customizable pricing
6. **Deep Research** - Verify all implementations against Necesse modding resources

---

## 🔬 RESEARCH PHASE (REQUIRED BEFORE IMPLEMENTATION)

### Research Checklist

**Before implementing ANY feature, research:**

- [x] **Feature 1: Admin Tools Button** ✅ RESEARCH COMPLETE
  - [x] How inventory UI is structured
  - [x] Where bottom-right buttons are defined
  - [x] How to add custom buttons to inventory
  - [x] How permission checking for button visibility
  - [x] Button click handlers and events

- [x] **Feature 2: Multi-Tile Placement Tool** ✅ RESEARCH COMPLETE
  - [x] How to create custom tool items
  - [x] How to create tool configuration UI
  - [x] Multi-tile placement patterns (shapes)
  - [x] Inventory checking and consumption
  - [x] Tool registration and activation

- [ ] **Feature 3: Zone Mapping System**
  - [ ] How tile protection works (already verified)
  - [ ] How to create custom zone types
  - [ ] Zone visualization (particles, overlays, UI)
  - [ ] Zone storage and persistence
  - [ ] PvP damage event hooks

- [ ] **Feature 4: Settlement Spacing**
  - [ ] How settlement expansion checking works (already verified)
  - [ ] Where minimum distance is defined
  - [ ] How to modify region rectangle calculations
  - [ ] Testing settlement placement validation

- [ ] **Feature 5: Plot Flag System**
  - [ ] How settlement flags work
  - [ ] How to create custom items (plot flag)
  - [ ] Container/UI for price customization
  - [ ] Purchase validation and currency handling
  - [ ] Flag placement and settlement creation

---

## 📊 IMPLEMENTATION ROADMAP

### Phase 1: Research & Planning (Week 1)
**Goal:** Complete all research before writing any code

**Tasks:**
1. Research inventory UI system
2. Research creative tools and presets
3. Research zone systems and tile protection
4. Research settlement spacing mechanics
5. Research item creation and containers
6. Document all findings with verified APIs
7. Create detailed implementation plan for each feature

**Deliverables:**
- Research documentation for each feature
- Verified API list for each feature
- Implementation patterns identified
- Risk assessment and complexity estimates

---

### Phase 2: Admin Tools Button (Week 2)
**Goal:** Add admin/owner tools button to inventory UI

**Prerequisites:**
- ✅ Research inventory UI structure
- ✅ Research button registration
- ✅ Research permission checking

**Implementation Steps:**
1. Locate inventory UI class and button container
2. Create custom button class for admin tools
3. Add permission check (ADMIN or OWNER level)
4. Register button to inventory UI
5. Create click handler to open admin tools menu
6. Test button visibility and permissions

**Success Criteria:**
- Button appears in bottom-right of inventory for ADMIN/OWNER
- Button hidden for non-admin players
- Button opens admin tools menu when clicked
- No crashes or UI glitches

---

### Phase 3: Admin Creation Tools (Week 3-4)
**Goal:** Implement creative presets access and multi-tile placement tool

**Prerequisites:**
- ✅ Research creative mode tools
- ✅ Research tile placement systems
- ✅ Research tool registration

**Implementation Steps:**

**Part A: Creative Presets Access**
1. Locate creative presets UI/system
2. Create permission-gated access to presets
3. Add presets option to admin tools menu
4. Test preset access for admins

**Part B: Multi-Tile Placement Tool**
1. Create custom tool item class
2. Implement tile selection system (shape/size configuration)
3. Add placement preview (visual feedback)
4. Implement bulk tile placement logic
5. Add shape options (rectangle, circle, line, fill)
6. Add size configuration UI
7. Register tool and add to admin tools menu
8. Test placement accuracy and performance

**Success Criteria:**
- Admins can access creative presets
- Multi-tile tool allows shape selection (rectangle, circle, etc.)
- Multi-tile tool allows size configuration
- Placement preview shows before confirming
- Bulk placement works correctly
- No performance issues with large placements

---

### Phase 4: Zone Mapping System (Week 5-6)
**Goal:** Create protected zones and PvP zones with admin tile mapping

**Prerequisites:**
- ✅ Research tile protection (already verified)
- ✅ Research damage event hooks
- ✅ Research zone visualization

**Implementation Steps:**

**Part A: Zone Data Structure**
1. Create Zone class (type, bounds, name, ID)
2. Create ZoneManager for storage and retrieval
3. Implement zone persistence (save/load)
4. Add zone synchronization to clients

**Part B: Protected Zones**
1. Create protected zone type
2. Implement tile protection application
3. Add zone creation tool (tile mapping)
4. Add zone editing/deletion tools
5. Test tile protection in zones

**Part C: PvP Zones**
1. Create PvP zone type (safe/combat)
2. Hook player damage events
3. Implement damage blocking in safe zones
4. Implement damage enabling in combat zones
5. Add zone entry/exit notifications
6. Test PvP mechanics in zones

**Part D: Zone Visualization**
1. Add zone boundary rendering (particles/overlay)
2. Add zone info display (name, type)
3. Add zone list UI for admins
4. Test visualization performance

**Success Criteria:**
- Admins can create protected zones via tile mapping
- Protected zones prevent tile destruction
- Admins can create PvP zones (safe/combat)
- Safe zones block all PvP damage
- Combat zones enable PvP damage
- Zones persist across server restarts
- Zone boundaries visible to players
- Zone list UI shows all zones

---

### Phase 5: Settlement Spacing (Week 7)
**Goal:** Increase minimum distance between settlements by 50 tiles

**Prerequisites:**
- ✅ Research settlement expansion (already verified)
- ✅ Research region rectangle calculations

**Implementation Steps:**
1. Locate settlement spacing validation code
2. Identify where region rectangle is calculated
3. Add 50-tile buffer to region rectangle calculation
4. Test settlement placement with new spacing
5. Test settlement expansion with new spacing
6. Verify "tooclosesettlement" message still works

**Success Criteria:**
- Settlements must be 50 tiles farther apart
- Settlement placement blocked if too close
- Settlement expansion blocked if would overlap
- Existing settlements unaffected
- Clear error message when placement fails

---

### Phase 6: Plot Flag System (Week 8-10)
**Goal:** Create purchasable settlement plots with customizable pricing

**Prerequisites:**
- ✅ Research settlement flag mechanics
- ✅ Research item creation
- ✅ Research container/UI systems
- ✅ Research currency handling

**Implementation Steps:**

**Part A: Plot Flag Item**
1. Create PlotFlag item class
2. Add item registration
3. Add item sprite/graphics
4. Add item to elder's shop (admin-only purchase)
5. Test item acquisition

**Part B: Price Customization**
1. Create PlotFlagContainer for price setting
2. Create PlotFlagForm UI for price input
3. Store price in item data (NBT/custom data)
4. Add "Set Price" interaction when in inventory
5. Test price customization UI

**Part C: Plot Placement & Purchase**
1. Create plot flag placement logic
2. Add purchase validation (check player currency)
3. Implement currency deduction
4. Create settlement flag at plot location
5. Transfer ownership to purchaser
6. Add purchase confirmation UI
7. Test purchase flow

**Part D: Plot Management**
1. Add plot listing UI (show available plots)
2. Add plot removal tool (admin-only)
3. Add plot price editing (admin-only)
4. Test plot management tools

**Success Criteria:**
- Admins can purchase plot flags from elder
- Plot flags can be customized with price
- Plot flags can be placed in world
- Players can purchase plots with currency
- Settlement flag created on purchase
- Ownership transferred correctly
- Currency deducted correctly
- Plot management tools work

---

## 🔍 RESEARCH AREAS

### 1. Inventory UI System

**Need to Research:**
- Where is inventory UI defined?
- How are bottom-right buttons added?
- What is the button container class?
- How to add custom buttons?
- How to check permissions for button visibility?

**Files to Investigate:**
- `necesse/gfx/forms/components/inventory/`
- `necesse/gfx/forms/InventoryForm.java`
- `necesse/entity/mobs/PlayerMob.java` (inventory access)

---

### 2. Creative Tools & Presets

**Need to Research:**
- How does creative mode work?
- Where are creative presets defined?
- How are tools registered?
- How does tile placement work?
- How to create custom tools?

**Files to Investigate:**
- `necesse/engine/world/worldPresets/`
- `necesse/inventory/item/toolItem/`
- `necesse/entity/mobs/PlayerMob.java` (creative mode)

---

### 3. Zone Systems

**Need to Research:**
- How to store custom zone data?
- How to visualize zones (particles, overlays)?
- How to hook damage events?
- How to sync zones to clients?

**Files to Investigate:**
- `necesse/level/maps/regionSystem/RegionManager.java` (already verified)
- `necesse/entity/mobs/Mob.java` (damage events)
- `necesse/engine/network/Packet.java` (synchronization)

---

### 4. Settlement Spacing

**Need to Research:**
- Where is minimum spacing defined?
- How to modify region rectangle size?
- How to add buffer to spacing calculation?

**Files to Investigate:**
- `necesse/level/maps/levelData/settlementData/SettlementBoundsManager.java` (already verified)
- `necesse/level/maps/levelData/settlementData/SettlementsWorldData.java`

---

### 5. Plot Flag System

**Need to Research:**
- How do settlement flags work?
- How to create custom items?
- How to store item data (price)?
- How to create purchase UI?
- How to handle currency?

**Files to Investigate:**
- `necesse/inventory/item/Item.java`
- `necesse/level/maps/levelData/settlementData/`
- `necesse/inventory/container/Container.java`
- `necesse/gfx/forms/Form.java`

---

## ⚠️ CRITICAL RULES

### Before Writing ANY Code:

1. **Research First** - Use codebase-retrieval to find relevant code
2. **Verify APIs** - Check decompiled source for exact method signatures
3. **Study Examples** - Look at Aphorea mod for implementation patterns
4. **Document Findings** - Record all verified APIs and patterns
5. **Plan Implementation** - Write detailed steps before coding
6. **Test Incrementally** - Test each small piece before moving on

### During Implementation:

1. **Use Verified APIs Only** - No guessing method signatures
2. **Follow Necesse Patterns** - Match existing code style
3. **Complete Implementations** - No placeholders or TODOs
4. **Test Thoroughly** - Test each feature completely
5. **Handle Errors** - Add proper error handling
6. **Document Code** - Add comments explaining complex logic

---

## 📈 PROGRESS TRACKING

### Phase 1: Research & Planning
- [ ] Research inventory UI system
- [ ] Research creative tools and presets
- [ ] Research zone systems
- [ ] Research settlement spacing
- [ ] Research plot flag system
- [ ] Document all findings
- [ ] Create detailed implementation plans

### Phase 2: Admin Tools Button
- [ ] Locate inventory UI classes
- [ ] Create admin tools button
- [ ] Add permission checking
- [ ] Register button to UI
- [ ] Create admin tools menu
- [ ] Test and verify

### Phase 3: Admin Creation Tools
- [ ] Implement creative presets access
- [ ] Create multi-tile placement tool
- [ ] Add shape selection
- [ ] Add size configuration
- [ ] Add placement preview
- [ ] Test and verify

### Phase 4: Zone Mapping System
- [ ] Create zone data structure
- [ ] Implement protected zones
- [ ] Implement PvP zones
- [ ] Add zone visualization
- [ ] Add zone management UI
- [ ] Test and verify

### Phase 5: Settlement Spacing
- [ ] Locate spacing validation code
- [ ] Modify region rectangle calculation
- [ ] Add 50-tile buffer
- [ ] Test settlement placement
- [ ] Test settlement expansion
- [ ] Verify error messages

### Phase 6: Plot Flag System
- [ ] Create plot flag item
- [ ] Implement price customization
- [ ] Implement plot placement
- [ ] Implement purchase system
- [ ] Add plot management tools
- [ ] Test complete flow

---

## 🎯 NEXT IMMEDIATE STEPS

1. **START WITH RESEARCH** - Do NOT write code yet
2. **Use codebase-retrieval** - Find inventory UI system
3. **Study decompiled source** - Verify exact APIs
4. **Document findings** - Record all verified information
5. **Create detailed plan** - Plan each feature implementation
6. **Get approval** - Confirm plan before coding

---

**Ready to begin research phase?** Let me know when you want to start researching Feature 1 (Admin Tools Button)!


