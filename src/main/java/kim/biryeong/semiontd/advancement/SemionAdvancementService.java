package kim.biryeong.semiontd.advancement;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.PlayerMatchStatsSnapshot;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SemionAdvancementService {
    public static final ResourceLocation TEAM_GAP_GG = id("team_gap_gg");
    public static final ResourceLocation CLUTCH = id("clutch");
    public static final ResourceLocation WHY_SO_FAST = id("why_so_fast");
    public static final ResourceLocation BALANCE_BREAKING = id("balance_breaking");
    public static final ResourceLocation BLOCK_20K_THREAT = id("block_20k_threat");
    public static final ResourceLocation SEND_10K_THREAT = id("send_10k_threat");
    public static final ResourceLocation PERFECT_DEFENSE_WIN = id("perfect_defense_win");
    public static final ResourceLocation DREAM_TEAM = id("dream_team");
    public static final ResourceLocation OH_LUCKY = id("oh_lucky");
    public static final ResourceLocation UNDERDOG = id("underdog");
    public static final ResourceLocation NEWBIE_EXIT = id("newbie_exit");
    public static final ResourceLocation VETERAN_100 = id("veteran_100");
    public static final Set<ResourceLocation> IDS = Set.of(
            TEAM_GAP_GG,
            CLUTCH,
            WHY_SO_FAST,
            BALANCE_BREAKING,
            BLOCK_20K_THREAT,
            SEND_10K_THREAT,
            PERFECT_DEFENSE_WIN,
            DREAM_TEAM,
            OH_LUCKY,
            UNDERDOG,
            NEWBIE_EXIT,
            VETERAN_100
    );

    private static final double RECEIVED_THREAT_TARGET = 20_000.0;
    private static final double SENT_THREAT_TARGET = 10_000.0;
    private final Map<UUID, PlayerMatchStatsSnapshot> roundStartStats = new HashMap<>();
    private final Map<UUID, Map<UUID, Double>> sentThreatByTarget = new HashMap<>();
    private final Set<UUID> attemptedDefensePlayers = new LinkedHashSet<>();
    private final Set<UUID> failedDefensePlayers = new LinkedHashSet<>();

    public void onMatchStarted(MinecraftServer server, SemionGame game) {
        roundStartStats.clear();
        sentThreatByTarget.clear();
        attemptedDefensePlayers.clear();
        failedDefensePlayers.clear();
        if (!eligible(game)) {
            return;
        }
        for (UUID playerId : game.players().keySet()) {
            award(server, playerId, NEWBIE_EXIT, "seen");
        }
    }

    public void onRoundStarted(MinecraftServer server, SemionGame game) {
        roundStartStats.clear();
        if (!eligible(game)) {
            return;
        }
        for (SemionPlayer player : game.players().values()) {
            roundStartStats.put(player.uuid(), player.matchStats().snapshot(player.economy().income()));
        }
        if (game.currentRound() == 40) {
            awardAll(server, game, BALANCE_BREAKING);
        }
    }

    public void recordIncomeSent(SemionGame game, UUID senderId, UUID targetId, double threat) {
        if (!eligible(game) || senderId == null || targetId == null || threat <= 0.0) {
            return;
        }
        sentThreatByTarget.computeIfAbsent(senderId, ignored -> new HashMap<>())
                .merge(targetId, threat, Double::sum);
    }

    public void tick(MinecraftServer server, SemionGame game) {
        if (!eligible(game)) {
            return;
        }
        var iterator = sentThreatByTarget.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Map<UUID, Double>> entry = iterator.next();
            if (entry.getValue().values().stream().anyMatch(threat -> threat >= SENT_THREAT_TARGET)) {
                award(server, entry.getKey(), SEND_10K_THREAT);
                iterator.remove();
            }
        }
    }

    public void onTeamEliminated(MinecraftServer server, SemionGame game) {
        if (eligible(game) && game.currentRound() == 15) {
            awardAll(server, game, WHY_SO_FAST);
        }
    }

    public void onRoundCompleted(MinecraftServer server, SemionGame game, int round) {
        if (!eligible(game) || roundStartStats.isEmpty()) {
            return;
        }
        for (SemionTeam team : game.teams().values()) {
            if (!team.active()) {
                continue;
            }
            List<PlayerLane> lanes = team.laneGroup().lanes();
            for (PlayerLane lane : lanes) {
                UUID playerId = lane.ownerPlayer();
                boolean clean = clean(lane);
                if (game.hasAttemptedRound(playerId, round)) {
                    attemptedDefensePlayers.add(playerId);
                    if (!clean) {
                        failedDefensePlayers.add(playerId);
                    }
                }
                SemionPlayer player = game.players().get(playerId);
                PlayerMatchStatsSnapshot start = roundStartStats.get(playerId);
                if (player == null || start == null) {
                    continue;
                }
                PlayerMatchStatsSnapshot end = player.matchStats().snapshot(player.economy().income());
                if (end.ownLaneIncomingThreat() - start.ownLaneIncomingThreat() >= RECEIVED_THREAT_TARGET) {
                    award(server, playerId, BLOCK_20K_THREAT);
                }
                if (round >= 10 && clean && end.incomingIncomeThreat() == start.incomingIncomeThreat()) {
                    award(server, playerId, OH_LUCKY);
                }
                if (!team.eliminated() && lanes.size() > 1 && clean
                        && end.assistClearThreat() > start.assistClearThreat()
                        && lanes.stream().filter(other -> other != lane).allMatch(SemionAdvancementService::failed)) {
                    award(server, playerId, CLUTCH);
                }
            }
        }
    }

    public void clear() {
        roundStartStats.clear();
        sentThreatByTarget.clear();
        attemptedDefensePlayers.clear();
        failedDefensePlayers.clear();
    }

    public void awardMatch(
            MinecraftServer server,
            MatchResult matchResult,
            Map<UUID, Integer> gamesPlayed
    ) {
        Set<UUID> perfectDefensePlayers = new LinkedHashSet<>(attemptedDefensePlayers);
        perfectDefensePlayers.removeAll(failedDefensePlayers);
        for (Map.Entry<UUID, Set<ResourceLocation>> entry : matchAwards(
                matchResult,
                gamesPlayed,
                perfectDefensePlayers
        ).entrySet()) {
            for (ResourceLocation advancementId : entry.getValue()) {
                award(server, entry.getKey(), advancementId);
            }
        }
    }

    static Map<UUID, Set<ResourceLocation>> matchAwards(
            MatchResult matchResult,
            Map<UUID, Integer> gamesPlayed,
            Set<UUID> perfectDefensePlayers
    ) {
        if (matchResult == null || matchResult.matchMode() != MatchMode.NORMAL) {
            return Map.of();
        }
        Map<TeamId, List<MatchParticipantResult>> teams = new EnumMap<>(TeamId.class);
        for (MatchParticipantResult participant : matchResult.participants()) {
            teams.computeIfAbsent(participant.teamId(), ignored -> new java.util.ArrayList<>()).add(participant);
        }
        Set<TeamId> dreamTeams = new LinkedHashSet<>();
        Set<TeamId> underdogTeams = new LinkedHashSet<>();
        for (TeamId winningTeam : matchResult.winningTeams()) {
            List<MatchParticipantResult> members = teams.getOrDefault(winningTeam, List.of());
            if (members.size() >= 2
                    && members.stream().allMatch(member -> member.jobId() != null)
                    && members.stream().map(MatchParticipantResult::jobId).distinct().count() == members.size()) {
                dreamTeams.add(winningTeam);
            }
            int largestOpponent = teams.entrySet().stream()
                    .filter(entry -> entry.getKey() != winningTeam)
                    .mapToInt(entry -> entry.getValue().size())
                    .max()
                    .orElse(0);
            if (!members.isEmpty() && largestOpponent == members.size() + 1) {
                underdogTeams.add(winningTeam);
            }
        }

        Map<UUID, Set<ResourceLocation>> awards = new LinkedHashMap<>();
        for (MatchParticipantResult participant : matchResult.participants()) {
            Set<ResourceLocation> playerAwards = new LinkedHashSet<>();
            if (perfectDefensePlayers != null && perfectDefensePlayers.contains(participant.playerId())) {
                playerAwards.add(participant.winner() ? PERFECT_DEFENSE_WIN : TEAM_GAP_GG);
            }
            if (participant.winner() && dreamTeams.contains(participant.teamId())) {
                playerAwards.add(DREAM_TEAM);
            }
            if (participant.winner() && underdogTeams.contains(participant.teamId())) {
                playerAwards.add(UNDERDOG);
            }
            int played = gamesPlayed == null ? 0 : gamesPlayed.getOrDefault(participant.playerId(), 0);
            if (played >= 10) {
                playerAwards.add(NEWBIE_EXIT);
            }
            if (played >= 100) {
                playerAwards.add(VETERAN_100);
            }
            if (!playerAwards.isEmpty()) {
                awards.put(participant.playerId(), Set.copyOf(playerAwards));
            }
        }
        return Map.copyOf(awards);
    }

    private static boolean clean(PlayerLane lane) {
        return lane.clearedThisRound() && !lane.leakedThisRound() && !lane.laneDefenseBroken();
    }

    private static boolean failed(PlayerLane lane) {
        return lane.leakedThisRound() || lane.laneDefenseBroken();
    }

    private static boolean eligible(SemionGame game) {
        return game != null && game.matchMode() == MatchMode.NORMAL;
    }

    private static void awardAll(MinecraftServer server, SemionGame game, ResourceLocation advancementId) {
        for (UUID playerId : game.players().keySet()) {
            award(server, playerId, advancementId);
        }
    }

    private static void award(MinecraftServer server, UUID playerId, ResourceLocation advancementId) {
        award(server, playerId, advancementId, "complete");
    }

    private static void award(
            MinecraftServer server,
            UUID playerId,
            ResourceLocation advancementId,
            String criterion
    ) {
        if (server == null || playerId == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        AdvancementHolder advancement = server.getAdvancements().get(advancementId);
        if (player != null && advancement != null) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, path);
    }
}
