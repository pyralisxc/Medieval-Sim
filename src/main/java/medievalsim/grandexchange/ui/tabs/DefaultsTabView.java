package medievalsim.grandexchange.ui.tabs;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import medievalsim.config.ModConfig;
import medievalsim.grandexchange.ui.GrandExchangeContainer;
import medievalsim.grandexchange.ui.components.TooltipFormCheckBox;
import medievalsim.grandexchange.ui.layout.GrandExchangeFonts;
import medievalsim.grandexchange.ui.viewmodel.GrandExchangeViewModel;
import medievalsim.grandexchange.ui.viewmodel.DefaultsTabState;
import medievalsim.ui.helpers.PlayerDropdownEntry;
import medievalsim.util.ModLogger;
import necesse.engine.Settings;
import necesse.engine.localization.Localization;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormDropdownSelectionButton;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.ui.ButtonColor;

/**
 * Comprehensive GE settings tab with collapsible categories.
 * All settings are organized professionally for world owners.
 * 
 * Categories:
 * 1. Inventory &amp; Slots
 * 2. Pricing &amp; Fees (includes profit recipient)
 * 3. Offer Duration
 * 4. Trading Features
 * 5. Collection &amp; UI
 * 6. Advanced (rate limiting, fraud detection, etc.)
 */
public final class DefaultsTabView {
    private final TabHostContext host;
    private final GrandExchangeContainer container;
    private final GrandExchangeViewModel viewModel;
    private final BiConsumer<String, Boolean> feedbackChannel;
    private final Consumer<FormLabel> feedbackRegistrar;

    // Section expansion state
    private static final Map<String, Boolean> sectionExpanded = new HashMap<>();
    static {
        // Default: first two sections expanded, rest collapsed
        sectionExpanded.put("inventory", true);
        sectionExpanded.put("pricing", true);
        sectionExpanded.put("duration", false);
        sectionExpanded.put("trading", false);
        sectionExpanded.put("collection", false);
        sectionExpanded.put("advanced", false);
    }

    // UI components that need to be tracked for rebuilding
    private FormContentBox scrollArea;
    private FormLabel feedbackLabel;
    private int scrollAreaHeight;

    // Player list for profit recipient dropdown
    private List<PlayerDropdownEntry> playerList = new ArrayList<>();

    private static final Color SECTION_HEADER_COLOR = Color.BLACK;
    private static final Color SETTING_LABEL_COLOR = Color.BLACK;
    private static final Color HINT_COLOR = Color.BLACK;

    public DefaultsTabView(TabHostContext host,
                           GrandExchangeContainer container,
                           GrandExchangeViewModel viewModel,
                           BiConsumer<String, Boolean> feedbackChannel,
                           Consumer<FormLabel> feedbackRegistrar) {
        this.host = host;
        this.container = container;
        this.viewModel = viewModel;
        this.feedbackChannel = feedbackChannel;
        this.feedbackRegistrar = feedbackRegistrar;
    }

    public void build(int startY) {
        int currentY = startY;

        // Header
        host.addComponent(new FormLabel(
            Localization.translate("ui", "grandexchange.defaults.header"),
            GrandExchangeFonts.HEADER, -1, 10, currentY));
        
        FormLabel subtitle = new FormLabel(
            Localization.translate("ui", "grandexchange.defaults.subtitle"),
            GrandExchangeFonts.SMALL, -1, 10, currentY += 25);
        subtitle.setColor(GrandExchangeFonts.SECONDARY_TEXT);
        host.addComponent(subtitle);

        // Feedback label
        feedbackLabel = new FormLabel("", GrandExchangeFonts.SMALL, -1, 10, currentY += 20);
        feedbackLabel.setColor(GrandExchangeFonts.SECONDARY_TEXT);
        host.addComponent(feedbackLabel);
        feedbackRegistrar.accept(feedbackLabel);

        // Create scrollable content area - calculated to fit within form bounds
        // Form is 700px tall, tab starts at ~87, status bar at ~672
        // After header (25) and feedback (20) + padding (15) = ~147 from form top
        // Leave 40px margin above status bar, so max height = 672 - (startY + 60) - 40
        scrollAreaHeight = 480; // Fits within form with margins
        scrollArea = new FormContentBox(10, currentY + 15, 860, scrollAreaHeight);
        scrollArea.shouldLimitDrawArea = true;
        host.addComponent(scrollArea);

        // Build all sections inside scroll area
        buildScrollContent();
    }

