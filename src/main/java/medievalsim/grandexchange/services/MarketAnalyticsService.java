package medievalsim.grandexchange.services;

import medievalsim.grandexchange.services.TradeTransaction.TradeResult;
import medievalsim.util.ModLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Market analytics and price discovery service.
 * 
 * Features:
 * - Guide price calculation (median of recent trades)
 * - Volume-Weighted Average Price (VWAP)
 * - 24-hour high/low tracking
 * - Price volatility metrics
 * - Trade history management
 * 
 * Pattern: Service layer for market intelligence.
 * Thread-safe using concurrent collections.
 */
public class MarketAnalyticsService {
    
    // Trade history per item (limited to last N trades)
    private final Map<String, List<TradeRecord>> tradeHistory;
    private final int maxTradesPerItem;
    
    // 24-hour price tracking
    private final Map<String, PriceRange> dailyRanges;
    private final long TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L;
    
    // Statistics
    private long totalTradesRecorded = 0;
    private long totalVolumeTraded = 0;
    
    /**
     * Create analytics service.
     * @param maxTradesPerItem Maximum trades to keep in history per item (default: 100)
     */
    public MarketAnalyticsService(int maxTradesPerItem) {
        this.maxTradesPerItem = maxTradesPerItem;
        this.tradeHistory = new ConcurrentHashMap<>();
        this.dailyRanges = new ConcurrentHashMap<>();
    }
    
    /**
     * Record a completed trade.
     */
    public void recordTrade(TradeResult trade) {
        String itemID = trade.getItemStringID();
        int price = trade.getPricePerItem();
        int quantity = trade.getQuantity();
        long timestamp = trade.getTimestamp();
        
        // Add to trade history
        List<TradeRecord> history = tradeHistory.computeIfAbsent(itemID, k -> new ArrayList<>());
        synchronized (history) {
            history.add(new TradeRecord(price, quantity, timestamp));
            
            // Trim if exceeds max
            if (history.size() > maxTradesPerItem) {
                history.remove(0); // Remove oldest
            }
        }
        
        // Update 24-hour range
        PriceRange range = dailyRanges.computeIfAbsent(itemID, k -> new PriceRange());
        synchronized (range) {
            range.update(price, timestamp);
        }
        
        // Update global statistics
        totalTradesRecorded++;
        totalVolumeTraded += (long) price * quantity;
        
        ModLogger.debug("Recorded trade: %s x%d @ %d coins (total trades: %d)",
            itemID, quantity, price, totalTradesRecorded);
    }
    
    /**
     * Get guide price (recommended price based on median of recent trades).
     * More stable than average, resistant to outliers.
     */
    public int getGuidePrice(String itemID) {
        List<TradeRecord> history = tradeHistory.get(itemID);
        if (history == null || history.isEmpty()) {
            return 0;
        }
        
        synchronized (history) {
            List<Integer> prices = history.stream()
                .map(TradeRecord::getPrice)
                .sorted()
                .collect(Collectors.toList());
            
            int size = prices.size();
            if (size == 0) return 0;
            
            // Calculate median
            if (size % 2 == 0) {
                return (prices.get(size / 2 - 1) + prices.get(size / 2)) / 2;
            } else {
                return prices.get(size / 2);
            }
        }
    }
    
    /**
     * Get Volume-Weighted Average Price (VWAP).
     * Weights prices by trade volume - more accurate for high-volume items.
     */
    public int getVWAP(String itemID) {
        List<TradeRecord> history = tradeHistory.get(itemID);
        if (history == null || history.isEmpty()) {
            return 0;
        }
        
        synchronized (history) {
            long totalValue = 0;
            long totalVolume = 0;
            
            for (TradeRecord trade : history) {
                totalValue += (long) trade.getPrice() * trade.getQuantity();
                totalVolume += trade.getQuantity();
            }
            
            return totalVolume > 0 ? (int) (totalValue / totalVolume) : 0;
        }
    }
    
    /**
     * Get simple average price (mean of recent trades).
     * Less accurate than VWAP or guide price, but fast to calculate.
     */
    public int getAveragePrice(String itemID) {
        List<TradeRecord> history = tradeHistory.get(itemID);
        if (history == null || history.isEmpty()) {
            return 0;
        }
        
        synchronized (history) {
            int sum = history.stream().mapToInt(TradeRecord::getPrice).sum();
            return sum / history.size();
        }
    }
    
    /**
     * Get 24-hour high price.
     */
    public int get24hHigh(String itemID) {
        PriceRange range = dailyRanges.get(itemID);
        return range != null ? range.getHigh() : 0;
    }
    
    /**
     * Get 24-hour low price.
     */
    public int get24hLow(String itemID) {
        PriceRange range = dailyRanges.get(itemID);
        return range != null ? range.getLow() : 0;
    }
    
    /**
     * Get 24-hour price range.
     */
    public PriceRange get24hRange(String itemID) {
        return dailyRanges.getOrDefault(itemID, new PriceRange());
    }
    
    /**
     * Get price volatility (standard deviation of recent prices).
     * Higher value = more volatile/unstable market.
     */
    public double getPriceVolatility(String itemID) {
        List<TradeRecord> history = tradeHistory.get(itemID);
        if (history == null || history.size() < 2) {
            return 0.0;
        }
        
        synchronized (history) {
            // Calculate mean
            double mean = history.stream()
                .mapToInt(TradeRecord::getPrice)
                .average()
                .orElse(0.0);
            
            // Calculate variance
            double variance = history.stream()
                .mapToDouble(t -> Math.pow(t.getPrice() - mean, 2))
                .average()
                .orElse(0.0);
            
            // Return standard deviation
            return Math.sqrt(variance);
        }
    }
    
