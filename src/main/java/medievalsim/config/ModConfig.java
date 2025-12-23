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
        /** If true, only world owners can edit this setting */
        boolean ownerOnly() default false;
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface ConfigSection {
        /** Section name for save/load */
        String value();
        /** Section description */
        String description() default "";
        /** If true, this section is hidden from the universal ModSettingsTab UI */
        boolean hidden() default false;
    }
    
    // ===== BUILD MODE CONFIGURATION =====
    
    @ConfigSection(value = "BUILD_MODE", description = "Advanced construction tools configuration", hidden = true)
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
    
    // ===== BOSS SUMMON CONFIGURATION =====
    
    @ConfigSection(value = "BOSS_SUMMONS", description = "Boss summon item restrictions and permissions")
    public static class BossSummons {
        
        @ConfigValue(
            defaultValue = "true",
            description = "Allow boss summons globally (disable to block all boss summons in the world)",
            runtime = true,
            ownerOnly = true
        )
        public static boolean allowBossSummonsGlobally = true;
        
        @ConfigValue(
            defaultValue = "false",
            description = "Allow boss summons in Protected Zones by default (individual zones can override)",
            runtime = true
        )
        public static boolean allowInProtectedZonesByDefault = false;
        
        @ConfigValue(
            defaultValue = "false",
            description = "Allow boss summons in Settlements by default (individual settlements can override)",
            runtime = true
        )
        public static boolean allowInSettlementsByDefault = false;
        
        @ConfigValue(
            defaultValue = "true",
            description = "Allow boss summons in PvP Zones by default (individual zones can override)",
            runtime = true
        )
        public static boolean allowInPvPZonesByDefault = true;
        
        // Setters with validation and edge case handling
        public static void setAllowBossSummonsGlobally(boolean value) {
            boolean changed = (allowBossSummonsGlobally != value);
            allowBossSummonsGlobally = value;
            
            if (changed && !value) {
                // Global boss summons disabled - log warning about zone configs being ignored
                ModLogger.info("Global boss summons disabled. Individual zone settings will be ignored.");
            }
        }
        
        public static void setAllowInProtectedZonesByDefault(boolean value) {
            allowInProtectedZonesByDefault = value;
        }
        
        public static void setAllowInSettlementsByDefault(boolean value) {
            allowInSettlementsByDefault = value;
        }
        
        public static void setAllowInPvPZonesByDefault(boolean value) {
            allowInPvPZonesByDefault = value;
        }
    }
    
    // ===== SETTLEMENTS CONFIGURATION (Consolidated) =====
    
    @ConfigSection(value = "SETTLEMENTS", description = "Settlement protection, plot flags, and spacing configuration")
    public static class Settlements {

        // === Protection Settings ===
        @ConfigValue(
            defaultValue = "false",
            description = "Enable zone protection for settlements (protects settlement area from non-members)"
        )
        public static boolean protectionEnabled = false;

        // === Plot Flags Settings ===
        @ConfigValue(
            defaultValue = "false",
            description = "Enable plot flag system (when enabled, normal settlement flags cannot be purchased/placed)"
        )
        public static boolean plotFlagsEnabled = false;
        
        @ConfigValue(
            defaultValue = "1000",
            description = "Coin cost to purchase plot flags",
            min = 1, max = 1000000
        )
        public static int plotFlagCoinCost = 1000;

        // === Settlement Spacing Settings ===
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
        public static void setProtectionEnabled(boolean value) {
            protectionEnabled = value;
        }

        public static void setPlotFlagsEnabled(boolean value) {
            plotFlagsEnabled = value;
        }
        
        public static void setPlotFlagCoinCost(int value) {
            plotFlagCoinCost = validateInt(value, 1, 1000000, "plotFlagCoinCost");
        }

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
            defaultValue = "true",
            description = "Enable Grand Exchange (marketplace) in the banking system. WARNING: Toggling this OFF and ON will permanently erase all GE data!",
            ownerOnly = true
        )
        public static boolean grandExchangeEnabled = true;

        // Track previous state to detect toggles
        private static boolean previousGEState = true;        @ConfigValue(
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

        /**
         * Set Grand Exchange enabled state.
         * When re-enabled after being disabled, all GE data is wiped.
         * @param value true to enable, false to disable
         */
        public static void setGrandExchangeEnabled(boolean value) {
            boolean wasEnabled = grandExchangeEnabled;
            grandExchangeEnabled = value;
            
            // When toggling ON after being OFF, schedule a data reset
            if (value && !wasEnabled) {
                grandExchangeResetPending = true;
                ModLogger.warn("Grand Exchange re-enabled - data reset will occur on next world load");
            }
            
            previousGEState = value;
        }
        
        // Flag to indicate GE data should be wiped on next access
        public static boolean grandExchangeResetPending = false;

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
    // Hidden from ModSettingsTab - all settings are managed in the GE Defaults tab (owner-only)
    @ConfigSection(value = "GRAND_EXCHANGE", description = "Player marketplace and trading system configuration (RuneScape-style)", hidden = true)
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

        @ConfigValue(
            defaultValue = "10000",
            description = "Confirmation threshold for large purchases (0 = disabled, shows dialog for purchases over this amount)",
            min = 0, max = 10000000
        )
        public static int largePurchaseThreshold = 10000;

        /**
         * Authentication ID of the player who receives GE profits (taxes/fees).
         * -1 means world owner (default behavior).
         * Can be set to any player's auth ID via the Defaults tab.
         */
        public static long profitRecipientAuth = -1L;
        
        /**
         * Display name of the profit recipient (cached for UI display).
         * Empty string means world owner.
         */
        public static String profitRecipientName = "";

        public static void setProfitRecipient(long auth, String name) {
            profitRecipientAuth = auth;
            profitRecipientName = name != null ? name : "";
            ModLogger.info("GE profit recipient set to: %s (auth=%d)", 
                profitRecipientName.isEmpty() ? "World Owner" : profitRecipientName, auth);
        }

        // === OFFER EXPIRATION ===
        
        @ConfigValue(
            defaultValue = "true",
            description = "Enable automatic offer expiration (offers cancel after duration)"
        )
        public static boolean enableOfferExpiration = true;

        @ConfigValue(
            defaultValue = "1",
            description = "Minimum sell duration exposed to players (days)",
            min = 1, max = 30
        )
        public static int minSellDurationDays = 1;

        @ConfigValue(
            defaultValue = "14",
            description = "Maximum sell duration exposed to players (days)",
            min = 1, max = 60
        )
        public static int maxSellDurationDays = 14;

        @ConfigValue(
            defaultValue = "168",
            description = "Offer expiration duration in hours (168 = 1 week, 0 = never expires)",
            min = 0, max = 720
        )
        public static int offerExpirationHours = 168;

        @ConfigValue(
            defaultValue = "2",
            description = "Cooldown in seconds before a sell listing can be disabled again",
            min = 0, max = 60
        )
        public static int sellDisableCooldownSeconds = 2;

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
            defaultValue = "250",
            description = "UI heartbeat interval for dynamic updates like cooldowns (milliseconds)",
            min = 100, max = 1000
        )
        public static int uiHeartbeatIntervalMs = 250;

        @ConfigValue(
            defaultValue = "15",
            description = "Collection tab entries per page",
            min = 5, max = 40
        )
        public static int collectionPageSize = 15;

        @ConfigValue(
            defaultValue = "true",
            description = "Default preference for depositing collection items to bank"
        )
        public static boolean defaultCollectionDepositToBank = true;

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

        @ConfigValue(
            defaultValue = "true",
            description = "Immediately clear the Sell tab staging slot when a post is submitted"
        )
        public static boolean autoClearSellStagingSlot = true;

        // === STATISTICS ===
        
        @ConfigValue(
            defaultValue = "true",
            description = "Track GE statistics (total trades, volume, etc.)"
        )
        public static boolean enableStatistics = true;

        @ConfigValue(
            defaultValue = "5",
            description = "Maximum number of items included in market insight summaries",
            min = 1, max = 20
        )
        public static int marketInsightTopEntries = 5;

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

        @ConfigValue(
            defaultValue = "20",
            description = "Diagnostics snapshots to retain for /gediag history",
            min = 5, max = 100
        )
        public static int diagnosticsHistorySize = 20;

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

        public static void setBuyOrderSlots(int value) {
            buyOrderSlots = validateInt(value, 1, 10, "buyOrderSlots");
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

        public static void setMinSellDurationDays(int value) {
            minSellDurationDays = validateInt(value, 1, 30, "minSellDurationDays");
            if (minSellDurationDays > maxSellDurationDays) {
                maxSellDurationDays = minSellDurationDays;
            }
        }

        public static void setMaxSellDurationDays(int value) {
            maxSellDurationDays = validateInt(value, 1, 60, "maxSellDurationDays");
            if (maxSellDurationDays < minSellDurationDays) {
                minSellDurationDays = maxSellDurationDays;
            }
        }

        public static void setPriceHistorySize(int value) {
            priceHistorySize = validateInt(value, 10, 200, "priceHistorySize");
        }

        public static void setAutoRefreshSeconds(int value) {
            autoRefreshSeconds = validateInt(value, 0, 300, "autoRefreshSeconds");
        }

        public static void setUiHeartbeatIntervalMs(int value) {
            uiHeartbeatIntervalMs = validateInt(value, 100, 1000, "uiHeartbeatIntervalMs");
        }

        public static void setCollectionPageSize(int value) {
            collectionPageSize = validateInt(value, 5, 40, "collectionPageSize");
        }

        public static void setDefaultCollectionDepositToBank(boolean value) {
            defaultCollectionDepositToBank = value;
        }

        public static void setOfferCreationCooldown(int value) {
            offerCreationCooldown = validateInt(value, 0, 60000, "offerCreationCooldown");
        }

        public static void setSellDisableCooldownSeconds(int value) {
            sellDisableCooldownSeconds = validateInt(value, 0, 60, "sellDisableCooldownSeconds");
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

        public static void setDiagnosticsHistorySize(int value) {
            diagnosticsHistorySize = validateInt(value, 5, 100, "diagnosticsHistorySize");
        }

        public static void setMaxListingsPerPage(int value) {
            maxListingsPerPage = validateInt(value, 10, 500, "maxListingsPerPage");
        }

        public static void setMarketInsightTopEntries(int value) {
            marketInsightTopEntries = validateInt(value, 1, 20, "marketInsightTopEntries");
        }

        public static void setAutoClearSellStagingSlot(boolean value) {
            autoClearSellStagingSlot = value;
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

        public static int getCollectionPageSize() {
            return collectionPageSize;
        }

        public static boolean getDefaultCollectionDepositPreference() {
            return defaultCollectionDepositToBank;
        }

        /**
         * Clamp a total duration (in hours) to configured sell duration bounds.
         * If offer expiration is disabled, returns 0 to signal "no expiration".
         */
        public static int normalizeSellDurationHours(int totalHours) {
            if (!isDurationActive()) {
                return 0;
            }

            int minHours = getMinSellDurationHoursInternal();
            int maxHours = getMaxSellDurationHoursInternal(minHours);
            int hours = totalHours;
            if (hours <= 0) {
                hours = offerExpirationHours;
            }

            if (hours < minHours) {
                hours = minHours;
            } else if (hours > maxHours) {
                hours = maxHours;
            }

            return hours;
        }

        /** Convenience overload that accepts separate day + hour controls. */
        public static int normalizeSellDurationHours(int requestedDays, int requestedExtraHours) {
            if (!isDurationActive()) {
                return 0;
            }

            long total = (long)Math.max(0, requestedDays) * 24L + Math.max(0, requestedExtraHours);
            if (total > Integer.MAX_VALUE) {
                total = Integer.MAX_VALUE;
            }
            return normalizeSellDurationHours((int)total);
        }

        /** Default duration to use when UI does not supply an explicit value. */
        public static int getDefaultSellDurationHours() {
            if (!isDurationActive()) {
                return 0;
            }
            return normalizeSellDurationHours(offerExpirationHours);
        }

        private static boolean isDurationActive() {
            return enableOfferExpiration && offerExpirationHours != 0;
        }

        private static int getMinSellDurationHoursInternal() {
            int minDays = Math.max(1, minSellDurationDays);
            return Math.max(1, minDays) * 24;
        }

        private static int getMaxSellDurationHoursInternal(int minHours) {
            int maxDays = Math.max(minSellDurationDays, maxSellDurationDays);
            int maxHours = Math.max(minHours, maxDays * 24);
            return maxHours;
        }
    }

    // ===== GUILDS CONFIGURATION =====
    
    @ConfigSection(value = "GUILDS", description = "Guild system configuration - player organizations, shared ownership, research")
    public static class Guilds {
        
        // === CORE LIMITS ===
        
        @ConfigValue(
            defaultValue = "3",
            description = "Maximum guilds a player can join simultaneously",
            min = 1, max = 10
        )
        public static int maxGuildsPerPlayer = 3;
        
        @ConfigValue(
            defaultValue = "50",
            description = "Maximum members per guild",
            min = 5, max = 200
        )
        public static int maxMembersPerGuild = 50;
        
        @ConfigValue(
            defaultValue = "50000",
            description = "Gold required to create a new guild",
            min = 0, max = 1000000
        )
        public static int guildCreationCost = 50000;
        
        // === BANNER LIMITS (per docs: enforce server-side) ===
        
        @ConfigValue(
            defaultValue = "1",
            description = "Maximum guild banners a player can own per settlement per guild (per docs)",
            min = 1, max = 10
        )
        public static int maxBannersPerSettlementPerGuild = 1;
        
        @ConfigValue(
            defaultValue = "500",
            description = "Gold cost to purchase a guild banner",
            min = 0, max = 100000
        )
        public static int bannerCost = 500;
        
        // === UNLOCK BOSS ===
        
        @ConfigValue(
            defaultValue = "PIRATE_CAPTAIN",
            description = "Boss that must be defeated to unlock guild features (NONE = no requirement)"
        )
        public static String unlockBoss = "PIRATE_CAPTAIN";
        
        // === PLOT PRICING (Leader-adjustable) ===
        
        @ConfigValue(
            defaultValue = "100000",
            description = "Base price for purchasing a plot",
            min = 0, max = 10000000
        )
        public static int basePlotPrice = 100000;
        
        @ConfigValue(
            defaultValue = "1000",
            description = "Additional price per settler in the plot",
            min = 0, max = 100000
        )
        public static int perSettlerWeight = 1000;
        
        @ConfigValue(
            defaultValue = "5000",
            description = "Additional price per quality room score",
            min = 0, max = 100000
        )
        public static int perQualityRoomWeight = 5000;
        
        @ConfigValue(
            defaultValue = "500",
            description = "Additional price per storage slot",
            min = 0, max = 10000
        )
        public static int perStorageSlotValue = 500;
        
        @ConfigValue(
            defaultValue = "2000",
            description = "Base value per workstation",
            min = 0, max = 100000
        )
        public static int workstationBaseValue = 2000;
        
        // Zone bonuses
        @ConfigValue(defaultValue = "50000", description = "Bonus value for Husbandry zones", min = 0)
        public static int husbandryZoneBonus = 50000;
        
        @ConfigValue(defaultValue = "30000", description = "Bonus value for Forestry zones", min = 0)
        public static int forestryZoneBonus = 30000;
        
        @ConfigValue(defaultValue = "40000", description = "Bonus value for Farming zones", min = 0)
        public static int farmingZoneBonus = 40000;
        
        // === TAX SETTINGS ===
        
        @ConfigValue(
            defaultValue = "0.03",
            description = "Default guild tax rate on Grand Exchange sales (0.03 = 3%)",
            min = 0.0, max = 0.15
        )
        public static float defaultTaxRate = 0.03f;
        
        @ConfigValue(
            defaultValue = "0.15",
            description = "Maximum allowed guild tax rate",
            min = 0.0, max = 0.25
        )
        public static float maxTaxRate = 0.15f;
        
        // === TREASURY ===
        
        @ConfigValue(
            defaultValue = "10000",
            description = "Default minimum treasury balance for scientist automation",
            min = 0, max = 1000000
        )
        public static long defaultTreasuryMinimum = 10000;
        
        @ConfigValue(
            defaultValue = "0",
            description = "Daily officer withdrawal limit (0 = unlimited)",
            min = 0, max = 10000000
        )
        public static long officerDailyWithdrawLimit = 0;
        
        // === RESEARCH / SCIENTIST ===
        
        @ConfigValue(
            defaultValue = "10000",
            description = "Default scientist pull rate (resources per hour)",
            min = 1000, max = 50000
        )
        public static int scientistPullRate = 10000;
        
        @ConfigValue(
            defaultValue = "false",
            description = "Require boss kills to unlock research nodes (false = any item counts)"
        )
        public static boolean requireResearchGating = false;
        
        // === CREST SYSTEM ===
        
        @ConfigValue(
            defaultValue = "true",
            description = "Use lazy crest generation (generate on first request vs upfront)"
        )
        public static boolean lazyCrestGeneration = true;
        
        @ConfigValue(
            defaultValue = "true",
            description = "Persist crest cache across mod updates"
        )
        public static boolean persistCrestCache = true;
        
        // === ITEMS ===
        
        @ConfigValue(
            defaultValue = "50",
            description = "Cost of Guild Teleport Stand item from Artisan",
            min = 0, max = 100000
        )
        public static int teleportStandCost = 50;
        
        @ConfigValue(
            defaultValue = "100",
            description = "Cost of Plot Survey item from Artisan",
            min = 0, max = 100000
        )
        public static int plotSurveyCost = 100;
        
        // ===== SETTERS WITH VALIDATION =====
        
        public static void setMaxGuildsPerPlayer(int value) {
            maxGuildsPerPlayer = validateInt(value, 1, 10, "maxGuildsPerPlayer");
        }
        
        public static void setMaxMembersPerGuild(int value) {
            maxMembersPerGuild = validateInt(value, 5, 200, "maxMembersPerGuild");
        }
        
        public static void setGuildCreationCost(int value) {
            guildCreationCost = validateInt(value, 0, 1000000, "guildCreationCost");
        }
        
        public static void setBasePlotPrice(int value) {
            basePlotPrice = validateInt(value, 0, 10000000, "basePlotPrice");
        }
        
        public static void setDefaultTaxRate(float value) {
            defaultTaxRate = validateFloat(value, 0.0f, maxTaxRate, "defaultTaxRate");
        }
        
        public static void setMaxTaxRate(float value) {
            maxTaxRate = validateFloat(value, 0.0f, 0.25f, "maxTaxRate");
            if (defaultTaxRate > maxTaxRate) {
                defaultTaxRate = maxTaxRate;
            }
        }
        
        public static void setScientistPullRate(int value) {
            scientistPullRate = validateInt(value, 1000, 50000, "scientistPullRate");
        }
        
        public static void setDefaultTreasuryMinimum(long value) {
            defaultTreasuryMinimum = validateLong(value, 0, 1000000, "defaultTreasuryMinimum");
        }
        
        public static void setOfficerDailyWithdrawLimit(long value) {
            officerDailyWithdrawLimit = validateLong(value, 0, 10000000, "officerDailyWithdrawLimit");
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
            
            // PlotFlags merged into Settlements section - no longer saved separately

            // Save Settlements section
            SaveData settlementsData = new SaveData("SETTLEMENTS");
            saveSectionToData(Settlements.class, settlementsData);
            parentSave.addSaveData(settlementsData);

            // SettlementSpacing merged into Settlements section - no longer saved separately

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

            // Save Guilds section
            SaveData guildsData = new SaveData("GUILDS");
            saveSectionToData(Guilds.class, guildsData);
            parentSave.addSaveData(guildsData);

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
            
            // PlotFlags merged into Settlements - migration handled in Settlements load

            // Load Settlements section
            LoadData settlementsData = parentLoad.getFirstLoadDataByName("SETTLEMENTS");
            if (settlementsData != null) {
                loadSectionFromData(Settlements.class, settlementsData);
            }

            // SettlementSpacing merged into Settlements - migration handled in Settlements load
            
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

            // Load Guilds section
            LoadData guildsData = parentLoad.getFirstLoadDataByName("GUILDS");
            if (guildsData != null) {
                loadSectionFromData(Guilds.class, guildsData);
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
        if (sectionData == null) {
            return;
        }
        Field[] fields = sectionClass.getDeclaredFields();
        for (Field field : fields) {
            ConfigValue annotation = field.getAnnotation(ConfigValue.class);
            if (annotation == null) continue;
            try {
                field.setAccessible(true);
                String fieldName = field.getName();
                if (!sectionData.hasLoadDataByName(fieldName)) {
                    // Leave default intact when legacy configs omit the field
                    continue;
                }
                // Load based on type with defaults
                if (field.getType() == int.class) {
                    int defaultValue = field.getInt(null);
                    int loadedValue = sectionData.getInt(fieldName, defaultValue);
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
                    boolean loadedValue = sectionData.getBoolean(fieldName, defaultValue, false);
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
