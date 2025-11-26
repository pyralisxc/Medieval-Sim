package medievalsim.grandexchange;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkClient;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.inventory.container.Container;
import necesse.inventory.container.customAction.EmptyCustomAction;
import necesse.inventory.container.customAction.IntCustomAction;
import necesse.inventory.container.customAction.PointCustomAction;
import necesse.inventory.container.customAction.StringCustomAction;
import necesse.inventory.container.slots.ContainerSlot;

import java.util.ArrayList;
import java.util.List;

/**
 * Server and client-side container for the Grand Exchange.
 * 
 * Features:
 * - Browse market listings
 * - Create new listings (sell items)
 * - Purchase items from listings
 * - View price history
 * - Filter and search
 */
public class GrandExchangeContainer extends Container {
    
    // GE data
    public final GrandExchangeLevelData geData;
    public final long playerAuth;
    
    // Current view state
    public List<MarketListing> currentListings = new ArrayList<>();
    public String currentFilter = ""; // Item name filter
    public int currentPage = 0;
    public int sortMode = 1; // 0=none, 1=price low-high, 2=price high-low, 3=quantity, 4=time remaining

    // Advanced filters
    public int minPrice = 0; // Minimum price filter (0 = no filter)
    public int maxPrice = 0; // Maximum price filter (0 = no filter)
    public int minQuantity = 0; // Minimum quantity filter (0 = no filter)
    public boolean showOnlyPlayerListings = false; // Show only player's own listings
    public String categoryFilter = ""; // Category filter (empty = all categories)

    // Sell slot (player places item here to sell)
    public static final int SELL_SLOT_INDEX = 0;

    // Custom actions
    public PointCustomAction createListing; // x=quantity, y=pricePerItem
    public PointCustomAction purchaseListing; // x=listingID (lower 32 bits), y=quantity
    public PointCustomAction purchaseToBank; // x=listingID (lower 32 bits), y=quantity (sends to bank)
    public IntCustomAction cancelListing; // Args: listingID (lower 32 bits)
    public StringCustomAction setFilter; // Args: filter string
    public IntCustomAction setPage; // Args: page number
    public IntCustomAction setSortMode; // Args: sort mode (0-4)
    public PointCustomAction setPriceFilter; // x=minPrice, y=maxPrice (0 = no filter)
    public IntCustomAction setMinQuantityFilter; // Args: minQuantity (0 = no filter)
    public IntCustomAction togglePlayerListings; // Args: 1=show only player's, 0=show all
    public StringCustomAction setCategoryFilter; // Args: category string (empty = all)
    public EmptyCustomAction clearFilters; // Clear all filters
    public EmptyCustomAction refreshListings;
    
    /**
     * Constructor for both client and server.
     * @param client NetworkClient
     * @param uniqueSeed Unique container seed
     * @param content Packet content with GE data
     */
    public GrandExchangeContainer(NetworkClient client, int uniqueSeed, Packet content) {
        super(client, uniqueSeed);
        
        PacketReader reader = new PacketReader(content);
        this.playerAuth = reader.getNextLong();
        
        // Get GE data
        if (client.isServer()) {
            // Server-side: get actual GE data from level
            this.geData = GrandExchangeLevelData.getGrandExchangeData(
                client.getServerClient().getLevel());
            if (this.geData == null) {
                ModLogger.error("Failed to get GE data for player auth=%d", playerAuth);
                throw new IllegalStateException("GE data not found");
            }
        } else {
            // Client-side: will be synced from server
            this.geData = null;
        }
        
        // Add sell slot (single slot where player places item to sell)
        this.addSlot(new ContainerSlot(client.getServerClient().playerMob.getInv().main, SELL_SLOT_INDEX) {
            @Override
            public String getItemInvalidError(necesse.inventory.InventoryItem item) {
                // Allow any valid item (null is OK for empty slot)
                if (item == null || item.item != null) {
                    return null;  // null means item is valid
                }
                return "";  // empty string means item is invalid
            }
        });
        
        // Register custom actions
        registerActions();
        
        // Load initial listings
        if (client.isServer()) {
            refreshCurrentListings();
        }
    }
    
