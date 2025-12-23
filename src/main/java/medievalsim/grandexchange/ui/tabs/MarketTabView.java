package medievalsim.grandexchange.ui.tabs;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import medievalsim.config.ModConfig;
import medievalsim.grandexchange.model.snapshot.MarketListingSnapshot;
import medievalsim.grandexchange.model.snapshot.MarketTabSnapshot;
import medievalsim.grandexchange.domain.MarketInsightsSummary;
import medievalsim.grandexchange.net.GEFeedbackChannel;
import medievalsim.grandexchange.net.GEFeedbackLevel;
import medievalsim.grandexchange.ui.GrandExchangeContainer;
import medievalsim.grandexchange.ui.viewmodel.GrandExchangeViewModel;
import medievalsim.grandexchange.ui.viewmodel.MarketTabState;
import medievalsim.grandexchange.ui.util.GrandExchangeUIUtils;
import medievalsim.grandexchange.ui.layout.GrandExchangeFonts;
import medievalsim.ui.dialogs.ConfirmationDialog;
import medievalsim.ui.helpers.SearchableDropdown;
import medievalsim.ui.fixes.InputFocusManager;
import necesse.engine.GlobalData;
import necesse.engine.localization.Localization;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.state.MainGame;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormDropdownSelectionButton;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormItemIcon;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.ui.ButtonColor;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemCategory;

/**
 * Rebuilds the market browser tab using shared host utilities so the container
 * form only handles tab switching.
 */
public final class MarketTabView {
    private final TabHostContext host;
    private final GrandExchangeContainer container;
    private final GrandExchangeViewModel viewModel;
    private final Consumer<SearchableDropdown<Item>> searchRegistrar;
    private final Consumer<FormLabel> feedbackRegistrar;
    private final BiConsumer<String, Boolean> feedbackChannel;

    public MarketTabView(TabHostContext host,
                         GrandExchangeContainer container,
                         GrandExchangeViewModel viewModel,
                         Consumer<SearchableDropdown<Item>> searchRegistrar,
                         Consumer<FormLabel> feedbackRegistrar,
                         BiConsumer<String, Boolean> feedbackChannel) {
        this.host = host;
        this.container = container;
        this.viewModel = viewModel;
        this.searchRegistrar = searchRegistrar;
        this.feedbackRegistrar = feedbackRegistrar;
        this.feedbackChannel = feedbackChannel;
    }