    private void buildScrollContent() {
        int scrollY = 5;
        int contentWidth = 860;

        // Section 1: Inventory & Slots
        scrollY = buildSectionHeader(scrollArea, "inventory", 
            Localization.translate("ui", "grandexchange.defaults.section.inventory"), scrollY, contentWidth);
        if (sectionExpanded.get("inventory")) {
            scrollY = buildInventorySection(scrollArea, scrollY, contentWidth);
        }

        // Section 2: Pricing & Fees
        scrollY = buildSectionHeader(scrollArea, "pricing", 
            Localization.translate("ui", "grandexchange.defaults.section.pricing"), scrollY + 10, contentWidth);
        if (sectionExpanded.get("pricing")) {
            scrollY = buildPricingSection(scrollArea, scrollY, contentWidth);
        }

        // Section 3: Offer Duration
        scrollY = buildSectionHeader(scrollArea, "duration", 
            Localization.translate("ui", "grandexchange.defaults.section.duration"), scrollY + 10, contentWidth);
        if (sectionExpanded.get("duration")) {
            scrollY = buildDurationSection(scrollArea, scrollY, contentWidth);
        }

        // Section 4: Trading Features
        scrollY = buildSectionHeader(scrollArea, "trading", 
            Localization.translate("ui", "grandexchange.defaults.section.trading"), scrollY + 10, contentWidth);
        if (sectionExpanded.get("trading")) {
            scrollY = buildTradingSection(scrollArea, scrollY, contentWidth);
        }

        // Section 5: Collection & UI
        scrollY = buildSectionHeader(scrollArea, "collection", 
            Localization.translate("ui", "grandexchange.defaults.section.collection"), scrollY + 10, contentWidth);
        if (sectionExpanded.get("collection")) {
            scrollY = buildCollectionSection(scrollArea, scrollY, contentWidth);
        }

        // Section 6: Advanced
        scrollY = buildSectionHeader(scrollArea, "advanced", 
            Localization.translate("ui", "grandexchange.defaults.section.advanced"), scrollY + 10, contentWidth);
        if (sectionExpanded.get("advanced")) {
            scrollY = buildAdvancedSection(scrollArea, scrollY, contentWidth);
        }

        // Bottom padding
        scrollY += 20;
        
        // Tell the scroll area how big the content is so scrolling works
        scrollArea.fitContentBoxToComponents(10);
    }

    // ===== SECTION HEADER BUILDER =====

    private int buildSectionHeader(FormContentBox area, String sectionKey, String title, int y, int width) {
        boolean expanded = sectionExpanded.getOrDefault(sectionKey, false);
        String icon = expanded ? "[-]" : "[+]";
        String tooltip = Localization.translate("ui", 
            expanded ? "grandexchange.defaults.section.collapse" : "grandexchange.defaults.section.expand");

        FormTextButton headerButton = new FormTextButton(
            icon + " " + title,
            tooltip,
            5, y, width - 10,
            FormInputSize.SIZE_24,
            ButtonColor.BASE
        );
        headerButton.onClicked(e -> {
            sectionExpanded.put(sectionKey, !expanded);
            rebuildScrollContent();
        });
        area.addComponent(headerButton);
        
        return y + 30;
    }

    private void rebuildScrollContent() {
        // Clear and rebuild the scroll area
        scrollArea.clearComponents();
        buildScrollContent();
    }

    // ===== SECTION 1: INVENTORY & SLOTS =====

    private int buildInventorySection(FormContentBox area, int startY, int width) {
        DefaultsTabState state = viewModel.getDefaultsTabState();
        int y = startY;

        // Sell Slots
        y = buildIntSetting(area, y, "Sell Slots per Player:", 
            ModConfig.GrandExchange.geInventorySlots, 5, 20,
            value -> {
                ModConfig.GrandExchange.setGeInventorySlots(value);
                container.updateSellSlotConfig.runAndSend(value);
            });

        // Buy Order Slots
        y = buildIntSetting(area, y, "Buy Order Slots per Player:", 
            ModConfig.GrandExchange.buyOrderSlots, 1, 10,
            value -> {
                ModConfig.GrandExchange.setBuyOrderSlots(value);
                container.updateBuySlotConfig.runAndSend(value);
            });

        // Max Active Offers
        y = buildIntSetting(area, y, "Max Active Offers per Player:", 
            ModConfig.GrandExchange.maxActiveOffersPerPlayer, 1, 50,
            value -> ModConfig.GrandExchange.setMaxActiveOffersPerPlayer(value));

        return y + 5;
    }

