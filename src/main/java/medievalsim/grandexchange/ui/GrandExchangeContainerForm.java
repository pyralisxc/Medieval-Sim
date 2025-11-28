package medievalsim.grandexchange.ui;

import medievalsim.grandexchange.domain.GEOffer;
import medievalsim.grandexchange.domain.BuyOrder;
import medievalsim.grandexchange.domain.CollectionItem;
import medievalsim.grandexchange.domain.SaleNotification;
import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import medievalsim.ui.helpers.SearchableDropdown;
import medievalsim.packets.PacketGECreateBuyOrder;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemCategory;
import necesse.engine.network.client.Client;
import necesse.engine.registries.ItemRegistry;
import necesse.gfx.forms.presets.containerComponent.ContainerForm;
import necesse.gfx.forms.components.*;
import necesse.gfx.forms.components.containerSlot.FormContainerSlot;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;
import necesse.engine.localization.message.StaticMessage;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Grand Exchange Container Form - 4-Tab UI (Phase 12)
 * 
 * Tab 0: Market Browser - Browse and buy from active offers
 * Tab 1: Buy Orders - Create/manage buy orders (3 slots)
 * Tab 2: Sell Offers - Create/manage sell offers (10 slots)
 * Tab 3: Collection & Settings - Collect items, view history, configure settings
 */
public class GrandExchangeContainerForm<T extends GrandExchangeContainer> extends ContainerForm<T> {
    
    // Constants
    private static final int MARGIN = 10;
    private static final int TAB_BUTTON_WIDTH = 150;
    private static final int TAB_BUTTON_HEIGHT = 32;
    private static final int TAB_SPACING = 5;
    
    // Font options (uses default black text)
    private static final FontOptions TITLE_FONT = new FontOptions(20);
    private static final FontOptions HEADER_FONT = new FontOptions(16);
    private static final FontOptions NORMAL_FONT = new FontOptions(14);
    private static final FontOptions SMALL_FONT = new FontOptions(12);
    
    // Tab buttons
    private FormTextButton marketTabButton;
    private FormTextButton buyOrdersTabButton;
    private FormTextButton sellOffersTabButton;
    private FormTextButton settingsTabButton;
    private FormTextButton historyTabButton;
    private FormTextButton adminTabButton; // Only visible to world owner
    
    // Shared components (persist across tab switches)
    private List<FormComponent> sharedComponents = new ArrayList<>();
    
    // Tab-specific components (rebuilt on tab switch)
    private List<FormComponent> tabComponents = new ArrayList<>();
    
    // Searchable dropdowns for item filtering
    private SearchableDropdown<Item> marketItemSearch;
    private FormDropdownSelectionButton<ItemCategory> marketCategoryFilter;
    private SearchableDropdown<Item> buyOrderItemSearch;

    private FormLabel buyOrderFeedbackLabel;
    private FormLabel sellOfferFeedbackLabel;
    
    // Price cache for sell offers (persists between UI rebuilds)
    
    // Coin display
    private FormLabel coinLabel;
    
    public GrandExchangeContainerForm(Client client, T container) {
        super(client, 900, 700, container);
        
        // Register callbacks for data updates from server
        ModLogger.info("[FORM INIT] Registering buy orders callback");
        container.setBuyOrdersUpdateCallback(() -> {
            ModLogger.info("[FORM CALLBACK] Buy orders callback triggered - rebuilding tab");
            rebuildCurrentTab();
            ModLogger.info("[FORM CALLBACK] Rebuild completed");
        });
        
        ModLogger.info("[FORM INIT] Registering sell offers callback");
        container.setSellOffersUpdateCallback(() -> {
            ModLogger.info("[FORM CALLBACK] Sell offers callback triggered - rebuilding tab");
            rebuildCurrentTab();
            ModLogger.info("[FORM CALLBACK] Rebuild completed");
        });

        ModLogger.info("[FORM INIT] Registering market listings callback");
        container.setMarketListingsUpdateCallback(() -> {
            if (container.activeTab == 0) {
                ModLogger.info("[FORM CALLBACK] Market listings callback triggered - rebuilding market tab");
                rebuildCurrentTab();
                ModLogger.info("[FORM CALLBACK] Market tab rebuild completed");
            }
        });

        container.setCoinCountUpdateCallback(this::refreshCoinLabel);
        
        buildUI();
    }
    
    @Override
    public void draw(necesse.engine.gameLoop.tickManager.TickManager tickManager, necesse.entity.mobs.PlayerMob perspective, java.awt.Rectangle renderBox) {
        // Update SearchableDropdown based on active tab
        if (marketItemSearch != null && container.activeTab == 0) {
            marketItemSearch.tick(tickManager);
        }
        if (buyOrderItemSearch != null && container.activeTab == 1) {
            buyOrderItemSearch.tick(tickManager);
        }
        
        // Call parent draw
        super.draw(tickManager, perspective, renderBox);
    }
    
    /**
     * Build complete UI with tabs.
     */
    private void buildUI() {
        int currentY = MARGIN;
        
        // Title
        FormLabel titleLabel = new FormLabel("Grand Exchange", TITLE_FONT, -1, MARGIN, currentY);
        this.addComponent(titleLabel);
        sharedComponents.add(titleLabel);
        
        // Bank balance (top right)
        coinLabel = new FormLabel(getCoinText(container), NORMAL_FONT, -1, this.getWidth() - 200, currentY + 5);
        this.addComponent(coinLabel);
        sharedComponents.add(coinLabel);
        
        currentY += 35;
        
        // Tab buttons
        buildTabButtons(currentY);
        currentY += TAB_BUTTON_HEIGHT + MARGIN;
        
        // Tab content area
        buildTabContent(currentY);
    }
    
    /**
     * Build tab buttons at top.
     */
    private void buildTabButtons(int yPos) {
        int currentX = MARGIN;
        
        // Tab 0: Market
        marketTabButton = new FormTextButton(
            "Market",
            currentX, yPos,
            TAB_BUTTON_WIDTH, FormInputSize.SIZE_32,
            container.activeTab == 0 ? ButtonColor.RED : ButtonColor.BASE
        );
        marketTabButton.onClicked(e -> switchToTab(0));
        this.addComponent(marketTabButton);
        sharedComponents.add(marketTabButton);
        currentX += TAB_BUTTON_WIDTH + TAB_SPACING;
        
        // Tab 1: Buy Orders
        buyOrdersTabButton = new FormTextButton(
            "Buy Orders",
            currentX, yPos,
            TAB_BUTTON_WIDTH, FormInputSize.SIZE_32,
            container.activeTab == 1 ? ButtonColor.RED : ButtonColor.BASE
        );
        buyOrdersTabButton.onClicked(e -> switchToTab(1));
        this.addComponent(buyOrdersTabButton);
        sharedComponents.add(buyOrdersTabButton);
        currentX += TAB_BUTTON_WIDTH + TAB_SPACING;
        
        // Tab 2: Sell Offers
        sellOffersTabButton = new FormTextButton(
            "Sell Offers",
            currentX, yPos,
            TAB_BUTTON_WIDTH, FormInputSize.SIZE_32,
            container.activeTab == 2 ? ButtonColor.RED : ButtonColor.BASE
        );
        sellOffersTabButton.onClicked(e -> switchToTab(2));
        this.addComponent(sellOffersTabButton);
        sharedComponents.add(sellOffersTabButton);
        currentX += TAB_BUTTON_WIDTH + TAB_SPACING;
        
        // Tab 3: Collection
        settingsTabButton = new FormTextButton(
            "Collection",
            currentX, yPos,
            TAB_BUTTON_WIDTH, FormInputSize.SIZE_32,
            container.activeTab == 3 ? ButtonColor.RED : ButtonColor.BASE
        );
        settingsTabButton.onClicked(e -> switchToTab(3));
        this.addComponent(settingsTabButton);
        sharedComponents.add(settingsTabButton);
        currentX += TAB_BUTTON_WIDTH + TAB_SPACING;
        
        // Tab 4: History
        historyTabButton = new FormTextButton(
            "History",
            currentX, yPos,
            TAB_BUTTON_WIDTH, FormInputSize.SIZE_32,
            container.activeTab == 4 ? ButtonColor.RED : ButtonColor.BASE
        );
        historyTabButton.onClicked(e -> switchToTab(4));
        this.addComponent(historyTabButton);
        sharedComponents.add(historyTabButton);
        currentX += TAB_BUTTON_WIDTH + TAB_SPACING;
        
        // Tab 5: Admin Dashboard (only for world owner)
        if (isWorldOwner()) {
            adminTabButton = new FormTextButton(
                "Admin",
                currentX, yPos,
                TAB_BUTTON_WIDTH, FormInputSize.SIZE_32,
                container.activeTab == 5 ? ButtonColor.RED : ButtonColor.BASE
            );
            adminTabButton.onClicked(e -> switchToTab(5));
            this.addComponent(adminTabButton);
            sharedComponents.add(adminTabButton);
        }
    }
    
