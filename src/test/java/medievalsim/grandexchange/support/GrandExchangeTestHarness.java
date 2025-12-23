package medievalsim.grandexchange.support;

import medievalsim.banking.domain.BankingLevelData;
import medievalsim.banking.domain.PlayerBank;
import medievalsim.config.ModConfig;
import medievalsim.grandexchange.domain.BuyOrder;
import medievalsim.grandexchange.domain.GEOffer;
import medievalsim.grandexchange.domain.GrandExchangeLevelData;
import medievalsim.grandexchange.domain.PlayerGEInventory;
import medievalsim.grandexchange.domain.CollectionItem;
import medievalsim.grandexchange.services.NotificationService;
import medievalsim.grandexchange.net.SellActionResultCode;
import medievalsim.registries.MedievalSimLevelData;
import necesse.engine.GlobalData;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.registries.GNDRegistry;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.LevelLayerRegistry;
import necesse.engine.registries.LevelRegistry;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bootstraps an isolated banking + Grand Exchange environment backed by a {@link HeadlessServerLevel}.
 * The harness configures {@link ModConfig.GrandExchange} for deterministic tests (no rate limits,
 * zero tax) and provides helper methods for staging players/offers.
 */
public class GrandExchangeTestHarness implements AutoCloseable {

    private static final AtomicBoolean REGISTRIES_BOOTSTRAPPED = new AtomicBoolean(false);
    private static final AtomicBoolean HEADLESS_LEVEL_REGISTERED = new AtomicBoolean(false);
    private static final AtomicBoolean LEVEL_LAYERS_REGISTERED = new AtomicBoolean(false);
    private static final AtomicBoolean GND_REGISTERED = new AtomicBoolean(false);
    private static final AtomicBoolean GLOBAL_DATA_BOOTSTRAPPED = new AtomicBoolean(false);
    private static final AtomicBoolean TEST_ITEM_REGISTERED = new AtomicBoolean(false);
    private static final String HEADLESS_LEVEL_ID = "medievalsim_test_headless_level";
    private static final String TEST_ITEM_ID = "medievalsim_test_item";
    private static final List<String> REQUIRED_LOCALE_FILES = Arrays.asList(
        "en.lang", "zh-CN.lang", "zh-TW.lang", "ru.lang", "pt-BR.lang", "es.lang", "de.lang",
        "pl.lang", "cs.lang", "tr.lang", "ja.lang", "fr.lang", "uk.lang", "da.lang", "se.lang",
        "no.lang", "hu.lang", "it.lang", "kr.lang", "th.lang", "vi.lang", "id.lang", "lt.lang",
        "nl.lang", "fi.lang", "hr.lang", "ca.lang", "ar.lang"
    );
    private static final List<String> DEFAULT_ITEM_IDS = Arrays.asList(
        "stone", "woodlog", "torch", "copperbar", "ironbar", "rope", "apple", "wheat", "carrot", "pumpkin"
    );

    public static GrandExchangeTestHarness boot() {
        return new GrandExchangeTestHarness();
    }

    private final float originalSalesTaxPercent = ModConfig.GrandExchange.salesTaxPercent;
    private final int originalOfferCreationCooldown = ModConfig.GrandExchange.offerCreationCooldown;
    private final int originalToggleCooldown = ModConfig.GrandExchange.sellDisableCooldownSeconds;
    private final boolean originalInstantTrades = ModConfig.GrandExchange.enableInstantTrades;

    public final HeadlessServerLevel level;
    public final GrandExchangeLevelData grandExchangeData;
    public final BankingLevelData bankingData;

    private final Map<Long, PlayerHandle> players = new ConcurrentHashMap<>();
    private final String defaultItemId;

    private GrandExchangeTestHarness() {
        ensureRegistriesBootstrapped();
        ModConfig.GrandExchange.salesTaxPercent = 0f;
        ModConfig.GrandExchange.offerCreationCooldown = 0;
        ModConfig.GrandExchange.sellDisableCooldownSeconds = 0;
        ModConfig.GrandExchange.enableInstantTrades = true;

        this.level = new HeadlessServerLevel();
        GrandExchangeLevelData data = GrandExchangeLevelData.getGrandExchangeData(level);
        if (data == null) {
            throw new IllegalStateException("Failed to attach GrandExchangeLevelData to test level");
        }
        this.grandExchangeData = data;
        this.bankingData = BankingLevelData.getBankingData(level);
        this.defaultItemId = resolveBaselineItemId();
    }