    // ===== SECTION 2: PRICING & FEES =====

    private int buildPricingSection(FormContentBox area, int startY, int width) {
        int y = startY;

        // Min Price
        y = buildIntSetting(area, y, "Minimum Price per Item:", 
            ModConfig.GrandExchange.minPricePerItem, 0, 100,
            value -> ModConfig.GrandExchange.setMinPricePerItem(value));

        // Max Price
        y = buildIntSetting(area, y, "Maximum Price per Item:", 
            ModConfig.GrandExchange.maxPricePerItem, 100, Integer.MAX_VALUE,
            value -> ModConfig.GrandExchange.setMaxPricePerItem(value));

        // Sales Tax
        y = buildPercentSetting(area, y, "Sales Tax (%):", 
            ModConfig.GrandExchange.salesTaxPercent, 0f, 0.25f,
            value -> ModConfig.GrandExchange.setSalesTaxPercent(value));

        // Listing Fee
        y = buildPercentSetting(area, y, "Listing Fee (%):", 
            ModConfig.GrandExchange.listingFeePercent, 0f, 0.25f,
            value -> ModConfig.GrandExchange.setListingFeePercent(value));

        // Profit Recipient (special dropdown)
        y = buildProfitRecipientSetting(area, y, width);

        return y + 5;
    }

    private int buildProfitRecipientSetting(FormContentBox area, int y, int width) {
        FormLabel label = new FormLabel("Profits go to:", GrandExchangeFonts.BODY, -1, 15, y);
        label.setColor(SETTING_LABEL_COLOR);
        area.addComponent(label);

        // Create dropdown for player selection
        FormDropdownSelectionButton<Long> dropdown = new FormDropdownSelectionButton<>(
            300, y - 5,
            FormInputSize.SIZE_24,
            ButtonColor.BASE,
            200,
            new StaticMessage("Select recipient")
        );

        // Add "World Owner" as default option
        dropdown.options.add(-1L, new StaticMessage("World Owner (default)"));

        // Populate with known players
        refreshPlayerList();
        for (PlayerDropdownEntry player : playerList) {
            String displayName = player.characterName + (player.isOnline ? " (online)" : "");
            dropdown.options.add(player.steamAuth, new StaticMessage(displayName));
        }

        // Set current selection
        long currentAuth = ModConfig.GrandExchange.profitRecipientAuth;
        String currentName = ModConfig.GrandExchange.profitRecipientName;
        if (currentAuth == -1L) {
            dropdown.setSelected(-1L, new StaticMessage("World Owner (default)"));
        } else {
            dropdown.setSelected(currentAuth, new StaticMessage(currentName.isEmpty() ? "Player #" + currentAuth : currentName));
        }

        dropdown.onSelected(event -> {
            if (event.value != null) {
                String name = "";
                if (event.value != -1L) {
                    // Find player name
                    for (PlayerDropdownEntry p : playerList) {
                        if (p.steamAuth == event.value) {
                            name = p.characterName;
                            break;
                        }
                    }
                }
                ModConfig.GrandExchange.setProfitRecipient(event.value, name);
                feedbackChannel.accept("Profit recipient updated - changes pending save", false);
            }
        });

        area.addComponent(dropdown);

        FormLabel hint = new FormLabel("(taxes and fees collected)", GrandExchangeFonts.SMALL, -1, 510, y);
        hint.setColor(HINT_COLOR);
        area.addComponent(hint);

        return y + 35;
    }

    private void refreshPlayerList() {
        playerList.clear();

        // Access the Client through GlobalData/MainGame pattern
        try {
            if (necesse.engine.GlobalData.getCurrentState() instanceof necesse.engine.state.MainGame) {
                necesse.engine.state.MainGame mainGame = 
                    (necesse.engine.state.MainGame) necesse.engine.GlobalData.getCurrentState();
                Client client = mainGame.getClient();
                
                if (client != null && client.streamClients() != null) {
                    client.streamClients()
                        .filter(c -> c != null && c.getName() != null)
                        .forEach(c -> {
                            playerList.add(new PlayerDropdownEntry(
                                c.getName(),
                                c.authentication,
                                true, // online
                                System.currentTimeMillis()
                            ));
                        });
                }
            }
        } catch (Exception e) {
            ModLogger.warn("DefaultsTabView", "Could not refresh player list: " + e.getMessage());
        }

        // Sort: online first, then alphabetically
        playerList.sort(null);
    }    // ===== SECTION 3: OFFER DURATION =====

