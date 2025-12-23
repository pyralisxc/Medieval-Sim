package medievalsim.grandexchange.ui.viewmodel;

import medievalsim.grandexchange.model.snapshot.HistoryDeltaPayload;
import medievalsim.grandexchange.ui.viewmodel.HistoryTabState;
import medievalsim.grandexchange.model.snapshot.HistoryEntrySnapshot;
import medievalsim.grandexchange.model.snapshot.HistoryTabSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GrandExchangeHistoryTabStateTest {

    @Test
    void applySnapshotTreatsFirstSyncAsSeen() {
        HistoryTabState state = new HistoryTabState();
        List<HistoryEntrySnapshot> entries = List.of(
            entryWithTimestamp(120L),
            entryWithTimestamp(90L)
        );

        state.applySnapshot(snapshot(entries, 0L));

        assertEquals(120L, state.getLatestEntryTimestamp(), "Latest entry should be tracked");
        assertEquals(120L, state.getLastViewedTimestamp(), "First snapshot should mark content as read");
        assertFalse(state.hasUnseenEntries(), "Badge should stay hidden on first open");
    }

    @Test
    void applyDeltaRespectsServerBaselineAndUnseenCounts() {
        HistoryTabState state = new HistoryTabState();
        state.applySnapshot(snapshot(List.of(
            entryWithTimestamp(40L),
            entryWithTimestamp(60L),
            entryWithTimestamp(70L)
        ), 50L));
        assertEquals(2, state.getUnseenCount(), "Entries newer than the server baseline should be unseen");

        HistoryDeltaPayload delta = new HistoryDeltaPayload(
            99L,
            List.of(entryWithTimestamp(90L), entryWithTimestamp(120L)),
            0,
            0,
            0,
            0,
            0,
            0,
            120L,
            70L
        );
        state.applyDelta(delta);

        assertEquals(70L, state.getLastViewedTimestamp(), "Server baseline should raise client acknowledgement");
        assertEquals(120L, state.getLatestEntryTimestamp(), "Delta timestamps should update latest entry");
        assertEquals(2, state.getUnseenCount(), "Only entries newer than the baseline should have badges");
    }

    @Test
    void applyDeltaIgnoresDuplicateTimestamps() {
        HistoryTabState state = new HistoryTabState();
        state.applySnapshot(snapshot(List.of(
            entryWithTimestamp(200L),
            entryWithTimestamp(150L)
        ), 200L));

        HistoryDeltaPayload duplicateDelta = new HistoryDeltaPayload(
            99L,
            List.of(entryWithTimestamp(200L), entryWithTimestamp(250L)),
            0,
            0,
            0,
            0,
            0,
            0,
            250L,
            200L
        );

        state.applyDelta(duplicateDelta);

        assertEquals(250L, state.getLatestEntryTimestamp(), "Newest entry should be preserved");
        assertEquals(1, state.getUnseenCount(), "Duplicate timestamps should not inflate unseen count");
        assertEquals(200L, state.getLastViewedTimestamp(), "Baseline should remain at the acknowledged timestamp");
    }

    @Test
    void synchronizeBadgeNeverDecreasesLatestTimestamp() {
        HistoryTabState state = new HistoryTabState();
        state.applySnapshot(snapshot(List.of(entryWithTimestamp(500L)), 500L));

        state.synchronizeBadge(5, 300L, 250L);

        assertEquals(500L, state.getLatestEntryTimestamp(), "Incoming badge timestamps must not roll back client state");
        assertEquals(500L, state.getLastViewedTimestamp(), "Client acknowledgement should never regress when badge baselines drop");
        assertEquals(5, state.getUnseenCount(), "Badge count always mirrors payload regardless of timestamp");
    }

    @Test
    void markEntriesSeenClearsUnseenAfterFreshDelta() {
        HistoryTabState state = new HistoryTabState();
        state.applySnapshot(snapshot(List.of(entryWithTimestamp(100L)), 50L));

        HistoryDeltaPayload delta = new HistoryDeltaPayload(
            99L,
            List.of(entryWithTimestamp(125L)),
            0,
            0,
            0,
            0,
            0,
            0,
            125L,
            75L
        );
        state.applyDelta(delta);
        assertTrue(state.hasUnseenEntries(), "Delta should introduce unseen entries");

        state.markEntriesSeen();

        assertFalse(state.hasUnseenEntries(), "Marking entries as seen should clear the badge");
        assertEquals(125L, state.getLastViewedTimestamp(), "Acknowledge should advance to the newest timestamp");
    }

    @Test
    void synchronizeBadgeAlignsClientWithServerCounts() {
        HistoryTabState state = new HistoryTabState();
        state.applySnapshot(snapshot(List.of(), 0L));

        state.synchronizeBadge(3, 200L, 150L);

        assertEquals(3, state.getUnseenCount(), "Badge count should match server payload");
        assertEquals(150L, state.getLastViewedTimestamp(), "Server baseline should advance acknowledgement");
        assertEquals(200L, state.getLatestEntryTimestamp(), "Latest timestamp should keep the highest server value");

        state.markEntriesSeen();

        assertEquals(200L, state.getLastViewedTimestamp(), "Acknowledgement should cover the latest timestamp");
        assertFalse(state.hasUnseenEntries(), "Badge should clear after marking entries as seen");
    }

    private static HistoryTabSnapshot snapshot(List<HistoryEntrySnapshot> entries, long baseline) {
        return new HistoryTabSnapshot(
            42L,
            entries,
            0,
            0,
            0,
            0,
            0,
            0,
            baseline
        );
    }

    private static HistoryEntrySnapshot entryWithTimestamp(long timestamp) {
        return new HistoryEntrySnapshot(
            "medievalsim:test:item",
            5,
            10,
            50,
            false,
            "buyer",
            timestamp,
            true  // isSale
        );
    }
}

