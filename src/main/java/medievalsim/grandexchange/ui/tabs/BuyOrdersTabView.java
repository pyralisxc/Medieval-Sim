package medievalsim.grandexchange.ui.tabs;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import medievalsim.config.ModConfig;
import medievalsim.grandexchange.domain.BuyOrder;
import medievalsim.grandexchange.model.snapshot.BuyOrderSlotSnapshot;
import medievalsim.grandexchange.model.snapshot.BuyOrdersSnapshot;
import medievalsim.grandexchange.net.GEFeedbackChannel;
import medievalsim.grandexchange.net.GEFeedbackLevel;
import medievalsim.grandexchange.ui.GrandExchangeContainer;
import medievalsim.grandexchange.ui.components.TooltipFormCheckBox;
import medievalsim.grandexchange.ui.layout.GrandExchangeFonts;
import medievalsim.grandexchange.ui.viewmodel.GrandExchangeViewModel;
import medievalsim.grandexchange.ui.viewmodel.BuyTabState;
import medievalsim.grandexchange.ui.viewmodel.BuyTabState.BuyActionPendingType;
import medievalsim.grandexchange.ui.util.GrandExchangeUIUtils;
import medievalsim.grandexchange.ui.util.TabHeartbeat;
import medievalsim.packets.PacketGECreateBuyOrder;
import medievalsim.ui.helpers.SearchableDropdown;
import medievalsim.util.ModLogger;
import necesse.engine.localization.Localization;
import necesse.engine.registries.ItemRegistry;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.ui.ButtonColor;
import necesse.gfx.forms.components.FormItemIcon;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;

/**
 * Encapsulates buy-order tab rendering and interaction logic.
 */
public final class BuyOrdersTabView {
    private final TabHostContext host;
    private final GrandExchangeContainer container;
    private final GrandExchangeViewModel viewModel;
    private final BiConsumer<String, Boolean> feedbackChannel;
    private final Consumer<FormLabel> feedbackRegistrar;
    private final Consumer<SearchableDropdown<Item>> searchRegistrar;

    private SearchableDropdown<Item> itemSearch;

    public BuyOrdersTabView(TabHostContext host,
                            GrandExchangeContainer container,
                            GrandExchangeViewModel viewModel,
                            BiConsumer<String, Boolean> feedbackChannel,
                            Consumer<FormLabel> feedbackRegistrar,
                            Consumer<SearchableDropdown<Item>> searchRegistrar) {
        this.host = host;
        this.container = container;
        this.viewModel = viewModel;
        this.feedbackChannel = feedbackChannel;
        this.feedbackRegistrar = feedbackRegistrar;
        this.searchRegistrar = searchRegistrar;
    }

