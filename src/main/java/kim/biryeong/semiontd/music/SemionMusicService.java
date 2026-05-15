package kim.biryeong.semiontd.music;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public final class SemionMusicService {
    public static final long RESTART_GRACE_TICKS = 20L;
    public static final long MIN_INTER_TRACK_GAP_TICKS = 5L * 20L;
    public static final long MAX_INTER_TRACK_GAP_TICKS = 10L * 20L;

    private final SemionMusicLibrary library;
    private final LongSupplier interTrackGapTicks;
    private final Map<UUID, PlayerMusicState> playerStates = new HashMap<>();
    private final List<ScheduleSegment> schedule = new ArrayList<>();
    private long musicTick;
    private boolean active;

    public SemionMusicService(SemionMusicLibrary library) {
        this(library, SemionMusicService::randomInterTrackGapTicks);
    }

    public SemionMusicService(SemionMusicLibrary library, LongSupplier interTrackGapTicks) {
        this.library = library;
        this.interTrackGapTicks = interTrackGapTicks;
    }

    public static SemionMusicService disabled() {
        return new SemionMusicService(SemionMusicLibrary.empty());
    }

    public static long randomInterTrackGapTicks() {
        return ThreadLocalRandom.current().nextLong(MIN_INTER_TRACK_GAP_TICKS, MAX_INTER_TRACK_GAP_TICKS + 1L);
    }

    public void tick(MinecraftServer server, SemionGame game) {
        if (!isMusicActive(game)) {
            stopAll(server);
            active = false;
            musicTick = 0L;
            schedule.clear();
            return;
        }
        if (!active) {
            active = true;
            musicTick = 0L;
            playerStates.clear();
            schedule.clear();
        }

        long currentTick = musicTick;
        Set<UUID> targetPlayers = targetPlayers(game);
        for (UUID playerId : targetPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                ensurePlayback(player, currentTick);
            }
        }
        playerStates.keySet().removeIf(playerId -> !targetPlayers.contains(playerId));
        musicTick++;
    }

    public void handlePlayerJoin(ServerPlayer player) {
        markClientStopped(player.getUUID());
    }

    public void handlePlayerWorldChanged(ServerPlayer player) {
        markClientStopped(player.getUUID());
    }

    public void markClientStopped(UUID playerId) {
        playerStates.computeIfAbsent(playerId, ignored -> new PlayerMusicState()).clientStopped = true;
    }

    public PlaybackDecision decisionFor(UUID playerId, long currentMusicTick, boolean clientStopped) {
        PlayerMusicState state = playerStates.computeIfAbsent(playerId, ignored -> new PlayerMusicState());
        state.clientStopped = clientStopped;
        return decisionFor(state, currentMusicTick);
    }

    private void ensurePlayback(ServerPlayer player, long currentMusicTick) {
        PlayerMusicState state = playerStates.computeIfAbsent(player.getUUID(), ignored -> new PlayerMusicState());
        PlaybackDecision decision = decisionFor(state, currentMusicTick);
        if (decision.action() == PlaybackAction.WAIT_FOR_NEXT_TRACK || decision.track() == null) {
            return;
        }
        if (state.playingEventId != null && !state.playingEventId.equals(decision.track().eventId())) {
            stopMusic(player, state.playingEventId);
        }
        playMusic(player, decision.track());
        state.playingEventId = decision.track().eventId();
        state.playingTrackStartedAtTick = decision.trackStartedAtTick();
        state.clientStopped = false;
    }

    private PlaybackDecision decisionFor(PlayerMusicState state, long currentMusicTick) {
        java.util.Optional<SemionMusicLibrary.TrackWindow> window = trackAt(currentMusicTick);
        if (window.isEmpty()) {
            return new PlaybackDecision(PlaybackAction.WAIT_FOR_NEXT_TRACK, null, 0L);
        }
        SemionMusicTrack track = window.get().track();
        if (!state.clientStopped
                && track.eventId().equals(state.playingEventId)
                && state.playingTrackStartedAtTick == window.get().startedAtTick()) {
            return PlaybackDecision.none();
        }
        if (state.clientStopped && window.get().elapsedTicks() > RESTART_GRACE_TICKS) {
            return new PlaybackDecision(PlaybackAction.WAIT_FOR_NEXT_TRACK, track, window.get().startedAtTick());
        }
        return new PlaybackDecision(PlaybackAction.START_TRACK, track, window.get().startedAtTick());
    }

    private java.util.Optional<SemionMusicLibrary.TrackWindow> trackAt(long currentMusicTick) {
        if (library.isEmpty()) {
            return java.util.Optional.empty();
        }
        extendSchedule(currentMusicTick);
        for (ScheduleSegment segment : schedule) {
            if (currentMusicTick >= segment.startTick() && currentMusicTick < segment.endTick()) {
                if (segment.track() == null) {
                    return java.util.Optional.empty();
                }
                return java.util.Optional.of(new SemionMusicLibrary.TrackWindow(
                        segment.track(),
                        currentMusicTick - segment.startTick(),
                        segment.startTick()
                ));
            }
        }
        return java.util.Optional.empty();
    }

    private void extendSchedule(long currentMusicTick) {
        if (schedule.isEmpty()) {
            SemionMusicTrack first = library.tracks().getFirst();
            schedule.add(ScheduleSegment.track(0, first, 0L));
        }
        while (schedule.getLast().endTick() <= currentMusicTick) {
            ScheduleSegment previous = schedule.getLast();
            if (previous.track() != null) {
                long gapTicks = clampInterTrackGap(interTrackGapTicks.getAsLong());
                schedule.add(ScheduleSegment.gap(previous.trackIndex(), previous.endTick(), gapTicks));
            } else {
                int nextTrackIndex = (previous.trackIndex() + 1) % library.tracks().size();
                SemionMusicTrack nextTrack = library.tracks().get(nextTrackIndex);
                schedule.add(ScheduleSegment.track(nextTrackIndex, nextTrack, previous.endTick()));
            }
        }
    }

    private static long clampInterTrackGap(long requestedTicks) {
        return Math.max(MIN_INTER_TRACK_GAP_TICKS, Math.min(MAX_INTER_TRACK_GAP_TICKS, requestedTicks));
    }

    private boolean isMusicActive(SemionGame game) {
        return game != null
                && game.rosterLocked()
                && game.phase() != RoundPhase.WAITING
                && game.phase() != RoundPhase.ENDED
                && !library.isEmpty();
    }

    private Set<UUID> targetPlayers(SemionGame game) {
        Set<UUID> playerIds = new HashSet<>(game.players().keySet());
        playerIds.addAll(game.matchSpectatorIds());
        return playerIds;
    }

    private void stopAll(MinecraftServer server) {
        if (playerStates.isEmpty()) {
            return;
        }
        for (UUID playerId : playerStates.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.connection.send(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
            }
        }
        playerStates.clear();
    }

    private void playMusic(ServerPlayer player, SemionMusicTrack track) {
        player.playNotifySound(SoundEvent.createVariableRangeEvent(track.eventId()), SoundSource.MUSIC, 1.0F, 1.0F);
    }

    private void stopMusic(ServerPlayer player, ResourceLocation eventId) {
        player.connection.send(new ClientboundStopSoundPacket(eventId, SoundSource.MUSIC));
    }

    public record PlaybackDecision(PlaybackAction action, SemionMusicTrack track, long trackStartedAtTick) {
        public static PlaybackDecision none() {
            return new PlaybackDecision(PlaybackAction.NONE, null, 0L);
        }
    }

    public enum PlaybackAction {
        NONE,
        START_TRACK,
        WAIT_FOR_NEXT_TRACK
    }

    private static final class PlayerMusicState {
        private ResourceLocation playingEventId;
        private long playingTrackStartedAtTick = -1L;
        private boolean clientStopped = true;
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
