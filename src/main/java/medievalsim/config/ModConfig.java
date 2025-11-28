package medievalsim.config;

import medievalsim.util.ModLogger;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;

/**
 * Unified configuration system for Medieval Sim mod.
 * 
 * This replaces the scattered Constants/RuntimeConstants/Settings pattern with
 * a clean, annotated configuration system that provides:
 * 
 * - Compile-time defaults
 * - Runtime validation
 * - Automatic persistence
 * - Type safety
 * - Clear documentation
 * 
 * USAGE:
 * ```java
 * // Get current value
 * int maxBlocks = ModConfig.BuildMode.maxBlocksPerPlacement;
 * 
 * // Set with validation
 * ModConfig.BuildMode.setMaxBlocksPerPlacement(500);
 * 
 * // Save/load automatically handled
 * ModConfig.save(saveData);
 * ModConfig.load(loadData);
 * ```
 */
public class ModConfig {
    
    // ===== ANNOTATIONS FOR CONFIGURATION =====
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface ConfigValue {
        /** Default value (for documentation) */
        String defaultValue() default "";
        /** Human-readable description */
        String description() default "";
        /** Minimum allowed value (for numbers) */
        double min() default Double.NEGATIVE_INFINITY;
        /** Maximum allowed value (for numbers) */
        double max() default Double.POSITIVE_INFINITY;
        /** Whether this value can be changed at runtime */
        boolean runtime() default true;
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface ConfigSection {
        /** Section name for save/load */
        String value();
        /** Section description */
        String description() default "";
    }
    
    // ===== BUILD MODE CONFIGURATION =====
    
    @ConfigSection(value = "BUILD_MODE", description = "Advanced construction tools configuration")
    public static class BuildMode {
        
        @ConfigValue(
            defaultValue = "500",
            description = "Maximum number of blocks that can be placed at once (anti-abuse limit)",
            min = 1, max = 1000
        )
        public static int maxBlocksPerPlacement = 500;
        
        @ConfigValue(
            defaultValue = "5",
            description = "Default line length for line shapes",
            min = 1, max = 50
        )
        public static int defaultLineLength = 5;
        
        @ConfigValue(
            defaultValue = "5", 
            description = "Default square size",
            min = 1, max = 25
        )
        public static int defaultSquareSize = 5;
        
        @ConfigValue(
            defaultValue = "5",
            description = "Default circle radius", 
            min = 1, max = 25
        )
        public static int defaultCircleRadius = 5;
        
        @ConfigValue(
            defaultValue = "1",
            description = "Default spacing between placements",
            min = 1, max = 10
        )
        public static int defaultSpacing = 1;
        
        // UI Preferences
        @ConfigValue(defaultValue = "false", description = "Remember build mode state between sessions")
        public static boolean rememberBuildModeState = false;
        
        // State persistence (runtime only)
        @ConfigValue(defaultValue = "0", description = "Last selected shape", runtime = true)
        public static int savedShape = 0;

        @ConfigValue(defaultValue = "false", description = "Last hollow state", runtime = true)
        public static boolean savedIsHollow = false;

        @ConfigValue(defaultValue = "5", description = "Last line length", runtime = true)
        public static int savedLineLength = 5;

        @ConfigValue(defaultValue = "5", description = "Last square size", runtime = true)
        public static int savedSquareSize = 5;

        @ConfigValue(defaultValue = "5", description = "Last circle radius", runtime = true)
        public static int savedCircleRadius = 5;

        @ConfigValue(defaultValue = "1", description = "Last spacing", runtime = true)
        public static int savedSpacing = 1;

        @ConfigValue(defaultValue = "0", description = "Last direction", runtime = true)
        public static int savedDirection = 0;

        // Setters with validation
        public static void setMaxBlocksPerPlacement(int value) {
            maxBlocksPerPlacement = validateInt(value, 1, 1000, "maxBlocksPerPlacement");
        }
        
        public static void setDefaultLineLength(int value) {
            defaultLineLength = validateInt(value, 1, 50, "defaultLineLength");
        }
        
        public static void setDefaultSquareSize(int value) {
            defaultSquareSize = validateInt(value, 1, 25, "defaultSquareSize");
        }
        
        public static void setDefaultCircleRadius(int value) {
            defaultCircleRadius = validateInt(value, 1, 25, "defaultCircleRadius");
        }
        
        public static void setDefaultSpacing(int value) {
            defaultSpacing = validateInt(value, 1, 10, "defaultSpacing");
        }
    }
    
    // ===== ZONE CONFIGURATION =====
    
    @ConfigSection(value = "ZONES", description = "Protected and PvP zone configuration")
    public static class Zones {
        
        @ConfigValue(
            defaultValue = "30000",
            description = "Re-entry cooldown (ms) after leaving a PvP zone",
            min = 0, max = 300000
        )
        public static long pvpReentryCooldownMs = 30000L;
        
        @ConfigValue(
            defaultValue = "10.0",
            description = "Seconds of immunity when entering/spawning in PvP zone",
            min = 0.0, max = 60.0
        )
        public static float pvpSpawnImmunitySeconds = 10.0f;
        
        @ConfigValue(
            defaultValue = "0.05",
            description = "Default PvP damage multiplier (0.05 = 5%)",
            min = 0.0, max = 1.0
        )
        public static float defaultDamageMultiplier = 0.05f;
        
        @ConfigValue(
            defaultValue = "1000",
            description = "Maximum barrier tiles to place before skipping",
            min = 10, max = 10000
        )
        public static int maxBarrierTiles = 1000;
        
        @ConfigValue(
            defaultValue = "50",
            description = "Batch size for barrier placement to avoid lag",
            min = 1, max = 200
        )
        public static int barrierAddBatchSize = 50;
        
        @ConfigValue(
            defaultValue = "10",
            description = "Max barrier tiles processed per server tick",
            min = 1, max = 100
        )
        public static int barrierMaxTilesPerTick = 10;
        
        @ConfigValue(
            defaultValue = "3",
            description = "Default combat lock duration (seconds)",
            min = 0, max = 10
        )
        public static int defaultCombatLockSeconds = 3;
        
        @ConfigValue(
            defaultValue = "100",
            description = "Default force-clean radius for PvP zones",
            min = 10, max = 500
        )
        public static int defaultForceCleanRadius = 100;

        @ConfigValue(
            defaultValue = "500",
            description = "Soft limit for protected zones (warning threshold)",
            min = 10, max = 5000
        )
        public static int protectedZoneSoftLimit = 500;

        @ConfigValue(
            defaultValue = "500",
            description = "Soft limit for PvP zones (warning threshold)",
            min = 10, max = 5000
        )
        public static int pvpZoneSoftLimit = 500;

        @ConfigValue(
            defaultValue = "1000",
            description = "Critical limit for protected zones (error threshold)",
            min = 100, max = 10000
        )
        public static int protectedZoneCriticalLimit = 1000;

        @ConfigValue(
            defaultValue = "1000",
            description = "Critical limit for PvP zones (error threshold)",
            min = 100, max = 10000
        )
        public static int pvpZoneCriticalLimit = 1000;

        // Setters with validation
        public static void setPvpReentryCooldownMs(long value) {
            pvpReentryCooldownMs = validateLong(value, 0L, 300000L, "pvpReentryCooldownMs");
        }
        
        public static void setPvpSpawnImmunitySeconds(float value) {
            pvpSpawnImmunitySeconds = validateFloat(value, 0.0f, 60.0f, "pvpSpawnImmunitySeconds");
        }
        
        public static void setDefaultDamageMultiplier(float value) {
            defaultDamageMultiplier = validateFloat(value, 0.0f, 1.0f, "defaultDamageMultiplier");
        }
        
        public static void setMaxBarrierTiles(int value) {
            maxBarrierTiles = validateInt(value, 10, 10000, "maxBarrierTiles");
        }
        
        public static void setBarrierAddBatchSize(int value) {
            barrierAddBatchSize = validateInt(value, 1, 200, "barrierAddBatchSize");
        }
        
        public static void setBarrierMaxTilesPerTick(int value) {
            barrierMaxTilesPerTick = validateInt(value, 1, 100, "barrierMaxTilesPerTick");
        }
        
        public static void setDefaultCombatLockSeconds(int value) {
            defaultCombatLockSeconds = validateInt(value, 0, 10, "defaultCombatLockSeconds");
        }

        public static void setProtectedZoneSoftLimit(int value) {
            protectedZoneSoftLimit = validateInt(value, 10, 5000, "protectedZoneSoftLimit");
        }

        public static void setPvpZoneSoftLimit(int value) {
            pvpZoneSoftLimit = validateInt(value, 10, 5000, "pvpZoneSoftLimit");
        }

        public static void setProtectedZoneCriticalLimit(int value) {
            protectedZoneCriticalLimit = validateInt(value, 100, 10000, "protectedZoneCriticalLimit");
        }

        public static void setPvpZoneCriticalLimit(int value) {
            pvpZoneCriticalLimit = validateInt(value, 100, 10000, "pvpZoneCriticalLimit");
        }
    }
    
    // ===== PLOT FLAGS CONFIGURATION =====
    
    @ConfigSection(value = "PLOT_FLAGS", description = "Purchasable plot flags that convert to player settlements")
    public static class PlotFlags {
        
        @ConfigValue(
            defaultValue = "false",
            description = "Enable plot flag system (when enabled, normal settlement flags cannot be purchased/placed)"
        )
        public static boolean enabled = false;
        
        @ConfigValue(
            defaultValue = "1000",
            description = "Coin cost to purchase plot flags",
            min = 1, max = 1000000
        )
        public static int coinCost = 1000;
        
        // Setters with validation
        public static void setEnabled(boolean value) {
            enabled = value;
        }
        
        public static void setCoinCost(int value) {
            coinCost = validateInt(value, 1, 1000000, "coinCost");
        }
    }
    
    // ===== SETTLEMENT SPACING CONFIGURATION =====
    
    @ConfigSection(value = "SETTLEMENTS", description = "Settlement protection and configuration")
    public static class Settlements {

        @ConfigValue(
            defaultValue = "false",
            description = "Enable zone protection for settlements (protects settlement area from non-members)"
        )
        public static boolean protectionEnabled = false;

        // Setter with validation
        public static void setProtectionEnabled(boolean value) {
            protectionEnabled = value;
        }
    }

    @ConfigSection(value = "SETTLEMENT_SPACING", description = "Minimum distance between settlements")
    public static class SettlementSpacing {

        @ConfigValue(
            defaultValue = "0",
            description = "Minimum settlement tier for spacing calculation (0 = vanilla spacing, 1-6 = enforce tier spacing). Tier sizes: 0=40 tiles, 1=56 tiles, 2=72 tiles, 3=88 tiles, 4=104 tiles, 5=120 tiles, 6=136 tiles",
            min = 0, max = 6
        )
        public static int minimumTier = 0;

        @ConfigValue(
            defaultValue = "0",
            description = "Additional region padding beyond minimum tier (1 region = 8 tiles)",
            min = 0, max = 10
        )
        public static int customPadding = 0;

        // Setters with validation
        public static void setMinimumTier(int value) {
            minimumTier = validateInt(value, 0, 6, "minimumTier");
        }

        public static void setCustomPadding(int value) {
            customPadding = validateInt(value, 0, 10, "customPadding");
        }
    }
    
    // ===== COMMAND CENTER CONFIGURATION =====
    
    @ConfigSection(value = "ADMIN_HUD", description = "Admin Tools HUD and Command Center UI configuration")
    public static class CommandCenter {

        @ConfigValue(defaultValue = "600", description = "Default Admin HUD width (applies to all admin tools screens)", min = 400, max = 1200)
        public static int defaultWidth = 600;

        @ConfigValue(defaultValue = "500", description = "Default Admin HUD height (applies to all admin tools screens)", min = 300, max = 800)
        public static int defaultHeight = 500;

        @ConfigValue(defaultValue = "10", description = "Maximum favorite commands", min = 1, max = 50)
        public static int maxFavorites = 10;

        @ConfigValue(defaultValue = "20", description = "Maximum history entries", min = 5, max = 100)
        public static int maxHistory = 20;

        @ConfigValue(defaultValue = "240", description = "Preferred width of Admin HUD main menu buttons", min = 160, max = 600)
        public static int mainMenuButtonWidth = 240;

        // Minimized HUD bar dimensions
        @ConfigValue(defaultValue = "160", description = "Width of minimized Admin HUD bar", min = 120, max = 400)
        public static int minimizedWidth = 160;

        @ConfigValue(defaultValue = "30", description = "Height of minimized Admin HUD bar", min = 20, max = 60)
        public static int minimizedHeight = 30;

        // HUD size state (runtime - last used size, per player)
        @ConfigValue(defaultValue = "600", description = "Last used Admin HUD width (per player)", runtime = true)
        public static int currentWidth = 600;

        @ConfigValue(defaultValue = "500", description = "Last used Admin HUD height (per player)", runtime = true)
        public static int currentHeight = 500;
    }

    // ===== LOGGING CONFIGURATION =====
    @ConfigSection(value = "LOGGING", description = "Logging configuration for Medieval Sim")
    public static class Logging {
        @ConfigValue(defaultValue = "false", description = "Enable verbose debug logging for placement/spacing checks (development only)", runtime = true)
        public static boolean verboseDebug = false;

        public static void setVerboseDebug(boolean value) {
            verboseDebug = value;
        }
    }

    // ===== BANKING CONFIGURATION =====
    @ConfigSection(value = "BANKING", description = "Player banking system configuration")
    public static class Banking {

        @ConfigValue(
            defaultValue = "true",
            description = "Enable banking system (adds Bank option to Elder NPC)"
        )
        public static boolean enabled = true;

        @ConfigValue(
            defaultValue = "20",
            description = "Base number of bank slots (before upgrades)",
            min = 10, max = 100
        )
        public static int baseSlots = 20;

        @ConfigValue(
            defaultValue = "5",
            description = "Number of slots added per upgrade",
            min = 1, max = 20
        )
        public static int slotsPerUpgrade = 5;

        @ConfigValue(
            defaultValue = "1000",
            description = "Coin cost per upgrade level (upgrade 1 = 1000, upgrade 2 = 2000, etc.)",
            min = 100, max = 10000
        )
        public static int coinCostPerUpgrade = 1000;

        @ConfigValue(
            defaultValue = "20",
            description = "Maximum number of upgrades allowed",
            min = 1, max = 50
        )
        public static int maxUpgrades = 20;

        @ConfigValue(
            defaultValue = "true",
            description = "Require PIN to access bank"
        )
        public static boolean requirePIN = true;

        @ConfigValue(
            defaultValue = "4",
            description = "PIN length (number of digits)",
            min = 4, max = 8
        )
        public static int pinLength = 4;

        @ConfigValue(
            defaultValue = "true",
            description = "Allow sending items to bank from containers"
        )
        public static boolean allowRemoteDeposit = true;

        @ConfigValue(
            defaultValue = "true",
            description = "Send settlement purchase coins to world owner's bank"
        )
        public static boolean settlementCoinsToBank = true;

        // Setters with validation
        public static void setBaseSlots(int value) {
            baseSlots = validateInt(value, 10, 100, "baseSlots");
        }

        public static void setSlotsPerUpgrade(int value) {
            slotsPerUpgrade = validateInt(value, 1, 20, "slotsPerUpgrade");
        }

        public static void setCoinCostPerUpgrade(int value) {
            coinCostPerUpgrade = validateInt(value, 100, 10000, "coinCostPerUpgrade");
        }

        public static void setMaxUpgrades(int value) {
            maxUpgrades = validateInt(value, 1, 50, "maxUpgrades");
        }

        public static void setPinLength(int value) {
            pinLength = validateInt(value, 4, 8, "pinLength");
        }

        /** Calculate total slots for a given upgrade level */
        public static int getTotalSlots(int upgradeLevel) {
            return baseSlots + (upgradeLevel * slotsPerUpgrade);
        }

        /** Calculate coin cost for a specific upgrade level */
        public static int getUpgradeCost(int upgradeLevel) {
            return upgradeLevel * coinCostPerUpgrade;
        }
    }

    // ===== GRAND EXCHANGE CONFIGURATION =====
    @ConfigSection(value = "GRAND_EXCHANGE", description = "Player marketplace and trading system configuration (RuneScape-style)")
    public static class GrandExchange {

        // === CORE SYSTEM ===
        
        @ConfigValue(
            defaultValue = "true",
            description = "Enable Grand Exchange system (adds GE option to Trader NPC)"
        )
        public static boolean enabled = true;

        // === INVENTORY SETTINGS ===
        
        @ConfigValue(
            defaultValue = "10",
            description = "Number of GE sell slots per player (Sell tab)",
            min = 5, max = 20
        )
        public static int geInventorySlots = 10;
        
        @ConfigValue(
            defaultValue = "3",
            description = "Number of buy order slots per player (Buy Orders tab)",
            min = 1, max = 10
        )
        public static int buyOrderSlots = 3;

        @ConfigValue(
            defaultValue = "10",
            description = "Maximum active sell offers per player (total across all slots)",
            min = 1, max = 50
        )
        public static int maxActiveOffersPerPlayer = 10;
        
        /** @deprecated Use maxActiveOffersPerPlayer instead. Compatibility alias for Phase 6. */
        @Deprecated
        public static int maxListingsPerPlayer = maxActiveOffersPerPlayer;

        // === PRICING & FEES ===
        
        @ConfigValue(
            defaultValue = "1",
            description = "Minimum price per item (anti-abuse)",
            min = 0, max = 100
        )
        public static int minPricePerItem = 1;

        @ConfigValue(
            defaultValue = "1000000",
            description = "Maximum price per item (anti-abuse)",
            min = 100, max = 2147483647
        )
        public static int maxPricePerItem = 1000000;

        @ConfigValue(
            defaultValue = "0.05",
            description = "Sales tax percentage (0.05 = 5% tax on completed sales)",
            min = 0.0, max = 0.25
        )
        public static float salesTaxPercent = 0.05f;

        @ConfigValue(
            defaultValue = "0.0",
            description = "Listing fee percentage (0.02 = 2% upfront fee to create offer)",
            min = 0.0, max = 0.25
        )
        public static float listingFeePercent = 0.0f;

        // === OFFER EXPIRATION ===
        
        @ConfigValue(
            defaultValue = "true",
            description = "Enable automatic offer expiration (offers cancel after duration)"
        )
        public static boolean enableOfferExpiration = true;

        @ConfigValue(
            defaultValue = "168",
            description = "Offer expiration duration in hours (168 = 1 week, 0 = never expires)",
            min = 0, max = 720
        )
        public static int offerExpirationHours = 168;

        @ConfigValue(
            defaultValue = "true",
            description = "Return expired items to seller's bank (false = return to inventory)"
        )
        public static boolean returnExpiredToBank = true;

        // === TRADING FEATURES ===
        
        @ConfigValue(
            defaultValue = "true",
            description = "Enable instant trade matching (auto-match buy/sell offers at compatible prices)"
        )
        public static boolean enableInstantTrades = true;

        @ConfigValue(
            defaultValue = "false",
            description = "Enable buy offers (Phase 10 feature: bid for items you want to buy)"
        )
        public static boolean enableBuyOffers = false;

        @ConfigValue(
            defaultValue = "true",
            description = "Allow purchasing items directly to bank (false = only to inventory)"
        )
        public static boolean allowPurchaseToBank = true;

        @ConfigValue(
            defaultValue = "true",
            description = "Send sale proceeds to seller's bank (false = to inventory)"
        )
        public static boolean saleProceedsToBank = true;

        // === PRICE HISTORY ===
        
        @ConfigValue(
            defaultValue = "50",
            description = "Number of recent sales to track for price history per item",
            min = 10, max = 200
        )
        public static int priceHistorySize = 50;

        @ConfigValue(
            defaultValue = "true",
            description = "Track average prices for market analytics"
        )
        public static boolean enablePriceTracking = true;

        // === UI & UX ===
        
        @ConfigValue(
            defaultValue = "30",
            description = "Auto-refresh interval for market data (seconds, 0 = manual only)",
            min = 0, max = 300
        )
        public static int autoRefreshSeconds = 30;

        @ConfigValue(
            defaultValue = "100",
            description = "Maximum listings to display per page in market browser",
            min = 10, max = 500
        )
        public static int maxListingsPerPage = 100;

        @ConfigValue(
            defaultValue = "true",
            description = "Show offer status badges on slot icons (SELLING/SOLD/PARTIAL)"
        )
        public static boolean showOfferStatusBadges = true;

        // === STATISTICS ===
        
        @ConfigValue(
            defaultValue = "true",
            description = "Track GE statistics (total trades, volume, etc.)"
        )
        public static boolean enableStatistics = true;

        // === RATE LIMITING ===
        
        @ConfigValue(
            defaultValue = "5000",
            description = "Cooldown in milliseconds between creating offers/orders (5000 = 5 seconds)",
            min = 0, max = 60000
        )
        public static int offerCreationCooldown = 5000;

        // === AUDIT & MONITORING ===
        
        @ConfigValue(
            defaultValue = "1000",
            description = "Number of trades to keep in audit log per category",
            min = 100, max = 10000
        )
        public static int auditLogSize = 1000;

        @ConfigValue(
            defaultValue = "true",
            description = "Enable fraud detection (detects self-trading and suspicious patterns)"
        )
        public static boolean enableFraudDetection = true;

        @ConfigValue(
            defaultValue = "2.0",
            description = "Price outlier threshold multiplier for fraud detection (2.0 = 2x standard deviation)",
            min = 1.0, max = 5.0
        )
        public static float priceOutlierThreshold = 2.0f;

        // === PERFORMANCE ===
        
        @ConfigValue(
            defaultValue = "60",
            description = "Performance metrics sliding window in seconds",
            min = 10, max = 300
        )
        public static int metricsWindowSeconds = 60;

        @ConfigValue(
            defaultValue = "true",
            description = "Enable performance monitoring (tracks trades/min, execution time, market health)"
        )
        public static boolean enablePerformanceMetrics = true;

        // ===== SETTERS WITH VALIDATION =====
        
        public static void setGeInventorySlots(int value) {
            geInventorySlots = validateInt(value, 5, 20, "geInventorySlots");
        }

        public static void setMaxActiveOffersPerPlayer(int value) {
            maxActiveOffersPerPlayer = validateInt(value, 1, 50, "maxActiveOffersPerPlayer");
        }

        public static void setMinPricePerItem(int value) {
            minPricePerItem = validateInt(value, 0, 100, "minPricePerItem");
        }

        public static void setMaxPricePerItem(int value) {
            maxPricePerItem = validateInt(value, 100, Integer.MAX_VALUE, "maxPricePerItem");
        }

        public static void setSalesTaxPercent(float value) {
            salesTaxPercent = validateFloat(value, 0.0f, 0.25f, "salesTaxPercent");
        }

        public static void setListingFeePercent(float value) {
            listingFeePercent = validateFloat(value, 0.0f, 0.25f, "listingFeePercent");
        }

        public static void setOfferExpirationHours(int value) {
            offerExpirationHours = validateInt(value, 0, 720, "offerExpirationHours");
        }

        public static void setPriceHistorySize(int value) {
            priceHistorySize = validateInt(value, 10, 200, "priceHistorySize");
        }

        public static void setAutoRefreshSeconds(int value) {
            autoRefreshSeconds = validateInt(value, 0, 300, "autoRefreshSeconds");
        }

        public static void setOfferCreationCooldown(int value) {
            offerCreationCooldown = validateInt(value, 0, 60000, "offerCreationCooldown");
        }

        public static void setAuditLogSize(int value) {
            auditLogSize = validateInt(value, 100, 10000, "auditLogSize");
        }

        public static void setPriceOutlierThreshold(float value) {
            priceOutlierThreshold = validateFloat(value, 1.0f, 5.0f, "priceOutlierThreshold");
        }

        public static void setMetricsWindowSeconds(int value) {
            metricsWindowSeconds = validateInt(value, 10, 300, "metricsWindowSeconds");
        }

        public static void setMaxListingsPerPage(int value) {
            maxListingsPerPage = validateInt(value, 10, 500, "maxListingsPerPage");
        }

        // ===== HELPER METHODS =====
        
        /** Calculate sales tax for a completed trade */
        public static int getSalesTax(int totalValue) {
            return (int)(totalValue * salesTaxPercent);
        }

        /** Calculate seller's proceeds after tax */
        public static int getSellerProceeds(int totalValue) {
            return totalValue - getSalesTax(totalValue);
        }

        /** Calculate listing fee for creating an offer */
        public static int getListingFee(int totalValue) {
            if (listingFeePercent == 0.0f) return 0;
            return Math.max(1, (int)(totalValue * listingFeePercent));
        }

        /** Calculate offer expiration time in milliseconds */
        public static long getOfferExpirationMs() {
            if (offerExpirationHours == 0) return 0L;  // Never expires
            return offerExpirationHours * 60L * 60L * 1000L;
        }

        /** Check if price is within valid range */
        public static boolean isValidPrice(int price) {
            return price >= minPricePerItem && price <= maxPricePerItem;
        }

        /** Get player's total GE slot count (uses geInventorySlots setting) */
        public static int getPlayerSlotCount() {
            return geInventorySlots;
        }
    }

    // ===== SAVE/LOAD FUNCTIONALITY =====
    
    /**
     * Save all configuration sections to SaveData.
     * Uses reflection to automatically save all @ConfigValue annotated fields.
     */
    public static void saveToData(SaveData parentSave) {
        try {
            // Save BuildMode section
            SaveData buildModeData = new SaveData("BUILD_MODE");
            saveSectionToData(BuildMode.class, buildModeData);
            parentSave.addSaveData(buildModeData);
            
            // Save Zones section
            SaveData zonesData = new SaveData("ZONES");
            saveSectionToData(Zones.class, zonesData);
            parentSave.addSaveData(zonesData);
            
            // Save PlotFlags section
            SaveData plotFlagsData = new SaveData("PLOT_FLAGS");
            saveSectionToData(PlotFlags.class, plotFlagsData);
            parentSave.addSaveData(plotFlagsData);

            // Save Settlements section
            SaveData settlementsData = new SaveData("SETTLEMENTS");
            saveSectionToData(Settlements.class, settlementsData);
            parentSave.addSaveData(settlementsData);

            // Save SettlementSpacing section
            SaveData settlementSpacingData = new SaveData("SETTLEMENT_SPACING");
            saveSectionToData(SettlementSpacing.class, settlementSpacingData);
            parentSave.addSaveData(settlementSpacingData);

            // Save CommandCenter section
            SaveData commandCenterData = new SaveData("COMMAND_CENTER");
            saveSectionToData(CommandCenter.class, commandCenterData);
            parentSave.addSaveData(commandCenterData);

            // Save Banking section
            SaveData bankingData = new SaveData("BANKING");
            saveSectionToData(Banking.class, bankingData);
            parentSave.addSaveData(bankingData);

            // Save GrandExchange section
            SaveData grandExchangeData = new SaveData("GRAND_EXCHANGE");
            saveSectionToData(GrandExchange.class, grandExchangeData);
            parentSave.addSaveData(grandExchangeData);

            ModLogger.debug("Saved configuration to data");
            
        } catch (Exception e) {
            ModLogger.error("Failed to save configuration: %s", e.getMessage());
        }
    }
    
    /**
     * Load all configuration sections from LoadData.
     * Uses reflection to automatically load all @ConfigValue annotated fields.
     */
    public static void loadFromData(LoadData parentLoad) {
        try {
            // Load BuildMode section
            LoadData buildModeData = parentLoad.getFirstLoadDataByName("BUILD_MODE");
            if (buildModeData != null) {
                loadSectionFromData(BuildMode.class, buildModeData);
            }
            
            // Load Zones section
            LoadData zonesData = parentLoad.getFirstLoadDataByName("ZONES");
            if (zonesData != null) {
                loadSectionFromData(Zones.class, zonesData);
            }
            
            // Load PlotFlags section
            LoadData plotFlagsData = parentLoad.getFirstLoadDataByName("PLOT_FLAGS");
            if (plotFlagsData != null) {
                loadSectionFromData(PlotFlags.class, plotFlagsData);
            }

            // Load Settlements section
            LoadData settlementsData = parentLoad.getFirstLoadDataByName("SETTLEMENTS");
            if (settlementsData != null) {
                loadSectionFromData(Settlements.class, settlementsData);
            }

            // Load SettlementSpacing section
            LoadData settlementSpacingData = parentLoad.getFirstLoadDataByName("SETTLEMENT_SPACING");
            if (settlementSpacingData != null) {
                loadSectionFromData(SettlementSpacing.class, settlementSpacingData);
            }
            
            // Load CommandCenter section
            LoadData commandCenterData = parentLoad.getFirstLoadDataByName("COMMAND_CENTER");
            if (commandCenterData != null) {
                loadSectionFromData(CommandCenter.class, commandCenterData);
            }

            // Load Banking section
            LoadData bankingData = parentLoad.getFirstLoadDataByName("BANKING");
            if (bankingData != null) {
                loadSectionFromData(Banking.class, bankingData);
            }

            // Load GrandExchange section
            LoadData grandExchangeData = parentLoad.getFirstLoadDataByName("GRAND_EXCHANGE");
            if (grandExchangeData != null) {
                loadSectionFromData(GrandExchange.class, grandExchangeData);
            }

            ModLogger.debug("Loaded configuration from data");
            
        } catch (Exception e) {
            ModLogger.error("Failed to load configuration: %s", e.getMessage());
        }
    }
    
    /**
     * Reset all configuration to defaults.
     */
    public static void resetToDefaults() {
        // BuildMode defaults
        BuildMode.maxBlocksPerPlacement = 500;
        BuildMode.defaultLineLength = 5;
        BuildMode.defaultSquareSize = 5;
        BuildMode.defaultCircleRadius = 5;
        BuildMode.defaultSpacing = 1;
        BuildMode.rememberBuildModeState = false;
        BuildMode.savedShape = 0;
        BuildMode.savedIsHollow = false;
        BuildMode.savedLineLength = 5;
        BuildMode.savedSquareSize = 5;
        BuildMode.savedCircleRadius = 5;
        BuildMode.savedSpacing = 1;
        BuildMode.savedDirection = 0;

        // Settlements defaults
        Settlements.protectionEnabled = false;

        // Zones defaults
        Zones.pvpReentryCooldownMs = 30000L;
        Zones.pvpSpawnImmunitySeconds = 10.0f;
        Zones.defaultDamageMultiplier = 0.05f;
        Zones.maxBarrierTiles = 1000;
        Zones.barrierAddBatchSize = 50;
        Zones.barrierMaxTilesPerTick = 10;
        Zones.defaultCombatLockSeconds = 3;
        Zones.defaultForceCleanRadius = 100;
        Zones.protectedZoneSoftLimit = 500;
        Zones.pvpZoneSoftLimit = 500;
        Zones.protectedZoneCriticalLimit = 1000;
        Zones.pvpZoneCriticalLimit = 1000;
        
        // CommandCenter defaults
        CommandCenter.defaultWidth = 600;
        CommandCenter.defaultHeight = 500;
        CommandCenter.maxFavorites = 10;
        CommandCenter.maxHistory = 20;
        CommandCenter.currentWidth = 600;
        CommandCenter.currentHeight = 500;

        ModLogger.info("Reset configuration to defaults");
    }
    
    // ===== REFLECTION HELPERS =====
    
    private static void saveSectionToData(Class<?> sectionClass, SaveData sectionData) {
        Field[] fields = sectionClass.getDeclaredFields();
        
        for (Field field : fields) {
            ConfigValue annotation = field.getAnnotation(ConfigValue.class);
            if (annotation == null) continue;
            
            try {
                field.setAccessible(true);
                String fieldName = field.getName();
                Object value = field.get(null); // Static field
                
                // Save based on type
                if (field.getType() == int.class) {
                    sectionData.addInt(fieldName, (Integer) value, annotation.description());
                } else if (field.getType() == long.class) {
                    sectionData.addLong(fieldName, (Long) value, annotation.description());
                } else if (field.getType() == float.class) {
                    sectionData.addFloat(fieldName, (Float) value, annotation.description());
                } else if (field.getType() == boolean.class) {
                    sectionData.addBoolean(fieldName, (Boolean) value, annotation.description());
                } else if (field.getType() == String.class) {
                    sectionData.addUnsafeString(fieldName, (String) value, annotation.description());
                }
                
            } catch (Exception e) {
                ModLogger.warn("Failed to save config field %s: %s", field.getName(), e.getMessage());
            }
        }
    }
    
    private static void loadSectionFromData(Class<?> sectionClass, LoadData sectionData) {
        Field[] fields = sectionClass.getDeclaredFields();
        
        for (Field field : fields) {
            ConfigValue annotation = field.getAnnotation(ConfigValue.class);
            if (annotation == null) continue;
            
            try {
                field.setAccessible(true);
                String fieldName = field.getName();
                
                // Load based on type with defaults
                if (field.getType() == int.class) {
                    int defaultValue = field.getInt(null);
                    int loadedValue = sectionData.getInt(fieldName, defaultValue);
                    
                    // Apply validation if specified
                    if (annotation.min() != Double.NEGATIVE_INFINITY || annotation.max() != Double.POSITIVE_INFINITY) {
                        loadedValue = validateInt(loadedValue, (int) annotation.min(), (int) annotation.max(), fieldName);
                    }
                    
                    field.setInt(null, loadedValue);
                    
                } else if (field.getType() == long.class) {
                    long defaultValue = field.getLong(null);
                    long loadedValue = sectionData.getLong(fieldName, defaultValue);
                    
                    if (annotation.min() != Double.NEGATIVE_INFINITY || annotation.max() != Double.POSITIVE_INFINITY) {
                        loadedValue = validateLong(loadedValue, (long) annotation.min(), (long) annotation.max(), fieldName);
                    }
                    
                    field.setLong(null, loadedValue);
                    
                } else if (field.getType() == float.class) {
                    float defaultValue = field.getFloat(null);
                    float loadedValue = sectionData.getFloat(fieldName, defaultValue);
                    
                    if (annotation.min() != Double.NEGATIVE_INFINITY || annotation.max() != Double.POSITIVE_INFINITY) {
                        loadedValue = validateFloat(loadedValue, (float) annotation.min(), (float) annotation.max(), fieldName);
                    }
                    
                    field.setFloat(null, loadedValue);
                    
                } else if (field.getType() == boolean.class) {
                    boolean defaultValue = field.getBoolean(null);
                    boolean loadedValue = sectionData.getBoolean(fieldName, defaultValue);
                    field.setBoolean(null, loadedValue);
                    
                } else if (field.getType() == String.class) {
                    String defaultValue = (String) field.get(null);
                    String loadedValue = sectionData.getUnsafeString(fieldName, defaultValue);
                    field.set(null, loadedValue);
                }
                
            } catch (Exception e) {
                ModLogger.warn("Failed to load config field %s: %s", field.getName(), e.getMessage());
            }
        }
    }
    
    /**
     * Get configuration summary for debugging.
     */
    public static String getConfigSummary() {
        StringBuilder sb = new StringBuilder("Configuration Summary:\n");
        
        sb.append("BuildMode:\n");
        sb.append(String.format("  maxBlocksPerPlacement: %d\n", BuildMode.maxBlocksPerPlacement));
        sb.append(String.format("  defaultLineLength: %d\n", BuildMode.defaultLineLength));
        sb.append(String.format("  defaultSquareSize: %d\n", BuildMode.defaultSquareSize));
        
        sb.append("Zones:\n");
        sb.append(String.format("  pvpReentryCooldownMs: %d\n", Zones.pvpReentryCooldownMs));
        sb.append(String.format("  defaultDamageMultiplier: %.2f\n", Zones.defaultDamageMultiplier));
        sb.append(String.format("  maxBarrierTiles: %d\n", Zones.maxBarrierTiles));
        
        sb.append("Admin HUD / Command Center:\n");
        sb.append(String.format("  defaultWidth: %d\n", CommandCenter.defaultWidth));
        sb.append(String.format("  defaultHeight: %d\n", CommandCenter.defaultHeight));
        sb.append(String.format("  minimizedWidth: %d\n", CommandCenter.minimizedWidth));
        sb.append(String.format("  minimizedHeight: %d\n", CommandCenter.minimizedHeight));
        sb.append(String.format("  currentWidth: %d\n", CommandCenter.currentWidth));
        sb.append(String.format("  currentHeight: %d\n", CommandCenter.currentHeight));
        sb.append(String.format("  maxFavorites: %d\n", CommandCenter.maxFavorites));
        sb.append(String.format("  maxHistory: %d\n", CommandCenter.maxHistory));
        sb.append(String.format("  mainMenuButtonWidth: %d\n", CommandCenter.mainMenuButtonWidth));

        return sb.toString();
    }
    
    // ===== VALIDATION HELPERS =====
    
    private static int validateInt(int value, int min, int max, String fieldName) {
        if (value < min || value > max) {
            ModLogger.warn("Config value %s (%d) out of range [%d, %d], clamping", fieldName, value, min, max);
            return Math.max(min, Math.min(max, value));
        }
        return value;
    }
    
    private static long validateLong(long value, long min, long max, String fieldName) {
        if (value < min || value > max) {
            ModLogger.warn("Config value %s (%d) out of range [%d, %d], clamping", fieldName, value, min, max);
            return Math.max(min, Math.min(max, value));
        }
        return value;
    }
    
    private static float validateFloat(float value, float min, float max, String fieldName) {
        if (value < min || value > max) {
            ModLogger.warn("Config value %s (%.2f) out of range [%.2f, %.2f], clamping", fieldName, value, min, max);
            return Math.max(min, Math.min(max, value));
        }
        return value;
    }
}
