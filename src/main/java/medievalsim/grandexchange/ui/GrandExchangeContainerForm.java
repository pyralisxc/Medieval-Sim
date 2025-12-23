/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  necesse.engine.gameLoop.tickManager.TickManager
 *  necesse.engine.localization.message.GameMessage
 *  necesse.engine.localization.message.StaticMessage
 *  necesse.engine.network.Packet
 *  necesse.engine.network.client.Client
 *  necesse.engine.registries.ItemRegistry
 *  necesse.entity.mobs.PlayerMob
 *  necesse.gfx.forms.Form
 *  necesse.gfx.forms.components.FormCheckBox
 *  necesse.gfx.forms.components.FormComponent
 *  necesse.gfx.forms.components.FormDropdownSelectionButton
 *  necesse.gfx.forms.components.FormInputSize
 *  necesse.gfx.forms.components.FormItemIcon
 *  necesse.gfx.forms.components.FormLabel
 *  necesse.gfx.forms.components.FormTextButton
 *  necesse.gfx.forms.components.FormTextInput
 *  necesse.gfx.forms.components.containerSlot.FormContainerSlot
 *  necesse.gfx.forms.presets.containerComponent.ContainerForm
 *  necesse.gfx.gameFont.FontOptions
 *  necesse.gfx.ui.ButtonColor
 *  necesse.inventory.InventoryItem
 *  necesse.inventory.item.Item
 *  necesse.inventory.item.ItemCategory
 */
package medievalsim.grandexchange.ui;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import medievalsim.grandexchange.net.GEFeedbackChannel;
import medievalsim.grandexchange.net.SellActionResultCode;
import medievalsim.grandexchange.net.SellActionResultMessage;
import medievalsim.grandexchange.net.SellActionType;
import medievalsim.grandexchange.ui.components.StatusBarComponent;
import medievalsim.grandexchange.ui.tabs.CollectionTabView;
import medievalsim.grandexchange.ui.tabs.BuyOrdersTabView;
import medievalsim.grandexchange.ui.tabs.DefaultsTabView;
import medievalsim.grandexchange.ui.tabs.HistoryTabView;
import medievalsim.grandexchange.ui.tabs.MarketTabView;
import medievalsim.grandexchange.ui.tabs.SellOffersTabView;
import medievalsim.grandexchange.ui.tabs.TabHostContext;
import medievalsim.grandexchange.ui.layout.GrandExchangeFonts;
import medievalsim.grandexchange.ui.util.GrandExchangeUIUtils;
import medievalsim.grandexchange.ui.viewmodel.GrandExchangeViewModel;
import medievalsim.grandexchange.ui.viewmodel.HistoryTabState;
import medievalsim.grandexchange.ui.viewmodel.SellTabState;
import medievalsim.grandexchange.ui.viewmodel.SellTabState.SellActionPendingType;
import medievalsim.packets.PacketGEHistoryAck;
import medievalsim.ui.helpers.SearchableDropdown;
import medievalsim.ui.UIStyleConstants;
import medievalsim.util.ModLogger;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.Localization;
import necesse.engine.network.client.Client;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.presets.containerComponent.ContainerForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;

