package medievalsim.grandexchange.ui.viewmodel;

import medievalsim.config.ModConfig;
import medievalsim.grandexchange.model.snapshot.DefaultsConfigSnapshot;

/**
 * Client-side view state for the Defaults/Admin tab.
 * Tracks slot configuration drafts and admin settings.
 * Only visible to world owners.
 */
public final class DefaultsTabState {
    private String sellSlotDraft;
    private String buySlotDraft;
    private Boolean autoClearDraft;
    private boolean visible;
    private int sellSlotMin = 5;
    private int sellSlotMax = 20;
    private int configuredSellSlots;
    private int buySlotMin = 1;
    private int buySlotMax = 10;
    private int configuredBuySlots;
    private boolean autoClearAuthoritative = ModConfig.GrandExchange.autoClearSellStagingSlot;
    private int stagingSlotIndex;

    public DefaultsTabState(int initialSellSlots, int initialBuySlots) {
        this.sellSlotDraft = Integer.toString(initialSellSlots);
        this.buySlotDraft = Integer.toString(initialBuySlots);
        this.autoClearDraft = ModConfig.GrandExchange.autoClearSellStagingSlot;
        this.configuredSellSlots = initialSellSlots;
        this.configuredBuySlots = initialBuySlots;
        this.autoClearAuthoritative = ModConfig.GrandExchange.autoClearSellStagingSlot;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void applySnapshot(DefaultsConfigSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        this.visible = true;
        this.sellSlotMin = snapshot.sellSlotMin();
        this.sellSlotMax = snapshot.sellSlotMax();
        this.configuredSellSlots = snapshot.sellSlotConfigured();
        this.buySlotMin = snapshot.buySlotMin();
        this.buySlotMax = snapshot.buySlotMax();
        this.configuredBuySlots = snapshot.buySlotConfigured();
        this.autoClearAuthoritative = snapshot.autoClearEnabled();
        this.stagingSlotIndex = snapshot.stagingSlotIndex();
        this.sellSlotDraft = Integer.toString(configuredSellSlots);
        this.buySlotDraft = Integer.toString(configuredBuySlots);
        this.autoClearDraft = snapshot.autoClearEnabled();
    }

    public int getSellSlotMin() {
        return sellSlotMin;
    }

    public int getSellSlotMax() {
        return sellSlotMax;
    }

    public int getConfiguredSellSlots() {
        return configuredSellSlots;
    }

    public int getBuySlotMin() {
        return buySlotMin;
    }

    public int getBuySlotMax() {
        return buySlotMax;
    }

    public int getConfiguredBuySlots() {
        return configuredBuySlots;
    }

    public boolean isAutoClearEnabled() {
        return autoClearAuthoritative;
    }

    public int getStagingSlotIndex() {
        return stagingSlotIndex;
    }

    public void setAutoClearAuthoritative(boolean value) {
        this.autoClearAuthoritative = value;
        this.autoClearDraft = value;
    }

    public String getSellSlotDraft(String fallback) {
        return sellSlotDraft != null ? sellSlotDraft : fallback;
    }

    public void setSellSlotDraft(String text) {
        this.sellSlotDraft = text;
    }

    public String getBuySlotDraft(String fallback) {
        return buySlotDraft != null ? buySlotDraft : fallback;
    }

    public void setBuySlotDraft(String text) {
        this.buySlotDraft = text;
    }

    public boolean getAutoClearDraft(boolean fallback) {
        if (autoClearDraft != null) {
            return autoClearDraft;
        }
        return fallback;
    }

    public void setAutoClearDraft(Boolean value) {
        this.autoClearDraft = value;
    }
}
