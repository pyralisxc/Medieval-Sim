package medievalsim.grandexchange.ui.tabs;

import medievalsim.config.ModConfig;
import medievalsim.grandexchange.domain.GEOffer;
import medievalsim.grandexchange.domain.MarketInsightsSummary;
import medievalsim.grandexchange.model.event.SellOfferSaleEvent;
import medievalsim.grandexchange.model.snapshot.SellOfferSlotSnapshot;
import medievalsim.grandexchange.model.snapshot.SellOffersSnapshot;
import medievalsim.grandexchange.ui.GrandExchangeContainer;
import medievalsim.grandexchange.ui.components.TooltipFormCheckBox;
import medievalsim.grandexchange.ui.layout.GrandExchangeFonts;
import medievalsim.grandexchange.ui.util.TabHeartbeat;
import medievalsim.grandexchange.ui.viewmodel.GrandExchangeViewModel;
import medievalsim.grandexchange.ui.viewmodel.SellTabState;
import medievalsim.grandexchange.ui.viewmodel.SellTabState.SellActionPendingType;
import medievalsim.util.ModLogger;
import necesse.engine.localization.Localization;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.client.Client;
import necesse.engine.registries.ItemRegistry;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormDropdownSelectionButton;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormItemIcon;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.forms.components.containerSlot.FormContainerSlot;
import necesse.gfx.ui.ButtonColor;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;

import java.awt.Color;
import java.util.List;
import java.util.Locale;

/**
 * Encapsulates the sell-offer tab so the container form can delegate all UI
 * composition to a reusable builder that understands the view model state.
 */
public final class SellOffersTabView {

    private static final int SLOT_SIZE = 40;
    private static final int SLOT_SPACING = 5;
    private static final String DEFAULT_PRICE_TEXT = "100";

    private final HostContext host;
    private final GrandExchangeContainer container;
    private final GrandExchangeViewModel viewModel;

    public SellOffersTabView(HostContext host,
                             GrandExchangeContainer container,
                             GrandExchangeViewModel viewModel) {
        this.host = host;
        this.container = container;
        this.viewModel = viewModel;
    }