    /**
     * Switch to different tab.
     */
    private void switchToTab(int tabIndex) {
        if (container.activeTab == tabIndex) {
            return; // Already on this tab
        }
        
        // Update container state (server will echo back)
        container.setActiveTab.runAndSend(tabIndex);
        
        // Update UI immediately on client for responsiveness
        updateTabUI(tabIndex);
    }
    
    /**
     * Update tab UI after tab change.
     */
    private void updateTabUI(int newTab) {
        // Remove SearchableDropdown from previous tab
        if (marketItemSearch != null) {
            marketItemSearch.removeFromForm(this);
            marketItemSearch = null;
        }
        if (buyOrderItemSearch != null) {
            buyOrderItemSearch.removeFromForm(this);
            buyOrderItemSearch = null;
        }
        
        // Remove all components and rebuild
        for (FormComponent component : tabComponents) {
            this.removeComponent(component);
        }
        tabComponents.clear();
        
        for (FormComponent component : sharedComponents) {
            this.removeComponent(component);
        }
        sharedComponents.clear();
        
        // Rebuild full UI with new tab active
        buildUI();
    }
    
    /**
     * Rebuild current tab (called when server data updates).
     * Only rebuilds tab-specific content, keeps shared components.
     */
    private void rebuildCurrentTab() {
        // Remove SearchableDropdowns
        if (marketItemSearch != null) {
            marketItemSearch.removeFromForm(this);
            marketItemSearch = null;
        }
        if (buyOrderItemSearch != null) {
            buyOrderItemSearch.removeFromForm(this);
            buyOrderItemSearch = null;
        }
        
        // Remove only tab-specific components
        for (FormComponent component : tabComponents) {
            this.removeComponent(component);
        }
        tabComponents.clear();
        
        // Rebuild just the tab content
        int startY = MARGIN + 35 + TAB_BUTTON_HEIGHT + MARGIN;
        buildTabContent(startY);
        
        ModLogger.debug("Rebuilt tab %d after data update", container.activeTab);
    }
    
    /**
     * Build content for current active tab.
     */
    private void buildTabContent(int startY) {
        switch (container.activeTab) {
            case 0:
                buildMarketTab(startY);
                break;
            case 1:
                buildBuyOrdersTab(startY);
                break;
            case 2:
                buildSellOffersTab(startY);
                break;
            case 3:
                buildCollectionTab(startY);
                break;
            case 4:
                buildHistoryTab(startY);
                break;
            case 5:
                if (isWorldOwner()) {
                    buildAdminDashboardTab(startY);
                }
                break;
        }
    }
    
    // ===== TAB 0: MARKET BROWSER =====
    
