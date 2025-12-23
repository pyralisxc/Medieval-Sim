package medievalsim.grandexchange.application;

import medievalsim.grandexchange.domain.BuyOrder;
import medievalsim.grandexchange.domain.GEOffer;
import medievalsim.grandexchange.domain.GrandExchangeLevelData;
import medievalsim.grandexchange.domain.PlayerGEInventory;
import medievalsim.grandexchange.domain.MarketSnapshot;
import medievalsim.grandexchange.net.SellActionResultCode;
import medievalsim.grandexchange.services.MarketAnalyticsService;
import medievalsim.grandexchange.services.RateLimitedAction;
import medievalsim.grandexchange.services.RateLimitService;
import medievalsim.grandexchange.services.RateLimitStatus;
import necesse.level.maps.Level;

/**
 * Facade that centralizes access to level data so higher layers stop
 * depending directly on {@link GrandExchangeLevelData}. The goal is to make it
 * easier to substitute backing stores or decorate the behavior later.
 */
public class GrandExchangeLedger {
    private final GrandExchangeLevelData levelData;

    public GrandExchangeLedger(GrandExchangeLevelData levelData) {
        this.levelData = levelData;
    }

    public PlayerGEInventory getOrCreateInventory(long playerAuth) {
        return levelData.getOrCreateInventory(playerAuth);
    }

    public GEOffer createSellOffer(long playerAuth,
                                   String playerName,
                                   int slot,
                                   String itemStringID,
                                   int quantity,
                                   int pricePerItem) {
        return levelData.createSellOffer(playerAuth, playerName, slot, itemStringID, quantity, pricePerItem);
    }

    public BuyOrder createBuyOrder(long playerAuth,
                                   String playerName,
                                   int slotIndex,
                                   String itemStringID,
                                   int quantity,
                                   int pricePerItem,
                                   int durationDays) {
        return levelData.createBuyOrder(playerAuth, playerName, slotIndex, itemStringID, quantity, pricePerItem, durationDays);
    }

    public SellActionResultCode enableSellOffer(Level level, long playerAuth, int slotIndex) {
        return levelData.enableSellOffer(level, playerAuth, slotIndex);
    }

    public SellActionResultCode disableSellOffer(Level level, long playerAuth, int slotIndex) {
        return levelData.disableSellOffer(level, playerAuth, slotIndex);
    }

    public boolean cancelOffer(Level level, long offerId) {
        return levelData.cancelOffer(level, offerId);
    }

    public boolean enableBuyOrder(Level level, long playerAuth, int slotIndex) {
        return levelData.enableBuyOrder(level, playerAuth, slotIndex);
    }

    public boolean disableBuyOrder(Level level, long playerAuth, int slotIndex) {
        return levelData.disableBuyOrder(level, playerAuth, slotIndex);
    }

    public boolean cancelBuyOrder(Level level, long playerAuth, int slotIndex) {
        return levelData.cancelBuyOrder(level, playerAuth, slotIndex);
    }

    public MarketSnapshot buildMarketSnapshot(String filter,
                                              String category,
                                              int sortMode,
                                              int page) {
        return levelData.buildMarketSnapshot(filter, category, sortMode, page);
    }

    public MarketAnalyticsService getAnalyticsService() {
        return levelData.getAnalyticsService();
    }

    public RateLimitStatus getRateLimitStatus(RateLimitedAction action, long playerAuth) {
        RateLimitService service = levelData.getRateLimitService();
        if (service == null) {
            return RateLimitStatus.inactive(action);
        }
        return service.snapshot(action, playerAuth);
    }

    public float getSellOfferCreationCooldown(long playerAuth) {
        return getRateLimitStatus(RateLimitedAction.SELL_CREATE, playerAuth).remainingSeconds();
    }

    public float getSellOfferToggleCooldown(long playerAuth) {
        return getRateLimitStatus(RateLimitedAction.SELL_TOGGLE, playerAuth).remainingSeconds();
    }

    public float getBuyOrderCreationCooldown(long playerAuth) {
        return getRateLimitStatus(RateLimitedAction.BUY_CREATE, playerAuth).remainingSeconds();
    }

    public float getBuyOrderToggleCooldown(long playerAuth) {
        return getRateLimitStatus(RateLimitedAction.BUY_TOGGLE, playerAuth).remainingSeconds();
    }
}
