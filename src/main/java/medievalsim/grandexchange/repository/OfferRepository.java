package medievalsim.grandexchange.repository;

import medievalsim.grandexchange.domain.GEOffer;
import medievalsim.grandexchange.domain.BuyOrder;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Grand Exchange offers and orders.
 * 
 * Pattern: Repository pattern for data access abstraction.
 * 
 * Benefits:
 * - Decouples business logic from data storage
 * - Enables easy testing with mock implementations
 * - Allows future migration to database without changing business logic
 * - Provides consistent API for data access
 * - Enables caching layer addition
 */
public interface OfferRepository {
    
    // ===== SELL OFFER OPERATIONS =====
    
    /**
     * Save or update a sell offer.
     * @return The saved offer
     */
    GEOffer saveSellOffer(GEOffer offer);
    
    /**
     * Find sell offer by ID.
     * @return Optional containing the offer, or empty if not found
     */
    Optional<GEOffer> findSellOfferById(long offerID);
    
    /**
     * Find all active sell offers for an item.
     * @return List of active offers, sorted by price (lowest first)
     */
    List<GEOffer> findActiveSellOffersByItem(String itemID);
    
    /**
     * Find all sell offers for a player.
     * @return List of player's offers (all states)
     */
    List<GEOffer> findSellOffersByPlayer(long playerAuth);
    
    /**
     * Find all active sell offers.
     * @return List of all active offers
     */
    List<GEOffer> findAllActiveSellOffers();
    
    /**
     * Delete a sell offer.
     * @return true if deleted, false if not found
     */
    boolean deleteSellOffer(long offerID);
    
    /**
     * Count active sell offers for an item.
     */
    int countActiveSellOffersForItem(String itemID);
    
    // ===== BUY ORDER OPERATIONS =====
    
    /**
     * Save or update a buy order.
     * @return The saved order
     */
    BuyOrder saveBuyOrder(BuyOrder order);
    
    /**
     * Find buy order by ID.
     * @return Optional containing the order, or empty if not found
     */
    Optional<BuyOrder> findBuyOrderById(long orderID);
    
    /**
     * Find all active buy orders for an item.
     * @return List of active orders, sorted by price (highest first)
     */
    List<BuyOrder> findActiveBuyOrdersByItem(String itemID);
    
    /**
     * Find all buy orders for a player.
     * @return List of player's orders (all states)
     */
    List<BuyOrder> findBuyOrdersByPlayer(long playerAuth);
    
    /**
     * Find all active buy orders.
     * @return List of all active orders
     */
    List<BuyOrder> findAllActiveBuyOrders();
    
    /**
     * Delete a buy order.
     * @return true if deleted, false if not found
     */
    boolean deleteBuyOrder(long orderID);
    
    /**
     * Count active buy orders for an item.
     */
    int countActiveBuyOrdersForItem(String itemID);
    
    // ===== UTILITY OPERATIONS =====
    
    /**
     * Count total active offers (buy + sell).
     */
    int countTotalActiveOffers();
    
    /**
     * Get all items with active offers or orders.
     */
    List<String> findAllActiveItems();
    
    /**
     * Clear all offers and orders (admin function).
     */
    void clearAll();
}