    private void buildMarketTab(int startY) {
        int currentY = startY;
        
        // Header
        FormLabel header = new FormLabel("Browse Market Offers", HEADER_FONT, -1, MARGIN, currentY);
        this.addComponent(header);
        tabComponents.add(header);
        currentY += 25;

        FormLabel hint = new FormLabel(
            "Tip: Search or filter, then buy an offer to send items to your collection box.",
            SMALL_FONT, -1, MARGIN, currentY);
        hint.setColor(new Color(160, 160, 160));
        this.addComponent(hint);
        tabComponents.add(hint);
        currentY += 30;
        
        // Item search filter with dropdown (like command center)
        FormLabel searchLabel = new FormLabel("Search Item:", NORMAL_FONT, -1, MARGIN, currentY);
        this.addComponent(searchLabel);
        tabComponents.add(searchLabel);
        currentY += 25;
        
        // Build list of all items for searchable dropdown (exclude mobs/summons)
        List<Item> allItems = ItemRegistry.streamItems()
            .filter(item -> item != null && !item.getStringID().endsWith("egg") && 
                    !item.getStringID().contains("summon") && !item.getStringID().endsWith("staff"))
            .collect(Collectors.toList());
        
        // Create searchable dropdown for item filtering
        marketItemSearch = new SearchableDropdown<>(
            MARGIN, currentY,
            350, 300,
            "Type to search items...",
            allItems,
            item -> item.getDisplayName(item.getDefaultItem(null, 1)), // Display function
            item -> {
                // On item selected, set filter
                container.setMarketFilter.runAndSend(item.getStringID());
            }
        );
        marketItemSearch.addToForm(this);
        currentY += 45;
        
        // Category filter using Necesse's ItemCategory system
        FormLabel categoryLabel = new FormLabel("Category:", NORMAL_FONT, -1, MARGIN, currentY);
        this.addComponent(categoryLabel);
        tabComponents.add(categoryLabel);
        
        // Create dropdown with Necesse's categories
        marketCategoryFilter = new FormDropdownSelectionButton<>(
            MARGIN + 90, currentY - 5,
            FormInputSize.SIZE_24,
            ButtonColor.BASE,
            200,
            new StaticMessage("All Items")
        );
        
        // Add "All" option
        marketCategoryFilter.options.add(null, new StaticMessage("All Items"));
        
        // Add main Necesse categories
        ItemCategory.masterCategory.streamChildren().forEach(category -> {
            if (category != null) {
                String displayName = category.displayName != null ? 
                    category.displayName.translate() : category.stringID;
                marketCategoryFilter.options.add(category, new StaticMessage(displayName));
            }
        });
        
        // On category selected, update search dropdown
        marketCategoryFilter.onSelected(event -> {
            ItemCategory selectedCat = event.value;
            String categoryId = selectedCat == null ? "all" : (selectedCat.stringID != null ? selectedCat.stringID : "all");
            container.setMarketCategory.runAndSend(categoryId);
            if (selectedCat == null) {
                // All items
                marketItemSearch.updateItems(allItems);
            } else {
                // Filter items by category
                List<Item> filteredItems = allItems.stream()
                    .filter(item -> {
                        ItemCategory itemCat = ItemCategory.getItemsCategory(item);
                        return isCategoryOrChild(itemCat, selectedCat);
                    })
                    .collect(Collectors.toList());
                marketItemSearch.updateItems(filteredItems);
            }
        });
        
        this.addComponent(marketCategoryFilter);
        tabComponents.add(marketCategoryFilter);
        currentY += 40;
        
        // Sort buttons
        FormLabel sortLabel = new FormLabel("Sort:", NORMAL_FONT, -1, MARGIN, currentY);
        this.addComponent(sortLabel);
        tabComponents.add(sortLabel);
        
        int sortX = MARGIN + 70;
        String[] sortLabels = {"Price Low", "Price High", "Qty High", "Time New"};
        for (int i = 0; i < sortLabels.length; i++) {
            final int sortMode = i + 1;
            FormTextButton sortBtn = new FormTextButton(
                sortLabels[i],
                sortX, currentY,
                80, FormInputSize.SIZE_24,
                container.marketSort == sortMode ? ButtonColor.GREEN : ButtonColor.BASE
            );
            sortBtn.onClicked(e -> container.setMarketSort.runAndSend(sortMode));
            this.addComponent(sortBtn);
            tabComponents.add(sortBtn);
            sortX += 85;
        }
        currentY += 35;
        
        // Listings display area
        FormLabel listingsLabel = new FormLabel(
            String.format("Showing %d of %d offers (Page %d/%d):", 
                container.marketListings.size(),
                container.marketTotalResults,
                container.marketPage + 1,
                Math.max(1, container.getTotalPages())),
            NORMAL_FONT, -1, MARGIN, currentY
        );
        this.addComponent(listingsLabel);
        tabComponents.add(listingsLabel);
        currentY += 30;
        
        // Display current page listings
        List<GrandExchangeContainer.MarketListingView> pageOffers = container.getCurrentPageListings();
        for (GrandExchangeContainer.MarketListingView offer : pageOffers) {
            String itemName = getItemDisplayName(offer.itemStringID);
            long totalPrice = Math.max(0L, offer.getTotalPrice());
            boolean canAfford = container.clientCoinCount >= totalPrice && totalPrice > 0;
            String qtyLine = String.format("Available %d/%d @ %s coins ea",
                offer.quantityRemaining,
                offer.quantityTotal,
                formatCoins(offer.pricePerItem));
            String totalLine = String.format("Total %s coins%s",
                formatCoins(totalPrice),
                canAfford ? "" : " (insufficient funds)");
            String metaLine = String.format("Seller %s • %s",
                resolveSellerLabel(offer),
                formatExpiration(offer.expirationTime));

            int entryTop = currentY;
            int iconX = MARGIN;
            int textX = iconX + 42;
            int lineY = entryTop;

            InventoryItem iconItem = buildListingIcon(offer.itemStringID, offer.quantityRemaining);
            if (iconItem != null) {
                FormItemIcon iconComponent = new FormItemIcon(iconX, entryTop, iconItem, false);
                this.addComponent(iconComponent);
                tabComponents.add(iconComponent);
            } else {
                textX = MARGIN + 5;
            }

            FormLabel itemLabel = new FormLabel(itemName, NORMAL_FONT, -1, textX, lineY);
            this.addComponent(itemLabel);
            tabComponents.add(itemLabel);
            lineY += 18;

            FormLabel availabilityLabel = new FormLabel(qtyLine, SMALL_FONT, -1, textX, lineY);
            this.addComponent(availabilityLabel);
            tabComponents.add(availabilityLabel);
            lineY += 18;

            FormLabel priceLabel = new FormLabel(totalLine, SMALL_FONT, -1, textX, lineY);
            priceLabel.setColor(canAfford ? new Color(70, 190, 90) : new Color(230, 170, 80));
            this.addComponent(priceLabel);
            tabComponents.add(priceLabel);
            lineY += 18;

            FormLabel metaLabel = new FormLabel(metaLine, SMALL_FONT, -1, textX, lineY);
            metaLabel.setColor(new Color(160, 160, 160));
            this.addComponent(metaLabel);
            tabComponents.add(metaLabel);

            // Buy button anchored near icon
            FormTextButton buyBtn = new FormTextButton(
                "Buy",
                700, entryTop + 4,
                80, FormInputSize.SIZE_24,
                ButtonColor.BASE
            );
            buyBtn.setActive(canAfford);
            if (!canAfford) {
                buyBtn.setTooltip("Not enough coins in bank");
            }
            final long offerId = offer.offerId;
            buyBtn.onClicked(e -> container.buyFromMarket.runAndSend(offerId));
            this.addComponent(buyBtn);
            tabComponents.add(buyBtn);

            int entryHeight = Math.max((lineY + 20) - entryTop, 48);
            currentY = entryTop + entryHeight + 15;
            
            if (currentY > 600) break; // Don't overflow screen
        }
        
        // Pagination buttons
        currentY += 10;
        if (container.marketPage > 0) {
            FormTextButton prevBtn = new FormTextButton(
                "← Previous",
                MARGIN, currentY,
                100, FormInputSize.SIZE_32,
                ButtonColor.BASE
            );
            prevBtn.onClicked(e -> container.setMarketPage.runAndSend(container.marketPage - 1));
            this.addComponent(prevBtn);
            tabComponents.add(prevBtn);
        }
        
        if (container.marketPage < container.getTotalPages() - 1) {
            FormTextButton nextBtn = new FormTextButton(
                "Next →",
                MARGIN + 110, currentY,
                100, FormInputSize.SIZE_32,
                ButtonColor.BASE
            );
            nextBtn.onClicked(e -> container.setMarketPage.runAndSend(container.marketPage + 1));
            this.addComponent(nextBtn);
            tabComponents.add(nextBtn);
        }
    }
    
    // ===== TAB 1: BUY ORDERS =====
    