    /**
     * Register custom actions for GE operations.
     */
    private void registerActions() {
        // Create listing action (x=quantity, y=pricePerItem)
        this.createListing = this.registerAction(new PointCustomAction() {
            @Override
            protected void run(int quantity, int pricePerItem) {
                if (client.isServer()) {
                    handleCreateListing(quantity, pricePerItem);
                }
            }
        });

        // Purchase listing action (x=listingID lower 32 bits, y=quantity)
        this.purchaseListing = this.registerAction(new PointCustomAction() {
            @Override
            protected void run(int listingIDLower, int quantity) {
                if (client.isServer()) {
                    // Treat listingIDLower as the full listingID (limits to 2^31-1 listings)
                    handlePurchaseListing((long)listingIDLower, quantity, false);
                }
            }
        });

        // Purchase to bank action (x=listingID lower 32 bits, y=quantity)
        this.purchaseToBank = this.registerAction(new PointCustomAction() {
            @Override
            protected void run(int listingIDLower, int quantity) {
                if (client.isServer()) {
                    handlePurchaseListing((long)listingIDLower, quantity, true);
                }
            }
        });

        // Cancel listing action (player cancels their own listing)
        this.cancelListing = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int listingIDLower) {
                if (client.isServer()) {
                    handleCancelListing((long)listingIDLower);
                }
            }
        });

        // Set filter action
        this.setFilter = this.registerAction(new StringCustomAction() {
            @Override
            protected void run(String value) {
                if (client.isServer()) {
                    currentFilter = value;
                    currentPage = 0;
                    refreshCurrentListings();
                }
            }
        });

        // Set page action
        this.setPage = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int page) {
                if (client.isServer()) {
                    currentPage = page;
                    refreshCurrentListings();
                }
            }
        });

        // Set sort mode action
        this.setSortMode = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int mode) {
                if (client.isServer()) {
                    sortMode = mode;
                    currentPage = 0; // Reset to first page when sorting changes
                    refreshCurrentListings();
                }
            }
        });

        // Set price filter action (x=minPrice, y=maxPrice)
        this.setPriceFilter = this.registerAction(new PointCustomAction() {
            @Override
            protected void run(int minPriceValue, int maxPriceValue) {
                if (client.isServer()) {
                    minPrice = Math.max(0, minPriceValue);
                    maxPrice = Math.max(0, maxPriceValue);
                    currentPage = 0;
                    refreshCurrentListings();
                    ModLogger.debug("Price filter set: min=%d, max=%d", minPrice, maxPrice);
                }
            }
        });

        // Set minimum quantity filter action
        this.setMinQuantityFilter = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int minQty) {
                if (client.isServer()) {
                    minQuantity = Math.max(0, minQty);
                    currentPage = 0;
                    refreshCurrentListings();
                    ModLogger.debug("Min quantity filter set: %d", minQuantity);
                }
            }
        });

        // Toggle player listings filter
        this.togglePlayerListings = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int value) {
                if (client.isServer()) {
                    showOnlyPlayerListings = (value == 1);
                    currentPage = 0;
                    refreshCurrentListings();
                    ModLogger.debug("Player listings filter: %s", showOnlyPlayerListings);
                }
            }
        });

        // Set category filter action
        this.setCategoryFilter = this.registerAction(new StringCustomAction() {
            @Override
            protected void run(String category) {
                if (client.isServer()) {
                    categoryFilter = category == null ? "" : category;
                    currentPage = 0;
                    refreshCurrentListings();
                    ModLogger.debug("Category filter set: %s", categoryFilter);
                }
            }
        });

        // Clear all filters action
        this.clearFilters = this.registerAction(new EmptyCustomAction() {
            @Override
            protected void run() {
                if (client.isServer()) {
                    currentFilter = "";
                    minPrice = 0;
                    maxPrice = 0;
                    minQuantity = 0;
                    showOnlyPlayerListings = false;
                    categoryFilter = "";
                    currentPage = 0;
                    refreshCurrentListings();
                    ModLogger.debug("All filters cleared");
                }
            }
        });

        // Refresh listings action
        this.refreshListings = this.registerAction(new EmptyCustomAction() {
            @Override
            protected void run() {
                if (client.isServer()) {
                    refreshCurrentListings();
                }
            }
        });
    }
    
    /**
     * Refresh the current listings based on all filters, sort mode, and page.
     */
    private void refreshCurrentListings() {
        if (geData == null) {
            return;
        }

        // Get all active listings
        List<MarketListing> allListings = geData.getAllListings();

        // Apply filters
        java.util.stream.Stream<MarketListing> stream = allListings.stream();

        // Item name filter
        if (!currentFilter.isEmpty()) {
            stream = stream.filter(l -> l.getItemStringID().toLowerCase().contains(currentFilter.toLowerCase()));
        }

        // Price range filter
        if (minPrice > 0) {
            stream = stream.filter(l -> l.getPricePerItem() >= minPrice);
        }
        if (maxPrice > 0) {
            stream = stream.filter(l -> l.getPricePerItem() <= maxPrice);
        }

        // Minimum quantity filter
        if (minQuantity > 0) {
            stream = stream.filter(l -> l.getQuantity() >= minQuantity);
        }

        // Player listings filter
        if (showOnlyPlayerListings) {
            stream = stream.filter(l -> l.getSellerAuth() == playerAuth);
        }

        // Category filter
        if (!categoryFilter.isEmpty()) {
            stream = stream.filter(l -> {
                necesse.inventory.item.Item item = necesse.engine.registries.ItemRegistry.getItem(l.getItemStringID());
                if (item == null) return false;

                // Get item's category
                necesse.inventory.item.ItemCategory itemCategory = necesse.inventory.item.ItemCategory.getItemsCategory(item);
                if (itemCategory == null) return false;

                // Check if item belongs to the filtered category or is a child of it
                return itemCategory.isOrHasParent(categoryFilter);
            });
        }

        // Apply sorting
        switch (sortMode) {
            case 1: // Price: Low to High
                stream = stream.sorted(java.util.Comparator.comparingInt(MarketListing::getPricePerItem));
                break;
            case 2: // Price: High to Low
                stream = stream.sorted(java.util.Comparator.comparingInt(MarketListing::getPricePerItem).reversed());
                break;
            case 3: // Quantity: High to Low
                stream = stream.sorted(java.util.Comparator.comparingInt(MarketListing::getQuantity).reversed());
                break;
            case 4: // Time Remaining: Least to Most
                stream = stream.sorted(java.util.Comparator.comparingLong(MarketListing::getExpirationTime));
                break;
            default: // No sorting (0)
                break;
        }

        currentListings = stream.collect(java.util.stream.Collectors.toList());

        ModLogger.debug("Refreshed GE listings: %d total, %d after filters (name=%s, price=%d-%d, minQty=%d, category=%s, playerOnly=%s), sort=%d",
            allListings.size(), currentListings.size(), currentFilter, minPrice, maxPrice, minQuantity, categoryFilter, showOnlyPlayerListings, sortMode);
    }

    /**
     * Get the current page of listings.
     * @return List of listings for the current page
     */
    public List<MarketListing> getCurrentPageListings() {
        int pageSize = ModConfig.GrandExchange.maxListingsPerPage;
        int startIndex = currentPage * pageSize;
        int endIndex = Math.min(startIndex + pageSize, currentListings.size());

        if (startIndex >= currentListings.size()) {
            return new ArrayList<>();
        }

        return new ArrayList<>(currentListings.subList(startIndex, endIndex));
    }

    /**
     * Get total number of pages.
     */
    public int getTotalPages() {
        int pageSize = ModConfig.GrandExchange.maxListingsPerPage;
        return (int) Math.ceil((double) currentListings.size() / pageSize);
    }

    /**
     * Get player's own active listings.
     */
    public List<MarketListing> getPlayerListings() {
        if (geData == null) {
            return new ArrayList<>();
        }
        return geData.getAllListings().stream()
            .filter(l -> l.getSellerAuth() == playerAuth)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Handle creating a new listing.
     */
    private void handleCreateListing(int quantity, int pricePerItem) {
        if (geData == null || client == null || !client.isServer()) {
            return;
        }

        // Get the item from the sell slot
        necesse.inventory.InventoryItem sellItem = client.getServerClient().playerMob.getInv().main.getItem(SELL_SLOT_INDEX);
        if (sellItem == null || sellItem.item == null) {
            ModLogger.warn("No item in sell slot");
            return;
        }

        // Validate quantity
        if (quantity <= 0 || quantity > sellItem.getAmount()) {
            ModLogger.warn("Invalid quantity: %d (have %d)", quantity, sellItem.getAmount());
            return;
        }

        // Validate price
        if (pricePerItem <= 0) {
            ModLogger.warn("Invalid price: %d", pricePerItem);
            return;
        }

        // Check max listings per player
        int currentListingCount = geData.getListingsBySeller(playerAuth).size();
        if (currentListingCount >= ModConfig.GrandExchange.maxListingsPerPlayer) {
            ModLogger.warn("Player has too many listings: %d/%d",
                currentListingCount, ModConfig.GrandExchange.maxListingsPerPlayer);
            return;
        }

        // Create the listing
        MarketListing listing = geData.createListing(
            client.getServerClient(),
            sellItem.item.getStringID(),
            quantity,
            pricePerItem
        );

        if (listing != null) {
            // Remove items from player's inventory
            client.getServerClient().playerMob.getInv().main.removeItems(
                client.getServerClient().getLevel(),
                client.getServerClient().playerMob,
                sellItem.item,
                quantity,
                "grandexchange_sell"
            );

            ModLogger.info("Created listing ID=%d for player auth=%d",
                listing.getListingID(), playerAuth);

            // Refresh listings
            refreshCurrentListings();
        }
    }

    /**
     * Handle purchasing from a listing.
     * @param listingID The listing ID to purchase from
     * @param quantity The quantity to purchase
     * @param sendToBank If true, send items to player's bank instead of inventory
     */
    private void handlePurchaseListing(long listingID, int quantity, boolean sendToBank) {
        if (geData == null || client == null || !client.isServer()) {
            return;
        }

        // Get the listing
        MarketListing listing = geData.getListing(listingID);
        if (listing == null || !listing.isActive()) {
            ModLogger.warn("Listing not found or inactive: %d", listingID);
            return;
        }

        // Validate quantity
        if (quantity <= 0 || quantity > listing.getQuantity()) {
            ModLogger.warn("Invalid purchase quantity: %d (available %d)",
                quantity, listing.getQuantity());
            return;
        }

        // Calculate total cost
        int totalCost = quantity * listing.getPricePerItem();

        // Check if player has enough coins
        necesse.inventory.item.Item coinItem = necesse.engine.registries.ItemRegistry.getItem("coin");
        int playerCoins = client.getServerClient().playerMob.getInv().main.getAmount(
            client.getServerClient().getLevel(),
            client.getServerClient().playerMob,
            coinItem,
            "grandexchange_buy"
        );

        if (playerCoins < totalCost) {
            ModLogger.warn("Player doesn't have enough coins: %d/%d", playerCoins, totalCost);
            return;
        }

        // Remove coins from player
        client.getServerClient().playerMob.getInv().main.removeItems(
            client.getServerClient().getLevel(),
            client.getServerClient().playerMob,
            coinItem,
            totalCost,
            "grandexchange_buy"
        );

        // Add item to player's inventory or bank
        necesse.inventory.InventoryItem purchasedItem = listing.getInventoryItem();
        if (purchasedItem != null) {
            purchasedItem.setAmount(quantity);

            if (sendToBank && ModConfig.Banking.enabled) {
                // Send to player's bank
                medievalsim.banking.BankingLevelData bankingData =
                    medievalsim.banking.BankingLevelData.getBankingData(client.getServerClient().getLevel());
                if (bankingData != null) {
                    medievalsim.banking.PlayerBank playerBank =
                        bankingData.getOrCreateBank(playerAuth);
                    boolean success = playerBank.getInventory().addItem(
                        client.getServerClient().getLevel(),
                        null,
                        purchasedItem,
                        "grandexchange_buy",
                        null
                    );
                    if (!success) {
                        ModLogger.warn("Failed to add item to bank (bank full?), adding to inventory instead");
                        // Fallback to inventory
                        client.getServerClient().playerMob.getInv().main.addItem(
                            client.getServerClient().getLevel(),
                            client.getServerClient().playerMob,
                            purchasedItem,
                            "grandexchange_buy",
                            null
                        );
                    }
                } else {
                    // Banking not available, add to inventory
                    client.getServerClient().playerMob.getInv().main.addItem(
                        client.getServerClient().getLevel(),
                        client.getServerClient().playerMob,
                        purchasedItem,
                        "grandexchange_buy",
                        null
                    );
                }
            } else {
                // Add to inventory
                client.getServerClient().playerMob.getInv().main.addItem(
                    client.getServerClient().getLevel(),
                    client.getServerClient().playerMob,
                    purchasedItem,
                    "grandexchange_buy",
                    null
                );
            }
        }

        // Send coins to seller's bank (if enabled)
        if (ModConfig.GrandExchange.saleProceedsToBank) {
            medievalsim.banking.BankingLevelData bankingData =
                medievalsim.banking.BankingLevelData.getBankingData(client.getServerClient().getLevel());
            if (bankingData != null) {
                medievalsim.banking.PlayerBank sellerBank =
                    bankingData.getOrCreateBank(listing.getSellerAuth());
                sellerBank.addCoins(totalCost);
            }
        }

        // Update listing quantity
        listing.reduceQuantity(quantity);

        // Record sale for price history
        geData.recordSale(listing.getItemStringID(), listing.getPricePerItem());

        ModLogger.info("Player auth=%d purchased %d x %s for %d coins from listing ID=%d",
            playerAuth, quantity, listing.getItemStringID(), totalCost, listingID);

        // Refresh listings
        refreshCurrentListings();
    }

    /**
     * Handle canceling a player's own listing.
     * Returns items to player's inventory.
     */
    private void handleCancelListing(long listingID) {
        if (geData == null || client == null || !client.isServer()) {
            return;
        }

        // Get the listing
        MarketListing listing = geData.getListing(listingID);
        if (listing == null) {
            ModLogger.warn("Listing not found: %d", listingID);
            return;
        }

        // Verify ownership
        if (listing.getSellerAuth() != playerAuth) {
            ModLogger.warn("Player auth=%d tried to cancel listing owned by auth=%d",
                playerAuth, listing.getSellerAuth());
            return;
        }

        // Return items to player's inventory
        necesse.inventory.InventoryItem returnedItem = listing.getInventoryItem();
        if (returnedItem != null && listing.getQuantity() > 0) {
            returnedItem.setAmount(listing.getQuantity());
            client.getServerClient().playerMob.getInv().main.addItem(
                client.getServerClient().getLevel(),
                client.getServerClient().playerMob,
                returnedItem,
                "grandexchange_cancel",
                null
            );
        }

        // Remove listing
        geData.removeListing(listingID);

        ModLogger.info("Player auth=%d canceled listing ID=%d (%d x %s)",
            playerAuth, listingID, listing.getQuantity(), listing.getItemStringID());

        // Refresh listings
        refreshCurrentListings();
    }
}

