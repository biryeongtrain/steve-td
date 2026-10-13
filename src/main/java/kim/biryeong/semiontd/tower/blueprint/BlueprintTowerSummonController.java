package kim.biryeong.semiontd.tower.blueprint;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;

final class BlueprintTowerSummonController {
    private static final int PULSE_TICKS = 20;
    private final java.util.LinkedHashMap<Integer, Long> minions = new java.util.LinkedHashMap<>();
    private int summonPulses;

    int count() {
        return minions.size();
    }

    private static double value(BlueprintTower tower, String parameter) {
        return BlueprintModule.SUMMON.value(parameter, tower.blueprintStats().level(BlueprintModule.SUMMON));
    }

    void expire(PlayerLane lane) {
        var world = lane.arenaWorld();
        var iterator = minions.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var entity = world.getEntity(entry.getKey());
            if (!(entity instanceof SemionTowerEntity minion) || !minion.isAlive() || world.getGameTime() >= entry.getValue()
                    || minion.runtimeTower() == null || minion.runtimeTower().health() <= 0.0) {
                if (entity != null && !entity.isRemoved()) {
                    entity.discard();
                }
                iterator.remove();
            }
        }
    }

    void summon(BlueprintTower tower, PlayerLane lane, SemionTowerEntity source) {
        var world = lane.arenaWorld();
        int interval = Math.max(1, (int) value(tower, "intervalTicks") / PULSE_TICKS);
        if (++summonPulses < interval || minions.size() >= (int) value(tower, "count")) {
            return;
        }
        summonPulses = 0;
        double ratio = value(tower, "statRatio");
        TowerType source0 = tower.type();
        TowerType minionType = new TowerType(source0.id(), source0.displayName() + " 하수인", source0.category(), 0,
                Math.max(1.0, tower.currentMaxHealth() * ratio), source0.range(), source0.damage() * ratio,
                source0.attackIntervalTicks(), source0.aggroPriority(), List.of(), source0.visual().withScale(0.7),
                List.of(), source0.primaryDamageType());
        double angle = source.getRandom().nextDouble() * Math.PI * 2.0;
        net.minecraft.world.phys.Vec3 spawn = source.position().add(Math.cos(angle) * 1.2, 0.0, Math.sin(angle) * 1.2);
        GridPosition grid = GridPosition.from(net.minecraft.core.BlockPos.containing(spawn.x, spawn.y - 1.0, spawn.z));
        Tower minionTower = new kim.biryeong.semiontd.tower.legion.IllusionRuntimeTower(minionType, tower.ownerPlayer(), tower.teamId(), tower.laneId(), grid);
        minionTower.markTemporaryCopy(UUID.randomUUID());
        minionTower.attachToLane(lane, lane.traitLoadout());
        SemionTowerEntity minion = new SemionTowerEntity(kim.biryeong.semiontd.entity.SemionEntityTypes.TOWER, world);
        minion.configure(minionTower, lane.laneLayout());
        minion.markIllusionClone();
        minion.setPos(spawn.x, spawn.y, spawn.z);
        if (world.addFreshEntity(minion)) {
            minions.put(minion.getId(), world.getGameTime() + (long) value(tower, "durationTicks"));
        }
    }

    void dismiss(PlayerLane lane) {
        if (lane != null) {
            for (int id : minions.keySet()) {
                var entity = lane.arenaWorld().getEntity(id);
                if (entity != null && !entity.isRemoved()) {
                    entity.discard();
                }
            }
        }
        minions.clear();
        summonPulses = 0;
    }
}
