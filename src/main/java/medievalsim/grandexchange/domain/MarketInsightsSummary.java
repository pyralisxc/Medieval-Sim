package medievalsim.grandexchange.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable summary of market-wide analytics data that can be serialized to clients.
 */
public final class MarketInsightsSummary {

    private final long generatedAt;
    private final long totalTradesLogged;
    private final long totalCoinsTraded;
    private final int trackedItemCount;
    private final List<ItemInsight> topVolumeItems;
    private final List<ItemInsight> widestSpreadItems;

    public MarketInsightsSummary(long generatedAt,
                                 long totalTradesLogged,
                                 long totalCoinsTraded,
                                 int trackedItemCount,
                                 List<ItemInsight> topVolumeItems,
                                 List<ItemInsight> widestSpreadItems) {
        this.generatedAt = generatedAt;
        this.totalTradesLogged = totalTradesLogged;
        this.totalCoinsTraded = totalCoinsTraded;
        this.trackedItemCount = trackedItemCount;
        this.topVolumeItems = Collections.unmodifiableList(new ArrayList<>(topVolumeItems));
        this.widestSpreadItems = Collections.unmodifiableList(new ArrayList<>(widestSpreadItems));
    }

    public long getGeneratedAt() {
        return generatedAt;
    }

    public long getTotalTradesLogged() {
        return totalTradesLogged;
    }

    public long getTotalCoinsTraded() {
        return totalCoinsTraded;
    }

    public int getTrackedItemCount() {
        return trackedItemCount;
    }

    public List<ItemInsight> getTopVolumeItems() {
        return topVolumeItems;
    }

    public List<ItemInsight> getWidestSpreadItems() {
        return widestSpreadItems;
    }

    /**
     * Describes guide price + volume metrics for a single item.
     */
    public static final class ItemInsight {
        private final String itemStringID;
        private final int guidePrice;
        private final int averagePrice;
        private final int volumeWeightedPrice;
        private final int high24h;
        private final int low24h;
        private final int spread;
        private final int tradeVolume;
        private final int tradeCount;
        private final long lastTradeTimestamp;

        public ItemInsight(String itemStringID,
                           int guidePrice,
                           int averagePrice,
                           int volumeWeightedPrice,
                           int high24h,
                           int low24h,
                           int spread,
                           int tradeVolume,
                           int tradeCount,
                           long lastTradeTimestamp) {
            this.itemStringID = itemStringID;
            this.guidePrice = guidePrice;
            this.averagePrice = averagePrice;
            this.volumeWeightedPrice = volumeWeightedPrice;
            this.high24h = high24h;
            this.low24h = low24h;
            this.spread = spread;
            this.tradeVolume = tradeVolume;
            this.tradeCount = tradeCount;
            this.lastTradeTimestamp = lastTradeTimestamp;
        }

        public String getItemStringID() {
            return itemStringID;
        }

        public int getGuidePrice() {
            return guidePrice;
        }

        public int getAveragePrice() {
            return averagePrice;
        }

        public int getVolumeWeightedPrice() {
            return volumeWeightedPrice;
        }

        public int getHigh24h() {
            return high24h;
        }

        public int getLow24h() {
            return low24h;
        }

        public int getSpread() {
            return spread;
        }

        public int getTradeVolume() {
            return tradeVolume;
        }

        public int getTradeCount() {
            return tradeCount;
        }

        public long getLastTradeTimestamp() {
            return lastTradeTimestamp;
        }
    }
}
