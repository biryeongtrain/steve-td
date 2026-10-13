package kim.biryeong.semiontd.music;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;

final class MusicPlaybackSchedule {
    private SemionMusicLibrary library;
    private final LongSupplier interTrackGapTicks;
    private final IntUnaryOperator nextTrackSelector;
    private final List<ScheduleSegment> schedule = new ArrayList<>();
    private final Set<Integer> playedTrackIndices = new HashSet<>();

    MusicPlaybackSchedule(SemionMusicLibrary library, LongSupplier interTrackGapTicks, IntUnaryOperator nextTrackSelector) {
        this.library = library;
        this.interTrackGapTicks = interTrackGapTicks;
        this.nextTrackSelector = nextTrackSelector;
    }

    void replaceLibrary(SemionMusicLibrary replacement) {
        library = Objects.requireNonNull(replacement, "replacement");
        resetTimeline();
        playedTrackIndices.clear();
    }

    void resetTimeline() {
        schedule.clear();
    }

    java.util.Optional<SemionMusicLibrary.TrackWindow> trackAt(long currentMusicTick) {
        if (library.isEmpty()) {
            return java.util.Optional.empty();
        }
        extendSchedule(currentMusicTick);
        int low = 0;
        int high = schedule.size();
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (schedule.get(middle).endTick() <= currentMusicTick) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        if (low == schedule.size()) {
            return java.util.Optional.empty();
        }
        ScheduleSegment segment = schedule.get(low);
        if (currentMusicTick < segment.startTick() || segment.track() == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new SemionMusicLibrary.TrackWindow(
                segment.track(),
                currentMusicTick - segment.startTick(),
                segment.startTick()
        ));
    }

    private void extendSchedule(long currentMusicTick) {
        if (schedule.isEmpty()) {
            int firstTrackIndex = firstTrackIndex();
            SemionMusicTrack first = library.tracks().get(firstTrackIndex);
            schedule.add(ScheduleSegment.track(firstTrackIndex, first, 0L));
            markTrackPlayed(firstTrackIndex);
        }
        while (schedule.getLast().endTick() <= currentMusicTick) {
            ScheduleSegment previous = schedule.getLast();
            if (previous.track() != null) {
                long gapTicks = clampInterTrackGap(interTrackGapTicks.getAsLong());
                schedule.add(ScheduleSegment.gap(previous.trackIndex(), previous.endTick(), gapTicks));
            } else {
                int nextTrackIndex = nextTrackIndexAfter(previous.trackIndex());
                SemionMusicTrack nextTrack = library.tracks().get(nextTrackIndex);
                schedule.add(ScheduleSegment.track(nextTrackIndex, nextTrack, previous.endTick()));
                markTrackPlayed(nextTrackIndex);
            }
        }
    }

    private int firstTrackIndex() {
        int trackCount = library.tracks().size();
        if (trackCount <= 1 || playedTrackIndices.isEmpty()) {
            return 0;
        }
        List<Integer> candidates = unplayedTrackIndices();
        if (candidates.isEmpty()) {
            playedTrackIndices.clear();
            candidates = unplayedTrackIndices();
        }
        int candidateIndex = Math.floorMod(nextTrackSelector.applyAsInt(candidates.size()), candidates.size());
        return candidates.get(candidateIndex);
    }

    private int nextTrackIndexAfter(int previousTrackIndex) {
        int trackCount = library.tracks().size();
        if (trackCount <= 1) {
            return 0;
        }

        List<Integer> candidates = unplayedTrackIndices();
        if (candidates.isEmpty()) {
            playedTrackIndices.clear();
            candidates = unplayedTrackIndices();
            candidates.remove(Integer.valueOf(previousTrackIndex));
        }
        int candidateIndex = Math.floorMod(nextTrackSelector.applyAsInt(candidates.size()), candidates.size());
        return candidates.get(candidateIndex);
    }

    private List<Integer> unplayedTrackIndices() {
        List<Integer> candidates = new ArrayList<>();
        for (int trackIndex = 0; trackIndex < library.tracks().size(); trackIndex++) {
            if (!playedTrackIndices.contains(trackIndex)) {
                candidates.add(trackIndex);
            }
        }
        return candidates;
    }

    private void markTrackPlayed(int trackIndex) {
        playedTrackIndices.add(trackIndex);
    }

    private static long clampInterTrackGap(long requestedTicks) {
        return Math.max(SemionMusicService.MIN_INTER_TRACK_GAP_TICKS, Math.min(SemionMusicService.MAX_INTER_TRACK_GAP_TICKS, requestedTicks));
    }

    private record ScheduleSegment(int trackIndex, SemionMusicTrack track, long startTick, long endTick) {
        private static ScheduleSegment track(int trackIndex, SemionMusicTrack track, long startTick) {
            return new ScheduleSegment(trackIndex, track, startTick, startTick + track.durationTicks());
        }

        private static ScheduleSegment gap(int previousTrackIndex, long startTick, long durationTicks) {
            return new ScheduleSegment(previousTrackIndex, null, startTick, startTick + durationTicks);
        }
    }
}