    public void build(int startY) {
        SellTabState state = viewModel.getSellTabState();
        SellOffersSnapshot snapshot = resolveSnapshot();
        state.setStagingInventorySlotIndex(container.getStagingInventorySlotIndex());
        boolean hasPendingAction = state.hasPendingAction();
        // Create and add heartbeat component for dynamic updates
        TabHeartbeat heartbeat = new TabHeartbeat();
        host.addComponent(heartbeat);
        int currentY = startY;
        int slotCount = snapshot.slotCapacity() > 0
            ? snapshot.slotCapacity()
            : ModConfig.GrandExchange.geInventorySlots;
        int usedSlots = countActiveSlots(snapshot);

        addHeaderLabel(Localization.translate("ui", "grandexchange.sell.header",
            "used", Integer.toString(usedSlots),
            "total", Integer.toString(slotCount)), currentY);
        addHintLabel(Localization.translate("ui", "grandexchange.sell.steps"), currentY + 25);

        currentY += 55;
        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.sell.placeitems"),
            GrandExchangeFonts.BODY, -1, 10, currentY));
        currentY += 20;
        currentY = buildStagingSlotSection(currentY, heartbeat);

        // Auto-clear checkbox removed - this setting is now in the Defaults tab

        currentY += 20;
        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.sell.priceperitem"),
                GrandExchangeFonts.BODY, -1, 10, currentY + 5));
        FormTextInput priceInput = new FormTextInput(130, currentY, FormInputSize.SIZE_32, 100, 10);
        priceInput.setText(resolveInitialPriceText(state));
        priceInput.setActive(!hasPendingAction);
        priceInput.onChange(event -> {
            SellTabState currentState = viewModel.getSellTabState();
            currentState.savePriceDraft(currentState.getStagingInventorySlotIndex(), priceInput.getText());
        });
        host.addComponent(priceInput);

        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.sell.coinsuffix"),
            GrandExchangeFonts.SMALL, -1, 235, currentY + 8));

        // Guide price label - shows suggested price based on recent trades
        FormLabel guidePriceLabel = new FormLabel("", GrandExchangeFonts.SMALL, -1, 290, currentY + 8);
        guidePriceLabel.setColor(GrandExchangeFonts.INFO_TEXT);
        host.addComponent(guidePriceLabel);
        heartbeat.registerTask(() -> updateGuidePriceLabel(guidePriceLabel));

        currentY += 45;
        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.sell.durationlabel"),
            GrandExchangeFonts.BODY, -1, 10, currentY + 5));
        FormDropdownSelectionButton<Integer> durationDropdown = buildDurationDropdown(130, currentY, state);
        host.addComponent(durationDropdown);

        currentY += 40;
        FormTextButton postBtn = new FormTextButton(Localization.translate("ui", "grandexchange.sell.postbutton"),
            10, currentY, 150, FormInputSize.SIZE_32, ButtonColor.GREEN);
        postBtn.onClicked(e -> handlePostOffer(priceInput, postBtn));
        host.addComponent(postBtn);
        updatePostButtonState(postBtn);
        attachStagingSlotMonitor(postBtn, heartbeat);

        // Feedback now goes to status bar only - no inline label needed
        currentY += 45; // Extra spacing to clear the Post button
        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.sell.offersection"),
            GrandExchangeFonts.HEADER, -1, 10, currentY));
        currentY += 25;
        
        // Create scrollable area for offer cards using 2-column grid layout
        // Form is ~700px tall, currentY is ~260px from top, leave margin for status bar
        int scrollAreaHeight = 280; // Reduced to trigger scroll with 3+ rows of offers
        int scrollAreaWidth = 860;
        FormContentBox offersScrollArea = new FormContentBox(0, currentY, scrollAreaWidth, scrollAreaHeight);
        offersScrollArea.shouldLimitDrawArea = true;
        host.addComponent(offersScrollArea);

        buildOfferCards(offersScrollArea, scrollAreaWidth, snapshot, heartbeat);
        
        // Fit content box to components to enable scrolling when content exceeds viewport
        offersScrollArea.fitContentBoxToComponents(5);
    }    private void addHeaderLabel(String text, int y) {
        host.addComponent(new FormLabel(text, GrandExchangeFonts.HEADER, -1, 10, y));
    }

    private void addHintLabel(String text, int y) {
        FormLabel hint = new FormLabel(text, GrandExchangeFonts.SMALL, -1, 10, y);
        hint.setColor(GrandExchangeFonts.SECONDARY_TEXT);
        host.addComponent(hint);
    }

    private SellOffersSnapshot resolveSnapshot() {
        SellOffersSnapshot snapshot = viewModel.getSellOffersSnapshot();
        if (snapshot == null) {
            snapshot = SellOffersSnapshot.empty(container.playerAuth, ModConfig.GrandExchange.geInventorySlots);
        }
        return snapshot;
    }

    private int resolveStagingSlotIndex() {
        SellTabState state = viewModel.getSellTabState();
        int slotIndex = state.getStagingInventorySlotIndex();
        if (slotIndex < 0) {
            slotIndex = container.getStagingInventorySlotIndex();
            state.setStagingInventorySlotIndex(slotIndex);
        }
        return slotIndex;
    }

    private boolean hasStagingItem() {
        return getCurrentStagingItem() != null;
    }

    private InventoryItem getCurrentStagingItem() {
        if (container.playerInventory == null) {
            return null;
        }
        int slotIndex = resolveStagingSlotIndex();
        if (slotIndex < 0) {
            return null;
        }
        return container.playerInventory.getSellInventory().getItem(slotIndex);
    }

    private boolean hasAvailableOfferCapacity(SellOffersSnapshot snapshot) {
        int capacity = snapshot != null && snapshot.slotCapacity() > 0
            ? snapshot.slotCapacity()
            : ModConfig.GrandExchange.geInventorySlots;
        return countActiveSlots(snapshot) < capacity;
    }

    private void updatePostButtonState(FormTextButton button) {
        if (button == null) {
            return;
        }
        SellTabState state = viewModel.getSellTabState();
        if (state.hasPendingAction()) {
            button.setActive(false);
            button.setTooltip(getAwaitingTooltip());
            return;
        }
        if (!hasAvailableOfferCapacity(resolveSnapshot())) {
            button.setActive(false);
            button.setTooltip(Localization.translate("ui", "grandexchange.sell.feedback.needslot"));
            return;
        }
        boolean staged = hasStagingItem();
        button.setActive(staged);
        if (!staged) {
            button.setTooltip(Localization.translate("ui", "grandexchange.sell.feedback.noitem"));
        } else {
            button.setTooltip(null);
        }
    }

    private void attachStagingSlotMonitor(FormTextButton postButton, TabHeartbeat heartbeat) {
        if (heartbeat != null) {
            heartbeat.registerTask(() -> updatePostButtonState(postButton));
        }
    }

    private int buildStagingSlotSection(int startY, TabHeartbeat heartbeat) {
        int slotIndex = container.GE_SELL_SLOTS_START;
        int cursorY = startY;
        int slotX = 10;
        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.sell.staging.header"),
            GrandExchangeFonts.BODY, -1, slotX, cursorY));
        cursorY += 18;
        if (slotIndex >= 0) {
            host.addComponent(new FormContainerSlot(host.getClient(), container, slotIndex, slotX, cursorY));
            FormLabel statusLabel = new FormLabel("", GrandExchangeFonts.SMALL, -1, slotX + SLOT_SIZE + 15, cursorY + 12);
            statusLabel.setColor(GrandExchangeFonts.SECONDARY_TEXT);
            host.addComponent(statusLabel);
            heartbeat.registerTask(() -> updateStagingStatus(statusLabel));
            return cursorY + SLOT_SIZE + SLOT_SPACING;
        } else {
            FormLabel warning = new FormLabel(Localization.translate("ui", "grandexchange.sell.feedback.needslot"),
                GrandExchangeFonts.SMALL, -1, slotX, cursorY);
            warning.setColor(Color.BLACK);
            host.addComponent(warning);
            return cursorY + 20;
        }
    }

    private void handlePostOffer(FormTextInput priceInput, FormTextButton postButton) {
        SellTabState state = viewModel.getSellTabState();
        if (state.hasPendingAction()) {
            host.showFeedback(Localization.translate("ui", "grandexchange.sell.feedback.pendingaction"), true);
            return;
        }

        try {
            SellOffersSnapshot snapshot = resolveSnapshot();
            if (!hasAvailableOfferCapacity(snapshot)) {
                host.showFeedback(Localization.translate("ui", "grandexchange.sell.feedback.needslot"), true);
                return;
            }

            int slotWithItem = resolveStagingSlotIndex();
            if (!hasStagingItem()) {
                host.showFeedback(Localization.translate("ui", "grandexchange.sell.feedback.noitem"), true);
                updatePostButtonState(postButton);
                return;
            }

            String priceText = priceInput.getText() == null ? "" : priceInput.getText().trim();
            if (priceText.isEmpty()) {
                host.showFeedback(Localization.translate("ui", "grandexchange.sell.feedback.needprice"), true);
                return;
            }

            int pricePerItem = Integer.parseInt(priceText);
            if (!ModConfig.GrandExchange.isValidPrice(pricePerItem)) {
                host.showFeedback(Localization.translate("ui", "grandexchange.sell.feedback.pricerange",
                    "min", Integer.toString(ModConfig.GrandExchange.minPricePerItem),
                    "max", Integer.toString(ModConfig.GrandExchange.maxPricePerItem)), true);
                return;
            }

            InventoryItem item = container.playerInventory.getSellInventory().getItem(slotWithItem);
            if (item == null) {
                host.showFeedback(Localization.translate("ui", "grandexchange.sell.feedback.noitem"), true);
                updatePostButtonState(postButton);
                return;
            }

            state.setStagingInventorySlotIndex(slotWithItem);
            state.savePriceDraft(slotWithItem, priceText);

            if (state.isAutoClearEnabled()) {
                state.stashPendingStagingItem(item);
                container.playerInventory.getSellInventory().setItem(slotWithItem, null);
            }

            ModLogger.info("Creating sell offer in slot %d: %s x%d @ %d coins/ea", slotWithItem,
                item.item.getStringID(), item.getAmount(), pricePerItem);
            host.showFeedback(Localization.translate("ui", "grandexchange.sell.feedback.posting",
                "slot", Integer.toString(slotWithItem + 1)), false);

            state.markPendingAction(slotWithItem, SellActionPendingType.CREATE);
            updatePostButtonState(postButton);
            priceInput.setActive(false);
            container.createSellOffer.runAndSend(slotWithItem, pricePerItem);
        } catch (NumberFormatException ex) {
            host.showFeedback(Localization.translate("ui", "grandexchange.sell.feedback.numeric"), true);
        }
    }

    private String resolveInitialPriceText(SellTabState state) {
        int slotIndex = resolveStagingSlotIndex();
        return state.getPriceDraft(slotIndex, DEFAULT_PRICE_TEXT);
    }

    private static final int CARD_WIDTH = 415;
    private static final int CARD_HEIGHT = 85;
    private static final int CARD_GAP = 10;
    private static final int CARDS_PER_ROW = 2;

    private void buildOfferCards(FormContentBox scrollArea, int areaWidth, SellOffersSnapshot snapshot, TabHeartbeat heartbeat) {
        List<SellOfferSlotSnapshot> slots = snapshot.slots();
        boolean hasOffers = false;
        int cardIndex = 0;
        
        if (slots != null) {
            for (SellOfferSlotSnapshot slot : slots) {
                if (slot == null || !slot.isOccupied()) {
                    continue;
                }
                hasOffers = true;
                int col = cardIndex % CARDS_PER_ROW;
                int row = cardIndex / CARDS_PER_ROW;
                int cardX = col * (CARD_WIDTH + CARD_GAP) + 5;
                int cardY = row * (CARD_HEIGHT + CARD_GAP) + 5;
                buildOfferCard(scrollArea, slot, slot.slotIndex(), cardX, cardY, heartbeat);
                cardIndex++;
            }
        }
        if (!hasOffers) {
            scrollArea.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.sell.nooffers"),
                    GrandExchangeFonts.SMALL, -1, 10, 10));
        }
    }

    private void buildOfferCard(FormContentBox scrollArea, SellOfferSlotSnapshot offer, int slotIndex, int cardX, int cardY, TabHeartbeat heartbeat) {
        SellTabState state = viewModel.getSellTabState();
        boolean globalPending = state.hasPendingAction();
        
        // Compact card layout: icon on left, info stacked on right, controls at bottom
        int iconX = cardX + 5;
        int contentX = cardX + 45; // Offset text to the right of icon

        // Add item icon with full tooltip on hover
        InventoryItem iconItem = buildOfferIcon(offer.itemStringID(), offer.quantityRemaining());
        if (iconItem != null) {
            FormItemIcon iconComponent = new FormItemIcon(iconX, cardY + 5, iconItem, false);
            scrollArea.addComponent(iconComponent);
        }

        // Line 1: Slot header (e.g., "Slot 1 - Active")
        FormLabel headerLabel = new FormLabel(formatOfferHeader(slotIndex, offer),
            GrandExchangeFonts.BODY, -1, contentX, cardY + 2);
        headerLabel.setColor(GrandExchangeFonts.PRIMARY_TEXT);
        scrollArea.addComponent(headerLabel);

        // Line 2: Progress info (e.g., "Selling Stone - 0/15 sold @ 100")
        FormLabel progressLabel = new FormLabel(formatOfferProgress(offer),
            GrandExchangeFonts.SMALL, -1, contentX, cardY + 18);
        progressLabel.setColor(GrandExchangeFonts.PRIMARY_TEXT);
        scrollArea.addComponent(progressLabel);

        // Line 3: Remaining + sale pulse combined
        FormLabel remainingLabel = new FormLabel(formatRemainingLine(offer),
            GrandExchangeFonts.SMALL, -1, contentX, cardY + 32);
        remainingLabel.setColor(GrandExchangeFonts.SECONDARY_TEXT);
        scrollArea.addComponent(remainingLabel);

        // Tax info - show expected proceeds after tax
        if (ModConfig.GrandExchange.salesTaxPercent > 0) {
            int sold = offer.quantityTotal() - offer.quantityRemaining();
            long grossRevenue = (long) sold * offer.pricePerItem();
            long taxAmount = (long) (grossRevenue * ModConfig.GrandExchange.salesTaxPercent);
            long netRevenue = grossRevenue - taxAmount;
            String taxInfo = Localization.translate("ui", "grandexchange.sell.taxinfo",
                "tax", String.format("%.0f", ModConfig.GrandExchange.salesTaxPercent * 100),
                "gross", Long.toString(grossRevenue),
                "net", Long.toString(netRevenue));
            FormLabel taxLabel = new FormLabel(taxInfo, GrandExchangeFonts.SMALL, -1, contentX + 180, cardY + 32);
            taxLabel.setColor(GrandExchangeFonts.INFO_TEXT);
            scrollArea.addComponent(taxLabel);
        }

        // Sale pulse on same line as remaining, offset to the right
        FormLabel salePulseLabel = new FormLabel("", GrandExchangeFonts.SMALL, -1, contentX + 120, cardY + 32);
        salePulseLabel.setColor(Color.BLACK);
        scrollArea.addComponent(salePulseLabel);
        if (heartbeat != null) {
            heartbeat.registerTask(() -> updateSalePulseLabel(salePulseLabel, slotIndex));
        }

        // Controls row at bottom of card
        int controlsY = cardY + 52;
        boolean canToggle = offer.state() == GEOffer.OfferState.DRAFT
            || offer.state() == GEOffer.OfferState.ACTIVE
            || offer.state() == GEOffer.OfferState.PARTIAL;
        TooltipFormCheckBox toggle = new TooltipFormCheckBox(
            Localization.translate("ui", "grandexchange.sell.toggle.label"), contentX, controlsY, offer.isActive());
        boolean toggleLocked = state.isToggleLocked(slotIndex);
        boolean enablePending = state.isActionPending(slotIndex, SellActionPendingType.ENABLE);
        boolean disablePending = state.isActionPending(slotIndex, SellActionPendingType.DISABLE);
        boolean toggleActive = canToggle && !toggleLocked && !enablePending && !disablePending && !globalPending;
        toggle.setActive(toggleActive);
        if (!toggleActive) {
            if (toggleLocked) {
                toggle.setTooltipSupplier(() -> formatCooldownTooltip(slotIndex));
            } else if (globalPending && !enablePending && !disablePending) {
                toggle.setTooltipSupplier(this::getGlobalPendingTooltip);
            } else {
                toggle.setTooltipSupplier(this::getAwaitingTooltip);
            }
        } else {
            toggle.clearTooltipSupplier();
        }
        toggle.onClicked(e -> handleToggle(offer, slotIndex, toggle, canToggle));
        scrollArea.addComponent(toggle);

        // Cancel button - position relative to card, not content offset
        FormTextButton cancelBtn = new FormTextButton(Localization.translate("ui", "grandexchange.sell.cancelbutton"),
              cardX + 300, controlsY, 100, FormInputSize.SIZE_24, ButtonColor.RED);
        boolean canCancel = offer.state() != GEOffer.OfferState.COMPLETED
            && offer.state() != GEOffer.OfferState.CANCELLED;
        boolean cancelEnabled = canCancel && !globalPending;
        cancelBtn.setActive(cancelEnabled);
        if (!canCancel) {
            cancelBtn.setTooltip(Localization.translate("ui", "grandexchange.sell.feedback.statefinalized"));
        } else if (globalPending) {
            cancelBtn.setTooltip(getGlobalPendingTooltip());
        } else {
            cancelBtn.setTooltip(Localization.translate("ui", "grandexchange.sell.canceltooltip"));
        }
        cancelBtn.onClicked(e -> {
            if (!canCancel || globalPending) {
                return;
            }
            container.cancelSellOffer.runAndSend(slotIndex);
        });
        scrollArea.addComponent(cancelBtn);
    }

    private void handleToggle(SellOfferSlotSnapshot offer, int slotIndex, TooltipFormCheckBox toggle, boolean canToggle) {
        SellTabState state = viewModel.getSellTabState();
        boolean previousActive = offer.isActive();
        if (!canToggle) {
            toggle.checked = previousActive;
            host.showFeedback(Localization.translate("ui", "grandexchange.sell.feedback.statefinalized"), true);
            return;
        }
        if (state.hasPendingAction()) {
            toggle.checked = previousActive;
            host.showFeedback(Localization.translate("ui", "grandexchange.sell.feedback.pendingaction"), true);
            return;
        }
        SellActionPendingType pendingType = toggle.checked ? SellActionPendingType.ENABLE : SellActionPendingType.DISABLE;
        state.markPendingAction(slotIndex, pendingType);
        toggle.setActive(false);
        toggle.setTooltipSupplier(this::getAwaitingTooltip);
        if (toggle.checked) {
            container.enableSellOffer.runAndSend(slotIndex);
        } else {
            container.disableSellOffer.runAndSend(slotIndex);
        }
    }

    private void updateStagingStatus(FormLabel label) {
        if (label == null) {
            return;
        }
        SellTabState state = viewModel.getSellTabState();
        if (state.hasPendingStagingItem()) {
            label.setText(Localization.translate("ui", "grandexchange.sell.staging.status.pending"));
            label.setColor(Color.BLACK);
            return;
        }
        InventoryItem item = getCurrentStagingItem();
        if (item == null || item.item == null || item.getAmount() <= 0) {
            label.setText(Localization.translate("ui", "grandexchange.sell.staging.status.empty"));
            label.setColor(GrandExchangeFonts.SECONDARY_TEXT);
            return;
        }
        label.setText(Localization.translate("ui", "grandexchange.sell.staging.status.item",
            "item", formatStagingItemName(item),
            "amount", Integer.toString(item.getAmount())));
        label.setColor(Color.BLACK);
    }

    private void updateGuidePriceLabel(FormLabel label) {
        if (label == null) {
            return;
        }
        InventoryItem item = getCurrentStagingItem();
        if (item == null || item.item == null) {
            label.setText("");
            return;
        }
        // Look up guide price from market insights
        int guidePrice = lookupGuidePrice(item.item.getStringID());
        if (guidePrice > 0) {
            label.setText(Localization.translate("ui", "grandexchange.sell.guideprice",
                "price", Integer.toString(guidePrice)));
            label.setColor(GrandExchangeFonts.INFO_TEXT);
        } else {
            label.setText(Localization.translate("ui", "grandexchange.sell.guideprice.nodata"));
            label.setColor(GrandExchangeFonts.SECONDARY_TEXT);
        }
    }

    private int lookupGuidePrice(String itemStringID) {
        if (itemStringID == null || itemStringID.isEmpty()) {
            return 0;
        }
        // Check market insights from the container's cached data
        MarketInsightsSummary insights = container.marketInsightsSummary;
        if (insights == null) {
            return 0;
        }
        // Check top volume items for this item's guide price
        for (MarketInsightsSummary.ItemInsight insight : insights.getTopVolumeItems()) {
            if (itemStringID.equals(insight.getItemStringID())) {
                return insight.getGuidePrice();
            }
        }
        // Check widest spread items as fallback
        for (MarketInsightsSummary.ItemInsight insight : insights.getWidestSpreadItems()) {
            if (itemStringID.equals(insight.getItemStringID())) {
                return insight.getGuidePrice();
            }
        }
        return 0;
    }

    private void updateSalePulseLabel(FormLabel label, int slotIndex) {
        if (label == null) {
            return;
        }
        SellTabState.SalePulse pulse = viewModel.getSellTabState().getSalePulse(slotIndex);
        if (pulse == null) {
            label.setText("");
            return;
        }
        SellOfferSaleEvent event = pulse.event();
        long totalCoinsLong = (long) event.quantitySold() * (long) event.pricePerItem();
        int totalCoins = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, totalCoinsLong));
        label.setText(Localization.translate("ui", "grandexchange.sell.pulse",
            "qty", Integer.toString(Math.max(0, event.quantitySold())),
            "coins", Integer.toString(totalCoins),
            "remaining", Integer.toString(Math.max(0, event.quantityRemaining()))));
        label.setColor(Color.BLACK);
    }

    // Auto-clear toggle methods removed - setting is now in Defaults tab only

    private String formatStagingItemName(InventoryItem item) {
        if (item == null || item.item == null) {
            return Localization.translate("ui", "grandexchange.sell.item.unknown");
        }
        return item.item.getDisplayName(item);
    }

    private String resolveItemName(SellOfferSlotSnapshot offer) {
        String itemId = offer.itemStringID();
        if (itemId == null || itemId.isEmpty()) {
            return Localization.translate("ui", "grandexchange.sell.item.unknown");
        }
        Item item = ItemRegistry.getItem(itemId);
        if (item == null) {
            return itemId;
        }
        InventoryItem displayItem = item.getDefaultItem(null, 1);
        return item.getDisplayName(displayItem);
    }

    /**
     * Creates an InventoryItem for displaying as an icon with full item tooltip.
     */
    private InventoryItem buildOfferIcon(String itemStringID, int quantity) {
        if (itemStringID == null || itemStringID.isEmpty()) {
            return null;
        }
        Item item = ItemRegistry.getItem(itemStringID);
        if (item == null) {
            return null;
        }
        int stackAmount = Math.max(1, Math.min(quantity, item.getStackSize()));
        InventoryItem iconItem = new InventoryItem(item, stackAmount);
        iconItem.setNew(false);
        return iconItem;
    }

    private int countActiveSlots(SellOffersSnapshot snapshot) {
        if (snapshot == null || snapshot.slots() == null) {
            return 0;
        }
        int used = 0;
        for (SellOfferSlotSnapshot slot : snapshot.slots()) {
            if (slot != null && slot.isOccupied() && !slot.isFinished()) {
                used++;
            }
        }
        return used;
    }

    private String formatOfferHeader(int slotIndex, SellOfferSlotSnapshot offer) {
        return Localization.translate("ui", "grandexchange.sell.offerheader",
            "slot", Integer.toString(slotIndex + 1),
            "state", formatOfferState(offer));
    }

    private String formatOfferProgress(SellOfferSlotSnapshot offer) {
        int sold = offer.quantityTotal() - offer.quantityRemaining();
        return Localization.translate("ui", "grandexchange.sell.offerprogress",
            "item", resolveItemName(offer),
            "sold", Integer.toString(Math.max(0, sold)),
            "total", Integer.toString(offer.quantityTotal()),
            "price", Integer.toString(offer.pricePerItem()));
    }

    private String formatRemainingLine(SellOfferSlotSnapshot offer) {
        return Localization.translate("ui", "grandexchange.sell.remainingline",
            "remaining", Integer.toString(offer.quantityRemaining()),
            "id", Long.toString(offer.offerId()));
    }

    private String formatOfferState(SellOfferSlotSnapshot offer) {
        if (offer == null) {
            return Localization.translate("ui", "grandexchange.sell.offerstate.empty");
        }
        return switch (offer.state()) {
            case DRAFT -> offer.enabled()
                ? Localization.translate("ui", "grandexchange.sell.offerstate.pending")
                : Localization.translate("ui", "grandexchange.sell.offerstate.drafthidden");
            case ACTIVE -> Localization.translate("ui", "grandexchange.sell.offerstate.active");
            case PARTIAL -> Localization.translate("ui", "grandexchange.sell.offerstate.partial");
            case COMPLETED -> Localization.translate("ui", "grandexchange.sell.offerstate.completed");
            case EXPIRED -> Localization.translate("ui", "grandexchange.sell.offerstate.expired");
            case CANCELLED -> Localization.translate("ui", "grandexchange.sell.offerstate.cancelled");
        };
    }

    /**
     * Duration options in hours for the sell offer duration selector.
     * Provides 1 day, 3 days, 7 days (default), and 14 days options.
     */
    private static final int[] DURATION_OPTIONS_HOURS = { 24, 72, 168, 336 };

    private FormDropdownSelectionButton<Integer> buildDurationDropdown(int x, int y, SellTabState state) {
        FormDropdownSelectionButton<Integer> dropdown = new FormDropdownSelectionButton<>(
            x, y,
            FormInputSize.SIZE_24,
            necesse.gfx.ui.ButtonColor.BASE,
            140,
            new StaticMessage(formatDurationLabel(state.getPreferredDurationHours()))
        );

        // Populate options
        int currentDuration = state.getPreferredDurationHours();
        int selectedIndex = 2; // Default to 7 days (index 2)
        
        for (int i = 0; i < DURATION_OPTIONS_HOURS.length; i++) {
            int hours = DURATION_OPTIONS_HOURS[i];
            String label = formatDurationLabel(hours);
            dropdown.options.add(hours, new StaticMessage(label));
            if (hours == currentDuration) {
                selectedIndex = i;
            }
        }

        // Set initial selection
        int initialHours = DURATION_OPTIONS_HOURS[selectedIndex];
        dropdown.setSelected(initialHours, new StaticMessage(formatDurationLabel(initialHours)));

        // Handle selection changes
        dropdown.onSelected(event -> {
            if (event.value != null) {
                state.setPreferredDurationHours(event.value);
            }
        });

        return dropdown;
    }

    private String formatDurationLabel(int hours) {
        if (hours <= 0) {
            return Localization.translate("ui", "grandexchange.sell.duration.none");
        }
        int days = hours / 24;
        int extraHours = hours % 24;
        if (extraHours == 0) {
            return Localization.translate("ui", "grandexchange.sell.duration.days",
                "days", Integer.toString(days),
                "plural", days == 1 ? "" : "s");
        }
        if (days == 0) {
            return Localization.translate("ui", "grandexchange.sell.duration.hours",
                "hours", Integer.toString(extraHours),
                "plural", extraHours == 1 ? "" : "s");
        }
        return Localization.translate("ui", "grandexchange.sell.duration.dayshours",
            "days", Integer.toString(days),
            "dayPlural", days == 1 ? "" : "s",
            "hours", Integer.toString(extraHours),
            "hourPlural", extraHours == 1 ? "" : "s");
    }

    private String formatCooldownTooltip(int slotIndex) {
        long remainingMillis = viewModel.getSellTabState().getToggleCooldownRemainingMillis(slotIndex);
        if (remainingMillis <= 0) {
            return null;
        }
        float seconds = Math.max(0f, remainingMillis / 1000f);
        return Localization.translate("ui", "grandexchange.sell.tooltips.cooldown",
            "seconds", String.format(Locale.US, "%.1f", seconds));
    }

    private String getGlobalPendingTooltip() {
        return Localization.translate("ui", "grandexchange.sell.tooltips.pendingglobal");
    }

    private String getAwaitingTooltip() {
        return Localization.translate("ui", "grandexchange.sell.tooltips.awaiting");
    }

    public interface HostContext {
        Client getClient();
        <C extends FormComponent> C addComponent(C component);
        void registerFeedbackLabel(FormLabel label);
        void showFeedback(String message, boolean isError);
    }
}
