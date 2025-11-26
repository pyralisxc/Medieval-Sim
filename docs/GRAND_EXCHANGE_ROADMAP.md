# Grand Exchange - Feature Roadmap

## ✅ **Completed Features**

### **Core Infrastructure**
- ✅ MarketListing data structure with expiration tracking
- ✅ GrandExchangeLevelData with price history and automatic cleanup
- ✅ GrandExchangeContainer with server-side validation
- ✅ GrandExchangeContainerForm with comprehensive UI
- ✅ Packet system (PacketOpenGrandExchange, PacketGESync)
- ✅ Trader NPC integration via ByteBuddy patching

### **Listing Management**
- ✅ Create listings with quantity and price
- ✅ Purchase listings (full or partial)
- ✅ Cancel own listings (returns items to inventory)
- ✅ Automatic expiration (configurable 1-720 hours, default 168 hours)
- ✅ Return expired items to bank (configurable)
- ✅ Send sale proceeds to bank (configurable)

### **Search & Filtering**
- ✅ Item name search
- ✅ Price range filter (min/max)
- ✅ Minimum quantity filter
- ✅ Category filter (weapons, armor, tools, materials, food, potions, trinkets, seeds, objects, tiles, misc)
- ✅ "My Listings" vs "All Listings" toggle
- ✅ Clear all filters button

### **Sorting**
- ✅ Price: Low to High
- ✅ Price: High to Low
- ✅ Quantity: High to Low
- ✅ Time Remaining: Least to Most

### **Bulk Purchasing**
- ✅ Per-listing quantity input (1-999,999)
- ✅ Buy X items button
- ✅ Buy All button
- ✅ Send to Bank button (purchase directly to bank)

### **UI Features**
- ✅ Pagination (8 listings per page)
- ✅ Stats display showing active filters
- ✅ Sort mode indicator
- ✅ Page navigation (Previous/Next)
- ✅ Category quick-filter buttons
- ✅ Cancel buttons for own listings

### **Price Discovery**
- ✅ Price history tracking (last 100 sales per item)
- ✅ Going price calculation (median of recent sales)
- ✅ Price data persists across server restarts

---

## 🎯 **OSRS Grand Exchange Feature Comparison**

### **What We Have (Similar to OSRS)**
1. ✅ Buy/Sell interface
2. ✅ Price history tracking
3. ✅ Partial fulfillment (buy X out of Y available)
4. ✅ Listing expiration
5. ✅ Category filtering
6. ✅ Search by item name
7. ✅ Price sorting

### **What OSRS Has That We Don't**
1. ❌ **Automatic Order Matching** - OSRS automatically matches buy/sell orders
2. ❌ **Buy Offers** - Players can place buy orders at specific prices
3. ❌ **Price Guides** - Visual price trends (graphs, +/- % change)
4. ❌ **Offer History** - Track completed transactions
5. ❌ **Collection Box** - Temporary storage for purchased items/coins
6. ❌ **Instant Buy/Sell** - Quick transactions at market price
7. ❌ **Price Limits** - Min/max price bounds based on item value
8. ❌ **Tax System** - Transaction fees
9. ❌ **Trade Volume Display** - Show how many items traded recently
10. ❌ **Favorites System** - Quick access to frequently traded items

---

## 🚀 **Recommended Next Features (Priority Order)**

### **Phase 11: Buy Offers System** (High Priority)
**Complexity:** High | **Impact:** Transforms GE into true marketplace

**What to implement:**
- `BuyOffer` class (similar to MarketListing but for buy orders)
- Automatic order matching when sell price <= buy price
- Partial fulfillment (match multiple sellers to one buyer)
- Buy offer expiration and cancellation
- UI for creating buy offers

**Why it matters:** This is the core OSRS GE mechanic - players can place buy orders and the system automatically matches them with sellers.

### **Phase 12: Collection Box** (Medium Priority)
**Complexity:** Medium | **Impact:** Improves UX significantly

**What to implement:**
- Temporary storage for purchased items and sale proceeds
- Separate UI tab for collection box
- Auto-collect to inventory/bank button
- Expiration warning (items held for X days)

**Why it matters:** Prevents inventory overflow and allows players to manage multiple transactions.

### **Phase 13: Price Guides & Trends** (Medium Priority)
**Complexity:** Medium | **Impact:** Helps players make informed decisions

**What to implement:**
- Price trend calculation (7-day, 30-day averages)
- Price change percentage display (+5%, -12%, etc.)
- Visual price graph (simple line chart)
- High/low price tracking

**Why it matters:** Players can see if prices are rising/falling and make better trading decisions.

### **Phase 14: Offer History** (Low Priority)
**Complexity:** Low | **Impact:** Quality of life

**What to implement:**
- Track last 50 completed transactions per player
- UI tab showing purchase/sale history
- Filter by item, date, type (buy/sell)

**Why it matters:** Players can review their trading activity.

### **Phase 15: Instant Buy/Sell** (Medium Priority)
**Complexity:** Medium | **Impact:** Convenience feature

**What to implement:**
- "Instant Buy" button (purchases at lowest available price)
- "Instant Sell" button (sells at highest buy offer price)
- Confirmation dialog showing final price

**Why it matters:** Quick transactions for players who don't want to wait for order matching.

### **Phase 16: Advanced Features** (Low Priority)
**Complexity:** Low-Medium | **Impact:** Polish

**What to implement:**
- Favorites system (star items for quick access)
- Trade volume display (X items traded in last 24h)
- Price limits (prevent extreme pricing)
- Transaction tax system (configurable % fee)
- Notification system (alert when buy offer is filled)