    private void buildBuyOrdersTab(int startY) {
        int currentY = startY;
        
        // Header with slot counter
        BuyOrder[] allOrders = container.playerInventory.getBuyOrders();
        int usedSlots = 0;
        int maxSlots = ModConfig.GrandExchange.buyOrderSlots;
        for (BuyOrder order : allOrders) {
            if (order != null && (order.getState() == BuyOrder.BuyOrderState.DRAFT || 
                                  order.getState() == BuyOrder.BuyOrderState.ACTIVE || 
                                  order.getState() == BuyOrder.BuyOrderState.PARTIAL)) {
                usedSlots++;
            }
        }
        
        FormLabel header = new FormLabel(
            String.format("Create Buy Order (%d/%d slots used)", usedSlots, maxSlots), 
            HEADER_FONT, -1, MARGIN, currentY
        );
        this.addComponent(header);
        tabComponents.add(header);
        currentY += 25;

        FormLabel hint = new FormLabel(
            "Steps: pick an item, set quantity & price, submit, then enable the slot to escrow coins.",
            SMALL_FONT, -1, MARGIN, currentY
        );
        hint.setColor(new Color(160, 160, 160));
        this.addComponent(hint);
        tabComponents.add(hint);
        currentY += 30;
        
        // ==== CREATION UI ====
        
        // Item search dropdown
        FormLabel itemLabel = new FormLabel("Item:", NORMAL_FONT, -1, MARGIN, currentY);
        this.addComponent(itemLabel);
        tabComponents.add(itemLabel);
        currentY += 25;
        
        List<Item> allItems = ItemRegistry.streamItems()
            .filter(item -> item != null && !item.getStringID().endsWith("egg") && 
                    !item.getStringID().contains("summon") && !item.getStringID().endsWith("staff"))
            .collect(Collectors.toList());
        
        // Store selected item reference
        final Item[] selectedItemHolder = new Item[1];
        
        buyOrderItemSearch = new SearchableDropdown<>(
            MARGIN, currentY,
            300, 250,
            "Type to search items...",
            allItems,
            item -> item.getDisplayName(item.getDefaultItem(null, 1)),
            item -> {
                // Store selected item for buy order creation
                selectedItemHolder[0] = item;
                ModLogger.debug("Selected item for buy order: %s", item.getStringID());
            }
        );
        buyOrderItemSearch.addToForm(this);
        currentY += 45;
        
        // Quantity and Price inputs on same row
        FormLabel qtyLabel = new FormLabel("Quantity:", NORMAL_FONT, -1, MARGIN, currentY + 5);
        this.addComponent(qtyLabel);
        tabComponents.add(qtyLabel);
        
        FormTextInput qtyInput = new FormTextInput(
            MARGIN + 80, currentY,
            FormInputSize.SIZE_32, 80, 8
        );
        qtyInput.setText("1");
        this.addComponent(qtyInput);
        tabComponents.add(qtyInput);
        
        FormLabel priceLabel = new FormLabel("Price/item:", NORMAL_FONT, -1, MARGIN + 180, currentY + 5);
        this.addComponent(priceLabel);
        tabComponents.add(priceLabel);
        
        FormTextInput priceInput = new FormTextInput(
            MARGIN + 270, currentY,
            FormInputSize.SIZE_32, 100, 10
        );
        priceInput.setText("100");
        this.addComponent(priceInput);
        tabComponents.add(priceInput);
        
        FormLabel coinsLabel = new FormLabel("coins", SMALL_FONT, -1, MARGIN + 375, currentY + 8);
        this.addComponent(coinsLabel);
        tabComponents.add(coinsLabel);
        currentY += 45;
        
        // Make Buy Order button
        FormTextButton createBtn = new FormTextButton(
            "Make Buy Order",
            MARGIN, currentY,
            200, FormInputSize.SIZE_32,
            ButtonColor.GREEN
        );
        createBtn.onClicked(e -> {
            try {
                // Validate item selected
                if (selectedItemHolder[0] == null) {
                    ModLogger.warn("No item selected for buy order");
                    showBuyOrderFeedback("Select an item before creating a buy order", true);
                    return;
                }
                
                // Parse quantity
                int quantity = Integer.parseInt(qtyInput.getText().trim());
                if (quantity <= 0 || quantity > 9999) {
                    ModLogger.warn("Invalid quantity: %d", quantity);
                    showBuyOrderFeedback("Quantity must be between 1 and 9999", true);
                    return;
                }
                
                // Parse price
                int price = Integer.parseInt(priceInput.getText().trim());
                if (price <= 0) {
                    ModLogger.warn("Invalid price: %d", price);
                    showBuyOrderFeedback("Enter a price above zero", true);
                    return;
                }
                
                // Find next available buy order slot (0-2)
                int availableSlot = -1;
                BuyOrder[] existingOrders = container.playerInventory.getBuyOrders();
                for (int i = 0; i < existingOrders.length; i++) {
                    if (existingOrders[i] == null) {
                        // Empty slot - perfect
                        availableSlot = i;
                        break;
                    }
                    BuyOrder.BuyOrderState state = existingOrders[i].getState();
                    if (state == BuyOrder.BuyOrderState.COMPLETED || 
                        state == BuyOrder.BuyOrderState.EXPIRED || 
                        state == BuyOrder.BuyOrderState.CANCELLED) {
                        // Finished order - can be replaced
                        availableSlot = i;
                        break;
                    }
                    // DRAFT, ACTIVE, PARTIAL - skip to next slot
                }
                
                if (availableSlot == -1) {
                    ModLogger.warn("No available buy order slots (max=%d)", ModConfig.GrandExchange.buyOrderSlots);
                    showBuyOrderFeedback("All buy order slots are in use", true);
                    return;
                }
                
                ModLogger.info("Creating buy order in slot %d: %s x%d @ %d coins", 
                    availableSlot, selectedItemHolder[0].getStringID(), quantity, price);
                showBuyOrderFeedback(String.format("Submitting slot %d: %d x %s @ %d", 
                    availableSlot + 1,
                    quantity,
                    selectedItemHolder[0].getStringID(),
                    price), false);
                
                // Send packet to server to create buy order
                PacketGECreateBuyOrder packet = new PacketGECreateBuyOrder(
                    availableSlot, 
                    selectedItemHolder[0].getStringID(), 
                    quantity, 
                    price, 
                    7 // Default 7 days duration
                );
                client.network.sendPacket(packet);
                
            } catch (NumberFormatException ex) {
                ModLogger.warn("Invalid number format in buy order inputs");
                showBuyOrderFeedback("Enter numeric values for quantity and price", true);
            }
        });
        this.addComponent(createBtn);
        tabComponents.add(createBtn);
        currentY += 50;

        buyOrderFeedbackLabel = new FormLabel("", SMALL_FONT, -1, MARGIN, currentY);
        buyOrderFeedbackLabel.setColor(Color.GRAY);
        this.addComponent(buyOrderFeedbackLabel);
        tabComponents.add(buyOrderFeedbackLabel);
        currentY += 25;
        
        // ==== ACTIVE BUY ORDERS ====
        
        FormLabel activeHeader = new FormLabel("Your Buy Orders:", HEADER_FONT, -1, MARGIN, currentY);
        this.addComponent(activeHeader);
        tabComponents.add(activeHeader);
        currentY += 30;
        
        BuyOrder[] buyOrders = container.playerInventory.getBuyOrders();
        boolean hasOrders = false;
        for (int i = 0; i < buyOrders.length; i++) {
            BuyOrder order = buyOrders[i];
            if (order != null) {
                hasOrders = true;
                currentY = buildBuyOrderCard(order, i, currentY) + 10;
            }
        }
        
        if (!hasOrders) {
            FormLabel emptyLabel = new FormLabel("No active buy orders", SMALL_FONT, -1, MARGIN + 10, currentY);
            this.addComponent(emptyLabel);
            tabComponents.add(emptyLabel);
        }
    }
    
