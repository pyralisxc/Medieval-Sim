package medievalsim.grandexchange.services;

/**
 * Immutable snapshot of a player's remaining cooldown for a specific action.
 */
public record RateLimitStatus(RateLimitedAction action, float remainingSeconds) {
    public RateLimitStatus {
        remainingSeconds = Math.max(0f, remainingSeconds);
    }

    public boolean isActive() {
        return remainingSeconds > 0f;
    }

    public static RateLimitStatus inactive(RateLimitedAction action) {
        return new RateLimitStatus(action, 0f);
    }
}
