package medievalsim.grandexchange.ui.util;

import java.util.concurrent.TimeUnit;

import necesse.engine.localization.Localization;
import necesse.engine.registries.ItemRegistry;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;

/**
 * Shared formatting helpers for Grand Exchange UI components.
 */
public final class GrandExchangeUIUtils {
    private GrandExchangeUIUtils() {
    }

    public static String formatCoins(long amount) {
        return String.format("%,d", amount);
    }

    /**
     * Returns a localized display name for the given item string ID.
     * Falls back to the raw ID if item is not found, or a localized "Unknown Item" if null/blank.
     */
    public static String getItemDisplayName(String itemStringID) {
        if (itemStringID == null || itemStringID.isBlank()) {
            return Localization.translate("ui", "grandexchange.item.unknown");
        }
        Item item = ItemRegistry.getItem(itemStringID);
        if (item == null) {
            return itemStringID;
        }
        InventoryItem defaultStack = item.getDefaultItem(null, 1);
        return item.getDisplayName(defaultStack);
    }

    /**
     * Formats an expiration timestamp into a localized remaining time string.
     */
    public static String formatExpiration(long expirationTime) {
        if (expirationTime <= 0L) {
            return Localization.translate("ui", "grandexchange.expiration.none");
        }
        long remaining = expirationTime - System.currentTimeMillis();
        if (remaining <= 0L) {
            return Localization.translate("ui", "grandexchange.expiration.now");
        }
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remaining);
        if (minutes >= 1440L) {
            long days = minutes / 1440L;
            return Localization.translate("ui", "grandexchange.expiration.days",
                "days", Long.toString(days),
                "plural", days == 1L ? "" : "s");
        }
        if (minutes >= 60L) {
            long hours = minutes / 60L;
            return Localization.translate("ui", "grandexchange.expiration.hours",
                "hours", Long.toString(hours),
                "plural", hours == 1L ? "" : "s");
        }
        return Localization.translate("ui", "grandexchange.expiration.minutes",
            "minutes", Long.toString(Math.max(1L, minutes)));
    }

    public static String formatRelativeTime(long timestamp) {
        long delta = Math.max(0L, System.currentTimeMillis() - timestamp);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(delta);
        long hours = TimeUnit.MILLISECONDS.toHours(delta);
        long days = TimeUnit.MILLISECONDS.toDays(delta);
        if (days > 0) {
            return Localization.translate("ui", "grandexchange.time.days", "value", Long.toString(days));
        }
        if (hours > 0) {
            return Localization.translate("ui", "grandexchange.time.hours", "value", Long.toString(hours));
        }
        if (minutes > 0) {
            return Localization.translate("ui", "grandexchange.time.minutes", "value", Long.toString(minutes));
        }
        return Localization.translate("ui", "grandexchange.time.justnow");
    }
}