    private int buildDurationSection(FormContentBox area, int startY, int width) {
        int y = startY;

        // Enable Expiration
        y = buildBoolSetting(area, y, "Enable Offer Expiration:", 
            ModConfig.GrandExchange.enableOfferExpiration,
            value -> { ModConfig.GrandExchange.enableOfferExpiration = value; });

        // Default Duration Hours
        y = buildIntSetting(area, y, "Default Duration (hours):", 
            ModConfig.GrandExchange.offerExpirationHours, 0, 720,
            value -> ModConfig.GrandExchange.setOfferExpirationHours(value));

        // Min Duration Days
        y = buildIntSetting(area, y, "Min Duration (days):", 
            ModConfig.GrandExchange.minSellDurationDays, 1, 30,
            value -> ModConfig.GrandExchange.setMinSellDurationDays(value));

        // Max Duration Days
        y = buildIntSetting(area, y, "Max Duration (days):", 
            ModConfig.GrandExchange.maxSellDurationDays, 1, 60,
            value -> ModConfig.GrandExchange.setMaxSellDurationDays(value));

        // Disable Cooldown
        y = buildIntSetting(area, y, "Disable Cooldown (seconds):", 
            ModConfig.GrandExchange.sellDisableCooldownSeconds, 0, 60,
            value -> ModConfig.GrandExchange.setSellDisableCooldownSeconds(value));

        // Return Expired to Bank
        y = buildBoolSetting(area, y, "Return Expired Items to Bank:", 
            ModConfig.GrandExchange.returnExpiredToBank,
            value -> { ModConfig.GrandExchange.returnExpiredToBank = value; });

        return y + 5;
    }

    // ===== SECTION 4: TRADING FEATURES =====

    private int buildTradingSection(FormContentBox area, int startY, int width) {
        int y = startY;

        // Enable Instant Trades
        y = buildBoolSetting(area, y, "Enable Instant Trade Matching:", 
            ModConfig.GrandExchange.enableInstantTrades,
            value -> { ModConfig.GrandExchange.enableInstantTrades = value; });

        // Enable Buy Offers
        y = buildBoolSetting(area, y, "Enable Buy Offers (Phase 10):", 
            ModConfig.GrandExchange.enableBuyOffers,
            value -> { ModConfig.GrandExchange.enableBuyOffers = value; });

        // Allow Purchase to Bank
        y = buildBoolSetting(area, y, "Allow Purchase Directly to Bank:", 
            ModConfig.GrandExchange.allowPurchaseToBank,
            value -> { ModConfig.GrandExchange.allowPurchaseToBank = value; });

        // Sale Proceeds to Bank
        y = buildBoolSetting(area, y, "Send Sale Proceeds to Seller's Bank:", 
            ModConfig.GrandExchange.saleProceedsToBank,
            value -> { ModConfig.GrandExchange.saleProceedsToBank = value; });

        return y + 5;
    }

    // ===== SECTION 5: COLLECTION & UI =====

    private int buildCollectionSection(FormContentBox area, int startY, int width) {
        DefaultsTabState state = viewModel.getDefaultsTabState();
        int y = startY;

        // Auto-Clear Staging Slot
        y = buildBoolSetting(area, y, "Auto-Clear Sell Staging on Post:", 
            ModConfig.GrandExchange.autoClearSellStagingSlot,
            value -> {
                ModConfig.GrandExchange.setAutoClearSellStagingSlot(value);
                if (container.updateAutoClearPreference != null) {
                    container.updateAutoClearPreference.runAndSend(value);
                }
            });

        // Default Collection Deposit to Bank
        y = buildBoolSetting(area, y, "Default: Deposit Collection to Bank:", 
            ModConfig.GrandExchange.defaultCollectionDepositToBank,
            value -> ModConfig.GrandExchange.setDefaultCollectionDepositToBank(value));

        // Collection Page Size
        y = buildIntSetting(area, y, "Collection Items per Page:", 
            ModConfig.GrandExchange.collectionPageSize, 5, 40,
            value -> ModConfig.GrandExchange.setCollectionPageSize(value));

        // Auto Refresh Seconds
        y = buildIntSetting(area, y, "Auto-Refresh Interval (seconds):", 
            ModConfig.GrandExchange.autoRefreshSeconds, 0, 300,
            value -> ModConfig.GrandExchange.setAutoRefreshSeconds(value));

        // Show Offer Status Badges
        y = buildBoolSetting(area, y, "Show Offer Status Badges:", 
            ModConfig.GrandExchange.showOfferStatusBadges,
            value -> { ModConfig.GrandExchange.showOfferStatusBadges = value; });

        // Max Listings Per Page
        y = buildIntSetting(area, y, "Max Listings per Page:", 
            ModConfig.GrandExchange.maxListingsPerPage, 10, 500,
            value -> ModConfig.GrandExchange.setMaxListingsPerPage(value));

        return y + 5;
    }