    private int buildBuyOrderCard(BuyOrder order, int slotIndex, int startY) {
        int blockY = startY;
        int contentX = MARGIN + 10;

        Item item = order.getItemStringID() != null ? ItemRegistry.getItem(order.getItemStringID()) : null;
        String itemName = item != null ? item.getDisplayName(item.getDefaultItem(null, 1)) :
            (order.getItemStringID() != null ? order.getItemStringID() : "Unconfigured order");

        FormLabel header = new FormLabel(
            String.format("Slot %d • %s", slotIndex + 1, formatBuyOrderState(order)),
            NORMAL_FONT,
            -1,
            contentX,
            blockY
        );
        this.addComponent(header);
        tabComponents.add(header);

        String quantityLine;
        if (order.getQuantityTotal() > 0) {
            int filled = order.getQuantityFilled();
            quantityLine = String.format("%s | %d/%d filled | %d remaining",
                itemName,
                filled,
                order.getQuantityTotal(),
                order.getQuantityRemaining());
        } else {
            quantityLine = itemName;
        }
        FormLabel quantityLabel = new FormLabel(quantityLine, SMALL_FONT, -1, contentX, blockY + 18);
        this.addComponent(quantityLabel);
        tabComponents.add(quantityLabel);

        if (order.getQuantityTotal() > 0) {
            long escrowValue = (long) order.getQuantityRemaining() * order.getPricePerItem();
            FormLabel priceLabel = new FormLabel(
                String.format("Price %d each • Escrow needed %d coins", order.getPricePerItem(), escrowValue),
                SMALL_FONT,
                -1,
                contentX,
                blockY + 34
            );
            this.addComponent(priceLabel);
            tabComponents.add(priceLabel);
        }

        boolean canToggle = order.isConfigured() &&
            (order.getState() == BuyOrder.BuyOrderState.DRAFT ||
             order.getState() == BuyOrder.BuyOrderState.ACTIVE ||
             order.getState() == BuyOrder.BuyOrderState.PARTIAL);

        FormCheckBox enableToggle = new FormCheckBox(
            "Enable & escrow coins",
            contentX,
            blockY + 54,
            order.isActive()
        );
        enableToggle.setActive(canToggle);
        enableToggle.onClicked(e -> {
            if (!canToggle) {
                enableToggle.checked = order.isActive();
                showBuyOrderFeedback("Order cannot be toggled in its current state", true);
                return;
            }
            if (enableToggle.checked) {
                container.enableBuyOrder.runAndSend(slotIndex);
            } else {
                container.disableBuyOrder.runAndSend(slotIndex);
            }
        });
        this.addComponent(enableToggle);
        tabComponents.add(enableToggle);

        FormTextButton cancelBtn = new FormTextButton(
            "Cancel Order",
            contentX + 220,
            blockY + 50,
            120,
            FormInputSize.SIZE_24,
            ButtonColor.RED
        );
        boolean cancellable = order.getState() != BuyOrder.BuyOrderState.CANCELLED &&
            order.getState() != BuyOrder.BuyOrderState.COMPLETED;
        cancelBtn.setActive(cancellable);
        cancelBtn.onClicked(e -> container.cancelBuyOrder.runAndSend(slotIndex));
        cancelBtn.setTooltip("Remove order and refund any escrow");
        this.addComponent(cancelBtn);
        tabComponents.add(cancelBtn);

        return blockY + 85;
    }
    
    // ===== TAB 2: SELL OFFERS =====
    
    private void buildSellOffersTab(int startY) {
        int currentY = startY;
        
        // Count active sell offers
        int usedSlots = 0;
        int maxSlots = ModConfig.GrandExchange.geInventorySlots;
        for (int i = 0; i < maxSlots; i++) {
            GEOffer offer = container.playerInventory.getSlotOffer(i);
            if (offer != null && (offer.getState() == GEOffer.OfferState.DRAFT || 
                                  offer.getState() == GEOffer.OfferState.ACTIVE || 
                                  offer.getState() == GEOffer.OfferState.PARTIAL)) {
                usedSlots++;
            }
        }
        
        // Header with slot counter
        String headerText = String.format("Create Sell Offer (%d/%d slots used)", usedSlots, maxSlots);
        FormLabel header = new FormLabel(headerText, HEADER_FONT, -1, MARGIN, currentY);
        this.addComponent(header);
        tabComponents.add(header);
        currentY += 25;

        FormLabel hint = new FormLabel(
            "Steps: drop an item into a slot, set the price, click Post Sale, then toggle \"Show on market\".",
            SMALL_FONT, -1, MARGIN, currentY
        );
        hint.setColor(new Color(160, 160, 160));
        this.addComponent(hint);
        tabComponents.add(hint);
        currentY += 30;
        
        // ==== SELL INVENTORY SLOTS ====
        
        FormLabel inventoryLabel = new FormLabel("Place items to sell:", NORMAL_FONT, -1, MARGIN, currentY);
        this.addComponent(inventoryLabel);
        tabComponents.add(inventoryLabel);
        currentY += 25;
        
        // Display all 10 sell inventory slots in 2 rows of 5
        int slotSize = 40;
        int slotSpacing = 5;
        int slotsPerRow = 5;
        
        for (int i = 0; i < maxSlots; i++) {
            int row = i / slotsPerRow;
            int col = i % slotsPerRow;
            int slotX = MARGIN + (col * (slotSize + slotSpacing));
            int slotY = currentY + (row * (slotSize + slotSpacing));
            
            FormContainerSlot slot = new FormContainerSlot(
                client, container,
                container.GE_SELL_SLOTS_START + i,
                slotX, slotY
            );
            this.addComponent(slot);
            tabComponents.add(slot);
        }
        currentY += ((maxSlots + slotsPerRow - 1) / slotsPerRow) * (slotSize + slotSpacing) + 20;
        
        // Price input
        FormLabel priceLabel = new FormLabel("Price/item:", NORMAL_FONT, -1, MARGIN, currentY + 5);
        this.addComponent(priceLabel);
        tabComponents.add(priceLabel);
        
        FormTextInput sellPriceInput = new FormTextInput(
            MARGIN + 100, currentY,
            FormInputSize.SIZE_32, 100, 10
        );
        sellPriceInput.setText("100");
        this.addComponent(sellPriceInput);
        tabComponents.add(sellPriceInput);
        
        FormLabel coinsLabel = new FormLabel("coins", SMALL_FONT, -1, MARGIN + 205, currentY + 8);
        this.addComponent(coinsLabel);
        tabComponents.add(coinsLabel);
        currentY += 45;
        
        // Duration slider
        FormLabel durationLabel = new FormLabel("Duration:", NORMAL_FONT, -1, MARGIN, currentY + 5);
        this.addComponent(durationLabel);
        tabComponents.add(durationLabel);
        
        FormLabel durationValue = new FormLabel("7 days", SMALL_FONT, -1, MARGIN + 200, currentY + 8);
        this.addComponent(durationValue);
        tabComponents.add(durationValue);
        currentY += 30;
        
        // Post Sale button
        FormTextButton postBtn = new FormTextButton(
            "Post Sale",
            MARGIN, currentY,
            150, FormInputSize.SIZE_32,
            ButtonColor.GREEN
        );
        
        postBtn.onClicked(e -> {
            ModLogger.info("[CLIENT SELL] Post sale clicked");
            
            try {
                // Find first slot with item that doesn't have an active offer
                int slotWithItem = -1;
                int totalSlots = ModConfig.GrandExchange.geInventorySlots;
                for (int i = 0; i < totalSlots; i++) {
                    InventoryItem item = container.playerInventory.getSellInventory().getItem(i);
                    ModLogger.info("[CLIENT SELL] Slot %d: item=%s", i, item != null ? item.item.getStringID() : "null");
                    
                    if (item != null) {
                        // Check if this slot already has an offer
                        GEOffer existingOffer = container.playerInventory.getSlotOffer(i);
                        if (existingOffer == null) {
                            // No offer - can use this slot
                            slotWithItem = i;
                            ModLogger.info("[CLIENT SELL] Found available sell slot %d (no existing offer)", i);
                            break;
                        } else {
                            GEOffer.OfferState state = existingOffer.getState();
                            ModLogger.info("[CLIENT SELL] Slot %d has offer ID=%d, state=%s, enabled=%s, isActive=%s", 
                                i, existingOffer.getOfferID(), state, existingOffer.isEnabled(), existingOffer.isActive());
                            if (state == GEOffer.OfferState.COMPLETED || 
                                state == GEOffer.OfferState.EXPIRED || 
                                state == GEOffer.OfferState.CANCELLED) {
                                // Finished offer - can replace
                                slotWithItem = i;
                                ModLogger.info("[CLIENT SELL] Found available sell slot %d (finished offer ID=%d can be replaced)", i, existingOffer.getOfferID());
                                break;
                            }
                            // DRAFT or ACTIVE - skip to next
                            ModLogger.info("[CLIENT SELL] Slot %d offer is active, skipping", i);
                        }
                    }
                }
                
                if (slotWithItem == -1) {
                    ModLogger.warn("No available sell slots (all %d slots have active/draft offers or no items present)", totalSlots);
                    showSellOfferFeedback("Place an item in a free slot or cancel an existing offer first", true);
                    return;
                }
                
                // Parse price
                String priceText = sellPriceInput.getText().trim();
                if (priceText.isEmpty()) {
                    ModLogger.warn("No price entered");
                    showSellOfferFeedback("Enter a price before posting", true);
                    return;
                }
                
                int pricePerItem = Integer.parseInt(priceText);
                if (pricePerItem <= 0) {
                    ModLogger.warn("Invalid price: %d", pricePerItem);
                    showSellOfferFeedback("Price must be above zero", true);
                    return;
                }
                
                ModLogger.info("Creating sell offer in slot %d: %s x%d @ %d coins/ea",
                    slotWithItem, 
                    container.playerInventory.getSellInventory().getItem(slotWithItem).item.getStringID(),
                    container.playerInventory.getSellInventory().getItem(slotWithItem).getAmount(),
                    pricePerItem);
                showSellOfferFeedback(String.format("Posting offer in slot %d", slotWithItem + 1), false);
                
                // Send packet via custom action
                container.createSellOffer.runAndSend(slotWithItem, pricePerItem);
                
            } catch (NumberFormatException ex) {
                ModLogger.warn("Invalid price format");
                showSellOfferFeedback("Enter a numeric price", true);
            }
        });
        this.addComponent(postBtn);
        tabComponents.add(postBtn);
        currentY += 50;

        sellOfferFeedbackLabel = new FormLabel("", SMALL_FONT, -1, MARGIN, currentY);
        sellOfferFeedbackLabel.setColor(Color.GRAY);
        this.addComponent(sellOfferFeedbackLabel);
        tabComponents.add(sellOfferFeedbackLabel);
        currentY += 25;
        
        // ==== ACTIVE SELL OFFERS ====
        
        FormLabel activeHeader = new FormLabel("Your Sell Offers:", HEADER_FONT, -1, MARGIN, currentY);
        this.addComponent(activeHeader);
        tabComponents.add(activeHeader);
        currentY += 30;
        
        // Display active sell offers
        GEOffer[] sellOffers = container.playerInventory.getSellOffers();
        boolean hasOffers = false;
        for (int i = 0; i < sellOffers.length; i++) {
            GEOffer offer = sellOffers[i];
            if (offer != null) {
                hasOffers = true;
                currentY = buildSellOfferCard(offer, i, currentY) + 10;
            }
        }
        
        if (!hasOffers) {
            FormLabel emptyLabel = new FormLabel("No active sell offers", SMALL_FONT, -1, MARGIN + 10, currentY);
            this.addComponent(emptyLabel);
            tabComponents.add(emptyLabel);
        }
    }
    
