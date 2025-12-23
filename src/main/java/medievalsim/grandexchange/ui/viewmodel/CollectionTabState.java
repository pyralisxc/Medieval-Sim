package medievalsim.grandexchange.ui.viewmodel;

import java.util.HashSet;
import java.util.Set;

/**
 * Client-side view state for the Collection/Settings tab.
 * Tracks pagination, pending collection actions, and user preferences.
 */
public final class CollectionTabState {
    private int pageIndex;
    private int pageSize;
    private int totalItems;
    private int totalPages;
    private boolean pendingPageRequest;
    private boolean pendingCollectAll;
    private boolean pendingDepositPreference;
    private boolean depositToBankPreferred;
    private boolean autoSendToBank;
    private boolean notifyPartialSales;
    private boolean playSoundOnSale;
    private boolean pendingAutoBank;
    private boolean pendingNotifyPartial;
    private boolean pendingPlaySound;
    private final Set<Integer> pendingLocalCollectSlots = new HashSet<>();

    public CollectionTabState(int pageIndex,
                              int pageSize,
                              int totalItems,
                              int totalPages,
                              boolean depositPreference,
                              boolean autoSendToBank,
                              boolean notifyPartialSales,
                              boolean playSoundOnSale) {
        updateMetadata(pageIndex, pageSize, totalItems, totalPages, depositPreference,
            autoSendToBank, notifyPartialSales, playSoundOnSale);
    }

    public void updateMetadata(int pageIndex,
                               int pageSize,
                               int totalItems,
                               int totalPages,
                               boolean depositPreference,
                               boolean autoSendToBank,
                               boolean notifyPartialSales,
                               boolean playSoundOnSale) {
        this.pageIndex = Math.max(0, pageIndex);
        this.pageSize = Math.max(1, pageSize);
        this.totalItems = Math.max(0, totalItems);
        this.totalPages = Math.max(1, totalPages);
        this.depositToBankPreferred = depositPreference;
        this.autoSendToBank = autoSendToBank;
        this.notifyPartialSales = notifyPartialSales;
        this.playSoundOnSale = playSoundOnSale;
        this.pendingPageRequest = false;
        this.pendingCollectAll = false;
        this.pendingDepositPreference = false;
        this.pendingAutoBank = false;
        this.pendingNotifyPartial = false;
        this.pendingPlaySound = false;
        this.pendingLocalCollectSlots.clear();
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public int getDisplayPage() {
        return pageIndex + 1;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean hasNextPage() {
        return pageIndex < totalPages - 1;
    }

    public boolean hasPreviousPage() {
        return pageIndex > 0;
    }

    public void requestPageChange() {
        this.pendingPageRequest = true;
    }

    public boolean isPageRequestPending() {
        return pendingPageRequest;
    }

    public void markCollectPending(int localIndex) {
        if (localIndex >= 0) {
            pendingLocalCollectSlots.add(localIndex);
        }
    }

    public void clearCollectPending(int localIndex) {
        if (localIndex >= 0) {
            pendingLocalCollectSlots.remove(localIndex);
        }
    }

    public boolean isCollectPending(int localIndex) {
        return pendingLocalCollectSlots.contains(localIndex);
    }

    public boolean hasCollectActionPending() {
        return pendingCollectAll || !pendingLocalCollectSlots.isEmpty();
    }

    public void markCollectAllPending() {
        this.pendingCollectAll = true;
    }

    public boolean isCollectAllPending() {
        return pendingCollectAll;
    }

    public void requestDepositPreferenceToggle() {
        this.pendingDepositPreference = true;
    }

    public boolean isDepositPreferencePending() {
        return pendingDepositPreference;
    }

    public boolean isDepositToBankPreferred() {
        return depositToBankPreferred;
    }

    public boolean isAutoSendToBankEnabled() {
        return autoSendToBank;
    }

    public boolean isNotifyPartialSalesEnabled() {
        return notifyPartialSales;
    }

    public boolean isPlaySoundOnSaleEnabled() {
        return playSoundOnSale;
    }

    public void requestAutoBankToggle() {
        this.pendingAutoBank = true;
    }

    public boolean isAutoBankPending() {
        return pendingAutoBank;
    }

    public void requestNotifyPartialToggle() {
        this.pendingNotifyPartial = true;
    }

    public boolean isNotifyPartialPending() {
        return pendingNotifyPartial;
    }

    public void requestPlaySoundToggle() {
        this.pendingPlaySound = true;
    }

    public boolean isPlaySoundPending() {
        return pendingPlaySound;
    }
}