    // ===== SECTION 6: ADVANCED SETTINGS =====

    private int buildAdvancedSection(FormContentBox area, int startY, int width) {
        int y = startY;

        // Warning label
        FormLabel warning = new FormLabel("These settings affect performance and security", 
            GrandExchangeFonts.SMALL, -1, 15, y);
        warning.setColor(Color.BLACK);
        area.addComponent(warning);
        y += 25;

        // Offer Creation Cooldown
        y = buildIntSetting(area, y, "Offer Creation Cooldown (ms):", 
            ModConfig.GrandExchange.offerCreationCooldown, 0, 60000,
            value -> ModConfig.GrandExchange.setOfferCreationCooldown(value));

        // UI Heartbeat Interval
        y = buildIntSetting(area, y, "UI Heartbeat Interval (ms):", 
            ModConfig.GrandExchange.uiHeartbeatIntervalMs, 100, 1000,
            value -> ModConfig.GrandExchange.setUiHeartbeatIntervalMs(value));

        // Price History Size
        y = buildIntSetting(area, y, "Price History Size:", 
            ModConfig.GrandExchange.priceHistorySize, 10, 200,
            value -> ModConfig.GrandExchange.setPriceHistorySize(value));

        // Enable Price Tracking
        y = buildBoolSetting(area, y, "Enable Price Tracking:", 
            ModConfig.GrandExchange.enablePriceTracking,
            value -> { ModConfig.GrandExchange.enablePriceTracking = value; });

        // Enable Statistics
        y = buildBoolSetting(area, y, "Enable Statistics:", 
            ModConfig.GrandExchange.enableStatistics,
            value -> { ModConfig.GrandExchange.enableStatistics = value; });

        // Audit Log Size
        y = buildIntSetting(area, y, "Audit Log Size:", 
            ModConfig.GrandExchange.auditLogSize, 100, 10000,
            value -> ModConfig.GrandExchange.setAuditLogSize(value));

        // Enable Fraud Detection
        y = buildBoolSetting(area, y, "Enable Fraud Detection:", 
            ModConfig.GrandExchange.enableFraudDetection,
            value -> { ModConfig.GrandExchange.enableFraudDetection = value; });

        // Price Outlier Threshold
        y = buildFloatSetting(area, y, "Price Outlier Threshold:", 
            ModConfig.GrandExchange.priceOutlierThreshold, 1.0f, 5.0f,
            value -> ModConfig.GrandExchange.setPriceOutlierThreshold(value));

        // Enable Performance Metrics
        y = buildBoolSetting(area, y, "Enable Performance Metrics:", 
            ModConfig.GrandExchange.enablePerformanceMetrics,
            value -> { ModConfig.GrandExchange.enablePerformanceMetrics = value; });

        // Metrics Window Seconds
        y = buildIntSetting(area, y, "Metrics Window (seconds):", 
            ModConfig.GrandExchange.metricsWindowSeconds, 10, 300,
            value -> ModConfig.GrandExchange.setMetricsWindowSeconds(value));

        // Diagnostics History Size
        y = buildIntSetting(area, y, "Diagnostics History Size:", 
            ModConfig.GrandExchange.diagnosticsHistorySize, 5, 100,
            value -> ModConfig.GrandExchange.setDiagnosticsHistorySize(value));

        return y + 5;
    }

    // ===== WIDGET BUILDERS =====