    private int buildSellOfferCard(GEOffer offer, int slotIndex, int startY) {
        int blockY = startY;
        int contentX = MARGIN + 10;

        Item item = ItemRegistry.getItem(offer.getItemStringID());
        String itemName = item != null ? item.getDisplayName(item.getDefaultItem(null, 1)) : offer.getItemStringID();

        FormLabel header = new FormLabel(
            String.format("Slot %d • %s", slotIndex + 1, formatSellOfferState(offer)),
            NORMAL_FONT,
            -1,
            contentX,
            blockY
        );
        this.addComponent(header);
        tabComponents.add(header);

        FormLabel detailLabel = new FormLabel(
            String.format("%s | %d/%d sold | %d coins ea", itemName,
                offer.getQuantityTotal() - offer.getQuantityRemaining(),
                offer.getQuantityTotal(),
                offer.getPricePerItem()),
            SMALL_FONT,
            -1,
            contentX,
            blockY + 18
        );
        this.addComponent(detailLabel);
        tabComponents.add(detailLabel);

        FormLabel remainingLabel = new FormLabel(
            String.format("Remaining %d • Offer ID %d", offer.getQuantityRemaining(), offer.getOfferID()),
            SMALL_FONT,
            -1,
            contentX,
            blockY + 34
        );
        this.addComponent(remainingLabel);
        tabComponents.add(remainingLabel);

        boolean canToggle = offer.getState() == GEOffer.OfferState.DRAFT ||
            offer.getState() == GEOffer.OfferState.ACTIVE ||
            offer.getState() == GEOffer.OfferState.PARTIAL;

        FormCheckBox enableToggle = new FormCheckBox(
            "Show on market",
            contentX,
            blockY + 54,
            offer.isActive()
        );
        enableToggle.setActive(canToggle);
        enableToggle.onClicked(e -> {
            if (!canToggle) {
                enableToggle.checked = offer.isActive();
                showSellOfferFeedback("Offer cannot change state once finished", true);
                return;
            }
            if (enableToggle.checked) {
                container.enableSellOffer.runAndSend(slotIndex);
            } else {
                container.disableSellOffer.runAndSend(slotIndex);
            }
        });
        this.addComponent(enableToggle);
        tabComponents.add(enableToggle);

        FormTextButton cancelBtn = new FormTextButton(
            "Cancel Offer",
            contentX + 200,
            blockY + 50,
            120,
            FormInputSize.SIZE_24,
            ButtonColor.RED
        );
        boolean canCancel = offer.getState() != GEOffer.OfferState.COMPLETED &&
            offer.getState() != GEOffer.OfferState.CANCELLED;
        cancelBtn.setActive(canCancel);
        cancelBtn.setTooltip("Remove from market and refund remaining items");
        cancelBtn.onClicked(e -> container.cancelSellOffer.runAndSend(slotIndex));
        this.addComponent(cancelBtn);
        tabComponents.add(cancelBtn);

        return blockY + 85;
    }
    
    // ===== TAB 3: COLLECTION & SETTINGS =====
    
