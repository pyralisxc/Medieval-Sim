package medievalsim.grandexchange.ui.viewmodel;

import medievalsim.config.ModConfig;
import medievalsim.grandexchange.model.event.SellOfferSaleEvent;
import medievalsim.grandexchange.net.SellActionType;
import necesse.inventory.InventoryItem;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side view state for the Sell Offers tab.
 * Tracks staging slot state, price drafts, pending actions, cooldowns, and sale pulses.
 */
public final class SellTabState {
    private int stagingInventorySlotIndex;
    private int preferredDurationHours = ModConfig.GrandExchange.getDefaultSellDurationHours();
    private final Map<Integer, String> priceDrafts = new HashMap<>();
    private final Map<Integer, String> lastSuccessfulPrices = new HashMap<>();
    private final Map<Integer, Long> toggleCooldowns = new HashMap<>();
    private String lastGlobalSuccessfulPrice = null;
    private int pendingSlotIndex = -1;
    private SellActionPendingType pendingType = SellActionPendingType.NONE;
    private long pendingActionTimeoutMillis = 0L;
    private static final long PENDING_ACTION_TIMEOUT_MS = 10_000L;
    private InventoryItem pendingStagingSnapshot;
    private boolean autoClearEnabled;
    private boolean autoClearLocked;
    private final Map<Integer, SalePulse> salePulses = new HashMap<>();
    private static final long SALE_PULSE_DURATION_MS = 8000L;
    private long lastSaleTimestamp;

    public SellTabState(int stagingInventorySlotIndex, boolean autoClearEnabled) {
        this.stagingInventorySlotIndex = stagingInventorySlotIndex;
        this.autoClearEnabled = autoClearEnabled;
    }

    public int getStagingInventorySlotIndex() {
        return stagingInventorySlotIndex;
    }

    public void setStagingInventorySlotIndex(int stagingInventorySlotIndex) {
        this.stagingInventorySlotIndex = stagingInventorySlotIndex;
    }

    public int getPreferredDurationHours() {
        return preferredDurationHours;
    }

    public void setPreferredDurationHours(int preferredDurationHours) {
        this.preferredDurationHours = preferredDurationHours;
    }

    public void savePriceDraft(int slotIndex, String text) {
        if (slotIndex < 0) {
            return;
        }
        if (text == null || text.isBlank()) {
            priceDrafts.remove(slotIndex);
            return;
        }
        priceDrafts.put(slotIndex, text.trim());
    }

    public String getPriceDraft(int slotIndex, String fallback) {
        if (slotIndex >= 0) {
            if (priceDrafts.containsKey(slotIndex)) {
                return priceDrafts.get(slotIndex);
            }
            if (lastSuccessfulPrices.containsKey(slotIndex)) {
                return lastSuccessfulPrices.get(slotIndex);
            }
        }
        return lastGlobalSuccessfulPrice != null ? lastGlobalSuccessfulPrice : fallback;
    }

    public void clearPriceDraft(int slotIndex) {
        if (slotIndex >= 0) {
            priceDrafts.remove(slotIndex);
        }
    }

    public void markPendingAction(int slotIndex, SellActionPendingType type) {
        this.pendingSlotIndex = slotIndex;
        this.pendingType = type == null ? SellActionPendingType.NONE : type;
        this.pendingActionTimeoutMillis = System.currentTimeMillis() + PENDING_ACTION_TIMEOUT_MS;
    }

    public void clearPendingAction(int slotIndex, SellActionPendingType type) {
        if ((slotIndex == pendingSlotIndex || slotIndex < 0) && (type == pendingType || type == SellActionPendingType.NONE)) {
            this.pendingSlotIndex = -1;
            this.pendingType = SellActionPendingType.NONE;
            this.pendingActionTimeoutMillis = 0L;
        }
    }

    public void clearPendingAction() {
        clearPendingAction(-1, SellActionPendingType.NONE);
    }

    public boolean isActionPending(int slotIndex, SellActionPendingType type) {
        checkPendingTimeout();
        return pendingSlotIndex == slotIndex && pendingType == type;
    }

    public boolean hasPendingAction() {
        checkPendingTimeout();
        return pendingType != SellActionPendingType.NONE;
    }

