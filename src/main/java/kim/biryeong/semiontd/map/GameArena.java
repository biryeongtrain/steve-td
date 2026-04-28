package kim.biryeong.semiontd.map;

import java.util.Map;
import java.util.Optional;
import kim.biryeong.semiontd.game.TeamId;

public final class GameArena {
    private final Map<TeamId, TeamArena> teamArenas;

    public GameArena(Map<TeamId, TeamArena> teamArenas) {
        this.teamArenas = Map.copyOf(teamArenas);
    }

    public Optional<TeamArena> teamArena(TeamId teamId) {
        return Optional.ofNullable(teamArenas.get(teamId));
    }

    public Optional<LaneRegionLayout> lane(TeamId teamId, int laneId) {
        return teamArena(teamId).flatMap(arena -> arena.layout().lane(laneId));
    }

    public void unload() {
        for (TeamArena teamArena : teamArenas.values()) {
            teamArena.unload();
        }
    }
}