    public void build(int startY) {
        MarketTabState marketState = viewModel.getMarketTabState();
        MarketTabSnapshot snapshot = viewModel.getMarketSnapshot();
        int currentY = startY;

        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.market.header"), GrandExchangeFonts.HEADER, -1, 10, currentY));
        
        // Market Insights summary on right side of header
        MarketInsightsSummary insights = snapshot.insightsSummary();
        if (insights != null && insights.getTotalTradesLogged() > 0) {
            String insightsText = Localization.translate("ui", "grandexchange.market.insights.summary",
                "trades", Long.toString(insights.getTotalTradesLogged()),
                "volume", GrandExchangeUIUtils.formatCoins(insights.getTotalCoinsTraded()),
                "items", Integer.toString(insights.getTrackedItemCount()));
            FormLabel insightsLabel = new FormLabel(insightsText, GrandExchangeFonts.SMALL, -1, 450, currentY + 5);
            insightsLabel.setColor(GrandExchangeFonts.INFO_TEXT);
            host.addComponent(insightsLabel);
        }

        FormLabel hint = new FormLabel(Localization.translate("ui", "grandexchange.market.hint"), GrandExchangeFonts.SMALL, -1, 10, currentY += 25);
        hint.setColor(GrandExchangeFonts.SECONDARY_TEXT);
        host.addComponent(hint);

        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.market.searchlabel"), GrandExchangeFonts.BODY, -1, 10, currentY += 30));

        List<Item> allItems = ItemRegistry.streamItems()
            .filter(item -> item != null
                && !item.getStringID().endsWith("egg")
                && !item.getStringID().contains("summon")
                && !item.getStringID().endsWith("staff"))
            .collect(Collectors.toList());

        SearchableDropdown<Item> itemSearch = new SearchableDropdown<>(10, currentY += 25, 350, 300,
            Localization.translate("ui", "grandexchange.market.searchplaceholder"),
            allItems,
            item -> item.getDisplayName(item.getDefaultItem(null, 1)),
            item -> {
                marketState.rememberSelectedItem(item.getStringID());
                container.setMarketFilter.runAndSend(item.getStringID());
            });
        itemSearch.addToForm(host.getForm());
        InputFocusManager.EnhancedTextInput searchInput = itemSearch.getSearchInput();
        searchInput.setPlaceholder(Localization.translate("ui", "grandexchange.market.searchplaceholder"));
        searchInput.setText(marketState.getSearchDraft(""));
        searchInput.onChange(event -> {
            String text = searchInput.getText();
            marketState.saveSearchDraft(text);
            // Real-time partial filtering: 2+ chars triggers server filter, empty clears filter
            if (text.isEmpty() || text.length() >= 2) {
                container.setMarketFilter.runAndSend(text);
            }
        });
        String lastItemId = marketState.getLastSelectedItemStringID();
        if (lastItemId != null) {
            Item restored = ItemRegistry.getItem(lastItemId);
            if (restored != null) {
                itemSearch.setSelectedItem(restored);
            }
        }
        searchRegistrar.accept(itemSearch);

        // Clear filter button - resets search and shows all listings
        FormTextButton clearBtn = new FormTextButton(Localization.translate("ui", "grandexchange.market.clearfilter"),
            370, currentY, 80, FormInputSize.SIZE_24, ButtonColor.BASE);
        clearBtn.onClicked(e -> {
            searchInput.setText("");
            marketState.saveSearchDraft("");
            marketState.clearSelectedItem();
            itemSearch.clearSelection();
            container.setMarketFilter.runAndSend("");
        });
        host.addComponent(clearBtn);

        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.market.categorylabel"), GrandExchangeFonts.BODY, -1, 10, currentY += 45));

        String currentCategoryId = snapshot.category();
        FormDropdownSelectionButton<ItemCategory> categoryFilter = new FormDropdownSelectionButton<>(100, currentY - 5, FormInputSize.SIZE_24, ButtonColor.BASE, 200,
            new StaticMessage(describeCategoryButton(currentCategoryId)));
        categoryFilter.options.add(null, new StaticMessage(Localization.translate("ui", "grandexchange.market.category.all")));
        ItemCategory.masterCategory.streamChildren().forEach(category -> {
            if (category != null) {
                String displayName = category.displayName != null ? category.displayName.translate() : category.stringID;
                categoryFilter.options.add(category, new StaticMessage(displayName));
            }
        });
        categoryFilter.onSelected(event -> {
            ItemCategory selectedCat = event.value;
            String categoryId = selectedCat == null ? "all" : (selectedCat.stringID != null ? selectedCat.stringID : "all");
            container.setMarketCategory.runAndSend(categoryId);
            if (selectedCat == null) {
                itemSearch.updateItems(allItems);
            } else {
                List<Item> filteredItems = allItems.stream()
                    .filter(item -> isCategoryOrChild(ItemCategory.getItemsCategory(item), selectedCat))
                    .collect(Collectors.toList());
                itemSearch.updateItems(filteredItems);
            }
        });
        host.addComponent(categoryFilter);

        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.market.sortlabel"), GrandExchangeFonts.BODY, -1, 10, currentY += 40));

        String[] sortKeys = new String[]{"pricelow", "pricehigh", "qtyhigh", "timenew"};
        int sortX = 80;
        for (int i = 0; i < sortKeys.length; i++) {
            int sortMode = i + 1;
            ButtonColor color = snapshot.sortMode() == sortMode ? ButtonColor.GREEN : ButtonColor.BASE;
            FormTextButton sortBtn = new FormTextButton(Localization.translate("ui", "grandexchange.market.sort." + sortKeys[i]),
                sortX, currentY, 100, FormInputSize.SIZE_24, color);
            sortBtn.onClicked(e -> container.setMarketSort.runAndSend(sortMode));
            host.addComponent(sortBtn);
            sortX += 105;
        }

        // Feedback now goes to status bar only - no inline label needed
        List<MarketListingSnapshot> pageOffers = snapshot.listings();
        int showing = pageOffers.size();
        int total = snapshot.totalResults();
        int page = snapshot.page() + 1;
        int pages = Math.max(1, snapshot.totalPages());
        FormLabel listingsLabel = new FormLabel(Localization.translate("ui", "grandexchange.market.listingslabel",
            "shown", Integer.toString(showing),
            "total", Integer.toString(total),
            "page", Integer.toString(page),
            "pages", Integer.toString(pages)), GrandExchangeFonts.BODY, -1, 10, currentY += 35);
        host.addComponent(listingsLabel);

        currentY += 30;
        
        // Create scrollable area for market listings
        int scrollAreaHeight = 320;
        int scrollAreaWidth = 860;
        FormContentBox listingsScrollArea = new FormContentBox(0, currentY, scrollAreaWidth, scrollAreaHeight);
        listingsScrollArea.shouldLimitDrawArea = true;
        host.addComponent(listingsScrollArea);
        
        int scrollY = 5;
        if (pageOffers.isEmpty()) {
            FormLabel emptyLabel = new FormLabel(Localization.translate("ui", "grandexchange.market.empty"), GrandExchangeFonts.SMALL, -1, 20, scrollY);
            listingsScrollArea.addComponent(emptyLabel);
            scrollY += 25;
        } else {
            for (MarketListingSnapshot offer : pageOffers) {
                scrollY = buildMarketListingCard(listingsScrollArea, offer, scrollY, marketState) + 15;
            }
        }
        listingsScrollArea.fitContentBoxToComponents(5);
        
        currentY += scrollAreaHeight + 10;
        if (snapshot.page() > 0) {
            FormTextButton prevBtn = new FormTextButton(Localization.translate("ui", "grandexchange.market.pagination.prev"), 10, currentY, 120, FormInputSize.SIZE_32, ButtonColor.BASE);
            prevBtn.onClicked(e -> container.setMarketPage.runAndSend(snapshot.page() - 1));
            host.addComponent(prevBtn);
        }
        if (snapshot.page() < snapshot.totalPages() - 1) {
            FormTextButton nextBtn = new FormTextButton(Localization.translate("ui", "grandexchange.market.pagination.next"), 140, currentY, 120, FormInputSize.SIZE_32, ButtonColor.BASE);
            nextBtn.onClicked(e -> container.setMarketPage.runAndSend(snapshot.page() + 1));
            host.addComponent(nextBtn);
        }
    }

