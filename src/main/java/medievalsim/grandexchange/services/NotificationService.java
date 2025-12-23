package medievalsim.grandexchange.services;

import medievalsim.grandexchange.domain.GEOffer;
import medievalsim.grandexchange.domain.BuyOrder;
import medievalsim.util.ModLogger;
import medievalsim.util.TimeConstants;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Notification service for Grand Exchange events.
 * 
 * Pattern: Observer pattern for event-driven updates.
 * 
 * Features:
 * - Notify players when offers are filled
 * - Notify players when offers are partially filled
 * - Alert players to favorable market conditions
 * - Queue notifications for offline players
 * 
 * Thread-safe using concurrent collections.
 */
public class NotificationService {
    
    // Player auth -> list of pending notifications
    private final Map<Long, List<Notification>> pendingNotifications = new ConcurrentHashMap<>();
    
    // Collection size limits
    private static final int MAX_NOTIFICATIONS_PER_PLAYER = 100;
    
    // Statistics
    private int totalNotificationsSent = 0;
    private int totalNotificationsQueued = 0;
    
    /**
     * Notify player that their sell offer was filled.
     */
    public void notifyOfferFilled(GEOffer offer, int quantityFilled, int coinsReceived) {
        String message = String.format(
            "Your sell offer for %s x%d was filled! Received %d coins.",
            offer.getItemStringID(), quantityFilled, coinsReceived
        );
        
        queueNotification(offer.getPlayerAuth(), NotificationType.OFFER_FILLED, 
            offer.getItemStringID(), message);
    }
    
    /**
     * Notify player that their sell offer was partially filled.
     */
    public void notifyOfferPartiallyFilled(GEOffer offer, int quantityFilled, 
                                          int quantityRemaining, int coinsReceived) {
        String message = String.format(
            "Your sell offer for %s was partially filled: %d/%d sold for %d coins. %d remaining.",
            offer.getItemStringID(), quantityFilled, 
            offer.getQuantityTotal(), coinsReceived, quantityRemaining
        );
        
        queueNotification(offer.getPlayerAuth(), NotificationType.OFFER_PARTIAL, 
            offer.getItemStringID(), message);
    }
    
    /**
     * Notify player that their buy order was filled.
     */
    public void notifyBuyOrderFilled(BuyOrder order, int quantityFilled, int coinsSpent) {
        String message = String.format(
            "Your buy order for %s x%d was filled! Spent %d coins. Check collection box.",
            order.getItemStringID(), quantityFilled, coinsSpent
        );
        
        queueNotification(order.getPlayerAuth(), NotificationType.BUY_ORDER_FILLED, 
            order.getItemStringID(), message);
    }
    
    /**
     * Notify player that their buy order was partially filled.
     */
    public void notifyBuyOrderPartiallyFilled(BuyOrder order, int quantityFilled,
                                             int quantityRemaining, int coinsSpent) {
        String message = String.format(
            "Your buy order for %s was partially filled: %d/%d bought for %d coins. %d remaining.",
            order.getItemStringID(), quantityFilled,
            order.getQuantityTotal(), coinsSpent, quantityRemaining
        );
        
        queueNotification(order.getPlayerAuth(), NotificationType.BUY_ORDER_PARTIAL,
            order.getItemStringID(), message);
    }
    
    /**
     * Notify player that their offer expired.
     */
    public void notifyOfferExpired(GEOffer offer) {
        String message = String.format(
            "Your sell offer for %s x%d has expired. Items returned to collection box.",
            offer.getItemStringID(), offer.getQuantityRemaining()
        );
        
        queueNotification(offer.getPlayerAuth(), NotificationType.OFFER_EXPIRED,
            offer.getItemStringID(), message);
    }
    
    /**
     * Notify player that their buy order expired.
     */
    public void notifyBuyOrderExpired(BuyOrder order) {
        String message = String.format(
            "Your buy order for %s x%d has expired. Coins refunded to bank.",
            order.getItemStringID(), order.getQuantityRemaining()
        );
        
        queueNotification(order.getPlayerAuth(), NotificationType.BUY_ORDER_EXPIRED,
            order.getItemStringID(), message);
    }
    
    /**
     * Alert player to favorable market condition.
     */
    public void alertFavorablePrice(long playerAuth, String itemID, int currentPrice, int targetPrice) {
        String message = String.format(
            "Price alert: %s is now %d coins (target: %d)!",
            itemID, currentPrice, targetPrice
        );
        
        queueNotification(playerAuth, NotificationType.PRICE_ALERT, itemID, message);
    }
    
