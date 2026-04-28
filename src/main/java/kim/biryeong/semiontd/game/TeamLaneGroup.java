package kim.biryeong.semiontd.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.entity.boss.SemionBossEntity;
import kim.biryeong.semiontd.entity.defender.DefenderEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class TeamLaneGroup {
    private final TeamId teamId;
    private final List<PlayerLane> lanes = new ArrayList<>();
    private final List<DefenderEntity> finalDefenseDefenders = new ArrayList<>();
    private final BossMonster boss;
    private ServerLevel bossWorld;
    private SemionBossEntity bossEntity;
    private int bossEntityId = -1;

    public TeamLaneGroup(TeamId teamId, BossMonster boss) {
        this.teamId = teamId;
        this.boss = boss;
    }

    public TeamId teamId() {
        return teamId;
    }

    public List<PlayerLane> lanes() {
        return lanes;
    }

    public BossMonster boss() {
        return boss;
    }

    public void addLane(PlayerLane lane) {
        lanes.add(lane);
    }

    public Optional<PlayerLane> lane(int laneId) {
        return lanes.stream()
                .filter(lane -> lane.laneId() == laneId)
                .findFirst();
    }

    public Optional<PlayerLane> firstActiveLane() {
        return lanes.stream().findFirst();
    }

    public void resetForRound() {
        for (PlayerLane lane : lanes) {
            lane.resetForRound();
        }
        for (DefenderEntity defenderEntity : finalDefenseDefenders) {
            defenderEntity.remove();
        }
        finalDefenseDefenders.clear();
    }

    public void tick(MinecraftServer server) {
        tick(server, null, Map.of());
    }

    public void tick(
            MinecraftServer server,
            EconomyService economyService,
            Map<UUID, SemionPlayer> players
    ) {
        for (PlayerLane lane : lanes) {
            lane.tick(server, economyService, players);
            if (lane.clearedThisRound()) {
                lane.moveTowersToFinalDefense();
                List<DefenderEntity> defenders = lane.releaseDefendersToFinalDefense();
                for (DefenderEntity defender : defenders) {
                    defender.arriveFinalDefense();
                }
                finalDefenseDefenders.addAll(defenders);
            }
        }
        syncBossEntity();
    }

    public boolean isRoundCleared() {
        return lanes.stream().allMatch(PlayerLane::clearedThisRound);
    }

    public void clearTowers() {
        for (PlayerLane lane : lanes) {
            lane.clearTowers();
        }
    }

    public void disableMonsters() {
        for (PlayerLane lane : lanes) {
            lane.disableMonsters();
        }
        for (DefenderEntity defenderEntity : finalDefenseDefenders) {
            defenderEntity.remove();
        }
        finalDefenseDefenders.clear();
    }

    public void forceFinalDefense() {
        for (PlayerLane lane : lanes) {
            finalDefenseDefenders.addAll(lane.forceFinalDefense());
        }
    }

    public boolean hasBossEntity() {
        return bossEntity != null && !bossEntity.isRemoved();
    }

    public int bossEntityId() {
        return bossEntityId;
    }

    public Optional<SemionBossEntity> bossEntity() {
        return Optional.ofNullable(bossEntity);
    }

    public void spawnBossEntity(ServerLevel world, Vec3 position) {
        discardBossEntity();
        bossWorld = world;

        SemionBossEntity entity = new SemionBossEntity(SemionEntityTypes.BOSS, world);
        entity.configure(teamId, boss);
        entity.setPos(position.x, position.y, position.z);

        if (world.addFreshEntity(entity)) {
            bossEntity = entity;
            bossEntityId = entity.getId();
        }
    }

    public void discardBossEntity() {
        if (bossWorld == null || bossEntityId < 0) {
            bossEntityId = -1;
            bossEntity = null;
            return;
        }

        if (bossEntity != null) {
            bossEntity.discard();
        }
        bossEntityId = -1;
        bossEntity = null;
        bossWorld = null;
    }

    private void syncBossEntity() {
        if (bossWorld == null || bossEntityId < 0 || bossEntity == null) {
            return;
        }

        if (bossEntity.isRemoved()) {
            boss.damage(Double.MAX_VALUE);
            bossEntityId = -1;
            bossEntity = null;
            bossWorld = null;
            return;
        }

        if (!boss.isAlive() || !bossEntity.isAlive()) {
            boss.damage(Double.MAX_VALUE);
            bossEntity.discard();
            bossEntityId = -1;
            bossEntity = null;
            bossWorld = null;
            return;
        }

        if (Math.abs(bossEntity.getHealth() - boss.health()) > 0.01F) {
            bossEntity.setHealth((float) Math.max(0.1, boss.health()));
        }
    }
}
