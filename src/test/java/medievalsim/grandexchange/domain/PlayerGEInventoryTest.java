package medievalsim.grandexchange.domain;

import medievalsim.grandexchange.domain.SaleNotification;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerGEInventoryTest {

    @Test
    public void removeFromCollectionBoxReturnsCorrectItem() {
        PlayerGEInventory inventory = buildInventoryWithItems(
            "medievalsim:test:item_one",
            "medievalsim:test:item_two"
        );

        CollectionItem removed = inventory.removeFromCollectionBox(1);

        assertNotNull(removed, "Expected remove to return the requested item");
        assertEquals("medievalsim:test:item_two", removed.getItemStringID());
        assertEquals(1, inventory.getCollectionBoxSize());
        assertEquals("medievalsim:test:item_one", inventory.getCollectionBox().get(0).getItemStringID());
    }

    @Test
    public void insertIntoCollectionBoxRestoresOrderAfterRollback() {
        PlayerGEInventory inventory = buildInventoryWithItems(
            "medievalsim:test:item_one",
            "medievalsim:test:item_two",
            "medievalsim:test:item_three"
        );
        List<String> originalOrder = snapshotIds(inventory);

        CollectionItem removed = inventory.removeFromCollectionBox(1);
        assertEquals("medievalsim:test:item_two", removed.getItemStringID());

        inventory.insertIntoCollectionBox(1, removed);

        assertEquals(originalOrder, snapshotIds(inventory),
            "Reinserting at the same index should restore the pre-removal order");
    }

    @Test
    public void insertIntoCollectionBoxAppendsWhenIndexIsOutOfRange() {
        PlayerGEInventory inventory = buildInventoryWithItems(
            "medievalsim:test:item_one",
            "medievalsim:test:item_two"
        );

        CollectionItem rollbackItem = new CollectionItem("medievalsim:test:item_three", 7, "rollback");
        inventory.insertIntoCollectionBox(99, rollbackItem);

        assertEquals(3, inventory.getCollectionBoxSize());
        assertEquals("medievalsim:test:item_three",
            inventory.getCollectionBox().get(2).getItemStringID(),
            "Out-of-range insertions should append to the end for rollback safety");
    }

    @Test
    public void unseenHistoryCountReflectsServerBaseline() {
        PlayerGEInventory inventory = new PlayerGEInventory(9876L);
        addHistoryEntries(inventory, 1_000_000L, 3);

        List<SaleNotification> history = inventory.getSaleHistory();
        assertEquals(3, history.size(), "Expected three notifications to be queued");

        long baseline = history.get(1).getTimestamp();
        inventory.markHistoryViewed(baseline);

        assertEquals(1, inventory.getUnseenHistoryCount(),
            "Only entries newer than the marked baseline should remain unseen");
        assertEquals(baseline, inventory.getLastHistoryViewedTimestamp(),
            "Baseline timestamp should be persisted");
    }

    @Test
    public void markHistoryViewedUpToLatestClearsUnseenCount() {
        PlayerGEInventory inventory = new PlayerGEInventory(777L);
        addHistoryEntries(inventory, 2_000_000L, 2);
        assertTrue(inventory.getUnseenHistoryCount() > 0, "Fresh notifications should be unseen");

        inventory.markHistoryViewedUpToLatest();

        assertEquals(0, inventory.getUnseenHistoryCount(), "Acknowledging up to latest should clear badges");
        assertEquals(inventory.getLatestHistoryTimestamp(), inventory.getLastHistoryViewedTimestamp(),
            "Baseline should advance to the newest entry timestamp");
    }

    private PlayerGEInventory buildInventoryWithItems(String... itemStringIds) {
        PlayerGEInventory inventory = new PlayerGEInventory(12345L);
        for (String id : itemStringIds) {
            inventory.addToCollectionBox(id, 5, "test");
        }
        return inventory;
    }

    private void addHistoryEntries(PlayerGEInventory inventory, long startTimestamp, int count) {
        for (int i = 0; i < count; i++) {
            inventory.addSaleNotification(notificationWithTimestamp(startTimestamp + (i * 100L), i));
        }
    }

    private SaleNotification notificationWithTimestamp(long timestamp, int index) {
        try {
            Constructor<SaleNotification> constructor = SaleNotification.class.getDeclaredConstructor(
                    long.class,
                    String.class,
                    int.class,
                    int.class,
                    int.class,
                    boolean.class,
                    String.class,
                    boolean.class  // isSale parameter
            );
            constructor.setAccessible(true);
            return constructor.newInstance(
                timestamp,
                "medievalsim:test:item" + index,
                5 + index,
                10 + index,
                50 + (index * 10),
                false,
                "buyer" + index,
                true  // isSale = true
            );
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to construct SaleNotification for tests", e);
        }
    }    private List<String> snapshotIds(PlayerGEInventory inventory) {
        return inventory.getCollectionBox().stream()
            .map(CollectionItem::getItemStringID)
            .collect(Collectors.toList());
    }
}
