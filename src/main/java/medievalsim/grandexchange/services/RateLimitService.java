package medievalsim.grandexchange.services;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import medievalsim.util.TimeConstants;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

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

    // Player auth -> last sell offer toggle timestamp
    private final Map<Long, Long> lastSellToggleTime = new ConcurrentHashMap<>();

    // Player auth -> last buy order toggle timestamp
    private final Map<Long, Long> lastBuyToggleTime = new ConcurrentHashMap<>();
    
    // Statistics
    private final LongAdder totalChecks = new LongAdder();
    private final LongAdder totalDenied = new LongAdder();

    private final LongAdder sellCreationAttempts = new LongAdder();
    private final LongAdder sellCreationDenied = new LongAdder();
    private final LongAdder sellCreationSuccess = new LongAdder();

    private final LongAdder buyCreationAttempts = new LongAdder();
    private final LongAdder buyCreationDenied = new LongAdder();
    private final LongAdder buyCreationSuccess = new LongAdder();

    private final LongAdder sellToggleAttempts = new LongAdder();
    private final LongAdder sellToggleDenied = new LongAdder();
    private final LongAdder sellToggleSuccess = new LongAdder();

    private final LongAdder buyToggleAttempts = new LongAdder();
    private final LongAdder buyToggleDenied = new LongAdder();
    private final LongAdder buyToggleSuccess = new LongAdder();

    private final Runnable dirtyListener;

    public RateLimitService() {
        this(null);
    }

    public RateLimitService(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener;
    }

    private void markDirty() {
        if (dirtyListener != null) {
            dirtyListener.run();
        }
    }
    
    /**
     * Check if player can create a sell offer.
     * @param playerAuth Player authentication ID
     * @return true if allowed, false if on cooldown
     */
    public boolean canCreateSellOffer(long playerAuth) {
        totalChecks.increment();
        sellCreationAttempts.increment();
        
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
        long cooldownMs = (long) (cooldownSeconds * TimeConstants.MILLIS_PER_SECOND);
        
        if (elapsedMs < cooldownMs) {
            totalDenied.increment();
            sellCreationDenied.increment();
            float remainingSeconds = (cooldownMs - elapsedMs) / (float) TimeConstants.MILLIS_PER_SECOND;
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
        totalChecks.increment();
        buyCreationAttempts.increment();
        
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
        long cooldownMs = (long) (cooldownSeconds * TimeConstants.MILLIS_PER_SECOND);
        
        if (elapsedMs < cooldownMs) {
            totalDenied.increment();
            buyCreationDenied.increment();
            float remainingSeconds = (cooldownMs - elapsedMs) / (float) TimeConstants.MILLIS_PER_SECOND;
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
        sellCreationSuccess.increment();
        ModLogger.debug("Recorded sell offer creation for player %d", playerAuth);
        markDirty();
    }

    /**
     * Rate limit sell offer enable/disable spam.
     */
    public boolean canToggleSellOffer(long playerAuth) {
        sellToggleAttempts.increment();

        int cooldownSeconds = ModConfig.GrandExchange.sellDisableCooldownSeconds;
        if (cooldownSeconds <= 0) {
            return true;
        }

        Long lastTime = lastSellToggleTime.get(playerAuth);
        if (lastTime == null) {
            return true;
        }

        long elapsed = System.currentTimeMillis() - lastTime;
        long cooldownMs = cooldownSeconds * TimeConstants.MILLIS_PER_SECOND;
        if (elapsed < cooldownMs) {
            sellToggleDenied.increment();
            ModLogger.debug("Sell toggle cooldown: player %d must wait %.2fs",
                playerAuth, (cooldownMs - elapsed) / (float) TimeConstants.MILLIS_PER_SECOND);
            return false;
        }
        return true;
    }

    public void recordSellToggle(long playerAuth) {
        lastSellToggleTime.put(playerAuth, System.currentTimeMillis());
        sellToggleSuccess.increment();
        markDirty();
    }

    /**
     * Get remaining cooldown (in seconds) before the player can toggle a sell offer again.
     */
    public float getRemainingToggleCooldown(long playerAuth) {
        int cooldownSeconds = ModConfig.GrandExchange.sellDisableCooldownSeconds;
        if (cooldownSeconds <= 0) {
            return 0f;
        }
        Long lastTime = lastSellToggleTime.get(playerAuth);
        if (lastTime == null) {
            return 0f;
        }
        long elapsed = System.currentTimeMillis() - lastTime;
        long cooldownMs = cooldownSeconds * 1000L;
        if (elapsed >= cooldownMs) {
            return 0f;
        }
        return (cooldownMs - elapsed) / 1000f;
    }

    public boolean canToggleBuyOrder(long playerAuth) {
        buyToggleAttempts.increment();

        int cooldownSeconds = ModConfig.GrandExchange.sellDisableCooldownSeconds;
        if (cooldownSeconds <= 0) {
            return true;
        }

        Long lastTime = lastBuyToggleTime.get(playerAuth);
        if (lastTime == null) {
            return true;
        }

        long elapsed = System.currentTimeMillis() - lastTime;
        long cooldownMs = cooldownSeconds * TimeConstants.MILLIS_PER_SECOND;
        if (elapsed < cooldownMs) {
            buyToggleDenied.increment();
            ModLogger.debug("Buy toggle cooldown: player %d must wait %.2fs",
                playerAuth, (cooldownMs - elapsed) / (float) TimeConstants.MILLIS_PER_SECOND);
            return false;
        }
        return true;
    }

    public void recordBuyToggle(long playerAuth) {
        lastBuyToggleTime.put(playerAuth, System.currentTimeMillis());
        buyToggleSuccess.increment();
        markDirty();
    }

    public float getRemainingCooldownForBuyToggle(long playerAuth) {
        int cooldownSeconds = ModConfig.GrandExchange.sellDisableCooldownSeconds;
        if (cooldownSeconds <= 0) {
            return 0f;
        }
        Long lastTime = lastBuyToggleTime.get(playerAuth);
        if (lastTime == null) {
            return 0f;
        }
        long elapsed = System.currentTimeMillis() - lastTime;
        long cooldownMs = cooldownSeconds * TimeConstants.MILLIS_PER_SECOND;
        if (elapsed >= cooldownMs) {
            return 0f;
        }
        return (cooldownMs - elapsed) / (float) TimeConstants.MILLIS_PER_SECOND;
    }
    
    /**
     * Record that player created a buy order.
     */
    public void recordBuyOrderCreation(long playerAuth) {
        lastBuyOrderCreationTime.put(playerAuth, System.currentTimeMillis());
        buyCreationSuccess.increment();
        ModLogger.debug("Recorded buy order creation for player %d", playerAuth);
        markDirty();
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
     * Returns remaining cooldown for sell-offer toggles. Alias for UI planning so callers
     * do not need to duplicate the toggle-specific accessor naming.
     */
    public float getRemainingCooldownForSellToggle(long playerAuth) {
        return getRemainingToggleCooldown(playerAuth);
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
        long cooldownMs = (long) (cooldownSeconds * TimeConstants.MILLIS_PER_SECOND);
        
        if (elapsedMs >= cooldownMs) {
            return 0;
        }
        
        return (cooldownMs - elapsedMs) / (float) TimeConstants.MILLIS_PER_SECOND;
    }

    public RateLimitStatus snapshot(RateLimitedAction action, long playerAuth) {
        if (action == null) {
            return RateLimitStatus.inactive(null);
        }
        return switch (action) {
            case SELL_CREATE -> new RateLimitStatus(action, getRemainingCooldownForSellOffer(playerAuth));
            case SELL_TOGGLE -> new RateLimitStatus(action, getRemainingCooldownForSellToggle(playerAuth));
            case BUY_CREATE -> new RateLimitStatus(action, getRemainingCooldownForBuyOrder(playerAuth));
            case BUY_TOGGLE -> new RateLimitStatus(action, getRemainingCooldownForBuyToggle(playerAuth));
        };
    }
    
    /**
     * Clear cooldown for player (admin override).
     */
    public void clearCooldown(long playerAuth) {
        lastOfferCreationTime.remove(playerAuth);
        lastBuyOrderCreationTime.remove(playerAuth);
        lastSellToggleTime.remove(playerAuth);
        lastBuyToggleTime.remove(playerAuth);
        ModLogger.info("Cleared rate limit cooldown for player %d", playerAuth);
        markDirty();
    }
    
    /**
     * Cleanup old entries (called periodically).
     * Removes entries older than 10 minutes.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        long cutoff = now - TimeConstants.TEN_MINUTES_MS; // 10 minutes
        
        int before1 = lastOfferCreationTime.size();
        lastOfferCreationTime.entrySet().removeIf(e -> e.getValue() < cutoff);
        int removed1 = before1 - lastOfferCreationTime.size();
        
        int before2 = lastBuyOrderCreationTime.size();
        lastBuyOrderCreationTime.entrySet().removeIf(e -> e.getValue() < cutoff);
        int removed2 = before2 - lastBuyOrderCreationTime.size();
        
        int removed = removed1 + removed2;
        int before3 = lastSellToggleTime.size();
        lastSellToggleTime.entrySet().removeIf(e -> e.getValue() < cutoff);
        removed += before3 - lastSellToggleTime.size();
        int before4 = lastBuyToggleTime.size();
        lastBuyToggleTime.entrySet().removeIf(e -> e.getValue() < cutoff);
        removed += before4 - lastBuyToggleTime.size();
        if (removed > 0) {
            ModLogger.debug("Rate limit cleanup: removed %d old entries", removed);
        }

        logStats();
        if (removed > 0) {
            markDirty();
        }
    }
    
    // Statistics
    public int getTotalChecks() {
        return totalChecks.intValue();
    }
    
    public int getTotalDenied() {
        return totalDenied.intValue();
    }
    
    public int getTrackedPlayerCount() {
        return lastOfferCreationTime.size()
            + lastBuyOrderCreationTime.size()
            + lastSellToggleTime.size()
            + lastBuyToggleTime.size();
    }
    
    public float getDenialRate() {
        int checks = getTotalChecks();
        return checks > 0 ? (float) getTotalDenied() / checks : 0;
    }

    private void logStats() {
        ModLogger.debug("Rate limit stats | sell attempts=%d denied=%d created=%d | buy attempts=%d denied=%d created=%d | sell toggle attempts=%d denied=%d success=%d | buy toggle attempts=%d denied=%d success=%d",
            sellCreationAttempts.sum(),
            sellCreationDenied.sum(),
            sellCreationSuccess.sum(),
            buyCreationAttempts.sum(),
            buyCreationDenied.sum(),
            buyCreationSuccess.sum(),
            sellToggleAttempts.sum(),
            sellToggleDenied.sum(),
            sellToggleSuccess.sum(),
            buyToggleAttempts.sum(),
            buyToggleDenied.sum(),
            buyToggleSuccess.sum());
    }

    // ===== PERSISTENCE =====

    public void addSaveData(SaveData save) {
        saveCooldownMap(save, "SELL_CREATION", lastOfferCreationTime);
        saveCooldownMap(save, "BUY_CREATION", lastBuyOrderCreationTime);
        saveCooldownMap(save, "SELL_TOGGLE", lastSellToggleTime);
        saveCooldownMap(save, "BUY_TOGGLE", lastBuyToggleTime);

        save.addLong("totalChecks", totalChecks.longValue());
        save.addLong("totalDenied", totalDenied.longValue());
        save.addLong("sellCreationAttempts", sellCreationAttempts.longValue());
        save.addLong("sellCreationDenied", sellCreationDenied.longValue());
        save.addLong("sellCreationSuccess", sellCreationSuccess.longValue());
        save.addLong("buyCreationAttempts", buyCreationAttempts.longValue());
        save.addLong("buyCreationDenied", buyCreationDenied.longValue());
        save.addLong("buyCreationSuccess", buyCreationSuccess.longValue());
        save.addLong("sellToggleAttempts", sellToggleAttempts.longValue());
        save.addLong("sellToggleDenied", sellToggleDenied.longValue());
        save.addLong("sellToggleSuccess", sellToggleSuccess.longValue());
        save.addLong("buyToggleAttempts", buyToggleAttempts.longValue());
        save.addLong("buyToggleDenied", buyToggleDenied.longValue());
        save.addLong("buyToggleSuccess", buyToggleSuccess.longValue());
    }

    public void applyLoadData(LoadData load) {
        loadCooldownMap(load.getFirstLoadDataByName("SELL_CREATION"), lastOfferCreationTime);
        loadCooldownMap(load.getFirstLoadDataByName("BUY_CREATION"), lastBuyOrderCreationTime);
        loadCooldownMap(load.getFirstLoadDataByName("SELL_TOGGLE"), lastSellToggleTime);
        loadCooldownMap(load.getFirstLoadDataByName("BUY_TOGGLE"), lastBuyToggleTime);

        resetAdder(totalChecks, load.getLong("totalChecks", 0L));
        resetAdder(totalDenied, load.getLong("totalDenied", 0L));
        resetAdder(sellCreationAttempts, load.getLong("sellCreationAttempts", 0L));
        resetAdder(sellCreationDenied, load.getLong("sellCreationDenied", 0L));
        resetAdder(sellCreationSuccess, load.getLong("sellCreationSuccess", 0L));
        resetAdder(buyCreationAttempts, load.getLong("buyCreationAttempts", 0L));
        resetAdder(buyCreationDenied, load.getLong("buyCreationDenied", 0L));
        resetAdder(buyCreationSuccess, load.getLong("buyCreationSuccess", 0L));
        resetAdder(sellToggleAttempts, load.getLong("sellToggleAttempts", 0L));
        resetAdder(sellToggleDenied, load.getLong("sellToggleDenied", 0L));
        resetAdder(sellToggleSuccess, load.getLong("sellToggleSuccess", 0L));
        resetAdder(buyToggleAttempts, load.getLong("buyToggleAttempts", 0L));
        resetAdder(buyToggleDenied, load.getLong("buyToggleDenied", 0L));
        resetAdder(buyToggleSuccess, load.getLong("buyToggleSuccess", 0L));
    }

    private void saveCooldownMap(SaveData parent, String name, Map<Long, Long> source) {
        SaveData mapData = new SaveData(name);
        source.forEach((auth, timestamp) -> {
            SaveData entry = new SaveData("ENTRY");
            entry.addLong("auth", auth);
            entry.addLong("timestamp", timestamp);
            mapData.addSaveData(entry);
        });
        parent.addSaveData(mapData);
    }

    private void loadCooldownMap(LoadData data, Map<Long, Long> target) {
        target.clear();
        if (data == null) {
            return;
        }
        for (LoadData entry : data.getLoadDataByName("ENTRY")) {
            long auth = entry.getLong("auth", -1L);
            long timestamp = entry.getLong("timestamp", 0L);
            if (auth >= 0 && timestamp > 0) {
                target.put(auth, timestamp);
            }
        }
    }

    private void resetAdder(LongAdder adder, long value) {
        adder.reset();
        adder.add(value);
    }
}
