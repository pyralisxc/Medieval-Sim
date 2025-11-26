package medievalsim.grandexchange;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import necesse.engine.network.server.ServerClient;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * World-level storage for Grand Exchange market listings.
 * 
 * Features:
 * - Store all active market listings
 * - Track price history for items
 * - Generate unique listing IDs
 * - Provide search/filter functionality
 */
public class GrandExchangeLevelData extends LevelData {
    
    public static final String DATA_KEY = "medievalsim_grandexchange";
    
    // All market listings (listingID -> MarketListing)
    private final ConcurrentHashMap<Long, MarketListing> listings = new ConcurrentHashMap<>();

    // Price history (itemStringID -> list of recent prices)
    private final ConcurrentHashMap<String, List<Integer>> priceHistory = new ConcurrentHashMap<>();

    // Listing ID generator
    private final AtomicLong nextListingID = new AtomicLong(1);

    // Cleanup tracking (run cleanup every 5 minutes)
    private int tickCounter = 0;
    private static final int CLEANUP_INTERVAL_TICKS = 20 * 60 * 5; // 5 minutes (20 ticks/sec * 60 sec * 5 min)
    
    // Maximum price history entries per item
    private static final int MAX_PRICE_HISTORY = 100;
    
    public GrandExchangeLevelData() {
        super();
    }
    
    // ===== STATIC HELPERS =====
    
    /**
     * Get or create GrandExchangeLevelData for a level.
     */
    public static GrandExchangeLevelData getGrandExchangeData(Level level) {
        if (level == null || !level.isServer()) {
            return null;
        }
        
        GrandExchangeLevelData data = (GrandExchangeLevelData) level.getLevelData(DATA_KEY);
        if (data == null) {
            data = new GrandExchangeLevelData();
            level.addLevelData(DATA_KEY, data);
            ModLogger.debug("Created new GrandExchangeLevelData for level %s", level.getIdentifier());
        }
        return data;
    }
    
    // ===== LISTING MANAGEMENT =====
    
    /**
     * Create a new market listing.
     * @return The created listing, or null if failed
     */
    public MarketListing createListing(ServerClient seller, String itemStringID, 
                                      int quantity, int pricePerItem) {
        if (seller == null || itemStringID == null || quantity <= 0 || pricePerItem <= 0) {
            ModLogger.error("Invalid listing parameters");
            return null;
        }
        
        long listingID = nextListingID.getAndIncrement();
        MarketListing listing = new MarketListing(
            listingID, 
            seller.authentication, 
            seller.getName(),
            itemStringID, 
            quantity, 
            pricePerItem
        );
        
        listings.put(listingID, listing);
        ModLogger.info("Created listing ID=%d: %s x%d @ %d coins (seller=%s)", 
            listingID, itemStringID, quantity, pricePerItem, seller.getName());
        
        return listing;
    }
    
    /**
     * Remove a listing by ID.
     */
    public boolean removeListing(long listingID) {
        MarketListing removed = listings.remove(listingID);
        if (removed != null) {
            ModLogger.debug("Removed listing ID=%d", listingID);
            return true;
        }
        return false;
    }
    
    /**
     * Get a listing by ID.
     */
    public MarketListing getListing(long listingID) {
        return listings.get(listingID);
    }
    
    /**
     * Get all active listings.
     */
    public List<MarketListing> getAllListings() {
        return listings.values().stream()
            .filter(MarketListing::isActive)
            .filter(l -> !l.isExpired())
            .collect(Collectors.toList());
    }
    
    /**
     * Get all listings for a specific item.
     */
    public List<MarketListing> getListingsForItem(String itemStringID) {
        return listings.values().stream()
            .filter(MarketListing::isActive)
            .filter(l -> !l.isExpired())
            .filter(l -> l.getItemStringID().equals(itemStringID))
            .sorted(Comparator.comparingInt(MarketListing::getPricePerItem))
            .collect(Collectors.toList());
    }
    
    /**
     * Get all listings by a specific seller.
     */
    public List<MarketListing> getListingsBySeller(long sellerAuth) {
        return listings.values().stream()
            .filter(l -> l.getSellerAuth() == sellerAuth)
            .collect(Collectors.toList());
    }
    
    // ===== PRICE TRACKING =====
    
    /**
     * Record a sale price for an item.
     */
    public void recordSale(String itemStringID, int pricePerItem) {
        List<Integer> history = priceHistory.computeIfAbsent(itemStringID, k -> new ArrayList<>());
        history.add(pricePerItem);

        // Trim history if too long
        if (history.size() > MAX_PRICE_HISTORY) {
            history.remove(0);
        }
    }

    /**
     * Get the average "going price" for an item based on recent sales.
     * @return Average price, or 0 if no history
     */
    public int getGoingPrice(String itemStringID) {
        List<Integer> history = priceHistory.get(itemStringID);
        if (history == null || history.isEmpty()) {
            return 0;
        }

        // Calculate average of recent sales
        int sum = 0;
        for (int price : history) {
            sum += price;
        }
        return sum / history.size();
    }

