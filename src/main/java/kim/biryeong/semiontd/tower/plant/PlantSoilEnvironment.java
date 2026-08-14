package kim.biryeong.semiontd.tower.plant;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.util.Mth;

/**
 * Terrain effects applied to monsters standing on claimed soil.
 *
 * <p>균사 weakens what stands on it. 사암 slows attacks and attributes its periodic magic damage to
 * the living terraformer that created the tile. 잔디 and 회백토 are friendly terrain and do nothing here.
 */
public final class PlantSoilEnvironment {
    private PlantSoilEnvironment() {
    }

    public static void tick(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null) {
            return;
        }
        UUID owner = lane.ownerPlayer();
        if (owner == null) {
            return;
        }
        long gameTime = lane.arenaWorld().getGameTime();
        if (PlantSoilStates.totalCount(owner) == 0) {
            return;
        }
        int interval = Math.max(1, globalTicks("environmentTickIntervalTicks"));
        if (gameTime % interval != 0) {
            return;
        }
        applyMeadowGrowthShare(lane, interval);

        for (Monster monster : List.copyOf(lane.activeMonsters())) {
            if (monster == null || !monster.isAlive() || !monster.hasMinecraftEntity()) {
                continue;
            }
            if (!(lane.arenaWorld().getEntity(monster.minecraftEntityId()) instanceof SemionMonsterEntity entity)
                    || entity.isRemoved()) {
                continue;
            }
            PlantSoil soil = PlantSoilStates.soilAtColumn(owner, Mth.floor(entity.getX()), Mth.floor(entity.getZ()));
            if (soil == null) {
                continue;
            }
            applyEnvironment(lane, owner, monster, entity, soil, interval);
        }
    }

    /**
     * 잔디 타워들이 키운 성장 체력을 합산해 라인 안 모든 타워에게 같은 값으로 겁니다.
     *
     * <p>거리 제한이 없고 여러 잔디 타워가 있으면 그만큼 더해집니다. 합계를 한 번 계산해 모두에게
     * 같은 값으로 걸기 때문에, 최댓값만 잡는 소스 없는 효과를 써도 결과가 동일합니다.
     */
    private static void applyMeadowGrowthShare(PlayerLane lane, int intervalTicks) {
        double total = 0.0;
        for (Tower tower : List.copyOf(lane.towers())) {
            if (tower instanceof PlantCombatTower plant && tower.health() > 0.0) {
                total += plant.sharedGrowthBonus();
            }
        }
        if (total <= 0.0) {
            return;
        }
        // 잔디 타워를 늘릴수록 합계가 커지므로 라인 전체 버프에는 상한을 둡니다.
        double cap = TowerBalanceRuntime.ability(PlantSoil.MEADOW.configId(), "growthShareCap", 0.0);
        if (cap > 0.0) {
            total = Math.min(cap, total);
        }
        int durationTicks = Math.max(
                intervalTicks * 2,
                TowerBalanceRuntime.abilityTicks(PlantSoil.MEADOW.configId(), "supportDurationTicks", 0)
        );
        for (Tower tower : List.copyOf(lane.towers())) {
            if (!(tower instanceof EntityBackedTower backed) || backed.entityId().isEmpty()) {
                continue;
            }
            if (lane.arenaWorld().getEntity(backed.entityId().getAsInt()) instanceof SemionTowerEntity entity) {
                entity.applyTimedEffect(TimedEffectType.TOWER_MAX_HEALTH_BONUS, total, durationTicks);
            }
        }
    }

    private static void applyEnvironment(
            PlayerLane lane,
            UUID owner,
            Monster monster,
            SemionMonsterEntity entity,
            PlantSoil soil,
            int intervalTicks
    ) {
        // 다음 펄스까지는 효과가 끊기지 않도록 간격보다 넉넉하게 겁니다.
        int durationTicks = Math.max(intervalTicks * 2, soilTicks(soil, "environmentDurationTicks"));

        double weakness = soilValue(soil, "environmentWeakness");
        if (weakness > 0.0) {
            entity.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION, weakness, durationTicks);
        }

        // 균사는 지뢰 계열이라 상주 타워가 없습니다. 딜증(취약)도 지형이 직접 겁니다.
        double damageTakenBonus = soilValue(soil, "environmentDamageTakenBonus");
        if (damageTakenBonus > 0.0) {
            entity.applyTimedEffect(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS, damageTakenBonus, durationTicks);
        }

        double moveSpeedReduction = soilValue(soil, "environmentMoveSpeedReduction");
        if (moveSpeedReduction > 0.0) {
            entity.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, moveSpeedReduction, durationTicks);
        }

        double attackSpeedReduction = soilValue(soil, "environmentAttackSpeedReduction");
        if (attackSpeedReduction > 0.0) {
            entity.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION, attackSpeedReduction, durationTicks);
        }

        // 최대 체력 비례라 라운드가 올라가 몬스터가 단단해져도 사암이 계속 값을 합니다.
        double ratioPerSecond = soilValue(soil, "environmentMaxHealthDamagePerSecond");
        if (ratioPerSecond > 0.0) {
            double damage = monster.maxHealth() * ratioPerSecond * (intervalTicks / 20.0);
            PlantTerraformTower source = terrainSource(lane, owner, entity);
            SemionTowerEntity sourceEntity = sourceEntity(lane, source);
            if (damage > 0.0 && sourceEntity != null) {
                Tower.DamageResult result = source.damageResolvedTargetResult(
                        sourceEntity,
                        entity,
                        damage,
                        DamageType.MAGIC
                );
                if (result.killed()) {
                    source.onKill(sourceEntity, entity, damage);
                }
            }
        }
    }

    private static PlantTerraformTower terrainSource(PlayerLane lane, UUID owner, SemionMonsterEntity monster) {
        int x = Mth.floor(monster.getX());
        int z = Mth.floor(monster.getZ());
        GridPosition sourcePosition = PlantSoilStates.sourceAtColumn(owner, x, z);
        if (sourcePosition == null) {
            return null;
        }
        for (Tower tower : lane.towers()) {
            if (tower instanceof PlantTerraformTower terraformer
                    && tower.health() > 0.0
                    && owner.equals(tower.ownerPlayer())
                    && sourcePosition.equals(tower.originalPosition())) {
                return terraformer;
            }
        }
        return null;
    }

    private static SemionTowerEntity sourceEntity(PlayerLane lane, PlantTerraformTower source) {
        if (source == null || source.entityId().isEmpty()) {
            return null;
        }
        return lane.arenaWorld().getEntity(source.entityId().getAsInt()) instanceof SemionTowerEntity entity
                && !entity.isRemoved()
                ? entity
                : null;
    }

    private static double soilValue(PlantSoil soil, String key) {
        return TowerBalanceRuntime.ability(soil.configId(), key, 0.0);
    }

    private static int soilTicks(PlantSoil soil, String key) {
        return TowerBalanceRuntime.abilityTicks(soil.configId(), key, 0);
    }

    private static int globalTicks(String key) {
        return TowerBalanceRuntime.abilityTicks(PlantTowers.GLOBAL_CONFIG_ID, key, 20);
    }
}
