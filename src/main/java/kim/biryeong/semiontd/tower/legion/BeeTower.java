package kim.biryeong.semiontd.tower.legion;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.entity.Entity;

public class BeeTower extends EntityBackedTower {
    private final Map<Integer, BeeStingPolicy.State> stings = new HashMap<>();
    private int currentSwarmStacks;

    public BeeTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public BeeTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        super.onPlaced(lane);
        refreshSwarmStacks(lane);
    }

    @Override
    public void tick(PlayerLane lane) {
        refreshSwarmStacks(lane);
        super.tick(lane);
        tickStings(lane);
    }

    @Override
    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        if (target == null || killedTarget) {
            return;
        }
        stings.put(target.getId(), BeeStingPolicy.applySting(
                stings.get(target.getId()),
                maxPoisonStacks(),
                abilityTicks("poisonDurationTicks"),
                abilityTicks("poisonTickIntervalTicks")
        ));
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        stings.clear();
        super.onRemoved(lane);
        refreshBeeSwarmStacks(lane);
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        stings.clear();
        super.resetForRound(lane);
    }

    private void refreshSwarmStacks(PlayerLane lane) {
        if (lane == null) {
            currentSwarmStacks = 0;
            return;
        }
        long matchingBees = lane.towers().stream()
                .filter(tower -> tower != this)
                .filter(tower -> ownerPlayer().equals(tower.ownerPlayer()))
                .filter(this::isSwarmFamily)
                .count();
        currentSwarmStacks = Math.min(maxSwarmStacks(), (int) matchingBees);
    }

    private boolean isSwarmFamily(Tower tower) {
        return tower != null && (
                tower.type().id().equals(LegionTowers.T1_BEE_TOWER.id())
                        || tower.type().id().equals(LegionTowers.T2_BEE_TOWER.id())
                        || tower.type().id().equals(LegionTowers.T3_BEE_TOWER.id())
        );
    }

    private int maxSwarmStacks() {
        return TowerBalanceRuntime.abilityInt(type().id(), "maxSwarmStacks");
    }

    private void tickStings(PlayerLane lane) {
        if (lane == null || stings.isEmpty()) {
            return;
        }
        SemionTowerEntity towerEntity = towerEntity(lane);
        if (towerEntity == null) {
            stings.clear();
            return;
        }
        Iterator<Map.Entry<Integer, BeeStingPolicy.State>> iterator = stings.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, BeeStingPolicy.State> entry = iterator.next();
            Entity entity = lane.arenaWorld().getEntity(entry.getKey());
            if (!(entity instanceof SemionMonsterEntity monster) || monster.isRemoved() || !monster.isAlive()) {
                iterator.remove();
                continue;
            }
            BeeStingPolicy.TickResult result = BeeStingPolicy.tick(
                    entry.getValue(),
                    poisonDamagePerStack(),
                    abilityTicks("poisonTickIntervalTicks")
            );
            if (result.damage() > 0.0) {
                applyPoisonDamage(towerEntity, monster, result.damage());
            }
            if (result.state().isPresent() && monster.isAlive() && !monster.isRemoved()) {
                entry.setValue(result.state().orElseThrow());
            } else {
                iterator.remove();
            }
        }
    }

    private SemionTowerEntity towerEntity(PlayerLane lane) {
        if (lane == null || entityId().isEmpty()) {
            return null;
        }
        Entity entity = lane.arenaWorld().getEntity(entityId().getAsInt());
        return entity instanceof SemionTowerEntity towerEntity && towerEntity.isAlive() && !towerEntity.isRemoved()
                ? towerEntity
                : null;
    }

    private void applyPoisonDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double baseDamage) {
        var runtimeMonster = target.runtimeMonster();
        double traitDamage = towerEntity.applyTraitOutgoingDamage(runtimeMonster, baseDamage);
        double damageAmount = target.towerDamageTaken(traitDamage);
        if (damageAmount <= 0.0) {
            return;
        }
        double previousHealth = runtimeMonster == null ? 0.0 : runtimeMonster.health();
        target.applyRuntimeDamage(
                towerEntity.damageSources().mobAttack(towerEntity),
                damageAmount,
                DamageType.MAGIC
        );
        if (runtimeMonster != null && runtimeMonster.health() < previousHealth) {
            runtimeMonster.recordLastHit(ownerPlayer(), KillSourceKind.TOWER);
        }
    }

    private int maxPoisonStacks() {
        return TowerBalanceRuntime.abilityInt(type().id(), "maxPoisonStacks")
                + currentSwarmStacks * abilityInt("poisonStacksPerSwarmStack");
    }

    private double poisonDamagePerStack() {
        return value("poisonDamagePerStack") + currentSwarmStacks * value("poisonDamagePerSwarmStack");
    }

    private int abilityTicks(String key) {
        return TowerBalanceRuntime.abilityTicks(type().id(), key);
    }

    private int abilityInt(String key) {
        return TowerBalanceRuntime.abilityInt(type().id(), key);
    }

    private double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    static void refreshBeeSwarmStacks(PlayerLane lane) {
        if (lane == null) {
            return;
        }
        for (Tower tower : lane.towers()) {
            if (tower instanceof BeeTower beeTower) {
                beeTower.refreshSwarmStacks(lane);
            }
        }
    }
}
