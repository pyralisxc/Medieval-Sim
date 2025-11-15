package medievalsim.zones.events;

import medievalsim.util.ModLogger;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central event bus for zone-related events.
 * 
 * Provides thread-safe event publishing and subscription using the Observer pattern.
 * Events are delivered to listeners in registration order on the same thread that
 * fires the event, ensuring predictable execution order.
 * 
 * Features:
 * - Thread-safe listener registration/removal
 * - Event statistics and debugging support
 * - Graceful error handling (failing listeners don't break event delivery)
 * - Optional event queuing for batch processing
 */
public class ZoneEventBus {
    private static final ZoneEventBus INSTANCE = new ZoneEventBus();
    
    // Thread-safe listener storage
    private final CopyOnWriteArrayList<ZoneEventListener> listeners;
    
    // Event statistics
    private final AtomicLong totalEventsPublished;
    private final AtomicLong totalListenerErrors;
    
    // Optional event queue for batch processing
    private final ConcurrentLinkedQueue<ZoneEvent> eventQueue;
    private volatile boolean batchModeEnabled = false;
    
    private ZoneEventBus() {
        this.listeners = new CopyOnWriteArrayList<>();
        this.totalEventsPublished = new AtomicLong(0);
        this.totalListenerErrors = new AtomicLong(0);
        this.eventQueue = new ConcurrentLinkedQueue<>();
    }
    
    /**
     * Get the singleton instance of the event bus.
     */
    public static ZoneEventBus getInstance() {
        return INSTANCE;
    }
    
    /**
     * Register a zone event listener.
     * 
     * @param listener The listener to register
     */
    public void register(ZoneEventListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        
        listeners.add(listener);
        ModLogger.debug("Registered zone event listener: %s (total: %d)", 
            listener.getClass().getSimpleName(), listeners.size());
    }
    
    /**
     * Unregister a zone event listener.
     * 
     * @param listener The listener to unregister
     * @return true if the listener was removed, false if it wasn't registered
     */
    public boolean unregister(ZoneEventListener listener) {
        boolean removed = listeners.remove(listener);
        if (removed) {
            ModLogger.debug("Unregistered zone event listener: %s (total: %d)", 
                listener.getClass().getSimpleName(), listeners.size());
        }
        return removed;
    }
    
    /**
     * Publish a zone event to all registered listeners.
     * 
     * @param event The event to publish
     */
    public void publish(ZoneEvent event) {
        if (event == null) {
            ModLogger.warn("Attempted to publish null zone event");
            return;
        }
        
        totalEventsPublished.incrementAndGet();
        
        if (batchModeEnabled) {
            eventQueue.offer(event);
            return;
        }
        
        deliverEvent(event);
    }
    
    /**
     * Deliver an event to all listeners immediately.
     */
    private void deliverEvent(ZoneEvent event) {
        ModLogger.debug("Publishing zone event: %s", event.toString());
        
        for (ZoneEventListener listener : listeners) {
            try {
                listener.onZoneEvent(event);
            } catch (Exception e) {
                totalListenerErrors.incrementAndGet();
                ModLogger.error("Error in zone event listener %s: %s", 
                    listener.getClass().getSimpleName(), e.getMessage());
                // Continue processing other listeners
            }
        }
    }
    
    /**
     * Enable batch mode - events will be queued instead of delivered immediately.
     * Call flushEvents() to process the queue.
     */
    public void enableBatchMode() {
        batchModeEnabled = true;
        ModLogger.debug("Zone event batch mode enabled");
    }
    
    /**
     * Disable batch mode - events will be delivered immediately.
     */
    public void disableBatchMode() {
        batchModeEnabled = false;
        ModLogger.debug("Zone event batch mode disabled");
    }
    
    /**
     * Process all queued events when in batch mode.
     * 
     * @return Number of events processed
     */
    public int flushEvents() {
        int processed = 0;
        ZoneEvent event;
        
        while ((event = eventQueue.poll()) != null) {
            deliverEvent(event);
            processed++;
        }
        
        if (processed > 0) {
            ModLogger.debug("Flushed %d queued zone events", processed);
        }
        
        return processed;
    }
    
    /**
     * Get the number of registered listeners.
     */
    public int getListenerCount() {
        return listeners.size();
    }
    
    /**
     * Get the total number of events published since startup.
     */
    public long getTotalEventsPublished() {
        return totalEventsPublished.get();
    }
    
    /**
     * Get the total number of listener errors since startup.
     */
    public long getTotalListenerErrors() {
        return totalListenerErrors.get();
    }
    
    /**
     * Get the number of queued events (when in batch mode).
     */
    public int getQueuedEventCount() {
        return eventQueue.size();
    }
    
    /**
     * Clear all queued events without processing them.
     */
    public void clearQueue() {
        int cleared = eventQueue.size();
        eventQueue.clear();
        if (cleared > 0) {
            ModLogger.debug("Cleared %d queued zone events", cleared);
        }
    }
    
    /**
     * Get debugging information about the event bus.
     */
    public String getDebugInfo() {
        return String.format("ZoneEventBus[listeners=%d, published=%d, errors=%d, queued=%d, batchMode=%s]",
            getListenerCount(), getTotalEventsPublished(), getTotalListenerErrors(), 
            getQueuedEventCount(), batchModeEnabled);
    }
    
    /**
     * Convenience method to register a simple lambda listener.
     */
    public static void listen(ZoneEventListener listener) {
        getInstance().register(listener);
    }
    
    /**
     * Convenience method to publish an event.
     */
    public static void fire(ZoneEvent event) {
        getInstance().publish(event);
    }
}