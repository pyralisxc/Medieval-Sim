package medievalsim.grandexchange.domain;

import org.junit.jupiter.api.Test;

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

    private PlayerGEInventory buildInventoryWithItems(String... itemStringIds) {
        PlayerGEInventory inventory = new PlayerGEInventory(12345L);
        for (String id : itemStringIds) {
            inventory.addToCollectionBox(id, 5, "test");
        }
        return inventory;
    }

    private List<String> snapshotIds(PlayerGEInventory inventory) {
        return inventory.getCollectionBox().stream()
            .map(CollectionItem::getItemStringID)
            .collect(Collectors.toList());
    }
}