    /**
     * Get total trade volume (quantity) for an item in last N trades.
     */
    public int getTradeVolume(String itemID) {
        List<TradeRecord> history = tradeHistory.get(itemID);
        if (history == null) {
            return 0;
        }
        
        synchronized (history) {
            return history.stream().mapToInt(TradeRecord::getQuantity).sum();
        }
    }
    
    /**
     * Get number of trades recorded for an item.
     */
    public int getTradeCount(String itemID) {
        List<TradeRecord> history = tradeHistory.get(itemID);
        return history != null ? history.size() : 0;
    }
    
    /**
     * Get all trade history for an item (immutable copy).
     */
    public List<TradeRecord> getTradeHistory(String itemID) {
        List<TradeRecord> history = tradeHistory.get(itemID);
        if (history == null) {
            return Collections.emptyList();
        }
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }
    
    /**
     * Get market summary for an item.
     */
    public MarketSummary getMarketSummary(String itemID) {
        return new MarketSummary(
            itemID,
            getGuidePrice(itemID),
            getVWAP(itemID),
            getAveragePrice(itemID),
            get24hHigh(itemID),
            get24hLow(itemID),
            getTradeVolume(itemID),
            getTradeCount(itemID),
            getPriceVolatility(itemID)
        );
    }
    
    /**
     * Clean up old data (called periodically).
     * Removes 24h ranges older than 24 hours.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        long cutoff = now - TWENTY_FOUR_HOURS_MS;
        
        int removed = 0;
        for (Map.Entry<String, PriceRange> entry : dailyRanges.entrySet()) {
            PriceRange range = entry.getValue();
            synchronized (range) {
                if (range.getLastUpdate() < cutoff) {
                    dailyRanges.remove(entry.getKey());
                    removed++;
                }
            }
        }
        
        if (removed > 0) {
            ModLogger.debug("Cleaned up %d expired 24h price ranges", removed);
        }
    }
    
    // ===== STATISTICS =====
    
    public long getTotalTradesRecorded() {
        return totalTradesRecorded;
    }
    
    public long getTotalVolumeTraded() {
        return totalVolumeTraded;
    }
    
    public int getTrackedItemCount() {
        return tradeHistory.size();
    }
    
    // ===== NESTED CLASSES =====
    
    /**
     * Individual trade record.
     */
    public static class TradeRecord {
        private final int price;
        private final int quantity;
        private final long timestamp;
        
        public TradeRecord(int price, int quantity, long timestamp) {
            this.price = price;
            this.quantity = quantity;
            this.timestamp = timestamp;
        }
        
        public int getPrice() {
            return price;
        }
        
        public int getQuantity() {
            return quantity;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
    
    /**
     * 24-hour price range tracker.
     */
    public static class PriceRange {
        private int high = 0;
        private int low = Integer.MAX_VALUE;
        private long lastUpdate = 0;
        
        public synchronized void update(int price, long timestamp) {
            // Reset if older than 24h
            long now = System.currentTimeMillis();
            if (lastUpdate > 0 && (now - lastUpdate) > 24 * 60 * 60 * 1000L) {
                high = price;
                low = price;
            } else {
                if (price > high) high = price;
                if (price < low) low = price;
            }
            lastUpdate = timestamp;
        }
        
        public int getHigh() {
            return high == 0 ? 0 : high;
        }
        
        public int getLow() {
            return low == Integer.MAX_VALUE ? 0 : low;
        }
        
        public long getLastUpdate() {
            return lastUpdate;
        }
        
        public int getSpread() {
            return getHigh() - getLow();
        }
        
        public boolean isValid() {
            return high > 0 && low < Integer.MAX_VALUE;
        }
    }
    
    /**
     * Comprehensive market summary.
     */
    public static class MarketSummary {
        private final String itemID;
        private final int guidePrice;
        private final int vwap;
        private final int averagePrice;
        private final int high24h;
        private final int low24h;
        private final int tradeVolume;
        private final int tradeCount;
        private final double volatility;
        
        public MarketSummary(String itemID, int guidePrice, int vwap, int averagePrice,
                            int high24h, int low24h, int tradeVolume, int tradeCount, double volatility) {
            this.itemID = itemID;
            this.guidePrice = guidePrice;
            this.vwap = vwap;
            this.averagePrice = averagePrice;
            this.high24h = high24h;
            this.low24h = low24h;
            this.tradeVolume = tradeVolume;
            this.tradeCount = tradeCount;
            this.volatility = volatility;
        }
        
        // Getters
        public String getItemID() { return itemID; }
        public int getGuidePrice() { return guidePrice; }
        public int getVWAP() { return vwap; }
        public int getAveragePrice() { return averagePrice; }
        public int getHigh24h() { return high24h; }
        public int getLow24h() { return low24h; }
        public int getTradeVolume() { return tradeVolume; }
        public int getTradeCount() { return tradeCount; }
        public double getVolatility() { return volatility; }
        
        public int get24hSpread() {
            return high24h - low24h;
        }
        
        public boolean hasTradeHistory() {
            return tradeCount > 0;
        }
        
        @Override
        public String toString() {
            return String.format("Market[%s: guide=%d, vwap=%d, 24h=%d-%d, vol=%d, trades=%d]",
                itemID, guidePrice, vwap, low24h, high24h, tradeVolume, tradeCount);
        }
    }
}
