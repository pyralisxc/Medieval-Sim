package medievalsim.util;

/**
 * Time conversion constants for Medieval Sim.
 * 
 * <p>Provides compile-time constants for time unit conversions to eliminate
 * magic numbers and prevent typos in time-based calculations.</p>
 * 
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * // Calculate minutes from milliseconds
 * long minutes = elapsedMs / TimeConstants.MILLIS_PER_MINUTE;
 * 
 * // Calculate cooldown expiry
 * long cooldownExpiry = now + (5 * TimeConstants.MILLIS_PER_MINUTE);
 * 
 * // Calculate 24-hour cutoff
 * long cutoff = now - TimeConstants.MILLIS_PER_DAY;
 * }</pre>
 * 
 * @since 1.0
 */
public final class TimeConstants {
    
    /**
     * Private constructor - utility class should not be instantiated.
     */
    private TimeConstants() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
    
    // ===== MILLISECOND CONVERSIONS =====
    
    /**
     * Milliseconds in one second (1,000 ms).
     */
    public static final long MILLIS_PER_SECOND = 1000L;
    
    /**
     * Milliseconds in one minute (60,000 ms).
     */
    public static final long MILLIS_PER_MINUTE = 60_000L;
    
    /**
     * Milliseconds in one hour (3,600,000 ms).
     */
    public static final long MILLIS_PER_HOUR = 3_600_000L;
    
    /**
     * Milliseconds in one day (86,400,000 ms).
     */
    public static final long MILLIS_PER_DAY = 86_400_000L;
    
    // ===== SECOND CONVERSIONS =====
    
    /**
     * Seconds in one minute (60 s).
     */
    public static final long SECONDS_PER_MINUTE = 60L;
    
    /**
     * Seconds in one hour (3,600 s).
     */
    public static final long SECONDS_PER_HOUR = 3_600L;
    
    /**
     * Seconds in one day (86,400 s).
     */
    public static final long SECONDS_PER_DAY = 86_400L;
    
    // ===== MINUTE CONVERSIONS =====
    
    /**
     * Minutes in one hour (60 min).
     */
    public static final long MINUTES_PER_HOUR = 60L;
    
    /**
     * Minutes in one day (1,440 min).
     */
    public static final long MINUTES_PER_DAY = 1_440L;
    
    // ===== HOUR CONVERSIONS =====
    
    /**
     * Hours in one day (24 hr).
     */
    public static final long HOURS_PER_DAY = 24L;
    
    // ===== COMMON TIME PERIODS (in milliseconds) =====
    
    /**
     * 10 minutes in milliseconds (600,000 ms).
     * Common for cleanup intervals and short-term caching.
     */
    public static final long TEN_MINUTES_MS = 10 * MILLIS_PER_MINUTE;
    
    /**
     * 30 seconds in milliseconds (30,000 ms).
     * Common for cooldowns and retry intervals.
     */
    public static final long THIRTY_SECONDS_MS = 30 * MILLIS_PER_SECOND;
}
