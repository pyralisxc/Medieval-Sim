package medievalsim.grandexchange.application;

import medievalsim.grandexchange.domain.GrandExchangeLevelData;
import medievalsim.grandexchange.domain.PlayerGEInventory;
import medievalsim.grandexchange.services.MarketAnalyticsService;
import medievalsim.grandexchange.services.RateLimitService;
import necesse.level.maps.Level;

/**
 * Lightweight helper that resolves {@link GrandExchangeLevelData} for a level and
 * exposes the shared {@link GrandExchangeLedger} plus commonly needed services.
 *
 * Higher layers should construct this once per request/command and reuse the
 * provided facade rather than touching level data directly.
 */
public final class GrandExchangeContext {
    private final Level level;
    private final GrandExchangeLevelData levelData;
    private final GrandExchangeLedger ledger;

    private GrandExchangeContext(Level level, GrandExchangeLevelData levelData) {
        this.level = level;
        this.levelData = levelData;
        this.ledger = new GrandExchangeLedger(levelData);
    }

    /**
     * Resolve the context for the provided level. Returns {@code null} if the
     * level is null or its Grand Exchange data has not been initialized yet.
     */
    public static GrandExchangeContext resolve(Level level) {
        if (level == null) {
            return null;
        }
        GrandExchangeLevelData data = GrandExchangeLevelData.getGrandExchangeData(level);
        if (data == null) {
            return null;
        }
        return new GrandExchangeContext(level, data);
    }

    public Level getLevel() {
        return level;
    }

    public GrandExchangeLedger getLedger() {
        return ledger;
    }

    /**
     * Direct data access should remain rare; prefer using {@link #getLedger()}.
     * This escape hatch exists for diagnostics and legacy flows still being
     * refactored toward the facade.
     */
    public GrandExchangeLevelData getLevelData() {
        return levelData;
    }

    public PlayerGEInventory getOrCreateInventory(long playerAuth) {
        return levelData.getOrCreateInventory(playerAuth);
    }

    public MarketAnalyticsService getAnalyticsService() {
        return levelData.getAnalyticsService();
    }

    public RateLimitService getRateLimitService() {
        return levelData.getRateLimitService();
    }
}