    public void build(int startY) {
        BuyTabState buyState = viewModel.getBuyTabState();
        boolean hasPendingAction = buyState.hasPendingAction();
        float createCooldown = buyState.getCreateCooldownRemainingSeconds();
        float toggleCooldown = buyState.getToggleCooldownRemainingSeconds();
        int currentY = startY;
        BuyOrdersSnapshot snapshot = viewModel.getBuyOrdersSnapshot();
        List<BuyOrderSlotSnapshot> slots = snapshot.slots();
        int usedSlots = countActiveBuyOrderSlots(slots);
        int maxSlots = Math.max(slots.size(), ModConfig.GrandExchange.buyOrderSlots);

        // Create and add heartbeat component for dynamic updates
        TabHeartbeat heartbeat = new TabHeartbeat();
        host.addComponent(heartbeat);

        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.buy.header",
            "used", Integer.toString(usedSlots),
            "total", Integer.toString(maxSlots)), GrandExchangeFonts.HEADER, -1, 10, currentY));

        FormLabel hint = new FormLabel(Localization.translate("ui", "grandexchange.buy.steps"), GrandExchangeFonts.SMALL, -1, 10, currentY += 25);
        hint.setColor(GrandExchangeFonts.SECONDARY_TEXT);
        host.addComponent(hint);

        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.buy.itemlabel"), GrandExchangeFonts.BODY, -1, 10, currentY += 30));

        List<Item> allItems = ItemRegistry.streamItems()
            .filter(item -> item != null
                && !item.getStringID().endsWith("egg")
                && !item.getStringID().contains("summon")
                && !item.getStringID().endsWith("staff"))
            .collect(Collectors.toList());
        Item[] selectedItemHolder = new Item[1];
        String restoredItemId = buyState.getSelectedItemStringID();
        if (restoredItemId != null) {
            Item restored = ItemRegistry.getItem(restoredItemId);
            if (restored != null) {
                selectedItemHolder[0] = restored;
            }
        }

        itemSearch = new SearchableDropdown<>(10, currentY += 25, 300, 250,
            Localization.translate("ui", "grandexchange.buy.searchplaceholder"),
            allItems,
            item -> item.getDisplayName(item.getDefaultItem(null, 1)),
            item -> {
                selectedItemHolder[0] = item;
                buyState.saveSelectedItem(item.getStringID());
                ModLogger.debug("Selected item for buy order: %s", item.getStringID());
            });
        itemSearch.addToForm(host.getForm());
        itemSearch.beginUpdate();
        if (selectedItemHolder[0] != null) {
            itemSearch.setSelectedItem(selectedItemHolder[0]);
        }
        itemSearch.endUpdate();
        itemSearch.getSearchInput().setActive(!hasPendingAction);
        searchRegistrar.accept(itemSearch);

        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.buy.quantitylabel"), GrandExchangeFonts.BODY, -1, 10, (currentY += 45) + 5));
        FormTextInput qtyInput = host.addComponent(new FormTextInput(90, currentY, FormInputSize.SIZE_32, 80, 8));
        qtyInput.setText(buyState.getQuantityDraft("1"));
        qtyInput.setActive(!hasPendingAction);
        qtyInput.onChange(event -> buyState.saveQuantityDraft(qtyInput.getText()));

        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.buy.priceperitem"), GrandExchangeFonts.BODY, -1, 200, currentY + 5));
        FormTextInput priceInput = host.addComponent(new FormTextInput(310, currentY, FormInputSize.SIZE_32, 100, 10));
        priceInput.setText(buyState.getPriceDraft("100"));
        priceInput.setActive(!hasPendingAction);
        priceInput.onChange(event -> buyState.savePriceDraft(priceInput.getText()));

        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.buy.coinsuffix"), GrandExchangeFonts.SMALL, -1, 415, currentY + 8));

        FormTextButton createBtn = host.addComponent(new FormTextButton(Localization.translate("ui", "grandexchange.buy.createbutton"), 10, currentY += 45, 200, FormInputSize.SIZE_32, ButtonColor.GREEN));
        boolean createActive = !hasPendingAction && createCooldown <= 0f;
        createBtn.setActive(createActive);
        if (hasPendingAction) {
            setBuyActionButtonPending(createBtn);
        }
        createBtn.onClicked(e -> handleCreateBuyOrder(selectedItemHolder, qtyInput, priceInput, buyState, createBtn));

        FormLabel createCooldownLabel = host.addComponent(new FormLabel("", GrandExchangeFonts.SMALL, -1, 220, currentY + 10));
        createCooldownLabel.setColor(GrandExchangeFonts.COOLDOWN_TEXT);
        updateCooldownLabel(createCooldownLabel, createCooldown, "grandexchange.buy.cooldown.create");

        // Register heartbeat task to update cooldown label dynamically
        heartbeat.registerTask(() -> {
            float remaining = viewModel.getBuyTabState().getCreateCooldownRemainingSeconds();
            updateCooldownLabel(createCooldownLabel, remaining, "grandexchange.buy.cooldown.create");
        });

        // Feedback now goes to status bar only - no inline label needed
        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.buy.sectionheader"), GrandExchangeFonts.HEADER, -1, 10, currentY += 40));

        currentY += 30;
        
        // Create scrollable area for buy order cards
        int scrollAreaHeight = 340;
        int scrollAreaWidth = 860;
        FormContentBox ordersScrollArea = new FormContentBox(0, currentY, scrollAreaWidth, scrollAreaHeight);
        ordersScrollArea.shouldLimitDrawArea = true;
        host.addComponent(ordersScrollArea);
        
        int scrollY = 5;
        boolean hasOrders = false;
        for (BuyOrderSlotSnapshot slot : slots) {
            if (!hasConfiguredOrder(slot)) {
                continue;
            }
            hasOrders = true;
            scrollY = buildBuyOrderCard(ordersScrollArea, slot, scrollY, buyState, toggleCooldown) + 10;
        }
        if (!hasOrders) {
            ordersScrollArea.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.buy.empty"), GrandExchangeFonts.SMALL, -1, 20, 5));
        }
        ordersScrollArea.fitContentBoxToComponents(5);
    }

    private int buildBuyOrderCard(FormContentBox scrollArea,
                                  BuyOrderSlotSnapshot slot,
                                  int startY,
                                  BuyTabState buyState,
                                  float globalToggleCooldown) {
        int blockY = startY;
        int iconX = 20;
        int contentX = iconX + 42; // Offset text to the right of icon
        boolean globalPending = buyState.hasPendingAction();
        int slotIndex = slot.slotIndex();
        boolean enablePending = buyState.isActionPending(slotIndex, BuyActionPendingType.ENABLE);
        boolean disablePending = buyState.isActionPending(slotIndex, BuyActionPendingType.DISABLE);
        boolean cancelPending = buyState.isActionPending(slotIndex, BuyActionPendingType.CANCEL);
        boolean slotPending = enablePending || disablePending || cancelPending;

        // Add item icon with full tooltip on hover
        InventoryItem iconItem = buildBuyOrderIcon(slot.itemStringID());
        if (iconItem != null) {
            FormItemIcon iconComponent = new FormItemIcon(iconX, blockY, iconItem, false);
            scrollArea.addComponent(iconComponent);
        }

        String itemName = getItemDisplayName(slot.itemStringID());
        FormLabel headerLabel = new FormLabel(formatBuyOrderHeader(slotIndex, slot), GrandExchangeFonts.BODY, -1, contentX, blockY);
        headerLabel.setColor(GrandExchangeFonts.PRIMARY_TEXT);
        scrollArea.addComponent(headerLabel);

        FormLabel qtyLabel = new FormLabel(formatBuyOrderQuantityLine(slot, itemName), GrandExchangeFonts.SMALL, -1, contentX, blockY + 18);
        qtyLabel.setColor(GrandExchangeFonts.PRIMARY_TEXT);
        scrollArea.addComponent(qtyLabel);

        String priceLine = formatBuyOrderPriceLine(slot);
        if (priceLine != null) {
            scrollArea.addComponent(new FormLabel(priceLine, GrandExchangeFonts.SMALL, -1, contentX, blockY + 34));
        }

        boolean canToggle = canToggle(slot);
        boolean toggleCooldownExpired = globalToggleCooldown <= 0f;
        TooltipFormCheckBox enableToggle = new TooltipFormCheckBox(Localization.translate("ui", "grandexchange.buy.toggle.label"), contentX, blockY + 54, isSlotActive(slot));
        boolean toggleActive = canToggle && toggleCooldownExpired && !slotPending && !globalPending;
        enableToggle.setActive(toggleActive);
        if (!toggleActive) {
            if (!canToggle) {
                enableToggle.setTooltipSupplier(() -> Localization.translate("ui", "grandexchange.buy.feedback.statefinalized"));
            } else if (!toggleCooldownExpired) {
                enableToggle.setTooltipSupplier(() -> formatCooldownMessage("grandexchange.buy.cooldown.toggle", globalToggleCooldown));
            } else if (slotPending) {
                enableToggle.setTooltipSupplier(this::getBuyAwaitingTooltip);
            } else {
                enableToggle.setTooltipSupplier(this::getBuyGlobalPendingTooltip);
            }
        } else {
            enableToggle.clearTooltipSupplier();
        }
        enableToggle.onClicked(e -> handleBuyToggle(slot, enableToggle, canToggle));
        scrollArea.addComponent(enableToggle);

        FormTextButton cancelBtn = new FormTextButton(Localization.translate("ui", "grandexchange.buy.cancelbutton"), contentX + 220, blockY + 50, 120, FormInputSize.SIZE_24, ButtonColor.RED);
        boolean cancellable = slot.state() != BuyOrder.BuyOrderState.CANCELLED && slot.state() != BuyOrder.BuyOrderState.COMPLETED;
        boolean cancelActive = cancellable && !globalPending;
        cancelBtn.setActive(cancelActive);
        if (!cancellable) {
            cancelBtn.setTooltip(Localization.translate("ui", "grandexchange.buy.feedback.statefinalized"));
        } else if (globalPending) {
            cancelBtn.setTooltip(getBuyGlobalPendingTooltip());
        } else {
            cancelBtn.setTooltip(Localization.translate("ui", "grandexchange.buy.canceltooltip"));
        }
        cancelBtn.onClicked(e -> handleCancelBuyOrder(slot, cancelBtn));
        scrollArea.addComponent(cancelBtn);
        return blockY + 85;
    }    private void handleCreateBuyOrder(Item[] selectedItemHolder,
                                      FormTextInput qtyInput,
                                      FormTextInput priceInput,
                                      BuyTabState buyState,
                                      FormTextButton createBtn) {
        // Immediately disable button to prevent double-clicks
        createBtn.setActive(false);
        
        if (buyState.hasPendingAction()) {
            feedbackChannel.accept(Localization.translate("ui", "grandexchange.buy.feedback.pendingaction"), true);
            return;
        }
        Item selectedItem = selectedItemHolder[0];
        if (selectedItem == null) {
            createBtn.setActive(true); // Re-enable on validation failure
            feedbackChannel.accept(Localization.translate("ui", "grandexchange.buy.feedback.selectitem"), true);
            return;
        }
        try {
            String qtyText = qtyInput.getText() == null ? "" : qtyInput.getText().trim();
            String priceText = priceInput.getText() == null ? "" : priceInput.getText().trim();
            int quantity = Integer.parseInt(qtyText);
            if (quantity <= 0 || quantity > 9999) {
                createBtn.setActive(true); // Re-enable on validation failure
                feedbackChannel.accept(Localization.translate("ui", "grandexchange.buy.feedback.quantityrange"), true);
                return;
            }
            int pricePerItem = Integer.parseInt(priceText);
            if (pricePerItem <= 0) {
                createBtn.setActive(true); // Re-enable on validation failure
                feedbackChannel.accept(Localization.translate("ui", "grandexchange.buy.feedback.pricerange"), true);
                return;
            }
            int availableSlot = findAvailableBuyOrderSlot();
            if (availableSlot == -1) {
                createBtn.setActive(true); // Re-enable on validation failure
                feedbackChannel.accept(Localization.translate("ui", "grandexchange.buy.feedback.noslots"), true);
                return;
            }

            buyState.saveSelectedItem(selectedItem.getStringID());
            buyState.saveQuantityDraft(qtyText);
            buyState.savePriceDraft(priceText);
            buyState.rememberSuccessfulDrafts(selectedItem.getStringID(), qtyText, priceText);

            ModLogger.info("Creating buy order in slot %d: %s x%d @ %d coins", availableSlot,
                selectedItem.getStringID(), quantity, pricePerItem);
            feedbackChannel.accept(Localization.translate("ui", "grandexchange.buy.feedback.posting",
                "slot", Integer.toString(availableSlot + 1),
                "quantity", Integer.toString(quantity),
                "item", selectedItem.getStringID(),
                "price", Integer.toString(pricePerItem)), false);

            buyState.markPendingAction(availableSlot, BuyActionPendingType.CREATE);
            setBuyActionButtonPending(createBtn);
            disableBuyOrderInputs(qtyInput, priceInput);
            PacketGECreateBuyOrder packet = new PacketGECreateBuyOrder(availableSlot,
                selectedItem.getStringID(), quantity, pricePerItem, 7);
            host.getClient().network.sendPacket(packet);
        } catch (NumberFormatException ex) {
            createBtn.setActive(true); // Re-enable on validation failure
            ModLogger.warn("Invalid number format in buy order inputs");
            feedbackChannel.accept(Localization.translate("ui", "grandexchange.buy.feedback.numeric"), true);
        }
    }

    private void disableBuyOrderInputs(FormTextInput qtyInput, FormTextInput priceInput) {
        qtyInput.setActive(false);
        priceInput.setActive(false);
        if (itemSearch != null) {
            itemSearch.getSearchInput().setActive(false);
            itemSearch.getSearchInput().setTyping(false);
        }
    }

    private void setBuyActionButtonPending(FormTextButton button) {
        button.setActive(false);
        button.setTooltip(getBuyAwaitingTooltip());
    }

    private String getBuyGlobalPendingTooltip() {
        return Localization.translate("ui", "grandexchange.buy.tooltips.pendingglobal");
    }

    private String getBuyAwaitingTooltip() {
        return Localization.translate("ui", "grandexchange.buy.tooltips.awaiting");
    }

    private void handleBuyToggle(BuyOrderSlotSnapshot slot, TooltipFormCheckBox toggle, boolean canToggle) {
        BuyTabState buyState = viewModel.getBuyTabState();
        if (!canToggle) {
            toggle.checked = isSlotActive(slot);
            feedbackChannel.accept(Localization.translate("ui", "grandexchange.buy.feedback.statefinalized"), true);
            return;
        }
        int slotIndex = slot.slotIndex();
        if (buyState.hasPendingAction()
            && !buyState.isActionPending(slotIndex, BuyActionPendingType.ENABLE)
            && !buyState.isActionPending(slotIndex, BuyActionPendingType.DISABLE)) {
            toggle.checked = isSlotActive(slot);
            feedbackChannel.accept(Localization.translate("ui", "grandexchange.buy.feedback.pendingaction"), true);
            return;
        }

        // If trying to ENABLE, check if player can afford the escrow
        if (toggle.checked) {
            long totalCoinsRequired = (long) slot.pricePerItem() * (long) slot.quantityRemaining();
            if (container.clientCoinCount < totalCoinsRequired) {
                toggle.checked = false; // Revert the checkbox
                feedbackChannel.accept(Localization.translate("ui", "grandexchange.buy.feedback.insufficientfunds",
                    "required", Long.toString(totalCoinsRequired),
                    "available", Long.toString(container.clientCoinCount)), true);
                return;
            }
        }

        BuyActionPendingType pendingType = toggle.checked ? BuyActionPendingType.ENABLE : BuyActionPendingType.DISABLE;
        buyState.markPendingAction(slotIndex, pendingType);
        toggle.setActive(false);
        toggle.setTooltipSupplier(this::getBuyAwaitingTooltip);
        if (toggle.checked) {
            container.enableBuyOrder.runAndSend(slotIndex);
        } else {
            container.disableBuyOrder.runAndSend(slotIndex);
        }
    }

    private void handleCancelBuyOrder(BuyOrderSlotSnapshot slot, FormTextButton cancelBtn) {
        BuyTabState buyState = viewModel.getBuyTabState();
        if (slot.state() == BuyOrder.BuyOrderState.CANCELLED || slot.state() == BuyOrder.BuyOrderState.COMPLETED) {
            feedbackChannel.accept(Localization.translate("ui", "grandexchange.buy.feedback.statefinalized"), true);
            return;
        }
        if (buyState.hasPendingAction()) {
            feedbackChannel.accept(Localization.translate("ui", "grandexchange.buy.feedback.pendingaction"), true);
            return;
        }
        int slotIndex = slot.slotIndex();
        buyState.markPendingAction(slotIndex, BuyActionPendingType.CANCEL);
        cancelBtn.setActive(false);
        cancelBtn.setTooltip(getBuyAwaitingTooltip());
        container.cancelBuyOrder.runAndSend(slotIndex);
    }

    private int countActiveBuyOrderSlots(List<BuyOrderSlotSnapshot> orders) {
        int usedSlots = 0;
        for (BuyOrderSlotSnapshot slot : orders) {
            if (hasConfiguredOrder(slot)
                && (slot.state() == BuyOrder.BuyOrderState.DRAFT
                || slot.state() == BuyOrder.BuyOrderState.ACTIVE
                || slot.state() == BuyOrder.BuyOrderState.PARTIAL)) {
                usedSlots++;
            }
        }
        return usedSlots;
    }

    private String formatBuyOrderHeader(int slotIndex, BuyOrderSlotSnapshot slot) {
        return Localization.translate("ui", "grandexchange.buy.slotheader",
            "slot", Integer.toString(slotIndex + 1),
            "state", formatBuyOrderState(slot));
    }

    private String formatBuyOrderQuantityLine(BuyOrderSlotSnapshot slot, String itemName) {
        if (slot == null || slot.quantityTotal() <= 0) {
            return itemName;
        }
        int filled = Math.max(0, slot.quantityTotal() - slot.quantityRemaining());
        return Localization.translate("ui", "grandexchange.buy.quantityline",
            "item", itemName,
            "filled", Integer.toString(filled),
            "total", Integer.toString(slot.quantityTotal()),
            "remaining", Integer.toString(slot.quantityRemaining()));
    }

    private String formatBuyOrderPriceLine(BuyOrderSlotSnapshot slot) {
        if (slot == null || !hasConfiguredOrder(slot)) {
            return null;
        }
        long escrowValue = Math.max(0L, (long) slot.quantityRemaining() * (long) slot.pricePerItem());
        return Localization.translate("ui", "grandexchange.buy.priceinfo",
            "price", Integer.toString(slot.pricePerItem()),
            "escrow", String.format("%,d", escrowValue));
    }

    private String formatBuyOrderState(BuyOrderSlotSnapshot slot) {
        if (slot == null || slot.state() == null) {
            return Localization.translate("ui", "grandexchange.buy.state.empty");
        }
        return switch (slot.state()) {
            case DRAFT -> hasConfiguredOrder(slot)
                ? Localization.translate("ui", "grandexchange.buy.state.draftready")
                : Localization.translate("ui", "grandexchange.buy.state.draftneedsitem");
            case ACTIVE -> Localization.translate("ui", "grandexchange.buy.state.active");
            case PARTIAL -> Localization.translate("ui", "grandexchange.buy.state.partial");
            case COMPLETED -> Localization.translate("ui", "grandexchange.buy.state.completed");
            case EXPIRED -> Localization.translate("ui", "grandexchange.buy.state.expired");
            case CANCELLED -> Localization.translate("ui", "grandexchange.buy.state.cancelled");
        };
    }

    private boolean canToggle(BuyOrderSlotSnapshot slot) {
        if (!hasConfiguredOrder(slot)) {
            return false;
        }
        return slot.state() == BuyOrder.BuyOrderState.DRAFT
            || slot.state() == BuyOrder.BuyOrderState.ACTIVE
            || slot.state() == BuyOrder.BuyOrderState.PARTIAL;
    }

    private boolean hasConfiguredOrder(BuyOrderSlotSnapshot slot) {
        return slot != null
            && slot.itemStringID() != null
            && !slot.itemStringID().isBlank();
    }

    private boolean isSlotActive(BuyOrderSlotSnapshot slot) {
        if (slot == null) {
            return false;
        }
        return slot.state() == BuyOrder.BuyOrderState.ACTIVE
            || slot.state() == BuyOrder.BuyOrderState.PARTIAL;
    }

    private int findAvailableBuyOrderSlot() {
        List<BuyOrderSlotSnapshot> slots = viewModel.getBuyOrdersSnapshot().slots();
        for (int i = 0; i < slots.size(); i++) {
            BuyOrderSlotSnapshot slot = slots.get(i);
            if (!hasConfiguredOrder(slot)) {
                return i;
            }
            if (slot.state() == BuyOrder.BuyOrderState.COMPLETED
                || slot.state() == BuyOrder.BuyOrderState.EXPIRED
                || slot.state() == BuyOrder.BuyOrderState.CANCELLED) {
                return i;
            }
        }
        return -1;
    }

    private String getItemDisplayName(String itemStringID) {
        return GrandExchangeUIUtils.getItemDisplayName(itemStringID);
    }

    /**
     * Creates an InventoryItem for displaying as an icon with full item tooltip.
     */
    private InventoryItem buildBuyOrderIcon(String itemStringID) {
        if (itemStringID == null || itemStringID.isEmpty()) {
            return null;
        }
        Item item = ItemRegistry.getItem(itemStringID);
        if (item == null) {
            return null;
        }
        InventoryItem iconItem = new InventoryItem(item, 1);
        iconItem.setNew(false);
        return iconItem;
    }

    private void updateCooldownLabel(FormLabel label, float secondsRemaining, String localizationKey) {
        if (label == null) {
            return;
        }
        if (secondsRemaining <= 0f) {
            label.setText("");
            return;
        }
        label.setText(formatCooldownMessage(localizationKey, secondsRemaining));
    }

    private String formatCooldownMessage(String localizationKey, float secondsRemaining) {
        int seconds = Math.max(1, Math.round(secondsRemaining));
        return Localization.translate("ui", localizationKey, "seconds", Integer.toString(seconds));
    }
}
