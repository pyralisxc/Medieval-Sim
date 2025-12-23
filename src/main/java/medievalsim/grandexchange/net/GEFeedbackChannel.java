package medievalsim.grandexchange.net;

/**
 * Identifies the UI surface that should display a feedback message.
 * Channels directly map to individual Grand Exchange tabs so the
 * client can scope feedback without clobbering messages for other tabs.
 */
public enum GEFeedbackChannel {
    MARKET,
    BUY,
    SELL,
    COLLECTION,
    HISTORY,
    DEFAULTS
}
