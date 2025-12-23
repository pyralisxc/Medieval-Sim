package medievalsim.grandexchange.services;

import medievalsim.util.ModLogger;
import medievalsim.util.TimeConstants;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Performance monitoring and metrics collection for Grand Exchange.
 * 
 * Tracks:
 * - Trades per minute/hour
 * - Average trade execution time
 * - Active offers per item
 * - Coin velocity (economic indicator)
 * - Market health indicators
 * 
 * Pattern: Metrics collector with sliding windows.
 * Thread-safe using concurrent collections.
 */
public class PerformanceMetrics {
    
    // Trade timing tracking
    private final List<TradeEvent> recentTrades = Collections.synchronizedList(new ArrayList<>());
    private final int maxTradeHistory = 1000;
    
    // Per-item metrics
    private final Map<String, ItemMetrics> itemMetrics = new ConcurrentHashMap<>();
    
    // System metrics
    private long totalTrades = 0;
    private long totalCoinsTraded = 0;
    private long systemStartTime = System.currentTimeMillis();
    
    // Performance tracking
    private final List<Long> tradeDurations = Collections.synchronizedList(new ArrayList<>());
    private final int maxDurationSamples = 100;
    
    /**
     * Record a completed trade.
     */
    public void recordTrade(String itemID, int quantity, int pricePerItem, long executionTimeMs) {
        long now = System.currentTimeMillis();
        
        // Record trade event
        TradeEvent event = new TradeEvent(now, itemID, quantity, pricePerItem);
        synchronized (recentTrades) {
            recentTrades.add(event);
            if (recentTrades.size() > maxTradeHistory) {
                recentTrades.remove(0);
            }
        }
        
        // Update item metrics
        ItemMetrics metrics = itemMetrics.computeIfAbsent(itemID, ItemMetrics::new);
        metrics.recordTrade(quantity, pricePerItem);
        
        // Update system metrics
        totalTrades++;
        totalCoinsTraded += (long) quantity * pricePerItem;
        
        // Record execution time
        synchronized (tradeDurations) {
            tradeDurations.add(executionTimeMs);
            if (tradeDurations.size() > maxDurationSamples) {
                tradeDurations.remove(0);
            }
        }
    }
    
    /**
     * Record active offer count for an item.
     */
    public void recordActiveOffers(String itemID, int buyOrders, int sellOffers) {
        ItemMetrics metrics = itemMetrics.computeIfAbsent(itemID, ItemMetrics::new);
        metrics.updateActiveOffers(buyOrders, sellOffers);
    }
    
    /**
     * Get trades per minute (last 60 minutes).
     */
    public double getTradesPerMinute() {
        long now = System.currentTimeMillis();
        long oneHourAgo = now - (60 * 60 * 1000);
        
        synchronized (recentTrades) {
            long tradesLastHour = recentTrades.stream()
                .filter(t -> t.getTimestamp() >= oneHourAgo)
                .count();
            
            return tradesLastHour / 60.0;
        }
    }
    
    /**
     * Get trades per hour (last 24 hours).
     */
    public double getTradesPerHour() {
        long now = System.currentTimeMillis();
        long oneDayAgo = now - (24 * 60 * 60 * 1000);
        
        synchronized (recentTrades) {
            long tradesLastDay = recentTrades.stream()
                .filter(t -> t.getTimestamp() >= oneDayAgo)
                .count();
            
            return tradesLastDay / 24.0;
        }
    }
    
    /**
     * Get average trade execution time (milliseconds).
     */
    public double getAverageExecutionTime() {
        synchronized (tradeDurations) {
            if (tradeDurations.isEmpty()) {
                return 0;
            }
            return tradeDurations.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
        }
    }
    
    /**
     * Get median trade execution time (milliseconds).
     */
    public long getMedianExecutionTime() {
        synchronized (tradeDurations) {
            if (tradeDurations.isEmpty()) {
                return 0;
            }
            List<Long> sorted = new ArrayList<>(tradeDurations);
            sorted.sort(Long::compareTo);
            return sorted.get(sorted.size() / 2);
        }
    }
    
    /**
     * Get coin velocity (coins traded per hour).
     * Higher velocity = more active economy.
     */
    public double getCoinVelocity() {
        long now = System.currentTimeMillis();
        long oneHourAgo = now - TimeConstants.MILLIS_PER_HOUR;
        
        synchronized (recentTrades) {
            long coinsLastHour = recentTrades.stream()
                .filter(t -> t.getTimestamp() >= oneHourAgo)
                .mapToLong(t -> (long) t.getQuantity() * t.getPricePerItem())
                .sum();
            
            return coinsLastHour;
        }
    }
    
    /**
     * Get market health score (0-100).
     * Based on trade volume, velocity, and active offers.
     */
    public int getMarketHealthScore() {
        double tradesPerMin = getTradesPerMinute();
        double coinVelocity = getCoinVelocity();
        int activeItems = itemMetrics.size();
        
        // Simple scoring: weight various factors
        double tradeScore = Math.min(tradesPerMin * 10, 40); // Max 40 points
        double velocityScore = Math.min(coinVelocity / 10000, 30); // Max 30 points
        double diversityScore = Math.min(activeItems * 2, 30); // Max 30 points
        
        return (int) (tradeScore + velocityScore + diversityScore);
    }
    