    /**
     * Queue notification for player.
     */
    private void queueNotification(long playerAuth, NotificationType type, 
                                   String itemID, String message) {
        Notification notification = new Notification(
            System.currentTimeMillis(),
            type,
            itemID,
            message
        );
        
        List<Notification> notifications = pendingNotifications.computeIfAbsent(
            playerAuth, k -> new ArrayList<>()
        );
        
        synchronized (notifications) {
            // Enforce size limit to prevent memory leaks
            if (notifications.size() >= MAX_NOTIFICATIONS_PER_PLAYER) {
                notifications.remove(0); // Remove oldest
                ModLogger.warn("Notification queue full for player %d, removing oldest", playerAuth);
            }
            notifications.add(notification);
            totalNotificationsQueued++;
        }
        
        ModLogger.debug("Queued notification for player %d: %s", playerAuth, message);
    }
    
    /**
     * Get pending notifications for player.
     * @param clear If true, removes returned notifications from queue
     */
    public List<Notification> getNotifications(long playerAuth, boolean clear) {
        List<Notification> notifications = pendingNotifications.get(playerAuth);
        if (notifications == null) {
            return Collections.emptyList();
        }
        
        synchronized (notifications) {
            List<Notification> result = new ArrayList<>(notifications);
            if (clear) {
                notifications.clear();
                totalNotificationsSent += result.size();
            }
            return result;
        }
    }
    
    /**
     * Get count of pending notifications for player.
     * Thread-safe: synchronizes on list to prevent race conditions with cleanup/clear.
     */
    public int getNotificationCount(long playerAuth) {
        List<Notification> notifications = pendingNotifications.get(playerAuth);
        if (notifications == null) {
            return 0;
        }
        synchronized (notifications) {
            return notifications.size();
        }
    }
    
    /**
     * Clear all notifications for player.
     */
    public void clearNotifications(long playerAuth) {
        List<Notification> notifications = pendingNotifications.remove(playerAuth);
        if (notifications != null) {
            totalNotificationsSent += notifications.size();
            ModLogger.debug("Cleared %d notifications for player %d", notifications.size(), playerAuth);
        }
    }
    
    /**
     * Cleanup old notifications (called periodically).
     * Removes notifications older than 24 hours.
     * Optimized: uses iterator for efficient removal without excessive lock contention.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        long cutoff = now - TimeConstants.MILLIS_PER_DAY; // 24 hours
        
        int removed = 0;
        // Use iterator to safely remove empty lists
        java.util.Iterator<Map.Entry<Long, List<Notification>>> mapIterator = pendingNotifications.entrySet().iterator();
        while (mapIterator.hasNext()) {
            Map.Entry<Long, List<Notification>> entry = mapIterator.next();
            List<Notification> notifications = entry.getValue();
            
            synchronized (notifications) {
                int before = notifications.size();
                notifications.removeIf(n -> n.getTimestamp() < cutoff);
                removed += (before - notifications.size());
                
                // Remove empty lists to prevent memory leaks
                if (notifications.isEmpty()) {
                    mapIterator.remove();
                }
            }
        }
        
        if (removed > 0) {
            ModLogger.debug("Notification cleanup: removed %d old notifications", removed);
        }
    }
    
    // Statistics
    public int getTotalNotificationsSent() {
        return totalNotificationsSent;
    }
    
    public int getTotalNotificationsQueued() {
        return totalNotificationsQueued;
    }
    
    public int getPendingNotificationCount() {
        return pendingNotifications.values().stream()
            .mapToInt(List::size)
            .sum();
    }
    
    // ===== NESTED CLASSES =====
    
    /**
     * Types of notifications.
     */
    public enum NotificationType {
        OFFER_FILLED,
        OFFER_PARTIAL,
        OFFER_EXPIRED,
        BUY_ORDER_FILLED,
        BUY_ORDER_PARTIAL,
        BUY_ORDER_EXPIRED,
        PRICE_ALERT,
        SYSTEM
    }
    
    /**
     * Individual notification.
     */
    public static class Notification {
        private final long timestamp;
        private final NotificationType type;
        private final String itemID;
        private final String message;
        
        public Notification(long timestamp, NotificationType type, String itemID, String message) {
            this.timestamp = timestamp;
            this.type = type;
            this.itemID = itemID;
            this.message = message;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public NotificationType getType() {
            return type;
        }
        
        public String getItemID() {
            return itemID;
        }
        
        public String getMessage() {
            return message;
        }
        
        public long getAge() {
            return System.currentTimeMillis() - timestamp;
        }
        
        @Override
        public String toString() {
            return String.format("[%s] %s", type, message);
        }
    }
}
