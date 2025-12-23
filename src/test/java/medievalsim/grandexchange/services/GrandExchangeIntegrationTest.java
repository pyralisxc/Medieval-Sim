package medievalsim.grandexchange.services;

import medievalsim.grandexchange.domain.BuyOrder;
import medievalsim.grandexchange.domain.GEOffer;
import medievalsim.grandexchange.domain.PlayerGEInventory;
import medievalsim.grandexchange.domain.CollectionItem;
import medievalsim.grandexchange.net.SellActionResultCode;
import medievalsim.grandexchange.support.GrandExchangeTestHarness;
import medievalsim.grandexchange.support.GrandExchangeTestHarness.PlayerHandle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GrandExchangeIntegrationTest {

    @Test
    void concurrentTradesSettleEscrowInventoriesAndHistory() throws Exception {
        try (GrandExchangeTestHarness harness = GrandExchangeTestHarness.boot()) {
            String itemId = harness.defaultItemId();

            PlayerHandle sellerAlpha = harness.registerPlayer(1L, "Alpha", 0);
            PlayerHandle sellerBeta = harness.registerPlayer(2L, "Beta", 0);
            PlayerHandle buyerOne = harness.registerPlayer(101L, "BuyerOne", 50_000);
            PlayerHandle buyerTwo = harness.registerPlayer(102L, "BuyerTwo", 50_000);

            harness.stageSellOffer(sellerAlpha, 0, itemId, 120, 50);
            harness.stageSellOffer(sellerBeta, 1, itemId, 150, 55);

            harness.stageBuyOrder(buyerOne, 0, itemId, 150, 60);
            harness.stageBuyOrder(buyerTwo, 1, itemId, 120, 55);

            runConcurrentToggles(harness, sellerAlpha, sellerBeta, buyerOne, buyerTwo);

            PlayerGEInventory alphaInventory = harness.inventory(sellerAlpha);
            PlayerGEInventory betaInventory = harness.inventory(sellerBeta);
            PlayerGEInventory buyerOneInventory = harness.inventory(buyerOne);
            PlayerGEInventory buyerTwoInventory = harness.inventory(buyerTwo);

            GEOffer alphaOffer = alphaInventory.getSlotOffer(0);
            GEOffer betaOffer = betaInventory.getSlotOffer(1);
            assertNotNull(alphaOffer, "Seller Alpha slot should retain completed offer reference");
            assertNotNull(betaOffer, "Seller Beta slot should retain completed offer reference");
            assertEquals(GEOffer.OfferState.COMPLETED, alphaOffer.getState());
            assertEquals(GEOffer.OfferState.COMPLETED, betaOffer.getState());

            assertEquals(BuyOrder.BuyOrderState.COMPLETED, buyerOneInventory.getBuyOrder(0).getState());
            assertEquals(BuyOrder.BuyOrderState.COMPLETED, buyerTwoInventory.getBuyOrder(1).getState());
            assertEquals(0, buyerOneInventory.getCoinsInEscrow());
            assertEquals(0, buyerTwoInventory.getCoinsInEscrow());

            assertEquals(150, harness.collectionQuantity(buyerOne, itemId), "Buyer one should collect full order quantity");
            assertEquals(120, harness.collectionQuantity(buyerTwo, itemId), "Buyer two should collect full order quantity");

            long totalBuyerSpend = harness.coinsSpent(buyerOne) + harness.coinsSpent(buyerTwo);
            long totalSellerProceeds = harness.bankBalance(sellerAlpha) + harness.bankBalance(sellerBeta);
            assertEquals(totalBuyerSpend, totalSellerProceeds, "Coins leaving buyers should equal coins delivered to sellers");

            assertEquals(alphaInventory.getSaleHistory().size(), alphaInventory.getUnseenHistoryCount(),
                "History badge count should mirror sale history backlog");
            assertEquals(betaInventory.getSaleHistory().size(), betaInventory.getUnseenHistoryCount());

            assertTrue(harness.notificationCount(sellerAlpha) > 0, "Sellers should receive sale notifications");
            assertTrue(harness.notificationCount(sellerBeta) > 0);
            assertTrue(harness.notificationCount(buyerOne) > 0, "Buyers should receive fulfillment notifications");
            assertTrue(harness.notificationCount(buyerTwo) > 0);

            assertFalse(alphaInventory.getSaleHistory().isEmpty(), "Sellers record sale history entries");
            assertFalse(betaInventory.getSaleHistory().isEmpty());
        }
    }

    @Test
    void partialFillLeavesOfferActiveAndBuyCompletes() throws Exception {
        try (GrandExchangeTestHarness harness = GrandExchangeTestHarness.boot()) {
            String itemId = harness.defaultItemId();

            PlayerHandle seller = harness.registerPlayer(3L, "Seller", 0);
            PlayerHandle buyer = harness.registerPlayer(103L, "Buyer", 10_000);

            harness.stageSellOffer(seller, 0, itemId, 200, 50);
            assertEquals(SellActionResultCode.SUCCESS, harness.enableSellOffer(seller, 0));

            harness.stageBuyOrder(buyer, 0, itemId, 120, 55);
            assertTrue(harness.enableBuyOrder(buyer, 0));

            PlayerGEInventory sellerInventory = harness.inventory(seller);
            GEOffer offer = sellerInventory.getSlotOffer(0);
            assertNotNull(offer);
            assertEquals(GEOffer.OfferState.PARTIAL, offer.getState());
            assertEquals(80, offer.getQuantityRemaining());

            PlayerGEInventory buyerInventory = harness.inventory(buyer);
            BuyOrder buyOrder = buyerInventory.getBuyOrder(0);
            assertEquals(BuyOrder.BuyOrderState.COMPLETED, buyOrder.getState());
            assertEquals(0, buyerInventory.getCoinsInEscrow());
            assertEquals(120, harness.collectionQuantity(buyer, itemId));

            long expectedCoins = 120L * 55L;
            assertEquals(expectedCoins, harness.coinsSpent(buyer));
            assertEquals(expectedCoins, harness.bankBalance(seller));
        }
    }

    @Test
    void disablingBuyOrderRefundsEscrowAndResetsState() throws Exception {
        try (GrandExchangeTestHarness harness = GrandExchangeTestHarness.boot()) {
            String itemId = harness.defaultItemId();
            PlayerHandle buyer = harness.registerPlayer(104L, "Saver", 10_000);

            harness.stageBuyOrder(buyer, 0, itemId, 50, 40);
            assertTrue(harness.enableBuyOrder(buyer, 0));

            PlayerGEInventory buyerInventory = harness.inventory(buyer);
            assertEquals(2_000, buyerInventory.getCoinsInEscrow());
            assertEquals(8_000, harness.bankBalance(buyer));

            assertTrue(harness.disableBuyOrder(buyer, 0));
            assertEquals(0, buyerInventory.getCoinsInEscrow());
            assertEquals(10_000, harness.bankBalance(buyer));
            assertEquals(BuyOrder.BuyOrderState.DRAFT, buyerInventory.getBuyOrder(0).getState());
        }
    }

    @Test
    void manualPurchaseSettlesWithoutBuyOrder() throws Exception {
        try (GrandExchangeTestHarness harness = GrandExchangeTestHarness.boot()) {
            String itemId = harness.defaultItemId();

            PlayerHandle seller = harness.registerPlayer(4L, "DirectSeller", 0);
            PlayerHandle buyer = harness.registerPlayer(105L, "DirectBuyer", 20_000);

            harness.stageSellOffer(seller, 0, itemId, 180, 75);
            assertEquals(SellActionResultCode.SUCCESS, harness.enableSellOffer(seller, 0));

            GEOffer offer = harness.inventory(seller).getSlotOffer(0);
            assertNotNull(offer);

            assertTrue(harness.processMarketPurchase(buyer, offer.getOfferID(), 100));

            assertEquals(7_500, harness.coinsSpent(buyer));
            assertEquals(7_500, harness.bankBalance(seller));
            assertEquals(100, harness.collectionQuantity(buyer, itemId));
            assertEquals(80, offer.getQuantityRemaining());
            assertEquals(GEOffer.OfferState.PARTIAL, offer.getState());
        }
    }

    @Test
    void randomizedSoakMaintainsCoinConservation() throws Exception {
        try (GrandExchangeTestHarness harness = GrandExchangeTestHarness.boot()) {
            String itemId = harness.defaultItemId();
            List<PlayerHandle> sellers = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                PlayerHandle seller = harness.registerPlayer(200L + i, "Seller" + i, 0);
                sellers.add(seller);
                harness.stageSellOffer(seller, 0, itemId, 400, 45 + i);
                assertEquals(SellActionResultCode.SUCCESS, harness.enableSellOffer(seller, 0));
            }

            List<PlayerHandle> buyers = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                buyers.add(harness.registerPlayer(400L + i, "Buyer" + i, 1_000_000));
            }

            Random rng = new Random(42);
            for (int i = 0; i < 200; i++) {
                PlayerHandle seller = sellers.get(rng.nextInt(sellers.size()));
                PlayerGEInventory sellerInventory = harness.inventory(seller);
                GEOffer offer = sellerInventory.getSlotOffer(0);
                if (offer == null || !offer.isActive()) {
                    harness.stageSellOffer(seller, 0, itemId, 400, 45 + rng.nextInt(10));
                    assertEquals(SellActionResultCode.SUCCESS, harness.enableSellOffer(seller, 0));
                    offer = sellerInventory.getSlotOffer(0);
                }

                PlayerHandle buyer = buyers.get(rng.nextInt(buyers.size()));
                int quantity = Math.min(offer.getQuantityRemaining(), 15 + rng.nextInt(20));
                quantity = Math.max(1, quantity);
                int buySlot = i % 3;
                int pricePerItem = offer.getPricePerItem() + rng.nextInt(5);

                harness.stageBuyOrder(buyer, buySlot, itemId, quantity, pricePerItem);
                assertTrue(harness.enableBuyOrder(buyer, buySlot));
            }

            long totalBuyerSpend = 0;
            for (PlayerHandle buyer : buyers) {
                totalBuyerSpend += harness.coinsSpent(buyer);
            }

            long totalSellerProceeds = 0;
            for (PlayerHandle seller : sellers) {
                totalSellerProceeds += harness.bankBalance(seller);
            }

            assertEquals(totalBuyerSpend, totalSellerProceeds, "Coin conservation must hold during soak test");
        }
    }

    private void runConcurrentToggles(GrandExchangeTestHarness harness,
                                      PlayerHandle sellerAlpha,
                                      PlayerHandle sellerBeta,
                                      PlayerHandle buyerOne,
                                      PlayerHandle buyerTwo) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Callable<Void>> tasks = new ArrayList<>();
        tasks.add(() -> {
            startSignal.await();
            assertEquals(SellActionResultCode.SUCCESS, harness.enableSellOffer(sellerAlpha, 0));
            return null;
        });
        tasks.add(() -> {
            startSignal.await();
            assertEquals(SellActionResultCode.SUCCESS, harness.enableSellOffer(sellerBeta, 1));
            return null;
        });
        tasks.add(() -> {
            startSignal.await();
            assertTrue(harness.enableBuyOrder(buyerOne, 0));
            return null;
        });
        tasks.add(() -> {
            startSignal.await();
            assertTrue(harness.enableBuyOrder(buyerTwo, 1));
            return null;
        });

        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(executor.submit(task));
            }
            startSignal.countDown();
            for (Future<Void> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
