package medievalsim.grandexchange.services;

import medievalsim.grandexchange.domain.BuyOrder;
import medievalsim.grandexchange.domain.GEOffer;
import medievalsim.util.ModLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance order book for Grand Exchange item matching.
 * 
 * Architecture:
 * - Buy orders: Max-heap (highest price first, FIFO for same price)
 * - Sell offers: Min-heap (lowest price first, FIFO for same price)
 * - O(log n) insertion, O(1) best price lookup, O(log n) matching
 * 
 * Thread-safety: Uses concurrent collections for multi-threaded server access.
 * 
 * Pattern: Similar to financial order book systems (Limit Order Book).
 */
public class OrderBook {
    
    private final String itemStringID;
    
    // Buy side: Max-heap ordered by price DESC, then creation time ASC (FIFO)
    private final PriorityQueue<BuyOrder> buyOrders;
    
    // Sell side: Min-heap ordered by price ASC, then creation time ASC (FIFO)
    private final PriorityQueue<GEOffer> sellOffers;
    
    // Quick lookup maps for order/offer retrieval
    private final Map<Long, BuyOrder> buyOrdersById;
    private final Map<Long, GEOffer> sellOffersById;
    
    // Statistics
    private int totalMatches;
    private long lastMatchTime;
    
    /**
     * Create order book for specific item.
     */
    public OrderBook(String itemStringID) {
        this.itemStringID = itemStringID;
        
        // Buy orders: Higher price = higher priority, earlier time = higher priority
        this.buyOrders = new PriorityQueue<>(new Comparator<BuyOrder>() {
            @Override
            public int compare(BuyOrder o1, BuyOrder o2) {
                // Price comparison (DESC - higher price first)
                int priceCompare = Integer.compare(o2.getPricePerItem(), o1.getPricePerItem());
                if (priceCompare != 0) {
                    return priceCompare;
                }
                // Time comparison (ASC - earlier first for FIFO)
                return Long.compare(o1.getCreationTime(), o2.getCreationTime());
            }
        });
        
        // Sell offers: Lower price = higher priority, earlier time = higher priority
        this.sellOffers = new PriorityQueue<>(new Comparator<GEOffer>() {
            @Override
            public int compare(GEOffer o1, GEOffer o2) {
                // Price comparison (ASC - lower price first)
                int priceCompare = Integer.compare(o1.getPricePerItem(), o2.getPricePerItem());
                if (priceCompare != 0) {
                    return priceCompare;
                }
                // Time comparison (ASC - earlier first for FIFO)
                return Long.compare(o1.getCreatedTime(), o2.getCreatedTime());
            }
        });
        
        this.buyOrdersById = new ConcurrentHashMap<>();
        this.sellOffersById = new ConcurrentHashMap<>();
        
        this.totalMatches = 0;
        this.lastMatchTime = 0L;
    }
    
    // ===== BUY ORDER OPERATIONS =====
    
    /**
     * Add buy order to the book.
     * O(log n) complexity.
     */
    public synchronized void addBuyOrder(BuyOrder order) {
        if (!order.getItemStringID().equals(itemStringID)) {
            ModLogger.error("Cannot add buy order for %s to order book for %s",
                order.getItemStringID(), itemStringID);
            return;
        }
        
        if (!order.canMatch()) {
            ModLogger.warn("Buy order ID=%d is not in matchable state", order.getOrderID());
            return;
        }
        
        // Prevent duplicate entries by order ID
        if (buyOrdersById.containsKey(order.getOrderID())) {
            ModLogger.debug("Buy order ID=%d already present in order book for %s, skipping add",
                order.getOrderID(), itemStringID);
            return;
        }

        buyOrders.offer(order);
        buyOrdersById.put(order.getOrderID(), order);
        
        ModLogger.debug("Added buy order ID=%d to order book for %s (price=%d, qty=%d)",
            order.getOrderID(), itemStringID, order.getPricePerItem(), order.getQuantityRemaining());
    }
    
    /**
     * Remove buy order from the book.
     * O(n) complexity (heap removal by value).
     */
    public synchronized boolean removeBuyOrder(long orderID) {
        BuyOrder order = buyOrdersById.remove(orderID);
        if (order == null) {
            return false;
        }
        
        boolean removed = buyOrders.remove(order);
        ModLogger.debug("Removed buy order ID=%d from order book for %s", orderID, itemStringID);
        return removed;
    }
    
    /**
     * Get best (highest price) buy order.
     * O(1) complexity.
     */
    public synchronized BuyOrder getBestBuyOrder() {
        return buyOrders.peek();
    }
    
    /**
     * Get all buy orders sorted by priority.
     */
    public synchronized List<BuyOrder> getAllBuyOrders() {
        return new ArrayList<>(buyOrders);
    }
    
    // ===== SELL OFFER OPERATIONS =====
    
