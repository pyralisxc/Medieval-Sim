package medievalsim.grandexchange.services;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting service for Grand Exchange offer creation.
 * 
 * Prevents spam by enforcing cooldowns between offer creations.
 * Tracks per-player timestamps and validates against configurable cooldown.
 * 
 * Pattern: Token bucket / cooldown tracker
 * Thread-safe using concurrent maps.
 */
public class RateLimitService {
    
    // Player auth -> last offer creation timestamp
    private final Map<Long, Long> lastOfferCreationTime = new ConcurrentHashMap<>();
    
    // Player auth -> last buy order creation timestamp
    private final Map<Long, Long> lastBuyOrderCreationTime = new ConcurrentHashMap<>();
    
    // Statistics
    private int totalChecks = 0;
    private int totalDenied = 0;
    
    /**
     * Check if player can create a sell offer.
     * @param playerAuth Player authentication ID
     * @return true if allowed, false if on cooldown
     */
    public boolean canCreateSellOffer(long playerAuth) {
        totalChecks++;
        
        float cooldownSeconds = ModConfig.GrandExchange.offerCreationCooldown;
        if (cooldownSeconds <= 0) {
            return true; // Cooldown disabled
        }
        
        long now = System.currentTimeMillis();
        Long lastTime = lastOfferCreationTime.get(playerAuth);
        
        if (lastTime == null) {
            return true; // First offer
        }
        
        long elapsedMs = now - lastTime;
        long cooldownMs = (long) (cooldownSeconds * 1000);
        
        if (elapsedMs < cooldownMs) {
            totalDenied++;
            float remainingSeconds = (cooldownMs - elapsedMs) / 1000.0f;
            ModLogger.debug("Rate limit: Player %d must wait %.1f more seconds to create offer",
                playerAuth, remainingSeconds);
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if player can create a buy order.
     * @param playerAuth Player authentication ID
     * @return true if allowed, false if on cooldown
     */
    public boolean canCreateBuyOrder(long playerAuth) {
        totalChecks++;
        
        float cooldownSeconds = ModConfig.GrandExchange.offerCreationCooldown;
        if (cooldownSeconds <= 0) {
            return true; // Cooldown disabled
        }
        
        long now = System.currentTimeMillis();
        Long lastTime = lastBuyOrderCreationTime.get(playerAuth);
        
        if (lastTime == null) {
            return true; // First order
        }
        
        long elapsedMs = now - lastTime;
        long cooldownMs = (long) (cooldownSeconds * 1000);
        
        if (elapsedMs < cooldownMs) {
            totalDenied++;
            float remainingSeconds = (cooldownMs - elapsedMs) / 1000.0f;
            ModLogger.debug("Rate limit: Player %d must wait %.1f more seconds to create buy order",
                playerAuth, remainingSeconds);
            return false;
        }
        
        return true;
    }
    
    /**
     * Record that player created a sell offer.
     */
    public void recordSellOfferCreation(long playerAuth) {
        lastOfferCreationTime.put(playerAuth, System.currentTimeMillis());
        ModLogger.debug("Recorded sell offer creation for player %d", playerAuth);
    }
    
    /**
     * Record that player created a buy order.
     */
    public void recordBuyOrderCreation(long playerAuth) {
        lastBuyOrderCreationTime.put(playerAuth, System.currentTimeMillis());
        ModLogger.debug("Recorded buy order creation for player %d", playerAuth);
    }
    
    /**
     * Get remaining cooldown for sell offers.
     * @return Seconds remaining, or 0 if no cooldown
     */
    public float getRemainingCooldownForSellOffer(long playerAuth) {
        float cooldownSeconds = ModConfig.GrandExchange.offerCreationCooldown;
        if (cooldownSeconds <= 0) {
            return 0;
        }
        
        Long lastTime = lastOfferCreationTime.get(playerAuth);
        if (lastTime == null) {
            return 0;
        }
        
        long now = System.currentTimeMillis();
        long elapsedMs = now - lastTime;
        long cooldownMs = (long) (cooldownSeconds * 1000);
        
        if (elapsedMs >= cooldownMs) {
            return 0;
        }
        
        return (cooldownMs - elapsedMs) / 1000.0f;
    }
    
    /**
     * Get remaining cooldown for buy orders.
     * @return Seconds remaining, or 0 if no cooldown
     */
    public float getRemainingCooldownForBuyOrder(long playerAuth) {
        float cooldownSeconds = ModConfig.GrandExchange.offerCreationCooldown;
        if (cooldownSeconds <= 0) {
            return 0;
        }
        
        Long lastTime = lastBuyOrderCreationTime.get(playerAuth);
        if (lastTime == null) {
            return 0;
        }
        
        long now = System.currentTimeMillis();
        long elapsedMs = now - lastTime;
        long cooldownMs = (long) (cooldownSeconds * 1000);
        
        if (elapsedMs >= cooldownMs) {
            return 0;
        }
        
        return (cooldownMs - elapsedMs) / 1000.0f;
    }
    
    /**
     * Clear cooldown for player (admin override).
     */
    public void clearCooldown(long playerAuth) {
        lastOfferCreationTime.remove(playerAuth);
        lastBuyOrderCreationTime.remove(playerAuth);
        ModLogger.info("Cleared rate limit cooldown for player %d", playerAuth);
    }
    
    /**
     * Cleanup old entries (called periodically).
     * Removes entries older than 10 minutes.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        long cutoff = now - (10 * 60 * 1000); // 10 minutes
        
        int before1 = lastOfferCreationTime.size();
        lastOfferCreationTime.entrySet().removeIf(e -> e.getValue() < cutoff);
        int removed1 = before1 - lastOfferCreationTime.size();
        
        int before2 = lastBuyOrderCreationTime.size();
        lastBuyOrderCreationTime.entrySet().removeIf(e -> e.getValue() < cutoff);
        int removed2 = before2 - lastBuyOrderCreationTime.size();
        
        int removed = removed1 + removed2;
        if (removed > 0) {
            ModLogger.debug("Rate limit cleanup: removed %d old entries", removed);
        }
    }
    
    // Statistics
    public int getTotalChecks() {
        return totalChecks;
    }
    
    public int getTotalDenied() {
        return totalDenied;
    }
    
    public int getTrackedPlayerCount() {
        return lastOfferCreationTime.size() + lastBuyOrderCreationTime.size();
    }
    
    public float getDenialRate() {
        return totalChecks > 0 ? (float) totalDenied / totalChecks : 0;
    }
}
