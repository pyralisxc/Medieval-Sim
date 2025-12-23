package medievalsim.grandexchange.application.workflow;

import medievalsim.config.ModConfig;
import medievalsim.grandexchange.application.GrandExchangeLedger;
import medievalsim.grandexchange.domain.GEOffer;
import medievalsim.grandexchange.domain.PlayerGEInventory;
import medievalsim.util.ModLogger;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.level.maps.Level;
import medievalsim.grandexchange.net.SellActionResultCode;
import medievalsim.grandexchange.net.SellActionType;

/**
 * Encapsulates all server-side sell-offer operations so the container becomes a
 * thin transport layer. This makes it easier to test staging logic without
 * instantiating the full container.
 */
public class SellOfferWorkflow {
    private final GrandExchangeLedger ledger;

    public SellOfferWorkflow(GrandExchangeLedger ledger) {
        this.ledger = ledger;
    }

    public ActionResult stageDraft(ServerClient serverClient,
                                   PlayerGEInventory inventory,
                                   long playerAuth,
                                   int stagingSlotIndex,
                                   int pricePerItem) {
        if (serverClient == null || inventory == null) {
            return ActionResult.failure(SellActionType.CREATE, SellActionResultCode.CLIENT_NOT_READY, stagingSlotIndex);
        }
        Inventory stagingInventory = inventory.getSellInventory();
        if (!inventory.isValidSellSlot(stagingSlotIndex)) {
            ModLogger.warn("Invalid staging slot %d for player auth=%d", stagingSlotIndex, playerAuth);
            return ActionResult.failure(SellActionType.CREATE, SellActionResultCode.INVALID_SLOT, stagingSlotIndex);
        }
        InventoryItem stagedItem = stagingInventory.getItem(stagingSlotIndex);
        if (stagedItem == null || stagedItem.item == null) {
            ModLogger.warn("No item in staging slot %d for player auth=%d", stagingSlotIndex, playerAuth);
            return ActionResult.failure(SellActionType.CREATE, SellActionResultCode.NO_ITEM_IN_SLOT, stagingSlotIndex);
        }
        if (!inventory.canCreateSellOffer()) {
            ModLogger.warn("Player auth=%d cannot create more sell offers (active=%d, max=%d)",
                playerAuth,
                inventory.getActiveOfferCount(),
                ModConfig.GrandExchange.maxActiveOffersPerPlayer);
            return ActionResult.failure(SellActionType.CREATE, SellActionResultCode.MAX_ACTIVE_REACHED, stagingSlotIndex);
        }
        if (pricePerItem <= 0 || !ModConfig.GrandExchange.isValidPrice(pricePerItem)) {
            ModLogger.warn("Invalid price %d for player auth=%d", pricePerItem, playerAuth);
            return ActionResult.failure(SellActionType.CREATE, SellActionResultCode.PRICE_OUT_OF_RANGE, stagingSlotIndex);
        }
        int quantity = stagedItem.getAmount();
        Item item = stagedItem.item;
        if (item == null || quantity <= 0 || quantity > item.getStackSize()) {
            ModLogger.warn("Invalid stack in staging slot %d for player auth=%d", stagingSlotIndex, playerAuth);
            return ActionResult.failure(SellActionType.CREATE, SellActionResultCode.INVALID_ITEM_STATE, stagingSlotIndex);
        }
        InventoryItem validationSnapshot = stagingInventory.getItem(stagingSlotIndex);
        if (validationSnapshot == null || validationSnapshot.item == null
            || !validationSnapshot.item.getStringID().equals(item.getStringID())
            || validationSnapshot.getAmount() != quantity) {
            ModLogger.warn("Item validation failed for staging slot %d (player auth=%d)", stagingSlotIndex, playerAuth);
            return ActionResult.failure(SellActionType.CREATE, SellActionResultCode.INVALID_ITEM_STATE, stagingSlotIndex);
        }
        int targetSlot = inventory.findAvailableSellSlot();
        if (targetSlot < 0) {
            ModLogger.warn("No reusable sell slots for player auth=%d", playerAuth);
            return ActionResult.failure(SellActionType.CREATE, SellActionResultCode.NO_AVAILABLE_SLOT, stagingSlotIndex);
        }

        GEOffer offer = ledger.createSellOffer(
            playerAuth,
            serverClient.getName(),
            targetSlot,
            item.getStringID(),
            quantity,
            pricePerItem
        );
        if (offer == null) {
            float cooldown = ledger.getSellOfferCreationCooldown(playerAuth);
            SellActionResultCode code = cooldown > 0 ? SellActionResultCode.RATE_LIMITED : SellActionResultCode.SERVER_REJECTED;
            return ActionResult.failure(SellActionType.CREATE, code, stagingSlotIndex);
        }
        stagingInventory.setItem(stagingSlotIndex, null);
        ModLogger.info("Created DRAFT sell offer slot %d (staged via slot %d): %s x%d @ %d coins",
            targetSlot, stagingSlotIndex, item.getStringID(), quantity, pricePerItem);
        return ActionResult.success(SellActionType.CREATE, targetSlot);
    }

