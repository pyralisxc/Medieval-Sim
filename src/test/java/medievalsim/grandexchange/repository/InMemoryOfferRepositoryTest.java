package medievalsim.grandexchange.repository;

import medievalsim.grandexchange.domain.BuyOrder;
import medievalsim.grandexchange.domain.GEOffer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class InMemoryOfferRepositoryTest {

    @Test
    public void testSellOfferSaveEnableFindDelete() {
        InMemoryOfferRepository repo = new InMemoryOfferRepository();

        // Create a sell offer (DRAFT)
        GEOffer offer = GEOffer.createSellOffer(1L, 42L, "Tester", 0, "test:item", 5, 10);
        assertNotNull(offer);

        // Save draft (should not appear in active listings)
        repo.saveSellOffer(offer);
        assertTrue(repo.findSellOfferById(1L).isPresent());
        List<GEOffer> active = repo.findActiveSellOffersByItem("test:item");
        assertEquals(0, active.size());

        // Enable and save again
        assertTrue(offer.enable());
        repo.saveSellOffer(offer);
        active = repo.findActiveSellOffersByItem("test:item");
        assertEquals(1, active.size());
        assertEquals(1L, active.get(0).getOfferID());

        // Delete and verify cleanup
        assertTrue(repo.deleteSellOffer(1L));
        assertFalse(repo.findSellOfferById(1L).isPresent());
        active = repo.findActiveSellOffersByItem("test:item");
        assertEquals(0, active.size());
    }

    @Test
    public void testReindexingWhenOfferReplaced() {
        InMemoryOfferRepository repo = new InMemoryOfferRepository();

        GEOffer oldOffer = GEOffer.createSellOffer(2L, 200L, "Bob", 0, "old:item", 5, 10);
        assertTrue(oldOffer.enable());
        repo.saveSellOffer(oldOffer);

        // Create a new offer object with same ID but different item (simulate update)
        GEOffer newOffer = GEOffer.fromPacketData(2L, 200L, 0, "new:item", 5, 5, 8, true, GEOffer.OfferState.ACTIVE);
        repo.saveSellOffer(newOffer);

        // Old item should not have active offers, new item should
        assertEquals(0, repo.findActiveSellOffersByItem("old:item").size());
        List<GEOffer> newList = repo.findActiveSellOffersByItem("new:item");
        assertEquals(1, newList.size());
        assertEquals(2L, newList.get(0).getOfferID());
    }

    @Test
    public void testFindActiveBuyOrdersByItemOrdersByPriceDesc() {
        InMemoryOfferRepository repo = new InMemoryOfferRepository();

        BuyOrder low = BuyOrder.fromPacketData(10L, 400L, 0, "item:x", 5, 5, 50, true, BuyOrder.BuyOrderState.ACTIVE, 1);
        BuyOrder high = BuyOrder.fromPacketData(11L, 401L, 0, "item:x", 5, 5, 100, true, BuyOrder.BuyOrderState.ACTIVE, 1);

        repo.saveBuyOrder(low);
        repo.saveBuyOrder(high);

        List<BuyOrder> orders = repo.findActiveBuyOrdersByItem("item:x");
        assertEquals(2, orders.size());
        // Highest price first
        assertEquals(11L, orders.get(0).getOrderID());
        assertEquals(10L, orders.get(1).getOrderID());
    }
}