    /**
     * Add sell offer to the book.
     * O(log n) complexity.
     */
    public synchronized void addSellOffer(GEOffer offer) {
        if (!offer.getItemStringID().equals(itemStringID)) {
            ModLogger.error("Cannot add sell offer for %s to order book for %s",
                offer.getItemStringID(), itemStringID);
            return;
        }
        
        if (!offer.isActive() || offer.getQuantityRemaining() <= 0) {
            ModLogger.warn("Sell offer ID=%d is not in matchable state", offer.getOfferID());
            return;
        }
        
        // Prevent duplicate entries by offer ID
        if (sellOffersById.containsKey(offer.getOfferID())) {
            ModLogger.debug("Sell offer ID=%d already present in order book for %s, skipping add",
                offer.getOfferID(), itemStringID);
            return;
        }

        sellOffers.offer(offer);
        sellOffersById.put(offer.getOfferID(), offer);
        
        ModLogger.debug("Added sell offer ID=%d to order book for %s (price=%d, qty=%d)",
            offer.getOfferID(), itemStringID, offer.getPricePerItem(), offer.getQuantityRemaining());
    }
    
    /**
     * Remove sell offer from the book.
     * O(n) complexity (heap removal by value).
     */
    public synchronized boolean removeSellOffer(long offerID) {
        GEOffer offer = sellOffersById.remove(offerID);
        if (offer == null) {
            return false;
        }
        
        boolean removed = sellOffers.remove(offer);
        ModLogger.debug("Removed sell offer ID=%d from order book for %s", offerID, itemStringID);
        return removed;
    }
    
    /**
     * Get best (lowest price) sell offer.
     * O(1) complexity.
     */
    public synchronized GEOffer getBestSellOffer() {
        return sellOffers.peek();
    }
    
    /**
     * Get all sell offers sorted by priority.
     */
    public synchronized List<GEOffer> getAllSellOffers() {
        return new ArrayList<>(sellOffers);
    }
    
    // ===== MATCHING OPERATIONS =====
    
    /**
     * Find all compatible matches between a buy order and sell offers.
     * Returns list of (sellOffer, quantity) pairs that can be matched.
     */
    public synchronized List<Match> findMatchesForBuyOrder(BuyOrder buyOrder) {
        List<Match> matches = new ArrayList<>();
        
        if (!buyOrder.canMatch()) {
            return matches;
        }
        
        int remainingQty = buyOrder.getQuantityRemaining();
        
        // Iterate through sell offers (lowest price first)
        for (GEOffer sellOffer : sellOffers) {
            if (remainingQty <= 0) {
                break;
            }
            
            // Check if prices are compatible (buy price >= sell price)
            if (buyOrder.getPricePerItem() < sellOffer.getPricePerItem()) {
                break; // No more compatible offers (heap is sorted)
            }
            
            // Check if both can still trade
            if (!sellOffer.isActive() || sellOffer.getQuantityRemaining() <= 0) {
                continue;
            }
            
            // Calculate trade quantity
            int tradeQty = Math.min(remainingQty, sellOffer.getQuantityRemaining());
            
            matches.add(new Match(sellOffer, tradeQty, buyOrder.getPricePerItem()));
            remainingQty -= tradeQty;
        }
        
        return matches;
    }
    
    /**
     * Find all compatible matches between a sell offer and buy orders.
     * Returns list of (buyOrder, quantity) pairs that can be matched.
     */
    public synchronized List<Match> findMatchesForSellOffer(GEOffer sellOffer) {
        List<Match> matches = new ArrayList<>();
        
        if (!sellOffer.isActive() || sellOffer.getQuantityRemaining() <= 0) {
            return matches;
        }
        
        int remainingQty = sellOffer.getQuantityRemaining();
        
        // Iterate through buy orders (highest price first)
        for (BuyOrder buyOrder : buyOrders) {
            if (remainingQty <= 0) {
                break;
            }
            
            // Check if prices are compatible (buy price >= sell price)
            if (buyOrder.getPricePerItem() < sellOffer.getPricePerItem()) {
                break; // No more compatible orders (heap is sorted)
            }
            
            // Check if both can still trade
            if (!buyOrder.canMatch()) {
                continue;
            }
            
            // Calculate trade quantity
            int tradeQty = Math.min(remainingQty, buyOrder.getQuantityRemaining());
            
            matches.add(new Match(sellOffer, buyOrder, tradeQty, buyOrder.getPricePerItem()));
            remainingQty -= tradeQty;
        }
        
        return matches;
    }
    