    private void checkPendingTimeout() {
        if (pendingType != SellActionPendingType.NONE
            && pendingActionTimeoutMillis > 0L
            && System.currentTimeMillis() > pendingActionTimeoutMillis) {
            this.pendingSlotIndex = -1;
            this.pendingType = SellActionPendingType.NONE;
            this.pendingActionTimeoutMillis = 0L;
        }
    }

    public int getPendingSlotIndex() {
        return pendingSlotIndex;
    }

    public SellActionPendingType getPendingType() {
        return pendingType;
    }

    public void rememberSuccessfulPrice(int slotIndex, String priceText) {
        if (priceText == null || priceText.isBlank()) {
            return;
        }
        String normalized = priceText.trim();
        if (slotIndex >= 0) {
            lastSuccessfulPrices.put(slotIndex, normalized);
        }
        lastGlobalSuccessfulPrice = normalized;
    }

    public void lockToggleSlot(int slotIndex, long cooldownMillis) {
        if (slotIndex < 0) {
            return;
        }
        toggleCooldowns.put(slotIndex, System.currentTimeMillis() + Math.max(0, cooldownMillis));
    }

    public boolean isToggleLocked(int slotIndex) {
        Long expiry = toggleCooldowns.get(slotIndex);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expiry) {
            toggleCooldowns.remove(slotIndex);
            return false;
        }
        return true;
    }

    public long getToggleCooldownRemainingMillis(int slotIndex) {
        Long expiry = toggleCooldowns.get(slotIndex);
        if (expiry == null) {
            return 0L;
        }
        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0) {
            toggleCooldowns.remove(slotIndex);
            return 0L;
        }
        return remaining;
    }

    public void clearToggleLock(int slotIndex) {
        toggleCooldowns.remove(slotIndex);
    }

    public void stashPendingStagingItem(InventoryItem item) {
        pendingStagingSnapshot = item == null ? null : item.copy();
    }

    public InventoryItem consumePendingStagingItem() {
        if (pendingStagingSnapshot == null) {
            return null;
        }
        InventoryItem copy = pendingStagingSnapshot.copy();
        pendingStagingSnapshot = null;
        return copy;
    }

    public boolean hasPendingStagingItem() {
        return pendingStagingSnapshot != null;
    }

    public void clearPendingStagingItem() {
        pendingStagingSnapshot = null;
    }

    public boolean isAutoClearEnabled() {
        return autoClearEnabled;
    }

    public void setAutoClearEnabled(boolean enabled) {
        this.autoClearEnabled = enabled;
    }

    public boolean isAutoClearLocked() {
        return autoClearLocked;
    }

    public void setAutoClearLocked(boolean locked) {
        this.autoClearLocked = locked;
    }

    public void registerSalePulse(int slotIndex, SellOfferSaleEvent event) {
        if (slotIndex < 0 || event == null) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + SALE_PULSE_DURATION_MS;
        salePulses.put(slotIndex, new SalePulse(event, expiresAt));
        lastSaleTimestamp = event.timestamp();
    }

    public SalePulse getSalePulse(int slotIndex) {
        SalePulse pulse = salePulses.get(slotIndex);
        if (pulse == null) {
            return null;
        }
        if (pulse.isExpired()) {
            salePulses.remove(slotIndex);
            return null;
        }
        return pulse;
    }

    public boolean hasSalePulse(int slotIndex) {
        return getSalePulse(slotIndex) != null;
    }

    public void clearSalePulse(int slotIndex) {
        salePulses.remove(slotIndex);
    }

    public long getLastSaleTimestamp() {
        return lastSaleTimestamp;
    }

    public record SalePulse(SellOfferSaleEvent event, long expiresAt) {
        public boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }

        public int slotIndex() {
            return event.slotIndex();
        }
    }

    public enum SellActionPendingType {
        NONE,
        CREATE,
        ENABLE,
        DISABLE;

        public static SellActionPendingType fromAction(SellActionType action) {
            if (action == null) {
                return NONE;
            }
            return switch (action) {
                case CREATE -> CREATE;
                case ENABLE -> ENABLE;
                case DISABLE, CANCEL -> DISABLE;
            };
        }
    }
}