public class GrandExchangeContainerForm<T extends GrandExchangeContainer>
extends ContainerForm<T> {
    private FormTextButton marketTabButton;
    private FormTextButton buyOrdersTabButton;
    private FormTextButton sellOffersTabButton;
    private FormTextButton settingsTabButton;
    private FormTextButton historyTabButton;
    private FormTextButton defaultsTabButton;
    private FormLabel historyBadgeLabel;
    private List<FormComponent> sharedComponents = new ArrayList<FormComponent>();
    private List<FormComponent> tabComponents = new ArrayList<FormComponent>();
    private SearchableDropdown<Item> marketItemSearch;
    private SearchableDropdown<Item> buyOrderItemSearch;
    private FormLabel buyOrderFeedbackLabel;
    private FormLabel marketFeedbackLabel;
    private FormLabel sellOfferFeedbackLabel;
    private FormLabel collectionFeedbackLabel;
    private FormLabel historyFeedbackLabel;
    private FormLabel defaultsFeedbackLabel;
    private FormLabel coinLabel;
    private StatusBarComponent statusBar;
    private final GrandExchangeViewModel viewModel;
    private long lastHistoryAckTimestamp;
    // Throttle excessive rebuilds (prevent performance impact from rapid updates)
    private static final long REBUILD_THROTTLE_MS = 100L;
    private long lastRebuildTime = 0L;

    public GrandExchangeContainerForm(Client client, T container) {
        super(client, 900, 700, container);
        GrandExchangeContainer geContainer = (GrandExchangeContainer)container;
        this.viewModel = geContainer.getViewModel();
        ModLogger.info("[FORM INIT] Registering buy orders callback");
        geContainer.setBuyOrdersUpdateCallback(() -> {
            ModLogger.info("[FORM CALLBACK] Buy orders callback triggered - rebuilding tab");
            this.viewModel.getBuyTabState().clearPendingAction();
            if (this.rebuildCurrentTabThrottled()) {
                ModLogger.info("[FORM CALLBACK] Rebuild completed");
            }
        });
        ModLogger.info("[FORM INIT] Registering sell offers callback");
        geContainer.setSellOffersUpdateCallback(() -> {
            ModLogger.info("[FORM CALLBACK] Sell offers callback triggered - rebuilding tab");
            // Clear pending action as safety net (main clear is in SellActionResultCallback)
            this.viewModel.getSellTabState().clearPendingAction();
            if (this.rebuildCurrentTabThrottled()) {
                ModLogger.info("[FORM CALLBACK] Rebuild completed");
            }
        });
        ModLogger.info("[FORM INIT] Registering market listings callback");
        geContainer.setMarketListingsUpdateCallback(() -> {
            if (geContainer.activeTab == 0) {
                ModLogger.info("[FORM CALLBACK] Market listings callback triggered - rebuilding market tab");
                this.viewModel.getMarketTabState().clearPendingPurchase();
                if (this.rebuildCurrentTabThrottled()) {
                    ModLogger.info("[FORM CALLBACK] Market tab rebuild completed");
                }
            }
        });
        geContainer.setCollectionUpdateCallback(() -> {
            if (geContainer.activeTab == 3) {
                this.rebuildCurrentTab();
            }
        });
        geContainer.setHistoryIndicatorCallback(this::refreshHistoryTabLabel);
        geContainer.setHistoryUpdateCallback(() -> {
            if (geContainer.activeTab == 4) {
                this.viewModel.getHistoryTabState().markEntriesSeen();
                refreshHistoryTabLabel();
                sendHistoryAckIfNeeded();
                this.rebuildCurrentTab();
            }
        });
        geContainer.setCoinCountUpdateCallback(this::refreshCoinLabel);
        geContainer.setSellActionResultCallback(this::handleSellActionResult);
        this.buildUI();
    }

    public void draw(TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
        if (this.marketItemSearch != null && ((GrandExchangeContainer)this.container).activeTab == 0) {
            this.marketItemSearch.tick(tickManager);
        }
        if (this.buyOrderItemSearch != null && ((GrandExchangeContainer)this.container).activeTab == 1) {
            this.buyOrderItemSearch.tick(tickManager);
        }
        super.draw(tickManager, perspective, renderBox);
    }

    private void buildUI() {
        int currentY = 10;
        FormLabel titleLabel = new FormLabel(Localization.translate("ui", "grandexchangetitle"), UIStyleConstants.TITLE_FONT, -1, 10, currentY);
        this.addComponent((FormComponent)titleLabel);
        this.sharedComponents.add((FormComponent)titleLabel);
        this.coinLabel = new FormLabel(this.getCoinText((GrandExchangeContainer)this.container), UIStyleConstants.BODY_FONT, -1, this.getWidth() - 230, currentY + 5);
        this.coinLabel.setColor(GrandExchangeFonts.GOLD_TEXT);
        this.addComponent((FormComponent)this.coinLabel);
        this.sharedComponents.add((FormComponent)this.coinLabel);
        this.buildTabButtons(currentY += 35);
        this.buildTabContent(currentY += 42);
        this.buildStatusBar();
    }

    private void buildStatusBar() {
        // Status bar at the bottom of the form
        int statusBarY = this.getHeight() - 28;
        this.statusBar = new StatusBarComponent(0, statusBarY, this.getWidth(), () -> this.viewModel);
        this.addComponent(this.statusBar);
        this.sharedComponents.add(this.statusBar);
    }

    private void buildTabButtons(int yPos) {
        int currentX = 10;
        this.marketTabButton = new FormTextButton(Localization.translate("ui", "grandexchange.tab.market"), currentX, yPos, UIStyleConstants.BUTTON_WIDTH_MEDIUM, UIStyleConstants.BUTTON_SIZE_STANDARD, ((GrandExchangeContainer)this.container).activeTab == 0 ? ButtonColor.RED : ButtonColor.BASE);
        this.marketTabButton.onClicked(e -> this.switchToTab(0));
        this.addComponent((FormComponent)this.marketTabButton);
        this.sharedComponents.add((FormComponent)this.marketTabButton);
        this.buyOrdersTabButton = new FormTextButton(Localization.translate("ui", "grandexchange.tab.buyorders"), currentX += 155, yPos, UIStyleConstants.BUTTON_WIDTH_MEDIUM, UIStyleConstants.BUTTON_SIZE_STANDARD, ((GrandExchangeContainer)this.container).activeTab == 1 ? ButtonColor.RED : ButtonColor.BASE);
        this.buyOrdersTabButton.onClicked(e -> this.switchToTab(1));
        this.addComponent((FormComponent)this.buyOrdersTabButton);
        this.sharedComponents.add((FormComponent)this.buyOrdersTabButton);
        this.sellOffersTabButton = new FormTextButton(Localization.translate("ui", "grandexchange.tab.selloffers"), currentX += 155, yPos, UIStyleConstants.BUTTON_WIDTH_MEDIUM, UIStyleConstants.BUTTON_SIZE_STANDARD, ((GrandExchangeContainer)this.container).activeTab == 2 ? ButtonColor.RED : ButtonColor.BASE);
        this.sellOffersTabButton.onClicked(e -> this.switchToTab(2));
        this.addComponent((FormComponent)this.sellOffersTabButton);
        this.sharedComponents.add((FormComponent)this.sellOffersTabButton);
        this.settingsTabButton = new FormTextButton(Localization.translate("ui", "grandexchange.tab.collection"), currentX += 155, yPos, UIStyleConstants.BUTTON_WIDTH_MEDIUM, UIStyleConstants.BUTTON_SIZE_STANDARD, ((GrandExchangeContainer)this.container).activeTab == 3 ? ButtonColor.RED : ButtonColor.BASE);
        this.settingsTabButton.onClicked(e -> this.switchToTab(3));
        this.addComponent((FormComponent)this.settingsTabButton);
        this.sharedComponents.add((FormComponent)this.settingsTabButton);
        int historyButtonX = currentX += 155;
        this.historyTabButton = new FormTextButton(Localization.translate("ui", "grandexchange.tab.history"), historyButtonX, yPos, 150, UIStyleConstants.BUTTON_SIZE_STANDARD, ((GrandExchangeContainer)this.container).activeTab == 4 ? ButtonColor.RED : ButtonColor.BASE);
        this.historyTabButton.onClicked(e -> this.switchToTab(4));
        this.addComponent((FormComponent)this.historyTabButton);
        this.sharedComponents.add((FormComponent)this.historyTabButton);
        this.historyBadgeLabel = new FormLabel("", UIStyleConstants.BODY_FONT, -1, historyButtonX + 130, yPos - 6);
        this.historyBadgeLabel.setColor(Color.BLACK);
        this.addComponent((FormComponent)this.historyBadgeLabel);
        this.sharedComponents.add((FormComponent)this.historyBadgeLabel);
        currentX += 155;
        if (this.viewModel.getDefaultsTabState().isVisible()) {
            this.defaultsTabButton = new FormTextButton(Localization.translate("ui", "grandexchange.tab.defaults"), currentX, yPos, UIStyleConstants.BUTTON_WIDTH_MEDIUM, UIStyleConstants.BUTTON_SIZE_STANDARD, ((GrandExchangeContainer)this.container).activeTab == 5 ? ButtonColor.RED : ButtonColor.BASE);
            this.defaultsTabButton.onClicked(e -> this.switchToTab(5));
            this.addComponent((FormComponent)this.defaultsTabButton);
            this.sharedComponents.add((FormComponent)this.defaultsTabButton);
        }
        refreshHistoryTabLabel();
    }

    private void switchToTab(int tabIndex) {
        if (((GrandExchangeContainer)this.container).activeTab == tabIndex) {
            return;
        }
        if (tabIndex == 5 && !this.viewModel.getDefaultsTabState().isVisible()) {
            return;
        }
        ((GrandExchangeContainer)this.container).setActiveTab.runAndSend(tabIndex);
        if (tabIndex == 4) {
            this.viewModel.getHistoryTabState().markEntriesSeen();
            refreshHistoryTabLabel();
            sendHistoryAckIfNeeded();
        }
        this.updateTabUI(tabIndex);
    }

    private void sendHistoryAckIfNeeded() {
        if (this.client == null || this.client.network == null || this.viewModel == null) {
            return;
        }
        long ackTimestamp = Math.max(0L, this.viewModel.getHistoryTabState().getLastViewedTimestamp());
        if (ackTimestamp == 0L || ackTimestamp <= this.lastHistoryAckTimestamp) {
            return;
        }
        this.client.network.sendPacket(new PacketGEHistoryAck(ackTimestamp));
        this.lastHistoryAckTimestamp = ackTimestamp;
    }

    private void refreshHistoryTabLabel() {
        if (this.historyTabButton != null) {
            this.historyTabButton.setText(Localization.translate("ui", "grandexchange.tab.history"));
        }
        refreshHistoryBadgeIndicator();
    }

    private void refreshHistoryBadgeIndicator() {
        if (this.historyBadgeLabel == null) {
            return;
        }
        HistoryTabState historyState = this.viewModel.getHistoryTabState();
        boolean show = historyState != null && historyState.hasUnseenEntries();
        this.historyBadgeLabel.setText(show ? "*" : "");
    }

    private void updateTabUI(int newTab) {
        if (this.marketItemSearch != null) {
            this.marketItemSearch.removeFromForm((Form)this);
            this.marketItemSearch = null;
        }
        if (this.buyOrderItemSearch != null) {
            this.buyOrderItemSearch.removeFromForm((Form)this);
            this.buyOrderItemSearch = null;
        }
        for (FormComponent component : this.tabComponents) {
            this.removeComponent(component);
        }
        this.tabComponents.clear();
        for (FormComponent component : this.sharedComponents) {
            this.removeComponent(component);
        }
        this.sharedComponents.clear();
        this.historyBadgeLabel = null;
        this.buildUI();
    }

    /**
     * Throttles rebuild calls to prevent excessive UI rebuilds from rapid data updates.
     * If called within REBUILD_THROTTLE_MS of the last rebuild, the call is ignored.
     * 
     * @return true if rebuild was performed, false if throttled
     */
    private boolean rebuildCurrentTabThrottled() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRebuildTime < REBUILD_THROTTLE_MS) {
            ModLogger.debug("Throttled rebuild (called %dms after last rebuild)", currentTime - lastRebuildTime);
            return false;
        }
        lastRebuildTime = currentTime;
        rebuildCurrentTab();
        return true;
    }

    private void rebuildCurrentTab() {
        if (this.marketItemSearch != null) {
            this.marketItemSearch.removeFromForm((Form)this);
            this.marketItemSearch = null;
        }
        if (this.buyOrderItemSearch != null) {
            this.buyOrderItemSearch.removeFromForm((Form)this);
            this.buyOrderItemSearch = null;
        }
        for (FormComponent component : this.tabComponents) {
            this.removeComponent(component);
        }
        this.tabComponents.clear();
        int startY = 87;
        this.buildTabContent(startY);
        ModLogger.debug("Rebuilt tab %d after data update", ((GrandExchangeContainer)this.container).activeTab);
    }

    private <C extends FormComponent> C registerTabComponent(C component) {
        this.addComponent(component);
        this.tabComponents.add(component);
        return component;
    }

    private TabHostContext createTabHostContext() {
        return new TabHostContext(this::registerTabComponent, () -> this.client, this);
    }

    private void setMarketItemSearch(SearchableDropdown<Item> dropdown) {
        this.marketItemSearch = dropdown;
    }

    private void setBuyOrderItemSearch(SearchableDropdown<Item> dropdown) {
        this.buyOrderItemSearch = dropdown;
    }

    private void setBuyOrderFeedbackLabel(FormLabel label) {
        this.buyOrderFeedbackLabel = label;
    }

    private void setMarketFeedbackLabel(FormLabel label) {
        this.marketFeedbackLabel = label;
    }

    private void buildTabContent(int startY) {
        switch (((GrandExchangeContainer)this.container).activeTab) {
            case 0: {
                MarketTabView marketTab = new MarketTabView(
                    this.createTabHostContext(),
                    (GrandExchangeContainer)this.container,
                    this.viewModel,
                    this::setMarketItemSearch,
                    this::setMarketFeedbackLabel,
                    this::showMarketFeedback);
                marketTab.build(startY);
                break;
            }
            case 1: {
                BuyOrdersTabView buyTab = new BuyOrdersTabView(
                    this.createTabHostContext(),
                    (GrandExchangeContainer)this.container,
                    this.viewModel,
                    this::showBuyOrderFeedback,
                    this::setBuyOrderFeedbackLabel,
                    this::setBuyOrderItemSearch);
                buyTab.build(startY);
                break;
            }
            case 2: {
                this.buildSellOffersTab(startY);
                break;
            }
            case 3: {
                CollectionTabView collectionTab = new CollectionTabView(
                    this.createTabHostContext(),
                    (GrandExchangeContainer)this.container,
                    this.viewModel,
                    this::showCollectionFeedback,
                    label -> this.collectionFeedbackLabel = label);
                collectionTab.build(startY);
                break;
            }
            case 4: {
                HistoryTabView historyTab = new HistoryTabView(
                    this.createTabHostContext(),
                    this.viewModel,
                    this::showHistoryFeedback,
                    label -> this.historyFeedbackLabel = label,
                    this::rebuildCurrentTab);
                historyTab.build(startY);
                break;
            }
            case 5: {
                if (!this.viewModel.getDefaultsTabState().isVisible()) break;
                DefaultsTabView defaultsTab = new DefaultsTabView(
                    this.createTabHostContext(),
                    (GrandExchangeContainer)this.container,
                    this.viewModel,
                    this::showDefaultsFeedback,
                    label -> this.defaultsFeedbackLabel = label);
                defaultsTab.build(startY);
            }
        }
    }
    private void buildSellOffersTab(int startY) {
        SellOffersTabView tabView = new SellOffersTabView(
            this.createSellOffersHostContext(),
            (GrandExchangeContainer)this.container,
            this.viewModel);
        tabView.build(startY);
    }


    private SellOffersTabView.HostContext createSellOffersHostContext() {
        return new SellOffersTabView.HostContext() {
            @Override
            public Client getClient() {
                return GrandExchangeContainerForm.this.client;
            }

            @Override
            public <C extends FormComponent> C addComponent(C component) {
                return GrandExchangeContainerForm.this.registerTabComponent(component);
            }

            @Override
            public void registerFeedbackLabel(FormLabel label) {
                GrandExchangeContainerForm.this.sellOfferFeedbackLabel = label;
            }

            @Override
            public void showFeedback(String message, boolean isError) {
                GrandExchangeContainerForm.this.showSellOfferFeedback(message, isError);
            }
        };
    }

    private String getCoinText(GrandExchangeContainer container) {
        return Localization.translate("ui", "grandexchange.bank.balance")
                .replace("<coins>", GrandExchangeUIUtils.formatCoins(container.clientCoinCount));
    }

    private void refreshCoinLabel() {
        if (this.coinLabel != null) {
            this.coinLabel.setText(this.getCoinText((GrandExchangeContainer)this.container));
        }
    }

    private void showBuyOrderFeedback(String message, boolean isError) {
        viewModel.getFeedbackBus().post(GEFeedbackChannel.BUY, message, isError);
        applyFeedbackLabel(this.buyOrderFeedbackLabel, message, isError);
    }

    private void showMarketFeedback(String message, boolean isError) {
        viewModel.getFeedbackBus().post(GEFeedbackChannel.MARKET, message, isError);
        applyFeedbackLabel(this.marketFeedbackLabel, message, isError);
    }

    private void showSellOfferFeedback(String message, boolean isError) {
        viewModel.getFeedbackBus().post(GEFeedbackChannel.SELL, message, isError);
        applyFeedbackLabel(this.sellOfferFeedbackLabel, message, isError);
    }

    private void showCollectionFeedback(String message, boolean isError) {
        viewModel.getFeedbackBus().post(GEFeedbackChannel.COLLECTION, message, isError);
        applyFeedbackLabel(this.collectionFeedbackLabel, message, isError);
    }

    private void showHistoryFeedback(String message, boolean isError) {
        viewModel.getFeedbackBus().post(GEFeedbackChannel.HISTORY, message, isError);
        applyFeedbackLabel(this.historyFeedbackLabel, message, isError);
    }

    private void showDefaultsFeedback(String message, boolean isError) {
        viewModel.getFeedbackBus().post(GEFeedbackChannel.DEFAULTS, message, isError);
        applyFeedbackLabel(this.defaultsFeedbackLabel, message, isError);
    }

    private void applyFeedbackLabel(FormLabel label, String message, boolean isError) {
        // Feedback now goes only to the status bar via feedbackBus.post()
        // Inline labels are no longer used - status bar handles all feedback display
    }

    private void handleSellActionResult(SellActionResultMessage message) {
        if (message == null) {
            return;
        }
        GrandExchangeContainer geContainer = (GrandExchangeContainer)this.container;
        SellTabState state = this.viewModel.getSellTabState();
        SellActionPendingType pendingType = SellActionPendingType.fromAction(message.getAction());
        state.clearPendingAction(message.getSlotIndex(), pendingType);

        if (message.getAction() == SellActionType.CREATE) {
            if (message.isSuccess()) {
                state.clearPendingStagingItem();
            } else {
                InventoryItem snapshot = state.consumePendingStagingItem();
                if (snapshot != null && geContainer.playerInventory != null) {
                    int stagingSlot = geContainer.getStagingInventorySlotIndex();
                    if (stagingSlot >= 0) {
                        geContainer.playerInventory.getSellInventory().setItem(stagingSlot, snapshot);
                    }
                }
            }
        }

        if (message.isSuccess()) {
            state.clearToggleLock(message.getSlotIndex());
            if (message.getAction() == SellActionType.CREATE) {
                int priceSlot = message.getSlotIndex();
                String draftedPrice = state.getPriceDraft(priceSlot, null);
                if (draftedPrice == null) {
                    priceSlot = state.getStagingInventorySlotIndex();
                    draftedPrice = state.getPriceDraft(priceSlot, null);
                }
                state.rememberSuccessfulPrice(priceSlot, draftedPrice);
                state.clearPriceDraft(priceSlot);
            }
            this.showSellOfferFeedback(message.getMessage(), false);
        } else {
            if (message.getResult() == SellActionResultCode.TOGGLE_COOLDOWN || message.getResult() == SellActionResultCode.RATE_LIMITED) {
                state.lockToggleSlot(message.getSlotIndex(), (long) (Math.max(0f, message.getCooldownSeconds()) * 1000));
            }
            this.showSellOfferFeedback(message.getMessage(), true);
        }

        if (((GrandExchangeContainer)this.container).activeTab == 2) {
            this.rebuildCurrentTab();
        }
    }
}