    private static void ensureRegistriesBootstrapped() {
        ensureGlobalDataBootstrapped();
        if (REGISTRIES_BOOTSTRAPPED.compareAndSet(false, true)) {
            MedievalSimLevelData.registerCore();
        }
        if (LEVEL_LAYERS_REGISTERED.compareAndSet(false, true)) {
            LevelLayerRegistry.instance.registerCore();
        }
        if (GND_REGISTERED.compareAndSet(false, true)) {
            GNDRegistry.instance.registerCore();
        }
        if (HEADLESS_LEVEL_REGISTERED.compareAndSet(false, true)) {
            LevelRegistry.registerLevel(HEADLESS_LEVEL_ID, HeadlessServerLevel.class);
        }
    }

    private static void ensureGlobalDataBootstrapped() {
        if (GLOBAL_DATA_BOOTSTRAPPED.compareAndSet(false, true)) {
            try {
                Path root = resolveLocaleRoot();
                ensureLocaleFiles(root);
                Path appData = Files.createTempDirectory("medievalsim-necesse-tests");
                setGlobalDataField("rootPath", withTrailingSeparator(root));
                setGlobalDataField("appDataPath", withTrailingSeparator(appData));
                setGlobalDataField("isServer", Boolean.TRUE);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to configure GlobalData paths", e);
            }
        }
    }

    private static Path resolveLocaleRoot() {
        Path repoRoot = Paths.get("").toAbsolutePath();
        Path binLocale = repoRoot.resolve(Paths.get("bin", "main", "locale"));
        if (Files.isDirectory(binLocale)) {
            return binLocale.getParent();
        }
        Path srcLocale = repoRoot.resolve(Paths.get("src", "main", "resources", "locale"));
        if (Files.isDirectory(srcLocale)) {
            return srcLocale.getParent();
        }
        throw new IllegalStateException("Unable to locate locale assets for GlobalData bootstrap");
    }

    private static String withTrailingSeparator(Path path) {
        String value = path.toAbsolutePath().toString();
        if (!value.endsWith(File.separator)) {
            value = value + File.separator;
        }
        return value;
    }

