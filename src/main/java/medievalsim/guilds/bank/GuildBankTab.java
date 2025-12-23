/*
 * Guild Bank Tab for Medieval Sim Mod
 * Single tab of guild bank storage - wrapper around Inventory with serialization.
 */
package medievalsim.guilds.bank;

import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.save.levelData.InventorySave;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;
import necesse.level.maps.Level;

/**
 * A single tab within a guild bank.
 * Wraps a standard Inventory with persistence and network support.
 */
public class GuildBankTab {

    private final Inventory inventory;

    public GuildBankTab(int slots) {
        this.inventory = new Inventory(slots);
    }

    public int getSlots() {
        return inventory.getSize();
    }

    public InventoryItem getItem(int slot) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return null;
        }
        return inventory.getItem(slot);
    }

    public void setItem(int slot, InventoryItem item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    /**
     * Add an item to the tab, stacking where possible.
     * @return Remaining items that couldn't fit, or null if all fit
     */
    public InventoryItem addItem(InventoryItem item) {
        if (item == null) return null;

        int remaining = item.getAmount();

        // First pass: stack with existing items
        for (int i = 0; i < inventory.getSize() && remaining > 0; i++) {
            InventoryItem slot = inventory.getItem(i);
            if (slot != null && slot.canCombine((Level) null, null, item, "stackAdd")) {
                int canAdd = slot.item.getStackSize() - slot.getAmount();
                int toAdd = Math.min(canAdd, remaining);
                if (toAdd > 0) {
                    slot.setAmount(slot.getAmount() + toAdd);
                    remaining -= toAdd;
                }
            }
        }

        // Second pass: fill empty slots
        for (int i = 0; i < inventory.getSize() && remaining > 0; i++) {
            if (inventory.getItem(i) == null) {
                InventoryItem newStack = item.copy();
                int toAdd = Math.min(remaining, item.item.getStackSize());
                newStack.setAmount(toAdd);
                inventory.setItem(i, newStack);
                remaining -= toAdd;
            }
        }

        if (remaining <= 0) {
            return null;  // All items fit
        }

        // Return remaining items
        InventoryItem leftover = item.copy();
        leftover.setAmount(remaining);
        return leftover;
    }

    /**
     * Check if the tab has space for the given item.
     */
    public boolean hasSpaceFor(InventoryItem item) {
        if (item == null) return true;

        int needed = item.getAmount();

        // Check stacking space
        for (int i = 0; i < inventory.getSize() && needed > 0; i++) {
            InventoryItem slot = inventory.getItem(i);
            if (slot == null) {
                needed -= item.item.getStackSize();
            } else if (slot.canCombine((Level) null, null, item, "stackAdd")) {
                int canAdd = slot.item.getStackSize() - slot.getAmount();
                needed -= canAdd;
            }
        }

        return needed <= 0;
    }

    /**
     * Count total items of a specific type in this tab.
     */
    public int countItem(String itemID) {
        int count = 0;
        for (int i = 0; i < inventory.getSize(); i++) {
            InventoryItem slot = inventory.getItem(i);
            if (slot != null && slot.item.getStringID().equals(itemID)) {
                count += slot.getAmount();
            }
        }
        return count;
    }

    /**
     * Check if the tab is empty.
     */
    public boolean isEmpty() {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) != null) {
                return false;
            }
        }
        return true;
    }

    // === Persistence ===

    public void addSaveData(SaveData save) {
        save.addSaveData(InventorySave.getSave(inventory, "INVENTORY"));
    }

    public void applyLoadData(LoadData load) {
        LoadData inventoryLoad = load.getFirstLoadDataByName("INVENTORY");
        if (inventoryLoad != null) {
            Inventory loadedInventory = InventorySave.loadSave(inventoryLoad);
            inventory.override(loadedInventory, false, false);
        }
    }

    // === Network ===

    public void writePacket(PacketWriter writer) {
        // Write each slot
        writer.putNextInt(inventory.getSize());
        for (int i = 0; i < inventory.getSize(); i++) {
            InventoryItem item = inventory.getItem(i);
            if (item != null) {
                writer.putNextBoolean(true);
                InventoryItem.addPacketContent(item, writer);
            } else {
                writer.putNextBoolean(false);
            }
        }
    }

    public void readPacket(PacketReader reader) {
        int size = reader.getNextInt();
        for (int i = 0; i < size && i < inventory.getSize(); i++) {
            if (reader.getNextBoolean()) {
                InventoryItem item = InventoryItem.fromContentPacket(reader);
                inventory.setItem(i, item);
            } else {
                inventory.setItem(i, null);
            }
        }
    }

    /**
     * Write a single slot update.
     */
    public void writeSlotPacket(PacketWriter writer, int slot) {
        InventoryItem item = getItem(slot);
        if (item != null) {
            writer.putNextBoolean(true);
            InventoryItem.addPacketContent(item, writer);
        } else {
            writer.putNextBoolean(false);
        }
    }

    /**
     * Get the underlying inventory (for container integration).
     */
    public Inventory getInventory() {
        return inventory;
    }
}