    /**
     * Rebuild heaps after external modifications.
     * Call this if order/offer quantities change externally.
     * O(n log n) complexity.
     */
    public synchronized void rebuild() {
        // Remove completed/invalid orders
        buyOrders.removeIf(order -> !order.canMatch());
        sellOffers.removeIf(offer -> !offer.isActive() || offer.getQuantityRemaining() <= 0);
        
        // Rebuild lookup maps
        buyOrdersById.clear();
        for (BuyOrder order : buyOrders) {
            buyOrdersById.put(order.getOrderID(), order);
        }
        
        sellOffersById.clear();
        for (GEOffer offer : sellOffers) {
            sellOffersById.put(offer.getOfferID(), offer);
        }
        
        ModLogger.debug("Rebuilt order book for %s: %d buy orders, %d sell offers",
            itemStringID, buyOrders.size(), sellOffers.size());
    }
    
    // ===== MARKET DEPTH =====
    
    /**
     * Get market depth (aggregated quantity at each price level).
     * Used for visualization and analytics.
     */
    public synchronized MarketDepth getMarketDepth() {
        Map<Integer, Integer> buyDepth = new TreeMap<>(Collections.reverseOrder()); // DESC
        Map<Integer, Integer> sellDepth = new TreeMap<>(); // ASC
        
        // Aggregate buy orders by price
        for (BuyOrder order : buyOrders) {
            int price = order.getPricePerItem();
            int qty = order.getQuantityRemaining();
            buyDepth.merge(price, qty, Integer::sum);
        }
        
        // Aggregate sell offers by price
        for (GEOffer offer : sellOffers) {
            int price = offer.getPricePerItem();
            int qty = offer.getQuantityRemaining();
            sellDepth.merge(price, qty, Integer::sum);
        }
        
        return new MarketDepth(buyDepth, sellDepth);
    }
    
    // ===== STATISTICS =====
    
    public String getItemStringID() {
        return itemStringID;
    }
    
    public synchronized int getBuyOrderCount() {
        return buyOrders.size();
    }
    
    public synchronized int getSellOfferCount() {
        return sellOffers.size();
    }
    
    public int getTotalMatches() {
        return totalMatches;
    }
    
    public long getLastMatchTime() {
        return lastMatchTime;
    }
    
    public synchronized boolean isEmpty() {
        return buyOrders.isEmpty() && sellOffers.isEmpty();
    }
    
    synchronized void recordMatch() {
        totalMatches++;
        lastMatchTime = System.currentTimeMillis();
    }
    
    // ===== NESTED CLASSES =====
    
    /**
     * Represents a potential match between buy and sell.
     */
    public static class Match {
        private final GEOffer sellOffer;
        private final BuyOrder buyOrder;
        private final int quantity;
        private final int executionPrice; // Price at which trade will execute
        
        // Match for buy order (finding sell offers)
        public Match(GEOffer sellOffer, int quantity, int executionPrice) {
            this.sellOffer = sellOffer;
            this.buyOrder = null;
            this.quantity = quantity;
            this.executionPrice = executionPrice;
        }
        
        // Match for sell offer (finding buy orders)
        public Match(GEOffer sellOffer, BuyOrder buyOrder, int quantity, int executionPrice) {
            this.sellOffer = sellOffer;
            this.buyOrder = buyOrder;
            this.quantity = quantity;
            this.executionPrice = executionPrice;
        }
        
        public GEOffer getSellOffer() {
            return sellOffer;
        }
        
        public BuyOrder getBuyOrder() {
            return buyOrder;
        }
        
        public int getQuantity() {
            return quantity;
        }
        
        public int getExecutionPrice() {
            return executionPrice;
        }
        
        public int getTotalValue() {
            return quantity * executionPrice;
        }
    }
    
    /**
     * Market depth snapshot (quantity at each price level).
     */
    public static class MarketDepth {
        private final Map<Integer, Integer> buyDepth;  // Price -> Total Quantity
        private final Map<Integer, Integer> sellDepth; // Price -> Total Quantity
        
        public MarketDepth(Map<Integer, Integer> buyDepth, Map<Integer, Integer> sellDepth) {
            this.buyDepth = buyDepth;
            this.sellDepth = sellDepth;
        }
        
        public Map<Integer, Integer> getBuyDepth() {
            return buyDepth;
        }
        
        public Map<Integer, Integer> getSellDepth() {
            return sellDepth;
        }
        
        public int getBestBuyPrice() {
            return buyDepth.isEmpty() ? 0 : buyDepth.keySet().iterator().next();
        }
        
        public int getBestSellPrice() {
            return sellDepth.isEmpty() ? 0 : sellDepth.keySet().iterator().next();
        }
        
        public int getSpread() {
            if (buyDepth.isEmpty() || sellDepth.isEmpty()) {
                return 0;
            }
            return getBestSellPrice() - getBestBuyPrice();
        }
        
        public int getTotalBuyVolume() {
            return buyDepth.values().stream().mapToInt(Integer::intValue).sum();
        }
        
        public int getTotalSellVolume() {
            return sellDepth.values().stream().mapToInt(Integer::intValue).sum();
        }
    }
}
