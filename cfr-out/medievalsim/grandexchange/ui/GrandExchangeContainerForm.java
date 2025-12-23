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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import medievalsim.config.ModConfig;
import medievalsim.grandexchange.domain.BuyOrder;
import medievalsim.grandexchange.domain.CollectionItem;
import medievalsim.grandexchange.domain.GEOffer;
import medievalsim.grandexchange.domain.SaleNotification;
import medievalsim.grandexchange.ui.GrandExchangeContainer;
import medievalsim.packets.PacketGECreateBuyOrder;
import medievalsim.ui.helpers.SearchableDropdown;
import medievalsim.util.ModLogger;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.Packet;
import necesse.engine.network.client.Client;
import necesse.engine.registries.ItemRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormCheckBox;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormDropdownSelectionButton;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormItemIcon;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.forms.components.containerSlot.FormContainerSlot;
import necesse.gfx.forms.presets.containerComponent.ContainerForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemCategory;

public class GrandExchangeContainerForm<T extends GrandExchangeContainer>
extends ContainerForm<T> {
    private static final int MARGIN = 10;
    private static final int TAB_BUTTON_WIDTH = 150;
    private static final int TAB_BUTTON_HEIGHT = 32;
    private static final int TAB_SPACING = 5;
    private static final FontOptions TITLE_FONT = new FontOptions(20);
    private static final FontOptions HEADER_FONT = new FontOptions(16);
    private static final FontOptions NORMAL_FONT = new FontOptions(14);
    private static final FontOptions SMALL_FONT = new FontOptions(12);
    private FormTextButton marketTabButton;
    private FormTextButton buyOrdersTabButton;
    private FormTextButton sellOffersTabButton;
    private FormTextButton settingsTabButton;
    private FormTextButton historyTabButton;
    private FormTextButton adminTabButton;
    private List<FormComponent> sharedComponents = new ArrayList<FormComponent>();
    private List<FormComponent> tabComponents = new ArrayList<FormComponent>();
    private SearchableDropdown<Item> marketItemSearch;
    private FormDropdownSelectionButton<ItemCategory> marketCategoryFilter;
    private SearchableDropdown<Item> buyOrderItemSearch;
    private FormLabel buyOrderFeedbackLabel;
    private FormLabel sellOfferFeedbackLabel;
    private FormLabel coinLabel;

    public GrandExchangeContainerForm(Client client, T container) {
        super(client, 900, 700, container);
        ModLogger.info("[FORM INIT] Registering buy orders callback");
        ((GrandExchangeContainer)((Object)container)).setBuyOrdersUpdateCallback(() -> {
            ModLogger.info("[FORM CALLBACK] Buy orders callback triggered - rebuilding tab");
            this.rebuildCurrentTab();
            ModLogger.info("[FORM CALLBACK] Rebuild completed");
        });
        ModLogger.info("[FORM INIT] Registering sell offers callback");
        ((GrandExchangeContainer)((Object)container)).setSellOffersUpdateCallback(() -> {
            ModLogger.info("[FORM CALLBACK] Sell offers callback triggered - rebuilding tab");
            this.rebuildCurrentTab();
            ModLogger.info("[FORM CALLBACK] Rebuild completed");
        });
        ModLogger.info("[FORM INIT] Registering market listings callback");
        ((GrandExchangeContainer)((Object)container)).setMarketListingsUpdateCallback(() -> {
            if (grandExchangeContainer.activeTab == 0) {
                ModLogger.info("[FORM CALLBACK] Market listings callback triggered - rebuilding market tab");
                this.rebuildCurrentTab();
                ModLogger.info("[FORM CALLBACK] Market tab rebuild completed");
            }
        });
        ((GrandExchangeContainer)((Object)container)).setCoinCountUpdateCallback(this::refreshCoinLabel);
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
        FormLabel titleLabel = new FormLabel("Grand Exchange", TITLE_FONT, -1, 10, currentY);
        this.addComponent((FormComponent)titleLabel);
        this.sharedComponents.add((FormComponent)titleLabel);
        this.coinLabel = new FormLabel(this.getCoinText((GrandExchangeContainer)this.container), NORMAL_FONT, -1, this.getWidth() - 200, currentY + 5);
        this.addComponent((FormComponent)this.coinLabel);
        this.sharedComponents.add((FormComponent)this.coinLabel);
        this.buildTabButtons(currentY += 35);
        this.buildTabContent(currentY += 42);
    }

    private void buildTabButtons(int yPos) {
        int currentX = 10;
        this.marketTabButton = new FormTextButton("Market", currentX, yPos, 150, FormInputSize.SIZE_32, ((GrandExchangeContainer)this.container).activeTab == 0 ? ButtonColor.RED : ButtonColor.BASE);
        this.marketTabButton.onClicked(e -> this.switchToTab(0));
        this.addComponent((FormComponent)this.marketTabButton);
        this.sharedComponents.add((FormComponent)this.marketTabButton);
        this.buyOrdersTabButton = new FormTextButton("Buy Orders", currentX += 155, yPos, 150, FormInputSize.SIZE_32, ((GrandExchangeContainer)this.container).activeTab == 1 ? ButtonColor.RED : ButtonColor.BASE);
        this.buyOrdersTabButton.onClicked(e -> this.switchToTab(1));
        this.addComponent((FormComponent)this.buyOrdersTabButton);
        this.sharedComponents.add((FormComponent)this.buyOrdersTabButton);
        this.sellOffersTabButton = new FormTextButton("Sell Offers", currentX += 155, yPos, 150, FormInputSize.SIZE_32, ((GrandExchangeContainer)this.container).activeTab == 2 ? ButtonColor.RED : ButtonColor.BASE);
        this.sellOffersTabButton.onClicked(e -> this.switchToTab(2));
        this.addComponent((FormComponent)this.sellOffersTabButton);
        this.sharedComponents.add((FormComponent)this.sellOffersTabButton);
        this.settingsTabButton = new FormTextButton("Collection", currentX += 155, yPos, 150, FormInputSize.SIZE_32, ((GrandExchangeContainer)this.container).activeTab == 3 ? ButtonColor.RED : ButtonColor.BASE);
        this.settingsTabButton.onClicked(e -> this.switchToTab(3));
        this.addComponent((FormComponent)this.settingsTabButton);
        this.sharedComponents.add((FormComponent)this.settingsTabButton);
        this.historyTabButton = new FormTextButton("History", currentX += 155, yPos, 150, FormInputSize.SIZE_32, ((GrandExchangeContainer)this.container).activeTab == 4 ? ButtonColor.RED : ButtonColor.BASE);
        this.historyTabButton.onClicked(e -> this.switchToTab(4));
        this.addComponent((FormComponent)this.historyTabButton);
        this.sharedComponents.add((FormComponent)this.historyTabButton);
        currentX += 155;
        if (this.isWorldOwner()) {
            this.adminTabButton = new FormTextButton("Admin", currentX, yPos, 150, FormInputSize.SIZE_32, ((GrandExchangeContainer)this.container).activeTab == 5 ? ButtonColor.RED : ButtonColor.BASE);
            this.adminTabButton.onClicked(e -> this.switchToTab(5));
            this.addComponent((FormComponent)this.adminTabButton);
            this.sharedComponents.add((FormComponent)this.adminTabButton);
        }
    }

    private void switchToTab(int tabIndex) {
        if (((GrandExchangeContainer)this.container).activeTab == tabIndex) {
            return;
        }
        ((GrandExchangeContainer)this.container).setActiveTab.runAndSend(tabIndex);
        this.updateTabUI(tabIndex);
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
        this.buildUI();
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

    private void buildTabContent(int startY) {
        switch (((GrandExchangeContainer)this.container).activeTab) {
            case 0: {
                this.buildMarketTab(startY);
                break;
            }
            case 1: {
                this.buildBuyOrdersTab(startY);
                break;
            }
            case 2: {
                this.buildSellOffersTab(startY);
                break;
            }
            case 3: {
                this.buildCollectionTab(startY);
                break;
            }
            case 4: {
                this.buildHistoryTab(startY);
                break;
            }
            case 5: {
                if (!this.isWorldOwner()) break;
                this.buildAdminDashboardTab(startY);
            }
        }
    }

    private void buildMarketTab(int startY) {
        int currentY = startY;
        FormLabel header = new FormLabel("Browse Market Offers", HEADER_FONT, -1, 10, currentY);
        this.addComponent((FormComponent)header);
        this.tabComponents.add((FormComponent)header);
        FormLabel hint = new FormLabel("Tip: Search or filter, then buy an offer to send items to your collection box.", SMALL_FONT, -1, 10, currentY += 25);
        hint.setColor(new Color(160, 160, 160));
        this.addComponent((FormComponent)hint);
        this.tabComponents.add((FormComponent)hint);
        FormLabel searchLabel = new FormLabel("Search Item:", NORMAL_FONT, -1, 10, currentY += 30);
        this.addComponent((FormComponent)searchLabel);
        this.tabComponents.add((FormComponent)searchLabel);
        List allItems = ItemRegistry.streamItems().filter(item -> item != null && !item.getStringID().endsWith("egg") && !item.getStringID().contains("summon") && !item.getStringID().endsWith("staff")).collect(Collectors.toList());
        this.marketItemSearch = new SearchableDropdown<Item>(10, currentY += 25, 350, 300, "Type to search items...", allItems, item -> item.getDisplayName(item.getDefaultItem(null, 1)), item -> ((GrandExchangeContainer)this.container).setMarketFilter.runAndSend(item.getStringID()));
        this.marketItemSearch.addToForm((Form)this);
        FormLabel categoryLabel = new FormLabel("Category:", NORMAL_FONT, -1, 10, currentY += 45);
        this.addComponent((FormComponent)categoryLabel);
        this.tabComponents.add((FormComponent)categoryLabel);
        this.marketCategoryFilter = new FormDropdownSelectionButton(100, currentY - 5, FormInputSize.SIZE_24, ButtonColor.BASE, 200, (GameMessage)new StaticMessage("All Items"));
        this.marketCategoryFilter.options.add(null, (GameMessage)new StaticMessage("All Items"));
        ItemCategory.masterCategory.streamChildren().forEach(category -> {
            if (category != null) {
                String displayName = category.displayName != null ? category.displayName.translate() : category.stringID;
                this.marketCategoryFilter.options.add(category, (GameMessage)new StaticMessage(displayName));
            }
        });
        this.marketCategoryFilter.onSelected(event -> {
            ItemCategory selectedCat = (ItemCategory)event.value;
            String categoryId = selectedCat == null ? "all" : (selectedCat.stringID != null ? selectedCat.stringID : "all");
            ((GrandExchangeContainer)this.container).setMarketCategory.runAndSend(categoryId);
            if (selectedCat == null) {
                this.marketItemSearch.updateItems(allItems);
            } else {
                List filteredItems = allItems.stream().filter(item -> {
                    ItemCategory itemCat = ItemCategory.getItemsCategory((Item)item);
                    return this.isCategoryOrChild(itemCat, selectedCat);
                }).collect(Collectors.toList());
                this.marketItemSearch.updateItems(filteredItems);
            }
        });
        this.addComponent((FormComponent)this.marketCategoryFilter);
        this.tabComponents.add((FormComponent)this.marketCategoryFilter);
        FormLabel sortLabel = new FormLabel("Sort:", NORMAL_FONT, -1, 10, currentY += 40);
        this.addComponent((FormComponent)sortLabel);
        this.tabComponents.add((FormComponent)sortLabel);
        int sortX = 80;
        String[] sortLabels = new String[]{"Price Low", "Price High", "Qty High", "Time New"};
        int i = 0;
        while (i < sortLabels.length) {
            int sortMode = i + 1;
            FormTextButton sortBtn = new FormTextButton(sortLabels[i], sortX, currentY, 80, FormInputSize.SIZE_24, ((GrandExchangeContainer)this.container).marketSort == sortMode ? ButtonColor.GREEN : ButtonColor.BASE);
            sortBtn.onClicked(e -> ((GrandExchangeContainer)this.container).setMarketSort.runAndSend(sortMode));
            this.addComponent((FormComponent)sortBtn);
            this.tabComponents.add((FormComponent)sortBtn);
            sortX += 85;
            ++i;
        }
        FormLabel listingsLabel = new FormLabel(String.format("Showing %d of %d offers (Page %d/%d):", ((GrandExchangeContainer)this.container).marketListings.size(), ((GrandExchangeContainer)this.container).marketTotalResults, ((GrandExchangeContainer)this.container).marketPage + 1, Math.max(1, ((GrandExchangeContainer)this.container).getTotalPages())), NORMAL_FONT, -1, 10, currentY += 35);
        this.addComponent((FormComponent)listingsLabel);
        this.tabComponents.add((FormComponent)listingsLabel);
        currentY += 30;
        List<GrandExchangeContainer.MarketListingView> pageOffers = ((GrandExchangeContainer)this.container).getCurrentPageListings();
        for (GrandExchangeContainer.MarketListingView offer : pageOffers) {
            String itemName = this.getItemDisplayName(offer.itemStringID);
            long totalPrice = Math.max(0L, (long)offer.getTotalPrice());
            boolean canAfford = ((GrandExchangeContainer)this.container).clientCoinCount >= totalPrice && totalPrice > 0L;
            String qtyLine = String.format("Available %d/%d @ %s coins ea", offer.quantityRemaining, offer.quantityTotal, this.formatCoins(offer.pricePerItem));
            String totalLine = String.format("Total %s coins%s", this.formatCoins(totalPrice), canAfford ? "" : " (insufficient funds)");
            String metaLine = String.format("Seller %s \u2022 %s", this.resolveSellerLabel(offer), this.formatExpiration(offer.expirationTime));
            int entryTop = currentY;
            int iconX = 10;
            int textX = iconX + 42;
            int lineY = entryTop;
            InventoryItem iconItem = this.buildListingIcon(offer.itemStringID, offer.quantityRemaining);
            if (iconItem != null) {
                FormItemIcon iconComponent = new FormItemIcon(iconX, entryTop, iconItem, false);
                this.addComponent((FormComponent)iconComponent);
                this.tabComponents.add((FormComponent)iconComponent);
            } else {
                textX = 15;
            }
            FormLabel itemLabel = new FormLabel(itemName, NORMAL_FONT, -1, textX, lineY);
            this.addComponent((FormComponent)itemLabel);
            this.tabComponents.add((FormComponent)itemLabel);
            FormLabel availabilityLabel = new FormLabel(qtyLine, SMALL_FONT, -1, textX, lineY += 18);
            this.addComponent((FormComponent)availabilityLabel);
            this.tabComponents.add((FormComponent)availabilityLabel);
            FormLabel priceLabel = new FormLabel(totalLine, SMALL_FONT, -1, textX, lineY += 18);
            priceLabel.setColor(canAfford ? new Color(70, 190, 90) : new Color(230, 170, 80));
            this.addComponent((FormComponent)priceLabel);
            this.tabComponents.add((FormComponent)priceLabel);
            FormLabel metaLabel = new FormLabel(metaLine, SMALL_FONT, -1, textX, lineY += 18);
            metaLabel.setColor(new Color(160, 160, 160));
            this.addComponent((FormComponent)metaLabel);
            this.tabComponents.add((FormComponent)metaLabel);
            FormTextButton buyBtn = new FormTextButton("Buy", 700, entryTop + 4, 80, FormInputSize.SIZE_24, ButtonColor.BASE);
            buyBtn.setActive(canAfford);
            if (!canAfford) {
                buyBtn.setTooltip("Not enough coins in bank");
            }
            long offerId = offer.offerId;
            buyBtn.onClicked(e -> ((GrandExchangeContainer)this.container).buyFromMarket.runAndSend(offerId));
            this.addComponent((FormComponent)buyBtn);
            this.tabComponents.add((FormComponent)buyBtn);
            int entryHeight = Math.max(lineY + 20 - entryTop, 48);
            currentY = entryTop + entryHeight + 15;
            if (currentY > 600) break;
        }
        currentY += 10;
        if (((GrandExchangeContainer)this.container).marketPage > 0) {
            FormTextButton prevBtn = new FormTextButton("\u2190 Previous", 10, currentY, 100, FormInputSize.SIZE_32, ButtonColor.BASE);
            prevBtn.onClicked(e -> ((GrandExchangeContainer)this.container).setMarketPage.runAndSend(((GrandExchangeContainer)this.container).marketPage - 1));
            this.addComponent((FormComponent)prevBtn);
            this.tabComponents.add((FormComponent)prevBtn);
        }
        if (((GrandExchangeContainer)this.container).marketPage < ((GrandExchangeContainer)this.container).getTotalPages() - 1) {
            FormTextButton nextBtn = new FormTextButton("Next \u2192", 120, currentY, 100, FormInputSize.SIZE_32, ButtonColor.BASE);
            nextBtn.onClicked(e -> ((GrandExchangeContainer)this.container).setMarketPage.runAndSend(((GrandExchangeContainer)this.container).marketPage + 1));
            this.addComponent((FormComponent)nextBtn);
            this.tabComponents.add((FormComponent)nextBtn);
        }
    }

    private void buildBuyOrdersTab(int startY) {
        int currentY = startY;
        BuyOrder[] allOrders = ((GrandExchangeContainer)this.container).playerInventory.getBuyOrders();
        int usedSlots = 0;
        int maxSlots = ModConfig.GrandExchange.buyOrderSlots;
        BuyOrder[] buyOrderArray = allOrders;
        int n = allOrders.length;
        int n2 = 0;
        while (n2 < n) {
            BuyOrder order = buyOrderArray[n2];
            if (order != null && (order.getState() == BuyOrder.BuyOrderState.DRAFT || order.getState() == BuyOrder.BuyOrderState.ACTIVE || order.getState() == BuyOrder.BuyOrderState.PARTIAL)) {
                ++usedSlots;
            }
            ++n2;
        }
        FormLabel header = new FormLabel(String.format("Create Buy Order (%d/%d slots used)", usedSlots, maxSlots), HEADER_FONT, -1, 10, currentY);
        this.addComponent((FormComponent)header);
        this.tabComponents.add((FormComponent)header);
        FormLabel hint = new FormLabel("Steps: pick an item, set quantity & price, submit, then enable the slot to escrow coins.", SMALL_FONT, -1, 10, currentY += 25);
        hint.setColor(new Color(160, 160, 160));
        this.addComponent((FormComponent)hint);
        this.tabComponents.add((FormComponent)hint);
        FormLabel itemLabel = new FormLabel("Item:", NORMAL_FONT, -1, 10, currentY += 30);
        this.addComponent((FormComponent)itemLabel);
        this.tabComponents.add((FormComponent)itemLabel);
        List allItems = ItemRegistry.streamItems().filter(item -> item != null && !item.getStringID().endsWith("egg") && !item.getStringID().contains("summon") && !item.getStringID().endsWith("staff")).collect(Collectors.toList());
        Item[] selectedItemHolder = new Item[1];
        this.buyOrderItemSearch = new SearchableDropdown<Item>(10, currentY += 25, 300, 250, "Type to search items...", allItems, item -> item.getDisplayName(item.getDefaultItem(null, 1)), item -> {
            itemArray[0] = item;
            ModLogger.debug("Selected item for buy order: %s", item.getStringID());
        });
        this.buyOrderItemSearch.addToForm((Form)this);
        FormLabel qtyLabel = new FormLabel("Quantity:", NORMAL_FONT, -1, 10, (currentY += 45) + 5);
        this.addComponent((FormComponent)qtyLabel);
        this.tabComponents.add((FormComponent)qtyLabel);
        FormTextInput qtyInput = new FormTextInput(90, currentY, FormInputSize.SIZE_32, 80, 8);
        qtyInput.setText("1");
        this.addComponent((FormComponent)qtyInput);
        this.tabComponents.add((FormComponent)qtyInput);
        FormLabel priceLabel = new FormLabel("Price/item:", NORMAL_FONT, -1, 190, currentY + 5);
        this.addComponent((FormComponent)priceLabel);
        this.tabComponents.add((FormComponent)priceLabel);
        FormTextInput priceInput = new FormTextInput(280, currentY, FormInputSize.SIZE_32, 100, 10);
        priceInput.setText("100");
        this.addComponent((FormComponent)priceInput);
        this.tabComponents.add((FormComponent)priceInput);
        FormLabel coinsLabel = new FormLabel("coins", SMALL_FONT, -1, 385, currentY + 8);
        this.addComponent((FormComponent)coinsLabel);
        this.tabComponents.add((FormComponent)coinsLabel);
        FormTextButton createBtn = new FormTextButton("Make Buy Order", 10, currentY += 45, 200, FormInputSize.SIZE_32, ButtonColor.GREEN);
        createBtn.onClicked(e -> {
            try {
                if (selectedItemHolder[0] == null) {
                    ModLogger.warn("No item selected for buy order");
                    this.showBuyOrderFeedback("Select an item before creating a buy order", true);
                    return;
                }
                int quantity = Integer.parseInt(qtyInput.getText().trim());
                if (quantity <= 0 || quantity > 9999) {
                    ModLogger.warn("Invalid quantity: %d", quantity);
                    this.showBuyOrderFeedback("Quantity must be between 1 and 9999", true);
                    return;
                }
                int price = Integer.parseInt(priceInput.getText().trim());
                if (price <= 0) {
                    ModLogger.warn("Invalid price: %d", price);
                    this.showBuyOrderFeedback("Enter a price above zero", true);
                    return;
                }
                int availableSlot = -1;
                BuyOrder[] existingOrders = ((GrandExchangeContainer)this.container).playerInventory.getBuyOrders();
                int i = 0;
                while (i < existingOrders.length) {
                    if (existingOrders[i] == null) {
                        availableSlot = i;
                        break;
                    }
                    BuyOrder.BuyOrderState state = existingOrders[i].getState();
                    if (state == BuyOrder.BuyOrderState.COMPLETED || state == BuyOrder.BuyOrderState.EXPIRED || state == BuyOrder.BuyOrderState.CANCELLED) {
                        availableSlot = i;
                        break;
                    }
                    ++i;
                }
                if (availableSlot == -1) {
                    ModLogger.warn("No available buy order slots (max=%d)", ModConfig.GrandExchange.buyOrderSlots);
                    this.showBuyOrderFeedback("All buy order slots are in use", true);
                    return;
                }
                ModLogger.info("Creating buy order in slot %d: %s x%d @ %d coins", availableSlot, selectedItemHolder[0].getStringID(), quantity, price);
                this.showBuyOrderFeedback(String.format("Submitting slot %d: %d x %s @ %d", availableSlot + 1, quantity, selectedItemHolder[0].getStringID(), price), false);
                PacketGECreateBuyOrder packet = new PacketGECreateBuyOrder(availableSlot, selectedItemHolder[0].getStringID(), quantity, price, 7);
                this.client.network.sendPacket((Packet)packet);
            }
            catch (NumberFormatException ex) {
                ModLogger.warn("Invalid number format in buy order inputs");
                this.showBuyOrderFeedback("Enter numeric values for quantity and price", true);
            }
        });
        this.addComponent((FormComponent)createBtn);
        this.tabComponents.add((FormComponent)createBtn);
        this.buyOrderFeedbackLabel = new FormLabel("", SMALL_FONT, -1, 10, currentY += 50);
        this.buyOrderFeedbackLabel.setColor(Color.GRAY);
        this.addComponent((FormComponent)this.buyOrderFeedbackLabel);
        this.tabComponents.add((FormComponent)this.buyOrderFeedbackLabel);
        FormLabel activeHeader = new FormLabel("Your Buy Orders:", HEADER_FONT, -1, 10, currentY += 25);
        this.addComponent((FormComponent)activeHeader);
        this.tabComponents.add((FormComponent)activeHeader);
        currentY += 30;
        BuyOrder[] buyOrders = ((GrandExchangeContainer)this.container).playerInventory.getBuyOrders();
        boolean hasOrders = false;
        int i = 0;
        while (i < buyOrders.length) {
            BuyOrder order = buyOrders[i];
            if (order != null) {
                hasOrders = true;
                currentY = this.buildBuyOrderCard(order, i, currentY) + 10;
            }
            ++i;
        }
        if (!hasOrders) {
            FormLabel emptyLabel = new FormLabel("No active buy orders", SMALL_FONT, -1, 20, currentY);
            this.addComponent((FormComponent)emptyLabel);
            this.tabComponents.add((FormComponent)emptyLabel);
        }
    }

    private int buildBuyOrderCard(BuyOrder order, int slotIndex, int startY) {
        String quantityLine;
        Item item;
        int blockY = startY;
        int contentX = 20;
        Item item2 = item = order.getItemStringID() != null ? ItemRegistry.getItem((String)order.getItemStringID()) : null;
        String itemName = item != null ? item.getDisplayName(item.getDefaultItem(null, 1)) : (order.getItemStringID() != null ? order.getItemStringID() : "Unconfigured order");
        FormLabel header = new FormLabel(String.format("Slot %d \u2022 %s", slotIndex + 1, this.formatBuyOrderState(order)), NORMAL_FONT, -1, contentX, blockY);
        this.addComponent((FormComponent)header);
        this.tabComponents.add((FormComponent)header);
        if (order.getQuantityTotal() > 0) {
            int filled = order.getQuantityFilled();
            quantityLine = String.format("%s | %d/%d filled | %d remaining", itemName, filled, order.getQuantityTotal(), order.getQuantityRemaining());
        } else {
            quantityLine = itemName;
        }
        FormLabel quantityLabel = new FormLabel(quantityLine, SMALL_FONT, -1, contentX, blockY + 18);
        this.addComponent((FormComponent)quantityLabel);
        this.tabComponents.add((FormComponent)quantityLabel);
        if (order.getQuantityTotal() > 0) {
            long escrowValue = (long)order.getQuantityRemaining() * (long)order.getPricePerItem();
            FormLabel priceLabel = new FormLabel(String.format("Price %d each \u2022 Escrow needed %d coins", order.getPricePerItem(), escrowValue), SMALL_FONT, -1, contentX, blockY + 34);
            this.addComponent((FormComponent)priceLabel);
            this.tabComponents.add((FormComponent)priceLabel);
        }
        boolean canToggle = order.isConfigured() && (order.getState() == BuyOrder.BuyOrderState.DRAFT || order.getState() == BuyOrder.BuyOrderState.ACTIVE || order.getState() == BuyOrder.BuyOrderState.PARTIAL);
        FormCheckBox enableToggle = new FormCheckBox("Enable & escrow coins", contentX, blockY + 54, order.isActive());
        enableToggle.setActive(canToggle);
        enableToggle.onClicked(e -> {
            if (!canToggle) {
                formCheckBox.checked = order.isActive();
                this.showBuyOrderFeedback("Order cannot be toggled in its current state", true);
                return;
            }
            if (formCheckBox.checked) {
                ((GrandExchangeContainer)this.container).enableBuyOrder.runAndSend(slotIndex);
            } else {
                ((GrandExchangeContainer)this.container).disableBuyOrder.runAndSend(slotIndex);
            }
        });
        this.addComponent((FormComponent)enableToggle);
        this.tabComponents.add((FormComponent)enableToggle);
        FormTextButton cancelBtn = new FormTextButton("Cancel Order", contentX + 220, blockY + 50, 120, FormInputSize.SIZE_24, ButtonColor.RED);
        boolean cancellable = order.getState() != BuyOrder.BuyOrderState.CANCELLED && order.getState() != BuyOrder.BuyOrderState.COMPLETED;
        cancelBtn.setActive(cancellable);
        cancelBtn.onClicked(e -> ((GrandExchangeContainer)this.container).cancelBuyOrder.runAndSend(slotIndex));
        cancelBtn.setTooltip("Remove order and refund any escrow");
        this.addComponent((FormComponent)cancelBtn);
        this.tabComponents.add((FormComponent)cancelBtn);
        return blockY + 85;
    }

    private void buildSellOffersTab(int startY) {
        int currentY = startY;
        int usedSlots = 0;
        int maxSlots = ModConfig.GrandExchange.geInventorySlots;
        int i = 0;
        while (i < maxSlots) {
            GEOffer offer = ((GrandExchangeContainer)this.container).playerInventory.getSlotOffer(i);
            if (offer != null && (offer.getState() == GEOffer.OfferState.DRAFT || offer.getState() == GEOffer.OfferState.ACTIVE || offer.getState() == GEOffer.OfferState.PARTIAL)) {
                ++usedSlots;
            }
            ++i;
        }
        String headerText = String.format("Create Sell Offer (%d/%d slots used)", usedSlots, maxSlots);
        FormLabel header = new FormLabel(headerText, HEADER_FONT, -1, 10, currentY);
        this.addComponent((FormComponent)header);
        this.tabComponents.add((FormComponent)header);
        FormLabel hint = new FormLabel("Steps: drop an item into a slot, set the price, click Post Sale, then toggle \"Show on market\".", SMALL_FONT, -1, 10, currentY += 25);
        hint.setColor(new Color(160, 160, 160));
        this.addComponent((FormComponent)hint);
        this.tabComponents.add((FormComponent)hint);
        FormLabel inventoryLabel = new FormLabel("Place items to sell:", NORMAL_FONT, -1, 10, currentY += 30);
        this.addComponent((FormComponent)inventoryLabel);
        this.tabComponents.add((FormComponent)inventoryLabel);
        currentY += 25;
        int slotSize = 40;
        int slotSpacing = 5;
        int slotsPerRow = 5;
        int i2 = 0;
        while (i2 < maxSlots) {
            int row = i2 / slotsPerRow;
            int col = i2 % slotsPerRow;
            int slotX = 10 + col * (slotSize + slotSpacing);
            int slotY = currentY + row * (slotSize + slotSpacing);
            FormContainerSlot slot = new FormContainerSlot(this.client, this.container, ((GrandExchangeContainer)this.container).GE_SELL_SLOTS_START + i2, slotX, slotY);
            this.addComponent((FormComponent)slot);
            this.tabComponents.add((FormComponent)slot);
            ++i2;
        }
        FormLabel priceLabel = new FormLabel("Price/item:", NORMAL_FONT, -1, 10, (currentY += (maxSlots + slotsPerRow - 1) / slotsPerRow * (slotSize + slotSpacing) + 20) + 5);
        this.addComponent((FormComponent)priceLabel);
        this.tabComponents.add((FormComponent)priceLabel);
        FormTextInput sellPriceInput = new FormTextInput(110, currentY, FormInputSize.SIZE_32, 100, 10);
        sellPriceInput.setText("100");
        this.addComponent((FormComponent)sellPriceInput);
        this.tabComponents.add((FormComponent)sellPriceInput);
        FormLabel coinsLabel = new FormLabel("coins", SMALL_FONT, -1, 215, currentY + 8);
        this.addComponent((FormComponent)coinsLabel);
        this.tabComponents.add((FormComponent)coinsLabel);
        FormLabel durationLabel = new FormLabel("Duration:", NORMAL_FONT, -1, 10, (currentY += 45) + 5);
        this.addComponent((FormComponent)durationLabel);
        this.tabComponents.add((FormComponent)durationLabel);
        FormLabel durationValue = new FormLabel("7 days", SMALL_FONT, -1, 210, currentY + 8);
        this.addComponent((FormComponent)durationValue);
        this.tabComponents.add((FormComponent)durationValue);
        FormTextButton postBtn = new FormTextButton("Post Sale", 10, currentY += 30, 150, FormInputSize.SIZE_32, ButtonColor.GREEN);
        postBtn.onClicked(e -> {
            ModLogger.info("[CLIENT SELL] Post sale clicked");
            try {
                int slotWithItem = -1;
                int totalSlots = ModConfig.GrandExchange.geInventorySlots;
                int i = 0;
                while (i < totalSlots) {
                    InventoryItem item = ((GrandExchangeContainer)this.container).playerInventory.getSellInventory().getItem(i);
                    ModLogger.info("[CLIENT SELL] Slot %d: item=%s", i, item != null ? item.item.getStringID() : "null");
                    if (item != null) {
                        GEOffer existingOffer = ((GrandExchangeContainer)this.container).playerInventory.getSlotOffer(i);
                        if (existingOffer == null) {
                            slotWithItem = i;
                            ModLogger.info("[CLIENT SELL] Found available sell slot %d (no existing offer)", i);
                            break;
                        }
                        GEOffer.OfferState state = existingOffer.getState();
                        ModLogger.info("[CLIENT SELL] Slot %d has offer ID=%d, state=%s, enabled=%s, isActive=%s", new Object[]{i, existingOffer.getOfferID(), state, existingOffer.isEnabled(), existingOffer.isActive()});
                        if (state == GEOffer.OfferState.COMPLETED || state == GEOffer.OfferState.EXPIRED || state == GEOffer.OfferState.CANCELLED) {
                            slotWithItem = i;
                            ModLogger.info("[CLIENT SELL] Found available sell slot %d (finished offer ID=%d can be replaced)", i, existingOffer.getOfferID());
                            break;
                        }
                        ModLogger.info("[CLIENT SELL] Slot %d offer is active, skipping", i);
                    }
                    ++i;
                }
                if (slotWithItem == -1) {
                    ModLogger.warn("No available sell slots (all %d slots have active/draft offers or no items present)", totalSlots);
                    this.showSellOfferFeedback("Place an item in a free slot or cancel an existing offer first", true);
                    return;
                }
                String priceText = sellPriceInput.getText().trim();
                if (priceText.isEmpty()) {
                    ModLogger.warn("No price entered");
                    this.showSellOfferFeedback("Enter a price before posting", true);
                    return;
                }
                int pricePerItem = Integer.parseInt(priceText);
                if (pricePerItem <= 0) {
                    ModLogger.warn("Invalid price: %d", pricePerItem);
                    this.showSellOfferFeedback("Price must be above zero", true);
                    return;
                }
                ModLogger.info("Creating sell offer in slot %d: %s x%d @ %d coins/ea", slotWithItem, ((GrandExchangeContainer)this.container).playerInventory.getSellInventory().getItem((int)slotWithItem).item.getStringID(), ((GrandExchangeContainer)this.container).playerInventory.getSellInventory().getItem(slotWithItem).getAmount(), pricePerItem);
                this.showSellOfferFeedback(String.format("Posting offer in slot %d", slotWithItem + 1), false);
                ((GrandExchangeContainer)this.container).createSellOffer.runAndSend(slotWithItem, pricePerItem);
            }
            catch (NumberFormatException ex) {
                ModLogger.warn("Invalid price format");
                this.showSellOfferFeedback("Enter a numeric price", true);
            }
        });
        this.addComponent((FormComponent)postBtn);
        this.tabComponents.add((FormComponent)postBtn);
        this.sellOfferFeedbackLabel = new FormLabel("", SMALL_FONT, -1, 10, currentY += 50);
        this.sellOfferFeedbackLabel.setColor(Color.GRAY);
        this.addComponent((FormComponent)this.sellOfferFeedbackLabel);
        this.tabComponents.add((FormComponent)this.sellOfferFeedbackLabel);
        FormLabel activeHeader = new FormLabel("Your Sell Offers:", HEADER_FONT, -1, 10, currentY += 25);
        this.addComponent((FormComponent)activeHeader);
        this.tabComponents.add((FormComponent)activeHeader);
        currentY += 30;
        GEOffer[] sellOffers = ((GrandExchangeContainer)this.container).playerInventory.getSellOffers();
        boolean hasOffers = false;
        int i3 = 0;
        while (i3 < sellOffers.length) {
            GEOffer offer = sellOffers[i3];
            if (offer != null) {
                hasOffers = true;
                currentY = this.buildSellOfferCard(offer, i3, currentY) + 10;
            }
            ++i3;
        }
        if (!hasOffers) {
            FormLabel emptyLabel = new FormLabel("No active sell offers", SMALL_FONT, -1, 20, currentY);
            this.addComponent((FormComponent)emptyLabel);
            this.tabComponents.add((FormComponent)emptyLabel);
        }
    }

    private int buildSellOfferCard(GEOffer offer, int slotIndex, int startY) {
        int blockY = startY;
        int contentX = 20;
        Item item = ItemRegistry.getItem((String)offer.getItemStringID());
        String itemName = item != null ? item.getDisplayName(item.getDefaultItem(null, 1)) : offer.getItemStringID();
        FormLabel header = new FormLabel(String.format("Slot %d \u2022 %s", slotIndex + 1, this.formatSellOfferState(offer)), NORMAL_FONT, -1, contentX, blockY);
        this.addComponent((FormComponent)header);
        this.tabComponents.add((FormComponent)header);
        FormLabel detailLabel = new FormLabel(String.format("%s | %d/%d sold | %d coins ea", itemName, offer.getQuantityTotal() - offer.getQuantityRemaining(), offer.getQuantityTotal(), offer.getPricePerItem()), SMALL_FONT, -1, contentX, blockY + 18);
        this.addComponent((FormComponent)detailLabel);
        this.tabComponents.add((FormComponent)detailLabel);
        FormLabel remainingLabel = new FormLabel(String.format("Remaining %d \u2022 Offer ID %d", offer.getQuantityRemaining(), offer.getOfferID()), SMALL_FONT, -1, contentX, blockY + 34);
        this.addComponent((FormComponent)remainingLabel);
        this.tabComponents.add((FormComponent)remainingLabel);
        boolean canToggle = offer.getState() == GEOffer.OfferState.DRAFT || offer.getState() == GEOffer.OfferState.ACTIVE || offer.getState() == GEOffer.OfferState.PARTIAL;
        FormCheckBox enableToggle = new FormCheckBox("Show on market", contentX, blockY + 54, offer.isActive());
        enableToggle.setActive(canToggle);
        enableToggle.onClicked(e -> {
            if (!canToggle) {
                formCheckBox.checked = offer.isActive();
                this.showSellOfferFeedback("Offer cannot change state once finished", true);
                return;
            }
            if (formCheckBox.checked) {
                ((GrandExchangeContainer)this.container).enableSellOffer.runAndSend(slotIndex);
            } else {
                ((GrandExchangeContainer)this.container).disableSellOffer.runAndSend(slotIndex);
            }
        });
        this.addComponent((FormComponent)enableToggle);
        this.tabComponents.add((FormComponent)enableToggle);
        FormTextButton cancelBtn = new FormTextButton("Cancel Offer", contentX + 200, blockY + 50, 120, FormInputSize.SIZE_24, ButtonColor.RED);
        boolean canCancel = offer.getState() != GEOffer.OfferState.COMPLETED && offer.getState() != GEOffer.OfferState.CANCELLED;
        cancelBtn.setActive(canCancel);
        cancelBtn.setTooltip("Remove from market and refund remaining items");
        cancelBtn.onClicked(e -> ((GrandExchangeContainer)this.container).cancelSellOffer.runAndSend(slotIndex));
        this.addComponent((FormComponent)cancelBtn);
        this.tabComponents.add((FormComponent)cancelBtn);
        return blockY + 85;
    }

    private void buildCollectionTab(int startY) {
        int currentY = startY;
        FormLabel header = new FormLabel("Collection Box & Settings", HEADER_FONT, -1, 10, currentY);
        this.addComponent((FormComponent)header);
        this.tabComponents.add((FormComponent)header);
        currentY += 30;
        List<CollectionItem> collection = ((GrandExchangeContainer)this.container).playerInventory.getCollectionBox();
        if (collection.isEmpty()) {
            FormLabel emptyLabel = new FormLabel("No items in collection box.", SMALL_FONT, -1, 10, currentY);
            this.addComponent((FormComponent)emptyLabel);
            this.tabComponents.add((FormComponent)emptyLabel);
            currentY += 25;
        } else {
            FormLabel collectionLabel = new FormLabel(String.format("Collection Box (%d items):", collection.size()), NORMAL_FONT, -1, 10, currentY);
            this.addComponent((FormComponent)collectionLabel);
            this.tabComponents.add((FormComponent)collectionLabel);
            currentY += 25;
            int i = 0;
            while (i < Math.min(10, collection.size())) {
                CollectionItem item = collection.get(i);
                String itemText = String.format("%s x%d (%s)", item.getItemStringID(), item.getQuantity(), item.getSource());
                FormLabel itemLabel = new FormLabel(itemText, SMALL_FONT, -1, 20, currentY);
                this.addComponent((FormComponent)itemLabel);
                this.tabComponents.add((FormComponent)itemLabel);
                int itemIndex = i++;
                FormTextButton collectBtn = new FormTextButton("Collect", 700, currentY - 5, 80, FormInputSize.SIZE_24, ButtonColor.BASE);
                collectBtn.onClicked(e -> ((GrandExchangeContainer)this.container).collectItem.runAndSend(itemIndex));
                this.addComponent((FormComponent)collectBtn);
                this.tabComponents.add((FormComponent)collectBtn);
                currentY += 25;
            }
            FormTextButton collectAllBtn = new FormTextButton("Collect All to Bank", 10, currentY += 10, 150, FormInputSize.SIZE_32, ButtonColor.GREEN);
            collectAllBtn.onClicked(e -> ((GrandExchangeContainer)this.container).collectAllToBank.runAndSend());
            this.addComponent((FormComponent)collectAllBtn);
            this.tabComponents.add((FormComponent)collectAllBtn);
            currentY += 45;
        }
        FormLabel settingsHeader = new FormLabel("Settings", HEADER_FONT, -1, 10, currentY);
        this.addComponent((FormComponent)settingsHeader);
        this.tabComponents.add((FormComponent)settingsHeader);
        currentY += 30;
        if (((GrandExchangeContainer)this.container).isWorldOwner()) {
            FormLabel adminHeader = new FormLabel("Admin Configuration", NORMAL_FONT, -1, 10, currentY);
            this.addComponent((FormComponent)adminHeader);
            this.tabComponents.add((FormComponent)adminHeader);
            FormLabel adminNote = new FormLabel("Changes are saved on the server and apply after reopening the Grand Exchange.", SMALL_FONT, -1, 20, currentY += 25);
            adminNote.setColor(new Color(160, 160, 160));
            this.addComponent((FormComponent)adminNote);
            this.tabComponents.add((FormComponent)adminNote);
            FormLabel slotsLabel = new FormLabel("Sell Offer Slots:", SMALL_FONT, -1, 20, (currentY += 25) + 5);
            this.addComponent((FormComponent)slotsLabel);
            this.tabComponents.add((FormComponent)slotsLabel);
            FormTextInput slotsInput = new FormTextInput(160, currentY, FormInputSize.SIZE_24, 60, 3);
            slotsInput.setText(String.valueOf(ModConfig.GrandExchange.geInventorySlots));
            this.addComponent((FormComponent)slotsInput);
            this.tabComponents.add((FormComponent)slotsInput);
            FormTextButton applySlotsBtn = new FormTextButton("Apply", 230, currentY, 80, FormInputSize.SIZE_24, ButtonColor.BASE);
            applySlotsBtn.onClicked(e -> {
                try {
                    int newSlots = Integer.parseInt(slotsInput.getText());
                    ((GrandExchangeContainer)this.container).updateSellSlotConfig.runAndSend(newSlots);
                }
                catch (NumberFormatException numberFormatException) {
                    // empty catch block
                }
            });
            this.addComponent((FormComponent)applySlotsBtn);
            this.tabComponents.add((FormComponent)applySlotsBtn);
            FormLabel rangeLabel = new FormLabel("(5-20)", SMALL_FONT, -1, 320, currentY + 5);
            this.addComponent((FormComponent)rangeLabel);
            this.tabComponents.add((FormComponent)rangeLabel);
            FormLabel buyOrderSlotsLabel = new FormLabel("Buy Order Slots:", SMALL_FONT, -1, 20, (currentY += 35) + 5);
            this.addComponent((FormComponent)buyOrderSlotsLabel);
            this.tabComponents.add((FormComponent)buyOrderSlotsLabel);
            FormTextInput buyOrderSlotsInput = new FormTextInput(160, currentY, FormInputSize.SIZE_24, 60, 3);
            buyOrderSlotsInput.setText(String.valueOf(ModConfig.GrandExchange.buyOrderSlots));
            this.addComponent((FormComponent)buyOrderSlotsInput);
            this.tabComponents.add((FormComponent)buyOrderSlotsInput);
            FormTextButton applyBuyOrderSlotsBtn = new FormTextButton("Apply", 230, currentY, 80, FormInputSize.SIZE_24, ButtonColor.BASE);
            applyBuyOrderSlotsBtn.onClicked(e -> {
                try {
                    int newSlots = Integer.parseInt(buyOrderSlotsInput.getText());
                    ((GrandExchangeContainer)this.container).updateBuySlotConfig.runAndSend(newSlots);
                }
                catch (NumberFormatException numberFormatException) {
                    // empty catch block
                }
            });
            this.addComponent((FormComponent)applyBuyOrderSlotsBtn);
            this.tabComponents.add((FormComponent)applyBuyOrderSlotsBtn);
            FormLabel buyOrderRangeLabel = new FormLabel("(1-10)", SMALL_FONT, -1, 320, currentY + 5);
            this.addComponent((FormComponent)buyOrderRangeLabel);
            this.tabComponents.add((FormComponent)buyOrderRangeLabel);
            currentY += 40;
        } else {
            FormLabel lockedLabel = new FormLabel("Grand Exchange settings can only be changed by the world owner.", SMALL_FONT, -1, 20, currentY);
            lockedLabel.setColor(new Color(160, 160, 160));
            this.addComponent((FormComponent)lockedLabel);
            this.tabComponents.add((FormComponent)lockedLabel);
            currentY += 30;
        }
        FormCheckBox autoBankCheckbox = new FormCheckBox("Auto-send purchases to bank", 10, currentY, ((GrandExchangeContainer)this.container).playerInventory.isAutoSendToBank());
        autoBankCheckbox.onClicked(e -> ((GrandExchangeContainer)this.container).toggleAutoBank.runAndSend(formCheckBox.checked));
        this.addComponent((FormComponent)autoBankCheckbox);
        this.tabComponents.add((FormComponent)autoBankCheckbox);
        FormCheckBox notifyPartialCheckbox = new FormCheckBox("Notify on partial sales", 10, currentY += 30, ((GrandExchangeContainer)this.container).playerInventory.isNotifyPartialSales());
        notifyPartialCheckbox.onClicked(e -> ((GrandExchangeContainer)this.container).toggleNotifyPartial.runAndSend(formCheckBox.checked));
        this.addComponent((FormComponent)notifyPartialCheckbox);
        this.tabComponents.add((FormComponent)notifyPartialCheckbox);
        FormCheckBox playSoundCheckbox = new FormCheckBox("Play sound on sale", 10, currentY += 30, ((GrandExchangeContainer)this.container).playerInventory.isPlaySoundOnSale());
        playSoundCheckbox.onClicked(e -> ((GrandExchangeContainer)this.container).togglePlaySound.runAndSend(formCheckBox.checked));
        this.addComponent((FormComponent)playSoundCheckbox);
        this.tabComponents.add((FormComponent)playSoundCheckbox);
    }

    private void buildHistoryTab(int startY) {
        String[] stats;
        int currentY = startY;
        FormLabel header = new FormLabel("Transaction History & Statistics", HEADER_FONT, -1, 10, currentY);
        this.addComponent((FormComponent)header);
        this.tabComponents.add((FormComponent)header);
        FormLabel statsHeader = new FormLabel("Your Statistics", NORMAL_FONT, -1, 10, currentY += 35);
        this.addComponent((FormComponent)statsHeader);
        this.tabComponents.add((FormComponent)statsHeader);
        currentY += 25;
        String[] stringArray = stats = new String[]{String.format("Total Items Purchased: %d", ((GrandExchangeContainer)this.container).playerInventory.getTotalItemsPurchased()), String.format("Total Items Sold: %d", ((GrandExchangeContainer)this.container).playerInventory.getTotalItemsSold()), String.format("Sell Offers Created: %d", ((GrandExchangeContainer)this.container).playerInventory.getTotalSellOffersCreated()), String.format("Sell Offers Completed: %d", ((GrandExchangeContainer)this.container).playerInventory.getTotalSellOffersCompleted()), String.format("Buy Orders Created: %d", ((GrandExchangeContainer)this.container).playerInventory.getTotalBuyOrdersCreated()), String.format("Buy Orders Completed: %d", ((GrandExchangeContainer)this.container).playerInventory.getTotalBuyOrdersCompleted())};
        int n = stats.length;
        int n2 = 0;
        while (n2 < n) {
            String stat = stringArray[n2];
            FormLabel statLabel = new FormLabel(stat, SMALL_FONT, -1, 20, currentY);
            this.addComponent((FormComponent)statLabel);
            this.tabComponents.add((FormComponent)statLabel);
            currentY += 20;
            ++n2;
        }
        FormLabel salesHeader = new FormLabel("Recent Sales (Last 20)", NORMAL_FONT, -1, 10, currentY += 15);
        this.addComponent((FormComponent)salesHeader);
        this.tabComponents.add((FormComponent)salesHeader);
        currentY += 25;
        List<SaleNotification> sales = ((GrandExchangeContainer)this.container).playerInventory.getSaleHistory();
        if (sales.isEmpty()) {
            FormLabel noSales = new FormLabel("No recent sales", SMALL_FONT, -1, 20, currentY);
            this.addComponent((FormComponent)noSales);
            this.tabComponents.add((FormComponent)noSales);
        } else {
            int displayCount = Math.min(20, sales.size());
            int i = 0;
            while (i < displayCount) {
                SaleNotification sale = sales.get(i);
                String saleText = String.format("%s x%d @ %d coins ea (Total: %d coins) - Buyer: %s", sale.getItemStringID(), sale.getQuantitySold(), sale.getPricePerItem(), sale.getTotalCoins(), sale.getBuyerName());
                FormLabel saleLabel = new FormLabel(saleText, SMALL_FONT, -1, 20, currentY);
                this.addComponent((FormComponent)saleLabel);
                this.tabComponents.add((FormComponent)saleLabel);
                currentY += 20;
                ++i;
            }
        }
    }

    private String getCoinText(GrandExchangeContainer container) {
        return String.format("Bank: %d coins", container.clientCoinCount);
    }

    private void refreshCoinLabel() {
        if (this.coinLabel != null) {
            this.coinLabel.setText(this.getCoinText((GrandExchangeContainer)this.container));
        }
    }

    private String getItemDisplayName(String itemStringID) {
        if (itemStringID == null || itemStringID.isEmpty()) {
            return "Unknown Item";
        }
        Item item = ItemRegistry.getItem((String)itemStringID);
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
        if (minutes >= 1440L) {
            long days = minutes / 1440L;
            return String.format("%d day%s left", days, days == 1L ? "" : "s");
        }
        if (minutes >= 60L) {
            long hours = minutes / 60L;
            return String.format("%d hour%s left", hours, hours == 1L ? "" : "s");
        }
        return String.format("%d min left", Math.max(1L, minutes));
    }

    private void showBuyOrderFeedback(String message, boolean isError) {
        if (this.buyOrderFeedbackLabel == null) {
            return;
        }
        this.buyOrderFeedbackLabel.setText(message == null ? "" : message);
        this.buyOrderFeedbackLabel.setColor(isError ? Color.ORANGE : new Color(70, 190, 90));
    }

    private void showSellOfferFeedback(String message, boolean isError) {
        if (this.sellOfferFeedbackLabel == null) {
            return;
        }
        this.sellOfferFeedbackLabel.setText(message == null ? "" : message);
        this.sellOfferFeedbackLabel.setColor(isError ? Color.ORANGE : new Color(70, 190, 90));
    }

    private String formatBuyOrderState(BuyOrder order) {
        if (order == null) {
            return "Empty";
        }
        switch (order.getState()) {
            case DRAFT: {
                return order.isConfigured() ? "Draft (disabled)" : "Draft (configure item)";
            }
            case ACTIVE: {
                return "Active";
            }
            case PARTIAL: {
                return "Partial fill";
            }
            case COMPLETED: {
                return "Completed";
            }
            case EXPIRED: {
                return "Expired";
            }
            case CANCELLED: {
                return "Cancelled";
            }
        }
        return order.getState().name();
    }

    private InventoryItem buildListingIcon(String itemStringID, int quantity) {
        Item item = ItemRegistry.getItem((String)itemStringID);
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
            case DRAFT: {
                return offer.isEnabled() ? "Pending" : "Draft (hidden)";
            }
            case ACTIVE: {
                return "Active";
            }
            case PARTIAL: {
                return "Partial fill";
            }
            case COMPLETED: {
                return "Completed";
            }
            case EXPIRED: {
                return "Expired";
            }
            case CANCELLED: {
                return "Cancelled";
            }
        }
        return offer.getState().name();
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

    private boolean isWorldOwner() {
        return this.container != null && ((GrandExchangeContainer)this.container).isWorldOwner();
    }

    private void buildAdminDashboardTab(int startY) {
        int currentY = startY;
        FormLabel header = new FormLabel("Admin Dashboard - Coming Soon", HEADER_FONT, -1, 10, currentY);
        this.addComponent((FormComponent)header);
        this.tabComponents.add((FormComponent)header);
        FormLabel desc = new FormLabel("Advanced market analytics will be available here.", NORMAL_FONT, -1, 10, currentY += 30);
        this.addComponent((FormComponent)desc);
        this.tabComponents.add((FormComponent)desc);
    }
}
