package medievalsim.banking;

import medievalsim.util.ModLogger;

/**
 * Helper that centralizes the banking module's logging so every message is tagged with context.
 */
public final class BankingDiagnostics {
    private static final String MODULE_TAG = "banking";

    private BankingDiagnostics() {
        // Utility class.
    }

    private static String format(String context, String message) {
        return String.format("[%s:%s] %s", MODULE_TAG, context, message);
    }

    public static void debug(String context, String message, Object... args) {
        ModLogger.debug(format(context, message), args);
    }

    public static void info(String context, String message, Object... args) {
        ModLogger.info(format(context, message), args);
    }

    public static void warn(String context, String message, Object... args) {
        ModLogger.warn(format(context, message), args);
    }

    public static void error(String context, String message, Object... args) {
        ModLogger.error(format(context, message), args);
    }
}
