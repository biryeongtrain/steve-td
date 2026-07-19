package kim.biryeong.semiontd.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.MonsterScalingConfig;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class SemionTeam {
    public static final int MAX_PLAYERS = 5;

    private final TeamId id;
    private final List<UUID> memberIds = new ArrayList<>();
    private final TeamLaneGroup laneGroup;
    private final Map<UUID, List<TowerCompositionEntry>> finalTowerCompositions = new HashMap<>();
    private boolean active;
    private boolean eliminated;
    private LeaderTargetingState leaderTargeting;

    public SemionTeam(TeamId id) {
        this.id = id;
        this.laneGroup = new TeamLaneGroup(id, BossMonster.defaultBoss(id));
    }

    public TeamId id() {
        return id;
    }

    public List<UUID> memberIds() {
        return memberIds;
    }

    public TeamLaneGroup laneGroup() {
        return laneGroup;
    }

    public boolean eliminated() {
        return eliminated;
    }

    public boolean active() {
        return active;
    }

    public Optional<UUID> leaderPlayerId() {
        return leaderTargeting == null ? Optional.empty() : Optional.of(leaderTargeting.leaderPlayerId());
    }

    public Optional<LeaderTargetingState> leaderTargeting() {
        if (!active || eliminated) {
            return Optional.empty();
        }
        return Optional.ofNullable(leaderTargeting);
    }

    public boolean hasLeader(UUID playerId) {
        return playerId != null && leaderPlayerId().map(playerId::equals).orElse(false);
    }

    public void setLeader(UUID playerId) {
        this.leaderTargeting = new LeaderTargetingState(playerId);
    }

    public void activate() {
        this.active = true;
        this.eliminated = false;
        this.finalTowerCompositions.clear();
    }

    public void deactivate() {
        this.active = false;
        this.eliminated = false;
        this.leaderTargeting = null;
        this.memberIds.clear();
        this.finalTowerCompositions.clear();
        this.laneGroup.closeRuntime();
    }

    public void closeRuntime() {
        this.active = false;
        this.eliminated = false;
        this.leaderTargeting = null;
        this.memberIds.clear();
        this.finalTowerCompositions.clear();
        this.laneGroup.closeRuntime();
    }

    public boolean addPlayer(SemionPlayer player, ServerLevel arenaWorld, LaneRegionLayout laneLayout) {
        if (!active || eliminated || memberIds.size() >= MAX_PLAYERS || player.teamId() != id) {
            return false;
        }
        memberIds.add(player.uuid());
        laneGroup.addLane(new PlayerLane(id, player.laneId(), player.uuid(), arenaWorld, laneLayout));
        return true;
    }

    public void tick(MinecraftServer server) {
        tick(server, null, Map.of());
    }

    public void tick(MinecraftServer server, EconomyService economyService, Map<UUID, SemionPlayer> players) {
        tick(server, economyService, players, MonsterScalingConfig.defaultConfig(), 0);
    }

    public void tick(
            MinecraftServer server,
            EconomyService economyService,
            Map<UUID, SemionPlayer> players,
            MonsterScalingConfig monsterScalingConfig,
            int roundElapsedTicks
    ) {
        if (!active || eliminated) {
            return;
        }
        laneGroup.tick(server, economyService, players, monsterScalingConfig, roundElapsedTicks);
        if (!laneGroup.boss().isAlive()) {
            eliminate();
        }
    }

    public void resetForRound() {
        if (active && !eliminated) {
            laneGroup.resetForRound();
        }
    }

    public boolean isRoundResolved() {
        return !active || eliminated || laneGroup.isRoundCleared();
    }

    public boolean eliminate() {
        if (eliminated) {
            return false;
        }
        eliminated = true;
        leaderTargeting = null;
        finalTowerCompositions.clear();
        for (PlayerLane lane : laneGroup.lanes()) {
            finalTowerCompositions.put(lane.ownerPlayer(), lane.towerComposition());
        }
        laneGroup.disableMonsters();
        laneGroup.clearTowers();
        laneGroup.discardBossEntity();
        return true;
    }

    public List<TowerCompositionEntry> finalTowerComposition(UUID playerId, int laneId) {
        List<TowerCompositionEntry> captured = finalTowerCompositions.get(playerId);
        if (captured != null) {
            return captured;
        }
        return laneGroup.lane(laneId)
                .filter(lane -> lane.ownerPlayer().equals(playerId))
                .map(PlayerLane::towerComposition)
                .orElse(List.of());
    }
}
