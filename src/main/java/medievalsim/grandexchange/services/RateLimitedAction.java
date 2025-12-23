package medievalsim.grandexchange.services;

/**
 * Enumerates the discrete Grand Exchange actions that enforce rate limits.
 */
public enum RateLimitedAction {
    SELL_CREATE,
    SELL_TOGGLE,
    BUY_CREATE,
    BUY_TOGGLE
}