    private int buildMarketListingCard(FormContentBox scrollArea, MarketListingSnapshot offer, int startY, MarketTabState marketState) {
        int entryTop = startY;
        int iconX = 10;
        int textX = iconX + 42;
        int lineY = entryTop;
        InventoryItem iconItem = buildListingIcon(offer.itemStringID(), offer.quantityRemaining());
        if (iconItem != null) {
            FormItemIcon iconComponent = new FormItemIcon(iconX, entryTop, iconItem, false);
            scrollArea.addComponent(iconComponent);
        } else {
            textX = 15;
        }

        String itemName = getItemDisplayName(offer.itemStringID());
        FormLabel nameLabel = new FormLabel(itemName, GrandExchangeFonts.BODY, -1, textX, lineY);
        nameLabel.setColor(GrandExchangeFonts.PRIMARY_TEXT);
        scrollArea.addComponent(nameLabel);

        String qtyLine = Localization.translate("ui", "grandexchange.market.listing.qty",
            "remaining", Integer.toString(offer.quantityRemaining()),
            "total", Integer.toString(offer.quantityTotal()),
            "price", GrandExchangeUIUtils.formatCoins(offer.pricePerItem()));
        FormLabel qtyLabel = new FormLabel(qtyLine, GrandExchangeFonts.SMALL, -1, textX, lineY += 18);
        qtyLabel.setColor(GrandExchangeFonts.PRIMARY_TEXT);
        scrollArea.addComponent(qtyLabel);

        String metaLine = Localization.translate("ui", "grandexchange.market.listing.meta",
            "seller", resolveSellerLabel(offer),
            "expiration", formatExpiration(offer.expirationTime()));
        FormLabel metaLabel = new FormLabel(metaLine, GrandExchangeFonts.SMALL, -1, textX, lineY += 18);
        metaLabel.setColor(GrandExchangeFonts.SECONDARY_TEXT);
        scrollArea.addComponent(metaLabel);

        // Quantity selector row
        int qtyRowY = lineY += 22;
        int qtyRowX = textX;
        
        // Calculate max affordable quantity
        int maxAffordable = calculateMaxAffordable(offer.pricePerItem(), offer.quantityRemaining());
        int defaultQty = Math.min(offer.quantityRemaining(), maxAffordable > 0 ? maxAffordable : 1);
        
        // Quantity input field
        FormTextInput qtyInput = new FormTextInput(qtyRowX, qtyRowY - 2, FormInputSize.SIZE_20, 50, 5);
        qtyInput.setText(Integer.toString(defaultQty));
        scrollArea.addComponent(qtyInput);
        
        // Dynamic total price label (updates based on quantity input)
        FormLabel priceLabel = new FormLabel("", GrandExchangeFonts.SMALL, -1, qtyRowX + 55, qtyRowY + 2);
        scrollArea.addComponent(priceLabel);
        
        // Update price label based on current quantity
        Runnable updatePriceLabel = () -> {
            int qty = parseQuantity(qtyInput.getText(), 1);
            qty = Math.min(qty, offer.quantityRemaining());
            long totalCost = (long) qty * (long) offer.pricePerItem();
            boolean canAffordQty = container.clientCoinCount >= totalCost && qty > 0;
            String suffix = canAffordQty ? "" : Localization.translate("ui", "grandexchange.market.listing.total.insufficient");
            priceLabel.setText(Localization.translate("ui", "grandexchange.market.listing.total",
                "coins", GrandExchangeUIUtils.formatCoins(totalCost),
                "suffix", suffix));
            priceLabel.setColor(canAffordQty ? GrandExchangeFonts.SUCCESS_TEXT : GrandExchangeFonts.WARNING_TEXT);
        };
        updatePriceLabel.run();
        qtyInput.onChange(e -> updatePriceLabel.run());
        
        // Quick quantity buttons: 1, 10, 25, 100, Max
        int btnX = qtyRowX + 200;
        int[] quickAmounts = {1, 10, 25, 100};
        for (int amount : quickAmounts) {
            int clampedAmount = clampToBudget(amount, offer.pricePerItem(), offer.quantityRemaining());
            FormTextButton qtyBtn = new FormTextButton(Integer.toString(amount),
                btnX, qtyRowY - 2, 35, FormInputSize.SIZE_20, ButtonColor.BASE);
            qtyBtn.onClicked(e -> {
                qtyInput.setText(Integer.toString(clampedAmount));
                updatePriceLabel.run();
            });
            qtyBtn.setActive(clampedAmount > 0);
            scrollArea.addComponent(qtyBtn);
            btnX += 40;
        }
        
        // Max button
        FormTextButton maxBtn = new FormTextButton(Localization.translate("ui", "grandexchange.market.qty.max"),
            btnX, qtyRowY - 2, 45, FormInputSize.SIZE_20, ButtonColor.BASE);
        maxBtn.onClicked(e -> {
            int maxQty = clampToBudget(offer.quantityRemaining(), offer.pricePerItem(), offer.quantityRemaining());
            qtyInput.setText(Integer.toString(Math.max(1, maxQty)));
            updatePriceLabel.run();
        });
        maxBtn.setActive(maxAffordable > 0);
        scrollArea.addComponent(maxBtn);

        // Buy button
        FormTextButton buyBtn = new FormTextButton(Localization.translate("ui", "grandexchange.market.buybutton"),
            700, entryTop + 4, 80, FormInputSize.SIZE_24, ButtonColor.BASE);
        boolean pendingSame = marketState.isPurchasePending(offer.offerId());
        boolean pendingOther = marketState.hasPendingPurchase() && !pendingSame;
        boolean canAffordAny = maxAffordable > 0;
        boolean buyActive = canAffordAny && !pendingSame && !pendingOther;
        buyBtn.setActive(buyActive);
        if (!canAffordAny) {
            buyBtn.setTooltip(Localization.translate("ui", "grandexchange.market.tooltip.nofunds"));
        } else if (pendingSame) {
            buyBtn.setTooltip(getMarketAwaitingTooltip());
        } else if (pendingOther) {
            buyBtn.setTooltip(getMarketPendingTooltip());
        } else {
            buyBtn.setTooltip(null);
        }
        buyBtn.onClicked(e -> handleMarketPurchase(offer, qtyInput, buyBtn, marketState));
        scrollArea.addComponent(buyBtn);

        int entryHeight = Math.max(qtyRowY + 25 - entryTop, 70);
        return entryTop + entryHeight;
    }

