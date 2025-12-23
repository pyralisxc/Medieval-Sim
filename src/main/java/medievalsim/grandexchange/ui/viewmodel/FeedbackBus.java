package medievalsim.grandexchange.ui.viewmodel;

import medievalsim.grandexchange.net.GEFeedbackChannel;
import medievalsim.grandexchange.net.GEFeedbackLevel;

import java.util.EnumMap;
import java.util.Map;

/**
 * Simple message bus for displaying feedback messages in the GE UI.
 * Each channel (MARKET, BUY, SELL, COLLECTION) can have one active message.
 */
public final class FeedbackBus {
    private final Map<GEFeedbackChannel, FeedbackMessage> messages = new EnumMap<>(GEFeedbackChannel.class);

    public void post(GEFeedbackChannel channel, String message, boolean isError) {
        post(channel, message, isError ? GEFeedbackLevel.ERROR : GEFeedbackLevel.INFO);
    }

    public void post(GEFeedbackChannel channel, String message, GEFeedbackLevel level) {
        if (channel == null) {
            return;
        }
        if (message == null || message.isBlank()) {
            messages.remove(channel);
            return;
        }
        GEFeedbackLevel resolvedLevel = level == null ? GEFeedbackLevel.INFO : level;
        messages.put(channel, new FeedbackMessage(message, resolvedLevel, System.currentTimeMillis()));
    }

    public void post(GEFeedbackChannel channel, String message, GEFeedbackLevel level, long timestamp) {
        if (channel == null) {
            return;
        }
        if (message == null || message.isBlank()) {
            messages.remove(channel);
            return;
        }
        GEFeedbackLevel resolvedLevel = level == null ? GEFeedbackLevel.INFO : level;
        long resolvedTimestamp = timestamp <= 0 ? System.currentTimeMillis() : timestamp;
        messages.put(channel, new FeedbackMessage(message, resolvedLevel, resolvedTimestamp));
    }

    public FeedbackMessage peek(GEFeedbackChannel channel) {
        if (channel == null) {
            return null;
        }
        return messages.get(channel);
    }

    public void clear(GEFeedbackChannel channel) {
        if (channel != null) {
            messages.remove(channel);
        }
    }

    public record FeedbackMessage(String message, GEFeedbackLevel level, long timestamp) {
    }
}