    private void buildCollectionTab(int startY) {
        int currentY = startY;
        
        // Header
        FormLabel header = new FormLabel("Collection Box & Settings", HEADER_FONT, -1, MARGIN, currentY);
        this.addComponent(header);
        tabComponents.add(header);
        currentY += 30;
        
        // Collection box
        List<CollectionItem> collection = container.playerInventory.getCollectionBox();
        if (collection.isEmpty()) {
            FormLabel emptyLabel = new FormLabel(
                "No items in collection box.",
                SMALL_FONT, -1, MARGIN, currentY
            );
            this.addComponent(emptyLabel);
            tabComponents.add(emptyLabel);
            currentY += 25;
        } else {
            FormLabel collectionLabel = new FormLabel(
                String.format("Collection Box (%d items):", collection.size()),
                NORMAL_FONT, -1, MARGIN, currentY
            );
            this.addComponent(collectionLabel);
            tabComponents.add(collectionLabel);
            currentY += 25;
            
            // Show first 10 items
            for (int i = 0; i < Math.min(10, collection.size()); i++) {
                CollectionItem item = collection.get(i);
                String itemText = String.format("%s x%d (%s)",
                    item.getItemStringID(),
                    item.getQuantity(),
                    item.getSource()
                );
                
                FormLabel itemLabel = new FormLabel(itemText, SMALL_FONT, -1, MARGIN + 10, currentY);
                this.addComponent(itemLabel);
                tabComponents.add(itemLabel);
                
                // Collect button
                final int itemIndex = i;
                FormTextButton collectBtn = new FormTextButton(
                    "Collect",
                    700, currentY - 5,
                    80, FormInputSize.SIZE_24,
                    ButtonColor.BASE
                );
                collectBtn.onClicked(e -> container.collectItem.runAndSend(itemIndex));
                this.addComponent(collectBtn);
                tabComponents.add(collectBtn);
                
                currentY += 25;
            }
            
            // Collect all button
            currentY += 10;
            FormTextButton collectAllBtn = new FormTextButton(
                "Collect All to Bank",
                MARGIN, currentY,
                150, FormInputSize.SIZE_32,
                ButtonColor.GREEN
            );
            collectAllBtn.onClicked(e -> container.collectAllToBank.runAndSend());
            this.addComponent(collectAllBtn);
            tabComponents.add(collectAllBtn);
            currentY += 45;
        }
        
        // Settings section
        FormLabel settingsHeader = new FormLabel("Settings", HEADER_FONT, -1, MARGIN, currentY);
        this.addComponent(settingsHeader);
        tabComponents.add(settingsHeader);
        currentY += 30;
        
        if (container.isWorldOwner()) {
            FormLabel adminHeader = new FormLabel("Admin Configuration", NORMAL_FONT, -1, MARGIN, currentY);
            this.addComponent(adminHeader);
            tabComponents.add(adminHeader);
            currentY += 25;

            FormLabel adminNote = new FormLabel(
                "Changes are saved on the server and apply after reopening the Grand Exchange.",
                SMALL_FONT, -1, MARGIN + 10, currentY
            );
            adminNote.setColor(new Color(160, 160, 160));
            this.addComponent(adminNote);
            tabComponents.add(adminNote);
            currentY += 25;
            
            // Sell offer slots configuration
            FormLabel slotsLabel = new FormLabel("Sell Offer Slots:", SMALL_FONT, -1, MARGIN + 10, currentY + 5);
            this.addComponent(slotsLabel);
            tabComponents.add(slotsLabel);
            
            FormTextInput slotsInput = new FormTextInput(
                MARGIN + 150, currentY,
                FormInputSize.SIZE_24, 60, 3
            );
            slotsInput.setText(String.valueOf(ModConfig.GrandExchange.geInventorySlots));
            this.addComponent(slotsInput);
            tabComponents.add(slotsInput);
            
            FormTextButton applySlotsBtn = new FormTextButton(
                "Apply",
                MARGIN + 220, currentY,
                80, FormInputSize.SIZE_24,
                ButtonColor.BASE
            );
            applySlotsBtn.onClicked(e -> {
                try {
                    int newSlots = Integer.parseInt(slotsInput.getText());
                    container.updateSellSlotConfig.runAndSend(newSlots);
                } catch (NumberFormatException ex) {
                    // Invalid input, ignore
                }
            });
            this.addComponent(applySlotsBtn);
            tabComponents.add(applySlotsBtn);
            
            FormLabel rangeLabel = new FormLabel("(5-20)", SMALL_FONT, -1, MARGIN + 310, currentY + 5);
            this.addComponent(rangeLabel);
            tabComponents.add(rangeLabel);
            currentY += 35;
            
            // Buy order slots configuration
            FormLabel buyOrderSlotsLabel = new FormLabel("Buy Order Slots:", SMALL_FONT, -1, MARGIN + 10, currentY + 5);
            this.addComponent(buyOrderSlotsLabel);
            tabComponents.add(buyOrderSlotsLabel);
            
            FormTextInput buyOrderSlotsInput = new FormTextInput(
                MARGIN + 150, currentY,
                FormInputSize.SIZE_24, 60, 3
            );
            buyOrderSlotsInput.setText(String.valueOf(ModConfig.GrandExchange.buyOrderSlots));
            this.addComponent(buyOrderSlotsInput);
            tabComponents.add(buyOrderSlotsInput);
            
            FormTextButton applyBuyOrderSlotsBtn = new FormTextButton(
                "Apply",
                MARGIN + 220, currentY,
                80, FormInputSize.SIZE_24,
                ButtonColor.BASE
            );
            applyBuyOrderSlotsBtn.onClicked(e -> {
                try {
                    int newSlots = Integer.parseInt(buyOrderSlotsInput.getText());
                    container.updateBuySlotConfig.runAndSend(newSlots);
                } catch (NumberFormatException ex) {
                    // Invalid input, ignore
                }
            });
            this.addComponent(applyBuyOrderSlotsBtn);
            tabComponents.add(applyBuyOrderSlotsBtn);
            
            FormLabel buyOrderRangeLabel = new FormLabel("(1-10)", SMALL_FONT, -1, MARGIN + 310, currentY + 5);
            this.addComponent(buyOrderRangeLabel);
            tabComponents.add(buyOrderRangeLabel);
            currentY += 40;
        } else {
            FormLabel lockedLabel = new FormLabel(
                "Grand Exchange settings can only be changed by the world owner.",
                SMALL_FONT, -1, MARGIN + 10, currentY
            );
            lockedLabel.setColor(new Color(160, 160, 160));
            this.addComponent(lockedLabel);
            tabComponents.add(lockedLabel);
            currentY += 30;
        }
        
        // Auto-bank toggle
        FormCheckBox autoBankCheckbox = new FormCheckBox(
            "Auto-send purchases to bank",
            MARGIN, currentY,
            container.playerInventory.isAutoSendToBank()
        );
        autoBankCheckbox.onClicked(e -> container.toggleAutoBank.runAndSend(autoBankCheckbox.checked));
        this.addComponent(autoBankCheckbox);
        tabComponents.add(autoBankCheckbox);
        currentY += 30;
        
        // Notify partial sales
        FormCheckBox notifyPartialCheckbox = new FormCheckBox(
            "Notify on partial sales",
            MARGIN, currentY,
            container.playerInventory.isNotifyPartialSales()
        );
        notifyPartialCheckbox.onClicked(e -> container.toggleNotifyPartial.runAndSend(notifyPartialCheckbox.checked));
        this.addComponent(notifyPartialCheckbox);
        tabComponents.add(notifyPartialCheckbox);
        currentY += 30;
        
        // Play sound on sale
        FormCheckBox playSoundCheckbox = new FormCheckBox(
            "Play sound on sale",
            MARGIN, currentY,
            container.playerInventory.isPlaySoundOnSale()
        );
        playSoundCheckbox.onClicked(e -> container.togglePlaySound.runAndSend(playSoundCheckbox.checked));
        this.addComponent(playSoundCheckbox);
        tabComponents.add(playSoundCheckbox);
    }
    
    // ===== TAB 4: HISTORY & STATISTICS =====
    
