package kim.biryeong.semionTd.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semionTd.entity.boss.BossMonster;
import kim.biryeong.semionTd.map.LaneRegionLayout;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class SemionTeam {
    public static final int MAX_PLAYERS = 5;

    private final TeamId id;
    private final List<UUID> memberIds = new ArrayList<>();
    private final TeamLaneGroup laneGroup;
    private boolean active;
    private boolean eliminated;

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

    public void activate() {
        this.active = true;
        this.eliminated = false;
    }

    public void deactivate() {
        this.active = false;
        this.eliminated = false;
        this.memberIds.clear();
        this.laneGroup.clearTowers();
        this.laneGroup.discardBossEntity();
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
        if (!active || eliminated) {
            return;
        }
        laneGroup.tick(server, economyService, players);
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
        laneGroup.clearTowers();
        laneGroup.discardBossEntity();
        return true;
    }
}