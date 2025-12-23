package medievalsim.grandexchange.ui.viewmodel;

/**
 * Client-side view state for the Market Browser tab.
 * Tracks search drafts, selected items, and pending purchase state.
 */
public final class MarketTabState {
    private String searchDraft;
    private String lastSelectedItemStringID;
    private long pendingOfferId = -1L;
    private long pendingPurchaseTimeoutMillis = 0L;
    private static final long PENDING_PURCHASE_TIMEOUT_MS = 10_000L;

    public void saveSearchDraft(String text) {
        if (text == null || text.isBlank()) {
            this.searchDraft = null;
            return;
        }
        this.searchDraft = text.trim();
    }

    public String getSearchDraft(String fallback) {
        if (searchDraft != null && !searchDraft.isBlank()) {
            return searchDraft;
        }
        return fallback;
    }

    public void rememberSelectedItem(String itemStringID) {
        if (itemStringID == null || itemStringID.isBlank()) {
            return;
        }
        this.lastSelectedItemStringID = itemStringID;
    }

    public String getLastSelectedItemStringID() {
        return lastSelectedItemStringID;
    }

    public void clearSelectedItem() {
        this.lastSelectedItemStringID = null;
    }

    public void markPendingPurchase(long offerId) {
        this.pendingOfferId = offerId;
        this.pendingPurchaseTimeoutMillis = System.currentTimeMillis() + PENDING_PURCHASE_TIMEOUT_MS;
    }

    public void clearPendingPurchase(long offerId) {
        if (offerId < 0 || this.pendingOfferId == offerId) {
            this.pendingOfferId = -1L;
            this.pendingPurchaseTimeoutMillis = 0L;
        }
    }

    public void clearPendingPurchase() {
        clearPendingPurchase(-1L);
    }

    public boolean isPurchasePending(long offerId) {
        checkPendingTimeout();
        return pendingOfferId >= 0 && pendingOfferId == offerId;
    }

    public boolean hasPendingPurchase() {
        checkPendingTimeout();
        return pendingOfferId >= 0;
    }

    private void checkPendingTimeout() {
        if (pendingOfferId >= 0
            && pendingPurchaseTimeoutMillis > 0L
            && System.currentTimeMillis() > pendingPurchaseTimeoutMillis) {
            this.pendingOfferId = -1L;
            this.pendingPurchaseTimeoutMillis = 0L;
        }
    }
}
