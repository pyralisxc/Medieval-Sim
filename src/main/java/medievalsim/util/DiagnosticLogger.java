package medievalsim.util;

import medievalsim.config.ModConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Diagnostic logging framework for Medieval Sim.
 * 
 * <p>Provides advanced logging capabilities for troubleshooting and performance analysis:</p>
 * <ul>
 *   <li>Method entry/exit tracing</li>
 *   <li>Performance timing</li>
 *   <li>Slow operation detection</li>
 *   <li>Resource usage tracking</li>
 * </ul>
 * 
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * // Enable verbose mode in ModConfig first
 * ModConfig.Debug.verboseLogging = true;
 * 
 * // Time an operation
 * int result = DiagnosticLogger.timeOperation("MatchingEngine.match", () -> {
 *     return performMatching();
 * });
 * 
 * // Log slow operation
 * long elapsed = stopwatch.elapsed();
 * DiagnosticLogger.logSlowOperation("GE_MATCHING", 100, elapsed);
 * 
 * // Track resource usage
 * DiagnosticLogger.logResourceUsage("ActiveOffers", activeOffers.size(), 10000);
 * }</pre>
 * 
 * @since 1.0
 */
public class DiagnosticLogger {
    
    /**
     * Private constructor - utility class should not be instantiated.
     */
    private DiagnosticLogger() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
    
    // Thread-local storage for nested method tracking
    private static final ThreadLocal<Map<String, Long>> methodStartTimes = 
        ThreadLocal.withInitial(HashMap::new);
    
    /**
     * Check if verbose diagnostic logging is enabled.
     * 
     * @return true if verbose logging enabled
     */
    private static boolean isVerboseEnabled() {
        try {
            return medievalsim.config.ModConfig.Logging.verboseDebug;
        } catch (Exception e) {
            // Fallback if config not initialized
            return false;
        }
    }
    
    // ===== METHOD TRACING =====
    
    /**
     * Log method entry (only if verbose mode enabled).
     * 
     * <p>Should be paired with {@link #logMethodExit(String, String)}.</p>
     * 
     * @param className Class name (use getClass().getSimpleName())
     * @param methodName Method name
     */
    public static void logMethodEntry(String className, String methodName) {
        if (!isVerboseEnabled()) {
            return;
        }
        
        String key = className + "." + methodName;
        methodStartTimes.get().put(key, System.currentTimeMillis());
        ModLogger.debug("[TRACE] → %s.%s()", className, methodName);
    }
    
    /**
     * Log method exit with execution time (only if verbose mode enabled).
     * 
     * @param className Class name (use getClass().getSimpleName())
     * @param methodName Method name
     */
    public static void logMethodExit(String className, String methodName) {
        if (!isVerboseEnabled()) {
            return;
        }
        
        String key = className + "." + methodName;
        Long startTime = methodStartTimes.get().remove(key);
        
        if (startTime != null) {
            long durationMs = System.currentTimeMillis() - startTime;
            ModLogger.debug("[TRACE] ← %s.%s() [%dms]", className, methodName, durationMs);
        } else {
            ModLogger.debug("[TRACE] ← %s.%s()", className, methodName);
        }
    }
    
    /**
     * Log method entry with parameters (only if verbose mode enabled).
     * 
     * @param className Class name
     * @param methodName Method name
     * @param paramInfo Parameter information string
     */
    public static void logMethodEntryWithParams(String className, String methodName, String paramInfo) {
        if (!isVerboseEnabled()) {
            return;
        }
        
        String key = className + "." + methodName;
        methodStartTimes.get().put(key, System.currentTimeMillis());
        ModLogger.debug("[TRACE] → %s.%s(%s)", className, methodName, paramInfo);
    }
    
    // ===== PERFORMANCE TIMING =====
    