    public ActionResult enable(ServerClient serverClient, long playerAuth, int slotIndex) {
        if (serverClient == null) {
            return ActionResult.failure(SellActionType.ENABLE, SellActionResultCode.CLIENT_NOT_READY, slotIndex);
        }
        if (slotIndex < 0 || slotIndex >= ModConfig.GrandExchange.geInventorySlots) {
            ModLogger.warn("Invalid sell slot index %d for enable (player auth=%d)", slotIndex, playerAuth);
            return ActionResult.failure(SellActionType.ENABLE, SellActionResultCode.INVALID_SLOT, slotIndex);
        }
        Level level = serverClient.getLevel();
        SellActionResultCode code = ledger.enableSellOffer(level, playerAuth, slotIndex);
        return new ActionResult(SellActionType.ENABLE, code, slotIndex);
    }

    public ActionResult disable(ServerClient serverClient, long playerAuth, int slotIndex) {
        if (serverClient == null) {
            return ActionResult.failure(SellActionType.DISABLE, SellActionResultCode.CLIENT_NOT_READY, slotIndex);
        }
        if (slotIndex < 0 || slotIndex >= ModConfig.GrandExchange.geInventorySlots) {
            ModLogger.warn("Invalid sell slot index %d for disable (player auth=%d)", slotIndex, playerAuth);
            return ActionResult.failure(SellActionType.DISABLE, SellActionResultCode.INVALID_SLOT, slotIndex);
        }
        Level level = serverClient.getLevel();
        SellActionResultCode code = ledger.disableSellOffer(level, playerAuth, slotIndex);
        return new ActionResult(SellActionType.DISABLE, code, slotIndex);
    }

    public boolean cancel(ServerClient serverClient,
                          PlayerGEInventory inventory,
                          long playerAuth,
                          int slotIndex) {
        if (serverClient == null || inventory == null) {
            return false;
        }
        if (slotIndex < 0 || slotIndex >= ModConfig.GrandExchange.geInventorySlots) {
            ModLogger.warn("Invalid sell slot index %d for cancel (player auth=%d)", slotIndex, playerAuth);
            return false;
        }
        GEOffer offer = inventory.getSlotOffer(slotIndex);
        if (offer == null) {
            ModLogger.warn("No offer in slot %d for player auth=%d", slotIndex, playerAuth);
            return false;
        }
        boolean canceled = ledger.cancelOffer(serverClient.getLevel(), offer.getOfferID());
        if (canceled) {
            ModLogger.info("Canceled sell offer slot %d for player auth=%d", slotIndex, playerAuth);
        } else {
            ModLogger.warn("Failed to cancel sell offer slot %d (offer ID=%d)", slotIndex, offer.getOfferID());
        }
        return canceled;
    }

    public boolean reclaimLegacyInventory(ServerClient serverClient, PlayerGEInventory inventory) {
        if (serverClient == null || serverClient.playerMob == null || inventory == null) {
            return false;
        }
        Inventory stagingInventory = inventory.getSellInventory();
        boolean collectionUpdated = false;
        for (int slot = 1; slot < stagingInventory.getSize(); slot++) {
            InventoryItem legacyItem = stagingInventory.getItem(slot);
            if (legacyItem == null || legacyItem.item == null || legacyItem.getAmount() <= 0) {
                continue;
            }
            InventoryItem copy = legacyItem.copy();
            boolean added = serverClient.playerMob.getInv().main.addItem(
                serverClient.getLevel(),
                serverClient.playerMob,
                copy,
                "grandexchange_cleanup",
                null
            );
            if (!added) {
                inventory.addToCollectionBox(
                    legacyItem.item.getStringID(),
                    legacyItem.getAmount(),
                    "Legacy slot cleanup"
                );
                collectionUpdated = true;
            }
            stagingInventory.setItem(slot, null);
            ModLogger.info("Recovered legacy sell slot %d item for player auth=%d", slot + 1, inventory.getOwnerAuth());
        }
        return collectionUpdated;
    }

        public static final class ActionResult {
            private final SellActionType action;
            private final SellActionResultCode code;
            private final int slotIndex;

            public ActionResult(SellActionType action, SellActionResultCode code, int slotIndex) {
                this.action = action;
                this.code = code;
                this.slotIndex = slotIndex;
            }

            public static ActionResult success(SellActionType action, int slotIndex) {
                return new ActionResult(action, SellActionResultCode.SUCCESS, slotIndex);
            }

            public static ActionResult failure(SellActionType action, SellActionResultCode code, int slotIndex) {
                return new ActionResult(action, code, slotIndex);
            }

            public SellActionType getAction() {
                return action;
            }

            public SellActionResultCode getCode() {
                return code;
            }

            public int getSlotIndex() {
                return slotIndex;
            }

            public boolean isSuccess() {
                return code.isSuccess();
            }
        }
}