    private static void setGlobalDataField(String fieldName, Object value) {
        try {
            Field field = GlobalData.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            if (field.getType() == boolean.class) {
                field.setBoolean(null, (Boolean) value);
            } else {
                field.set(null, value);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to update GlobalData field " + fieldName, e);
        }
    }

    private static void ensureLocaleFiles(Path root) throws IOException {
        Path localeDir = root.resolve("locale");
        if (!Files.isDirectory(localeDir)) {
            throw new IllegalStateException("Locale directory missing at " + localeDir);
        }
        Path english = localeDir.resolve("en.lang");
        if (!Files.exists(english)) {
            throw new IllegalStateException("Missing base en.lang file at " + english);
        }
        for (String fileName : REQUIRED_LOCALE_FILES) {
            if ("en.lang".equals(fileName)) {
                continue;
            }
            Path target = localeDir.resolve(fileName);
            if (Files.notExists(target)) {
                Files.copy(english, target);
            }
        }
    }

    private String resolveBaselineItemId() {
        for (String candidate : DEFAULT_ITEM_IDS) {
            if (ItemRegistry.getItem(candidate) != null) {
                return candidate;
            }
        }
        // Fallback: scan numeric IDs until we find a valid entry.
        for (int id = 0; id < 4096; id++) {
            if (ItemRegistry.getItem(id) != null) {
                return ItemRegistry.getItemStringID(id);
            }
        }
        ensureFallbackItemRegistered();
        return TEST_ITEM_ID;
    }

    public String defaultItemId() {
        return defaultItemId;
    }

    public PlayerHandle registerPlayer(long auth, String name, int startingCoins) {
        if (players.containsKey(auth)) {
            throw new IllegalArgumentException("Player auth already registered: " + auth);
        }
        String resolvedName = (name == null || name.isBlank()) ? ("Player-" + auth) : name;
        PlayerHandle handle = new PlayerHandle(auth, resolvedName, startingCoins);
        players.put(auth, handle);
        grandExchangeData.getOrCreateInventory(auth);
        PlayerBank bank = bankingData.getOrCreateBank(auth);
        if (startingCoins > 0) {
            bank.addCoins(startingCoins);
        }
        return handle;
    }

    public GEOffer stageSellOffer(PlayerHandle player, int slot, String itemStringID, int quantity, int pricePerItem) {
        String itemId = itemStringID == null ? defaultItemId : itemStringID;
        GEOffer offer = grandExchangeData.createSellOffer(player.auth(), player.name(), slot, itemId, quantity, pricePerItem);
        if (offer == null) {
            throw new IllegalStateException("Failed to create sell offer for " + player.name());
        }
        return offer;
    }

    public BuyOrder stageBuyOrder(PlayerHandle player, int slot, String itemStringID, int quantity, int pricePerItem) {
        String itemId = itemStringID == null ? defaultItemId : itemStringID;
        BuyOrder order = grandExchangeData.createBuyOrder(player.auth(), player.name(), slot, itemId, quantity, pricePerItem, 7);
        if (order == null) {
            throw new IllegalStateException("Failed to create buy order for " + player.name());
        }
        return order;
    }

    public SellActionResultCode enableSellOffer(PlayerHandle player, int slot) {
        return grandExchangeData.enableSellOffer(level, player.auth(), slot);
    }

    public boolean enableBuyOrder(PlayerHandle player, int slot) {
        return grandExchangeData.enableBuyOrder(level, player.auth(), slot);
    }

    public boolean disableBuyOrder(PlayerHandle player, int slot) {
        return grandExchangeData.disableBuyOrder(level, player.auth(), slot);
    }

    public boolean cancelSellOffer(PlayerHandle player, int slot) {
        PlayerGEInventory inventory = inventory(player);
        GEOffer offer = inventory.getSlotOffer(slot);
        if (offer == null) {
            return false;
        }
        return grandExchangeData.cancelOffer(level, offer.getOfferID());
    }

    public boolean processMarketPurchase(PlayerHandle buyer, long offerId, int quantity) {
        return grandExchangeData.processMarketPurchase(level, buyer.auth(), buyer.name(), offerId, quantity);
    }

    public void setCollectionDepositPreference(PlayerHandle player, boolean preferBank) {
        grandExchangeData.setCollectionDepositPreference(player.auth(), preferBank);
    }

    public PlayerGEInventory inventory(PlayerHandle player) {
        return grandExchangeData.getOrCreateInventory(player.auth());
    }

    public PlayerBank bank(PlayerHandle player) {
        return bankingData.getOrCreateBank(player.auth());
    }

    public long bankBalance(PlayerHandle player) {
        return bank(player).getCoins();
    }

    public long coinsSpent(PlayerHandle player) {
        return Math.max(0, player.startingCoins() - bank(player).getCoins());
    }

    public int collectionQuantity(PlayerHandle player, String itemStringID) {
        PlayerGEInventory inventory = inventory(player);
        String itemId = itemStringID == null ? defaultItemId : itemStringID;
        return inventory.getCollectionBox().stream()
            .filter(item -> Objects.equals(item.getItemStringID(), itemId))
            .mapToInt(CollectionItem::getQuantity)
            .sum();
    }

    public int notificationCount(PlayerHandle player) {
        NotificationService service = grandExchangeData.getNotificationService();
        return service != null ? service.getNotificationCount(player.auth()) : 0;
    }

    @Override
    public void close() {
        ModConfig.GrandExchange.salesTaxPercent = originalSalesTaxPercent;
        ModConfig.GrandExchange.offerCreationCooldown = originalOfferCreationCooldown;
        ModConfig.GrandExchange.sellDisableCooldownSeconds = originalToggleCooldown;
        ModConfig.GrandExchange.enableInstantTrades = originalInstantTrades;
    }

    public record PlayerHandle(long auth, String name, int startingCoins) {}

    private static void ensureFallbackItemRegistered() {
        if (TEST_ITEM_REGISTERED.compareAndSet(false, true)) {
            ItemRegistry.registerItem(TEST_ITEM_ID, new HarnessTestItem(), 1.0f, true);
        }
    }

    private static final class HarnessTestItem extends Item {

        HarnessTestItem() {
            super(9999);
        }

        @Override
        public GameMessage getNewLocalization() {
            return new StaticMessage("Harness Test Item");
        }

        @Override
        public GameMessage getLocalization(InventoryItem item) {
            return new StaticMessage("Harness Test Item");
        }
    }
}
