package medievalsim.grandexchange.net;

/**
 * Outcome categories for sell-offer actions. These codes are shared between
 * server responses, client packets, and UI feedback so messages can be
 * localized consistently.
 */
public enum SellActionResultCode {
    SUCCESS,
    INVALID_SLOT,
    NO_ITEM_IN_SLOT,
    NO_AVAILABLE_SLOT,
    PRICE_OUT_OF_RANGE,
    MAX_ACTIVE_REACHED,
    INVALID_ITEM_STATE,
    NO_OFFER_IN_SLOT,
    OFFER_STATE_LOCKED,
    TOGGLE_COOLDOWN,
    RATE_LIMITED,
    SERVER_REJECTED,
    CLIENT_NOT_READY,
    UNKNOWN_FAILURE;

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
