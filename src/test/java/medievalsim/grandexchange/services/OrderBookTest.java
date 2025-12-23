package medievalsim.grandexchange.services;

import medievalsim.grandexchange.domain.BuyOrder;
import medievalsim.grandexchange.domain.GEOffer;
import medievalsim.grandexchange.domain.BuyOrder.BuyOrderState;
import medievalsim.grandexchange.domain.GEOffer.OfferState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {
    private static final String ITEM_ID = "medievalsim:test:item";

    @Test
    void findMatchesForSellOfferHandlesHundredsOfBuyers() {
        OrderBook book = new OrderBook(ITEM_ID);
        GEOffer sellOffer = activeSellOffer(1L, 120, 500);
        int expectedFill = 0;

        for (int i = 0; i < 200; i++) {
            int price = 80 + i; // Ensure strictly increasing prices for deterministic heap ordering
            BuyOrder order = activeBuyOrder(i + 1, price, 5);
            book.addBuyOrder(order);
            if (price >= sellOffer.getPricePerItem()) {
                expectedFill += order.getQuantityRemaining();
            }
        }

        List<OrderBook.Match> matches = book.findMatchesForSellOffer(sellOffer);
        int matchedQuantity = matches.stream().mapToInt(OrderBook.Match::getQuantity).sum();

        assertEquals(Math.min(expectedFill, sellOffer.getQuantityRemaining()), matchedQuantity,
            "Sell offer should match all buy-side liquidity at or above its price until quantity is exhausted");

        int previousPrice = Integer.MAX_VALUE;
        for (OrderBook.Match match : matches) {
            assertNotNull(match.getBuyOrder(), "Sell-side matching should reference the winning buy order");
            assertTrue(match.getExecutionPrice() <= previousPrice,
                "Matches must be returned in price-descending order to honor priority");
            assertTrue(match.getExecutionPrice() >= sellOffer.getPricePerItem(),
                "All execution prices must meet or exceed the sell offer ask");
            previousPrice = match.getExecutionPrice();
        }
    }

    @Test
    void findMatchesForBuyOrderHandlesHundredsOfSellers() {
        OrderBook book = new OrderBook(ITEM_ID);
        BuyOrder megaBuyer = activeBuyOrder(999L, 150, 600);
        int expectedFill = 0;

        for (int i = 0; i < 250; i++) {
            int price = 90 + i; // Increasing prices so only the cheapest tiers fill first
            GEOffer offer = activeSellOffer(i + 10, price, 4);
            book.addSellOffer(offer);
            if (price <= megaBuyer.getPricePerItem()) {
                expectedFill += offer.getQuantityRemaining();
            }
        }

        List<OrderBook.Match> matches = book.findMatchesForBuyOrder(megaBuyer);
        int matchedQuantity = matches.stream().mapToInt(OrderBook.Match::getQuantity).sum();

        assertEquals(Math.min(expectedFill, megaBuyer.getQuantityRemaining()), matchedQuantity,
            "Buy order should consume cheapest sell offers until its quantity is depleted");

        int previousPrice = 0;
        for (OrderBook.Match match : matches) {
            assertNotNull(match.getSellOffer(), "Buy-side matching should point to contributing sell offers");
            assertTrue(match.getExecutionPrice() >= previousPrice,
                "Matches must be returned in price-ascending order for sell offers");
            assertTrue(match.getExecutionPrice() <= megaBuyer.getPricePerItem(),
                "Execution prices must never exceed the buyer's bid");
            previousPrice = match.getExecutionPrice();
        }
    }

    @Test
    void marketDepthAggregatesThousandsOfOrders() {
        OrderBook book = new OrderBook(ITEM_ID);
        AtomicInteger expectedBuyVolume = new AtomicInteger();
        AtomicInteger expectedSellVolume = new AtomicInteger();

        for (int i = 0; i < 500; i++) {
            int price = (i % 5 == 0) ? 200 : 195;
            BuyOrder order = activeBuyOrder(10_000L + i, price, 8);
            book.addBuyOrder(order);
            expectedBuyVolume.addAndGet(order.getQuantityRemaining());
        }

        for (int i = 0; i < 500; i++) {
            int price = (i % 4 == 0) ? 205 : 210;
            GEOffer offer = activeSellOffer(20_000L + i, price, 6);
            book.addSellOffer(offer);
            expectedSellVolume.addAndGet(offer.getQuantityRemaining());
        }

        OrderBook.MarketDepth depth = book.getMarketDepth();
        assertEquals(expectedBuyVolume.get(), depth.getTotalBuyVolume(),
            "Buy-side market depth should aggregate the total remaining volume across all prices");
        assertEquals(expectedSellVolume.get(), depth.getTotalSellVolume(),
            "Sell-side market depth should aggregate the total remaining volume across all prices");
        assertEquals(200, depth.getBestBuyPrice(), "Best bid should reflect highest buy tier");
        assertEquals(205, depth.getBestSellPrice(), "Best ask should reflect lowest sell tier");
        assertEquals(5, depth.getSpread(), "Spread should be best ask minus best bid");
    }

    private BuyOrder activeBuyOrder(long orderId, int pricePerItem, int quantity) {
        return BuyOrder.fromPacketData(
            orderId,
            1000L + orderId,
            (int) (orderId % 3),
            ITEM_ID,
            quantity,
            quantity,
            pricePerItem,
            true,
            BuyOrderState.ACTIVE,
            7
        );
    }

    private GEOffer activeSellOffer(long offerId, int pricePerItem, int quantity) {
        return GEOffer.fromPacketData(
            offerId,
            2000L + offerId,
            (int) (offerId % 10),
            ITEM_ID,
            quantity,
            quantity,
            pricePerItem,
            true,
            OfferState.ACTIVE
        );
    }
}
