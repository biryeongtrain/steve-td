package kim.biryeong.semiontd.music;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class MusicPlaybackScheduleTest {
    @Test
    void binaryLookupPreservesEveryTrackAndGapBoundaryIncludingHistoricalQueries() {
        SemionMusicTrack track = track("single", 40);
        AtomicInteger gaps = new AtomicInteger();
        MusicPlaybackSchedule schedule = new MusicPlaybackSchedule(new SemionMusicLibrary(List.of(track)),
                () -> { gaps.incrementAndGet(); return 100; }, bound -> fail("One track needs no random choice"));
        schedule.trackAt(140_039);
        int gapCalls = gaps.get();
        for (long tick = 140_039; tick >= -1; tick -= 1) {
            var actual = schedule.trackAt(tick);
            if (tick < 0 || tick % 140 >= 40) {
                assertTrue(actual.isEmpty(), "gap at " + tick);
            } else {
                var window = actual.orElseThrow();
                assertSame(track, window.track());
                assertEquals(tick / 140 * 140, window.startedAtTick());
                assertEquals(tick % 140, window.elapsedTicks());
            }
        }
        assertEquals(gapCalls, gaps.get(), "Past lookup must not consume future gaps");
    }

    @Test
    void skippedTimeConsumesSameShuffleAndGapSequenceAsSequentialTime() {
        var tracks = List.of(track("one", 7), track("two", 11), track("three", 13), track("four", 17));
        List<String> sequentialCalls = new ArrayList<>();
        List<String> skippedCalls = new ArrayList<>();
        MusicPlaybackSchedule sequential = schedule(tracks, sequentialCalls);
        MusicPlaybackSchedule skipped = schedule(tracks, skippedCalls);
        var expected = new ArrayList<java.util.Optional<SemionMusicLibrary.TrackWindow>>();
        for (long tick = 0; tick <= 15_000; tick++) {
            expected.add(sequential.trackAt(tick));
        }
        assertEquals(expected.getLast(), skipped.trackAt(15_000));
        assertEquals(sequentialCalls, skippedCalls);
        for (int tick = 15_000; tick >= 0; tick -= 31) {
            assertEquals(expected.get(tick), skipped.trackAt(tick), "historical tick " + tick);
        }
        assertEquals(sequentialCalls, skippedCalls, "Read-only history must not alter future RNG");
    }

    @Test
    void timelineResetRetainsPlayedCycleButLibraryReplacementClearsIt() {
        var first = track("first", 40);
        var second = track("second", 60);
        var third = track("third", 50);
        AtomicInteger choices = new AtomicInteger();
        MusicPlaybackSchedule schedule = new MusicPlaybackSchedule(new SemionMusicLibrary(List.of(first, second, third)),
                () -> 100, bound -> { choices.incrementAndGet(); return bound - 1; });
        assertSame(first, schedule.trackAt(0).orElseThrow().track());
        schedule.resetTimeline();
        assertSame(third, schedule.trackAt(0).orElseThrow().track());
        assertEquals(1, choices.get());
        schedule.replaceLibrary(new SemionMusicLibrary(List.of(first, second, third)));
        assertSame(first, schedule.trackAt(0).orElseThrow().track());
        assertEquals(1, choices.get());
        schedule.replaceLibrary(SemionMusicLibrary.empty());
        assertTrue(schedule.trackAt(1_000).isEmpty());
        assertEquals(1, choices.get());
    }

    @Test
    void sameTickPlayersShareTimelineWithoutChangingRestartGrace() {
        AtomicInteger gaps = new AtomicInteger();
        AtomicInteger choices = new AtomicInteger();
        SemionMusicService service = new SemionMusicService(
                new SemionMusicLibrary(List.of(track("first", 40), track("second", 60))),
                () -> { gaps.incrementAndGet(); return 100; },
                bound -> { choices.incrementAndGet(); return 0; });
        for (int player = 0; player < 64; player++) {
            UUID id = new UUID(0, player);
            var start = service.decisionFor(id, 140, true);
            assertEquals(SemionMusicService.PlaybackAction.START_TRACK, start.action());
            assertEquals("second", start.track().id());
            assertEquals(140, start.trackStartedAtTick());
        }
        assertEquals(1, gaps.get());
        assertEquals(1, choices.get());
        assertEquals(SemionMusicService.PlaybackAction.START_TRACK,
                service.decisionFor(new UUID(0, 65), 160, true).action());
        assertEquals(SemionMusicService.PlaybackAction.WAIT_FOR_NEXT_TRACK,
                service.decisionFor(new UUID(0, 66), 161, true).action());
        assertEquals(SemionMusicService.PlaybackAction.START_TRACK,
                service.decisionFor(new UUID(0, 66), 161, false).action());
    }

    private static MusicPlaybackSchedule schedule(List<SemionMusicTrack> tracks, List<String> calls) {
        AtomicInteger gaps = new AtomicInteger();
        AtomicInteger choices = new AtomicInteger();
        return new MusicPlaybackSchedule(new SemionMusicLibrary(tracks), () -> {
            int call = gaps.getAndIncrement();
            long value = switch (call % 3) { case 0 -> 1; case 1 -> 150; default -> 900; };
            calls.add("gap:" + value);
            return value;
        }, bound -> {
            int value = choices.getAndIncrement() % 2 == 0 ? -1 : 7;
            calls.add("choice:" + bound + ":" + value);
            return value;
        });
    }

    private static SemionMusicTrack track(String id, long duration) {
        return new SemionMusicTrack(id, Path.of(id + ".ogg"),
                Identifier.fromNamespaceAndPath("semion-td", "music." + id),
                Identifier.fromNamespaceAndPath("semion-td", "music/" + id), duration);
    }
}
