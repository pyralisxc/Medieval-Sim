package medievalsim.ui.helpers;

/**
 * Encapsulates debounce state for slider updates in the Admin Tools HUD.
 * Combines timer and pending action into a single object for cleaner code.
 */
public class SliderDebounceState {
    private long lastUpdateTime;
    private Runnable pendingUpdate;

    public SliderDebounceState() {
        this.lastUpdateTime = 0L;
        this.pendingUpdate = null;
    }

    /**
     * Get the timestamp of the last update.
     */
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    /**
     * Set the timestamp of the last update.
     */
    public void setLastUpdateTime(long timestamp) {
        this.lastUpdateTime = timestamp;
    }

    /**
     * Get the pending update action.
     */
    public Runnable getPendingUpdate() {
        return pendingUpdate;
    }

    /**
     * Set the pending update action.
     */
    public void setPendingUpdate(Runnable update) {
        this.pendingUpdate = update;
    }

    /**
     * Check if there's a pending update.
     */
    public boolean hasPendingUpdate() {
        return pendingUpdate != null;
    }

    /**
     * Clear the pending update.
     */
    public void clearPendingUpdate() {
        this.pendingUpdate = null;
    }

    /**
     * Check if enough time has passed since last update.
     */
    public boolean shouldDebounce(long currentTime, long debounceMillis) {
        return (currentTime - lastUpdateTime) < debounceMillis;
    }
}
