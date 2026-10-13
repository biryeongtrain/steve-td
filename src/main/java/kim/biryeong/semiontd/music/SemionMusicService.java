package kim.biryeong.semiontd.music;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public final class SemionMusicService {
    public static final long RESTART_GRACE_TICKS = 20L;
    public static final long MIN_INTER_TRACK_GAP_TICKS = 5L * 20L;
    public static final long MAX_INTER_TRACK_GAP_TICKS = 10L * 20L;
    private static final long NANOS_PER_MUSIC_TICK = 50_000_000L;

    private SemionMusicLibrary library;
    private final MusicPlaybackSchedule playbackSchedule;
    private final Map<UUID, PlayerMusicState> playerStates = new HashMap<>();
    private long musicStartedAtNanos;
    private boolean active;

    public SemionMusicService(SemionMusicLibrary library) {
        this(library, SemionMusicService::randomInterTrackGapTicks);
    }

    public SemionMusicService(SemionMusicLibrary library, LongSupplier interTrackGapTicks) {
        this(library, interTrackGapTicks, bound -> ThreadLocalRandom.current().nextInt(bound));
    }

    public SemionMusicService(
            SemionMusicLibrary library,
            LongSupplier interTrackGapTicks,
            IntUnaryOperator nextTrackSelector
    ) {
        this.library = library;
        this.playbackSchedule = new MusicPlaybackSchedule(library, interTrackGapTicks, nextTrackSelector);
    }

    public static SemionMusicService disabled() {
        return new SemionMusicService(SemionMusicLibrary.empty());
    }

    public static long randomInterTrackGapTicks() {
        return ThreadLocalRandom.current().nextLong(MIN_INTER_TRACK_GAP_TICKS, MAX_INTER_TRACK_GAP_TICKS + 1L);
    }

    public SemionMusicLibrary library() {
        return library;
    }

    public void replaceLibrary(MinecraftServer server, SemionMusicLibrary replacement) {
        if (server != null) {
            stopAll(server);
        } else {
            playerStates.clear();
        }
        library = Objects.requireNonNull(replacement, "replacement");
        active = false;
        musicStartedAtNanos = 0L;
        playbackSchedule.replaceLibrary(replacement);
    }

    public void tick(MinecraftServer server, SemionGame activeGame, Collection<SemionGame> sandboxGames) {
        Set<UUID> targetPlayers = targetPlayers(activeGame, sandboxGames);
        if (targetPlayers.isEmpty()) {
            stopAll(server);
            active = false;
            musicStartedAtNanos = 0L;
            playbackSchedule.resetTimeline();
            return;
        }
        long now = System.nanoTime();
        if (!active) {
            active = true;
            musicStartedAtNanos = now;
            playerStates.clear();
            playbackSchedule.resetTimeline();
        }

        long currentTick = elapsedMusicTicks(musicStartedAtNanos, now);
        java.util.Optional<SemionMusicLibrary.TrackWindow> window = null;
        for (UUID playerId : targetPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                if (window == null) {
                    window = playbackSchedule.trackAt(currentTick);
                }
                ensurePlayback(player, window);
            }
        }
        stopPlayersOutsideTargets(server, targetPlayers);
    }

    static long elapsedMusicTicks(long startedAtNanos, long nowNanos) {
        return Math.max(0L, nowNanos - startedAtNanos) / NANOS_PER_MUSIC_TICK;
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
        return decisionFor(state, playbackSchedule.trackAt(currentMusicTick));
    }

    private void ensurePlayback(ServerPlayer player, java.util.Optional<SemionMusicLibrary.TrackWindow> window) {
        PlayerMusicState state = playerStates.computeIfAbsent(player.getUUID(), ignored -> new PlayerMusicState());
        PlaybackDecision decision = decisionFor(state, window);
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

    private PlaybackDecision decisionFor(PlayerMusicState state, java.util.Optional<SemionMusicLibrary.TrackWindow> window) {
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

    private boolean isMusicActive(SemionGame game) {
        return game != null
                && game.rosterLocked()
                && game.phase() != RoundPhase.WAITING
                && game.phase() != RoundPhase.ENDED
                && !library.isEmpty();
    }

    private Set<UUID> targetPlayers(SemionGame activeGame, Collection<SemionGame> sandboxGames) {
        Set<UUID> playerIds = new HashSet<>();
        addTargetPlayers(activeGame, playerIds);
        for (SemionGame sandboxGame : sandboxGames) {
            addTargetPlayers(sandboxGame, playerIds);
        }
        return playerIds;
    }

    private void addTargetPlayers(SemionGame game, Set<UUID> playerIds) {
        if (!isMusicActive(game)) {
            return;
        }
        playerIds.addAll(game.players().keySet());
        playerIds.addAll(game.matchSpectatorIds());
    }

    private void stopPlayersOutsideTargets(MinecraftServer server, Set<UUID> targetPlayers) {
        Iterator<Map.Entry<UUID, PlayerMusicState>> iterator = playerStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PlayerMusicState> entry = iterator.next();
            if (targetPlayers.contains(entry.getKey())) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && entry.getValue().playingEventId != null) {
                stopMusic(player, entry.getValue().playingEventId);
            }
            iterator.remove();
        }
    }

    private void stopAll(MinecraftServer server) {
        if (playerStates.isEmpty()) {
            return;
        }
        for (UUID playerId : playerStates.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.connection.send(new ClientboundStopSoundPacket(null, SoundSource.RECORDS));
            }
        }
        playerStates.clear();
    }

    private void playMusic(ServerPlayer player, SemionMusicTrack track) {
        kim.biryeong.semiontd.util.SemionPlayerPackets.playSound(player, SoundEvent.createVariableRangeEvent(track.eventId()), SoundSource.RECORDS, 1.0F, 1.0F);
    }

    private void stopMusic(ServerPlayer player, Identifier eventId) {
        player.connection.send(new ClientboundStopSoundPacket(eventId, SoundSource.RECORDS));
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
        private Identifier playingEventId;
        private long playingTrackStartedAtTick = -1L;
        private boolean clientStopped = true;
    }

}
