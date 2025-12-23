package medievalsim.grandexchange.ui.viewmodel;

/**
 * Client-side view state for the Buy Orders tab.
 * Tracks item selection, quantity/price drafts, pending actions, and cooldowns.
 */
public final class BuyTabState {
    private String selectedItemStringID;
    private String quantityDraft;
    private String priceDraft;
    private String lastSuccessfulItem;
    private String lastSuccessfulQuantity;
    private String lastSuccessfulPrice;
    private int pendingSlotIndex = -1;
    private BuyActionPendingType pendingType = BuyActionPendingType.NONE;
    private long pendingActionTimeoutMillis = 0L;
    private static final long PENDING_ACTION_TIMEOUT_MS = 10_000L;
    private long createCooldownExpiryMillis;
    private long toggleCooldownExpiryMillis;

    public void saveSelectedItem(String itemStringID) {
        this.selectedItemStringID = itemStringID;
        if (itemStringID != null && !itemStringID.isBlank()) {
            this.lastSuccessfulItem = itemStringID;
        }
    }

    public String getSelectedItemStringID() {
        if (selectedItemStringID != null && !selectedItemStringID.isBlank()) {
            return selectedItemStringID;
        }
        return lastSuccessfulItem;
    }

    public void saveQuantityDraft(String text) {
        if (text == null || text.isBlank()) {
            this.quantityDraft = null;
            return;
        }
        this.quantityDraft = text.trim();
    }

    public void savePriceDraft(String text) {
        if (text == null || text.isBlank()) {
            this.priceDraft = null;
            return;
        }
        this.priceDraft = text.trim();
    }

    public String getQuantityDraft(String fallback) {
        if (quantityDraft != null && !quantityDraft.isBlank()) {
            return quantityDraft;
        }
        if (lastSuccessfulQuantity != null && !lastSuccessfulQuantity.isBlank()) {
            return lastSuccessfulQuantity;
        }
        return fallback;
    }

    public String getPriceDraft(String fallback) {
        if (priceDraft != null && !priceDraft.isBlank()) {
            return priceDraft;
        }
        if (lastSuccessfulPrice != null && !lastSuccessfulPrice.isBlank()) {
            return lastSuccessfulPrice;
        }
        return fallback;
    }

    public void rememberSuccessfulDrafts(String itemStringID, String quantityText, String priceText) {
        if (itemStringID != null && !itemStringID.isBlank()) {
            lastSuccessfulItem = itemStringID;
        }
        if (quantityText != null && !quantityText.isBlank()) {
            lastSuccessfulQuantity = quantityText.trim();
        }
        if (priceText != null && !priceText.isBlank()) {
            lastSuccessfulPrice = priceText.trim();
        }
    }

    public void markPendingAction(int slotIndex, BuyActionPendingType type) {
        this.pendingSlotIndex = slotIndex;
        this.pendingType = type == null ? BuyActionPendingType.NONE : type;
        this.pendingActionTimeoutMillis = System.currentTimeMillis() + PENDING_ACTION_TIMEOUT_MS;
    }

    public void clearPendingAction(int slotIndex, BuyActionPendingType type) {
        if ((slotIndex == pendingSlotIndex || slotIndex < 0)
            && (type == pendingType || type == BuyActionPendingType.NONE)) {
            this.pendingSlotIndex = -1;
            this.pendingType = BuyActionPendingType.NONE;
            this.pendingActionTimeoutMillis = 0L;
        }
    }

    public void clearPendingAction() {
        clearPendingAction(-1, BuyActionPendingType.NONE);
    }

    public boolean isActionPending(int slotIndex, BuyActionPendingType type) {
        checkPendingTimeout();
        return pendingSlotIndex == slotIndex && pendingType == type;
    }

    public boolean hasPendingAction() {
        checkPendingTimeout();
        return pendingType != BuyActionPendingType.NONE;
    }

    private void checkPendingTimeout() {
        if (pendingType != BuyActionPendingType.NONE
            && pendingActionTimeoutMillis > 0L
            && System.currentTimeMillis() > pendingActionTimeoutMillis) {
            this.pendingSlotIndex = -1;
            this.pendingType = BuyActionPendingType.NONE;
            this.pendingActionTimeoutMillis = 0L;
        }
    }

    public int getPendingSlotIndex() {
        return pendingSlotIndex;
    }

    public BuyActionPendingType getPendingType() {
        return pendingType;
    }

    public void updateCooldowns(float createSeconds, float toggleSeconds) {
        long now = System.currentTimeMillis();
        this.createCooldownExpiryMillis = createSeconds <= 0
            ? 0L
            : now + (long) (createSeconds * 1000L);
        this.toggleCooldownExpiryMillis = toggleSeconds <= 0
            ? 0L
            : now + (long) (toggleSeconds * 1000L);
    }

    public float getCreateCooldownRemainingSeconds() {
        return clampRemaining(createCooldownExpiryMillis);
    }

    public float getToggleCooldownRemainingSeconds() {
        return clampRemaining(toggleCooldownExpiryMillis);
    }

    private float clampRemaining(long expiryMillis) {
        if (expiryMillis <= 0) {
            return 0f;
        }
        long remaining = expiryMillis - System.currentTimeMillis();
        if (remaining <= 0) {
            return 0f;
        }
        return remaining / 1000f;
    }

    public enum BuyActionPendingType {
        NONE,
        CREATE,
        ENABLE,
        DISABLE,
        CANCEL
    }
}
