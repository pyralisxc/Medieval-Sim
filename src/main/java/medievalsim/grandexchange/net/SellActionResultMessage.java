package medievalsim.grandexchange.net;

import java.util.Objects;

/**
 * Lightweight payload for communicating sell action outcomes from the server
 * to the active UI form. Mirrors the data carried by PacketGESellActionResult.
 */
public final class SellActionResultMessage {
    private final SellActionType action;
    private final SellActionResultCode result;
    private final int slotIndex;
    private final String message;
    private final float cooldownSeconds;

    public SellActionResultMessage(SellActionType action,
                                   SellActionResultCode result,
                                   int slotIndex,
                                   String message,
                                   float cooldownSeconds) {
        this.action = Objects.requireNonNull(action, "action");
        this.result = Objects.requireNonNull(result, "result");
        this.slotIndex = slotIndex;
        this.message = message == null ? "" : message;
        this.cooldownSeconds = cooldownSeconds;
    }

    public SellActionType getAction() {
        return action;
    }

    public SellActionResultCode getResult() {
        return result;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public String getMessage() {
        return message;
    }

    public float getCooldownSeconds() {
        return cooldownSeconds;
    }

    public boolean isSuccess() {
        return result.isSuccess();
    }
}
