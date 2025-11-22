package medievalsim.util;

/**
 * Centralized logging utility for Medieval Sim mod
 * Provides consistent logging patterns and proper integration with Necesse's logging system
 */
public final class ModLogger {
    
    private static final String MOD_ID = Constants.MOD_ID;
    private static java.util.logging.Logger logger;
    
    static {
        initializeLogger();
    }
    
    private static void initializeLogger() {
        try {
            // Try to get the proper Necesse logger
            logger = java.util.logging.Logger.getLogger(MOD_ID);
        } catch (Exception e) {
            // Fallback to standard logger if Necesse logger unavailable
            logger = java.util.logging.Logger.getLogger(Constants.MOD_NAME);
        }
    }
    
    /**
     * Log an info message
     */
    public static void info(String message) {
        logger.info(Constants.LOG_INFO_PREFIX + message);
    }
    
    /**
     * Log an info message with formatting
     */
    public static void info(String format, Object... args) {
        info(String.format(format, args));
    }
    
    /**
     * Log a warning message
     */
    public static void warn(String message) {
        logger.warning(Constants.LOG_WARNING_PREFIX + message);
    }
    
    /**
     * Log a warning message with formatting
     */
    public static void warn(String format, Object... args) {
        warn(String.format(format, args));
    }
    
    /**
     * Log an error message
     */
    public static void error(String message) {
        logger.severe(Constants.LOG_ERROR_PREFIX + message);
    }
    
    /**
     * Log an error message with formatting
     */
    public static void error(String format, Object... args) {
        error(String.format(format, args));
    }
    
    /**
     * Log an error with exception details including full stack trace
     */
    public static void error(String message, Throwable throwable) {
        // Log the message with exception class and message
        logger.severe(Constants.LOG_ERROR_PREFIX + message + ": " + throwable.getClass().getName() + " - " + throwable.getMessage());
        
        // Capture and log the full stack trace through our logger (not stderr)
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        throwable.printStackTrace(pw);
        logger.severe(sw.toString());
    }
    
    /**
     * Log a debug message (only shown when logging level set to FINE or lower)
     */
    public static void debug(String message) {
        logger.fine(Constants.LOG_PREFIX + message);
    }
    
    /**
     * Log a debug message with formatting
     */
    public static void debug(String format, Object... args) {
        debug(String.format(format, args));
    }
    
    private ModLogger() {} // Prevent instantiation
}