    /**
     * Build History tab with transaction stats and recent sales.
     */
    private void buildHistoryTab(int startY) {
        int currentY = startY;
        
        // Header
        FormLabel header = new FormLabel("Transaction History & Statistics", HEADER_FONT, -1, MARGIN, currentY);
        this.addComponent(header);
        tabComponents.add(header);
        currentY += 35;
        
        // Statistics section
        FormLabel statsHeader = new FormLabel("Your Statistics", NORMAL_FONT, -1, MARGIN, currentY);
        this.addComponent(statsHeader);
        tabComponents.add(statsHeader);
        currentY += 25;
        
        // Display stats from playerInventory
        String[] stats = {
            String.format("Total Items Purchased: %d", container.playerInventory.getTotalItemsPurchased()),
            String.format("Total Items Sold: %d", container.playerInventory.getTotalItemsSold()),
            String.format("Sell Offers Created: %d", container.playerInventory.getTotalSellOffersCreated()),
            String.format("Sell Offers Completed: %d", container.playerInventory.getTotalSellOffersCompleted()),
            String.format("Buy Orders Created: %d", container.playerInventory.getTotalBuyOrdersCreated()),
            String.format("Buy Orders Completed: %d", container.playerInventory.getTotalBuyOrdersCompleted())
        };
        
        for (String stat : stats) {
            FormLabel statLabel = new FormLabel(stat, SMALL_FONT, -1, MARGIN + 10, currentY);
            this.addComponent(statLabel);
            tabComponents.add(statLabel);
            currentY += 20;
        }
        
        currentY += 15;
        
        // Recent sales section
        FormLabel salesHeader = new FormLabel("Recent Sales (Last 20)", NORMAL_FONT, -1, MARGIN, currentY);
        this.addComponent(salesHeader);
        tabComponents.add(salesHeader);
        currentY += 25;
        
        // Display sale notifications
        java.util.List<SaleNotification> sales = container.playerInventory.getSaleHistory();
        if (sales.isEmpty()) {
            FormLabel noSales = new FormLabel("No recent sales", SMALL_FONT, -1, MARGIN + 10, currentY);
            this.addComponent(noSales);
            tabComponents.add(noSales);
        } else {
            // Show up to 20 most recent sales
            int displayCount = Math.min(20, sales.size());
            for (int i = 0; i < displayCount; i++) {
                SaleNotification sale = sales.get(i);
                String saleText = String.format("%s x%d @ %d coins ea (Total: %d coins) - Buyer: %s",
                    sale.getItemStringID(),
                    sale.getQuantitySold(),
                    sale.getPricePerItem(),
                    sale.getTotalCoins(),
                    sale.getBuyerName()
                );
                
                FormLabel saleLabel = new FormLabel(saleText, SMALL_FONT, -1, MARGIN + 10, currentY);
                this.addComponent(saleLabel);
                tabComponents.add(saleLabel);
                currentY += 20;
            }
        }
    }
    
    /**
     * Get formatted coin text for display.
     */
    private String getCoinText(GrandExchangeContainer container) {
        return String.format("Bank: %d coins", container.clientCoinCount);
    }

    private void refreshCoinLabel() {
        if (coinLabel != null) {
            coinLabel.setText(getCoinText(container));
        }
    }

    private String getItemDisplayName(String itemStringID) {
        if (itemStringID == null || itemStringID.isEmpty()) {
            return "Unknown Item";
        }
        Item item = ItemRegistry.getItem(itemStringID);
        if (item == null) {
            return itemStringID;
        }
        InventoryItem displayItem = item.getDefaultItem(null, 1);
        return item.getDisplayName(displayItem);
    }

    private String formatCoins(long amount) {
        return String.format("%,d", amount);
    }

    private String resolveSellerLabel(GrandExchangeContainer.MarketListingView offer) {
        if (offer.sellerName != null && !offer.sellerName.isBlank()) {
            return offer.sellerName;
        }
        return String.format("Player #%d", offer.sellerAuth);
    }

    private String formatExpiration(long expirationTime) {
        if (expirationTime <= 0L) {
            return "No expiration";
        }
        long remaining = expirationTime - System.currentTimeMillis();
        if (remaining <= 0L) {
            return "Expiring now";
        }
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remaining);
        if (minutes >= 60 * 24) {
            long days = minutes / (60 * 24);
            return String.format("%d day%s left", days, days == 1 ? "" : "s");
        }
        if (minutes >= 60) {
            long hours = minutes / 60;
            return String.format("%d hour%s left", hours, hours == 1 ? "" : "s");
        }
        return String.format("%d min left", Math.max(1, minutes));
    }

    private void showBuyOrderFeedback(String message, boolean isError) {
        if (buyOrderFeedbackLabel == null) {
            return;
        }
        buyOrderFeedbackLabel.setText(message == null ? "" : message);
        buyOrderFeedbackLabel.setColor(isError ? Color.ORANGE : new Color(70, 190, 90));
    }

    private void showSellOfferFeedback(String message, boolean isError) {
        if (sellOfferFeedbackLabel == null) {
            return;
        }
        sellOfferFeedbackLabel.setText(message == null ? "" : message);
        sellOfferFeedbackLabel.setColor(isError ? Color.ORANGE : new Color(70, 190, 90));
    }

    private String formatBuyOrderState(BuyOrder order) {
        if (order == null) {
            return "Empty";
        }
        switch (order.getState()) {
            case DRAFT:
                return order.isConfigured() ? "Draft (disabled)" : "Draft (configure item)";
            case ACTIVE:
                return "Active";
            case PARTIAL:
                return "Partial fill";
            case COMPLETED:
                return "Completed";
            case EXPIRED:
                return "Expired";
            case CANCELLED:
                return "Cancelled";
            default:
                return order.getState().name();
        }
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

    private String formatSellOfferState(GEOffer offer) {
        if (offer == null) {
            return "Empty";
        }
        switch (offer.getState()) {
            case DRAFT:
                return offer.isEnabled() ? "Pending" : "Draft (hidden)";
            case ACTIVE:
                return "Active";
            case PARTIAL:
                return "Partial fill";
            case COMPLETED:
                return "Completed";
            case EXPIRED:
                return "Expired";
            case CANCELLED:
                return "Cancelled";
            default:
                return offer.getState().name();
        }
    }
    
    /**
     * Check if itemCategory is the same as or a child of targetCategory
     */
    private boolean isCategoryOrChild(ItemCategory itemCat, ItemCategory targetCat) {
        if (itemCat == null || targetCat == null) return false;
        ItemCategory current = itemCat;
        while (current != null) {
            if (current == targetCat || current.stringID.equals(targetCat.stringID)) {
                return true;
            }
            current = current.parent;
        }
        return false;
    }
    
    /**
     * Check if current player is the world owner.
     */
    private boolean isWorldOwner() {
        return container != null && container.isWorldOwner();
    }
    
    /**
     * Build Admin Dashboard tab - only accessible to world owner.
     * Shows market health, performance metrics, and analytics.
     * TODO: Implement when PerformanceMetrics API is finalized
     */
    private void buildAdminDashboardTab(int startY) {
        int currentY = startY;
        
        // Header
        FormLabel header = new FormLabel("Admin Dashboard - Coming Soon", HEADER_FONT, -1, MARGIN, currentY);
        this.addComponent(header);
        tabComponents.add(header);
        currentY += 30;
        
        FormLabel desc = new FormLabel("Advanced market analytics will be available here.", NORMAL_FONT, -1, MARGIN, currentY);
        this.addComponent(desc);
        tabComponents.add(desc);
    }
}