    /**
     * Calculate maximum quantity affordable with current coins.
     */
    private int calculateMaxAffordable(int pricePerItem, int maxQuantity) {
        if (pricePerItem <= 0) {
            return maxQuantity;
        }
        long maxFromCoins = container.clientCoinCount / pricePerItem;
        return (int) Math.min(maxQuantity, Math.min(maxFromCoins, Integer.MAX_VALUE));
    }

    /**
     * Clamp requested quantity to what player can afford and what's available.
     */
    private int clampToBudget(int requestedQty, int pricePerItem, int maxAvailable) {
        int maxAffordable = calculateMaxAffordable(pricePerItem, maxAvailable);
        return Math.min(requestedQty, maxAffordable);
    }

    /**
     * Parse quantity from text input, with fallback.
     */
    private int parseQuantity(String text, int fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            int val = Integer.parseInt(text.trim());
            return val > 0 ? val : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void handleMarketPurchase(MarketListingSnapshot offer,
                                      FormTextInput qtyInput,
                                      FormTextButton buyBtn,
                                      MarketTabState marketState) {
        if (marketState.hasPendingPurchase() && !marketState.isPurchasePending(offer.offerId())) {
            feedbackChannel.accept(Localization.translate("ui", "grandexchange.market.feedback.pendingpurchase"), true);
            return;
        }

        // Parse and validate quantity
        int requestedQty = parseQuantity(qtyInput.getText(), 1);
        int maxAffordable = calculateMaxAffordable(offer.pricePerItem(), offer.quantityRemaining());
        int finalQty = Math.min(requestedQty, Math.min(offer.quantityRemaining(), maxAffordable));
        
        if (finalQty <= 0) {
            feedbackChannel.accept(Localization.translate("ui", "grandexchange.market.feedback.invalidqty"), true);
            return;
        }

        // Calculate total cost and check if large purchase confirmation is needed
        long totalCost = (long) finalQty * offer.pricePerItem();
        int threshold = ModConfig.GrandExchange.largePurchaseThreshold;
        
        if (threshold > 0 && totalCost >= threshold) {
            // Show confirmation dialog for large purchases
            String itemName = getItemDisplayName(offer.itemStringID());
            ConfirmationDialog dialog = ConfirmationDialog.forLargePurchase(
                itemName, finalQty, totalCost,
                confirmed -> {
                    if (confirmed) {
                        executePurchase(offer, finalQty, buyBtn, marketState);
                    }
                }
            );
            // Add dialog to form manager
            if (GlobalData.getCurrentState() instanceof MainGame) {
                MainGame mainGame = (MainGame) GlobalData.getCurrentState();
                mainGame.formManager.addComponent((FormComponent) dialog);
            }
        } else {
            // Small purchase - proceed directly
            executePurchase(offer, finalQty, buyBtn, marketState);
        }
    }

    /**
     * Execute the actual purchase after validation (and optional confirmation).
     */
    private void executePurchase(MarketListingSnapshot offer, int quantity, 
                                 FormTextButton buyBtn, MarketTabState marketState) {
        marketState.markPendingPurchase(offer.offerId());
        buyBtn.setActive(false);
        buyBtn.setTooltip(getMarketAwaitingTooltip());
        feedbackChannel.accept(Localization.translate("ui", "grandexchange.market.feedback.buying",
            "item", getItemDisplayName(offer.itemStringID()),
            "quantity", Integer.toString(quantity)), false);
        container.buyFromMarket.runAndSend(offer.offerId(), quantity);
    }    private String getItemDisplayName(String itemStringID) {
        return GrandExchangeUIUtils.getItemDisplayName(itemStringID);
    }

    private InventoryItem buildListingIcon(String itemStringID, int quantity) {
        Item item = ItemRegistry.getItem(itemStringID);
        if (item == null) {
            return null;
        }
        int stackAmount = Math.max(1, Math.min(quantity, item.getStackSize()));
        InventoryItem iconItem = new InventoryItem(item, stackAmount);
        iconItem.setNew(false);
        return iconItem;
    }

    private String resolveSellerLabel(MarketListingSnapshot offer) {
        if (offer.sellerName() != null && !offer.sellerName().isBlank()) {
            return offer.sellerName();
        }
        return String.format("Player #%d", offer.sellerAuth());
    }

    private String formatExpiration(long expirationTime) {
        return GrandExchangeUIUtils.formatExpiration(expirationTime);
    }

    private String getMarketPendingTooltip() {
        return Localization.translate("ui", "grandexchange.market.tooltips.pendingglobal");
    }

    private String getMarketAwaitingTooltip() {
        return Localization.translate("ui", "grandexchange.market.tooltips.awaiting");
    }

    private boolean isCategoryOrChild(ItemCategory itemCat, ItemCategory targetCat) {
        if (itemCat == null || targetCat == null) {
            return false;
        }
        ItemCategory current = itemCat;
        while (current != null) {
            if (current == targetCat || current.stringID.equals(targetCat.stringID)) {
                return true;
            }
            current = current.parent;
        }
        return false;
    }

    private String describeCategoryButton(String categoryId) {
        if (categoryId == null || categoryId.isBlank() || categoryId.equals("all")) {
            return Localization.translate("ui", "grandexchange.market.category.all");
        }
        ItemCategory match = ItemCategory.masterCategory.streamChildren()
            .filter(cat -> cat != null && categoryId.equals(cat.stringID))
            .findFirst()
            .orElse(null);
        if (match != null) {
            return match.displayName != null ? match.displayName.translate() : match.stringID;
        }
        return categoryId;
    }
}