    /**
     * Get the lowest current listing price for an item.
     * @return Lowest price, or 0 if no listings
     */
    public int getLowestPrice(String itemStringID) {
        return getListingsForItem(itemStringID).stream()
            .mapToInt(MarketListing::getPricePerItem)
            .min()
            .orElse(0);
    }

    /**
     * Tick handler - called every game tick by LevelDataManager.
     * Periodically cleans up expired listings.
     */
    @Override
    public void tick() {
        super.tick();

        // Only run on server
        if (this.level == null || !this.level.isServer()) {
            return;
        }

        tickCounter++;
        if (tickCounter >= CLEANUP_INTERVAL_TICKS) {
            tickCounter = 0;
            if (ModConfig.GrandExchange.enableListingExpiration) {
                cleanupExpiredListings(this.level);
            }
        }
    }

    /**
     * Clean up expired listings.
     * Returns unsold items to seller's bank if enabled.
     */
    public void cleanupExpiredListings(necesse.level.maps.Level level) {
        int removed = 0;
        int returned = 0;
        Iterator<Map.Entry<Long, MarketListing>> iterator = listings.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, MarketListing> entry = iterator.next();
            MarketListing listing = entry.getValue();

            if (listing.isExpired()) {
                // Return unsold items to seller's bank if enabled
                if (ModConfig.GrandExchange.returnExpiredToBank &&
                    ModConfig.Banking.enabled &&
                    listing.getQuantity() > 0) {

                    medievalsim.banking.BankingLevelData bankingData =
                        medievalsim.banking.BankingLevelData.getBankingData(level);

                    if (bankingData != null) {
                        medievalsim.banking.PlayerBank sellerBank =
                            bankingData.getOrCreateBank(listing.getSellerAuth());

                        necesse.inventory.InventoryItem unsoldItem = listing.getInventoryItem();
                        if (unsoldItem != null) {
                            boolean success = sellerBank.getInventory().addItem(
                                level,
                                null,
                                unsoldItem,
                                "grandexchange_expired",
                                null
                            );

                            if (success) {
                                returned++;
                                ModLogger.debug("Returned %d x %s to seller auth=%d (listing expired)",
                                    listing.getQuantity(), listing.getItemStringID(), listing.getSellerAuth());
                            } else {
                                ModLogger.warn("Failed to return expired items to seller bank (bank full?) - items lost");
                            }
                        }
                    }
                }

                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            ModLogger.info("Cleaned up %d expired listings (%d items returned to banks)", removed, returned);
        }
    }

    // ===== SAVE/LOAD =====

    @Override
    public void addSaveData(SaveData save) {
        // Save next listing ID
        save.addLong("nextListingID", nextListingID.get());

        // Save all listings
        SaveData listingsSave = new SaveData("listings");
        for (MarketListing listing : listings.values()) {
            SaveData listingSave = new SaveData("listing");
            listing.addSaveData(listingSave);
            listingsSave.addSaveData(listingSave);
        }
        save.addSaveData(listingsSave);

        // Save price history
        SaveData historySave = new SaveData("priceHistory");
        for (Map.Entry<String, List<Integer>> entry : priceHistory.entrySet()) {
            SaveData itemHistory = new SaveData("item");
            itemHistory.addUnsafeString("itemID", entry.getKey());
            itemHistory.addIntArray("prices", entry.getValue().stream().mapToInt(i -> i).toArray());
            historySave.addSaveData(itemHistory);
        }
        save.addSaveData(historySave);
    }

    @Override
    public void applyLoadData(LoadData load) {
        // Load next listing ID
        nextListingID.set(load.getLong("nextListingID", 1L));

        // Load all listings
        LoadData listingsSave = load.getFirstLoadDataByName("listings");
        if (listingsSave != null) {
            for (LoadData listingSave : listingsSave.getLoadDataByName("listing")) {
                try {
                    MarketListing listing = MarketListing.fromLoadData(listingSave);
                    listings.put(listing.getListingID(), listing);
                } catch (Exception e) {
                    ModLogger.error("Failed to load market listing: %s", e.getMessage());
                }
            }
            ModLogger.info("Loaded %d market listings", listings.size());
        }

        // Load price history
        LoadData historySave = load.getFirstLoadDataByName("priceHistory");
        if (historySave != null) {
            for (LoadData itemHistory : historySave.getLoadDataByName("item")) {
                try {
                    String itemID = itemHistory.getUnsafeString("itemID");
                    int[] prices = itemHistory.getIntArray("prices");
                    List<Integer> priceList = new ArrayList<>();
                    for (int price : prices) {
                        priceList.add(price);
                    }
                    priceHistory.put(itemID, priceList);
                } catch (Exception e) {
                    ModLogger.error("Failed to load price history: %s", e.getMessage());
                }
            }
        }
    }
}