    private int buildIntSetting(FormContentBox area, int y, String label, int currentValue, 
                                int min, int max, Consumer<Integer> onApply) {
        FormLabel settingLabel = new FormLabel(label, GrandExchangeFonts.BODY, -1, 15, y);
        settingLabel.setColor(SETTING_LABEL_COLOR);
        area.addComponent(settingLabel);

        FormTextInput input = new FormTextInput(300, y - 5, FormInputSize.SIZE_24, 80, 10);
        input.setText(String.valueOf(currentValue));
        area.addComponent(input);

        FormTextButton applyBtn = new FormTextButton("Apply", "", 390, y - 5, 60, 
            FormInputSize.SIZE_24, ButtonColor.BASE);
        applyBtn.onClicked(e -> {
            try {
                int value = Integer.parseInt(input.getText().trim());
                if (value < min || value > max) {
                    feedbackChannel.accept("Value must be between " + min + " and " + max, true);
                    return;
                }
                onApply.accept(value);
                feedbackChannel.accept("Setting updated", false);
            } catch (NumberFormatException ex) {
                feedbackChannel.accept("Please enter a valid number", true);
            }
        });
        area.addComponent(applyBtn);

        FormLabel range = new FormLabel("(" + min + "-" + max + ")", GrandExchangeFonts.SMALL, -1, 460, y);
        range.setColor(HINT_COLOR);
        area.addComponent(range);

        return y + 32;
    }

    private int buildFloatSetting(FormContentBox area, int y, String label, float currentValue, 
                                  float min, float max, Consumer<Float> onApply) {
        FormLabel settingLabel = new FormLabel(label, GrandExchangeFonts.BODY, -1, 15, y);
        settingLabel.setColor(SETTING_LABEL_COLOR);
        area.addComponent(settingLabel);

        FormTextInput input = new FormTextInput(300, y - 5, FormInputSize.SIZE_24, 80, 10);
        input.setText(String.format("%.2f", currentValue));
        area.addComponent(input);

        FormTextButton applyBtn = new FormTextButton("Apply", "", 390, y - 5, 60, 
            FormInputSize.SIZE_24, ButtonColor.BASE);
        applyBtn.onClicked(e -> {
            try {
                float value = Float.parseFloat(input.getText().trim());
                if (value < min || value > max) {
                    feedbackChannel.accept("Value must be between " + min + " and " + max, true);
                    return;
                }
                onApply.accept(value);
                feedbackChannel.accept("Setting updated", false);
            } catch (NumberFormatException ex) {
                feedbackChannel.accept("Please enter a valid number", true);
            }
        });
        area.addComponent(applyBtn);

        FormLabel range = new FormLabel("(" + min + "-" + max + ")", GrandExchangeFonts.SMALL, -1, 460, y);
        range.setColor(HINT_COLOR);
        area.addComponent(range);

        return y + 32;
    }

    private int buildPercentSetting(FormContentBox area, int y, String label, float currentValue, 
                                    float min, float max, Consumer<Float> onApply) {
        FormLabel settingLabel = new FormLabel(label, GrandExchangeFonts.BODY, -1, 15, y);
        settingLabel.setColor(SETTING_LABEL_COLOR);
        area.addComponent(settingLabel);

        // Display as percentage (0.05 -> 5)
        FormTextInput input = new FormTextInput(300, y - 5, FormInputSize.SIZE_24, 80, 10);
        input.setText(String.format("%.1f", currentValue * 100));
        area.addComponent(input);

        FormTextButton applyBtn = new FormTextButton("Apply", "", 390, y - 5, 60, 
            FormInputSize.SIZE_24, ButtonColor.BASE);
        applyBtn.onClicked(e -> {
            try {
                float percent = Float.parseFloat(input.getText().trim());
                float value = percent / 100f; // Convert back to decimal
                if (value < min || value > max) {
                    feedbackChannel.accept("Value must be between " + (min * 100) + "% and " + (max * 100) + "%", true);
                    return;
                }
                onApply.accept(value);
                feedbackChannel.accept("Setting updated", false);
            } catch (NumberFormatException ex) {
                feedbackChannel.accept("Please enter a valid percentage", true);
            }
        });
        area.addComponent(applyBtn);

        FormLabel range = new FormLabel("(0-25%)", GrandExchangeFonts.SMALL, -1, 460, y);
        range.setColor(HINT_COLOR);
        area.addComponent(range);

        return y + 32;
    }

    private int buildBoolSetting(FormContentBox area, int y, String label, boolean currentValue, 
                                 Consumer<Boolean> onApply) {
        FormLabel settingLabel = new FormLabel(label, GrandExchangeFonts.BODY, -1, 15, y);
        settingLabel.setColor(SETTING_LABEL_COLOR);
        area.addComponent(settingLabel);

        TooltipFormCheckBox checkbox = new TooltipFormCheckBox("", 300, y - 2, currentValue);
        checkbox.onClicked(e -> {
            onApply.accept(checkbox.checked);
            feedbackChannel.accept("Setting updated", false);
        });
        area.addComponent(checkbox);

        return y + 28;
    }
}