    /**
     * Get metrics for a specific item.
     */
    public ItemMetrics getItemMetrics(String itemID) {
        return itemMetrics.get(itemID);
    }
    
    /**
     * Get top traded items (by volume).
     */
    public List<String> getTopTradedItems(int limit) {
        return itemMetrics.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().getTotalVolume(), a.getValue().getTotalVolume()))
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Get comprehensive market report.
     */
    public MarketReport getMarketReport() {
        return new MarketReport(
            totalTrades,
            totalCoinsTraded,
            getTradesPerMinute(),
            getTradesPerHour(),
            getAverageExecutionTime(),
            getCoinVelocity(),
            getMarketHealthScore(),
            itemMetrics.size(),
            System.currentTimeMillis() - systemStartTime
        );
    }
    
    /**
     * Cleanup old data (called periodically).
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        long cutoff = now - TimeConstants.MILLIS_PER_DAY; // 24 hours
        
        synchronized (recentTrades) {
            int before = recentTrades.size();
            recentTrades.removeIf(t -> t.getTimestamp() < cutoff);
            int removed = before - recentTrades.size();
            
            if (removed > 0) {
                ModLogger.debug("Performance metrics cleanup: removed %d old trade events", removed);
            }
        }
    }
    
    // ===== NESTED CLASSES =====
    
    /**
     * Trade event for time-series analysis.
     */
    private static class TradeEvent {
        private final long timestamp;
        private final String itemID;
        private final int quantity;
        private final int pricePerItem;
        
        public TradeEvent(long timestamp, String itemID, int quantity, int pricePerItem) {
            this.timestamp = timestamp;
            this.itemID = itemID;
            this.quantity = quantity;
            this.pricePerItem = pricePerItem;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public String getItemID() {
            return itemID;
        }
        
        public int getQuantity() {
            return quantity;
        }
        
        public int getPricePerItem() {
            return pricePerItem;
        }
    }
    
    /**
     * Per-item metrics.
     */
    public static class ItemMetrics {
        private final String itemID;
        private long tradeCount = 0;
        private long totalVolume = 0; // Total quantity traded
        private long totalValue = 0;  // Total coins traded
        private int activeBuyOrders = 0;
        private int activeSellOffers = 0;
        
        public ItemMetrics(String itemID) {
            this.itemID = itemID;
        }
        
        public void recordTrade(int quantity, int pricePerItem) {
            tradeCount++;
            totalVolume += quantity;
            totalValue += (long) quantity * pricePerItem;
        }
        
        public void updateActiveOffers(int buyOrders, int sellOffers) {
            this.activeBuyOrders = buyOrders;
            this.activeSellOffers = sellOffers;
        }
        
        public String getItemID() {
            return itemID;
        }
        
        public long getTradeCount() {
            return tradeCount;
        }
        
        public long getTotalVolume() {
            return totalVolume;
        }
        
        public long getTotalValue() {
            return totalValue;
        }
        
        public int getActiveBuyOrders() {
            return activeBuyOrders;
        }
        
        public int getActiveSellOffers() {
            return activeSellOffers;
        }
        
        public double getAveragePrice() {
            return totalVolume > 0 ? (double) totalValue / totalVolume : 0;
        }
    }
    
    /**
     * Comprehensive market report.
     */
    public static class MarketReport {
        private final long totalTrades;
        private final long totalCoins;
        private final double tradesPerMinute;
        private final double tradesPerHour;
        private final double avgExecutionTime;
        private final double coinVelocity;
        private final int marketHealth;
        private final int activeItems;
        private final long uptime;
        
        public MarketReport(long totalTrades, long totalCoins, double tradesPerMinute,
                           double tradesPerHour, double avgExecutionTime, double coinVelocity,
                           int marketHealth, int activeItems, long uptime) {
            this.totalTrades = totalTrades;
            this.totalCoins = totalCoins;
            this.tradesPerMinute = tradesPerMinute;
            this.tradesPerHour = tradesPerHour;
            this.avgExecutionTime = avgExecutionTime;
            this.coinVelocity = coinVelocity;
            this.marketHealth = marketHealth;
            this.activeItems = activeItems;
            this.uptime = uptime;
        }
        
        // Getters
        public long getTotalTrades() { return totalTrades; }
        public long getTotalCoins() { return totalCoins; }
        public double getTradesPerMinute() { return tradesPerMinute; }
        public double getTradesPerHour() { return tradesPerHour; }
        public double getAvgExecutionTime() { return avgExecutionTime; }
        public double getCoinVelocity() { return coinVelocity; }
        public int getMarketHealth() { return marketHealth; }
        public int getActiveItems() { return activeItems; }
        public long getUptime() { return uptime; }
        
        @Override
        public String toString() {
            return String.format(
                "Market Report:\n" +
                "  Total Trades: %d\n" +
                "  Total Coins: %d\n" +
                "  Trades/Min: %.2f\n" +
                "  Trades/Hour: %.2f\n" +
                "  Avg Exec Time: %.2fms\n" +
                "  Coin Velocity: %.0f/hr\n" +
                "  Market Health: %d/100\n" +
                "  Active Items: %d\n" +
                "  Uptime: %dh",
                totalTrades, totalCoins, tradesPerMinute, tradesPerHour,
                avgExecutionTime, coinVelocity, marketHealth, activeItems,
                uptime / TimeConstants.MILLIS_PER_HOUR
            );
        }
    }
}