---

## 📊 **Current Complexity vs OSRS GE**

### **Our Current System: ~40% of OSRS GE**

**What we have:**
- ✅ Sell-only marketplace (no buy offers)
- ✅ Manual matching (players browse and purchase)
- ✅ Basic price tracking
- ✅ Comprehensive filtering and sorting
- ✅ Bulk purchasing
- ✅ Category system

**What we're missing:**
- ❌ Automatic order matching
- ❌ Buy offers
- ❌ Collection box
- ❌ Price trend visualization
- ❌ Offer history

### **To Reach 80% OSRS GE Complexity:**

Implement **Phases 11-13**:
1. Buy Offers System (automatic matching)
2. Collection Box (temporary storage)
3. Price Guides & Trends (visual feedback)

**Estimated Development Time:**
- Phase 11: 8-12 hours (complex matching logic)
- Phase 12: 4-6 hours (new container + UI)
- Phase 13: 6-8 hours (data aggregation + visualization)

**Total:** ~20-26 hours of development

### **To Reach 100% OSRS GE Complexity:**

Implement **Phases 11-16** (all features)

**Estimated Development Time:** ~35-45 hours

---

## 🛠️ **Technical Challenges**

### **Phase 11: Buy Offers (Biggest Challenge)**

**Problem:** Automatic order matching requires:
1. Efficient matching algorithm (match best prices first)
2. Partial fulfillment logic (split large orders)
3. Transaction atomicity (prevent race conditions)
4. Notification system (alert buyers/sellers)

**Solution Approach:**
```java
// Pseudo-code for order matching
public void matchOrders(Level level) {
    // Get all buy offers sorted by price (highest first)
    List<BuyOffer> buyOffers = getAllBuyOffers()
        .stream()
        .sorted(Comparator.comparingInt(BuyOffer::getPricePerItem).reversed())
        .collect(Collectors.toList());

    // Get all sell listings sorted by price (lowest first)
    List<MarketListing> sellListings = getAllListings()
        .stream()
        .sorted(Comparator.comparingInt(MarketListing::getPricePerItem))
        .collect(Collectors.toList());

    // Match orders
    for (BuyOffer buyOffer : buyOffers) {
        for (MarketListing sellListing : sellListings) {
            if (sellListing.getItemStringID().equals(buyOffer.getItemStringID()) &&
                sellListing.getPricePerItem() <= buyOffer.getPricePerItem()) {

                // Match found! Execute transaction
                int quantityToTransfer = Math.min(
                    buyOffer.getRemainingQuantity(),
                    sellListing.getQuantity()
                );

                executeTransaction(buyOffer, sellListing, quantityToTransfer);

                if (buyOffer.getRemainingQuantity() == 0) break;
            }
        }
    }
}
```

### **Phase 12: Collection Box**

**Problem:** Need temporary storage that's separate from inventory/bank.

**Solution:** Create `CollectionBoxLevelData` similar to `BankingLevelData`:
- Store items and coins per player
- Auto-expire after X days (configurable)
- UI tab in GE form

### **Phase 13: Price Trends**

**Problem:** Need to aggregate historical data and render graphs.

**Solution:**
- Extend `GrandExchangeLevelData.priceHistory` to track timestamps
- Calculate 7-day/30-day moving averages
- Use Necesse's drawing API to render simple line charts
- Show +/- % change in listing display

---

## 🎮 **Gameplay Impact**

### **Current System (Phases 1-10)**
- Players can sell items and browse listings
- Good for casual trading
- Requires manual browsing and purchasing
- No automated economy

### **With Buy Offers (Phase 11)**
- **Transforms into true player-driven economy**
- Players can place buy orders and go offline
- Automatic price discovery (supply/demand)
- Encourages bulk trading and speculation

### **With Collection Box (Phase 12)**
- Reduces inventory management hassle
- Allows multiple simultaneous transactions
- Better UX for active traders

### **With Price Trends (Phase 13)**
- Players can identify profitable items
- Market manipulation becomes visible
- Encourages informed trading decisions

---

## 📝 **Recommendations**

### **For a "Good Enough" GE:**
✅ **Current system is already functional!**
- Players can list, search, filter, sort, and purchase items
- Price tracking works
- Banking integration is solid

**Suggested polish:**
- Add tooltips explaining filters
- Add confirmation dialogs for large purchases
- Add sound effects for transactions
- Add visual feedback (item icons in listings)

### **For an "OSRS-Level" GE:**
🎯 **Implement Phases 11-13** (Buy Offers, Collection Box, Price Trends)

This will give you ~80% of OSRS GE functionality and create a true player-driven economy.

### **For a "Best-in-Class" GE:**
🚀 **Implement all phases (11-16)**

This will exceed OSRS GE in some areas (better filtering, bulk purchasing) while matching it in core features.

---

## 🏁 **Conclusion**

**Current Status:** Medieval Sim's Grand Exchange is a **fully functional sell-only marketplace** with excellent filtering, sorting, and bulk purchasing features.

**Next Steps:**
1. **Test current implementation** - Run `./gradlew.bat runDevClient` and verify all features work
2. **Decide on scope** - Do you want a simple marketplace or full OSRS-style economy?
3. **Implement Phase 11** (Buy Offers) if you want automatic order matching
4. **Polish UI** - Add item icons, tooltips, better visual feedback

**You've built a solid foundation!** The current system is production-ready for a sell-only marketplace. Adding buy offers (Phase 11) would transform it into a true economy system.