    /**
     * Execute an operation and log its execution time.
     * 
     * <p>Always logs performance data (not gated by verbose mode).</p>
     * 
     * @param operationName Operation name for logging
     * @param operation Operation to execute
     * @param <T> Return type
     * @return Result of operation
     */
    public static <T> T timeOperation(String operationName, Supplier<T> operation) {
        long start = System.currentTimeMillis();
        try {
            T result = operation.get();
            long elapsed = System.currentTimeMillis() - start;
            ModLogger.debug("[PERF] %s completed in %dms", operationName, elapsed);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            ModLogger.error("[PERF] %s failed after %dms: %s", operationName, elapsed, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Execute an operation without return value and log its execution time.
     * 
     * @param operationName Operation name for logging
     * @param operation Operation to execute
     */
    public static void timeOperation(String operationName, Runnable operation) {
        long start = System.currentTimeMillis();
        try {
            operation.run();
            long elapsed = System.currentTimeMillis() - start;
            ModLogger.debug("[PERF] %s completed in %dms", operationName, elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            ModLogger.error("[PERF] %s failed after %dms: %s", operationName, elapsed, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Log a slow operation that exceeded threshold.
     * 
     * <p>Always logs slow operations (not gated by verbose mode).</p>
     * 
     * @param operationName Operation name
     * @param thresholdMs Threshold in milliseconds
     * @param actualMs Actual time taken
     */
    public static void logSlowOperation(String operationName, long thresholdMs, long actualMs) {
        if (actualMs > thresholdMs) {
            ModLogger.warn("[SLOW] %s took %dms (threshold: %dms)", 
                operationName, actualMs, thresholdMs);
        }
    }
    
    // ===== RESOURCE TRACKING =====
    
    /**
     * Log resource usage with percentage utilization.
     * 
     * <p>Only logs if verbose mode enabled OR usage exceeds 80%.</p>
     * 
     * @param resourceName Resource name (e.g., "ActiveOffers", "CachedTrades")
     * @param used Current usage
     * @param total Total capacity
     */
    public static void logResourceUsage(String resourceName, int used, int total) {
        if (total <= 0) {
            ModLogger.warn("[RESOURCE] %s: Invalid total capacity %d", resourceName, total);
            return;
        }
        
        double percentUsed = (used * 100.0) / total;
        
        // Always log if usage is high (>80%)
        if (percentUsed > 80.0) {
            ModLogger.warn("[RESOURCE] %s: %.1f%% used (%d/%d)", 
                resourceName, percentUsed, used, total);
        } else if (isVerboseEnabled()) {
            ModLogger.debug("[RESOURCE] %s: %.1f%% used (%d/%d)", 
                resourceName, percentUsed, used, total);
        }
    }
    
    /**
     * Log resource usage with context information.
     * 
     * @param resourceName Resource name
     * @param used Current usage
     * @param total Total capacity
     * @param context Additional context (e.g., "player123", "surface level")
     */
    public static void logResourceUsageWithContext(String resourceName, int used, int total, String context) {
        if (total <= 0) {
            return;
        }
        
        double percentUsed = (used * 100.0) / total;
        
        if (percentUsed > 80.0) {
            ModLogger.warn("[RESOURCE] %s [%s]: %.1f%% used (%d/%d)", 
                resourceName, context, percentUsed, used, total);
        } else if (isVerboseEnabled()) {
            ModLogger.debug("[RESOURCE] %s [%s]: %.1f%% used (%d/%d)", 
                resourceName, context, percentUsed, used, total);
        }
    }
    
    // ===== STATE TRACKING =====
    
    /**
     * Log state transition for debugging state machines.
     * 
     * <p>Only logs if verbose mode enabled.</p>
     * 
     * @param entityName Entity undergoing state change
     * @param fromState Previous state
     * @param toState New state
     */
    public static void logStateTransition(String entityName, String fromState, String toState) {
        if (!isVerboseEnabled()) {
            return;
        }
        
        ModLogger.debug("[STATE] %s: %s → %s", entityName, fromState, toState);
    }
    
    /**
     * Log state transition with reason/trigger.
     * 
     * @param entityName Entity undergoing state change
     * @param fromState Previous state
     * @param toState New state
     * @param reason Reason for transition
     */
    public static void logStateTransition(String entityName, String fromState, String toState, String reason) {
        if (!isVerboseEnabled()) {
            return;
        }
        
        ModLogger.debug("[STATE] %s: %s → %s (reason: %s)", entityName, fromState, toState, reason);
    }
}
